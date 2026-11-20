package org.koitharu.kotatsu.scrobbling.common.domain

import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus

/**
 * Decides how a tracker's reading status should follow the reading, so it does not have to be kept
 * by hand. Only moves the status forward from what reading implies; never touches a title the user
 * has finished or is not reading, except to start a finished title over from its first chapter.
 */
internal object SmartStatus {

	/**
	 * The status to switch to after reading up to [chapter], or null to leave it as it is.
	 *
	 * @param current the status on the tracker now
	 * @param previous the chapter the tracker had before this one
	 * @param total chapters the tracker knows the title has, or 0 when it does not say
	 * @param lastLocal the last chapter number the source offers
	 * @param localFinished whether the source says the title is finished
	 * @param smart whether the extra rules are on; the Planned to Reading step always applies
	 */
	fun next(
		current: ScrobblingStatus?,
		chapter: Int,
		previous: Int,
		total: Int,
		lastLocal: Int,
		localFinished: Boolean,
		smart: Boolean,
	): ScrobblingStatus? {
		if (chapter <= 0) return null
		var status = current
		// Reading a chapter means it is no longer merely "planned"
		if (status == ScrobblingStatus.PLANNED) status = ScrobblingStatus.READING
		if (smart) {
			// Picking a paused or dropped title up again
			if ((status == ScrobblingStatus.ON_HOLD || status == ScrobblingStatus.DROPPED) && chapter > previous) {
				status = ScrobblingStatus.READING
			}
			// Starting a finished title over from its first chapter. Only chapter 1 counts, so opening a
			// middle chapter of a completed title to look something up never changes its status.
			if (status == ScrobblingStatus.COMPLETED && chapter == 1 && previous > 1) {
				status = ScrobblingStatus.RE_READING
			}
			if (status == ScrobblingStatus.READING || status == ScrobblingStatus.RE_READING) {
				val reachedEnd = when {
					total > 0 -> chapter >= total
					else -> localFinished && lastLocal > 0 && chapter >= lastLocal
				}
				if (reachedEnd) status = ScrobblingStatus.COMPLETED
			}
		}
		return status.takeIf { it != current }
	}
}
