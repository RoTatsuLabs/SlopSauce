package org.koitharu.kotatsu.core.model

import org.koitharu.kotatsu.parsers.model.MangaChapter
import java.math.BigDecimal
import kotlin.math.floor

/**
 * A chapter numbered 10.1, 10.2 or 10.5 is an extra of chapter 10, not a chapter of its own. Only
 * whole numbers (10, 12, 17) and chapters without any number count as main chapters.
 */
fun isMainChapterNumber(number: Float): Boolean = !(number > 0f) || number == floor(number)

/**
 * How many main chapters the list has. A number that appears more than once (the same chapter from
 * several scanlators) counts once, and chapters without a number count one each.
 */
fun Collection<MangaChapter>.mainChapterCount(): Int {
	val numbers = HashSet<Float>()
	var unnumbered = 0
	for (chapter in this) {
		val number = chapter.number
		when {
			!(number > 0f) -> unnumbered++
			number == floor(number) -> numbers.add(number)
		}
	}
	return numbers.size + unnumbered
}

/**
 * The chapter's own number for display, keeping decimals (10.2), or its place among the main
 * chapters of [chapters] when the source gives it no number.
 */
fun MangaChapter.displayNumber(chapters: List<MangaChapter>): String {
	return if (number > 0f) {
		formatChapterNumber(number)
	} else {
		mainOrdinal(chapters).toString()
	}
}

/**
 * The progress a tracker should record when this chapter is read: the chapter's number without its
 * decimals (10.2 is progress 10), or its place among the main chapters when it has no number. All
 * trackers take whole chapters, so they all get the same figure.
 */
fun MangaChapter.trackerProgress(chapters: List<MangaChapter>): Int {
	return if (number > 0f) number.toInt() else mainOrdinal(chapters)
}

/** The [trackerProgress] of every chapter of [this], in order. */
fun List<MangaChapter>.trackerProgressList(): List<Int> {
	val seen = HashSet<Float>()
	var unnumbered = 0
	return map { chapter ->
		val number = chapter.number
		when {
			!(number > 0f) -> {
				unnumbered++
				seen.size + unnumbered
			}

			else -> {
				if (number == floor(number)) seen.add(number)
				number.toInt()
			}
		}
	}
}

/** 1-based place of the chapter among the main chapters of [chapters]. */
private fun MangaChapter.mainOrdinal(chapters: List<MangaChapter>): Int {
	val index = chapters.indexOfFirst { it.id == id }
	val before = if (index > 0) chapters.subList(0, index).mainChapterCount() else 0
	return before + 1
}

/** 10f becomes "10" and 10.2f becomes "10.2". */
fun formatChapterNumber(number: Float): String {
	return BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
}
