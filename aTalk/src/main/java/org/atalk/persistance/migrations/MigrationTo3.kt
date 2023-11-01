package org.atalk.persistance.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.aTalkApp
import org.atalk.persistance.FileBackend
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import java.io.File

object MigrationTo3 {
    fun updateSQLDatabase(db: SQLiteDatabase) {
        updateOmemoIdentitiesTable(db)
        clearUnUsedTableEntries(db)
        deleteOldDatabase()
    }

    private fun updateOmemoIdentitiesTable(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.LAST_DEVICE_ID_PUBLISH + " NUMBER")
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.LAST_MESSAGE_RX + " NUMBER")
    }

    private fun clearUnUsedTableEntries(db: SQLiteDatabase) {
        // remove old property name
        val args = arrayOf("replacement.%")
        db.delete(SQLiteConfigurationStore.TABLE_NAME, SQLiteConfigurationStore.COLUMN_NAME + " LIKE ?", args)
    }

    private fun deleteOldDatabase() {
        // Proceed to delete if "SQLiteConfigurationStore.db" exist
        val PROPERTIES_DB = "net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore.db"
        val ctx = aTalkApp.globalContext
        val DBPath = ctx.getDatabasePath(PROPERTIES_DB).path
        val dbFile = File(DBPath)
        if (dbFile.exists()) {
            ctx.deleteDatabase(PROPERTIES_DB)
        }

        // Delete old history files
        val filesDir = ctx.filesDir.absolutePath
        val omemoDir = File("$filesDir/OMEMO_Store")
        val historyDir = File("$filesDir/history_ver1.0")
        val xmlFP = File("$filesDir/contactlist.xml")
        try {
            if (historyDir.exists()) FileBackend.deleteRecursive(historyDir)
            if (xmlFP.exists()) FileBackend.deleteRecursive(xmlFP)
            if (omemoDir.exists()) FileBackend.deleteRecursive(omemoDir)
        } catch (ignore: Exception) {
        }

        // Clean up avatar store to remove files named with old userID
        VCardAvatarManager.clearPersistentStorage()
    }
}