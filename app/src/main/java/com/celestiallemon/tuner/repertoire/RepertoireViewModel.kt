package com.celestiallemon.tuner.repertoire

import android.app.Application
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class RepertoireUiState(
    val loaded: Boolean = false,
    val songs: List<Song> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val plan: DayPlan = DayPlan(LocalDate.now().toString(), emptyMap()),
    /** A parsed backup waiting for the user to confirm it should replace everything. */
    val pendingImport: List<Song>? = null,
) {
    fun song(id: String?) = songs.firstOrNull { it.id == id }

    /** Today's songs in level order, with whether each is done. */
    val todaySongs: List<Pair<Level, Song>>
        get() = Level.entries.mapNotNull { level -> song(plan.picks[level])?.let { level to it } }

    val doneToday: Int get() = todaySongs.count { (_, song) -> song.practicedOn(today) }
}

/** Fields the add/edit form produces. */
data class SongDraft(
    val title: String = "",
    val artist: String = "",
    val bpm: Int? = null,
    val notes: String = "",
    val link: String = "",
)

class RepertoireViewModel(application: Application) : AndroidViewModel(application) {
    private val file = AtomicFile(File(application.filesDir, "repertoire.json"))
    private val writeLock = Mutex()

    private val _state = MutableStateFlow(RepertoireUiState())
    val state: StateFlow<RepertoireUiState> = _state.asStateFlow()

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** A one-off note for the snackbar; [undo] restores what the action changed. */
    data class Message(val text: String, val undo: (() -> Unit)? = null)

    init {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                try {
                    load()
                } catch (e: Exception) {
                    // Keep the unreadable file aside instead of letting the next save overwrite it.
                    val kept = File(application.filesDir, "repertoire-unreadable-${System.currentTimeMillis()}.json")
                    runCatching { file.baseFile.copyTo(kept) }
                    _messages.send(Message("Couldn't read your saved songs. The file was kept as ${kept.name}."))
                    RepertoireData()
                }
            }
            val today = LocalDate.now()
            _state.value = RepertoireUiState(
                loaded = true,
                songs = data.songs,
                today = today,
                plan = planDay(today, data.songs, data.today),
            )
            // Roll over to a fresh practice set at midnight even if the app stays open.
            while (true) {
                val untilMidnight = Duration.between(LocalDateTime.now(), LocalDate.now().plusDays(1).atStartOfDay())
                delay(untilMidnight.toMillis() + 1_000)
                refreshDay()
            }
        }
    }

    /** Re-checks the date; call when the app comes back to the front. */
    fun refreshDay() {
        val today = LocalDate.now()
        if (!_state.value.loaded || today == _state.value.today) return
        update { it.copy(today = today) }
    }

    fun add(draft: SongDraft): String {
        val now = System.currentTimeMillis()
        val song = Song(
            id = UUID.randomUUID().toString(),
            title = draft.title.trim(),
            artist = draft.artist.trim(),
            bpm = draft.bpm,
            notes = draft.notes.trim(),
            link = draft.link.trim(),
            level = Level.L1,
            levelSince = now,
            addedAt = now,
        )
        update { it.copy(songs = it.songs + song) }
        _messages.trySend(Message("Added “${song.title}” to L1"))
        return song.id
    }

    fun edit(id: String, draft: SongDraft) = updateSong(id) {
        it.copy(
            title = draft.title.trim(),
            artist = draft.artist.trim(),
            bpm = draft.bpm,
            notes = draft.notes.trim(),
            link = draft.link.trim(),
        )
    }

    fun move(id: String, level: Level) {
        val before = _state.value.song(id) ?: return
        updateSong(id) { it.movedTo(level, System.currentTimeMillis()) }
        val verb = if (level.ordinal > before.level.ordinal) "Moved up to" else "Moved back to"
        _messages.trySend(Message("$verb ${level.label}") { updateSong(id) { before.copy(practiced = it.practiced) } })
    }

    fun delete(id: String) {
        val song = _state.value.song(id) ?: return
        val index = _state.value.songs.indexOf(song)
        update { it.copy(songs = it.songs - song) }
        _messages.trySend(
            Message("Deleted “${song.title}”") {
                update { s -> s.copy(songs = s.songs.toMutableList().apply { add(index.coerceAtMost(size), song) }) }
            },
        )
    }

    fun toggleDone(id: String) {
        val today = _state.value.today
        updateSong(id) { it.togglePracticed(today) }
    }

    /** Puts [songId] in today's slot for its level. */
    fun swap(level: Level, songId: String) =
        update { it.copy(plan = it.plan.copy(picks = it.plan.picks + (level to songId))) }

    fun export(uri: Uri) {
        val songs = _state.value.songs
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(BackupFormat.encode(songs).toByteArray())
                    }
                }
            }
            _messages.send(
                Message(if (result.isSuccess) "Backup saved · ${songs.size} ${plural(songs.size)}" else "Couldn't save the backup"),
            )
        }
    }

    /** Reads a backup and holds it in [RepertoireUiState.pendingImport] until [confirmImport]. */
    fun readImport(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    BackupFormat.decode(text)
                }
            }
            result.onSuccess { songs -> _state.update { it.copy(pendingImport = songs) } }
            result.onFailure { e ->
                _messages.send(Message((e as? IllegalArgumentException)?.message ?: "Couldn't read that file"))
            }
        }
    }

    fun confirmImport() {
        val songs = _state.value.pendingImport ?: return
        update { it.copy(songs = songs, pendingImport = null, plan = DayPlan(it.today.toString(), emptyMap())) }
        _messages.trySend(Message("Restored ${songs.size} ${plural(songs.size)}"))
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    private fun updateSong(id: String, change: (Song) -> Song) =
        update { s -> s.copy(songs = s.songs.map { if (it.id == id) change(it) else it }) }

    /** Applies [change], re-plans the day so the practice set stays valid, and saves. */
    private fun update(change: (RepertoireUiState) -> RepertoireUiState) {
        _state.update { old ->
            val new = change(old)
            new.copy(plan = planDay(new.today, new.songs, new.plan))
        }
        persist()
    }

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            writeLock.withLock {
                // Read the state inside the lock so the last write always holds the newest data.
                val snapshot = _state.value
                val data = RepertoireData(songs = snapshot.songs, today = snapshot.plan)
                var out: java.io.FileOutputStream? = null
                try {
                    out = file.startWrite()
                    out.write(BackupFormat.json.encodeToString(RepertoireData.serializer(), data).toByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    out?.let(file::failWrite)
                    // Every save writes the whole repertoire, so the next change retries this one.
                    _messages.send(Message("Couldn't save your changes. Check the phone's free storage."))
                }
            }
        }
    }

    /** Reads the saved repertoire. A missing file is an empty repertoire; a broken one throws. */
    private fun load(): RepertoireData {
        if (!file.baseFile.exists()) return RepertoireData()
        val data = BackupFormat.json.decodeFromString(RepertoireData.serializer(), file.readFully().decodeToString())
        return data.copy(songs = data.songs.map(BackupFormat::normalized))
    }

    private fun plural(n: Int) = if (n == 1) "song" else "songs"
}
