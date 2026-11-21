package org.koitharu.kotatsu.mihon

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.core.model.ContentWarning
import org.koitharu.kotatsu.mihon.model.MihonMangaSource
import org.koitharu.kotatsu.mihon.model.parseVolume
import org.koitharu.kotatsu.mihon.model.toManga
import org.koitharu.kotatsu.parsers.model.ContentRating

class MihonDataConvertersTest {

	@Test
	fun `nsfw extension does not mark every manga adult`() {
		val manga = SManga.create().apply {
			url = "/safe-title"
			title = "Safe title"
			genre = "Action, Comedy"
		}

		assertNull(manga.toManga(source(ContentWarning.NSFW)).contentRating)
	}

	@Test
	fun `adult title genre still marks manga adult`() {
		val manga = SManga.create().apply {
			url = "/adult-title"
			title = "Adult title"
			genre = "Romance, 18+"
		}

		assertEquals(ContentRating.ADULT, manga.toManga(source(ContentWarning.NSFW)).contentRating)
	}

	@Test
	fun `volume is read from the chapter name`() {
		assertEquals(1, chapter("Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation").parseVolume())
		assertEquals(12, chapter("Volume 12 Chapter 3").parseVolume())
		assertEquals(3, chapter("vol 3").parseVolume())
	}

	@Test
	fun `names without a whole volume number give zero`() {
		assertEquals(0, chapter("Chapter 4").parseVolume())
		assertEquals(0, chapter("Vol.1.5 Extra").parseVolume())
		assertEquals(0, chapter("Chapter 12 v2").parseVolume())
	}

	private fun chapter(name: String) = SChapter.create().apply {
		url = "/chapter"
		this.name = name
	}

	private fun source(contentWarning: ContentWarning) = MihonMangaSource(
		catalogueSource = object : CatalogueSource {
			override val id = 1L
			override val name = "Test"
			override val lang = "en"
			override val supportsLatest = false
		},
		pkgName = "test.extension",
		contentWarning = contentWarning,
	)
}
