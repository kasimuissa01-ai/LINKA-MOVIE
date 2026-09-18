package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class TranslationResult {
    data class Success(val translatedText: String, val isFromCache: Boolean = false) : TranslationResult()
    data class Error(val message: String, val fallbackText: String? = null) : TranslationResult()
}

/**
 * Intelligent Swahili Translation Manager.
 *
 * Provides Instagram-style on-demand translation for movie descriptions.
 * - Uses MyMemory Neural Machine Translation API (en -> sw)
 * - Automatic segment chunking for long synopses
 * - Offline pre-seeded translations for catalog movies
 * - Permanent SharedPreferences caching so translations only load once
 */
object TranslationManager {
    private const val TAG = "TranslationManager"
    private const val PREFS_NAME = "swahili_translations_cache"
    private const val MAX_CHUNK_LENGTH = 380

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // In-memory fast cache
    private val memoryCache = mutableMapOf<String, String>()

    // High quality offline fallback translations for popular catalog items
    private val preloadedTranslations = mapOf(
        "sniper fierce battle" to "Kikosi maalum cha usalama kinafichua mtandao wa magendo ya binadamu wakati wa kuwaokoa mateka, kisha kinateuliwa kusindikiza afisa wa siri ili kukusanya ushahidi dhidi ya genge hatari la wahalifu. Wakikabiliwa na ufisadi serikalini na mashambulizi ya ghafla, kikosi lazima kipambane kwenye mapigano makali na kumlinda shahidi wao huku wakifichua operesheni za genge hilo.",
        "speed demon" to "Padre Novak na Sista Lu wanapopanda treni ya mwendo kasi kutoka Montreal kuelekea Jiji la New York, wanakumbana na janga kuu baada ya pepo Asmodeus kumvaa abiria na kudhibiti treni nzima. Treni inapogeuka kuwa mtego wa mauti unaokimbia, abiria waliopagawa wanakuwa wakali na idadi ya vifo inaongezeka. Sista Lu—mtawa aliyepoteza imani yake—lazima ashinde shaka zake na afanye ibada ya kwanza kabisa ya kutoa pepo kuwahi kufanywa na mtawa ili kumkomesha pepo huyo na kuokoa kila mtu aliyemo.",
        "the furious" to "Binti wa Wang Wei anatekwa nyara na mtandao wa wahalifu, lakini polisi mafisadi wanakataa kumsaidia. Anaanza msako mkali wa kumtafuta na kuungana na Navin, mwandishi wa habari ambaye mkewe pia ametoweka kwa njia ya ajabu. Kwa pamoja, wanakabiliana na genge katili linalojihusisha na utekaji nyara na biashara haramu ya binadamu, na kupelekea mapigano makali ya karate na misheni ya uokoaji ya kulipiza kisasi."
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if translation is already cached.
     */
    fun getCachedTranslation(context: Context, cacheKey: String): String? {
        memoryCache[cacheKey]?.let { return it }
        val diskCached = getPrefs(context).getString(cacheKey, null)
        if (!diskCached.isNullOrBlank()) {
            memoryCache[cacheKey] = diskCached
            return diskCached
        }
        return null
    }

    /**
     * Translates English text to Swahili (Kiswahili).
     */
    suspend fun translateToSwahili(
        context: Context,
        text: String,
        movieTitle: String = ""
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext TranslationResult.Success("")
        }

        val cacheKey = trimmed.hashCode().toString()

        // 1. Check memory / disk cache
        getCachedTranslation(context, cacheKey)?.let {
            return@withContext TranslationResult.Success(it, isFromCache = true)
        }

        // 2. Check preloaded catalog translations by title
        val normalizedTitle = movieTitle.lowercase().trim()
        for ((titlePattern, translation) in preloadedTranslations) {
            if (normalizedTitle.contains(titlePattern) || titlePattern.contains(normalizedTitle)) {
                cacheTranslation(context, cacheKey, translation)
                return@withContext TranslationResult.Success(translation, isFromCache = true)
            }
        }

        // 3. Fetch from MyMemory translation API
        try {
            val chunks = splitIntoChunks(trimmed, MAX_CHUNK_LENGTH)
            val translatedChunks = mutableListOf<String>()

            for (chunk in chunks) {
                val encodedQuery = URLEncoder.encode(chunk, "UTF-8")
                val url = "https://api.mymemory.translated.net/get?q=$encodedQuery&langpair=en|sw"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MovieRoom-Android/1.0")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val responseData = json.optJSONObject("responseData")
                    val translatedText = responseData?.optString("translatedText", "").orEmpty()

                    if (translatedText.isNotBlank()) {
                        // Decode common HTML entities that might be returned by translation engine
                        val cleanChunk = cleanHtmlEntities(translatedText)
                        translatedChunks.add(cleanChunk)
                    } else {
                        translatedChunks.add(chunk) // Fallback to original segment
                    }
                }
            }

            val finalTranslation = translatedChunks.joinToString(" ").trim()
            if (finalTranslation.isNotBlank()) {
                cacheTranslation(context, cacheKey, finalTranslation)
                return@withContext TranslationResult.Success(finalTranslation)
            } else {
                return@withContext TranslationResult.Error("Haikuweza kutafsiri kwa sasa.", trimmed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation error: ${e.message}", e)

            // If network failed, check if title matches any preloaded translation
            val fallback = preloadedTranslations.entries.firstOrNull {
                normalizedTitle.contains(it.key) || it.key.contains(normalizedTitle)
            }?.value

            if (fallback != null) {
                cacheTranslation(context, cacheKey, fallback)
                return@withContext TranslationResult.Success(fallback, isFromCache = true)
            }

            return@withContext TranslationResult.Error(
                message = "Haikuweza kuunganisha na huduma ya tafsiri: ${e.message}",
                fallbackText = trimmed
            )
        }
    }

    private fun cacheTranslation(context: Context, cacheKey: String, translated: String) {
        memoryCache[cacheKey] = translated
        try {
            getPrefs(context).edit().putString(cacheKey, translated).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist translation: ${e.message}")
        }
    }

    private fun splitIntoChunks(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val result = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        var current = StringBuilder()

        for (sentence in sentences) {
            if (current.length + sentence.length + 1 <= maxLen) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                if (sentence.length > maxLen) {
                    // Force chunk long sentence
                    var i = 0
                    while (i < sentence.length) {
                        val end = minOf(i + maxLen, sentence.length)
                        result.add(sentence.substring(i, end))
                        i = end
                    }
                } else {
                    current.append(sentence)
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun cleanHtmlEntities(text: String): String {
        return text
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
