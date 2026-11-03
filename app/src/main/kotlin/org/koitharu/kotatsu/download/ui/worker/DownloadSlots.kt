package org.koitharu.kotatsu.download.ui.worker

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caps how many downloads run at once. The limit is read again on every check, so changing the
 * setting takes effect for downloads that are still waiting.
 */
@Singleton
class DownloadSlots @Inject constructor() {

	private val lock = Mutex()
	private var running = 0

	/** Waits until a slot is free, then runs [block]. [onWaiting] is called once if it has to wait. */
	suspend fun <T> withSlot(limit: () -> Int, onWaiting: suspend () -> Unit, block: suspend () -> T): T {
		var notified = false
		while (!tryAcquire(limit())) {
			if (!notified) {
				notified = true
				onWaiting()
			}
			delay(POLL_INTERVAL)
		}
		try {
			return block()
		} finally {
			lock.withLock { running-- }
		}
	}

	private suspend fun tryAcquire(limit: Int): Boolean = lock.withLock {
		if (running < limit) {
			running++
			true
		} else {
			false
		}
	}

	private companion object {

		const val POLL_INTERVAL = 1_000L
	}
}
