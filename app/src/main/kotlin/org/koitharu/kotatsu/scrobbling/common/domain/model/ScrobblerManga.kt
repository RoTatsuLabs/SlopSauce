package org.koitharu.kotatsu.scrobbling.common.domain.model

import org.koitharu.kotatsu.list.ui.model.ListModel

data class ScrobblerManga(
	val id: Long,
	val name: String,
	val altName: String?,
	val cover: String?,
	val url: String,
	val isBestMatch: Boolean,
	/** Every other name the tracker knows (synonyms, romaji, English). */
	val names: List<String> = emptyList(),
	/** ISO country of origin, when the tracker says it. */
	val country: String? = null,
	val format: String? = null,
	/** Genres and tags, used to tell Boys' and Girls' Love apart. */
	val labels: List<String> = emptyList(),
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ScrobblerManga && other.id == id
	}

	override fun toString(): String {
		return "ScrobblerManga #$id \"$name\" $url"
	}
}
