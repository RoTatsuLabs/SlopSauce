package org.koitharu.kotatsu.scrobbling.common.domain.model

import org.json.JSONArray

class ScrobblerMangaInfo(
	val id: Long,
	val name: String,
	val cover: String,
	val url: String,
	val descriptionHtml: String,
	/** Total chapters as the tracker knows them, or 0 when it does not publish a count. */
	val totalChapters: Int,
	val genres: List<String> = emptyList(),
	/** Other titles of the manga. Never contains [name]. */
	val altNames: List<String> = emptyList(),
	val authors: List<String> = emptyList(),
	val status: ScrobblerMangaStatus? = null,
	/** Year of the first release, or 0 when unknown. */
	val year: Int = 0,
	/** The tracker's average score from 0 to 10, or 0 when unknown. */
	val score: Float = 0f,
	val isAdult: Boolean = false,
)

/** Reads the strings of a JSON array, or one key of each object in it. Missing or odd data gives an empty list. */
internal fun JSONArray?.stringList(key: String? = null): List<String> {
	if (this == null) {
		return emptyList()
	}
	val result = ArrayList<String>(length())
	for (i in 0 until length()) {
		val value = if (key == null) {
			optString(i, "")
		} else {
			optJSONObject(i)?.optString(key, "").orEmpty()
		}
		if (value.isNotBlank() && value != "null") {
			result.add(value.trim())
		}
	}
	return result
}

/** Alternative titles without blanks, duplicates, or the main title itself. */
internal fun cleanAltNames(name: String, vararg lists: List<String>): List<String> =
	lists.asSequence().flatten()
		.filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
		.distinct()
		.toList()
