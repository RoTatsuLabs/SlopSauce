package org.koitharu.kotatsu.download.ui.worker

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.download.domain.DownloadState
import org.koitharu.kotatsu.download.ui.list.DownloadsActivity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One notification for the whole queue, like 1DM's: total speed, how many downloads are running or
 * waiting, and Pause all / Resume all / Stop all. Each worker reports its state here.
 */
@Singleton
class DownloadSummaryNotifier @Inject constructor(
	@ApplicationContext private val context: Context,
	private val workManager: WorkManager,
) {

	private class Entry(val speed: Long, val isRunning: Boolean, val isWaiting: Boolean)

	private val entries = ConcurrentHashMap<UUID, Entry>()
	private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

	@Volatile
	private var isReceiverRegistered = false

	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent?) {
			when (intent?.action) {
				ACTION_PAUSE_ALL -> entries.keys.forEach {
					context.sendBroadcast(PausingReceiver.getPauseIntent(context, it))
				}

				ACTION_RESUME_ALL -> entries.keys.forEach {
					context.sendBroadcast(PausingReceiver.getResumeIntent(context, it))
				}

				ACTION_STOP_ALL -> workManager.cancelAllWorkByTag(DownloadWorker.TAG)
			}
		}
	}

	fun update(id: UUID, state: DownloadState) {
		if (state.isFinalState || state.isStopped) {
			remove(id)
			return
		}
		entries[id] = Entry(
			speed = state.speed,
			isRunning = !state.isPaused && !state.isQueued,
			isWaiting = state.isQueued,
		)
		refresh()
	}

	fun remove(id: UUID) {
		if (entries.remove(id) != null) {
			refresh()
		}
	}

	@SuppressLint("MissingPermission")
	private fun refresh() {
		if (entries.isEmpty()) {
			manager.cancel(NOTIFICATION_ID)
			return
		}
		registerReceiver()
		val values = entries.values
		val speed = values.filter { it.isRunning }.sumOf { it.speed }
		val running = values.count { it.isRunning }
		val waiting = values.count { it.isWaiting }
		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.general_notification)
			.setContentTitle(
				context.getString(R.string.download_speed_format, Formatter.formatFileSize(context, speed)),
			)
			.setContentText(context.getString(R.string.download_summary_line, running, waiting))
			.setContentIntent(
				PendingIntentCompat.getActivity(context, 0, Intent(context, DownloadsActivity::class.java), 0, false),
			)
			.setGroup(GROUP_ID)
			.setGroupSummary(true)
			.setOnlyAlertOnce(true)
			.setSilent(true)
			.setOngoing(true)
			.setShowWhen(false)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.addAction(0, context.getString(R.string.pause_all), broadcast(ACTION_PAUSE_ALL))
			.addAction(0, context.getString(R.string.resume_all), broadcast(ACTION_RESUME_ALL))
			.addAction(0, context.getString(R.string.stop_all), broadcast(ACTION_STOP_ALL))
			.build()
		manager.notify(NOTIFICATION_ID, notification)
	}

	private fun registerReceiver() {
		if (isReceiverRegistered) {
			return
		}
		synchronized(this) {
			if (isReceiverRegistered) {
				return
			}
			ContextCompat.registerReceiver(
				context,
				receiver,
				IntentFilter().apply {
					addAction(ACTION_PAUSE_ALL)
					addAction(ACTION_RESUME_ALL)
					addAction(ACTION_STOP_ALL)
				},
				ContextCompat.RECEIVER_NOT_EXPORTED,
			)
			isReceiverRegistered = true
		}
	}

	private fun broadcast(action: String) = PendingIntentCompat.getBroadcast(
		context,
		action.hashCode(),
		Intent(action).setPackage(context.packageName),
		0,
		false,
	)

	private companion object {

		const val CHANNEL_ID = "download"
		const val GROUP_ID = "downloads"
		const val NOTIFICATION_ID = 0x44_4C_53 // "DLS"
		const val ACTION_PAUSE_ALL = "org.koitharu.kotatsu.download.PAUSE_ALL"
		const val ACTION_RESUME_ALL = "org.koitharu.kotatsu.download.RESUME_ALL"
		const val ACTION_STOP_ALL = "org.koitharu.kotatsu.download.STOP_ALL"
	}
}
