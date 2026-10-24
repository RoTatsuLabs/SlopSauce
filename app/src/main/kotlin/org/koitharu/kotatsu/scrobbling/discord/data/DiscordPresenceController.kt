package org.koitharu.kotatsu.scrobbling.discord.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The user's online status, as far as Rich Presence changes it. */
enum class PresenceStatus { Online, Idle, DoNotDisturb }

/** The connection to Discord that [DiscordPresenceController] drives. */
interface DiscordPresenceTransport {

	/** True while connecting or connected. */
	val isActive: Boolean

	/** Starts connecting. False when that is not possible, e.g. nobody is signed in. */
	suspend fun connect(): Boolean

	fun setOnlineStatus(status: PresenceStatus)

	fun setActivity(activity: DiscordNativeActivity)

	/** Takes the presence down. With [restoreStatus] the user's own online status is put back. */
	fun disconnect(restoreStatus: Boolean)
}

/** What the controller needs to know from settings, read again on every call. */
data class DiscordPresenceConfig(
	val isEnabled: Boolean,
	val isSignedIn: Boolean,
	/** The "Reading presence" setting: DND/Idle/Online handling and the delayed teardown. */
	val isReadingPresence: Boolean,
)

/**
 * Decides what the app's one Rich Presence session does as readers come and go.
 *
 * Every call names the reader it comes from, its `owner`. A reader that is closing can run its
 * teardown after the next one has started, and must not touch that one's session.
 *
 * With "reading presence" on: the user is Do Not Disturb while reading and Idle once the reader is
 * open but untouched. Leaving the reader restores Online and ends the session after
 * [leaveDebounceMs]; coming back before that keeps the session and its timestamp. With it off,
 * the user's online status is never touched and leaving ends the session at once.
 *
 * Updates to the presence itself are spaced at least [updateDebounceMs] apart.
 */
class DiscordPresenceController(
	private val transport: DiscordPresenceTransport,
	private val scope: CoroutineScope,
	private val elapsedRealtime: () -> Long,
	private val epochSeconds: () -> Long,
	private val config: () -> DiscordPresenceConfig,
	private val updateDebounceMs: Long = UPDATE_DEBOUNCE_MS,
	private val leaveDebounceMs: Long = LEAVE_DEBOUNCE_MS,
) {

	private val lock = Any()
	private var owner: Any? = null
	private var updateJob: Job? = null
	private var teardownJob: Job? = null
	private var teardownGeneration = 0
	private var lastUpdate: Long? = null
	private var startSecs = 0L
	private var status = PresenceStatus.Online
	private var statusPushed = false
	private var lastBuild: (suspend () -> DiscordNativeActivity?)? = null
	private var lastShowTimestamp = true

	/** The reader of [owner] is on screen. A teardown that is waiting to run is cancelled. */
	fun enter(owner: Any) = synchronized(lock) {
		this.owner = owner
		cancelTeardownLocked()
	}

	/**
	 * Shows what [build] returns, at most once per debounce period (the latest call wins). It is
	 * called when the presence is about to be published, so it sees the freshest reading state.
	 * The elapsed-time counter starts with the first activity of a session and keeps running
	 * across updates; [showTimestamp] only decides whether it is shown.
	 */
	fun update(
		owner: Any,
		showTimestamp: Boolean,
		idle: Boolean = false,
		build: suspend () -> DiscordNativeActivity?,
	) {
		val config = config()
		if (!config.isEnabled) {
			stop(owner)
			return
		}
		if (!config.isSignedIn) {
			return
		}
		synchronized(lock) {
			this.owner = owner
			cancelTeardownLocked()
			lastBuild = build
			lastShowTimestamp = showTimestamp
			if (config.isReadingPresence) {
				requestStatusLocked(if (idle) PresenceStatus.Idle else PresenceStatus.DoNotDisturb)
			}
			val previous = updateJob
			updateJob = scope.launch {
				previous?.cancelAndJoin()
				val last = lastUpdate
				if (last != null) {
					val wait = last + updateDebounceMs - elapsedRealtime()
					if (wait > 0) delay(wait)
				}
				publish(build, showTimestamp)
				lastUpdate = elapsedRealtime()
			}
		}
	}

	/** The reader is open but nobody is reading. */
	fun setIdle(owner: Any) {
		synchronized(lock) {
			if (this.owner !== owner || !config().isReadingPresence) return
			requestStatusLocked(PresenceStatus.Idle)
		}
	}

	/** The reader of [owner] left the screen. */
	fun leave(owner: Any) {
		if (!config().isReadingPresence) {
			stop(owner)
			return
		}
		synchronized(lock) {
			if (this.owner !== owner) return
			scheduleTeardownLocked()
		}
	}

	/** The reader of [owner] is gone for good. */
	fun release(owner: Any) {
		if (config().isReadingPresence) {
			synchronized(lock) {
				if (this.owner !== owner) return
				if (teardownJob == null) scheduleTeardownLocked()
			}
			return
		}
		synchronized(lock) {
			if (this.owner !== owner) return
			teardownLocked()
			this.owner = null
			lastBuild = null
			startSecs = 0L
		}
	}

	/** Takes the presence down at once. The timestamp is kept for when it comes back. */
	fun stop(owner: Any) {
		synchronized(lock) {
			if (this.owner !== owner) return
			cancelTeardownLocked()
			teardownLocked()
		}
	}

	private suspend fun publish(build: suspend () -> DiscordNativeActivity?, showTimestamp: Boolean) {
		val activity = build() ?: return
		if (!transport.isActive && !transport.connect()) return
		val job = currentCoroutineContext()[Job]
		synchronized(lock) {
			// A teardown that ran while connecting has cancelled this job: don't bring the presence back.
			if (job?.isActive == false) return
			if (startSecs == 0L) startSecs = epochSeconds()
			if (config().isReadingPresence) {
				transport.setOnlineStatus(status)
				statusPushed = true
			}
			transport.setActivity(activity.copy(startTimestamp = if (showTimestamp) startSecs else 0L))
		}
	}

	private fun requestStatusLocked(newStatus: PresenceStatus) {
		if (newStatus == status && statusPushed) return
		status = newStatus
		if (transport.isActive) {
			transport.setOnlineStatus(newStatus)
			statusPushed = true
		}
	}

	private fun scheduleTeardownLocked() {
		cancelTeardownLocked()
		val generation = ++teardownGeneration
		teardownJob = scope.launch {
			delay(leaveDebounceMs)
			synchronized(lock) {
				if (generation != teardownGeneration) return@launch
				teardownJob = null
				teardownLocked()
				owner = null
				lastBuild = null
				startSecs = 0L
			}
		}
	}

	private fun cancelTeardownLocked() {
		teardownGeneration++
		teardownJob?.cancel()
		teardownJob = null
	}

	/** Cancels pending work and drops the connection, putting the user's own status back. */
	private fun teardownLocked() {
		updateJob?.cancel()
		updateJob = null
		lastUpdate = null
		status = PresenceStatus.Online
		val restore = statusPushed
		statusPushed = false
		transport.disconnect(restoreStatus = restore)
	}

	companion object {

		const val UPDATE_DEBOUNCE_MS = 16_000L
		const val LEAVE_DEBOUNCE_MS = 5_000L
	}
}
