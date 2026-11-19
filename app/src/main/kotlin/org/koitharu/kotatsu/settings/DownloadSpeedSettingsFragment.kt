package org.koitharu.kotatsu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SliderSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberIntPref

/** The three download limits, kept off the main Downloads screen. */
@AndroidEntryPoint
class DownloadSpeedSettingsFragment : BaseComposeSettingsFragment(R.string.downloads_speed_title) {

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				DownloadSpeedScreen()
			}
		}
	}
}

@Composable
private fun DownloadSpeedScreen() {
	var connections by rememberIntPref(AppSettings.KEY_DOWNLOADS_CONNECTIONS, 5)
	var simultaneous by rememberIntPref(AppSettings.KEY_DOWNLOADS_SIMULTANEOUS, 3)
	var retries by rememberIntPref(AppSettings.KEY_DOWNLOADS_RETRIES, 2)

	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SliderSettingsItem(
						title = stringResource(R.string.download_connections),
						subtitle = stringResource(R.string.download_connections_summary),
						value = connections.coerceIn(1, 10),
						valueFrom = 1,
						valueTo = 10,
						stepSize = 1,
						onValueChange = { connections = it },
						icon = R.drawable.ic_download,
						shape = pos.shape,
					)
				}
				item { pos ->
					SliderSettingsItem(
						title = stringResource(R.string.download_simultaneous),
						subtitle = stringResource(R.string.download_simultaneous_summary),
						value = simultaneous.coerceIn(1, 5),
						valueFrom = 1,
						valueTo = 5,
						stepSize = 1,
						onValueChange = { simultaneous = it },
						icon = R.drawable.ic_download,
						shape = pos.shape,
					)
				}
				item { pos ->
					SliderSettingsItem(
						title = stringResource(R.string.download_retries),
						subtitle = stringResource(R.string.download_retries_summary),
						value = retries.coerceIn(0, 5),
						valueFrom = 0,
						valueTo = 5,
						stepSize = 1,
						onValueChange = { retries = it },
						icon = R.drawable.ic_download,
						shape = pos.shape,
					)
				}
			}
		}
	}
}
