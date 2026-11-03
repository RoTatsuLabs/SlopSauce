package org.koitharu.kotatsu.settings

import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.secret.SecretGate
import org.koitharu.kotatsu.secret.SecretImporter
import org.koitharu.kotatsu.secret.SecretSpace
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.InfoSettingsItem
import org.koitharu.kotatsu.settings.compose.ListSettingsItem
import org.koitharu.kotatsu.settings.compose.MultiChoiceDialog
import org.koitharu.kotatsu.settings.compose.SegmentedSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import javax.inject.Inject

/** Settings of the secret space itself. Only reachable from inside the secret space. */
@AndroidEntryPoint
class SecretSettingsFragment : BaseComposeSettingsFragment(R.string.secret_space) {

	@Inject
	lateinit var secretImporter: SecretImporter

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				SecretScreen(
					onImportFromMain = ::importFromMain,
					onChangePin = { SecretGate.changePin(requireActivity()) },
					onUnlockMethod = { SecretGate.setBiometric(requireActivity(), it) },
					onLockNow = { SecretSpace.leave(requireActivity()) },
					onDelete = { SecretGate.deleteSpace(requireActivity()) },
				)
			}
		}
	}

	private fun importFromMain(sections: Set<SecretImporter.Section>) {
		Toast.makeText(requireContext(), R.string.secret_import_running, Toast.LENGTH_SHORT).show()
		val context = requireContext().applicationContext
		lifecycleScope.launch {
			val message = runCatching { secretImporter.import(sections) }.fold(
				onSuccess = { context.getString(R.string.secret_import_done) },
				onFailure = { it.getDisplayMessage(context.resources) },
			)
			Toast.makeText(context, message, Toast.LENGTH_LONG).show()
		}
	}
}

private val LOCK_DELAYS = listOf(0L, 15_000L, 30_000L, 60_000L, 300_000L)

@Composable
private fun SecretScreen(
	onImportFromMain: (Set<SecretImporter.Section>) -> Unit,
	onChangePin: () -> Unit,
	onUnlockMethod: (Boolean) -> Boolean,
	onLockNow: () -> Unit,
	onDelete: () -> Unit,
) {
	val context = LocalContext.current.applicationContext
	var leaveDelay by remember { mutableStateOf(SecretSpace.leaveDelayMs) }
	val usedBytes by produceState<Long?>(null) {
		value = withContext(Dispatchers.IO) { SecretSpace.usedBytes(context) }
	}
	var biometric by remember { mutableStateOf(SecretSpace.isBiometricEnabled) }
	var showImportDialog by remember { mutableStateOf(false) }
	if (showImportDialog) {
		val sections = SecretImporter.Section.entries
		MultiChoiceDialog(
			title = stringResource(R.string.secret_import),
			entries = sections.map { stringResource(it.titleResId) },
			selectedIndices = emptySet(),
			onConfirm = { indices ->
				if (indices.isNotEmpty()) {
					onImportFromMain(indices.mapTo(HashSet()) { sections[it] })
				}
			},
			onDismiss = { showImportDialog = false },
		)
	}
	SettingsScaffold {
		item {
			SettingsGroup(title = stringResource(R.string.secret_unlock)) {
				item { pos ->
					SegmentedSettingsItem(
						title = stringResource(R.string.secret_unlock_method),
						labels = listOf(
							stringResource(R.string.secret_method_fingerprint),
							stringResource(R.string.secret_method_pin),
						),
						selectedIndex = if (biometric) 0 else 1,
						onSelected = { biometric = onUnlockMethod(it == 0) },
						icon = R.drawable.ic_lock,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.secret_change_pin),
						onClick = onChangePin,
						icon = R.drawable.ic_lock,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.secret_privacy)) {
				item { pos ->
					ListSettingsItem(
						title = stringResource(R.string.secret_auto_lock),
						entries = listOf(
							stringResource(R.string.secret_lock_immediately),
							stringResource(R.string.secret_lock_15s),
							stringResource(R.string.secret_lock_30s),
							stringResource(R.string.secret_lock_1m),
							stringResource(R.string.secret_lock_5m),
						),
						entryValues = LOCK_DELAYS.map { it.toString() },
						selectedValue = leaveDelay.toString(),
						onValueChange = {
							leaveDelay = it.toLong()
							SecretSpace.leaveDelayMs = leaveDelay
						},
						icon = R.drawable.ic_lock,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.secret_lock_now),
						subtitle = stringResource(R.string.secret_lock_now_summary),
						onClick = onLockNow,
						icon = R.drawable.ic_lock,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
		item {
			SettingsGroup(title = stringResource(R.string.secret_data)) {
				item { pos ->
					InfoSettingsItem(
						title = stringResource(R.string.secret_storage_used),
						subtitle = usedBytes?.let { Formatter.formatFileSize(context, it) }
							?: stringResource(R.string.loading_),
						icon = R.drawable.ic_storage,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.secret_import),
						subtitle = stringResource(R.string.secret_import_summary),
						icon = R.drawable.ic_backup_restore,
						shape = pos.shape,
						onClick = { showImportDialog = true },
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.secret_delete),
						subtitle = stringResource(R.string.secret_delete_summary),
						onClick = onDelete,
						icon = R.drawable.ic_delete,
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}
}
