package org.koitharu.kotatsu.backup.local.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Sends backup files to a Telegram chat through the Bot API. */
class TelegramBackupUploader @Inject constructor(
	@BaseHttpClient private val baseClient: OkHttpClient,
	private val settings: AppSettings,
) {

	private val client by lazy {
		baseClient.newBuilder()
			.callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
			.writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
			.readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
			.build()
	}

	suspend fun send(file: File) = runInterruptible(Dispatchers.IO) {
		val token = checkNotNull(settings.backupTelegramBotToken) { "Telegram bot token is not specified" }
		val chatId = checkNotNull(settings.backupTelegramChatId) { "Telegram chat ID is not specified" }
		if (file.length() > MAX_FILE_SIZE) {
			throw IOException("Backup is larger than the 50 MB Telegram limit")
		}
		val body = MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("chat_id", chatId)
			.addFormDataPart("document", file.name, file.asRequestBody(BackupUtils.MIME_TYPE.toMediaType()))
			.build()
		val request = Request.Builder()
			.url("https://api.telegram.org/bot$token/sendDocument")
			.post(body)
			.build()
		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				val description = runCatching {
					JSONObject(response.body?.string().orEmpty()).optString("description")
				}.getOrNull()
				throw IOException("Telegram: ${description?.takeIf { it.isNotEmpty() } ?: "HTTP ${response.code}"}")
			}
		}
	}

	/**
	 * Looks through the latest bot updates for a private "/start [code]" message and returns its chat ID.
	 * The negative offset reads only the newest updates, so updates of other users stay mostly untouched.
	 */
	suspend fun findChatId(code: String): String? = runInterruptible(Dispatchers.IO) {
		val token = checkNotNull(settings.backupTelegramBotToken) { "Telegram bot token is not specified" }
		val request = Request.Builder()
			.url("https://api.telegram.org/bot$token/getUpdates?offset=-100&allowed_updates=%5B%22message%22%5D")
			.build()
		client.newCall(request).execute().use { response ->
			val json = JSONObject(response.body?.string().orEmpty())
			if (!json.optBoolean("ok")) {
				throw IOException("Telegram: ${json.optString("description").ifEmpty { "HTTP ${response.code}" }}")
			}
			val updates = json.optJSONArray("result") ?: return@use null
			for (i in updates.length() - 1 downTo 0) {
				val message = updates.getJSONObject(i).optJSONObject("message") ?: continue
				val chat = message.optJSONObject("chat") ?: continue
				if (chat.optString("type") == "private" && message.optString("text") == "/start $code") {
					return@use chat.optLong("id").toString()
				}
			}
			null
		}
	}

	private companion object {

		const val MAX_FILE_SIZE = 50L * 1024 * 1024
	}
}
