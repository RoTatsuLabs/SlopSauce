package org.koitharu.kotatsu.backup.local.domain

import androidx.annotation.StringRes
import org.koitharu.kotatsu.R
import java.util.EnumSet
import java.util.Locale
import java.util.zip.ZipEntry

enum class BackupSection(
	val entryName: String,
	@StringRes val titleResId: Int,
) {

	INDEX("index", R.string.app_name),
	HISTORY("history", R.string.history),
	CATEGORIES("categories", R.string.categories),
	FAVOURITES("favourites", R.string.favourites),
	BOOKMARKS("bookmarks", R.string.bookmarks),
	SETTINGS("settings", R.string.settings),
	SETTINGS_READER_GRID("reader_grid", R.string.reader_actions),
	SOURCES("sources", R.string.remote_sources),
	SOURCE_SETTINGS("source_settings", R.string.backup_include_source_settings),
	SCROBBLING("scrobbling", R.string.tracking),
	STATS("statistics", R.string.reading_stats),
	CHAPTERS("chapters", R.string.chapters),
	FEED("feed", R.string.feed),
	MANGA_PREFS("manga_prefs", R.string.sync_content_covers),
	;

	companion object {

		/** Sections the user can include or leave out; the index is always written. */
		val selectable: List<BackupSection> = entries.filter { it != INDEX }

		fun byEntryName(name: String): BackupSection? = entries.find { it.entryName == name }

		/** Always keeps the index and adds categories when favourites are chosen, as restore requires. */
		fun normalize(selected: Set<BackupSection>): Set<BackupSection> {
			val result = EnumSet.of(INDEX)
			result.addAll(selected)
			if (FAVOURITES in result) {
				result.add(CATEGORIES)
			}
			return result
		}

		fun of(entry: ZipEntry): BackupSection? {
			val name = entry.name.lowercase(Locale.ROOT)
			return entries.find { it.entryName == name }
		}
	}
}
