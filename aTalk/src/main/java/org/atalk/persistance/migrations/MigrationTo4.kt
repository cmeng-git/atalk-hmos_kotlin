package org.atalk.persistance.migrations

import android.database.sqlite.SQLiteDatabase
import org.atalk.crypto.omemo.SQLiteOmemoStore

object MigrationTo4 {
    fun updateOmemoIdentitiesTable(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.MESSAGE_COUNTER + " INTEGER")
    }
}