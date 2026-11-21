package org.koitharu.kotatsu.tracker.domain

import androidx.annotation.StringRes
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.mihon.MihonSourceMetadataStore
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaState

/**
 * Why the tracker would not spend a network request on an entry, or `null` if it would check it.
 * The actual filtering happens in `TracksDao.findAllForChecking` — this mirrors it for the
 * tracker debug screen, so keep the two in sync.
 */
@StringRes
fun smartUpdateSkipReason(
	rules: Set<String>,
	state: MangaState?,
	hasProgress: Boolean,
	newChapters: Int,
	fetchOnce: Boolean = false,
): Int? = when {
	fetchOnce -> R.string.smart_update_skip_fetch_once
	rules.isEmpty() -> null
	AppSettings.SMART_UPDATE_SKIP_COMPLETED in rules && state == MangaState.FINISHED ->
		R.string.smart_update_skip_completed

	AppSettings.SMART_UPDATE_SKIP_UNSTARTED in rules && !hasProgress ->
		R.string.smart_update_skip_unstarted

	AppSettings.SMART_UPDATE_SKIP_UNREAD in rules && newChapters > 0 ->
		R.string.smart_update_skip_unread

	else -> null
}

/**
 * Whether the extension marked this title as fetch-once and its chapters were already fetched.
 * Mihon skips those in library updates; a title never fetched yet still gets its first check.
 * Not part of the tracks query because the strategy lives in the source metadata store.
 */
internal fun MihonSourceMetadataStore.isFetchedOnce(manga: Manga, hasFetchedChapters: Boolean): Boolean {
	if (!hasFetchedChapters) return false
	val sourceId = manga.source.name
		.takeIf { it.startsWith("MIHON_") }
		?.removePrefix("MIHON_")
		?.substringBefore(':')
		?.toLongOrNull()
		?: return false
	return isFetchOnce(sourceId, manga.url)
}
