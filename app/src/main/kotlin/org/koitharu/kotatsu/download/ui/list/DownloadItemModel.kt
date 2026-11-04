package org.koitharu.kotatsu.download.ui.list

import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.work.WorkInfo
import org.koitharu.kotatsu.core.util.ext.getQuantityStringSafe
import kotlinx.coroutines.flow.StateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.download.ui.list.chapters.DownloadChapter
import org.koitharu.kotatsu.list.ui.ListModelDiffCallback
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.parsers.model.Manga
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import androidx.appcompat.R as appcompatR

data class DownloadItemModel(
	val id: UUID,
	val workState: WorkInfo.State,
	val isIndeterminate: Boolean,
	val isPaused: Boolean,
	val manga: Manga?,
	val error: String?,
	val max: Int,
	val progress: Int,
	val eta: Long,
	val isStuck: Boolean,
	val speed: Long,
	val isQueued: Boolean,
	val downloadedBytes: Long,
	val estimatedBytes: Long,
	val totalChapters: Int,
	val chapterProgress: Float,
	val timestamp: Instant,
	val chaptersDownloaded: Int,
	val isExpanded: Boolean,
	val chapters: StateFlow<List<DownloadChapter>?>,
) : ListModel, Comparable<DownloadItemModel> {

	val percent: Float
		get() = if (max > 0) progress / max.toFloat() else 0f

	val hasEta: Boolean
		get() = workState == WorkInfo.State.RUNNING && !isPaused && eta > 0L

	val canPause: Boolean
		get() = workState == WorkInfo.State.RUNNING && !isPaused && error == null && !isQueued

	val canReorder: Boolean
		get() = workState == WorkInfo.State.RUNNING && isQueued

	val canResume: Boolean
		get() = workState == WorkInfo.State.RUNNING && isPaused

	val canRetry: Boolean
		get() = workState == WorkInfo.State.FAILED

	fun getEtaString(): CharSequence? = if (hasEta) {
		DateUtils.getRelativeTimeSpanString(
			eta,
			System.currentTimeMillis(),
			DateUtils.SECOND_IN_MILLIS,
		)
	} else {
		null
	}

	/** Live speed and time left, for example "2.3 MB/s · in 4 minutes". */
	fun getDetails(context: Context): CharSequence? {
		val speedText = if (speed > 0L) {
			context.getString(R.string.download_speed_format, Formatter.formatFileSize(context, speed))
		} else {
			null
		}
		val eta = getEtaString()
		return when {
			speedText != null && eta != null -> "$speedText · $eta"
			else -> speedText ?: eta
		}
	}

	val isFinished: Boolean
		get() = workState.isFinished

	/** One line in the 1DM style, for example "12% | 5.3 MB / ~44 MB | 574 kB/s | 16s". */
	fun getInfoLine(context: Context): String {
		val percentText = "${(percent * 100f).roundToInt()}%"
		val parts = ArrayList<String>(4)
		when (workState) {
			WorkInfo.State.ENQUEUED,
			WorkInfo.State.BLOCKED -> parts += context.getString(R.string.queued)

			WorkInfo.State.RUNNING -> when {
				isQueued -> parts += context.getString(R.string.download_waiting)
				isIndeterminate -> parts += context.getString(R.string.preparing_)
				isPaused -> {
					parts += context.getString(R.string.paused)
					parts += percentText
					sizeText(context)?.let(parts::add)
				}

				else -> {
					parts += percentText
					sizeText(context)?.let(parts::add)
					if (speed > 0L) {
						parts += context.getString(R.string.download_speed_format, Formatter.formatFileSize(context, speed))
					}
					if (isStuck) {
						parts += context.getString(R.string.stuck)
					} else {
						timeLeftText()?.let(parts::add)
					}
				}
			}

			WorkInfo.State.SUCCEEDED -> {
				parts += context.getString(R.string.download_complete)
				if (chaptersDownloaded > 0) {
					parts += context.resources.getQuantityStringSafe(R.plurals.chapters, chaptersDownloaded, chaptersDownloaded)
				}
				if (downloadedBytes > 0L) {
					parts += Formatter.formatFileSize(context, downloadedBytes)
				}
			}

			WorkInfo.State.FAILED -> {
				parts += context.getString(R.string.error_occurred)
				if (downloadedBytes > 0L) {
					parts += Formatter.formatFileSize(context, downloadedBytes)
				}
			}

			WorkInfo.State.CANCELLED -> parts += context.getString(R.string.canceled)
		}
		return parts.joinToString(" | ")
	}

	private fun sizeText(context: Context): String? = when {
		downloadedBytes <= 0L -> null
		estimatedBytes > downloadedBytes -> context.getString(
			R.string.download_size_estimate,
			Formatter.formatFileSize(context, downloadedBytes),
			Formatter.formatFileSize(context, estimatedBytes),
		)

		else -> Formatter.formatFileSize(context, downloadedBytes)
	}

	private fun timeLeftText(): String? {
		if (eta <= 0L) {
			return null
		}
		val seconds = ((eta - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
		return when {
			seconds < 60L -> "${seconds}s"
			seconds < 3600L -> "${seconds / 60}m%02ds".format(seconds % 60)
			else -> "${seconds / 3600}h%02dm".format(seconds % 3600 / 60)
		}
	}

	fun getErrorMessage(context: Context): CharSequence? = if (error != null) {
		buildSpannedString {
			bold {
				color(context.getThemeColor(appcompatR.attr.colorError, Color.RED)) {
					append(error)
				}
			}
		}
	} else {
		null
	}

	override fun compareTo(other: DownloadItemModel): Int {
		return timestamp compareTo other.timestamp
	}

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is DownloadItemModel && other.id == id
	}

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is DownloadItemModel -> super.getChangePayload(previousState)
		workState != previousState.workState -> null
		isExpanded != previousState.isExpanded -> ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED
		else -> ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
	}
}
