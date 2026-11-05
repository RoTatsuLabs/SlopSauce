package org.koitharu.kotatsu.scrobbling.autotrack

import dagger.Reusable
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.model.isNovelSource
import org.koitharu.kotatsu.favourites.domain.FavouriteTitleIndex
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.scrobbling.common.domain.Scrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerManga
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerMangaType
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus
import javax.inject.Inject

/**
 * Links favourites to the trackers the user is signed in to, on its own.
 *
 * A title is only linked when it matches a tracker entry clearly. Anything uncertain is left alone and
 * put on the "needs review" list, because a wrong link would write a wrong reading list on the
 * user's tracker account.
 */
@Reusable
class AutoTracker @Inject constructor(
	private val db: MangaDatabase,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val historyRepository: HistoryRepository,
	private val limiter: TrackerRateLimiter,
	private val store: AutoTrackStore,
) {

	class Pending(val mangaId: Long, val scrobbler: Scrobbler)

	enum class Outcome { TRACKED, NEEDS_REVIEW, SKIPPED }

	val enabledScrobblers: List<Scrobbler>
		get() = scrobblers.filter { it.isEnabled }

	/** Favourites that are not on a tracker yet, per signed-in tracker, minus the ones awaiting review. */
	suspend fun findPending(): List<Pending> {
		val active = enabledScrobblers
		if (active.isEmpty()) return emptyList()
		val favourites = db.getFavouritesDao().findMangaIds()
		val result = ArrayList<Pending>()
		for (scrobbler in active) {
			val service = scrobbler.scrobblerService
			val tracked = db.getScrobblingDao().findMangaIds(service.id).toHashSet()
			for (id in favourites) {
				// Picked by hand since the last run: no longer waiting for review
				if (id in tracked) {
					store.removeReview(id, service)
				} else if (!store.isReview(id, service)) {
					result.add(Pending(id, scrobbler))
				}
			}
		}
		return result
	}

	/**
	 * The titles still waiting for a manual pick. Ones that were linked by hand since, or are no
	 * longer favourites, are dropped from the list here, so it always shows only what is really left.
	 */
	suspend fun pendingReview(): List<Pair<Long, ScrobblerService>> {
		val items = store.reviewItems()
		if (items.isEmpty()) return emptyList()
		val favourites = db.getFavouritesDao().findMangaIds().toHashSet()
		val tracked = HashMap<Int, Set<Long>>()
		val result = ArrayList<Pair<Long, ScrobblerService>>(items.size)
		for ((mangaId, serviceId) in items) {
			val service = ScrobblerService.entries.firstOrNull { it.id == serviceId }
			if (service == null) continue
			val linked = tracked.getOrPut(serviceId) { db.getScrobblingDao().findMangaIds(serviceId).toHashSet() }
			if (mangaId in linked || mangaId !in favourites) {
				store.removeReview(mangaId, service)
			} else {
				result.add(mangaId to service)
			}
		}
		return result
	}

	suspend fun trackOne(pending: Pending): Outcome {
		val scrobbler = pending.scrobbler
		val service = scrobbler.scrobblerService
		val manga = db.getMangaDao().find(pending.mangaId)?.toManga() ?: return Outcome.SKIPPED
		limiter.awaitBackground(service)
		val type = if (manga.source.isNovelSource) ScrobblerMangaType.NOVEL else ScrobblerMangaType.MANGA
		val found = scrobbler.findManga(manga.title, 0, type)
		val match = pickMatch(manga, found) ?: return Outcome.NEEDS_REVIEW
		val history = historyRepository.getOne(manga)
		limiter.awaitBackground(service)
		val canPushProgress = scrobbler.linkManga(
			mangaId = manga.id,
			targetId = match.id,
			fallbackStatus = when {
				history == null -> ScrobblingStatus.PLANNED
				ReadingProgress.isCompleted(history.percent) -> ScrobblingStatus.COMPLETED
				else -> ScrobblingStatus.READING
			},
		)
		// A tracker that is already ahead keeps its count, exactly as when linking by hand
		if (history != null && canPushProgress) {
			limiter.awaitBackground(service)
			scrobbler.scrobble(manga, history.chapterId)
		}
		return Outcome.TRACKED
	}

	/**
	 * The one tracker entry whose name equals the title (or one of its alternative names) once case
	 * and symbols are ignored. Several equal candidates are only accepted when exactly one of them is
	 * the tracker's own best match.
	 */
	private fun pickMatch(manga: Manga, candidates: List<ScrobblerManga>): ScrobblerManga? {
		val names = (sequenceOf(manga.title) + manga.altTitles.asSequence())
			.mapNotNull { FavouriteTitleIndex.normalize(it) }
			.toSet()
		if (names.isEmpty()) return null
		val equal = candidates.filter { c ->
			FavouriteTitleIndex.normalize(c.name) in names ||
				c.altName?.let { FavouriteTitleIndex.normalize(it) } in names
		}
		return when (equal.size) {
			0 -> null
			1 -> equal.first()
			else -> equal.singleOrNull { it.isBestMatch }
		}
	}
}
