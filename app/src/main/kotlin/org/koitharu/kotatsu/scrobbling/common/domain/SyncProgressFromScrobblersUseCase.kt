package org.koitharu.kotatsu.scrobbling.common.domain

import dagger.Reusable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.koitharu.kotatsu.core.model.isNsfw
import org.koitharu.kotatsu.core.model.trackerProgress
import org.koitharu.kotatsu.core.model.trackerProgressList
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

@Reusable
class SyncProgressFromScrobblersUseCase @Inject constructor(
	private val historyRepository: HistoryRepository,
	private val settings: AppSettings,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
) {

	/**
	 * Brings the app and the trackers level without ever moving anything back: a tracker that is ahead
	 * moves the app's history forward, and trackers that are behind the app's history are sent its
	 * progress. Returns the chapter the history was moved to, if it was.
	 */
	suspend operator fun invoke(manga: Manga, branch: String?): Int? {
		if (!settings.isScrobblingProgressSyncEnabled || settings.isIncognitoModeEnabled(manga.isNsfw())) {
			return null
		}
		val chapters = manga.getChapters(branch).orEmpty()
		if (chapters.isEmpty()) {
			return null
		}
		val remotes = supervisorScope {
			scrobblers.filter { it.isEnabled }.map { scrobbler ->
				async {
					runCatchingCancellable {
						scrobbler.refreshScrobblingOrNull(manga.id)?.let { scrobbler to it }
					}.onFailure {
						it.printStackTraceDebug()
					}.getOrNull()
				}
			}.awaitAll().filterNotNull()
		}
		val remoteChapter = remotes
			.filterNot { (scrobbler, entity) -> scrobbler.isNotStarted(entity.status) }
			.maxOfOrNull { (_, entity) -> entity.chapter }
		var pulled: Int? = null
		if (remoteChapter != null) {
			val targetIndex = findRemoteProgressIndex(chapters, remoteChapter)
			if (targetIndex >= 0 && historyRepository.advanceFromTracking(manga, chapters, targetIndex)) {
				pulled = chapters.trackerProgressList()[targetIndex]
			}
		}
		pushLocalProgress(manga, remotes)
		return pulled
	}

	private suspend fun pushLocalProgress(manga: Manga, remotes: List<Pair<Scrobbler, ScrobblingEntity>>) {
		if (remotes.isEmpty()) {
			return
		}
		val history = historyRepository.getOne(manga) ?: return
		val all = manga.chapters.orEmpty()
		val chapter = all.findById(history.chapterId) ?: return
		val local = chapter.trackerProgress(all.filter { it.branch == chapter.branch })
		if (local <= 0) {
			return
		}
		for ((scrobbler, entity) in remotes) {
			if (entity.chapter < local) {
				scrobbler.tryScrobble(manga, history.chapterId)
			}
		}
	}
}

internal fun findRemoteProgressIndex(chapters: List<MangaChapter>, remoteChapter: Int): Int {
	if (remoteChapter <= 0) {
		return -1
	}
	var previousNumber = 0f
	var result = -1
	// A chapter without a number takes its place among the main chapters, as when progress is sent.
	val fallbackNumbers = chapters.trackerProgressList()
	for ((index, chapter) in chapters.withIndex()) {
		val number = chapter.number.takeIf { it > 0f } ?: fallbackNumbers[index].toFloat()
		if (number < previousNumber || number > remoteChapter) {
			break
		}
		previousNumber = number
		result = index
	}
	return result
}
