package org.koitharu.kotatsu.scrobbling.common.domain

import dagger.Reusable
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.model.trackerProgress
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.scrobbling.autotrack.TrackerRateLimiter
import javax.inject.Inject

/**
 * The titles whose reading progress differs between the app and a tracker, read from what is already
 * stored (no network), and a way to bring one level: the side that is behind catches up, and nothing
 * is ever moved backwards.
 */
@Reusable
class ScrobblerSyncReport @Inject constructor(
	private val db: MangaDatabase,
	private val historyRepository: HistoryRepository,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val limiter: TrackerRateLimiter,
) {

	class Item(
		val manga: Manga,
		val scrobbler: Scrobbler,
		/** Chapter the app has reached, in the tracker's numbering. */
		val app: Int,
		/** Chapter the tracker has. */
		val tracker: Int,
		internal val chapterId: Long,
	) {

		val isAppAhead: Boolean
			get() = app > tracker
	}

	suspend fun load(): List<Item> {
		val result = ArrayList<Item>()
		for (scrobbler in scrobblers.filter { it.isEnabled }) {
			val service = scrobbler.scrobblerService
			for (mangaId in db.getScrobblingDao().findMangaIds(service.id)) {
				val entity = db.getScrobblingDao().find(service.id, mangaId) ?: continue
				val history = db.getHistoryDao().find(mangaId)
				val manga = db.getMangaDao().find(mangaId)
					?.toManga(db.getChaptersDao().findAll(mangaId)) ?: continue
				val chapters = manga.chapters.orEmpty()
				val chapter = history?.let { chapters.findById(it.chapterId) }
				val app = chapter?.trackerProgress(chapters.filter { it.branch == chapter.branch }) ?: 0
				if (app != entity.chapter && (app > 0 || entity.chapter > 0)) {
					result.add(Item(manga, scrobbler, app, entity.chapter, history?.chapterId ?: 0L))
				}
			}
		}
		return result.sortedBy { it.manga.title.lowercase() }
	}

	/** Brings one title level. Returns false when there was nothing it could do. */
	suspend fun sync(item: Item): Boolean {
		val chapters = item.manga.chapters.orEmpty()
		if (item.isAppAhead) {
			if (item.chapterId == 0L) return false
			limiter.awaitBackground(item.scrobbler.scrobblerService)
			item.scrobbler.scrobble(item.manga, item.chapterId)
			return true
		}
		val index = findRemoteProgressIndex(chapters, item.tracker)
		return index >= 0 && historyRepository.advanceFromTracking(item.manga, chapters, index)
	}
}
