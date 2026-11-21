package org.koitharu.kotatsu.details.ui

import androidx.annotation.StringRes
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerTitleType

/**
 * What kind of title this is (manga, manhwa, manhua, webtoon...).
 *
 * Neither Mihon extensions nor the Kotatsu model carry this as a field: sources send it as one more
 * genre, so it sat among the tags. This recognises those tags so the details screen can show them
 * as the title's type instead.
 */
internal enum class MangaFormat(@StringRes val titleResId: Int, vararg val aliases: String) {
	MANGA(R.string.content_type_manga, "manga"),
	MANHWA(R.string.content_type_manhwa, "manhwa"),
	MANHUA(R.string.content_type_manhua, "manhua"),
	WEBTOON(R.string.webtoon, "webtoon", "webtoons"),
	COMICS(R.string.content_type_comics, "comic", "comics"),
	DOUJINSHI(R.string.content_type_doujinshi, "doujinshi", "doujin"),
	ONE_SHOT(R.string.content_type_one_shot, "one shot", "one-shot", "oneshot"),
	NOVEL(R.string.content_type_novel, "novel", "light novel");

	companion object {

		private val byAlias: Map<String, MangaFormat> = entries
			.flatMap { format -> format.aliases.map { it to format } }
			.toMap()

		fun of(tag: MangaTag): MangaFormat? = byAlias[tag.title.trim().lowercase()]

		fun ofTracker(type: String?): MangaFormat? = when (type) {
			ScrobblerTitleType.MANGA -> MANGA
			ScrobblerTitleType.MANHWA -> MANHWA
			ScrobblerTitleType.MANHUA -> MANHUA
			ScrobblerTitleType.NOVEL -> NOVEL
			ScrobblerTitleType.ONE_SHOT -> ONE_SHOT
			ScrobblerTitleType.DOUJINSHI -> DOUJINSHI
			else -> null
		}

		/** Where the title comes from: a tracker names one of these from the country of origin. */
		val ORIGINS = setOf(MANGA, MANHWA, MANHUA)
	}
}

/**
 * A type of the title. [tag] is set when the source sent it as a genre, so tapping it can still
 * search by that tag; a type that only a tracker named has none.
 */
internal class FormatTag(val format: MangaFormat, val tag: MangaTag?)

internal fun Collection<MangaTag>.formatTags(): List<FormatTag> = mapNotNull { tag ->
	MangaFormat.of(tag)?.let { FormatTag(it, tag) }
}.distinctBy { it.format }

internal fun Collection<MangaTag>.withoutFormats(): List<MangaTag> = filter { MangaFormat.of(it) == null }

/**
 * The types to show for a title. A tracker knows where a title was made, which a source's genres
 * only guess at, so its manga/manhwa/manhua replaces the source's. Everything else the source
 * says (webtoon, comics, doujinshi...) stays, and a source type with no tracker answer is kept.
 */
internal fun resolveFormats(fromTags: List<FormatTag>, trackerTypes: List<String?>): List<FormatTag> {
	val fromTracker = trackerTypes.mapNotNull { MangaFormat.ofTracker(it) }.distinct()
	if (fromTracker.isEmpty()) return fromTags
	// Trackers can disagree on where a title is from; the first one listed decides
	val origin = fromTracker.firstOrNull { it in MangaFormat.ORIGINS }
	val trackerFormats = listOfNotNull(origin) + fromTracker.filter { it !in MangaFormat.ORIGINS }
	val trackerResult = trackerFormats.map { format ->
		fromTags.firstOrNull { it.format == format } ?: FormatTag(format, null)
	}
	val sourceExtras = fromTags.filter { tag ->
		tag.format !in trackerFormats && !(origin != null && tag.format in MangaFormat.ORIGINS)
	}
	return trackerResult + sourceExtras
}
