package org.koitharu.kotatsu.scrobbling.autotrack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.awaitUniqueWorkInfoByName
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.scrobbling.common.domain.Scrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus
import org.koitharu.kotatsu.secret.SecretSpace
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Once a day, puts the titles that sit on "Reading" but have not been opened for a while on "On hold",
 * so a tracker list shows what is really being read. Reading again brings a title back on its own
 * (see [org.koitharu.kotatsu.scrobbling.common.domain.SmartStatus]). A title with no reading history
 * here is left alone, since there is nothing to say how long it has been idle.
 */
@HiltWorker
class AutoPauseWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted workerParams: WorkerParameters,
	private val db: MangaDatabase,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val limiter: TrackerRateLimiter,
	private val settings: AppSettings,
) : CoroutineWorker(context, workerParams) {

	override suspend fun doWork(): Result {
		if (SecretSpace.isActive || !settings.isAutoPauseStale) {
			return Result.success()
		}
		val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(settings.autoPauseDays.toLong())
		var retry = false
		for (scrobbler in scrobblers.filter { it.isEnabled }) {
			val service = scrobbler.scrobblerService
			val ids = db.getScrobblingDao().findMangaIds(service.id)
			for (mangaId in ids) {
				val entity = db.getScrobblingDao().find(service.id, mangaId) ?: continue
				if (scrobbler.statusOf(entity.status) != ScrobblingStatus.READING) continue
				val history = db.getHistoryDao().find(mangaId) ?: continue
				if (history.updatedAt >= cutoff) continue
				try {
					limiter.awaitBackground(service)
					scrobbler.updateScrobblingInfo(mangaId, entity.rating, ScrobblingStatus.ON_HOLD, entity.comment)
				} catch (e: CancellationException) {
					throw e
				} catch (e: TrackerRateLimitedException) {
					return Result.retry()
				} catch (e: Exception) {
					e.printStackTraceDebug()
					if (limiter.isBlocked(service)) retry = true
				}
			}
		}
		return if (retry) Result.retry() else Result.success()
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val request = PeriodicWorkRequestBuilder<AutoPauseWorker>(1, TimeUnit.DAYS)
				.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
				.setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
				.addTag(TAG)
				.build()
			workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request).await()
		}

		override suspend fun unschedule() {
			workManager.cancelUniqueWork(TAG).await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager.awaitUniqueWorkInfoByName(TAG).any { !it.state.isFinished }
		}
	}

	private companion object {

		const val TAG = "auto_pause"
	}
}
