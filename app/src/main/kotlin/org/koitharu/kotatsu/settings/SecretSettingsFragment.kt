package org.koitharu.kotatsu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.secret.SecretGate
import org.koitharu.kotatsu.secret.SecretSpace
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.SegmentedSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold

/** Settings of the secret space itself. Only reachable from inside the secret space. */
@AndroidEntryPoint
class SecretSettingsFragment : BaseComposeSettingsFragment(R.string.secret_space) {

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				SecretScreen(
					onChangePin = { SecretGate.changePin(requireActivity()) },
					onUnlockMethod = { SecretGate.setBiometric(requireActivity(), it) },
					onDelete = { SecretGate.deleteSpace(requireActivity()) },
				)
			}
		}
	}
}

@Composable
private fun SecretScreen(
	onChangePin: () -> Unit,
	onUnlockMethod: (Boolean) -> Boolean,
	onDelete: () -> Unit,
) {
	var biometric by remember { mutableStateOf(SecretSpace.isBiometricEnabled) }
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
			SettingsGroup(title = stringResource(R.string.secret_data)) {
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
