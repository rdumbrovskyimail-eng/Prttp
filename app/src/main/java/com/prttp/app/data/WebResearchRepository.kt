package com.prttp.app.data

import com.prttp.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ResearchResult(val summary: String, val sources: List<String>)

@Singleton
class WebResearchRepository @Inject constructor(
    private val logger: AppLogger
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        const val GROUNDING_MODEL = "gemini-3.5-flash"
    }

    suspend fun research(apiKey: String, query: String, model: String = GROUNDING_MODEL): ResearchResult? {
        if (apiKey.isBlank() || query.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val instruction =
                    "Ты клинический ресёрч-ассистент. Ответь кратко (5–8 предложений), на русском, " +
                    "только проверяемые факты и конкретные практические шаги/техники, без воды. Тема: "
                val body = buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", instruction + query) })
                            })
                        })
                    })
                    put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject {}) }) })
                }
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                var raw: String? = null
                var attempt = 0
                while (attempt <= 2) {
                    val (code, body) = http.newCall(req).execute().use { it.code to it.body?.string() }
                    if (code in 200..299) { raw = body; break }
                    if (code == 429 || code == 500 || code == 503) {
                        attempt++
                        logger.w("research HTTP $code — ретрай $attempt")
                        kotlinx.coroutines.delay(900L * attempt)
                        continue
                    }
                    logger.w("research HTTP $code — отказ"); break
                }
                if (raw == null) return@runCatching null
                parse(raw)
            }.onFailure { logger.e("research error: ${it.message}", it) }.getOrNull()
        }
    }

    private fun parse(raw: String): ResearchResult? {
        val root = json.parseToJsonElement(raw).jsonObject
        val cand = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val parts = cand["content"]?.jsonObject?.get("parts")?.jsonArray ?: return null
        val text = parts.mapNotNull { runCatching { it.jsonObject["text"]?.jsonPrimitive?.content }.getOrNull() }
            .joinToString("").trim()
        if (text.isBlank()) return null
        val sources = cand["groundingMetadata"]?.jsonObject
            ?.get("groundingChunks")?.jsonArray
            ?.mapNotNull {
                runCatching {
                    val web = it.jsonObject["web"]?.jsonObject ?: return@runCatching null
                    val uri = web["uri"]?.jsonPrimitive?.content
                    val title = web["title"]?.jsonPrimitive?.content
                    when {
                        title != null && uri != null -> "$title — $uri"
                        uri != null -> uri
                        else -> null
                    }
                }.getOrNull()
            }
            ?.distinct()?.take(6) ?: emptyList()
        return ResearchResult(text, sources)
    }
}