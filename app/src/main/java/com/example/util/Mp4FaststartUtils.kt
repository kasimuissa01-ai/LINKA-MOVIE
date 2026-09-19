package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Faststart (Web-Optimized MP4) inspection and status utility.
 *
 * MP4 files consist of box/atom structures:
 * - 'ftyp': File type identification
 * - 'moov': Movie header / index metadata (table of contents: tracks, codecs, timing)
 * - 'mdat': Movie data (actual audio and video bitstream)
 *
 * If 'moov' precedes 'mdat', the video is "Faststart / Web Optimized".
 * Streaming players (ExoPlayer, web browsers, iOS AVPlayer) can begin instant playback
 * without having to seek or read through gigabytes of raw data.
 */
object Mp4FaststartUtils {
    private const val TAG = "Mp4FaststartUtils"

    enum class FaststartStatus {
        OPTIMIZED,      // moov atom is located before mdat: video begins playing instantaneously
        NON_OPTIMIZED,  // moov atom is located after mdat or at end of file: player must fetch end-of-file
        UNKNOWN,        // Not an MP4 container or could not be determined
        CHECKING
    }

    data class Mp4InspectionResult(
        val status: FaststartStatus,
        val details: String,
        val moovOffset: Long? = null,
        val mdatOffset: Long? = null
    )

    /**
     * Inspects the first chunk of an MP4 stream (up to 4MB) to determine
     * whether the 'moov' atom is placed before the 'mdat' atom.
     * Operates non-destructively and synchronously on IO dispatcher.
     */
    suspend fun inspectUri(context: Context, uri: Uri): Mp4InspectionResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                return@withContext inspectStream(stream)
            } ?: Mp4InspectionResult(FaststartStatus.UNKNOWN, "Unable to open input stream")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect MP4 for faststart: ${e.message}")
            Mp4InspectionResult(FaststartStatus.UNKNOWN, "Inspection failed: ${e.localizedMessage}")
        }
    }

    fun inspectStream(stream: InputStream): Mp4InspectionResult {
        var currentOffset = 0L
        val maxScanBytes = 8 * 1024 * 1024L // Scan up to first 8MB
        var foundMoov = false
        var foundMdat = false
        var moovPos: Long? = null
        var mdatPos: Long? = null

        val headerBuffer = ByteArray(8)

        try {
            while (currentOffset < maxScanBytes) {
                var bytesRead = 0
                while (bytesRead < 8) {
                    val r = stream.read(headerBuffer, bytesRead, 8 - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }
                if (bytesRead < 8) break

                val bb = ByteBuffer.wrap(headerBuffer).order(ByteOrder.BIG_ENDIAN)
                var boxSize = bb.int.toLong() and 0xFFFFFFFFL
                val boxType = String(headerBuffer, 4, 4, Charsets.ISO_8859_1)

                var headerSize = 8L
                if (boxSize == 1L) {
                    // 64-bit extended size
                    val extBuffer = ByteArray(8)
                    var extRead = 0
                    while (extRead < 8) {
                        val r = stream.read(extBuffer, extRead, 8 - extRead)
                        if (r == -1) break
                        extRead += r
                    }
                    if (extRead < 8) break
                    val extBb = ByteBuffer.wrap(extBuffer).order(ByteOrder.BIG_ENDIAN)
                    boxSize = extBb.long
                    headerSize += 8
                } else if (boxSize == 0L) {
                    // Box extends to end of file
                    boxSize = maxScanBytes - currentOffset
                }

                if (boxType == "moov") {
                    foundMoov = true
                    moovPos = currentOffset
                    // If moov is discovered before mdat, the file is 100% Web Optimized!
                    if (!foundMdat) {
                        return Mp4InspectionResult(
                            status = FaststartStatus.OPTIMIZED,
                            details = "Faststart (moov atom at byte $moovPos before video data). Starts playing immediately.",
                            moovOffset = moovPos,
                            mdatOffset = null
                        )
                    }
                } else if (boxType == "mdat") {
                    foundMdat = true
                    mdatPos = currentOffset
                    if (!foundMoov) {
                        // mdat was encountered first! The moov atom is located at the back of the file.
                        return Mp4InspectionResult(
                            status = FaststartStatus.NON_OPTIMIZED,
                            details = "Index is at end of file (mdat at byte $mdatPos). Player uses smart Range requests.",
                            moovOffset = null,
                            mdatOffset = mdatPos
                        )
                    }
                }

                val payloadToSkip = (boxSize - headerSize).coerceAtLeast(0L)
                val skipped = skipFully(stream, payloadToSkip)
                currentOffset += headerSize + skipped

                if (skipped < payloadToSkip) {
                    break
                }
            }

            return if (foundMoov && !foundMdat) {
                Mp4InspectionResult(
                    status = FaststartStatus.OPTIMIZED,
                    details = "Web-Optimized Faststart container.",
                    moovOffset = moovPos
                )
            } else if (foundMdat && !foundMoov) {
                Mp4InspectionResult(
                    status = FaststartStatus.NON_OPTIMIZED,
                    details = "Non-optimized MP4 (mdat at byte $mdatPos). Handled automatically by player range streaming.",
                    mdatOffset = mdatPos
                )
            } else {
                Mp4InspectionResult(
                    status = FaststartStatus.UNKNOWN,
                    details = "Standard media file."
                )
            }
        } catch (e: Exception) {
            return Mp4InspectionResult(FaststartStatus.UNKNOWN, "Stream parse error: ${e.message}")
        }
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long): Long {
        var remaining = bytesToSkip
        val tempBuf = ByteArray(8192)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val toRead = remaining.coerceAtMost(tempBuf.size.toLong()).toInt()
                val read = stream.read(tempBuf, 0, toRead)
                if (read == -1) break
                remaining -= read
            }
        }
        return bytesToSkip - remaining
    }
}
