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
import androidx.compose.runtime.MutableState
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
import org.koitharu.kotatsu.settings.compose.SettingsGroup
import org.koitharu.kotatsu.settings.compose.SettingsGroupScope
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
	val button1 = rememberButtonPrefs(
		AppSettings.KEY_DISCORD_RPC_BUTTON1_ENABLED,
		true,
		AppSettings.KEY_DISCORD_RPC_BUTTON1_LABEL,
		AppSettings.KEY_DISCORD_RPC_BUTTON1_URL,
	)
	val button2 = rememberButtonPrefs(
		AppSettings.KEY_DISCORD_RPC_BUTTON2_ENABLED,
		false,
		AppSettings.KEY_DISCORD_RPC_BUTTON2_LABEL,
		AppSettings.KEY_DISCORD_RPC_BUTTON2_URL,
	)

	val button1LabelHint = stringResource(R.string.read_on_s, "{${DiscordPlaceholders.SOURCE_NAME}}")
	val button2LabelHint = stringResource(R.string.discord_button_read_chapter)

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
			}
		}
		item {
			SettingsGroup(title = stringResource(R.string.discord_section_presence)) {
				item { pos ->
					SwitchSettingsItem(
						title = stringResource(R.string.discord_reading_presence),
						subtitle = stringResource(R.string.discord_reading_presence_summary),
						checked = readingPresence,
						onCheckedChange = { readingPresence = it },
						icon = R.drawable.ic_eye,
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
						icon = R.drawable.ic_timer,
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
						icon = R.drawable.ic_title,
						shape = pos.shape,
						enabled = rpcEnabled,
					)
				}
			}
		}
		item {
			SettingsGroup(title = stringResource(R.string.discord_section_buttons)) {
				discordButtonItems(
					number = 1,
					prefs = button1,
					labelHint = button1LabelHint,
					urlHint = "{${DiscordPlaceholders.COMIC_URL}}",
					rpcEnabled = rpcEnabled,
				)
				discordButtonItems(
					number = 2,
					prefs = button2,
					labelHint = button2LabelHint,
					urlHint = "{${DiscordPlaceholders.CHAPTER_URL}}",
					rpcEnabled = rpcEnabled,
				)
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

private class ButtonPrefs(
	enabledState: MutableState<Boolean>,
	labelState: MutableState<String>,
	urlState: MutableState<String>,
) {
	var enabled by enabledState
	var label by labelState
	var url by urlState
}

@Composable
private fun rememberButtonPrefs(
	enabledKey: String,
	enabledDefault: Boolean,
	labelKey: String,
	urlKey: String,
): ButtonPrefs {
	val enabled = rememberBooleanPref(enabledKey, enabledDefault)
	val label = rememberStringPref(labelKey, "")
	val url = rememberStringPref(urlKey, "")
	return ButtonPrefs(enabled, label, url)
}

/** One button's switch, label and link, as rows of the surrounding "Buttons" group. */
private fun SettingsGroupScope.discordButtonItems(
	number: Int,
	prefs: ButtonPrefs,
	labelHint: String,
	urlHint: String,
	rpcEnabled: Boolean,
) {
	val fieldsEnabled = rpcEnabled && prefs.enabled
	item { pos ->
		SwitchSettingsItem(
			title = stringResource(R.string.discord_button_n, number),
			subtitle = stringResource(R.string.discord_button_summary),
			checked = prefs.enabled,
			onCheckedChange = { prefs.enabled = it },
			icon = R.drawable.ic_open_external,
			shape = pos.shape,
			enabled = rpcEnabled,
		)
	}
	item { pos ->
		EditTextSettingsItem(
			title = stringResource(R.string.discord_button_label, number),
			value = prefs.label,
			hint = labelHint,
			onValueChange = { prefs.label = it.trim() },
			icon = R.drawable.ic_label,
			shape = pos.shape,
			enabled = fieldsEnabled,
		)
	}
	item { pos ->
		EditTextSettingsItem(
			title = stringResource(R.string.discord_button_url, number),
			value = prefs.url,
			hint = urlHint,
			onValueChange = { prefs.url = it.trim() },
			icon = R.drawable.ic_web,
			shape = pos.shape,
			enabled = fieldsEnabled,
		)
	}
}
