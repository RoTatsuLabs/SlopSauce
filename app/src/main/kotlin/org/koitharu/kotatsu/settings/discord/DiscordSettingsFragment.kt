package org.koitharu.kotatsu.settings.discord

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordAuth
import org.koitharu.kotatsu.scrobbling.discord.ui.DiscordLoginActivity
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordPlaceholders
import org.koitharu.kotatsu.settings.compose.ActionSettingsItem
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme
import org.koitharu.kotatsu.settings.compose.EditTextSettingsItem
import org.koitharu.kotatsu.settings.compose.InfoSettingsItem
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsScaffold
import org.koitharu.kotatsu.settings.compose.SwitchSettingsItem
import org.koitharu.kotatsu.settings.compose.rememberBooleanPref
import org.koitharu.kotatsu.settings.compose.rememberStringPref
import javax.inject.Inject

@AndroidEntryPoint
class DiscordSettingsFragment : BaseComposeSettingsFragment(R.string.discord) {

	@Inject
	lateinit var auth: DiscordAuth

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				DiscordScreen(
					onSignIn = ::openSignIn,
					onSignOut = auth::signOut,
				)
			}
		}
	}

	private fun openSignIn() {
		startActivity(Intent(requireContext(), DiscordLoginActivity::class.java))
	}
}

@Composable
private fun DiscordScreen(
	onSignIn: () -> Unit,
	onSignOut: () -> Unit,
) {
	var rpcEnabled by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC, false)
	var skipNsfw by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC_SKIP_NSFW, false)
	var readingPresence by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC_READING_PRESENCE, false)
	var showTimestamp by rememberBooleanPref(AppSettings.KEY_DISCORD_RPC_SHOW_TIMESTAMP, true)
	var activityName by rememberStringPref(AppSettings.KEY_DISCORD_RPC_ACTIVITY_NAME, "")
	val accessToken by rememberStringPref(AppSettings.KEY_DISCORD_ACCESS_TOKEN, "")
	val userName by rememberStringPref(AppSettings.KEY_DISCORD_USER_NAME, "")
	var showSignOutDialog by remember { mutableStateOf(false) }

	val isSignedIn = accessToken.isNotEmpty()

	SettingsScaffold {
		item {
			SettingsGroup {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.discord_rpc),
						subtitle = stringResource(R.string.discord_rpc_summary),
						checked = rpcEnabled,
						onCheckedChange = { rpcEnabled = it },
						icon = R.drawable.ic_discord,
						shape = pos.shape,
					)
				}
				item { pos ->
					ActionSettingsItem(
						title = stringResource(R.string.discord_account),
						subtitle = if (isSignedIn) {
							stringResource(R.string.discord_signed_in_as, userName)
						} else {
							stringResource(R.string.discord_account_summary)
						},
						icon = if (rpcEnabled && !isSignedIn) R.drawable.ic_alert_outline else R.drawable.ic_lock,
						shape = pos.shape,
						enabled = rpcEnabled,
						onClick = { if (isSignedIn) showSignOutDialog = true else onSignIn() },
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.disable_nsfw),
						subtitle = stringResource(R.string.rpc_skip_nsfw_summary),
						checked = skipNsfw,
						onCheckedChange = { skipNsfw = it },
						icon = R.drawable.ic_nsfw,
						shape = pos.shape,
						enabled = rpcEnabled,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.discord_reading_presence),
						subtitle = stringResource(R.string.discord_reading_presence_summary),
						checked = readingPresence,
						onCheckedChange = { readingPresence = it },
						shape = pos.shape,
						enabled = rpcEnabled,
					)
				}
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.discord_show_timestamp),
						subtitle = stringResource(R.string.discord_show_timestamp_summary),
						checked = showTimestamp,
						onCheckedChange = { showTimestamp = it },
						shape = pos.shape,
						enabled = rpcEnabled,
					)
				}
				item { pos ->
					EditTextSettingsItem(
						title = stringResource(R.string.discord_activity_name),
						value = activityName,
						hint = stringResource(R.string.discord_activity_name_hint),
						onValueChange = { activityName = it.trim() },
						shape = pos.shape,
						enabled = rpcEnabled,
					)
				}
			}
		}
		item {
			DiscordButtonGroup(
				number = 1,
				enabledKey = AppSettings.KEY_DISCORD_RPC_BUTTON1_ENABLED,
				enabledDefault = true,
				labelKey = AppSettings.KEY_DISCORD_RPC_BUTTON1_LABEL,
				urlKey = AppSettings.KEY_DISCORD_RPC_BUTTON1_URL,
				labelHint = stringResource(R.string.read_on_s, "{${DiscordPlaceholders.SOURCE_NAME}}"),
				urlHint = "{${DiscordPlaceholders.COMIC_URL}}",
				rpcEnabled = rpcEnabled,
			)
		}
		item {
			DiscordButtonGroup(
				number = 2,
				enabledKey = AppSettings.KEY_DISCORD_RPC_BUTTON2_ENABLED,
				enabledDefault = false,
				labelKey = AppSettings.KEY_DISCORD_RPC_BUTTON2_LABEL,
				urlKey = AppSettings.KEY_DISCORD_RPC_BUTTON2_URL,
				labelHint = stringResource(R.string.discord_button_read_chapter),
				urlHint = "{${DiscordPlaceholders.CHAPTER_URL}}",
				rpcEnabled = rpcEnabled,
			)
		}
		item {
			SettingsGroup {
				item { pos ->
					InfoSettingsItem(
						title = stringResource(R.string.discord_placeholders),
						subtitle = stringResource(R.string.discord_placeholders_summary),
						shape = pos.shape,
					)
				}
			}
		}
		item { Spacer(Modifier.height(24.dp).fillMaxWidth()) }
	}

	if (showSignOutDialog) {
		AlertDialog(
			onDismissRequest = { showSignOutDialog = false },
			title = { Text(stringResource(R.string.discord_account)) },
			text = { Text(stringResource(R.string.discord_sign_out_message, userName)) },
			confirmButton = {
				TextButton(onClick = {
					showSignOutDialog = false
					onSignOut()
				}) { Text(stringResource(R.string.discord_sign_out)) }
			},
			dismissButton = {
				TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(android.R.string.cancel)) }
			},
		)
	}
}

@Composable
private fun DiscordButtonGroup(
	number: Int,
	enabledKey: String,
	enabledDefault: Boolean,
	labelKey: String,
	urlKey: String,
	labelHint: String,
	urlHint: String,
	rpcEnabled: Boolean,
) {
	var enabled by rememberBooleanPref(enabledKey, enabledDefault)
	var label by rememberStringPref(labelKey, "")
	var url by rememberStringPref(urlKey, "")
	val fieldsEnabled = rpcEnabled && enabled
	SettingsGroup {
		item { pos ->
			SwitchSettingsItem(
				title = stringResource(R.string.discord_button_n, number),
				subtitle = stringResource(R.string.discord_button_summary),
				checked = enabled,
				onCheckedChange = { enabled = it },
				shape = pos.shape,
				enabled = rpcEnabled,
			)
		}
		item { pos ->
			EditTextSettingsItem(
				title = stringResource(R.string.discord_button_label),
				value = label,
				hint = labelHint,
				onValueChange = { label = it.trim() },
				shape = pos.shape,
				enabled = fieldsEnabled,
			)
		}
		item { pos ->
			EditTextSettingsItem(
				title = stringResource(R.string.discord_button_url),
				value = url,
				hint = urlHint,
				onValueChange = { url = it.trim() },
				shape = pos.shape,
				enabled = fieldsEnabled,
			)
		}
	}
}
