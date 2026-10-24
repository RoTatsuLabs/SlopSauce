package org.koitharu.kotatsu.scrobbling.discord.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordActivityFactoryTest {

	private val off = DiscordButtonConfig(enabled = false, label = "", url = "")
	private val on = DiscordButtonConfig(enabled = true, label = "", url = "")

	private val snapshot = ReadingSnapshot(
		comicTitle = "My Comic",
		comicUrl = "https://example.org/comic",
		chapterUrl = "https://example.org/chapter/10-2",
		chapterTitle = "Extra",
		currentChapter = "10.2",
		totalChapters = 6,
		sourceName = "Example",
		author = null,
		currentPage = 3,
		totalPages = 20,
		progressPercent = 15,
		coverUrl = "https://example.org/cover.jpg",
	)

	private val options = DiscordPresenceOptions(
		appName = "SlopSauce",
		customActivityName = "",
		stateFormat = "Chapter %1\$s of %2\$s",
		largeTextFormat = "%1\$s",
		smallText = "SlopSauce",
		smallImageUrl = null,
		button1 = on,
		button1DefaultLabel = "Read on {source_name}",
		button1DefaultUrl = "{comic_url}",
		button2 = off,
		button2DefaultLabel = "Read chapter",
		button2DefaultUrl = "{chapter_url}",
	)

	@Test
	fun `state shows decimal chapter and main chapter total`() {
		assertEquals("Chapter 10.2 of 6", DiscordActivityFactory.create(snapshot, options).state)
	}

	@Test
	fun `state ignores custom settings`() {
		val activity = DiscordActivityFactory.create(snapshot, options.copy(customActivityName = "Reading"))
		assertEquals("Chapter 10.2 of 6", activity.state)
	}

	@Test
	fun `blank activity name uses the app name`() {
		assertEquals("SlopSauce", DiscordActivityFactory.create(snapshot, options).name)
		assertEquals("SlopSauce", DiscordActivityFactory.create(snapshot, options.copy(customActivityName = "  ")).name)
	}

	@Test
	fun `custom activity name is used`() {
		assertEquals("Manga", DiscordActivityFactory.create(snapshot, options.copy(customActivityName = " Manga ")).name)
	}

	@Test
	fun `default button 1 points to the comic`() {
		val activity = DiscordActivityFactory.create(snapshot, options)
		assertEquals("Read on Example", activity.button1Label)
		assertEquals("https://example.org/comic", activity.button1Url)
		assertNull(activity.button2Label)
	}

	@Test
	fun `disabled button is left out and the other moves up`() {
		val activity = DiscordActivityFactory.create(snapshot, options.copy(button1 = off, button2 = on))
		assertEquals("Read chapter", activity.button1Label)
		assertEquals("https://example.org/chapter/10-2", activity.button1Url)
		assertNull(activity.button2Label)
	}

	@Test
	fun `both buttons`() {
		val activity = DiscordActivityFactory.create(snapshot, options.copy(button2 = on))
		assertEquals("Read on Example", activity.button1Label)
		assertEquals("Read chapter", activity.button2Label)
		assertEquals("https://example.org/chapter/10-2", activity.button2Url)
	}

	@Test
	fun `custom label and link use placeholders`() {
		val config = DiscordButtonConfig(true, "{comic_title} {current_chapter}/{total_chapters}", "https://x.org/?t={comic_title}")
		val result = DiscordActivityFactory.resolveButton(config, "d", "https://d.org", DiscordActivityFactory.placeholderValues(snapshot))
		assertEquals("My Comic 10.2/6" to "https://x.org/?t=My%20Comic", result)
	}

	@Test
	fun `button without chapter link is hidden`() {
		val values = DiscordActivityFactory.placeholderValues(snapshot.copy(chapterUrl = null))
		assertNull(DiscordActivityFactory.resolveButton(on, "Read chapter", "{chapter_url}", values))
	}

	@Test
	fun `button with a link that is not a web address is hidden`() {
		val values = DiscordActivityFactory.placeholderValues(snapshot)
		assertNull(DiscordActivityFactory.resolveButton(on.copy(url = "ftp://x.org"), "a", "{comic_url}", values))
		assertNull(DiscordActivityFactory.resolveButton(on.copy(url = "javascript:alert(1)"), "a", "{comic_url}", values))
	}

	@Test
	fun `long button label is truncated`() {
		val values = DiscordActivityFactory.placeholderValues(snapshot)
		val result = DiscordActivityFactory.resolveButton(on.copy(label = "x".repeat(60)), "a", "{comic_url}", values)
		assertEquals(32, result?.first?.length)
	}

	@Test
	fun `unknown progress hides a button that needs it`() {
		val values = DiscordActivityFactory.placeholderValues(snapshot.copy(progressPercent = -1))
		assertNull(DiscordActivityFactory.resolveButton(on.copy(label = "{progress}"), "a", "{comic_url}", values))
	}

	@Test
	fun `non web cover is not sent`() {
		val activity = DiscordActivityFactory.create(snapshot.copy(coverUrl = "content://cover"), options)
		assertNull(activity.largeImage)
	}
}
