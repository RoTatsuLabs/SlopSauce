package org.koitharu.kotatsu.secret

import android.content.Context
import androidx.annotation.StringRes
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.local.data.index.LocalMangaIndex
import java.io.File
import javax.inject.Inject
import javax.inject.Provider

/** Copies data from the main space into the secret space. The main space is never changed. */
class SecretImporter @Inject constructor(
	@ApplicationContext private val context: Context,
	private val database: MangaDatabase,
	private val indexProvider: Provider<LocalMangaIndex>,
) {

	enum class Section(@StringRes val titleResId: Int) {
		LIBRARY(R.string.favourites),
		HISTORY(R.string.history),
		BOOKMARKS(R.string.bookmarks),
		DOWNLOADS(R.string.downloads),
	}

	suspend fun import(sections: Set<Section>) = withContext(Dispatchers.IO) {
		check(SecretSpace.isActive) { "Only available in the secret space" }
		val main = SecretSpace.mainContext
		val dbSections = sections - Section.DOWNLOADS
		if (dbSections.isNotEmpty()) {
			importDatabase(main.getDatabasePath(DB_NAME), dbSections)
		}
		if (Section.DOWNLOADS in sections) {
			copyDownloads(main)
			indexProvider.get().update()
		}
	}

	private fun importDatabase(source: File, sections: Set<Section>) {
		check(source.exists()) { "Nothing to import" }
		val db = database.openHelper.writableDatabase
		db.execSQL("ATTACH DATABASE ? AS $SRC", arrayOf<Any?>(source.path))
		try {
			db.beginTransaction()
			try {
				val ids = sections.joinToString(" UNION ") {
					when (it) {
						Section.LIBRARY -> "SELECT manga_id FROM $SRC.favourites"
						Section.HISTORY -> "SELECT manga_id FROM $SRC.history"
						else -> "SELECT manga_id FROM $SRC.bookmarks"
					}
				}
				// Parents never overwrite what the secret space already has.
				copy(db, "tags", "IGNORE", "tag_id IN (SELECT tag_id FROM $SRC.manga_tags WHERE manga_id IN ($ids))")
				copy(db, "manga", "IGNORE", "manga_id IN ($ids)")
				copy(db, "manga_tags", "IGNORE", "manga_id IN ($ids)")
				copy(db, "chapters", "IGNORE", "manga_id IN ($ids)")
				if (Section.LIBRARY in sections) {
					copy(db, "favourite_categories", "REPLACE")
					copy(db, "favourites", "REPLACE")
				}
				if (Section.HISTORY in sections) {
					copy(db, "history", "REPLACE")
				}
				if (Section.BOOKMARKS in sections) {
					copy(db, "bookmarks", "REPLACE")
				}
				db.setTransactionSuccessful()
			} finally {
				db.endTransaction()
			}
		} finally {
			runCatching { db.execSQL("DETACH DATABASE $SRC") }
		}
		database.invalidationTracker.refreshVersionsAsync()
	}

	/** Copies by column name, so a different column order in the main database does not matter. */
	private fun copy(db: SupportSQLiteDatabase, table: String, onConflict: String, where: String? = null) {
		val target = columns(db, "main", table)
		val columns = target.intersect(columns(db, SRC, table).toSet()).joinToString(",") { "\"$it\"" }
		val filter = where?.let { " WHERE $it" }.orEmpty()
		db.execSQL("INSERT OR $onConflict INTO main.$table ($columns) SELECT $columns FROM $SRC.$table$filter")
	}

	private fun columns(db: SupportSQLiteDatabase, schema: String, table: String): List<String> =
		db.query("PRAGMA $schema.table_info($table)").use { c ->
			val index = c.getColumnIndexOrThrow("name")
			buildList { while (c.moveToNext()) add(c.getString(index)) }
		}

	private fun copyDownloads(main: Context) {
		val pairs = buildList {
			add(File(main.filesDir, DIR_MANGA) to File(context.filesDir, DIR_MANGA))
			val from = main.getExternalFilesDirs(DIR_MANGA)
			val to = context.getExternalFilesDirs(DIR_MANGA)
			for (i in from.indices) {
				val source = from[i] ?: continue
				val target = to.getOrNull(i) ?: continue
				add(source to target)
			}
		}
		for ((source, target) in pairs) {
			if (!source.isDirectory) continue
			target.mkdirs()
			source.copyRecursively(target, overwrite = false) { _, e ->
				if (e is kotlin.io.FileAlreadyExistsException) {
					kotlin.io.OnErrorAction.SKIP
				} else {
					kotlin.io.OnErrorAction.TERMINATE
				}
			}
		}
	}

	private companion object {

		const val DB_NAME = "kotatsu-db"
		const val SRC = "src"
		const val DIR_MANGA = "manga"
	}
}
