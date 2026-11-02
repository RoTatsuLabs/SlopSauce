package org.koitharu.kotatsu.secret

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.kanade.tachiyomi.util.lang.Hash
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.io.File
import java.util.UUID

/**
 * A second, fully separate copy of the app data. The space is chosen per process: entering or leaving
 * restarts the app, and the process then runs either on the normal storage or on [SecretContext].
 */
object SecretSpace {

	internal const val EXTERNAL_DIR = ".secret"
	private const val MARKER = ".ss_enter"
	private const val WIPE = ".ss_wipe"
	private const val MARKER_TTL_MS = 30_000L
	private const val LEAVE_DELAY_MS = 2_000L
	private const val GATE_PREFS = "ss_gate"
	private const val KEY_HASH = "hash"
	private const val KEY_SALT = "salt"
	private const val KEY_BIOMETRIC = "bio"
	private const val KEY_SEED_MAIN = "seed_main"
	private const val KEY_SEEDED = "ss_seeded"
	private val SEED_SKIP = setOf(
		AppSettings.KEY_HIDE_FAVOURITES_IN_BROWSE,
		AppSettings.KEY_BACKUP_SECTIONS,
	)

	/** True when the current process runs in the secret space. */
	@Volatile
	var isActive = false
		private set

	private lateinit var raw: Context
	private val handler = Handler(Looper.getMainLooper())

	/** Called from `Application.attachBaseContext`; returns the context the app should run on. */
	fun attach(base: Context): Context {
		raw = base
		if (!isMainProcess(base)) {
			return base
		}
		val marker = markerFile(base)
		if (marker.exists()) {
			isActive = System.currentTimeMillis() - marker.lastModified() < MARKER_TTL_MS
			marker.delete()
		}
		if (!isActive) {
			wipeIfRequested(base)
			return base
		}
		return SecretContext(base).also { seedSettings(base, it) }
	}

	/** Copies the look and reading settings once, so the secret space feels the same. Nothing private goes along. */
	private fun seedSettings(base: Context, secret: Context) {
		val name = base.packageName + "_preferences"
		val target = secret.getSharedPreferences(name, Context.MODE_PRIVATE)
		if (target.contains(KEY_SEEDED)) {
			return
		}
		val copyMain = gate().getBoolean(KEY_SEED_MAIN, false)
		val source = if (copyMain) base.getSharedPreferences(name, Context.MODE_PRIVATE).all else emptyMap()
		target.edit {
			for ((key, value) in source) {
				if (!AppSettings.isSyncableKey(key) || key in SEED_SKIP) continue
				when (value) {
					is Boolean -> putBoolean(key, value)
					is Int -> putInt(key, value)
					is Long -> putLong(key, value)
					is Float -> putFloat(key, value)
					is String -> putString(key, value)
					is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
				}
			}
			putBoolean(AppSettings.KEY_PRIVATE_INSTALLER, true)
			putBoolean(KEY_SEEDED, true)
		}
	}

	/** Wraps an activity base context so it uses the same storage as the application. */
	fun wrap(context: Context): Context = if (isActive) SecretContext(context) else context

	/** Locks the space (restarts into the main app) once the app has been in the background for a moment. */
	fun watchBackground(app: Application) {
		if (!isActive) {
			return
		}
		ProcessLifecycleOwner.get().lifecycle.addObserver(
			object : DefaultLifecycleObserver {
				override fun onStart(owner: LifecycleOwner) {
					handler.removeCallbacksAndMessages(null)
				}

				override fun onStop(owner: LifecycleOwner) {
					handler.postDelayed({ exit(app) }, LEAVE_DELAY_MS)
				}
			},
		)
	}

	/** Whether the secret space starts with a copy of the main app settings (only read on the first entry). */
	var copyMainSettings: Boolean
		get() = gate().getBoolean(KEY_SEED_MAIN, false)
		set(value) = gate().edit { putBoolean(KEY_SEED_MAIN, value) }

	/** The storage of the main space, for importing data into the secret space. */
	internal val mainContext: Context
		get() = raw

	val isRestartProcess: Boolean
		get() = processName().endsWith(":secret_restart")

	val hasPin: Boolean
		get() = gate().getString(KEY_HASH, null) != null

	var isBiometricEnabled: Boolean
		get() = gate().getBoolean(KEY_BIOMETRIC, false)
		set(value) = gate().edit { putBoolean(KEY_BIOMETRIC, value) }

	fun setPin(pin: String) {
		val salt = UUID.randomUUID().toString()
		gate().edit {
			putString(KEY_SALT, salt)
			putString(KEY_HASH, Hash.sha256(salt + pin))
		}
	}

	fun verifyPin(pin: String): Boolean {
		val prefs = gate()
		val salt = prefs.getString(KEY_SALT, null) ?: return false
		val hash = prefs.getString(KEY_HASH, null) ?: return false
		return Hash.sha256(salt + pin) == hash
	}

	/** Restarts the app into the secret space. */
	fun enter(activity: Activity) {
		markerFile(raw).apply {
			parentFile?.mkdirs()
			writeText("1")
		}
		restart(activity)
	}

	/** Deletes everything in the secret space, including the PIN, then restarts into the main space. */
	fun delete(activity: Activity) {
		wipeFile(raw).apply {
			parentFile?.mkdirs()
			writeText("1")
		}
		markerFile(raw).delete()
		restart(activity)
	}

	/** Runs in the main space after a restart, once the secret process is gone. */
	private fun wipeIfRequested(base: Context) {
		val flag = wipeFile(base)
		if (!flag.exists()) {
			return
		}
		Thread {
			runCatching {
				val storage = base.createDeviceProtectedStorageContext()
				for (name in listOf("databases", "shared_prefs", "files", "cache", "code_cache")) {
					File(storage.dataDir, name).deleteRecursively()
				}
				storage.noBackupFilesDir.listFiles()?.filter { it.name != WIPE }?.forEach { it.deleteRecursively() }
				base.getExternalFilesDirs(null).forEach { dir -> dir?.let { File(it, EXTERNAL_DIR).deleteRecursively() } }
				flag.delete()
			}
		}.start()
	}

	/** Restarts the app into the main space. */
	fun leave(activity: Activity) {
		markerFile(raw).delete()
		restart(activity)
	}

	private fun restart(activity: Activity) {
		val next = checkNotNull(raw.packageManager.getLaunchIntentForPackage(raw.packageName)).addFlags(
			Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
		)
		activity.startActivity(
			Intent(activity, SecretRestartActivity::class.java)
				.putExtra(SecretRestartActivity.EXTRA_NEXT, next)
				.putExtra(SecretRestartActivity.EXTRA_PID, Process.myPid())
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
		)
		activity.finish()
		Runtime.getRuntime().exit(0)
	}

	/** Drops all screens from recents and stops the process, so the next launch opens the main space. */
	private fun exit(app: Application) {
		app.getSystemService<ActivityManager>()?.appTasks?.forEach { it.finishAndRemoveTask() }
		Process.killProcess(Process.myPid())
	}

	private fun gate() = raw.createDeviceProtectedStorageContext().getSharedPreferences(GATE_PREFS, Context.MODE_PRIVATE)

	private fun wipeFile(context: Context) = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, WIPE)

	private fun markerFile(context: Context) = File(context.createDeviceProtectedStorageContext().noBackupFilesDir, MARKER)

	private fun isMainProcess(context: Context) = processName() == context.packageName

	private fun processName(): String = runCatching {
		File("/proc/self/cmdline").readText().trim { it <= ' ' }
	}.getOrDefault("")
}
