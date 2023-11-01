package org.atalk.persistance.migrations

import android.database.sqlite.SQLiteDatabase
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.persistance.DatabaseBackend
import timber.log.Timber

object MigrationTo5 {
    fun updateOmemoDevicesTable(db: SQLiteDatabase) {
        val OLD_TABLE = "omemo_devices_old"
        db.execSQL("DROP TABLE IF EXISTS $OLD_TABLE")
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME + " RENAME TO " + OLD_TABLE)
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME)
        db.execSQL(DatabaseBackend.CREATE_OMEMO_DEVICES_STATEMENT)
        db.execSQL("INSERT INTO " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME + " SELECT * FROM " + OLD_TABLE)
        db.execSQL("DROP TABLE IF EXISTS $OLD_TABLE")
        Timber.d("Updated omemo_devices table successfully!")
    }
}