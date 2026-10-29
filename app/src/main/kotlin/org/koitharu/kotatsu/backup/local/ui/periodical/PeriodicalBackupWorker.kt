package org.koitharu.kotatsu.backup.local.ui.periodical

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.local.data.LocalBackupRepository
import org.koitharu.kotatsu.backup.local.domain.BackupSection
import org.koitharu.kotatsu.backup.local.domain.BackupUtils
import org.koitharu.kotatsu.backup.local.domain.ExternalBackupStorage
import org.koitharu.kotatsu.backup.local.domain.TelegramBackupUploader
import org.koitharu.kotatsu.backup.local.ui.BaseBackupRestoreService
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.awaitUniqueWorkInfoByName
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.trySetForeground
import org.koitharu.kotatsu.core.util.progress.Progress
import org.koitharu.kotatsu.settings.work.PeriodicWorkScheduler
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltWorker
class PeriodicalBackupWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted workerParams: WorkerParameters,
	private val repository: LocalBackupRepository,
	private val externalBackupStorage: ExternalBackupStorage,
	private val telegramUploader: TelegramBackupUploader,
	private val settings: AppSettings,
) : CoroutineWorker(context, workerParams) {

	private val notificationManager by lazy { NotificationManagerCompat.from(applicationContext) }

	override suspend fun doWork(): Result {
		trySetForeground()
		val saveLocally = settings.periodicalBackupDirectory != null
		val sendToTelegram = settings.isBackupTelegramConfigured
		val localSections = settings.backupSections
		val telegramSections = settings.backupTelegramSections
		val files = ArrayList<File>(2)
		var outputUri: Uri? = null
		var fileName: String? = null
		var telegramError: Throwable? = null
		try {
			var localFile: File? = null
			if (saveLocally) {
				localFile = createBackupFile(localSections).also(files::add)
				fileName = localFile.name
				outputUri = externalBackupStorage.put(localFile)
			}
			if (sendToTelegram) {
				try {
					val telegramFile = if (localFile != null && localSections == telegramSections) {
						localFile
					} else {
						createBackupFile(telegramSections).also(files::add)
					}
					fileName = fileName ?: telegramFile.name
					telegramUploader.send(telegramFile)
				} catch (e: CancellationException) {
					throw e
				} catch (e: Throwable) {
					if (!saveLocally) throw e
					e.printStackTraceDebug()
					telegramError = e
				}
			}
		} catch (e: CancellationException) {
			files.forEach { it.delete() }
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			files.forEach { it.delete() }
			postErrorNotification(e)
			return Result.failure()
		}
		files.forEach { it.delete() }
		if (outputUri != null) {
			externalBackupStorage.trim(settings.periodicalBackupMaxCount)
		}
		if (telegramError != null) {
			postErrorNotification(telegramError)
		} else {
			postCompletionNotification(outputUri?.lastPathSegment ?: fileName)
		}
		return Result.success()
	}

	private suspend fun createBackupFile(sections: Set<BackupSection>): File {
		val file = BackupUtils.createTempFile(applicationContext)
		try {
			ZipOutputStream(file.outputStream()).use { output ->
				repository.createBackup(output, MutableStateFlow(Progress.INDETERMINATE), sections)
			}
		} catch (e: Throwable) {
			file.delete()
			throw e
		}
		return file
	}

	override suspend fun getForegroundInfo(): ForegroundInfo {
		BaseBackupRestoreService.createNotificationChannel(applicationContext)
		val notification = buildOngoingNotification()
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(
				FOREGROUND_NOTIFICATION_ID,
				notification,
				ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
			)
		} else {
			ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
		}
	}

	private fun buildOngoingNotification(): Notification {
		return NotificationCompat.Builder(applicationContext, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(applicationContext.getString(R.string.creating_backup))
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setDefaults(0)
			.setSilent(true)
			.setOngoing(true)
			.setProgress(0, 0, true)
			.setSmallIcon(R.drawable.general_notification)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
			.setCategory(NotificationCompat.CATEGORY_PROGRESS)
			.build()
	}

	private fun postCompletionNotification(name: String?) {
		if (!applicationContext.checkNotificationPermission(BaseBackupRestoreService.CHANNEL_ID)) return
		val notification = NotificationCompat.Builder(applicationContext, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(applicationContext.getString(R.string.backup_saved))
			.setContentText(name)
			.setSmallIcon(R.drawable.general_notification)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setSilent(true)
			.setAutoCancel(true)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.homeIntent(applicationContext),
					0,
					false,
				),
			)
			.build()
		notificationManager.notify(TAG_RESULT, RESULT_NOTIFICATION_ID, notification)
	}

	private fun postErrorNotification(error: Throwable) {
		if (!applicationContext.checkNotificationPermission(BaseBackupRestoreService.CHANNEL_ID)) return
		val message = error.getDisplayMessage(applicationContext.resources)
		val notification = NotificationCompat.Builder(applicationContext, BaseBackupRestoreService.CHANNEL_ID)
			.setContentTitle(applicationContext.getString(R.string.error_occurred))
			.setContentText(message)
			.setStyle(NotificationCompat.BigTextStyle().bigText(message))
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setSilent(true)
			.setAutoCancel(true)
			.build()
		notificationManager.notify(TAG_RESULT, RESULT_NOTIFICATION_ID, notification)
	}

	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
		private val settings: AppSettings,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val frequencyMillis = settings.periodicalBackupFrequencyMillis
			if (frequencyMillis <= 0L || (settings.periodicalBackupDirectory == null && !settings.isBackupTelegramConfigured)) {
				return unschedule()
			}
			val constraints = Constraints.Builder()
				.setRequiresStorageNotLow(true)
				.setRequiresBatteryNotLow(true)
				.build()
			val request = PeriodicWorkRequestBuilder<PeriodicalBackupWorker>(frequencyMillis, TimeUnit.MILLISECONDS)
				.setConstraints(constraints)
				.addTag(TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
				.build()
			workManager
				.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager.cancelUniqueWork(TAG).await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager.awaitUniqueWorkInfoByName(TAG).any { !it.state.isFinished }
		}
	}

	companion object {

		private const val FOREGROUND_NOTIFICATION_ID = 41
		private const val RESULT_NOTIFICATION_ID = 42
		private const val TAG = "periodical_backup"
		private const val TAG_RESULT = "periodical_backup_result"
	}
}
