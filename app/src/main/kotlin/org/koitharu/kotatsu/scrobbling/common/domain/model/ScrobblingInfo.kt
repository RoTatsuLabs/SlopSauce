package org.koitharu.kotatsu.scrobbling.common.domain.model

import org.koitharu.kotatsu.list.ui.ListModelDiffCallback
import org.koitharu.kotatsu.list.ui.model.ListModel

data class ScrobblingInfo(
	val scrobbler: ScrobblerService,
	val mangaId: Long,
	val targetId: Long,
	val status: ScrobblingStatus?,
	val chapter: Int,
	val totalChapters: Int,
	val comment: String?,
	val rating: Float,
	val title: String,
	val coverUrl: String,
	val description: CharSequence?,
	val externalUrl: String,
	val genres: List<String> = emptyList(),
	val altNames: List<String> = emptyList(),
	val authors: List<String> = emptyList(),
	val mangaStatus: ScrobblerMangaStatus? = null,
	val year: Int = 0,
	val score: Float = 0f,
	val isAdult: Boolean = false,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ScrobblingInfo && other.scrobbler == scrobbler
	}

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is ScrobblingInfo -> null
		previousState.status != status || previousState.rating != rating -> ListModelDiffCallback.PAYLOAD_ANYTHING_CHANGED
		else -> super.getChangePayload(previousState)
	}
}
