// Путь: app/src/main/java/com/translator/app/data/PatientRepository.kt
//
// Единый источник правды для базы пациента и дневника.
// Хранит два зашифрованных файла во внутренней памяти приложения:
//   • patient.bin  — PatientProfile (факты, заметки, настроение, ДЗ, флаги)
//   • journal.bin  — список JournalEntry (пишет человек)
//
// Всё AES-256-GCM (KeystoreCrypto). Наружу отдаёт реактивные StateFlow,
// чтобы экраны и ViewModel мгновенно видели изменения, как только ассистент
// (через function-call) или пользователь что-то записал.
//
// Запись сериализуется через Mutex — конкурентные function-call'ы ассистента
// и правки из UI не затрут друг друга (read-modify-write под локом).
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.data

import android.content.Context
import com.translator.app.data.crypto.KeystoreCrypto
import com.translator.app.domain.model.ClinicalFlag
import com.translator.app.domain.model.Homework
import com.translator.app.domain.model.JournalEntry
import com.translator.app.domain.model.MoodLog
import com.translator.app.domain.model.PatientProfile
import com.translator.app.domain.model.ProfileFact
import com.translator.app.domain.model.RiskLevel
import com.translator.app.domain.model.SessionNote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val profileFile: File get() = File(context.filesDir, "patient.bin")
    private val journalFile: File get() = File(context.filesDir, "journal.bin")

    private val _profile = MutableStateFlow(PatientProfile())
    val profile: StateFlow<PatientProfile> = _profile.asStateFlow()

    private val _journal = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journal: StateFlow<List<JournalEntry>> = _journal.asStateFlow()

    init { ioScope.launch { load() } }

    private suspend fun load() = withContext(Dispatchers.IO) {
        _profile.value = readProfile()
        _journal.value = readJournal()
    }

    private fun readProfile(): PatientProfile {
        if (!profileFile.exists()) return PatientProfile()
        val blob = runCatching { profileFile.readBytes() }.getOrNull() ?: return PatientProfile()
        val plain = KeystoreCrypto.decrypt(KEY_PROFILE, blob) ?: return PatientProfile()
        return runCatching {
            json.decodeFromString(PatientProfile.serializer(), plain.decodeToString())
        }.getOrDefault(PatientProfile())
    }

    private fun readJournal(): List<JournalEntry> {
        if (!journalFile.exists()) return emptyList()
        val blob = runCatching { journalFile.readBytes() }.getOrNull() ?: return emptyList()
        val plain = KeystoreCrypto.decrypt(KEY_JOURNAL, blob) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(JournalEntry.serializer()), plain.decodeToString())
        }.getOrDefault(emptyList())
    }

    private fun persistProfile(p: PatientProfile) {
        val bytes = json.encodeToString(PatientProfile.serializer(), p).encodeToByteArray()
        profileFile.writeBytes(KeystoreCrypto.encrypt(KEY_PROFILE, bytes))
    }

    private fun persistJournal(list: List<JournalEntry>) {
        val bytes = json.encodeToString(ListSerializer(JournalEntry.serializer()), list).encodeToByteArray()
        journalFile.writeBytes(KeystoreCrypto.encrypt(KEY_JOURNAL, bytes))
    }

    /** read-modify-write профиля под локом + публикация во flow. */
    private suspend fun mutateProfile(block: (PatientProfile) -> PatientProfile) =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val updated = block(_profile.value).copy(updatedAt = System.currentTimeMillis())
                persistProfile(updated)
                _profile.value = updated
            }
        }

    private suspend fun mutateJournal(block: (List<JournalEntry>) -> List<JournalEntry>) =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val updated = block(_journal.value)
                persistJournal(updated)
                _journal.value = updated
            }
        }

    // ── Операции, которые дёргает ассистент (через TherapistToolHandler) ──────

    suspend fun upsertFact(category: String, key: String, value: String, confidence: Float) =
        mutateProfile { p ->
            val others = p.facts.filterNot { it.category == category && it.key.equals(key, true) }
            p.copy(facts = others + ProfileFact(category, key, value, confidence))
        }

    suspend fun setDisplayName(name: String) = mutateProfile { it.copy(displayName = name) }

    suspend fun addSessionNote(summary: String, observations: String, techniques: List<String>) =
        mutateProfile { p ->
            p.copy(sessionNotes = p.sessionNotes + SessionNote(
                id = UUID.randomUUID().toString(),
                summary = summary, observations = observations, techniques = techniques
            ))
        }

    suspend fun logMood(score: Int, note: String) = mutateProfile { p ->
        p.copy(moodLogs = p.moodLogs + MoodLog(score.coerceIn(1, 10), note))
    }

    suspend fun addHomework(title: String, detail: String, method: String) = mutateProfile { p ->
        p.copy(homework = p.homework + Homework(
            id = UUID.randomUUID().toString(), title = title, detail = detail, method = method
        ))
    }

    suspend fun completeHomework(id: String) = mutateProfile { p ->
        p.copy(homework = p.homework.map {
            if (it.id == id) it.copy(done = true, doneAt = System.currentTimeMillis()) else it
        })
    }

    suspend fun raiseFlag(level: RiskLevel, reason: String) = mutateProfile { p ->
        p.copy(flags = p.flags + ClinicalFlag(
            id = UUID.randomUUID().toString(), level = level, reason = reason
        ))
    }

    suspend fun clearFlag(id: String) = mutateProfile { p ->
        p.copy(flags = p.flags.map { if (it.id == id) it.copy(active = false) else it })
    }

    // ── Дневник (пишет человек из UI) ─────────────────────────────────────────

    suspend fun addJournalEntry(text: String, mood: Int?, tags: List<String>) = mutateJournal { list ->
        list + JournalEntry(UUID.randomUUID().toString(), text, mood, tags)
    }

    suspend fun deleteJournalEntry(id: String) = mutateJournal { list ->
        list.filterNot { it.id == id }
    }

    /** Полное стирание всех данных пациента (право на забвение). */
    suspend fun wipeEverything() = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { profileFile.delete() }
            runCatching { journalFile.delete() }
            KeystoreCrypto.deleteKey(KEY_PROFILE)
            KeystoreCrypto.deleteKey(KEY_JOURNAL)
            _profile.value = PatientProfile()
            _journal.value = emptyList()
        }
    }

    companion object {
        private const val KEY_PROFILE = "patient_profile_key_v1"
        private const val KEY_JOURNAL = "patient_journal_key_v1"
    }
}
