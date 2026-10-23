package org.koitharu.kotatsu.scrobbling.discord.ui

import android.content.Context
import androidx.annotation.AnyThread
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.lifecycle.RetainedLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import okio.utf8Size
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.LocalizedAppContext
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.isHttpUrl
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.reader.ui.pager.ReaderUiState
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordNativeActivity
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordRpcSession
import javax.inject.Inject

private const val BUTTON_TEXT_LIMIT = 32

/** Builds the reader's Rich Presence and hands it to the app-wide [DiscordRpcSession]. */
@ViewModelScoped
class DiscordRpc @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
	private val session: DiscordRpcSession,
	lifecycle: ViewModelLifecycle,
) : RetainedLifecycle.OnClearedListener {

	private val appName = context.getString(R.string.app_name)
	private val appIcon = context.getString(R.string.app_icon_url)

	init {
		lifecycle.addOnClearedListener(this)
	}

	override fun onCleared() {
		session.release(this)
	}

	fun clearRpc() {
		session.stop(this)
	}

	fun setIdle() {
		session.setIdle(this)
	}

	@AnyThread
	fun updateRpc(manga: Manga, state: ReaderUiState, coverUrl: String?) {
		if (settings.isDiscordRpcSkipNsfw && manga.isNsfw()) {
			clearRpc()
			return
		}
		val buttonLabel = context.getString(R.string.read_on_s, manga.source.getTitle(context))
		val showButton = buttonLabel.utf8Size() <= BUTTON_TEXT_LIMIT && manga.publicUrl.isHttpUrl()
		session.update(
			owner = this,
			activity = DiscordNativeActivity(
				name = appName,
				details = manga.title,
				state = context.getString(R.string.chapter_d_of_d, state.chapterNumber, state.chaptersTotal),
				// Discord fetches cover URLs itself, so a local file:// cover is unreachable.
				// Callers pass an http(s) cover (URL override or source default); never a local file.
				largeImage = coverUrl?.takeIf { it.isHttpUrl() },
				largeText = context.getString(R.string.reading_s, manga.title),
				smallImage = appIcon,
				smallText = context.getString(R.string.discord_rpc_description),
				button1Label = buttonLabel.takeIf { showButton },
				button1Url = manga.publicUrl.takeIf { showButton },
			),
		)
	}
}
