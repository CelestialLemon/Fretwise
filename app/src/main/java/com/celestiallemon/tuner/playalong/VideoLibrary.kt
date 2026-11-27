package com.celestiallemon.tuner.playalong

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.celestiallemon.tuner.repertoire.Song
import java.io.File

/**
 * Resolves local play-along `.mp4` videos from:
 * 1. A song's individually linked video URI ([Song.videoUri]),
 * 2. The app's external `videos/` folder (`/sdcard/Android/data/<pkg>/files/videos/`),
 * 3. A user-selected Storage Access Framework folder (`OpenDocumentTree`).
 */
class VideoLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("playalong", Context.MODE_PRIVATE)

    var folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER_URI, null)?.takeIf { it.isNotBlank() }?.toUri()
        set(value) {
            prefs.edit { putString(KEY_FOLDER_URI, value?.toString()) }
        }

    /** Human-readable name for the selected SAF folder, if any. */
    fun folderDisplayName(): String? {
        val uri = folderUri ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
    }

    /** Directory where `scripts/push_videos.sh` pushes play-along videos via `adb`. */
    fun appVideosDir(): File? = context.getExternalFilesDir("videos")

    /** Finds a playable video URI for [song] (and optional [chart]), or null if not yet on the device. */
    fun resolveVideo(song: Song, chart: SongChart?): Uri? {
        // 1. Direct per-song video URI.
        if (song.videoUri.isNotBlank()) {
            val uri = song.videoUri.toUri()
            if (isReadable(uri)) return uri
        }

        val expectedName = chart?.outputFilename?.takeIf { it.isNotBlank() }
        val normalizedTitle = normalize(song.title)
        val normalizedId = chart?.id?.let(::normalize)

        // 2. App external videos directory (fast, no permissions needed, works with `adb push`).
        appVideosDir()?.takeIf { it.isDirectory }?.let { dir ->
            if (expectedName != null) {
                val exact = File(dir, expectedName)
                if (exact.isFile) return Uri.fromFile(exact)
            }
            val files = dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }
            findMatchingFile(files, expectedName, normalizedTitle, normalizedId)?.let { return Uri.fromFile(it) }
        }

        // 3. User-picked Storage Access Framework folder.
        val treeUri = folderUri ?: return null
        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return null
        if (!root.canRead()) return null
        if (expectedName != null) {
            root.findFile(expectedName)?.takeIf { it.isFile }?.let { return it.uri }
        }
        val docs = root.listFiles().filter { it.isFile && (it.name?.endsWith(".mp4", ignoreCase = true) == true) }
        return findMatchingDoc(docs, expectedName, normalizedTitle, normalizedId)?.uri
    }

    private fun isReadable(uri: Uri): Boolean = runCatching {
        if (uri.scheme == "file") {
            uri.path?.let { File(it).isFile } == true
        } else {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
        }
    }.getOrDefault(false)

    private fun findMatchingFile(
        files: List<File>,
        expectedName: String?,
        normalizedTitle: String,
        normalizedId: String?,
    ): File? {
        if (expectedName != null) {
            files.firstOrNull { it.name.equals(expectedName, ignoreCase = true) }?.let { return it }
        }
        return files.firstOrNull { f ->
            val n = normalize(f.nameWithoutExtension)
            (normalizedTitle.length >= 3 && normalizedTitle in n) ||
                (normalizedId != null && normalizedId.length >= 3 && normalizedId in n)
        }
    }

    private fun findMatchingDoc(
        docs: List<DocumentFile>,
        expectedName: String?,
        normalizedTitle: String,
        normalizedId: String?,
    ): DocumentFile? {
        if (expectedName != null) {
            docs.firstOrNull { it.name.equals(expectedName, ignoreCase = true) }?.let { return it }
        }
        return docs.firstOrNull { d ->
            val raw = d.name?.substringBeforeLast('.') ?: return@firstOrNull false
            val n = normalize(raw)
            (normalizedTitle.length >= 3 && normalizedTitle in n) ||
                (normalizedId != null && normalizedId.length >= 3 && normalizedId in n)
        }
    }

    private fun normalize(text: String): String = text.lowercase().replace(Regex("[^a-z0-9]+"), "")

    companion object {
        private const val KEY_FOLDER_URI = "video_folder_uri"
    }
}
