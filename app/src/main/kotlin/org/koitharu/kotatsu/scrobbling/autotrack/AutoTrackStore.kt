package org.koitharu.kotatsu.scrobbling.autotrack

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import javax.inject.Inject
import javax.inject.Singleton

/** What auto-tracking could not decide on its own, and how the last run went. */
@Singleton
class AutoTrackStore @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences("auto_track", Context.MODE_PRIVATE)

	data class Summary(val tracked: Int, val needsReview: Int, val failed: Int, val total: Int)

	/** Titles with no clear match on a tracker; they are left alone until picked by hand. */
	fun addReview(mangaId: Long, service: ScrobblerService) = synchronized(this) {
		prefs.edit { putStringSet(KEY_REVIEW, reviewKeys() + key(mangaId, service)) }
	}

	fun removeReview(mangaId: Long, service: ScrobblerService) = synchronized(this) {
		prefs.edit { putStringSet(KEY_REVIEW, reviewKeys() - key(mangaId, service)) }
	}

	fun isReview(mangaId: Long, service: ScrobblerService): Boolean = key(mangaId, service) in reviewKeys()

	/** (manga id, service id) pairs waiting for a manual pick. */
	fun reviewItems(): List<Pair<Long, Int>> = reviewKeys().mapNotNull {
		val parts = it.split(':')
		val service = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
		val manga = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
		manga to service
	}

	fun clearReview() = prefs.edit { remove(KEY_REVIEW) }

	var lastSummary: Summary?
		get() = if (prefs.contains(KEY_TOTAL)) {
			Summary(
				tracked = prefs.getInt(KEY_TRACKED, 0),
				needsReview = prefs.getInt(KEY_REVIEW_COUNT, 0),
				failed = prefs.getInt(KEY_FAILED, 0),
				total = prefs.getInt(KEY_TOTAL, 0),
			)
		} else {
			null
		}
		set(value) = prefs.edit {
			if (value == null) {
				remove(KEY_TOTAL)
			} else {
				putInt(KEY_TRACKED, value.tracked)
				putInt(KEY_REVIEW_COUNT, value.needsReview)
				putInt(KEY_FAILED, value.failed)
				putInt(KEY_TOTAL, value.total)
			}
		}

	private fun reviewKeys(): Set<String> = prefs.getStringSet(KEY_REVIEW, null).orEmpty().toSet()

	private fun key(mangaId: Long, service: ScrobblerService) = "${service.id}:$mangaId"

	private companion object {

		const val KEY_REVIEW = "review"
		const val KEY_TRACKED = "tracked"
		const val KEY_REVIEW_COUNT = "review_count"
		const val KEY_FAILED = "failed"
		const val KEY_TOTAL = "total"
	}
}
