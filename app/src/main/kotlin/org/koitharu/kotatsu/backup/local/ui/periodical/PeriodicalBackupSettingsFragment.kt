package org.koitharu.kotatsu.backup.local.ui.periodical

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.StateFlow
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.local.domain.BackupSection
import org.koitharu.kotatsu.core.os.OpenDocumentTreeHelper
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.EditTextSettingsItem
import org.koitharu.kotatsu.settings.compose.ListSettingsItem
import org.koitharu.kotatsu.settings.compose.MultiSelectSettingsItem
import org.koitharu.kotatsu.settings.compose.PlainInfoSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SliderSettingsItem
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberIntPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import org.koitharu.kotatsu.settings.compose.rememberStringSetPref
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicalBackupSettingsFragment :
	BaseComposeSettingsFragment(R.string.periodic_backups),
	ActivityResultCallback<Uri?> {

	private val viewModel by viewModels<PeriodicalBackupSettingsViewModel>()

	@Inject
	lateinit var settings: AppSettings

	private val outputSelectCall = OpenDocumentTreeHelper(this, this)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				PeriodicalBackupScreen(
					backupsDirectory = viewModel.backupsDirectory,
					lastBackupDate = viewModel.lastBackupDate,
					isTelegramSending = viewModel.isTelegramSending,
					isTelegramLinking = viewModel.isTelegramLinking,
					onLinkTelegram = viewModel::linkTelegram,
					onPickDirectory = ::pickDirectory,
					onSendToTelegram = viewModel::sendToTelegram,
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.onTelegramSent.observeEvent(viewLifecycleOwner) {
			Snackbar.make(view, R.string.backup_telegram_sent, Snackbar.LENGTH_SHORT).show()
		}
		viewModel.onTelegramLink.observeEvent(viewLifecycleOwner) { url ->
			try {
				startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
			} catch (_: ActivityNotFoundException) {
				Snackbar.make(view, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
			}
		}
		viewModel.onTelegramLinked.observeEvent(viewLifecycleOwner) {
			Snackbar.make(view, R.string.backup_telegram_linked, Snackbar.LENGTH_SHORT).show()
		}
		viewModel.onError.observeEvent(viewLifecycleOwner) { e ->
			MaterialAlertDialogBuilder(requireContext())
				.setPositiveButton(android.R.string.ok, null)
				.setTitle(R.string.error_occurred)
				.setMessage(e.getDisplayMessage(resources))
				.show()
		}
	}

	private fun pickDirectory() {
		if (!outputSelectCall.tryLaunch(null)) {
			Snackbar.make(requireView(), R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context?.contentResolver?.takePersistableUriPermission(result, takeFlags)
			settings.periodicalBackupDirectory = result
			viewModel.updateSummaryData()
		}
	}
}

@Composable
private fun PeriodicalBackupScreen(
	backupsDirectory: StateFlow<String?>,
	lastBackupDate: StateFlow<Date?>,
	isTelegramSending: StateFlow<Boolean>,
	isTelegramLinking: StateFlow<Boolean>,
	onLinkTelegram: () -> Unit,
	onPickDirectory: () -> Unit,
	onSendToTelegram: () -> Unit,
) {
	val ctx = LocalContext.current
	val directory by backupsDirectory.collectAsState()
	val lastBackup by lastBackupDate.collectAsState()
	val telegramSending by isTelegramSending.collectAsState()
	val telegramLinking by isTelegramLinking.collectAsState()

	val frequencyEntries = remember { ctx.resources.getStringArray(R.array.backup_frequency).toList() }
	val frequencyValues = remember { ctx.resources.getStringArray(R.array.backup_frequency_values).toList() }

	var enabled by rememberBooleanPref(AppSettings.KEY_BACKUP_PERIODICAL_ENABLED, false)
	var frequency by rememberStringPref(AppSettings.KEY_BACKUP_PERIODICAL_FREQ, "3")
	var trim by rememberBooleanPref(AppSettings.KEY_BACKUP_PERIODICAL_TRIM, true)
	var maxCount by rememberIntPref(AppSettings.KEY_BACKUP_PERIODICAL_COUNT, 10)
	val backupSections = remember { BackupSection.selectable }
	val backupSectionNames = remember { backupSections.mapTo(HashSet()) { it.entryName } }
	var includedSections by rememberStringSetPref(AppSettings.KEY_BACKUP_SECTIONS, backupSectionNames)
	var telegramSections by rememberStringSetPref(AppSettings.KEY_BACKUP_TELEGRAM_SECTIONS, backupSectionNames)
	var telegramEnabled by rememberBooleanPref(AppSettings.KEY_BACKUP_TELEGRAM_ENABLED, false)
	var telegramChatId by rememberStringPref(AppSettings.KEY_BACKUP_TELEGRAM_CHAT_ID, "")
	val telegramAvailable = BuildConfig.TELEGRAM_BOT_TOKEN.isNotEmpty()
	val telegramReady = telegramChatId.isNotBlank()

	val dirIsWarning = directory == null
	val dirSubtitle = when (directory) {
		null -> stringResource(R.string.invalid_value_message)
		"" -> null
		else -> directory
	}

	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.periodic_backups_enable),
						checked = enabled,
						onCheckedChange = { enabled = it },
						icon = R.drawable.ic_backup_restore,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.backups_output_directory),
						subtitle = dirSubtitle,
						icon = if (dirIsWarning) R.drawable.ic_alert_outline else R.drawable.ic_folder_file,
						shape = pos.shape,
						enabled = enabled,
						onClick = onPickDirectory,
					)
				}
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.backup_frequency),
						entries = frequencyEntries,
						entryValues = frequencyValues,
						selectedValue = frequency,
						onValueChange = { frequency = it },
						icon = R.drawable.ic_timelapse,
						shape = pos.shape,
						enabled = enabled,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.delete_old_backups),
						subtitle = stringResource(R.string.delete_old_backups_summary),
						checked = trim,
						onCheckedChange = { trim = it },
						icon = R.drawable.ic_delete,
						shape = pos.shape,
						enabled = enabled,
					)
				}
				item { pos ->
					SliderSettingsItem(
						title = stringResource(R.string.max_backups_count),
						value = maxCount.coerceIn(1, 32),
						valueFrom = 1,
						valueTo = 32,
						stepSize = 1,
						onValueChange = { maxCount = it },
						icon = R.drawable.ic_storage,
						shape = pos.shape,
						enabled = enabled && trim,
					)
				}
				item { pos ->
					MultiSelectSettingsItem(
						title = stringResource(R.string.backup_contents),
						entries = backupSections.map { stringResource(it.titleResId) },
						entryValues = backupSections.map { it.entryName },
						selectedValues = includedSections,
						onValuesChange = { if (it.isNotEmpty()) includedSections = it },
						icon = R.drawable.ic_save,
						shape = pos.shape,
						enabled = enabled,
					)
				}
			}
		}
		if (telegramAvailable) item {
			SettingsGroup(title = stringResource(R.string.backup_telegram)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.backup_telegram_enable),
						subtitle = stringResource(R.string.backup_telegram_summary),
						checked = telegramEnabled,
						onCheckedChange = { telegramEnabled = it },
						icon = R.drawable.ic_share,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.backup_telegram_link),
						subtitle = when {
							telegramLinking -> stringResource(R.string.backup_telegram_link_waiting)
							telegramChatId.isNotBlank() -> stringResource(R.string.backup_telegram_linked_to, telegramChatId)
							else -> stringResource(R.string.backup_telegram_link_summary)
						},
						icon = R.drawable.ic_account_circle,
						shape = pos.shape,
						enabled = telegramEnabled && !telegramLinking,
						onClick = onLinkTelegram,
					)
				}
				item { pos ->
					EditTextSettingsItem(
						title = stringResource(R.string.backup_telegram_chat_id),
						value = telegramChatId,
						hint = stringResource(R.string.backup_telegram_chat_id_hint),
						onValueChange = { telegramChatId = it.trim() },
						icon = R.drawable.ic_account_circle,
						shape = pos.shape,
						enabled = telegramEnabled,
					)
				}
				item { pos ->
					MultiSelectSettingsItem(
						title = stringResource(R.string.backup_contents),
						entries = backupSections.map { stringResource(it.titleResId) },
						entryValues = backupSections.map { it.entryName },
						selectedValues = telegramSections,
						onValuesChange = { if (it.isNotEmpty()) telegramSections = it },
						icon = R.drawable.ic_save,
						shape = pos.shape,
						enabled = telegramEnabled,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.backup_telegram_send_now),
						subtitle = if (telegramSending) stringResource(R.string.creating_backup) else null,
						icon = R.drawable.ic_upload_file,
						shape = pos.shape,
						enabled = telegramEnabled && telegramReady && !telegramSending,
						onClick = onSendToTelegram,
					)
				}
			}
		}
		if (lastBackup != null) {
			item {
				PlainInfoSettingsItem(
					text = stringResource(
						R.string.last_successful_backup,
						DateUtils.getRelativeTimeSpanString(lastBackup!!.time),
					),
					icon = R.drawable.ic_info_outline,
				)
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
