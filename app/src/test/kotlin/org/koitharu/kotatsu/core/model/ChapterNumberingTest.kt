package org.koitharu.kotatsu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaChapter

class ChapterNumberingTest {

	private val sample = chapters(1f, 2f, 3f, 10f, 10.1f, 10.2f, 11f, 12f)

	@Test
	fun `sub-chapters are left out of the total`() {
		assertEquals(6, sample.mainChapterCount())
	}

	@Test
	fun `half chapters are left out of the total`() {
		assertEquals(2, chapters(10f, 10.5f, 11f).mainChapterCount())
	}

	@Test
	fun `same number from several scanlators counts once`() {
		assertEquals(3, chapters(1f, 1f, 2f, 3f, 3f).mainChapterCount())
	}

	@Test
	fun `chapters without a number count one each`() {
		assertEquals(3, chapters(0f, 0f, 0f).mainChapterCount())
		assertEquals(3, chapters(0f, 1f, 0f).mainChapterCount())
	}

	@Test
	fun `empty list has no chapters`() {
		assertEquals(0, emptyList<MangaChapter>().mainChapterCount())
	}

	@Test
	fun `main chapter numbers`() {
		assertTrue(isMainChapterNumber(10f))
		assertTrue(isMainChapterNumber(0f))
		assertFalse(isMainChapterNumber(10.1f))
		assertFalse(isMainChapterNumber(10.5f))
	}

	@Test
	fun `display keeps the decimal`() {
		val chapter = sample[5]
		assertEquals("10.2", chapter.displayNumber(sample))
		assertEquals("10", sample[3].displayNumber(sample))
		assertEquals("12", sample[7].displayNumber(sample))
	}

	@Test
	fun `number formatting`() {
		assertEquals("10", formatChapterNumber(10f))
		assertEquals("10.2", formatChapterNumber(10.2f))
		assertEquals("0.5", formatChapterNumber(0.5f))
		assertEquals("1000", formatChapterNumber(1000f))
	}

	@Test
	fun `unnumbered chapter is shown by its place among main chapters`() {
		val list = chapters(0f, 0f, 0f)
		assertEquals(listOf("1", "2", "3"), list.map { it.displayNumber(list) })
	}

	@Test
	fun `tracker progress drops the decimals`() {
		assertEquals(listOf(1, 2, 3, 10, 10, 10, 11, 12), sample.trackerProgressList())
		assertEquals(listOf(1, 2, 3, 10, 10, 10, 11, 12), sample.map { it.trackerProgress(sample) })
	}

	@Test
	fun `tracker progress of unnumbered chapters follows main chapters`() {
		val list = chapters(0f, 0f, 0f)
		assertEquals(listOf(1, 2, 3), list.trackerProgressList())
		assertEquals(listOf(1, 2, 3), list.map { it.trackerProgress(list) })
	}

	@Test
	fun `list and single chapter progress agree`() {
		val list = chapters(1f, 0f, 2f, 2.5f, 0f, 3f)
		assertEquals(list.trackerProgressList(), list.map { it.trackerProgress(list) })
	}

	private fun chapters(vararg numbers: Float) = numbers.mapIndexed { index, number ->
		MangaChapter(
			id = index + 1L,
			title = "Chapter $number",
			number = number,
			volume = 0,
			url = "/chapter/$index",
			scanlator = null,
			uploadDate = 0L,
			branch = null,
			source = MissingMangaSource("TEST"),
		)
	}
}
