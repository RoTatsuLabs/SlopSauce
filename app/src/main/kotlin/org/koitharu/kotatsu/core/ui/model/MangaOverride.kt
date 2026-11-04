package org.koitharu.kotatsu.core.ui.model

import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaState

data class MangaOverride(
	val coverUrl: String?,
	val title: String?,
	val description: String?,
	val contentRating: ContentRating?,
	/** Tag names that replace the source tags, or null to keep them. */
	val tags: Set<String>? = null,
	/** Author names that replace the source authors, or null to keep them. */
	val authors: Set<String>? = null,
	val state: MangaState? = null,
	/** Rating from 0 to 1, or null to keep the source one. */
	val rating: Float? = null,
)
