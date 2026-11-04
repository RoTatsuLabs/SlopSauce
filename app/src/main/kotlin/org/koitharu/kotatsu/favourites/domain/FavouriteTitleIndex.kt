package org.koitharu.kotatsu.favourites.domain

import dagger.Reusable
import org.koitharu.kotatsu.core.db.MangaDatabase
import java.util.Locale
import javax.inject.Inject

/**
 * Finds out whether a title from one source is already favourited from another. Titles are compared
 * ignoring case and symbols, so "Basara", "BASARA" and "Basara!" are one title; alternative names count too.
 */
@Reusable
class FavouriteTitleIndex @Inject constructor(
	private val db: MangaDatabase,
) {

	suspend fun load(): Snapshot {
		val map = HashMap<String, MutableList<Entry>>()
		for (item in db.getFavouritesDao().findTitles()) {
			val entry = Entry(item.id, item.source)
			val names = sequenceOf(item.title) + item.altTitles.orEmpty().splitToSequence('\n')
			for (key in names.mapNotNull(::normalize).distinct()) {
				map.getOrPut(key) { ArrayList(1) }.add(entry)
			}
		}
		return Snapshot(map)
	}

	data class Entry(val mangaId: Long, val sourceName: String)

	class Snapshot internal constructor(private val byKey: Map<String, List<Entry>>) {

		val isEmpty: Boolean
			get() = byKey.isEmpty()

		/** A favourite with the same title that is not [mangaId] itself, or null. */
		fun find(mangaId: Long, title: String, altTitles: Collection<String>): Entry? {
			if (byKey.isEmpty()) return null
			val keys = (sequenceOf(title) + altTitles.asSequence()).mapNotNull(::normalize)
			for (key in keys) {
				val found = byKey[key]?.firstOrNull { it.mangaId != mangaId }
				if (found != null) return found
			}
			return null
		}
	}

	companion object {

		val EMPTY = Snapshot(emptyMap())

		internal fun normalize(title: String): String? =
			title.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.takeIf { it.isNotEmpty() }
	}
}
