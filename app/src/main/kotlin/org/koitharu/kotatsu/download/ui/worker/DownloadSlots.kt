package org.koitharu.kotatsu.download.ui.worker

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caps how many downloads run at once and decides which waiting download goes next. The limit is
 * read again on every check, so changing the setting takes effect for downloads that are still
 * waiting. The waiting order can be changed with [moveToTop] and [moveToEnd].
 */
@Singleton
class DownloadSlots @Inject constructor() {

	private val lock = Mutex()
	private var running = 0
	private val order = ConcurrentHashMap<UUID, Long>()
	private val waiting = ConcurrentHashMap.newKeySet<UUID>()
	private val tail = AtomicLong(0)
	private val head = AtomicLong(0)

	/** Changes whenever the waiting queue changes, so lists can sort again. */
	val changes = MutableStateFlow(0)

	/** Position key of a waiting download: the smaller, the sooner it starts. */
	fun orderOf(id: UUID): Long = order[id] ?: Long.MAX_VALUE

	fun moveToTop(ids: List<UUID>) {
		ids.sortedBy(::orderOf).asReversed().forEach { order[it] = head.decrementAndGet() }
		changes.update { it + 1 }
	}

	fun moveToEnd(ids: List<UUID>) {
		ids.sortedBy(::orderOf).forEach { order[it] = tail.incrementAndGet() }
		changes.update { it + 1 }
	}

	/** Waits until a slot is free and it is [id]'s turn, then runs [block]. [onWaiting] is called once if it has to wait. */
	suspend fun <T> withSlot(
		id: UUID,
		limit: () -> Int,
		onWaiting: suspend () -> Unit,
		block: suspend () -> T,
	): T {
		order.putIfAbsent(id, tail.incrementAndGet())
		waiting.add(id)
		changes.update { it + 1 }
		var notified = false
		try {
			while (!tryAcquire(id, limit())) {
				if (!notified) {
					notified = true
					onWaiting()
				}
				delay(POLL_INTERVAL)
			}
		} catch (e: Throwable) {
			waiting.remove(id)
			order.remove(id)
			changes.update { it + 1 }
			throw e
		}
		changes.update { it + 1 }
		try {
			return block()
		} finally {
			lock.withLock { running-- }
			order.remove(id)
		}
	}

	private suspend fun tryAcquire(id: UUID, limit: Int): Boolean = lock.withLock {
		if (running < limit && waiting.minByOrNull(::orderOf) == id) {
			running++
			waiting.remove(id)
			true
		} else {
			false
		}
	}

	private companion object {

		const val POLL_INTERVAL = 1_000L
	}
}
