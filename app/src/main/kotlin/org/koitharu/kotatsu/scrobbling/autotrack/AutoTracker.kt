package org.koitharu.kotatsu.scrobbling.autotrack

import dagger.Reusable
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.model.isNovelSource
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

	/** [tracked] marks a title that is already linked: a pass over everything only re-sends its progress. */
	class Pending(val mangaId: Long, val scrobbler: Scrobbler, val tracked: Boolean = false)

	/** Which favourites a pass looks at. */
	enum class Scope {
		/** Not on a tracker yet, minus the ones awaiting review. */
		UNTRACKED,

		/** Not on a tracker yet, including the ones awaiting review, searched again from scratch. */
		WITH_REVIEW,

		/** Every favourite, so a pass can be repeated even when all of them are already tracked. */
		ALL,
	}

	enum class Outcome { TRACKED, NEEDS_REVIEW, SKIPPED }

	val enabledScrobblers: List<Scrobbler>
		get() = scrobblers.filter { it.isEnabled }

	/** Favourites a pass of the given [scope] covers, per signed-in tracker. */
	suspend fun findPending(scope: Scope = Scope.UNTRACKED): List<Pending> {
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
					if (scope == Scope.ALL) result.add(Pending(id, scrobbler, tracked = true))
				} else if (scope != Scope.UNTRACKED || !store.isReview(id, service)) {
					result.add(Pending(id, scrobbler))
				}
			}
		}
		return result
	}

	/** The titles waiting for a manual pick, as work for a pass that links them to their nearest entry. */
	suspend fun findReviewPending(): List<Pending> {
		val active = enabledScrobblers.associateBy { it.scrobblerService }
		return pendingReview().mapNotNull { (mangaId, service) -> active[service]?.let { Pending(mangaId, it) } }
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

	/** [closest] links the nearest entry when nothing is clear, for titles the user chose to push through. */
	suspend fun trackOne(pending: Pending, closest: Boolean = false): Outcome {
		val scrobbler = pending.scrobbler
		val service = scrobbler.scrobblerService
		val manga = db.getMangaDao().find(pending.mangaId)?.toManga() ?: return Outcome.SKIPPED
		if (pending.tracked) {
			// Already linked: nothing to search, only send the reading progress again
			val last = historyRepository.getOne(manga) ?: return Outcome.SKIPPED
			limiter.awaitBackground(service)
			scrobbler.scrobble(manga, last.chapterId)
			return Outcome.TRACKED
		}
		limiter.awaitBackground(service)
		val type = if (manga.source.isNovelSource) ScrobblerMangaType.NOVEL else ScrobblerMangaType.MANGA
		// The title first; the alternative titles only when it found nothing clear, to spare requests
		val found = LinkedHashMap<Long, ScrobblerManga>()
		var match: ScrobblerManga? = null
		for ((index, query) in TitleMatch.searchQueries(manga).withIndex()) {
			if (index > 0) limiter.awaitBackground(service)
			scrobbler.findManga(query, 0, type).forEach { found.putIfAbsent(it.id, it) }
			match = TitleMatch.pick(manga, found.values.toList())
			if (match != null) break
		}
		if (match == null && closest) match = TitleMatch.closest(manga, found.values.toList())
		if (match == null) return Outcome.NEEDS_REVIEW
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
}
