package org.koitharu.kotatsu.scrobbling.discord.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordAuth
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordRpcManager
import javax.inject.Inject

/**
 * Transparent activity that runs the Social SDK's OAuth2 flow and saves the account on success.
 * It finishes with RESULT_OK on success and RESULT_CANCELED otherwise.
 */
@AndroidEntryPoint
class DiscordLoginActivity : ComponentActivity() {

	@Inject
	lateinit var auth: DiscordAuth

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (!DiscordRpcManager.init(this, this)) {
			fail()
			return
		}
		DiscordRpcManager.attachActivity(this)
		DiscordRpcManager.authorize { tokens ->
			if (tokens == null) {
				fail()
				return@authorize
			}
			lifecycleScope.launch {
				val user = withContext(Dispatchers.IO) { DiscordRpcManager.fetchCurrentUser(tokens.accessToken) }
				if (user == null) {
					fail()
				} else {
					auth.signIn(tokens, user)
					setResult(RESULT_OK)
					finish()
				}
			}
		}
	}

	private fun fail() {
		Toast.makeText(this, R.string.discord_sign_in_failed, Toast.LENGTH_SHORT).show()
		setResult(RESULT_CANCELED)
		finish()
	}
}
