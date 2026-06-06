package com.prttp.app.data

import android.content.Context
import com.prttp.app.data.crypto.KeystoreCrypto
import com.prttp.app.domain.model.ClinicalFlag
import com.prttp.app.domain.model.ConversationMessage
import com.prttp.app.domain.model.Homework
import com.prttp.app.therapy.ImageTheme
import com.prttp.app.domain.model.JournalEntry
import com.prttp.app.domain.model.MoodLog
import com.prttp.app.domain.model.PatientProfile
import com.prttp.app.domain.model.ProfileFact
import com.prttp.app.domain.model.ResearchNote
import com.prttp.app.domain.model.RiskLevel
import com.prttp.app.domain.model.SessionNote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
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

    private val researchFile: File get() = File(context.filesDir, "research.bin")
    private val _research = MutableStateFlow<List<ResearchNote>>(emptyList())
    val research: StateFlow<List<ResearchNote>> = _research.asStateFlow()

    // Триггер ожидания первичной расшифровки
    private val isLoaded = CompletableDeferred<Unit>()

    init {
        ioScope.launch {
            load()
            isLoaded.complete(Unit) // Сигнализируем о готовности локальной памяти
        }
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        _profile.value = readProfile()
        _journal.value = readJournal()
        _research.value = readResearch()
    }

    suspend fun getProfile(): PatientProfile {
        isLoaded.await()
        return _profile.value
    }

    suspend fun getJournal(): List<JournalEntry> {
        isLoaded.await()
        return _journal.value
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

    private suspend fun mutateProfile(block: (PatientProfile) -> PatientProfile) =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                isLoaded.await() // Блокируем гонки данных
                val updated = block(_profile.value).copy(updatedAt = System.currentTimeMillis())
                persistProfile(updated)
                _profile.value = updated
            }
        }

    private suspend fun mutateJournal(block: (List<JournalEntry>) -> List<JournalEntry>) =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                isLoaded.await() // Блокируем гонки данных
                val updated = block(_journal.value)
                persistJournal(updated)
                _journal.value = updated
            }
        }

    suspend fun upsertFact(category: String, key: String, value: String, confidence: Float) =
        mutateProfile { p ->
            val others = p.facts.filterNot { it.category == category && it.key.equals(key, true) }
            p.copy(facts = others + ProfileFact(category, key, value, confidence))
        }

    suspend fun setDisplayName(name: String) = mutateProfile { it.copy(displayName = name) }

    suspend fun setImageTheme(theme: ImageTheme) = mutateProfile { p ->
        p.copy(imageTheme = theme)
    }

    fun getImageTheme(): ImageTheme = _profile.value.imageTheme

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
        p.copy(flags = p.flags.map {
            if (it.id == id) it.copy(active = false, resolvedAt = System.currentTimeMillis()) else it
        })
    }

    suspend fun startNewSession(): String {
        val newId = UUID.randomUUID().toString()
        mutateProfile { p ->
            p.copy(
                sessionCount = p.sessionCount + 1,
                currentSessionId = newId
            )
        }
        return newId
    }

    fun getSessionCount(): Int = _profile.value.sessionCount
    fun getCurrentSessionId(): String = _profile.value.currentSessionId

    suspend fun addMessage(role: String, text: String) = mutateProfile { p ->
        val msg = ConversationMessage(
            role = role,
            text = text,
            sessionId = p.currentSessionId,
            timestamp = System.currentTimeMillis()
        )
        p.copy(messages = (p.messages + msg).takeLast(200))
    }

    suspend fun addJournalEntry(text: String, mood: Int?, tags: List<String>) = mutateJournal { list ->
        list + JournalEntry(UUID.randomUUID().toString(), text, mood, tags)
    }

    suspend fun deleteJournalEntry(id: String) = mutateJournal { list ->
        list.filterNot { it.id == id }
    }

    suspend fun getResearchNotes(): List<ResearchNote> { isLoaded.await(); return _research.value }

    private fun readResearch(): List<ResearchNote> {
        if (!researchFile.exists()) return emptyList()
        val blob = runCatching { researchFile.readBytes() }.getOrNull() ?: return emptyList()
        val plain = KeystoreCrypto.decrypt(KEY_RESEARCH, blob) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ResearchNote.serializer()), plain.decodeToString())
        }.getOrDefault(emptyList())
    }

    private fun persistResearch(list: List<ResearchNote>) {
        val bytes = json.encodeToString(ListSerializer(ResearchNote.serializer()), list).encodeToByteArray()
        researchFile.writeBytes(KeystoreCrypto.encrypt(KEY_RESEARCH, bytes))
    }

    private suspend fun mutateResearch(block: (List<ResearchNote>) -> List<ResearchNote>) =
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                isLoaded.await()
                val updated = block(_research.value)
                persistResearch(updated)
                _research.value = updated
            }
        }

    suspend fun addResearchNote(query: String, topic: String, summary: String, sources: List<String>) =
        mutateResearch { list ->
            (list + ResearchNote(
                id = UUID.randomUUID().toString(),
                query = query, topic = topic, summary = summary, sources = sources
            )).takeLast(200)
        }

    suspend fun wipeEverything() = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { profileFile.delete() }
            runCatching { journalFile.delete() }
            runCatching { researchFile.delete() }
            KeystoreCrypto.deleteKey(KEY_PROFILE)
            KeystoreCrypto.deleteKey(KEY_JOURNAL)
            KeystoreCrypto.deleteKey(KEY_RESEARCH)
            _profile.value = PatientProfile()
            _journal.value = emptyList()
            _research.value = emptyList()
        }
    }

    companion object {
        private const val KEY_PROFILE = "patient_profile_key_v1"
        private const val KEY_JOURNAL = "patient_journal_key_v1"
        private const val KEY_RESEARCH = "patient_research_key_v1"
    }
}