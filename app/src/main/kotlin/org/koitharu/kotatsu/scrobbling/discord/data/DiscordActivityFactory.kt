package org.koitharu.kotatsu.scrobbling.discord.data

import java.util.Locale

/** What the reader is showing right now, as far as Rich Presence is concerned. */
data class ReadingSnapshot(
	val comicTitle: String,
	val comicUrl: String?,
	val chapterUrl: String?,
	val chapterTitle: String?,
	/** The chapter's own number, decimals included ("10.2"). */
	val currentChapter: String,
	/** How many main chapters there are, sub-chapters like 10.1 left out. */
	val totalChapters: Int,
	val sourceName: String,
	val author: String?,
	/** 1-based page of the chapter, or 0 when unknown. */
	val currentPage: Int,
	val totalPages: Int,
	/** 0..100, or -1 when unknown. */
	val progressPercent: Int,
	val coverUrl: String?,
)

/** One of the two buttons as configured in settings. A blank label or link means the default. */
data class DiscordButtonConfig(
	val enabled: Boolean,
	val label: String,
	val url: String,
)

/** Everything settings and resources contribute to the presence, apart from what is being read. */
data class DiscordPresenceOptions(
	val appName: String,
	val customActivityName: String,
	/** `Chapter %1$s of %2$s`; not configurable. */
	val stateFormat: String,
	val largeTextFormat: String,
	val smallText: String,
	val smallImageUrl: String?,
	val button1: DiscordButtonConfig,
	val button1DefaultLabel: String,
	val button1DefaultUrl: String,
	val button2: DiscordButtonConfig,
	val button2DefaultLabel: String,
	val button2DefaultUrl: String,
)

/** Builds the [DiscordNativeActivity] to show. The timestamp is added by the session. */
object DiscordActivityFactory {

	/** Discord rejects activity text and button labels over these lengths. */
	private const val MAX_TEXT_LENGTH = 128
	private const val MAX_BUTTON_LABEL_LENGTH = 32
	private const val MAX_BUTTON_URL_LENGTH = 512

	fun placeholderValues(snapshot: ReadingSnapshot): Map<String, String?> = mapOf(
		DiscordPlaceholders.COMIC_URL to snapshot.comicUrl,
		DiscordPlaceholders.CHAPTER_URL to snapshot.chapterUrl,
		DiscordPlaceholders.COMIC_TITLE to snapshot.comicTitle,
		DiscordPlaceholders.CURRENT_CHAPTER to snapshot.currentChapter,
		DiscordPlaceholders.TOTAL_CHAPTERS to snapshot.totalChapters.toString(),
		DiscordPlaceholders.CHAPTER_TITLE to snapshot.chapterTitle?.takeIf { it.isNotBlank() },
		DiscordPlaceholders.SOURCE_NAME to snapshot.sourceName,
		DiscordPlaceholders.AUTHOR to snapshot.author?.takeIf { it.isNotBlank() },
		DiscordPlaceholders.CURRENT_PAGE to snapshot.currentPage.takeIf { it > 0 }?.toString(),
		DiscordPlaceholders.TOTAL_PAGES to snapshot.totalPages.takeIf { it > 0 }?.toString(),
		DiscordPlaceholders.PROGRESS to snapshot.progressPercent.takeIf { it >= 0 }?.let { "$it%" },
	)

	fun create(snapshot: ReadingSnapshot, options: DiscordPresenceOptions): DiscordNativeActivity {
		val values = placeholderValues(snapshot)
		val buttons = listOfNotNull(
			resolveButton(options.button1, options.button1DefaultLabel, options.button1DefaultUrl, values),
			resolveButton(options.button2, options.button2DefaultLabel, options.button2DefaultUrl, values),
		)
		return DiscordNativeActivity(
			name = options.customActivityName.trim().ifEmpty { options.appName }.take(MAX_TEXT_LENGTH),
			details = sanitize(snapshot.comicTitle),
			state = String.format(
				Locale.ROOT,
				options.stateFormat,
				snapshot.currentChapter,
				snapshot.totalChapters.toString(),
			),
			largeImage = snapshot.coverUrl?.takeIf(::isWebUrl),
			largeText = sanitize(String.format(Locale.ROOT, options.largeTextFormat, snapshot.comicTitle)),
			smallImage = options.smallImageUrl?.takeIf(::isWebUrl),
			smallText = options.smallText,
			button1Label = buttons.getOrNull(0)?.first,
			button1Url = buttons.getOrNull(0)?.second,
			button2Label = buttons.getOrNull(1)?.first,
			button2Url = buttons.getOrNull(1)?.second,
		)
	}

	/**
	 * The label and link of a button, or null when it is switched off or cannot be shown: its
	 * label is empty, its link is not a web address, or a placeholder in either has no value.
	 */
	fun resolveButton(
		config: DiscordButtonConfig,
		defaultLabel: String,
		defaultUrl: String,
		values: Map<String, String?>,
	): Pair<String, String>? {
		if (!config.enabled) return null
		val label = DiscordPlaceholders.resolve(
			config.label.ifBlank { defaultLabel },
			values,
			forUrl = false,
		)
		val url = DiscordPlaceholders.resolve(
			config.url.trim().ifBlank { defaultUrl },
			values,
			forUrl = true,
		)
		if (!label.isComplete || !url.isComplete) return null
		val text = label.text.trim().take(MAX_BUTTON_LABEL_LENGTH).trim()
		val link = url.text.trim()
		if (text.isEmpty() || !isWebUrl(link) || link.length > MAX_BUTTON_URL_LENGTH) return null
		return text to link
	}

	private fun isWebUrl(url: String) =
		url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

	/** Discord rejects blank, single-character and over-long text. */
	private fun sanitize(value: String): String? {
		val trimmed = value.trim()
		return when {
			trimmed.isEmpty() -> null
			trimmed.length < 2 -> "$trimmed "
			else -> trimmed.take(MAX_TEXT_LENGTH)
		}
	}
}
