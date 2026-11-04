package org.koitharu.kotatsu.favourites.data

import androidx.room.ColumnInfo

/** Just enough of a favourited manga to tell whether a title from another source is the same one. */
data class FavouriteTitle(
	@ColumnInfo(name = "id") val id: Long,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "alt_titles") val altTitles: String?,
	@ColumnInfo(name = "source") val source: String,
)
