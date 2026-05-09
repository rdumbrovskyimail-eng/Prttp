// Путь: app/src/main/java/com/learnde/app/data/translator/GeminiTranslationClient.kt
package com.learnde.app.data.translator

import com.learnde.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

@Singleton
class GeminiTranslationClient @Inject constructor(
    private val logger: AppLogger,
) {
    companion object {
        // Ультрабыстрая модель чисто для корректировки готового текста (200-400 мс)
        private const val MODEL = "gemini-3.1-flash-lite-preview"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)     // Требуем скорости от легковесных задач!
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * PHASE 2: Мгновенная интеллектуальная сверка драфтов (REST)
     * Входные тексты взяты прямо с оперативной памяти STT и Socket_Response. 
     * Больше никакого Base64 AudioUpload. Трафик запроса ~400 байт.
     */
    suspend fun refine(
        voskRawOriginal: String,
        socketInputTranscript: String, 
        liveTranslationRaw: String,
        apiKey: String
    ): TranslationResult = withContext(Dispatchers.IO) {

        val requestStartTime = System.currentTimeMillis()

        // Создаем системный жесткий JSON-указатель
        val systemInstructionStr = """
            Ты ультрабыстрый авто-редактор системы "Синхронного Переводчика" (Русский ↔ Немецкий). 
            Сравни два сырых варианта фразы человека и сырой текст-перевод (Ответ нейросети). 
            Удали мусор (эканья, обрывки). Собери одну безупречную оригинальную фразу и один правильный литературный перевод этой фразы с пунктуацией и заглавными буквами. 
            Если STT услышал язык неверно - переосмысли его! В JSON выведи исключительно ДВЕ готовые идеальные строки.
        """.trimIndent()

        val dataText = """
            [ИСХОДНЫЕ ДАННЫЕ]
            Вариант 1 STT (Локально-Быстрый): "$voskRawOriginal"
            Вариант 2 STT (ЛивМодель-Умный): "$socketInputTranscript"
            Перевод Нейросети на слух: "$liveTranslationRaw"
        """.trimIndent()

        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", systemInstructionStr) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", dataText) }) })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.0)      // Нам нужен 100% сухой робот-редактор, никаких стихов!
                put("responseMimeType", "application/json") // Gemini вышлет сразу идеальный спарсенный JSON словарь 
            })
        }

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            val responseText = httpClient.newCall(request).execute().body?.string() 
                ?: throw IllegalStateException("Empty Server body")

            // Извлекаем ответ. Для формата 3.1: json -> candidates -> [0] -> content -> parts -> [0] -> text
            val rootObj = json.parseToJsonElement(responseText).jsonObject
            val candidatesObj = rootObj["candidates"]?.jsonArray?.get(0)?.jsonObject
            val candidateText = candidatesObj?.get("content")?.jsonObject?.get("parts")
                ?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

            // Напрямую читаем "очищенный" JSON, созданный Gemini Flash
            val parsedRefinement = json.parseToJsonElement(candidateText).jsonObject
            
            // Запасная магия, если Gemini назвал ключи по своему 
            val refRu = (parsedRefinement.values.firstOrNull()?.jsonPrimitive?.content) ?: voskRawOriginal
            val refDe = (parsedRefinement.values.drop(1).firstOrNull()?.jsonPrimitive?.content) ?: liveTranslationRaw
            
            logger.d("Refinement Check ✓ (${System.currentTimeMillis() - requestStartTime}ms): '$refRu' & '$refDe'")

            TranslationResult(original = refRu, translation = refDe)
            
        } catch (e: Exception) {
            logger.e("GeminiTranslate Refiner ✗ fallback ($requestStartTime): ${e.message}")
            // Fallback до драфтов без обрушения - Галочки станут Зелеными из имеющегося 
            TranslationResult(original = voskRawOriginal.ifEmpty { socketInputTranscript }, translation = liveTranslationRaw)
        }
    }
}

data class TranslationResult(
    val original: String,
    val translation: String,
)