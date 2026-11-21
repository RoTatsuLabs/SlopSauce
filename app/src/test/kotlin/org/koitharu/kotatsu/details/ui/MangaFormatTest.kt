package org.koitharu.kotatsu.details.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerTitleType

class MangaFormatTest {

	@Test
	fun `no tracker answer keeps what the source said`() {
		val fromTags = listOf(FormatTag(MangaFormat.WEBTOON, null))

		assertEquals(fromTags, resolveFormats(fromTags, listOf(null)))
	}

	@Test
	fun `tracker names a type the source never sent`() {
		val result = resolveFormats(emptyList(), listOf(ScrobblerTitleType.MANHWA))

		assertEquals(listOf(MangaFormat.MANHWA), result.map { it.format })
		assertNull(result.single().tag)
	}

	@Test
	fun `tracker origin replaces the source origin`() {
		val result = resolveFormats(
			listOf(FormatTag(MangaFormat.MANGA, null)),
			listOf(ScrobblerTitleType.MANHWA),
		)

		assertEquals(listOf(MangaFormat.MANHWA), result.map { it.format })
	}

	@Test
	fun `source extras survive next to the tracker origin`() {
		val result = resolveFormats(
			listOf(FormatTag(MangaFormat.WEBTOON, null)),
			listOf(ScrobblerTitleType.MANHWA),
		)

		assertEquals(listOf(MangaFormat.MANHWA, MangaFormat.WEBTOON), result.map { it.format })
	}

	@Test
	fun `first tracker decides when trackers disagree`() {
		val result = resolveFormats(
			emptyList(),
			listOf(ScrobblerTitleType.MANHUA, ScrobblerTitleType.MANGA),
		)

		assertEquals(listOf(MangaFormat.MANHUA), result.map { it.format })
	}
}
