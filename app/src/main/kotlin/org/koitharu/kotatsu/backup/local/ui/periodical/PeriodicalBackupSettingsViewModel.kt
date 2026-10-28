package org.koitharu.kotatsu.backup.local.ui.periodical

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.backup.local.data.LocalBackupRepository
import org.koitharu.kotatsu.backup.local.domain.BackupUtils
import org.koitharu.kotatsu.backup.local.domain.ExternalBackupStorage
import org.koitharu.kotatsu.backup.local.domain.TelegramBackupUploader
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.resolveFile
import org.koitharu.kotatsu.core.util.progress.Progress
import java.security.SecureRandom
import java.util.Date
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class PeriodicalBackupSettingsViewModel @Inject constructor(
	private val settings: AppSettings,
	private val backupStorage: ExternalBackupStorage,
	private val repository: LocalBackupRepository,
	private val telegramUploader: TelegramBackupUploader,
	@ApplicationContext private val appContext: Context,
) : BaseViewModel() {

	val lastBackupDate = MutableStateFlow<Date?>(null)
	val backupsDirectory = MutableStateFlow<String?>("")

	private val isSendingState = MutableStateFlow(false)
	val isTelegramSending: StateFlow<Boolean> = isSendingState.asStateFlow()
	val onTelegramSent = MutableEventFlow<Unit>()

	private val isLinkingState = MutableStateFlow(false)
	val isTelegramLinking: StateFlow<Boolean> = isLinkingState.asStateFlow()
	val onTelegramLink = MutableEventFlow<String>()
	val onTelegramLinked = MutableEventFlow<Unit>()

	init {
		updateSummaryData()
	}

	/** Opens the bot with a one-time code, then waits for the user to press Start to learn the chat ID. */
	fun linkTelegram() {
		if (isLinkingState.value) return
		launchJob(Dispatchers.Default) {
			isLinkingState.value = true
			try {
				val code = generateLinkCode()
				onTelegramLink.call("https://t.me/${BuildConfig.TELEGRAM_BOT_USERNAME}?start=$code")
				val deadline = System.currentTimeMillis() + LINK_TIMEOUT_MS
				while (System.currentTimeMillis() < deadline) {
					delay(LINK_POLL_INTERVAL_MS)
					val chatId = telegramUploader.findChatId(code)
					if (chatId != null) {
						settings.backupTelegramChatId = chatId
						onTelegramLinked.call(Unit)
						return@launchJob
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				errorEvent.call(e)
			} finally {
				isLinkingState.value = false
			}
		}
	}

	private fun generateLinkCode(): String {
		val random = SecureRandom()
		return buildString {
			repeat(16) { append(LINK_CODE_ALPHABET[random.nextInt(LINK_CODE_ALPHABET.length)]) }
		}
	}

	fun sendToTelegram() {
		if (isSendingState.value) return
		launchJob(Dispatchers.Default) {
			isSendingState.value = true
			val file = BackupUtils.createTempFile(appContext)
			try {
				ZipOutputStream(file.outputStream()).use { output ->
					repository.createBackup(output, MutableStateFlow(Progress.INDETERMINATE))
				}
				telegramUploader.send(file)
				onTelegramSent.call(Unit)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				errorEvent.call(e)
			} finally {
				file.delete()
				isSendingState.value = false
			}
		}
	}

	fun updateSummaryData() {
		updateBackupsDirectory()
		updateLastBackupDate()
	}

	private fun updateBackupsDirectory() = launchJob(Dispatchers.Default) {
		val dir = settings.periodicalBackupDirectory
		backupsDirectory.value = if (dir != null) {
			dir.toUserFriendlyString()
		} else {
			BackupUtils.getAppBackupDir(appContext).path
		}
	}

	private fun updateLastBackupDate() = launchJob(Dispatchers.Default) {
		lastBackupDate.value = runCatching { backupStorage.getLastBackupDate() }.getOrNull()
	}

	private fun Uri.toUserFriendlyString(): String? {
		val df = DocumentFile.fromTreeUri(appContext, this)
		if (df?.canWrite() != true) {
			return null
		}
		return resolveFile(appContext)?.path ?: toString()
	}

	private companion object {

		const val LINK_CODE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
		const val LINK_TIMEOUT_MS = 120_000L
		const val LINK_POLL_INTERVAL_MS = 2_000L
	}
}
