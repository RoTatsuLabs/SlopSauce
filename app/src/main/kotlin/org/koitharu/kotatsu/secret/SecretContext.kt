package org.koitharu.kotatsu.secret

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.Display
import java.io.File

/**
 * Redirects every app storage location to separate ones, so the whole app runs on its own data.
 * Databases, preferences, files and caches live in device-protected storage, which Auto Backup skips.
 */
internal class SecretContext(base: Context) : ContextWrapper(base) {

	private val storage: Context = base.createDeviceProtectedStorageContext()

	override fun getSharedPreferences(name: String?, mode: Int) = storage.getSharedPreferences(name, mode)

	override fun deleteSharedPreferences(name: String?) = storage.deleteSharedPreferences(name)

	override fun getDatabasePath(name: String?): File = storage.getDatabasePath(name)

	override fun openOrCreateDatabase(
		name: String?,
		mode: Int,
		factory: SQLiteDatabase.CursorFactory?,
	): SQLiteDatabase = storage.openOrCreateDatabase(name, mode, factory)

	override fun openOrCreateDatabase(
		name: String?,
		mode: Int,
		factory: SQLiteDatabase.CursorFactory?,
		errorHandler: DatabaseErrorHandler?,
	): SQLiteDatabase = storage.openOrCreateDatabase(name, mode, factory, errorHandler)

	override fun deleteDatabase(name: String?) = storage.deleteDatabase(name)

	override fun databaseList(): Array<String> = storage.databaseList()

	override fun getFilesDir(): File = storage.filesDir

	override fun getNoBackupFilesDir(): File = storage.noBackupFilesDir

	override fun getCacheDir(): File = storage.cacheDir

	override fun getCodeCacheDir(): File = storage.codeCacheDir

	override fun getDir(name: String?, mode: Int): File = storage.getDir(name, mode)

	override fun getFileStreamPath(name: String?): File = storage.getFileStreamPath(name)

	override fun openFileInput(name: String?) = storage.openFileInput(name)

	override fun openFileOutput(name: String?, mode: Int) = storage.openFileOutput(name, mode)

	override fun deleteFile(name: String?) = storage.deleteFile(name)

	override fun fileList(): Array<String> = storage.fileList()

	override fun getExternalFilesDir(type: String?): File? =
		super.getExternalFilesDir(null)?.let { secretDir(it, type) }

	override fun getExternalFilesDirs(type: String?): Array<File?> =
		super.getExternalFilesDirs(null).map { dir -> dir?.let { secretDir(it, type) } }.toTypedArray()

	override fun getExternalCacheDir(): File? = super.getExternalCacheDir()?.let { secretDir(it, null) }

	override fun getExternalCacheDirs(): Array<File?> =
		super.getExternalCacheDirs().map { dir -> dir?.let { secretDir(it, null) } }.toTypedArray()

	override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
		SecretContext(super.createConfigurationContext(overrideConfiguration))

	override fun createDisplayContext(display: Display): Context =
		SecretContext(super.createDisplayContext(display))

	private fun secretDir(parent: File, type: String?): File {
		val root = File(parent, SecretSpace.EXTERNAL_DIR)
		return (if (type.isNullOrEmpty()) root else File(root, type)).also { it.mkdirs() }
	}
}
