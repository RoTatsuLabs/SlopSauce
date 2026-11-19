package org.koitharu.kotatsu.core

import org.koitharu.kotatsu.BuildConfig

/**
 * What a crash report says about the build. The report reads every public field of this class,
 * so it lists the build facts a bug needs and leaves out secrets that sit in [BuildConfig], such as
 * the Telegram bot token.
 */
object CrashBuildInfo {

	@JvmField val APPLICATION_ID = BuildConfig.APPLICATION_ID
	@JvmField val BUILD_TYPE = BuildConfig.BUILD_TYPE
	@JvmField val FLAVOR = BuildConfig.FLAVOR
	@JvmField val DEBUG = BuildConfig.DEBUG
	@JvmField val VERSION_NAME = BuildConfig.VERSION_NAME
	@JvmField val VERSION_CODE = BuildConfig.VERSION_CODE
}
