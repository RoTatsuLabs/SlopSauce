package org.koitharu.kotatsu.secret

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.IntentCompat

/** Lives in its own process: kills the app process, then starts the app again in a fresh one. */
class SecretRestartActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val pid = intent.getIntExtra(EXTRA_PID, -1)
		val next = IntentCompat.getParcelableExtra(intent, EXTRA_NEXT, Intent::class.java)
		if (pid > 0) {
			Process.killProcess(pid)
		}
		next?.let(::startActivity)
		finish()
		Runtime.getRuntime().exit(0)
	}

	companion object {

		const val EXTRA_NEXT = "next"
		const val EXTRA_PID = "pid"
	}
}
