package org.koitharu.kotatsu.scrobbling.discord.data

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single Rich Presence session of the app. Readers come and go (each reader screen has its
 * own `DiscordRpc`), so the connection, the elapsed-time counter and the delayed teardown live in
 * one place. See [DiscordPresenceController] for the rules.
 */
@Singleton
class DiscordRpcSession @Inject constructor(
	@ApplicationContext context: Context,
	settings: AppSettings,
	auth: DiscordAuth,
) {

	private val controller = DiscordPresenceController(
		transport = SdkDiscordTransport(context, auth),
		scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
		elapsedRealtime = SystemClock::elapsedRealtime,
		epochSeconds = { System.currentTimeMillis() / 1000 },
		config = {
			DiscordPresenceConfig(
				isEnabled = settings.isDiscordRpcEnabled,
				isSignedIn = auth.isSignedIn,
				isReadingPresence = settings.isDiscordReadingPresence,
			)
		},
	)

	fun enter(owner: Any) = controller.enter(owner)

	fun update(
		owner: Any,
		showTimestamp: Boolean,
		build: suspend () -> DiscordNativeActivity?,
	) = controller.update(owner, showTimestamp, idle = false, build)

	fun setIdle(owner: Any) = controller.setIdle(owner)

	fun leave(owner: Any) = controller.leave(owner)

	fun release(owner: Any) = controller.release(owner)

	fun stop(owner: Any) = controller.stop(owner)
}

/** Talks to Discord through the Social SDK. */
private class SdkDiscordTransport(
	private val context: Context,
	private val auth: DiscordAuth,
) : DiscordPresenceTransport {

	override val isActive: Boolean
		get() = DiscordRpcManager.isActive

	override suspend fun connect(): Boolean {
		val token = auth.validAccessToken() ?: return false
		if (!DiscordRpcManager.init(context)) return false
		DiscordRpcManager.connect(token)
		return true
	}

	override fun setOnlineStatus(status: PresenceStatus) = DiscordRpcManager.setOnlineStatus(
		when (status) {
			PresenceStatus.Online -> DiscordRpcManager.OnlineStatus.Online
			PresenceStatus.Idle -> DiscordRpcManager.OnlineStatus.Idle
			PresenceStatus.DoNotDisturb -> DiscordRpcManager.OnlineStatus.DoNotDisturb
		},
	)

	override fun setActivity(activity: DiscordNativeActivity) = DiscordRpcManager.setActivity(activity)

	override fun disconnect(restoreStatus: Boolean) = DiscordRpcManager.disconnect(restoreStatus)
}
