package org.koitharu.kotatsu.scrobbling.discord.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.discord.socialsdk.AuthenticationClientCallback
import com.discord.socialsdk.DiscordSocialSdkInit
import com.discord.socialsdk.NativeCalls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koitharu.kotatsu.BuildConfig
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/** OAuth2 tokens as returned by Discord's token endpoint. */
data class DiscordTokens(
	val accessToken: String,
	val refreshToken: String?,
	/** Epoch milliseconds, or 0 when Discord did not say. */
	val expiresAtMillis: Long,
)

/** A Discord user profile, fetched right after a successful authorization. */
data class DiscordUser(
	val id: String,
	val username: String,
	val name: String,
)

/** Outcome of exchanging an authorization code or refreshing tokens. */
sealed interface DiscordTokenResult {

	data class Success(val tokens: DiscordTokens) : DiscordTokenResult

	/** Discord refused the request, e.g. the refresh token was revoked or has expired. */
	data object Rejected : DiscordTokenResult

	/** Network trouble or an unexpected response; trying again later may work. */
	data object Failed : DiscordTokenResult
}

/**
 * Drives Discord Rich Presence through the official Social SDK, over JNI to discord_bridge.cpp.
 *
 * It handles one account. [init] loads the native library and starts the callback pump,
 * [authorize] runs the OAuth2 PKCE flow, and [connect] opens the SDK client with an access
 * token. The presence and online status that were requested are remembered and (re)applied
 * whenever the client becomes ready, so callers never have to wait for the connection.
 */
object DiscordRpcManager {

	private const val TAG = "DiscordRpcManager"
	private const val SCOPES = "identify openid sdk.social_layer_presence"
	private const val AUTH_URL = "https://discord.com/oauth2/authorize"
	private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
	private const val USER_URL = "https://discord.com/api/v10/users/@me"
	private const val CALLBACK_INTERVAL_MS = 500L
	private const val HTTP_TIMEOUT_MS = 10_000

	/** Time the native client gets to send the restored online status before it disconnects. */
	private const val STATUS_RESTORE_GRACE_MS = 1_000L

	private val appId = BuildConfig.DISCORD_APP_ID

	// Discord's fixed format for mobile OAuth2 redirects. It must be registered verbatim under
	// OAuth2 > Redirects of the Discord application.
	private val redirectUri = "discord-$appId:/authorize/callback"

	enum class Status { Disconnected, Connecting, Connected }

	enum class OnlineStatus(val value: Int) {
		Online(0),
		Idle(3),
		DoNotDisturb(4),
	}

	private val lock = Any()

	/** Serializes the callback pump with native teardown: the SDK must not be freed mid-callback. */
	private val nativeLock = Any()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val mainHandler = Handler(Looper.getMainLooper())

	@Volatile
	private var initialized = false

	@Volatile
	private var ready = false

	@Volatile
	private var hasEngineActivity = false

	@Volatile
	private var pendingToken: String? = null

	@Volatile
	private var desiredActivity: DiscordNativeActivity? = null

	@Volatile
	private var desiredStatus: OnlineStatus? = null

	@Volatile
	private var statusOverridden = false

	@Volatile
	private var previousStatus: Int? = null

	/** True between restoring the user's status and the delayed disconnect that follows it. */
	@Volatile
	private var disconnecting = false

	private var callbackJob: Job? = null
	private var lifecycleRegistered = false
	@Volatile
	private var lastActivity: WeakReference<Activity>? = null

	// Bumped on every connect so a pending delayed disconnect can tell it has been superseded.
	private val connectionEpoch = AtomicInteger()

	private val _status = MutableStateFlow(Status.Disconnected)
	val status: StateFlow<Status> = _status

	/** True while connecting or connected, but not while a disconnect is on its way. */
	val isActive: Boolean
		get() = _status.value != Status.Disconnected && !disconnecting

	val isReady: Boolean
		get() = ready

	/** Called from discord_bridge.cpp whenever the native client's connection status changes. */
	@JvmStatic
	@Suppress("unused")
	private fun onNativeStatusChanged(statusCode: Int, ready: Boolean, authorized: Boolean) {
		this.ready = ready
		_status.value = when {
			ready && authorized -> Status.Connected
			!ready && !authorized -> Status.Disconnected
			else -> Status.Connecting
		}
		Log.d(TAG, "status=$statusCode ready=$ready authorized=$authorized")
		if (ready && authorized) {
			scope.launch { applyDesired() }
		}
	}

	private external fun nativeInit(appId: Long): Boolean
	private external fun nativeSetTokenAndConnect(token: String)
	private external fun nativeSetActivity(
		activityType: Int,
		name: String?,
		state: String?,
		details: String?,
		startSecs: Long,
		endSecs: Long,
		largeImage: String?,
		largeText: String?,
		smallImage: String?,
		smallText: String?,
		button1Label: String?,
		button1Url: String?,
		button2Label: String?,
		button2Url: String?,
	)

	private external fun nativeSetOnlineStatus(statusType: Int)
	private external fun nativeGetOnlineStatus(): Int
	private external fun nativeClear()
	private external fun nativeDisconnect()
	private external fun nativeRunCallbacks()
	private external fun nativeDestroy()

	/** Loads the native library and starts the callback pump. Safe to call more than once. */
	fun init(context: Context, activity: Activity? = null): Boolean = synchronized(lock) {
		if (initialized) return true
		try {
			System.loadLibrary("slopsauce_discord")
		} catch (e: UnsatisfiedLinkError) {
			Log.e(TAG, "Failed to load slopsauce_discord", e)
			return false
		}
		trackActivities(context.applicationContext as Application)
		// The SDK looks for the Discord app as soon as its client exists, and that needs an
		// activity already, so one is handed over before the client is created.
		val engineActivity = activity ?: lastActivity?.get()
		if (engineActivity == null) {
			Log.w(TAG, "No activity to hand to the SDK")
			return false
		}
		attachActivity(engineActivity)
		if (!nativeInit(appId)) {
			Log.w(TAG, "nativeInit returned false")
			return false
		}
		initialized = true
		callbackJob = scope.launch {
			while (isActive) {
				try {
					synchronized(nativeLock) {
						if (initialized) nativeRunCallbacks()
					}
				} catch (e: Exception) {
					Log.w(TAG, "nativeRunCallbacks threw", e)
				}
				delay(CALLBACK_INTERVAL_MS)
			}
		}
		true
	}

	/**
	 * The SDK needs a live Activity to resolve a Context (e.g. while checking for the Discord
	 * app or connecting) and fails without one. The foreground activity is remembered from app
	 * start, so it is known even when the presence starts inside an already open screen.
	 */
	fun trackActivities(app: Application) {
		if (lifecycleRegistered) return
		lifecycleRegistered = true
		app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
			override fun onActivityResumed(activity: Activity) {
				lastActivity = WeakReference(activity)
				if (initialized) attachActivity(activity)
			}

			override fun onActivityDestroyed(activity: Activity) {
				if (lastActivity?.get() === activity) lastActivity = null
			}

			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityPaused(activity: Activity) = Unit
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
		})
	}

	/** Hands [activity] to the SDK, and starts a connection that was waiting for one. */
	fun attachActivity(activity: Activity) {
		try {
			DiscordSocialSdkInit.setEngineActivity(activity)
		} catch (e: Throwable) {
			Log.w(TAG, "setEngineActivity failed", e)
			return
		}
		hasEngineActivity = true
		val token = pendingToken ?: return
		pendingToken = null
		if (initialized) connect(token)
	}

	/**
	 * Opens the Discord app (or a browser) for OAuth2 PKCE consent and exchanges the resulting
	 * code for tokens. [onComplete] runs on the main thread, with null on failure or cancellation.
	 */
	fun authorize(onComplete: (DiscordTokens?) -> Unit) {
		if (!initialized || !hasEngineActivity) {
			onComplete(null)
			return
		}
		val verifier = randomUrlSafe(64)
		val expectedState = randomUrlSafe(16)
		val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
		val oauthUrl = AUTH_URL +
			"?client_id=$appId" +
			"&response_type=code" +
			"&redirect_uri=${encode(redirectUri)}" +
			"&scope=${encode(SCOPES)}" +
			"&state=$expectedState" +
			"&code_challenge_method=S256" +
			"&code_challenge=$challenge"

		var finished = false
		fun finish(tokens: DiscordTokens?) {
			mainHandler.post { onComplete(tokens) }
		}
		val callback = object : AuthenticationClientCallback(0) {
			override fun onAuthorizationComplete(error: String?, authCode: String?, returnedState: String?) {
				if (finished) return
				finished = true
				val stateMismatch = returnedState != null && returnedState != expectedState
				if (!error.isNullOrEmpty() || authCode.isNullOrEmpty() || stateMismatch) {
					Log.w(TAG, "Authorization failed or cancelled: $error")
					finish(null)
					return
				}
				scope.launch {
					val result = requestTokens(
						"grant_type=authorization_code" +
							"&code=${encode(authCode)}" +
							"&redirect_uri=${encode(redirectUri)}" +
							"&code_verifier=$verifier",
					)
					finish((result as? DiscordTokenResult.Success)?.tokens)
				}
			}
		}
		try {
			NativeCalls.authorize(oauthUrl, callback)
		} catch (e: Throwable) {
			Log.e(TAG, "NativeCalls.authorize threw", e)
			finish(null)
		}
	}

	/** Exchanges [refreshToken] for a new set of tokens. */
	suspend fun refreshTokens(refreshToken: String): DiscordTokenResult = requestTokens(
		"grant_type=refresh_token&refresh_token=${encode(refreshToken)}",
	)

	/** Fetches the profile for [accessToken]. Blocking; call it off the main thread. */
	fun fetchCurrentUser(accessToken: String): DiscordUser? = try {
		val conn = URL(USER_URL).openConnection() as HttpURLConnection
		try {
			conn.connectTimeout = HTTP_TIMEOUT_MS
			conn.readTimeout = HTTP_TIMEOUT_MS
			conn.setRequestProperty("Authorization", "Bearer $accessToken")
			conn.setRequestProperty("Accept", "application/json")
			if (conn.responseCode !in 200..299) {
				Log.w(TAG, "fetchCurrentUser: HTTP ${conn.responseCode}")
				null
			} else {
				val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
				val username = json.getString("username")
				DiscordUser(
					id = json.getString("id"),
					username = username,
					name = json.optString("global_name").ifEmpty { username },
				)
			}
		} finally {
			conn.disconnect()
		}
	} catch (e: Exception) {
		Log.w(TAG, "fetchCurrentUser failed", e)
		null
	}

	private suspend fun requestTokens(grant: String): DiscordTokenResult = withContext(Dispatchers.IO) {
		try {
			val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
			try {
				conn.connectTimeout = HTTP_TIMEOUT_MS
				conn.readTimeout = HTTP_TIMEOUT_MS
				conn.requestMethod = "POST"
				conn.doOutput = true
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
				OutputStreamWriter(conn.outputStream).use { it.write("client_id=$appId&$grant") }
				val code = conn.responseCode
				when {
					code in 200..299 -> parseTokens(conn.inputStream.bufferedReader().use { it.readText() })
					code == 400 || code == 401 -> DiscordTokenResult.Rejected
					else -> DiscordTokenResult.Failed
				}
			} finally {
				conn.disconnect()
			}
		} catch (e: Exception) {
			Log.w(TAG, "Token request failed", e)
			DiscordTokenResult.Failed
		}
	}

	private fun parseTokens(body: String): DiscordTokenResult {
		val json = JSONObject(body)
		val accessToken = json.optString("access_token")
		if (accessToken.isEmpty()) return DiscordTokenResult.Failed
		val expiresIn = json.optLong("expires_in", 0L)
		return DiscordTokenResult.Success(
			DiscordTokens(
				accessToken = accessToken,
				refreshToken = json.optString("refresh_token").ifEmpty { null },
				expiresAtMillis = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L,
			),
		)
	}

	/**
	 * Connects the SDK client with [accessToken]. When no activity has been handed over yet the
	 * connection is started as soon as one is.
	 */
	fun connect(accessToken: String) {
		if (!initialized) return
		connectionEpoch.incrementAndGet()
		disconnecting = false
		_status.value = Status.Connecting
		if (!hasEngineActivity) {
			pendingToken = accessToken
			return
		}
		nativeSetTokenAndConnect(accessToken)
	}

	/** Sets the presence, or applies it as soon as the client is ready. Null clears it. */
	fun setActivity(activity: DiscordNativeActivity?) {
		desiredActivity = activity
		if (!ready) return
		if (activity == null) nativeClear() else sendActivity(activity)
	}

	fun setOnlineStatus(status: OnlineStatus) {
		desiredStatus = status
		if (ready) sendStatus(status)
	}

	private fun applyDesired() {
		if (!ready) return
		desiredStatus?.let(::sendStatus)
		desiredActivity?.let(::sendActivity)
	}

	private fun sendStatus(status: OnlineStatus) {
		if (!statusOverridden) {
			// Remember what the user had before the first override so it can be put back.
			val current = nativeGetOnlineStatus()
			previousStatus = current.takeIf { value -> OnlineStatus.entries.any { it.value == value } }
			statusOverridden = true
		}
		nativeSetOnlineStatus(status.value)
	}

	private fun sendActivity(activity: DiscordNativeActivity) = nativeSetActivity(
		activity.activityType,
		activity.name, activity.state, activity.details,
		activity.startTimestamp, activity.endTimestamp,
		activity.largeImage, activity.largeText,
		activity.smallImage, activity.smallText,
		activity.button1Label, activity.button1Url,
		activity.button2Label, activity.button2Url,
	)

	/**
	 * Drops the connection. With [restoreStatus], the status the user had before [setOnlineStatus]
	 * first changed it (Online when unknown) is put back before disconnecting. A [connect] that
	 * happens meanwhile cancels the pending disconnect.
	 */
	fun disconnect(restoreStatus: Boolean = false) {
		if (!initialized) return
		desiredActivity = null
		desiredStatus = null
		pendingToken = null
		if (restoreStatus && ready && statusOverridden) {
			val target = previousStatus ?: OnlineStatus.Online.value
			statusOverridden = false
			previousStatus = null
			nativeSetOnlineStatus(target)
			disconnecting = true
			val epoch = connectionEpoch.get()
			scope.launch {
				delay(STATUS_RESTORE_GRACE_MS)
				if (connectionEpoch.get() == epoch) finishDisconnect()
			}
			return
		}
		statusOverridden = false
		previousStatus = null
		finishDisconnect()
	}

	private fun finishDisconnect() {
		disconnecting = false
		_status.value = Status.Disconnected
		ready = false
		nativeDisconnect()
	}

	/** Frees the native client. [init] has to be called again before reuse. */
	fun destroy() {
		synchronized(lock) {
			if (!initialized) return
			initialized = false
			ready = false
			pendingToken = null
			desiredActivity = null
			desiredStatus = null
			statusOverridden = false
			previousStatus = null
			_status.value = Status.Disconnected
			callbackJob?.cancel()
			callbackJob = null
			synchronized(nativeLock) { nativeDestroy() }
		}
	}

	private fun randomUrlSafe(byteCount: Int): String {
		val bytes = ByteArray(byteCount)
		SecureRandom().nextBytes(bytes)
		return base64Url(bytes)
	}

	private fun base64Url(bytes: ByteArray): String =
		Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

	private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
