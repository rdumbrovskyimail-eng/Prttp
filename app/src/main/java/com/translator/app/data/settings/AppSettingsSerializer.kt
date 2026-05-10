package com.translator.app.data.settings

import androidx.datastore.core.Serializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class AppSettingsSerializer @Inject constructor() : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) defaultValue else json.decodeFromString(AppSettings.serializer(), text)
        } catch (e: Exception) { defaultValue }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}