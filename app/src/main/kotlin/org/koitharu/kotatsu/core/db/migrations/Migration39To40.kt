package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration39To40 : Migration(39, 40) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE preferences ADD COLUMN `tags_override` TEXT")
		db.execSQL("ALTER TABLE preferences ADD COLUMN `authors_override` TEXT")
		db.execSQL("ALTER TABLE preferences ADD COLUMN `state_override` TEXT")
		db.execSQL("ALTER TABLE preferences ADD COLUMN `rating_override` REAL")
	}
}
