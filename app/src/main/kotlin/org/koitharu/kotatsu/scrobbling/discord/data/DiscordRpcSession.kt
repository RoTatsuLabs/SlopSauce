package org.koitharu.kotatsu.scrobbling.discord.data

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

private const val DEBOUNCE_TIMEOUT = 16_000L // 16 sec

/**
 * The single Rich Presence session of the app. Readers come and go (each reader screen has its
 * own `DiscordRpc`), so connection state and the presence timestamp live here instead.
 *
 * Every call carries the `owner` that made it. A reader that is closing can run its teardown
 * after the next reader already started, and must not cut that one's session short.
 */
@Singleton
class DiscordRpcSession @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val auth: DiscordAuth,
) {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private var owner: Any? = null
	private var updateJob: Job? = null
	private var lastUpdate = 0L

	@Volatile
	private var lastActivity: DiscordNativeActivity? = null
	private var startSecs = 0L

	/**
	 * Publishes [activity] (debounced) on behalf of [owner]. The elapsed-time counter starts with
	 * the first activity of a session and then keeps running across updates.
	 */
	fun update(owner: Any, activity: DiscordNativeActivity, idle: Boolean = false) {
		if (!settings.isDiscordRpcEnabled) {
			stop(owner)
			return
		}
		if (!auth.isSignedIn) {
			return
		}
		synchronized(this) {
			this.owner = owner
			val previous = updateJob
			updateJob = scope.launch {
				previous?.cancelAndJoin()
				val debounceTime = lastUpdate + DEBOUNCE_TIMEOUT - SystemClock.elapsedRealtime()
				if (debounceTime > 0) {
					delay(debounceTime)
				}
				publish(activity, idle)
				lastUpdate = SystemClock.elapsedRealtime()
			}
		}
	}

	/** Shows the last published activity again, with the user marked as idle. */
	fun setIdle(owner: Any) {
		val activity = synchronized(this) {
			if (this.owner !== owner) return
			lastActivity
		} ?: return
		update(owner, activity, idle = true)
	}

	/** Takes the presence down for [owner]. The timestamp is kept for when it comes back. */
	fun stop(owner: Any) {
		synchronized(this) {
			if (this.owner !== owner) return
			updateJob?.cancel()
			updateJob = null
			lastUpdate = 0L
			DiscordRpcManager.disconnect(restoreStatus = true)
		}
	}

	/** Ends the session of [owner] for good, so that the next one starts a fresh timestamp. */
	fun release(owner: Any) {
		stop(owner)
		synchronized(this) {
			if (this.owner !== owner) return
			this.owner = null
			lastActivity = null
			startSecs = 0L
		}
	}

	private suspend fun publish(activity: DiscordNativeActivity, idle: Boolean) {
		if (!DiscordRpcManager.isActive) {
			val token = auth.validAccessToken() ?: return
			if (!DiscordRpcManager.init(context)) return
			DiscordRpcManager.connect(token)
		}
		val timestamped = synchronized(this) {
			if (startSecs == 0L) startSecs = System.currentTimeMillis() / 1000
			activity.copy(startTimestamp = startSecs)
		}
		lastActivity = timestamped
		DiscordRpcManager.setOnlineStatus(
			if (idle) DiscordRpcManager.OnlineStatus.Idle else DiscordRpcManager.OnlineStatus.Online,
		)
		DiscordRpcManager.setActivity(timestamped)
	}
}
