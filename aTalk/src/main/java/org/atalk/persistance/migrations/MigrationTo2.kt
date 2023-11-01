package org.atalk.persistance.migrations

import android.database.sqlite.SQLiteDatabase
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.persistance.DatabaseBackend

object MigrationTo2 {
    // Create all relevant tables for OMEMO crypto support
    fun createOmemoTables(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME)
        db.execSQL(DatabaseBackend.CREATE_OMEMO_DEVICES_STATEMENT)
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.PREKEY_TABLE_NAME)
        db.execSQL(DatabaseBackend.CREATE_PREKEYS_STATEMENT)
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME)
        db.execSQL(DatabaseBackend.CREATE_SIGNED_PREKEYS_STATEMENT)
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME)
        db.execSQL(DatabaseBackend.CREATE_IDENTITIES_STATEMENT)
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.SESSION_TABLE_NAME)
        db.execSQL(DatabaseBackend.CREATE_SESSIONS_STATEMENT)
    }
}