package org.koitharu.kotatsu.scrobbling.discord.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPlaceholdersTest {

	private val values = mapOf<String, String?>(
		DiscordPlaceholders.COMIC_URL to "https://example.org/comic/a?x=1",
		DiscordPlaceholders.CHAPTER_URL to null,
		DiscordPlaceholders.COMIC_TITLE to "Dr. Stone & Co",
		DiscordPlaceholders.CURRENT_CHAPTER to "10.2",
		DiscordPlaceholders.TOTAL_CHAPTERS to "6",
	)

	@Test
	fun `known placeholders are replaced`() {
		val result = DiscordPlaceholders.resolve("{comic_title}: {current_chapter}/{total_chapters}", values, false)
		assertEquals("Dr. Stone & Co: 10.2/6", result.text)
		assertTrue(result.isComplete)
	}

	@Test
	fun `unknown placeholders stay as typed`() {
		val result = DiscordPlaceholders.resolve("{nope} {comic_title}", values, false)
		assertEquals("{nope} Dr. Stone & Co", result.text)
		assertTrue(result.isComplete)
	}

	@Test
	fun `placeholder without a value makes the text incomplete`() {
		assertFalse(DiscordPlaceholders.resolve("{chapter_url}", values, true).isComplete)
		assertFalse(DiscordPlaceholders.resolve("{author}", values, false).isComplete)
	}

	@Test
	fun `text in links is encoded but addresses are not`() {
		val result = DiscordPlaceholders.resolve("{comic_url}&q={comic_title}", values, true)
		assertEquals("https://example.org/comic/a?x=1&q=Dr.%20Stone%20%26%20Co", result.text)
	}

	@Test
	fun `text in labels is not encoded`() {
		assertEquals("Dr. Stone & Co", DiscordPlaceholders.resolve("{comic_title}", values, false).text)
	}

	@Test
	fun `template without placeholders is unchanged`() {
		val result = DiscordPlaceholders.resolve("https://example.org", values, true)
		assertEquals("https://example.org", result.text)
		assertTrue(result.isComplete)
	}

	@Test
	fun `uses detects a placeholder`() {
		assertTrue(DiscordPlaceholders.uses("a {chapter_url}", DiscordPlaceholders.CHAPTER_URL))
		assertFalse(DiscordPlaceholders.uses("a {comic_url}", DiscordPlaceholders.CHAPTER_URL))
	}

	@Test
	fun `stray braces are kept as typed`() {
		assertEquals("{ a } {", DiscordPlaceholders.resolve("{ a } {", values, false).text)
		assertEquals("{unclosed", DiscordPlaceholders.resolve("{unclosed", values, false).text)
		assertEquals("}x{", DiscordPlaceholders.resolve("}x{", values, false).text)
	}

	@Test
	fun `nested braces resolve the inner placeholder`() {
		assertEquals("{Dr. Stone & Co}", DiscordPlaceholders.resolve("{{comic_title}}", values, false).text)
	}

	@Test
	fun `adjacent placeholders`() {
		assertEquals("10.26", DiscordPlaceholders.resolve("{current_chapter}{total_chapters}", values, false).text)
	}
}
