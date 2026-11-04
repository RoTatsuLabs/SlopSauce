package org.koitharu.kotatsu.download.domain

import androidx.work.Data
import org.koitharu.kotatsu.list.domain.ReadingProgress.Companion.PROGRESS_NONE
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.parsers.model.Manga
import java.time.Instant

data class DownloadState(
	val manga: Manga,
	val isIndeterminate: Boolean,
	val isPaused: Boolean = false,
	val isStopped: Boolean = false,
	val error: Throwable? = null,
	val errorMessage: String? = null,
	val totalChapters: Int = 0,
	val currentChapter: Int = 0,
	val totalPages: Int = 0,
	val currentPage: Int = 0,
	val eta: Long = -1L,
	val isStuck: Boolean = false,
	val speed: Long = 0L,
	val isQueued: Boolean = false,
	val downloadedBytes: Long = 0L,
	val localManga: LocalManga? = null,
	val downloadedChapters: Int = 0,
	val timestamp: Long = System.currentTimeMillis(),
) {

	val max: Int = totalChapters * totalPages

	val progress: Int = totalPages * currentChapter + currentPage + 1

	/** Total size guessed from the average size of the pages saved so far; 0 while unknown. */
	val estimatedBytes: Long = if (downloadedBytes > 0L && progress > 0 && max > 0) {
		maxOf(downloadedBytes, downloadedBytes * max / progress)
	} else {
		0L
	}

	/** How far the chapter being downloaded is, from 0 to 1. */
	val chapterProgress: Float = if (totalPages > 0) (currentPage + 1f) / totalPages else 0f

	val percent: Float = if (max > 0) progress.toFloat() / max else PROGRESS_NONE

	val isFinalState: Boolean
		get() = localManga != null || (error != null && !isPaused)

	val isParticularProgress: Boolean
		get() = localManga == null && error == null && !isPaused && !isStopped && max > 0 && !isIndeterminate

	fun toWorkData() = Data.Builder()
		.putLong(DATA_MANGA_ID, manga.id)
		.putInt(DATA_MAX, max)
		.putInt(DATA_PROGRESS, progress)
		.putLong(DATA_ETA, eta)
		.putBoolean(DATA_STUCK, isStuck)
		.putLong(DATA_TIMESTAMP, timestamp)
		.putString(DATA_ERROR, errorMessage)
		.putInt(DATA_CHAPTERS, downloadedChapters)
		.putBoolean(DATA_INDETERMINATE, isIndeterminate)
		.putBoolean(DATA_PAUSED, isPaused)
		.putLong(DATA_SPEED, speed)
		.putBoolean(DATA_QUEUED, isQueued)
		.putLong(DATA_BYTES, downloadedBytes)
		.putLong(DATA_TOTAL_BYTES, estimatedBytes)
		.putInt(DATA_TOTAL_CHAPTERS, totalChapters)
		.putFloat(DATA_CHAPTER_PROGRESS, chapterProgress)
		.build()

	companion object {

		private const val DATA_MANGA_ID = "manga_id"
		private const val DATA_MAX = "max"
		private const val DATA_PROGRESS = "progress"
		private const val DATA_CHAPTERS = "chapter_cnt"
		private const val DATA_ETA = "eta"
		private const val DATA_STUCK = "stuck"
		const val DATA_TIMESTAMP = "timestamp"
		private const val DATA_ERROR = "error"
		private const val DATA_INDETERMINATE = "indeterminate"
		private const val DATA_PAUSED = "paused"
		private const val DATA_SPEED = "speed"
		private const val DATA_QUEUED = "queued"
		private const val DATA_BYTES = "bytes"
		private const val DATA_TOTAL_BYTES = "bytes_total"
		private const val DATA_TOTAL_CHAPTERS = "chapters_total"
		private const val DATA_CHAPTER_PROGRESS = "chapter_progress"

		fun getMangaId(data: Data): Long = data.getLong(DATA_MANGA_ID, 0L)

		fun isIndeterminate(data: Data): Boolean = data.getBoolean(DATA_INDETERMINATE, false)

		fun isPaused(data: Data): Boolean = data.getBoolean(DATA_PAUSED, false)

		fun getSpeed(data: Data): Long = data.getLong(DATA_SPEED, 0L)

		fun isQueued(data: Data): Boolean = data.getBoolean(DATA_QUEUED, false)

		fun getDownloadedBytes(data: Data): Long = data.getLong(DATA_BYTES, 0L)

		fun getEstimatedBytes(data: Data): Long = data.getLong(DATA_TOTAL_BYTES, 0L)

		fun getTotalChapters(data: Data): Int = data.getInt(DATA_TOTAL_CHAPTERS, 0)

		fun getChapterProgress(data: Data): Float = data.getFloat(DATA_CHAPTER_PROGRESS, 0f)

		fun getMax(data: Data): Int = data.getInt(DATA_MAX, 0)

		fun getError(data: Data): String? = data.getString(DATA_ERROR)

		fun getProgress(data: Data): Int = data.getInt(DATA_PROGRESS, 0)

		fun getEta(data: Data): Long = data.getLong(DATA_ETA, -1L)

		fun isStuck(data: Data): Boolean = data.getBoolean(DATA_STUCK, false)

		fun getTimestamp(data: Data): Instant = Instant.ofEpochMilli(data.getLong(DATA_TIMESTAMP, 0L))

		fun getDownloadedChapters(data: Data): Int = data.getInt(DATA_CHAPTERS, 0)
	}
}
