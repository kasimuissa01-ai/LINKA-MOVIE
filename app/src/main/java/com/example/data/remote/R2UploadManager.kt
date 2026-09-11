package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Central configuration for the Render Node.js Cloudflare R2 Upload Service.
 *
 * ARCHITECTURE:
 * Android App ──(metadata only: /create, /complete, /abort)──> Render Node.js backend
 *      │
 *      └──(raw video parts via presigned URLs)──> Cloudflare R2 (DIRECT)
 *
 * Render NEVER receives the actual video bytes.
 * The Android app NEVER contains private R2 or Cloudflare credentials.
 */
object R2UploadConfig {
    /**
     * Public URL of the Render upload service.
     * Easy to change when deploying to different Render environments.
     */
    var R2_UPLOAD_SERVICE_URL: String = "https://linka-movie.onrender.com"

    /** 50 MB part size for multipart upload */
    const val PART_SIZE: Long = 50 * 1024 * 1024L // 50 MB

    /** Maximum retries per failed part */
    const val MAX_RETRIES: Int = 5
}

/**
 * Presigned part information returned by the Render /create endpoint.
 */
data class PresignedPart(
    val partNumber: Int,
    val url: String
)

/**
 * Collected ETag from an individual part uploaded directly to Cloudflare R2.
 */
data class PartETag(
    val partNumber: Int,
    val etag: String
)

/**
 * Response payload from Render POST /create.
 */
data class CreateUploadResponse(
    val uploadId: String,
    val key: String,
    val partSize: Long,
    val partCount: Int,
    val parts: List<PresignedPart>
)

/**
 * Result of Render POST /complete.
 */
data class CompleteUploadResult(
    val success: Boolean,
    val key: String,
    val url: String = ""
)

/**
 * Information resolved from an Android Content URI or File URI.
 */
data class UploadFileInfo(
    val filename: String,
    val fileSize: Long,
    val contentType: String
)

/**
 * Manager responsible for coordinating multipart video uploads via the Render backend
 * and uploading raw parts directly to Cloudflare R2.
 */
class R2UploadManager(
    private var serviceUrl: String = R2UploadConfig.R2_UPLOAD_SERVICE_URL,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    companion object {
        private const val TAG = "R2UploadManager"
        private val RETRY_DELAYS_MS = longArrayOf(1000L, 2000L, 4000L, 8000L, 16000L)
    }

    fun setServiceUrl(url: String) {
        serviceUrl = url
        R2UploadConfig.R2_UPLOAD_SERVICE_URL = url
    }

    fun getServiceUrl(): String = serviceUrl

    /**
     * Resolves the filename, filesize, and MIME type from an Android Content or File Uri.
     */
    fun resolveFileInfo(
        context: Context,
        uri: Uri,
        fallbackName: String = "movie_${System.currentTimeMillis()}.mp4"
    ): UploadFileInfo {
        var filename = fallbackName
        var fileSize = 0L
        var mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { if (it.isNotBlank()) filename = it }
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve file info from content query: ${e.message}")
        }

        if (fileSize <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    fileSize = pfd.statSize
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inspect file descriptor: ${e.message}")
            }
        }

        if (fileSize <= 0L && uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    fileSize = file.length()
                    if (filename == fallbackName) filename = file.name
                }
            }
        }

        return UploadFileInfo(
            filename = filename,
            fileSize = fileSize.coerceAtLeast(1024L),
            contentType = if (mimeType.isNotBlank()) mimeType else "video/mp4"
        )
    }

    /**
     * Step 1: Tell Render to initiate multipart upload and generate presigned R2 URLs.
     *
     * POST /create
     * Body: { "filename": "...", "contentType": "video/mp4", "fileSize": 123456789 }
     */
    suspend fun createMultipartUpload(
        filename: String,
        contentType: String,
        fileSize: Long
    ): CreateUploadResponse = withContext(Dispatchers.IO) {
        val endpoint = "${serviceUrl.trimEnd('/')}/create"
        val payload = JSONObject().apply {
            put("filename", filename)
            put("contentType", contentType)
            put("fileSize", fileSize)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), payload.toString()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Render /create failed with HTTP ${response.code}: $errorBody")
            }

            val bodyString = response.body?.string()
                ?: throw IOException("Render /create returned an empty response")

            val json = JSONObject(bodyString)
            val uploadId = json.optString("uploadId")
            val key = json.optString("key")
            val partSize = json.optLong("partSize", R2UploadConfig.PART_SIZE).let {
                if (it <= 0L) R2UploadConfig.PART_SIZE else it
            }
            val partCount = json.optInt("partCount", 1)

            if (uploadId.isBlank() || key.isBlank()) {
                throw IOException("Render /create response missing uploadId or key: $bodyString")
            }

            val partsList = mutableListOf<PresignedPart>()
            val partsArray = json.optJSONArray("parts") ?: json.optJSONArray("urls")
            if (partsArray != null) {
                for (i in 0 until partsArray.length()) {
                    val item = partsArray.opt(i)
                    if (item is JSONObject) {
                        val partNum = item.optInt("partNumber", item.optInt("PartNumber", i + 1))
                        val url = item.optString("url", item.optString("presignedUrl", ""))
                        if (url.isNotBlank()) {
                            partsList.add(PresignedPart(partNum, url))
                        }
                    } else if (item is String && item.isNotBlank()) {
                        partsList.add(PresignedPart(i + 1, item))
                    }
                }
            }

            if (partsList.isEmpty()) {
                throw IOException("Render /create did not return any presigned URLs: $bodyString")
            }

            CreateUploadResponse(
                uploadId = uploadId,
                key = key,
                partSize = partSize,
                partCount = partCount,
                parts = partsList.sortedBy { it.partNumber }
            )
        }
    }

    /**
     * Step 2: Stream part bytes DIRECTLY from Android to Cloudflare R2 using the presigned URL.
     * Render NEVER receives these bytes.
     */
    suspend fun uploadPartDirectToR2(
        context: Context,
        uri: Uri,
        partUrl: String,
        partNumber: Int,
        startByte: Long,
        partLength: Long
    ): String = withContext(Dispatchers.IO) {
        val requestBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
            override fun contentLength(): Long = partLength

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    var skipped = 0L
                    while (skipped < startByte) {
                        val n = inputStream.skip(startByte - skipped)
                        if (n <= 0) {
                            if (inputStream.read() == -1) break
                            skipped += 1
                        } else {
                            skipped += n
                        }
                    }

                    val buffer = ByteArray(64 * 1024)
                    var remaining = partLength
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val bytesRead = inputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        sink.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                    }
                } ?: throw IOException("Could not open input stream for URI: $uri")
            }
        }

        val request = Request.Builder()
            .url(partUrl)
            .put(requestBody)
            .header("Content-Type", "application/octet-stream")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                throw IOException("R2 direct part $partNumber upload failed (HTTP ${response.code}): $err")
            }
            val rawEtag = response.header("ETag") ?: response.header("etag")
            val etag = rawEtag?.trim('"', ' ') ?: ""
            if (etag.isBlank()) {
                throw IOException("R2 did not return an ETag header for part $partNumber")
            }
            etag
        }
    }

    /**
     * Step 3: Send collected part ETags to Render to complete the R2 multipart upload.
     *
     * POST /complete
     */
    suspend fun completeMultipartUpload(
        key: String,
        uploadId: String,
        etags: List<PartETag>
    ): CompleteUploadResult = withContext(Dispatchers.IO) {
        val endpoint = "${serviceUrl.trimEnd('/')}/complete"

        val partsArray = JSONArray()
        val etagsArray = JSONArray()

        etags.sortedBy { it.partNumber }.forEach { part ->
            val partObj = JSONObject().apply {
                put("partNumber", part.partNumber)
                put("etag", part.etag)
                put("PartNumber", part.partNumber)
                put("ETag", part.etag)
            }
            partsArray.put(partObj)
            etagsArray.put(partObj)
        }

        val payload = JSONObject().apply {
            put("key", key)
            put("uploadId", uploadId)
            put("parts", partsArray)
            put("etags", etagsArray)
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), payload.toString()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Render /complete failed with HTTP ${response.code}: $errorBody")
            }

            val bodyString = response.body?.string() ?: ""
            val json = if (bodyString.isNotBlank()) JSONObject(bodyString) else JSONObject()
            val success = json.optBoolean("success", true)
            val finalKey = json.optString("key", key).ifBlank { key }
            val finalUrl = json.optString("url", "")

            CompleteUploadResult(
                success = success,
                key = finalKey,
                url = finalUrl
            )
        }
    }

    /**
     * Abort multipart upload on R2 via Render if any part permanently fails.
     *
     * POST /abort
     */
    suspend fun abortMultipartUpload(
        key: String,
        uploadId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${serviceUrl.trimEnd('/')}/abort"
            val payload = JSONObject().apply {
                put("key", key)
                put("uploadId", uploadId)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), payload.toString()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Abort response for key $key: HTTP ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Render /abort: ${e.message}", e)
            false
        }
    }

    /**
     * Comprehensive upload orchestrator meeting all requirements:
     * - Obtains Uri & metadata
     * - Requests presigned URLs from Render /create
     * - Directly streams 50MB parts to Cloudflare R2
     * - Retries failed parts up to 5 times with exponential backoff (1s, 2s, 4s, 8s, 16s)
     * - Aborts multipart upload via Render /abort if all retries fail
     * - Collects ETags and completes upload via Render /complete
     * - Reports genuine part-based progress (0% -> 100%)
     */
    suspend fun uploadVideo(
        context: Context,
        uri: Uri,
        customFilename: String? = null,
        onProgress: ((progressPercent: Int, statusMessage: String) -> Unit)? = null
    ): CompleteUploadResult = withContext(Dispatchers.IO) {
        val fileInfo = resolveFileInfo(
            context = context,
            uri = uri,
            fallbackName = customFilename ?: "video_${System.currentTimeMillis()}.mp4"
        )
        val filename = customFilename ?: fileInfo.filename
        val fileSize = fileInfo.fileSize
        val contentType = fileInfo.contentType

        Log.i(TAG, "Starting direct R2 multipart upload for '$filename' ($fileSize bytes) via Render: $serviceUrl")
        onProgress?.invoke(0, "Initiating multipart upload with Render service...")

        // Step 1: POST /create
        val createResponse = try {
            createMultipartUpload(
                filename = filename,
                contentType = contentType,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during Render /create: ${e.message}", e)
            throw IOException("Render /create failed: ${e.message}", e)
        }

        val uploadId = createResponse.uploadId
        val key = createResponse.key
        val partSize = createResponse.partSize
        val parts = createResponse.parts
        val totalParts = parts.size

        Log.i(TAG, "Upload session initiated: uploadId=$uploadId, key=$key, totalParts=$totalParts")

        val etags = mutableListOf<PartETag>()

        // Step 2: Upload parts directly to Cloudflare R2
        for (index in parts.indices) {
            val part = parts[index]
            val currentPartNum = part.partNumber
            val startByte = (currentPartNum - 1) * partSize
            val endByte = minOf(startByte + partSize, fileSize)
            val partLength = endByte - startByte

            var partSuccess = false
            var lastPartError: Exception? = null

            val currentProgressPct = (((index).toFloat() / totalParts) * 100).toInt()
            onProgress?.invoke(
                currentProgressPct,
                "Uploading part $currentPartNum of $totalParts directly to R2..."
            )

            for (attempt in 1..R2UploadConfig.MAX_RETRIES) {
                try {
                    Log.d(TAG, "Uploading part $currentPartNum (attempt $attempt/${R2UploadConfig.MAX_RETRIES}, $partLength bytes)")
                    val etag = uploadPartDirectToR2(
                        context = context,
                        uri = uri,
                        partUrl = part.url,
                        partNumber = currentPartNum,
                        startByte = startByte,
                        partLength = partLength
                    )
                    etags.add(PartETag(currentPartNum, etag))
                    partSuccess = true
                    Log.d(TAG, "Part $currentPartNum uploaded successfully (ETag: $etag)")
                    break
                } catch (e: Exception) {
                    lastPartError = e
                    Log.w(TAG, "Part $currentPartNum attempt $attempt failed: ${e.message}")
                    if (attempt < R2UploadConfig.MAX_RETRIES) {
                        val delayMs = RETRY_DELAYS_MS.getOrElse(attempt - 1) { 16000L }
                        onProgress?.invoke(
                            currentProgressPct,
                            "Part $currentPartNum failed (retry $attempt/${R2UploadConfig.MAX_RETRIES} in ${delayMs / 1000}s)..."
                        )
                        delay(delayMs)
                    }
                }
            }

            if (!partSuccess) {
                // If all retries fail, call POST /abort
                Log.e(TAG, "Part $currentPartNum failed after ${R2UploadConfig.MAX_RETRIES} attempts. Calling /abort...")
                onProgress?.invoke(
                    currentProgressPct,
                    "Upload failed on part $currentPartNum. Aborting upload session..."
                )
                abortMultipartUpload(key, uploadId)
                throw IOException(
                    "Upload failed on part $currentPartNum after ${R2UploadConfig.MAX_RETRIES} retries: ${lastPartError?.message}",
                    lastPartError
                )
            }

            val finishedPartPct = (((index + 1).toFloat() / totalParts) * 100).toInt().coerceIn(0, 100)
            onProgress?.invoke(
                finishedPartPct,
                "Part $currentPartNum of $totalParts uploaded ($finishedPartPct%)"
            )
        }

        // Step 3: POST /complete
        onProgress?.invoke(99, "Finalizing multipart upload on Render...")
        val completeResult = try {
            completeMultipartUpload(key, uploadId, etags)
        } catch (e: Exception) {
            Log.e(TAG, "Error completing multipart upload: ${e.message}", e)
            throw IOException("Render /complete failed: ${e.message}", e)
        }

        onProgress?.invoke(100, "Upload completed successfully!")
        Log.i(TAG, "Multipart upload finalized! Key: ${completeResult.key}, URL: ${completeResult.url}")
        completeResult
    }

    /**
     * Checks if the Render upload service endpoint is reachable.
     */
    suspend fun checkRenderService(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(serviceUrl)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code in listOf(404, 405)) {
                    Pair(true, "Render service reachable (HTTP ${response.code})")
                } else {
                    Pair(false, "Render service returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Render service unreachable")
        }
    }
}
