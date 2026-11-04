package org.koitharu.kotatsu.list.domain

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.IntDef
import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.core.ui.model.MangaOverride
import org.koitharu.kotatsu.core.ui.widgets.ChipsView
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.favourites.domain.FavouriteTitleIndex
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.ui.model.MangaCompactListModel
import org.koitharu.kotatsu.list.ui.model.MangaDetailedListModel
import org.koitharu.kotatsu.list.ui.model.MangaGridModel
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.local.data.index.LocalMangaIndex
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.koitharu.kotatsu.tracker.domain.model.TrackingLogItem
import org.koitharu.kotatsu.tracker.ui.feed.model.FeedItem
import javax.inject.Inject

@Reusable
class MangaListMapper @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val trackingRepository: TrackingRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val favouriteTitles: FavouriteTitleIndex,
	private val localMangaIndex: LocalMangaIndex,
	private val dataRepository: MangaDataRepository,
	private val db: MangaDatabase,
) {

	private val dict by lazy { readTagsDict(context) }

	suspend fun toListModelList(
		manga: Collection<Manga>,
		mode: ListMode,
		@Flags flags: Int = DEFAULTS,
	): List<MangaListModel> = ArrayList<MangaListModel>(manga.size).apply {
		toListModelList(
			destination = this,
			manga = manga,
			mode = mode,
			flags = flags,
		)
	}

	suspend fun toListModelList(
		destination: MutableCollection<in MangaListModel>,
		manga: Collection<Manga>,
		mode: ListMode,
		@Flags flags: Int = DEFAULTS,
	) {
		val options = getOptions(flags)
		val overrides = dataRepository.getOverrides()
		val sameTitles = loadSameTitles(options)
		manga.mapTo(destination) {
			toListModelImpl(it, mode, options, overrides[it.id], sameTitles)
		}
	}

	suspend fun toListModel(
		manga: Manga,
		mode: ListMode,
		@Flags flags: Int = DEFAULTS,
	): MangaListModel = toListModelImpl(
		manga = manga,
		mode = mode,
		options = getOptions(flags),
		override = dataRepository.getOverride(manga.id),
		sameTitles = loadSameTitles(getOptions(flags)),
	)

	suspend fun toFeedItem(logItem: TrackingLogItem): FeedItem {
		val override = dataRepository.getOverride(logItem.manga.id)
		val history = db.getHistoryDao().find(logItem.manga.id)
		val chapters = if (history != null) {
			val allChapters = db.getChaptersDao().findAll(logItem.manga.id)
			val lastReadChapterIndex = allChapters.indexOfFirst { it.chapterId == history.chapterId }
			if (lastReadChapterIndex != -1) {
				logItem.chapters.map { chapter ->
					val chapterIndex = allChapters.indexOfFirst { it.chapterId == chapter.id }
					val isNew = chapterIndex == -1 || chapterIndex > lastReadChapterIndex
					chapter.copy(isNew = isNew)
				}
			} else {
				logItem.chapters
			}
		} else {
			logItem.chapters
		}

		val isNew = logItem.isNew && chapters.any { it.isNew }

		return FeedItem(
			id = logItem.id,
			override = override,
			chapters = chapters,
			manga = logItem.manga,
			isNew = isNew,
		)
	}

	fun mapTags(tags: Collection<MangaTag>) = tags.map {
		ChipsView.ChipModel(
			tint = getTagTint(it),
			title = it.title,
			data = it,
		)
	}

	private suspend fun toCompactListModel(
		manga: Manga,
		@Options options: Int,
		override: MangaOverride?,
	) = MangaCompactListModel(
		manga = manga,
		override = override,
		subtitle = manga.authors.joinToString(", "),
		counter = getCounter(manga.id, options),
	)

	private suspend fun toDetailedListModel(
		manga: Manga,
		@Options options: Int,
		override: MangaOverride?,
		sameTitles: FavouriteTitleIndex.Snapshot,
	) = MangaDetailedListModel(
		subtitle = manga.altTitles.firstOrNull(),
		manga = manga,
		override = override,
		counter = getCounter(manga.id, options),
		progress = getProgress(manga.id, options),
		isFavorite = isFavorite(manga.id, options),
		isSaved = isSaved(manga.id, options),
		tags = mapTags(manga.tags),
		favouriteElsewhere = favouriteElsewhere(manga, sameTitles),
	)

	private suspend fun toGridModel(
		manga: Manga,
		@Options options: Int,
		override: MangaOverride?,
		isTitleHidden: Boolean,
		sameTitles: FavouriteTitleIndex.Snapshot,
	) = MangaGridModel(
		manga = manga,
		override = override,
		counter = getCounter(manga.id, options),
		progress = getProgress(manga.id, options),
		isFavorite = isFavorite(manga.id, options),
		isSaved = isSaved(manga.id, options),
		isTitleHidden = isTitleHidden,
		isTitleOverCover = settings.isTitleOverCover,
		isGridSpacingIncreased = settings.isGridSpacingIncreased,
		favouriteElsewhere = favouriteElsewhere(manga, sameTitles),
	)

	private suspend fun toListModelImpl(
		manga: Manga,
		mode: ListMode,
		@Options options: Int,
		override: MangaOverride?,
		sameTitles: FavouriteTitleIndex.Snapshot,
	): MangaListModel = when (mode) {
		ListMode.LIST -> toCompactListModel(manga, options, override)
		ListMode.DETAILED_LIST -> toDetailedListModel(manga, options, override, sameTitles)
		ListMode.GRID -> toGridModel(manga, options, override, false, sameTitles)
		ListMode.COVER_ONLY -> toGridModel(manga, options, override, true, sameTitles)
	}

	private suspend fun loadSameTitles(@Options options: Int): FavouriteTitleIndex.Snapshot {
		return if (options.isBadgeEnabled(FAVORITE)) favouriteTitles.load() else FavouriteTitleIndex.EMPTY
	}

	/** The source a same-titled favourite lives in, shown when this very entry is not a favourite. */
	private suspend fun favouriteElsewhere(manga: Manga, sameTitles: FavouriteTitleIndex.Snapshot): String? {
		if (sameTitles.isEmpty) return null
		val found = sameTitles.find(manga.id, manga.title, manga.altTitles) ?: return null
		return MangaSource(found.sourceName).getTitle(context)
	}

	private suspend fun getCounter(mangaId: Long, @Options options: Int): Int {
		return if (settings.isTrackerEnabled) {
			trackingRepository.getNewChaptersCount(mangaId)
		} else {
			0
		}
	}

	private suspend fun getProgress(mangaId: Long, @Options options: Int): ReadingProgress? {
		return if (options.isBadgeEnabled(PROGRESS)) {
			historyRepository.getProgress(mangaId, settings.progressIndicatorMode)
		} else {
			null
		}
	}

	private suspend fun isFavorite(mangaId: Long, @Options options: Int): Boolean {
		return options.isBadgeEnabled(FAVORITE) && favouritesRepository.isFavorite(mangaId)
	}

	private suspend fun isSaved(mangaId: Long, @Options options: Int): Boolean {
		return options.isBadgeEnabled(SAVED) && mangaId in localMangaIndex
	}

	@ColorRes
	private fun getTagTint(tag: MangaTag): Int {
		return if (tag.title.lowercase() in dict) {
			R.color.warning
		} else {
			0
		}
	}

	private fun readTagsDict(context: Context): ScatterSet<String> =
		context.resources.openRawResource(R.raw.tags_warnlist).use {
			val set = MutableScatterSet<String>()
			it.bufferedReader().forEachLine { x ->
				val line = x.trim().lowercase()
				if (line.isNotEmpty()) {
					set.add(line)
				}
			}
			set.trim()
			set
		}

	private fun Int.isBadgeEnabled(@Options badge: Int) = this and badge == badge

	@Options
	@SuppressLint("WrongConstant")
	private fun getOptions(@Flags flags: Int): Int {
		var options = settings.getMangaListBadges() or PROGRESS
		options = options and flags.inv()
		return options
	}

	@IntDef(DEFAULTS, NO_SAVED, NO_PROGRESS, NO_FAVORITE, flag = true)
	@Retention(AnnotationRetention.SOURCE)
	annotation class Flags

	@IntDef(NONE, SAVED, FAVORITE, PROGRESS)
	@Retention(AnnotationRetention.SOURCE)
	private annotation class Options

	companion object {

		private const val NONE = 0
		private const val SAVED = 1
		private const val PROGRESS = 2
		private const val FAVORITE = 4

		const val DEFAULTS = NONE
		const val NO_SAVED = SAVED
		const val NO_PROGRESS = PROGRESS
		const val NO_FAVORITE = FAVORITE
	}
}
