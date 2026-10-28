package org.koitharu.kotatsu.scrobbling.discord.ui

import android.content.Context
import androidx.annotation.AnyThread
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.lifecycle.RetainedLifecycle
import dagger.hilt.android.scopes.ViewModelScoped
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.LocalizedAppContext
import org.koitharu.kotatsu.core.model.displayNumber
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.model.mainChapterCount
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.isHttpUrl
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.ui.pager.ReaderUiState
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordActivityFactory
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordButtonConfig
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordNativeActivity
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordPlaceholders
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordPresenceOptions
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordRpcManager
import org.koitharu.kotatsu.scrobbling.discord.data.DiscordRpcSession
import org.koitharu.kotatsu.scrobbling.discord.data.ReadingSnapshot
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.math.roundToInt

/** Describes the reader's current state for Rich Presence and hands it to [DiscordRpcSession]. */
@ViewModelScoped
class DiscordRpc @Inject constructor(
	@LocalizedAppContext private val context: Context,
	private val settings: AppSettings,
	private val session: DiscordRpcSession,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	lifecycle: ViewModelLifecycle,
) : RetainedLifecycle.OnClearedListener {

	init {
		lifecycle.addOnClearedListener(this)
	}

	override fun onCleared() {
		session.release(this)
	}

	/** The reader came on screen. */
	fun onEnterReader() {
		session.enter(this)
	}

	/** Takes the presence down right away. */
	fun clearRpc() {
		session.stop(this)
	}

	/**
	 * The reader stopped. Closing it, a rotation or another screen of the app means the reader is
	 * left. If no screen of the app is left on screen, the user went to another app with the
	 * reader still open, which shows as Idle (when smart account status is on).
	 */
	fun onReaderStopped(isFinishing: Boolean, isChangingConfigurations: Boolean) {
		val leftApp = !isFinishing && !isChangingConfigurations && !DiscordRpcManager.hasStartedActivity()
		if (leftApp && settings.isDiscordReadingPresence) {
			session.setIdle(this)
		} else {
			session.leave(this)
		}
	}

	/** Rich Presence is optional: whatever goes wrong here must never reach the reader. */
	@AnyThread
	fun updateRpc(details: MangaDetails, state: ReaderUiState, coverUrl: String?) {
		try {
			updateRpcImpl(details, state, coverUrl)
		} catch (e: Throwable) {
			e.printStackTraceDebug()
		}
	}

	private fun updateRpcImpl(details: MangaDetails, state: ReaderUiState, coverUrl: String?) {
		val manga = details.toManga()
		if (settings.isDiscordRpcSkipNsfw && manga.isNsfw()) {
			clearRpc()
			return
		}
		val options = presenceOptions()
		val chapters = details.chapters[state.chapter.branch].orEmpty()
		val usesChapterUrl = DiscordPlaceholders.uses(options.button1.url.ifBlank { options.button1DefaultUrl }, DiscordPlaceholders.CHAPTER_URL) ||
			DiscordPlaceholders.uses(options.button2.url.ifBlank { options.button2DefaultUrl }, DiscordPlaceholders.CHAPTER_URL)
		val isEpubScroll = state.isEpub && !state.isEpubPaged
		session.update(this, settings.isDiscordRpcShowTimestamp) {
			try {
				buildActivity(manga, details, state, coverUrl, options, chapters, usesChapterUrl, isEpubScroll)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
				null
			}
		}
	}

	private suspend fun buildActivity(
		manga: Manga,
		details: MangaDetails,
		state: ReaderUiState,
		coverUrl: String?,
		options: DiscordPresenceOptions,
		chapters: List<MangaChapter>,
		usesChapterUrl: Boolean,
		isEpubScroll: Boolean,
	): DiscordNativeActivity {
		val snapshot = ReadingSnapshot(
			comicTitle = manga.title,
			comicUrl = manga.publicUrl.takeIf { it.isHttpUrl() },
			chapterUrl = if (usesChapterUrl) findChapterUrl(details, state.chapter) else null,
			chapterTitle = state.chapter.title,
			currentChapter = state.chapter.displayNumber(chapters),
			totalChapters = chapters.mainChapterCount(),
			sourceName = manga.source.getTitle(context),
			author = manga.authors.firstOrNull(),
			currentPage = if (isEpubScroll) state.currentPage / 10 else state.currentPage + 1,
			totalPages = if (isEpubScroll) 100 else state.totalPages,
			progressPercent = if (state.percent in 0f..1f) (state.percent * 100).roundToInt() else -1,
			coverUrl = coverUrl,
		)
		return DiscordActivityFactory.create(snapshot, options)
	}

	/**
	 * The chapter's web address. Downloaded chapters replace their online twins in the merged list
	 * and carry a file path, so the chapter is looked up in the source's own manga instead.
	 */
	private suspend fun findChapterUrl(details: MangaDetails, chapter: MangaChapter): String? {
		val manga = details.sourceManga.takeUnless { it.isLocal } ?: return null
		val sourceChapter = manga.findChapterById(chapter.id) ?: return null
		val url = runCatchingCancellable {
			mangaRepositoryFactory.create(manga.source).getChapterUrl(manga, sourceChapter)
		}.getOrNull()
		return url?.takeIf { it.isHttpUrl() } ?: sourceChapter.url.takeIf { it.isHttpUrl() }
	}

	private fun presenceOptions() = DiscordPresenceOptions(
		appName = context.getString(R.string.app_name),
		customActivityName = settings.discordActivityName,
		stateFormat = context.getString(R.string.discord_rpc_state),
		largeTextFormat = context.getString(R.string.reading_s),
		smallText = context.getString(R.string.discord_rpc_description),
		smallImageUrl = context.getString(R.string.app_icon_url),
		placeholderImageUrl = context.getString(R.string.discord_placeholder_url),
		button1 = DiscordButtonConfig(
			enabled = settings.isDiscordButton1Enabled,
			label = settings.discordButton1Label,
			url = settings.discordButton1Url,
		),
		button1DefaultLabel = context.getString(R.string.read_on_s, "{${DiscordPlaceholders.SOURCE_NAME}}"),
		button1DefaultUrl = "{${DiscordPlaceholders.COMIC_URL}}",
		button2 = DiscordButtonConfig(
			enabled = settings.isDiscordButton2Enabled,
			label = settings.discordButton2Label,
			url = settings.discordButton2Url,
		),
		button2DefaultLabel = context.getString(R.string.discord_button_read_chapter),
		button2DefaultUrl = "{${DiscordPlaceholders.CHAPTER_URL}}",
	)
}
