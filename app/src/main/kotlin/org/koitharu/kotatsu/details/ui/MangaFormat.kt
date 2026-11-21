package org.koitharu.kotatsu.details.ui

import androidx.annotation.StringRes
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.parsers.model.MangaTag

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
	}
}

/** A type tag, kept together with its [MangaTag] so tapping it can still search by that tag. */
internal class FormatTag(val format: MangaFormat, val tag: MangaTag)

internal fun Collection<MangaTag>.formatTags(): List<FormatTag> = mapNotNull { tag ->
	MangaFormat.of(tag)?.let { FormatTag(it, tag) }
}.distinctBy { it.format }

internal fun Collection<MangaTag>.withoutFormats(): List<MangaTag> = filter { MangaFormat.of(it) == null }
