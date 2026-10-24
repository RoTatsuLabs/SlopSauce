package org.koitharu.kotatsu.scrobbling.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.model.MissingMangaSource
import org.koitharu.kotatsu.core.model.trackerProgress
import org.koitharu.kotatsu.core.model.trackerProgressList
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.scrobbling.anilist.domain.AniListScrobbler
import org.koitharu.kotatsu.scrobbling.kitsu.domain.KitsuScrobbler
import org.koitharu.kotatsu.scrobbling.mal.domain.MALScrobbler
import org.koitharu.kotatsu.scrobbling.mangabaka.domain.MangaBakaScrobbler
import org.koitharu.kotatsu.scrobbling.shikimori.domain.ShikimoriScrobbler

class ScrobblerProgressConsistencyTest {

	private val chapters = listOf(1f, 2f, 3f, 10f, 10.1f, 10.2f, 11f, 12f).mapIndexed { index, number ->
		MangaChapter(
			id = index + 1L,
			title = null,
			number = number,
			volume = 0,
			url = "/chapter/$index",
			scanlator = null,
			uploadDate = 0L,
			branch = null,
			source = MissingMangaSource("TEST"),
		)
	}

	@Test
	fun `every tracker sends progress through the shared scrobble`() {
		// A service that overrides scrobble could count chapters its own way.
		val services = listOf(
			AniListScrobbler::class.java,
			MALScrobbler::class.java,
			KitsuScrobbler::class.java,
			ShikimoriScrobbler::class.java,
			MangaBakaScrobbler::class.java,
		)
		for (service in services) {
			assertSame(
				"${service.simpleName} must not override scrobble",
				Scrobbler::class.java,
				service.methods.first { it.name == "scrobble" }.declaringClass,
			)
		}
	}

	@Test
	fun `sub-chapters report the progress of their main chapter`() {
		assertEquals(10, chapters[5].trackerProgress(chapters))
	}

	@Test
	fun `progress is never higher than the chapter read`() {
		val list = chapters.trackerProgressList()
		chapters.forEachIndexed { index, chapter -> assertTrue(list[index] <= chapter.number) }
	}

	@Test
	fun `remote progress finds the main chapter`() {
		assertEquals(3, findRemoteProgressIndex(chapters, 10))
		assertEquals(6, findRemoteProgressIndex(chapters, 11))
		assertEquals(7, findRemoteProgressIndex(chapters, 12))
		assertEquals(2, findRemoteProgressIndex(chapters, 5))
	}

	@Test
	fun `progress sent matches progress read back`() {
		val progress = chapters.trackerProgressList()
		for (index in chapters.indices) {
			val back = findRemoteProgressIndex(chapters, progress[index])
			assertEquals(progress[index], progress[back])
		}
	}
}
