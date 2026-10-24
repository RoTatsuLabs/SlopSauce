package org.koitharu.kotatsu.scrobbling.discord.data

import java.net.URLEncoder

/**
 * Replaces `{placeholders}` in the text a user typed for a Rich Presence button.
 *
 * A placeholder that is not known is left as typed, so a typo stays visible. A known one that has
 * no value right now (e.g. `{chapter_url}` for a source without chapter links) makes the whole
 * text [Resolved.isComplete] false, so the caller can leave the button out instead of showing a
 * half-filled link.
 */
object DiscordPlaceholders {

	const val COMIC_URL = "comic_url"
	const val CHAPTER_URL = "chapter_url"
	const val COMIC_TITLE = "comic_title"
	const val CURRENT_CHAPTER = "current_chapter"
	const val TOTAL_CHAPTERS = "total_chapters"
	const val CHAPTER_TITLE = "chapter_title"
	const val SOURCE_NAME = "source_name"
	const val AUTHOR = "author"
	const val CURRENT_PAGE = "current_page"
	const val TOTAL_PAGES = "total_pages"
	const val PROGRESS = "progress"

	/** Every placeholder that can be used, in the order they are listed in settings. */
	val ALL = listOf(
		COMIC_URL, CHAPTER_URL, COMIC_TITLE, CURRENT_CHAPTER, TOTAL_CHAPTERS,
		CHAPTER_TITLE, SOURCE_NAME, AUTHOR, CURRENT_PAGE, TOTAL_PAGES, PROGRESS,
	)

	/** Values that already are web addresses, so they go into a link as they are. */
	private val URL_KEYS = setOf(COMIC_URL, CHAPTER_URL)

	private val PLACEHOLDER = Regex("\\{([a-z_]+)}")

	class Resolved(val text: String, val isComplete: Boolean)

	/**
	 * @param values the current value of each placeholder, null when there is none
	 * @param forUrl percent-encodes inserted text (except web addresses) so it is safe in a link
	 */
	fun resolve(template: String, values: Map<String, String?>, forUrl: Boolean): Resolved {
		var complete = true
		val text = PLACEHOLDER.replace(template) { match ->
			val key = match.groupValues[1]
			if (key !in ALL) {
				return@replace match.value
			}
			val value = values[key]
			when {
				value == null -> {
					complete = false
					""
				}

				forUrl && key !in URL_KEYS -> encode(value)
				else -> value
			}
		}
		return Resolved(text, complete)
	}

	/** True when [template] uses [key], to skip work (like finding a chapter link) nobody asked for. */
	fun uses(template: String, key: String): Boolean = template.contains("{$key}")

	private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
