package org.koitharu.kotatsu.scrobbling.autotrack

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.trySetForeground
import org.koitharu.kotatsu.scrobbling.common.domain.ScrobblerAuthRequiredException
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Tracks the favourites that are not on a tracker yet. One pass handles everything pending, so a new
 * favourite and the "track untracked favourites" button use the same work. Doing it is idempotent: a
 * pass that is stopped halfway, by the system or by a rate limit, simply continues where it left off.
 */
@HiltWorker
class AutoTrackWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted workerParams: WorkerParameters,
	private val autoTracker: AutoTracker,
	private val store: AutoTrackStore,
	private val limiter: TrackerRateLimiter,
) : CoroutineWorker(context, workerParams) {

	override suspend fun doWork(): Result {
		val pending = autoTracker.findPending()
		if (pending.isEmpty()) {
			return Result.success()
		}
		trySetForeground()
		var tracked = 0
		var review = 0
		var failed = 0
		var hitLimit = false
		val skipped = HashSet<ScrobblerService>()
		for ((index, item) in pending.withIndex()) {
			val service = item.scrobbler.scrobblerService
			if (service in skipped) continue
			try {
				when (autoTracker.trackOne(item)) {
					AutoTracker.Outcome.TRACKED -> tracked++
					AutoTracker.Outcome.NEEDS_REVIEW -> {
						store.addReview(item.mangaId, service)
						review++
					}

					AutoTracker.Outcome.SKIPPED -> Unit
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: TrackerRateLimitedException) {
				// A long timeout: hand the job back instead of holding a foreground service open for it
				saveSummary(tracked, review, failed, pending.size)
				return Result.retry()
			} catch (e: ScrobblerAuthRequiredException) {
				skipped += service
			} catch (e: Exception) {
				e.printStackTraceDebug()
				// A 429 mid-request surfaces as an ordinary error; it is not this title's fault, so it stays
				// pending for the next pass instead of counting as failed.
				if (limiter.isBlocked(service)) hitLimit = true else failed++
			}
			setProgress(workDataOf(KEY_DONE to index + 1, KEY_TOTAL to pending.size, KEY_REVIEW to review))
			updateForeground(index + 1, pending.size)
		}
		saveSummary(tracked, review, failed, pending.size)
		return if (hitLimit) Result.retry() else Result.success()
	}

	override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0)

	private fun saveSummary(tracked: Int, review: Int, failed: Int, total: Int) {
		val previous = store.lastSummary
		// A pass continued after a stop adds to the figures of the one it continues
		store.lastSummary = AutoTrackStore.Summary(
			tracked = tracked + (previous?.takeIf { isContinuation }?.tracked ?: 0),
			needsReview = store.reviewItems().size,
			failed = failed,
			total = total + (previous?.takeIf { isContinuation }?.total ?: 0),
		)
	}

	private val isContinuation: Boolean
		get() = runAttemptCount > 0

	private suspend fun updateForeground(done: Int, total: Int) {
		runCatching { setForeground(createForegroundInfo(done, total)) }
	}

	private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
		val manager = NotificationManagerCompat.from(applicationContext)
		manager.createNotificationChannel(
			NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
				.setName(applicationContext.getString(R.string.auto_track_favourites))
				.setShowBadge(false)
				.setVibrationEnabled(false)
				.setSound(null, null)
				.setLightsEnabled(false)
				.build(),
		)
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setContentTitle(applicationContext.getString(R.string.auto_track_favourites))
			.setContentText(
				if (total > 0) {
					applicationContext.getString(R.string.auto_track_progress, done, total)
				} else {
					null
				},
			)
			.setSmallIcon(R.drawable.general_notification)
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setSilent(true)
			.setProgress(total, done, total == 0)
			.addAction(
				androidx.appcompat.R.drawable.abc_ic_clear_material,
				applicationContext.getString(android.R.string.cancel),
				WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
			)
			.build()
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			ForegroundInfo(NOTIFICATION_ID, notification)
		}
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
	) {

		/** Starts a pass, or queues one behind the pass already running. */
		fun start() {
			val request = OneTimeWorkRequestBuilder<AutoTrackWorker>()
				.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
				.addTag(TAG)
				.build()
			workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
		}

		suspend fun stop() {
			workManager.cancelUniqueWork(TAG).await()
		}

		/** The pass that is running or waiting, or null. */
		fun observe(): Flow<Progress?> = workManager.getWorkInfosByTagFlow(TAG).map { list ->
			val info = list.firstOrNull { !it.state.isFinished } ?: return@map null
			Progress(
				done = info.progress.getInt(KEY_DONE, 0),
				total = info.progress.getInt(KEY_TOTAL, 0),
				needsReview = info.progress.getInt(KEY_REVIEW, 0),
				isWaiting = info.state != WorkInfo.State.RUNNING,
			)
		}
	}

	data class Progress(val done: Int, val total: Int, val needsReview: Int, val isWaiting: Boolean)

	private companion object {

		const val TAG = "auto_track"
		const val CHANNEL_ID = "auto_track"
		const val NOTIFICATION_ID = 47
		const val KEY_DONE = "done"
		const val KEY_TOTAL = "total"
		const val KEY_REVIEW = "review"
	}
}
