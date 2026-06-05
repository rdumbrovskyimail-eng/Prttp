package com.prttp.app.data

import com.prttp.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TherapyImage(
    val url: String,
    val thumbUrl: String,
    val query: String,
    val caption: String = ""
)

@Singleton
class PexelsImageRepository @Inject constructor(
    private val logger: AppLogger
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // LRU-кэш: query → image, максимум 30 записей
    private val cache = object : LinkedHashMap<String, TherapyImage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, TherapyImage>) = size > 30
    }

    suspend fun fetchImage(
        apiKey: String,
        query: String,
        caption: String = "",
        theme: ImageTheme = ImageTheme.NATURE
    ): TherapyImage? {
        if (apiKey.isBlank()) return null
        val enrichedQuery = ImageTheme.enrichQuery(query, theme)

        cache[enrichedQuery]?.let { return it.copy(caption = caption) }

        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(enrichedQuery, "UTF-8")
                val request = Request.Builder()
                    .url("https://api.pexels.com/v1/search?query=$encoded&per_page=5&orientation=landscape")
                    .header("Authorization", apiKey)
                    .build()

                val body = httpClient.newCall(request).execute().use { it.body?.string() }
                    ?: return@runCatching null

                val resp = json.decodeFromString(PexelsResponse.serializer(), body)
                val photo = resp.photos.randomOrNull() ?: return@runCatching null

                TherapyImage(
                    url      = photo.src.large.ifBlank { photo.src.medium },
                    thumbUrl = photo.src.medium,
                    query    = enrichedQuery,
                    caption  = caption
                ).also { cache[enrichedQuery] = it }

            }.onFailure { logger.e("Pexels error: ${it.message}", it) }.getOrNull()
        }
    }

    @Serializable private data class PexelsResponse(val photos: List<PexelsPhoto> = emptyList())
    @Serializable private data class PexelsPhoto(val src: PexelsSrc)
    @Serializable private data class PexelsSrc(val medium: String = "", val large: String = "")
}