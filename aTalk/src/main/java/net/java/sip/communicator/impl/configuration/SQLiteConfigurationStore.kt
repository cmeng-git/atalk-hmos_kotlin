/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.configuration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.configuration.DatabaseConfigurationStore
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.osgi.OSGiService
import timber.log.Timber
import java.io.IOException

/**
 * Implements a `ConfigurationStore` which stores property name-value associations in an SQLite database.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SQLiteConfigurationStore : DatabaseConfigurationStore {
    /**
     * aTalk backend SQLite database
     */
    private val openHelper: DatabaseBackend?

    /**
     * Warning: Do not remove, required in invokeGetServiceOnImpl(ConfigurationService::class.java)!!
     * Initializes a new `SQLiteConfigurationStore` instance.
     */
    constructor() {
        val context = ServiceUtils.getService(ConfigurationActivator.bundleContext, OSGiService::class.java)
        openHelper = DatabaseBackend.getInstance(context!!)
    }

    constructor(context: Context) {
        openHelper = DatabaseBackend.getInstance(context)
    }

    /**
     * Overrides HashtableConfigurationStore.getProperty. If this
     * `ConfigurationStore` contains a value associated with the specified property name,
     * returns it. Otherwise, searches for a system property with the specified name and returns
     * its value. If property name starts with "acc", the look up the value in table
     * AccountID.TBL_PROPERTIES for the specified accountUuid, otherwise use table TABLE_NAME
     *
     * @param name the name of the property to get the value of
     * @return the value in this `ConfigurationStore` of the property with the specified
     * name; `null` if the property with the specified name does not have an association
     * with a value in this `ConfigurationStore`
     * @see ConfigurationStore.property
     */
    override fun getProperty(name: String): Any? {
        var value = properties[name]
        if (value == null) {
            var cursor: Cursor? = null
            val columns = arrayOf(COLUMN_VALUE)

            synchronized(openHelper as Any) {
                mDB = openHelper.readableDatabase
                if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                    val idx = name.indexOf(".")
                    if (idx == -1) {
                        value = name // just return the accountUuid
                    } else {
                        val args = arrayOf(name.substring(0, idx), name.substring(idx + 1))
                        cursor = mDB.query(AccountID.TBL_PROPERTIES, columns,
                                AccountID.ACCOUNT_UUID + "=? AND " + COLUMN_NAME + "=?",
                                args, null, null, null, "1")
                    }
                } else {
                    cursor = mDB.query(TABLE_NAME, columns,
                            "$COLUMN_NAME=?", arrayOf(name), null, null, null, "1")
                }

                if (cursor != null) {
                    try {
                        if ((cursor!!.count == 1) && cursor!!.moveToFirst()) value = cursor!!.getString(0)
                    } finally {
                        cursor?.close()
                    }
                }
            }
            if (value == null) value = System.getProperty(name)
        }
        return value
    }

    /**
     * Overrides [HashtableConfigurationStore.getPropertyNames]. Gets the names of
     * the properties which have values associated in this `ConfigurationStore`.
     *
     * @return an array of `String`s which specify the names of the properties that have
     * values associated in this `ConfigurationStore`; an empty array if this instance
     * contains no property values
     * @see ConfigurationStore.getPropertyNames
     */
    override fun getPropertyNames(name: String): Array<String?> {
        val propertyNames = ArrayList<String>()
        var tableName: String
        synchronized(openHelper as Any) {
            mDB = openHelper.readableDatabase
            tableName = if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                AccountID.TBL_PROPERTIES
            } else {
                TABLE_NAME
            }

            mDB.query(tableName, arrayOf(COLUMN_NAME),
                    null, null, null, null, "$COLUMN_NAME ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    propertyNames.add(cursor.getString(0))
                }
            }
        }
        return propertyNames.toTypedArray()
    }

    /**
     * Removes all property name-value associations currently present in this
     * `ConfigurationStore` instance and de-serializes new property name-value
     * associations from its underlying database (storage).
     *
     * @throws IOException if there is an input error while reading from the underlying database (storage)
     */
    @Throws(IOException::class)
    override fun reloadConfiguration() {
        // TODO Auto-generated method stub
    }

    /**
     * Overrides HashtableConfigurationStore.removeProperty. Removes the value
     * association in this `ConfigurationStore` of the property with a specific name. If
     * the property with the specified name is not associated with a value in this
     * `ConfigurationStore`, does nothing.
     *
     * @param name the name of the property which is to have its value association in this
     * `ConfigurationStore` removed
     * @see ConfigurationStore.removeProperty
     */
    override fun removeProperty(name: String) {
        super.removeProperty(name)
        synchronized(openHelper as Any) {
            mDB = openHelper.writableDatabase
            if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                val idx = name.indexOf(".")
                // remove user account if only accountUuid is specified
                if (idx == -1) {
                    val args = arrayOf(name)
                    mDB.delete(AccountID.TABLE_NAME, AccountID.ACCOUNT_UUID + "=?", args)
                } else {
                    val args = arrayOf(name.substring(0, idx), name.substring(idx + 1))
                    mDB.delete(AccountID.TBL_PROPERTIES,
                            AccountID.ACCOUNT_UUID + "=? AND " + COLUMN_NAME + "=?", args)
                }
            } else {
                mDB.delete(TABLE_NAME, "$COLUMN_NAME=?", arrayOf(name))
            }
        }
        Timber.log(TimberLog.FINER, "### Remove property from table: %s", name)
    }

    /**
     * Overrides [HashtableConfigurationStore.setNonSystemProperty].
     *
     * @param name the name of the non-system property to be set to the specified value in this
     * `ConfigurationStore`
     * @param value the value to be assigned to the non-system property with the specified name in this
     * `ConfigurationStore`
     * @see ConfigurationStore.setNonSystemProperty
     */
    override fun setNonSystemProperty(name: String, value: Any?) {
        synchronized(openHelper as Any) {
            val mDB = openHelper.writableDatabase
            var tableName = TABLE_NAME
            val contentValues = ContentValues()
            contentValues.put(COLUMN_VALUE, value.toString())
            if (name.startsWith(AccountID.ACCOUNT_UUID_PREFIX)) {
                val idx = name.indexOf(".")
                contentValues.put(AccountID.ACCOUNT_UUID, name.substring(0, idx))
                contentValues.put(COLUMN_NAME, name.substring(idx + 1))
                tableName = AccountID.TBL_PROPERTIES
            } else {
                contentValues.put(COLUMN_NAME, name)
            }

            // Insert the properties in DB, replace if exist
            val rowId = mDB.replace(tableName, null, contentValues)
            if (rowId == -1L) Timber.e("Failed to set non-system property: %s: %s <= %s", tableName, name, value)
            Timber.log(TimberLog.FINER, "### Set non-system property: %s: %s <= %s", tableName, name, value)
        }
        // To take care of cached properties and accountProperties
        super.setNonSystemProperty(name, value)
    }

    companion object {
        const val TABLE_NAME = "properties"
        const val COLUMN_NAME = "Name"
        const val COLUMN_VALUE = "Value"
        private lateinit var mDB: SQLiteDatabase
    }
}