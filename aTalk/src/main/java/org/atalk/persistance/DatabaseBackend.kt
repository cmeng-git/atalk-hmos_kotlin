/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.persistance

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import android.util.Base64
import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore
import net.java.sip.communicator.impl.msghistory.MessageSourceService
import net.java.sip.communicator.service.callhistory.CallHistoryService
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.apache.commons.lang3.StringUtils
import org.atalk.crypto.omemo.FingerprintStatus
import org.atalk.crypto.omemo.SQLiteOmemoStore
import org.atalk.hmos.BuildConfig
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.persistance.migrations.Migrations
import org.atalk.persistance.migrations.MigrationsHelper
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * The `DatabaseBackend` uses SQLite to store all the aTalk application data in the database "dbRecords.db"
 *
 * @author Eng Chong Meng
 */
@SuppressLint("Range")
class DatabaseBackend private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null,
    DATABASE_VERSION) {
    private val mProvider: ProtocolProviderService? = null
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Timber.i("Upgrading database from version %s to version %s", oldVersion, newVersion)
        db.beginTransaction()
        try {
            // cmeng: mProvider == null currently not use - must fixed if use
            val migrationsHelper = RealMigrationsHelper(mProvider)
            Migrations.upgradeDatabase(db, migrationsHelper)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Timber.e("Exception while upgrading database. Resetting the DB to original: %s", e.message)
            db.version = oldVersion
            if (BuildConfig.DEBUG) {
                db.endTransaction()
                throw Error("Database upgrade failed! Exception: ", e)
            }
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Create all the required virgin database tables and perform initial data migration
     * a. System properties
     * b. Account Tables: accountID & accountProperties
     * c. Group Tables: metaContactGroup & childContacts
     * d. contacts
     * e. chatSessions
     * f. chatMessages
     * g. callHistory
     * f. recentMessages
     * i. Axolotl tables: identities, sessions, preKeys, signed_preKeys
     *
     *
     * # Initialize and initial data migration
     *
     * @param db SQLite database
     */
    override fun onCreate(db: SQLiteDatabase) {
        // db.execSQL("PRAGMA foreign_keys=ON;");
        val query = String.format("PRAGMA foreign_keys =%s", "ON")
        db.execSQL(query)

        // System properties table
        db.execSQL("CREATE TABLE " + SQLiteConfigurationStore.TABLE_NAME + "("
                + SQLiteConfigurationStore.COLUMN_NAME + " TEXT PRIMARY KEY, "
                + SQLiteConfigurationStore.COLUMN_VALUE + " TEXT, UNIQUE("
                + SQLiteConfigurationStore.COLUMN_NAME
                + ") ON CONFLICT REPLACE);")

        // Account info table
        db.execSQL("CREATE TABLE " + AccountID.TABLE_NAME + "("
                + AccountID.ACCOUNT_UUID + " TEXT PRIMARY KEY, "
                + AccountID.PROTOCOL + " TEXT DEFAULT " + AccountID.PROTOCOL_DEFAULT + ", "
                + AccountID.USER_ID + " TEXT, "
                + AccountID.ACCOUNT_UID + " TEXT, "
                + AccountID.KEYS + " TEXT, UNIQUE(" + AccountID.ACCOUNT_UID
                + ") ON CONFLICT REPLACE);")

        // Account properties table
        db.execSQL("CREATE TABLE " + AccountID.TBL_PROPERTIES + "("
                + AccountID.ACCOUNT_UUID + " TEXT, "
                + AccountID.COLUMN_NAME + " TEXT, "
                + AccountID.COLUMN_VALUE + " TEXT, PRIMARY KEY("
                + AccountID.ACCOUNT_UUID + ", "
                + AccountID.COLUMN_NAME + "), FOREIGN KEY("
                + AccountID.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE);")

        // Meta contact groups table
        db.execSQL("CREATE TABLE " + MetaContactGroup.TABLE_NAME + "("
                + MetaContactGroup.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MetaContactGroup.ACCOUNT_UUID + " TEXT, "
                + MetaContactGroup.MC_GROUP_NAME + " TEXT, "
                + MetaContactGroup.MC_GROUP_UID + " TEXT, "
                + MetaContactGroup.PARENT_PROTO_GROUP_UID + " TEXT, "
                + MetaContactGroup.PROTO_GROUP_UID + " TEXT, "
                + MetaContactGroup.PERSISTENT_DATA + " TEXT, FOREIGN KEY("
                + MetaContactGroup.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + MetaContactGroup.ACCOUNT_UUID + ", "
                + MetaContactGroup.MC_GROUP_UID + ", " + MetaContactGroup.PARENT_PROTO_GROUP_UID
                + ") ON CONFLICT REPLACE);")

        /*
         * Meta contact group members table. The entries in the table are linked to the
         * MetaContactGroup.TABLE_NAME each entry by ACCOUNT_UUID && PROTO_GROUP_UID
         */
        db.execSQL("CREATE TABLE " + MetaContactGroup.TBL_CHILD_CONTACTS + "("
                + MetaContactGroup.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MetaContactGroup.MC_UID + " TEXT, "
                + MetaContactGroup.ACCOUNT_UUID + " TEXT, "
                + MetaContactGroup.PROTO_GROUP_UID + " TEXT, "
                + MetaContactGroup.CONTACT_JID + " TEXT, "
                + MetaContactGroup.MC_DISPLAY_NAME + " TEXT, "
                + MetaContactGroup.MC_USER_DEFINED + " TEXT DEFAULT 'false',"
                + MetaContactGroup.PERSISTENT_DATA + " TEXT, "
                + MetaContactGroup.MC_DETAILS + " TEXT, FOREIGN KEY("
                + MetaContactGroup.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + MetaContactGroup.ACCOUNT_UUID + ", "
                + MetaContactGroup.PROTO_GROUP_UID + ", " + MetaContactGroup.CONTACT_JID
                + ") ON CONFLICT REPLACE);")

        // Contacts information table
        db.execSQL("CREATE TABLE " + Contact.TABLE_NAME + "("
                + Contact.CONTACT_UUID + " TEXT PRIMARY KEY, "
                + Contact.PROTOCOL_PROVIDER + " TEXT, "
                + Contact.CONTACT_JID + " TEXT, "
                + Contact.SVR_DISPLAY_NAME + " TEXT, "
                + Contact.OPTIONS + " NUMBER, "
                + Contact.PHOTO_URI + " TEXT, "
                + Contact.AVATAR_HASH + " TEXT, "
                + Contact.LAST_PRESENCE + " TEXT, "
                + Contact.PRESENCE_STATUS + " INTEGER, "
                + Contact.LAST_SEEN + " NUMBER,"
                + Contact.KEYS + " TEXT, UNIQUE("
                + Contact.PROTOCOL_PROVIDER + ", " + Contact.CONTACT_JID
                + ") ON CONFLICT IGNORE);")

        // Chat session information table
        db.execSQL(CREATE_CHAT_SESSIONS_STATEMENT)

        // chat / MUC message table
        db.execSQL("CREATE TABLE " + ChatMessage.TABLE_NAME + "( "
                + ChatMessage.UUID + " TEXT, "
                + ChatMessage.SESSION_UUID + " TEXT, "
                + ChatMessage.TIME_STAMP + " NUMBER, "
                + ChatMessage.ENTITY_JID + " TEXT,"
                + ChatMessage.JID + " TEXT, "
                + ChatMessage.MSG_BODY + " TEXT, "
                + ChatMessage.ENC_TYPE + " TEXT, "
                + ChatMessage.MSG_TYPE + " TEXT, "
                + ChatMessage.DIRECTION + " TEXT, "
                + ChatMessage.STATUS + " TEXT,"
                + ChatMessage.FILE_PATH + " TEXT, "
                + ChatMessage.FINGERPRINT + " TEXT, "
                + ChatMessage.STEALTH_TIMER + "  INTEGER DEFAULT 0, "
                + ChatMessage.CARBON + " INTEGER DEFAULT 0, "
                + ChatMessage.READ + " INTEGER DEFAULT 0, "
                + ChatMessage.OOB + " INTEGER DEFAULT 0, "
                + ChatMessage.ERROR_MSG + " TEXT, "
                + ChatMessage.SERVER_MSG_ID + " TEXT, "
                + ChatMessage.REMOTE_MSG_ID + " TEXT, FOREIGN KEY("
                + ChatMessage.SESSION_UUID + ") REFERENCES "
                + ChatSession.TABLE_NAME + "(" + ChatSession.SESSION_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + ChatMessage.UUID
                + ") ON CONFLICT REPLACE);")

        // Call history table
        db.execSQL("CREATE TABLE " + CallHistoryService.TABLE_NAME + " ("
                + CallHistoryService.UUID + " TEXT PRIMARY KEY, "
                + CallHistoryService.TIME_STAMP + " NUMBER, "
                + CallHistoryService.ACCOUNT_UID + " TEXT, "
                + CallHistoryService.CALL_START + " NUMBER, "
                + CallHistoryService.CALL_END + " NUMBER, "
                + CallHistoryService.DIRECTION + " TEXT, "
                + CallHistoryService.ENTITY_FULL_JID + " TEXT, "
                + CallHistoryService.ENTITY_CALL_START + " NUMBER, "
                + CallHistoryService.ENTITY_CALL_END + " NUMBER, "
                + CallHistoryService.ENTITY_CALL_STATE + " TEXT, "
                + CallHistoryService.CALL_END_REASON + " TEXT, "
                + CallHistoryService.ENTITY_JID + " TEXT, "
                + CallHistoryService.SEC_ENTITY_ID + " TEXT, FOREIGN KEY("
                + CallHistoryService.ACCOUNT_UID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UID
                + ") ON DELETE CASCADE);")

        // Recent message table
        db.execSQL("CREATE TABLE " + MessageSourceService.TABLE_NAME + " ("
                + MessageSourceService.UUID + " TEXT PRIMARY KEY, "
                + MessageSourceService.ACCOUNT_UID + " TEXT, "
                + MessageSourceService.ENTITY_JID + " TEXT, "
                + MessageSourceService.TIME_STAMP + " NUMBER, "
                + MessageSourceService.VERSION + " TEXT, FOREIGN KEY("
                + MessageSourceService.ACCOUNT_UID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UID
                + ") ON DELETE CASCADE);")

        // Create all relevant tables for OMEMO support
        db.execSQL(CREATE_OMEMO_DEVICES_STATEMENT)
        db.execSQL(CREATE_PREKEYS_STATEMENT)
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT)
        db.execSQL(CREATE_IDENTITIES_STATEMENT)
        db.execSQL(CREATE_SESSIONS_STATEMENT)

        // Perform the first data migration to SQLite database
        initDatabase(db)
    }

    /**
     * Initialize, migrate and fill the database from old data implementation
     */
    private fun initDatabase(db: SQLiteDatabase) {
        Timber.i("### Starting Database migration! ###")
        db.beginTransaction()
        try {
            db.setTransactionSuccessful()
            Timber.i("### Completed SQLite DataBase migration successfully! ###")
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Create or update the AccountID table for a specified accountId
     *
     * @param accountId AccountID to be replaced/inserted
     */
    fun createAccount(accountId: AccountID) {
        val db = this.writableDatabase
        db.replace(AccountID.TABLE_NAME, null, accountId.contentValues)
    }

    val allAccountIDs: List<String>
        get() {
            val userIDs = ArrayList<String>()
            val db = this.readableDatabase
            val columns = arrayOf(AccountID.USER_ID)
            val cursor = db.query(AccountID.TABLE_NAME, columns, null, null, null, null, null)
            while (cursor.moveToNext()) {
                userIDs.add(cursor.getString(0))
            }
            cursor.close()
            return userIDs
        }

    fun getAccounts(factory: ProtocolProviderFactory): List<AccountID> {
        val db = this.readableDatabase
        val accountIDs = ArrayList<AccountID>()
        val args = arrayOf(factory.protocolName)
        val cursor = db.query(AccountID.TABLE_NAME, null, AccountID.PROTOCOL + "=?",
            args, null, null, null)
        while (cursor.moveToNext()) {
            accountIDs.add(AccountID.fromCursor(db, cursor, factory))
        }
        cursor.close()
        return accountIDs
    }

    fun updateAccount(accountId: AccountID): Boolean {
        val db = this.writableDatabase
        val args = arrayOf(accountId.accountUuid)
        val rows = db.update(AccountID.TABLE_NAME, accountId.contentValues,
            AccountID.ACCOUNT_UUID + "=?", args)
        return rows == 1
    }

    fun deleteAccount(accountId: AccountID): Boolean {
        val db = this.writableDatabase
        val args = arrayOf(accountId.accountUuid)
        val rows = db.delete(AccountID.TABLE_NAME, AccountID.ACCOUNT_UUID + "=?", args)
        return rows == 1
    }

    override fun getWritableDatabase(): SQLiteDatabase {
        val db = super.getWritableDatabase()
        // db.execSQL("PRAGMA foreign_keys=ON;");
        val query = String.format("PRAGMA foreign_keys =%s", "ON")
        db.execSQL(query)
        return db
    }

    // ========= OMEMO Devices =========
    fun loadDeviceIdsOf(user: BareJid): SortedSet<Int> {
        val deviceIds = TreeSet<Int>()
        var registrationId: Int
        val ORDER_ASC = SQLiteOmemoStore.OMEMO_REG_ID + " ASC"
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.OMEMO_REG_ID)
        val selectionArgs = arrayOf(user.toString())
        val cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
            SQLiteOmemoStore.OMEMO_JID + "=?", selectionArgs, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            registrationId = cursor.getInt(0)
            deviceIds.add(registrationId)
        }
        cursor.close()
        return deviceIds
    }

    fun loadAllOmemoRegIds(): HashMap<String, Int> {
        val registrationIds = HashMap<String, Int>()
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.OMEMO_JID, SQLiteOmemoStore.OMEMO_REG_ID)
        val cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
            null, null, null, null, null)
        while (cursor.moveToNext()) {
            registrationIds[cursor.getString(0)] = cursor.getInt(1)
        }
        cursor.close()
        return registrationIds
    }

    fun storeOmemoRegId(user: BareJid, defaultDeviceId: Int) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.OMEMO_JID, user.toString())
        values.put(SQLiteOmemoStore.OMEMO_REG_ID, defaultDeviceId)
        values.put(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID, 0)
        val row = db.insert(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, null, values)
        if (row > 0) Timber.i("### Omemo device added for: %s; %s", user, defaultDeviceId)
        else Timber.e("### Error in creating Omemo device for: %s: %s", user, defaultDeviceId)
    }

    fun loadCurrentSignedPKeyId(omemoManager: OmemoManager): Int {
        var currentSignedPKeyId = getCurrentSignedPreKeyId(omemoManager)
        val device = omemoManager.ownDevice
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
            SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
            selectionArgs, null, null, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            currentSignedPKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID))
        }
        cursor.close()
        return currentSignedPKeyId
    }

    fun storeCurrentSignedPKeyId(omemoManager: OmemoManager, currentSignedPreKeyId: Int) {
        val db = this.writableDatabase
        val device = omemoManager.ownDevice
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val values = ContentValues()
        values.put(SQLiteOmemoStore.OMEMO_JID, device.jid.toString())
        values.put(SQLiteOmemoStore.OMEMO_REG_ID, device.deviceId)
        values.put(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID, currentSignedPreKeyId)
        val row = db.update(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, values,
            SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?", selectionArgs)
        if (row == 0) {
            db.insert(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, null, values)
        }
    }

    // cmeng: encountered getLastPreKeyId not equal to store lastPreKey causing omemo msg problem!
    // To reset stored lastPreKey???
    fun loadLastPreKeyId(omemoManager: OmemoManager): Int {
        var lastPKeyId = getLastPreKeyId(omemoManager)
        val device = omemoManager.ownDevice
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.LAST_PREKEY_ID)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
            SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
            selectionArgs, null, null, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            lastPKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.LAST_PREKEY_ID))
        }
        cursor.close()
        return lastPKeyId
    }

    fun storeLastPreKeyId(omemoManager: OmemoManager, lastPreKeyId: Int) {
        val db = this.writableDatabase
        val device = omemoManager.ownDevice
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val values = ContentValues()
        values.put(SQLiteOmemoStore.OMEMO_JID, device.jid.toString())
        values.put(SQLiteOmemoStore.OMEMO_REG_ID, device.deviceId)
        values.put(SQLiteOmemoStore.LAST_PREKEY_ID, lastPreKeyId)
        val row = db.update(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, values,
            SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?", selectionArgs)
        if (row == 0) {
            db.insert(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, null, values)
        }
    }

    // ========= OMEMO PreKey =========
    private fun getCursorForPreKey(userDevice: OmemoDevice, preKeyId: Int): Cursor {
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.PRE_KEYS)
        val selectionArgs = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString(), preKeyId.toString())
        return db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
                    + SQLiteOmemoStore.PRE_KEY_ID + "=?",
            selectionArgs, null, null, null)
    }

    fun loadPreKeys(userDevice: OmemoDevice): TreeMap<Int, PreKeyRecord> {
        var preKeyId: Int
        var preKeyRecord: PreKeyRecord
        val ORDER_ASC = SQLiteOmemoStore.PRE_KEY_ID + " ASC"
        val PreKeyRecords = TreeMap<Int, PreKeyRecord>()
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.PRE_KEY_ID, SQLiteOmemoStore.PRE_KEYS)
        val selectionArgs = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
            selectionArgs, null, null, ORDER_ASC)
        while (cursor.moveToNext()) {
            preKeyId = cursor.getInt(0)
            try {
                preKeyRecord = PreKeyRecord(Base64.decode(cursor.getString(1), Base64.DEFAULT))
                PreKeyRecords[preKeyId] = preKeyRecord
            } catch (e: IOException) {
                Timber.w("Failed to deserialize preKey from store preky: %s: %s", preKeyId, e.message)
            }
        }
        cursor.close()
        return PreKeyRecords
    }

    fun loadPreKey(userDevice: OmemoDevice, preKeyId: Int): PreKeyRecord? {
        var record: PreKeyRecord? = null
        val cursor = getCursorForPreKey(userDevice, preKeyId)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            try {
                record = PreKeyRecord(Base64.decode(
                    cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.PRE_KEYS)), Base64.DEFAULT))
            } catch (e: IOException) {
                Timber.w("Failed to deserialize preKey from store. %s", e.message)
            }
        }
        cursor.close()
        return record
    }

    fun storePreKey(userDevice: OmemoDevice, preKeyId: Int, record: PreKeyRecord) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.BARE_JID, userDevice.jid.toString())
        values.put(SQLiteOmemoStore.DEVICE_ID, userDevice.deviceId)
        values.put(SQLiteOmemoStore.PRE_KEY_ID, preKeyId)
        values.put(SQLiteOmemoStore.PRE_KEYS, Base64.encodeToString(record.serialize(), Base64.DEFAULT))
        db.insert(SQLiteOmemoStore.PREKEY_TABLE_NAME, null, values)
    }

    fun deletePreKey(userDevice: OmemoDevice, preKeyId: Int) {
        val db = this.writableDatabase
        val args = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString(), preKeyId.toString())
        db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=? AND "
                + SQLiteOmemoStore.DEVICE_ID + "=? AND " + SQLiteOmemoStore.PRE_KEY_ID + "=?", args)
    }

    private fun getLastPreKeyId(omemoManager: OmemoManager): Int {
        var lastPreKeyId = 0
        val ORDER_DESC = SQLiteOmemoStore.PRE_KEY_ID + " DESC"
        val device = omemoManager.ownDevice
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.PRE_KEY_ID)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
            selectionArgs, null, null, ORDER_DESC, "1")
        if (cursor.count != 0) {
            cursor.moveToFirst()
            lastPreKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.PRE_KEY_ID))
        }
        cursor.close()
        return lastPreKeyId
    }

    // ========= OMEMO Signed PreKey =========
    private fun getCursorForSignedPreKey(userDevice: OmemoDevice, signedPreKeyId: Int): Cursor {
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.SIGNED_PRE_KEYS)
        val selectionArgs = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString(), signedPreKeyId.toString())
        return db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND "
                    + SQLiteOmemoStore.DEVICE_ID + "=? AND "
                    + SQLiteOmemoStore.SIGNED_PRE_KEY_ID + "=?", selectionArgs, null, null,
            null)
    }

    fun loadSignedPreKey(userDevice: OmemoDevice, signedPreKeyId: Int): SignedPreKeyRecord? {
        var record: SignedPreKeyRecord? = null
        val cursor = getCursorForSignedPreKey(userDevice, signedPreKeyId)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            try {
                record = SignedPreKeyRecord(Base64.decode(
                    cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEYS)), Base64.DEFAULT))
            } catch (e: IOException) {
                Timber.w("Could not deserialize signed preKey for %s: %s", userDevice, e.message)
            }
        }
        cursor.close()
        return record
    }

    fun loadSignedPreKeys(device: OmemoDevice): TreeMap<Int, SignedPreKeyRecord> {
        var preKeyId: Int
        var signedPreKeysRecord: SignedPreKeyRecord
        val preKeys = TreeMap<Int, SignedPreKeyRecord>()
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.SIGNED_PRE_KEY_ID, SQLiteOmemoStore.SIGNED_PRE_KEYS)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
            selectionArgs, null, null, null)
        while (cursor.moveToNext()) {
            try {
                preKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEY_ID))
                signedPreKeysRecord = SignedPreKeyRecord(Base64.decode(cursor.getString(
                    cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEYS)), Base64.DEFAULT))
                preKeys[preKeyId] = signedPreKeysRecord
            } catch (e: IOException) {
                Timber.w("Could not deserialize signed preKey for %s: %s", device, e.message)
            }
        }
        cursor.close()
        return preKeys
    }

    fun storeSignedPreKey(device: OmemoDevice, signedPreKeyId: Int, record: SignedPreKeyRecord) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.BARE_JID, device.jid.toString())
        values.put(SQLiteOmemoStore.DEVICE_ID, device.deviceId)
        values.put(SQLiteOmemoStore.SIGNED_PRE_KEY_ID, signedPreKeyId)
        values.put(SQLiteOmemoStore.SIGNED_PRE_KEYS, Base64.encodeToString(record.serialize(), Base64.DEFAULT))
        values.put(SQLiteOmemoStore.LAST_RENEWAL_DATE, record.timestamp)
        db.insert(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, null, values)
    }

    fun deleteSignedPreKey(userDevice: OmemoDevice, signedPreKeyId: Int) {
        val db = this.writableDatabase
        val args = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString(), signedPreKeyId.toString())
        db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
                    + SQLiteOmemoStore.SIGNED_PRE_KEY_ID + "=?", args)
    }

    fun setLastSignedPreKeyRenewal(userDevice: OmemoDevice, date: Date) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.LAST_RENEWAL_DATE, date.time)
        val selectionArgs = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString())
        db.update(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, values,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs)
    }

    fun getLastSignedPreKeyRenewal(userDevice: OmemoDevice): Date? {
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.LAST_RENEWAL_DATE)
        val selectionArgs = arrayOf(userDevice.jid.toString(), userDevice.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
            selectionArgs, null, null, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            val ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_RENEWAL_DATE))
            cursor.close()
            return if (ts > 0) Date(ts) else null
        }
        return null
    }

    private fun getCurrentSignedPreKeyId(omemoManager: OmemoManager): Int {
        var currentSignedPKId = 1
        val db = this.readableDatabase
        val device = omemoManager.ownDevice
        val columns = arrayOf(SQLiteOmemoStore.SIGNED_PRE_KEY_ID)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        val cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
            selectionArgs, null, null, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            currentSignedPKId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEY_ID))
        }
        cursor.close()
        return currentSignedPKId
    }

    // ========= OMEMO Identity =========
    private fun getIdentityKeyCursor(device: OmemoDevice, fingerprint: String?): Cursor {
        val db = this.readableDatabase
        val selectionArgs = ArrayList<String>(3)
        selectionArgs.add(device.jid.toString())
        var selectionString = SQLiteOmemoStore.BARE_JID + "=?"
        selectionArgs.add(device.deviceId.toString())
        selectionString += " AND " + SQLiteOmemoStore.DEVICE_ID + "=?"
        if (fingerprint != null) {
            selectionArgs.add(fingerprint)
            selectionString += " AND " + SQLiteOmemoStore.FINGERPRINT + "=?"
        }
        return db.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, selectionString,
            selectionArgs.toTypedArray(), null, null, null)
    }

    @Throws(CorruptedOmemoKeyException::class)
    fun loadIdentityKeyPair(device: OmemoDevice): IdentityKeyPair? {
        var identityKeyPair: IdentityKeyPair? = null
        val cursor = getIdentityKeyCursor(device, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            val identityKP = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY))
            cursor.close()
            try {
                if (StringUtils.isNotEmpty(identityKP)) {
                    identityKeyPair = IdentityKeyPair(Base64.decode(identityKP, Base64.DEFAULT))
                }
            } catch (e: InvalidKeyException) {
                // deleteIdentityKey(device); // may corrupt DB and out of sync with other data
                val msg = aTalkApp.getResString(R.string.omemo_identity_keypairs_invalid, device, e.message)
                throw CorruptedOmemoKeyException(msg)
            }
        }
        return identityKeyPair
    }

    @Throws(CorruptedOmemoKeyException::class)
    fun loadIdentityKey(device: OmemoDevice): IdentityKey? {
        var identityKey: IdentityKey? = null
        val cursor = getIdentityKeyCursor(device, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            val key = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY))
            cursor.close()
            try {
                if (StringUtils.isNotEmpty(key)) {
                    identityKey = IdentityKey(Base64.decode(key, Base64.DEFAULT), 0)
                }
            } catch (e: InvalidKeyException) {
                // Delete corrupted identityKey, let omemo rebuilt this
                deleteIdentityKey(device)
                val msg = aTalkApp.getResString(R.string.omemo_identity_key_invalid, device, e.message)
                throw CorruptedOmemoKeyException(msg)
            }
        }
        return identityKey
    }

    // Use this to delete the device corrupted identityKeyPair/identityKey
    // - Later identityKeyPair gets rebuilt when device restart
    fun deleteIdentityKey(device: OmemoDevice) {
        val db = this.writableDatabase
        val whereArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=? AND "
                + SQLiteOmemoStore.DEVICE_ID + "=?", whereArgs)
    }

    fun storeIdentityKeyPair(userDevice: OmemoDevice, identityKeyPair: IdentityKeyPair, fingerprint: String) {
        storeIdentityKey(userDevice, fingerprint,
            Base64.encodeToString(identityKeyPair.serialize(), Base64.DEFAULT),
            FingerprintStatus.createActiveVerified(false))
    }

    fun storeIdentityKey(
            device: OmemoDevice, identityKey: IdentityKey, fingerprint: String,
            status: FingerprintStatus,
    ) {
        storeIdentityKey(device, fingerprint, Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT), status)
    }

    private fun storeIdentityKey(
            device: OmemoDevice, fingerprint: String,
            base64Serialized: String, status: FingerprintStatus,
    ) {
        val db = this.writableDatabase
        val bareJid = device.jid.toString()
        val deviceId = device.deviceId.toString()
        val values = ContentValues()
        values.put(SQLiteOmemoStore.BARE_JID, bareJid)
        values.put(SQLiteOmemoStore.DEVICE_ID, deviceId)
        values.put(SQLiteOmemoStore.FINGERPRINT, fingerprint)
        values.put(SQLiteOmemoStore.IDENTITY_KEY, base64Serialized)
        values.putAll(status.toContentValues())
        val where = SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?"
        val whereArgs = arrayOf(bareJid, deviceId)
        val rows = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values, where, whereArgs)
        if (rows == 0) {
            db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values)
        }
    }

    @JvmOverloads
    fun loadIdentityKeys(device: OmemoDevice, status: FingerprintStatus? = null): Set<IdentityKey> {
        val identityKeys = HashSet<IdentityKey>()
        val cursor = getIdentityKeyCursor(device, null)
        while (cursor.moveToNext()) {
            if (status != null && status != FingerprintStatus.fromCursor(cursor)) {
                continue
            }
            try {
                val key = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY))
                if (StringUtils.isNotEmpty(key)) {
                    identityKeys.add(IdentityKey(Base64.decode(key, Base64.DEFAULT), 0))
                }
                else {
                    Timber.d("Missing key (possibly pre-verified) in database for account: %s", device.jid)
                }
            } catch (e: InvalidKeyException) {
                Timber.d("Encountered invalid IdentityKey in DB for omemoDevice: %s", device)
            }
        }
        cursor.close()
        return identityKeys
    }

    fun loadCachedDeviceList(contact: BareJid?): OmemoCachedDeviceList? {
        if (contact == null) {
            return null
        }
        val cachedDeviceList = OmemoCachedDeviceList()
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.ACTIVE)
        val selectionArgs = arrayOf(contact.toString())
        val cursor = db.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=?", selectionArgs, null, null, null)
        val activeDevices = cachedDeviceList.activeDevices
        val inActiveDevices = cachedDeviceList.inactiveDevices
        while (cursor.moveToNext()) {
            val deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID))
            if (cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.ACTIVE)) == 1) {
                activeDevices.add(deviceId)
            }
            else {
                inActiveDevices.add(deviceId)
            }
        }
        cursor.close()
        return cachedDeviceList
    }

    fun storeCachedDeviceList(userDevice: OmemoDevice?, contact: BareJid?, deviceList: OmemoCachedDeviceList) {
        if (contact == null) {
            return
        }
        val db = this.writableDatabase
        val values = ContentValues()

        // Active devices
        values.put(SQLiteOmemoStore.ACTIVE, 1)
        val activeDevices = deviceList.activeDevices
        // Timber.d("Identities table - updating for activeDevice: %s:%s", contact, activeDevices);
        for (deviceId in activeDevices) {
            val selectionArgs = arrayOf(contact.toString(), deviceId.toString())
            val row = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs)
            if (row == 0) {
                /*
                 * Just logged the error. Any missing buddy identityKey will be handled by
                 * AndroidOmemoService#buddyDeviceListUpdateListener()
                 */
                Timber.d("Identities table - create new activeDevice: %s:%s ", contact, deviceId)
                values.put(SQLiteOmemoStore.BARE_JID, contact.toString())
                values.put(SQLiteOmemoStore.DEVICE_ID, deviceId)
                db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values)
            }
        }

        /*
         * Inactive devices:
         * Important: Must clear values before use, otherwise update exiting deviceID with new deviceID but still keeping
         * old fingerPrint and identityKey. This forbids update of the fingerPrint and IdentityKey for the new deviceID,
         * Worst it causes aTalk to crash on next access to omemo chat with the identity
         */
        values.clear()
        values.put(SQLiteOmemoStore.ACTIVE, 0)
        val inActiveDevices = deviceList.inactiveDevices
        // Timber.i("Identities table updated for inactiveDevice: %s:%s", contact, inActiveDevices);
        for (deviceId in inActiveDevices) {
            val selectionArgs = arrayOf(contact.toString(), deviceId.toString())
            val row = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs)
            if (row == 0) {
                Timber.w("Identities table contains no inactiveDevice (create new): %s:%s", contact, deviceId)
                values.put(SQLiteOmemoStore.BARE_JID, contact.toString())
                values.put(SQLiteOmemoStore.DEVICE_ID, deviceId)
                db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values)
            }
        }
    }

    fun deleteNullIdentityKeyDevices(): Int {
        val db = this.writableDatabase
        return db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.IDENTITY_KEY + " IS NULL", null)
    }

    fun setLastDeviceIdPublicationDate(device: OmemoDevice, date: Date) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.LAST_MESSAGE_RX, date.time)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs)
    }

    fun getLastDeviceIdPublicationDate(device: OmemoDevice): Date? {
        val cursor = getIdentityKeyCursor(device, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            val ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_MESSAGE_RX))
            cursor.close()
            return if (ts > 0) Date(ts) else null
        }
        return null
    }

    fun setLastMessageReceiveDate(device: OmemoDevice, date: Date) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.LAST_MESSAGE_RX, date.time)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs)
    }

    fun getLastMessageReceiveDate(device: OmemoDevice): Date? {
        val cursor = getIdentityKeyCursor(device, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            val ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_MESSAGE_RX))
            cursor.close()
            return if (ts > 0) Date(ts) else null
        }
        return null
    }

    fun setOmemoMessageCounter(device: OmemoDevice, count: Int) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.MESSAGE_COUNTER, count)
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString())
        db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs)
    }

    fun getOmemoMessageCounter(device: OmemoDevice): Int {
        val cursor = getIdentityKeyCursor(device, null)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            val count = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.MESSAGE_COUNTER))
            cursor.close()
            return count
        }
        return 0
    }

    // ========= Fingerprint =========
    fun getFingerprintStatus(device: OmemoDevice, fingerprint: String?): FingerprintStatus? {
        val cursor = getIdentityKeyCursor(device, fingerprint)
        val status = if (cursor.count > 0) {
            cursor.moveToFirst()
            FingerprintStatus.fromCursor(cursor)
        }
        else {
            null
        }
        cursor.close()
        return status
    }

    fun numTrustedKeys(bareJid: String): Long {
        val db = readableDatabase
        val args = arrayOf(bareJid,
            FingerprintStatus.Trust.TRUSTED.toString(),
            FingerprintStatus.Trust.VERIFIED.toString(),
            FingerprintStatus.Trust.VERIFIED_X509.toString()
        )
        return DatabaseUtils.queryNumEntries(db, SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND ("
                    + SQLiteOmemoStore.TRUST + "=? OR "
                    + SQLiteOmemoStore.TRUST + "=? OR "
                    + SQLiteOmemoStore.TRUST + "=?) AND "
                    + SQLiteOmemoStore.ACTIVE + ">0", args
        )
    }

    fun storePreVerification(device: OmemoDevice, fingerprint: String?, status: FingerprintStatus) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.BARE_JID, device.jid.toString())
        values.put(SQLiteOmemoStore.DEVICE_ID, device.deviceId)
        values.put(SQLiteOmemoStore.FINGERPRINT, fingerprint)
        values.putAll(status.toContentValues())
        db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values)
    }

    fun setIdentityKeyTrust(device: OmemoDevice, fingerprint: String, fingerprintStatus: FingerprintStatus): Boolean {
        val db = this.writableDatabase
        val selectionArgs = arrayOf(device.jid.toString(), device.deviceId.toString(), fingerprint)
        val rows = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
            fingerprintStatus.toContentValues(), SQLiteOmemoStore.BARE_JID + "=? AND "
                    + SQLiteOmemoStore.DEVICE_ID + "=? AND " + SQLiteOmemoStore.FINGERPRINT + "=?", selectionArgs)
        return rows == 1
    }

    // ========= OMEMO session =========
    private fun getCursorForSession(omemoContact: OmemoDevice): Cursor {
        val db = this.readableDatabase
        val selectionArgs = arrayOf(omemoContact.jid.toString(), omemoContact.deviceId.toString())
        return db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, null,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
            selectionArgs, null, null, null)
    }

    fun loadSession(omemoContact: OmemoDevice): SessionRecord? {
        var sessionRecord: SessionRecord? = null
        val cursor = getCursorForSession(omemoContact)
        if (cursor.count != 0) {
            cursor.moveToFirst()
            try {
                sessionRecord = SessionRecord(Base64.decode(
                    cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SESSION_KEY)), Base64.DEFAULT))
            } catch (e: IOException) {
                Timber.w("Could not deserialize raw session. %s", e.message)
            }
        }
        cursor.close()
        return sessionRecord
    }

    fun getSubDeviceSessions(contact: BareJid): HashMap<Int, SessionRecord?> {
        var deviceId: Int
        var session: SessionRecord? = null
        val deviceSessions = HashMap<Int, SessionRecord?>()
        val db = this.readableDatabase
        val columns = arrayOf(SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.SESSION_KEY)
        val selectionArgs = arrayOf(contact.toString())
        val cursor = db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, columns,
            SQLiteOmemoStore.BARE_JID + "=?", selectionArgs, null, null, null)
        while (cursor.moveToNext()) {
            deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID))
            val sessionKey = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SESSION_KEY))
            if (StringUtils.isNotEmpty(sessionKey)) {
                try {
                    session = SessionRecord(Base64.decode(sessionKey, Base64.DEFAULT))
                } catch (e: IOException) {
                    Timber.w("Could not deserialize raw session. %s", e.message)
                }
                deviceSessions[deviceId] = session
            }
        }
        cursor.close()
        return deviceSessions
    }

    val allDeviceSessions: HashMap<OmemoDevice, SessionRecord>
        get() {
            var omemoDevice: OmemoDevice
            var bareJid: BareJid?
            var deviceId: Int
            var sJid: String
            var session: SessionRecord
            val deviceSessions = HashMap<OmemoDevice, SessionRecord>()
            val db = this.readableDatabase
            val columns = arrayOf(SQLiteOmemoStore.BARE_JID, SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.SESSION_KEY)
            val cursor = db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, columns,
                null, null, null, null, null)
            while (cursor.moveToNext()) {
                val sessionKey = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SESSION_KEY))
                if (StringUtils.isNotEmpty(sessionKey)) {
                    session = try {
                        SessionRecord(Base64.decode(sessionKey, Base64.DEFAULT))
                    } catch (e: IOException) {
                        Timber.w("Could not deserialize raw session! %s", e.message)
                        continue
                    }
                    deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID))
                    sJid = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.BARE_JID))
                    try {
                        bareJid = JidCreate.bareFrom(sJid)
                        omemoDevice = OmemoDevice(bareJid, deviceId)
                        deviceSessions[omemoDevice] = session
                    } catch (e: XmppStringprepException) {
                        Timber.w("Jid creation error for: %s", sJid)
                    }
                }
            }
            cursor.close()
            return deviceSessions
        }

    fun storeSession(omemoContact: OmemoDevice, session: SessionRecord) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(SQLiteOmemoStore.BARE_JID, omemoContact.jid.toString())
        values.put(SQLiteOmemoStore.DEVICE_ID, omemoContact.deviceId)
        values.put(SQLiteOmemoStore.SESSION_KEY, Base64.encodeToString(session.serialize(), Base64.DEFAULT))
        db.insert(SQLiteOmemoStore.SESSION_TABLE_NAME, null, values)
    }

    fun deleteSession(omemoContact: OmemoDevice) {
        val db = this.writableDatabase
        val args = arrayOf(omemoContact.jid.toString(), omemoContact.deviceId.toString())
        db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args)
    }

    fun deleteAllSessions(contact: BareJid) {
        val db = this.writableDatabase
        val args = arrayOf(contact.toString())
        db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args)
    }

    fun containsSession(omemoContact: OmemoDevice): Boolean {
        val cursor = getCursorForSession(omemoContact)
        val count = cursor.count
        cursor.close()
        return count != 0
    }
    // ========= Purge OMEMO dataBase =========
    /**
     * Call by OMEMO regeneration to perform clean up for:
     * 1. purge own Omemo deviceId
     * 2. All the preKey records for the deviceId
     * 3. Singed preKey data
     * 4. All the identities and sessions that are associated with the accountUuid
     *
     * @param accountId the specified AccountID to regenerate
     */
    fun purgeOmemoDb(accountId: AccountID) {
        val accountJid = accountId.accountJid
        Timber.d(">>> Wiping OMEMO database for account : %s", accountJid)
        val db = this.writableDatabase
        var args = arrayOf(accountJid)
        db.delete(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, SQLiteOmemoStore.OMEMO_JID + "=?", args)
        db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args)
        db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args)

        // Cleanup all the session and identities records for own resources, and the contacts
        val identityJids = getContactsForAccount(accountId.accountUuid)
        identityJids.add(accountJid)
        for (identityJid in identityJids) {
            args = arrayOf(identityJid!!)
            db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args)
            db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args)
        }
    }

    /**
     * Call by OMEMO purgeOwnDeviceKeys, it will clean up:
     * 1. purge own Omemo deviceId
     * 2. All the preKey records for own deviceId
     * 3. Singed preKey data
     * 4. The identities and sessions for the specified omemoDevice
     *
     * @param device the specified omemoDevice for cleanup
     */
    fun purgeOmemoDb(device: OmemoDevice) {
        Timber.d(">>> Wiping OMEMO database for device : %s", device)
        val db = this.writableDatabase
        val args = arrayOf(device.jid.toString(), device.deviceId.toString())
        db.delete(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME,
            SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?", args)
        db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args)
        db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args)
        db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args)
        db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME,
            SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args)
    }

    /**
     * Fetch all the contacts of the specified accountUuid
     *
     * @param accountUuid Account Uuid
     *
     * @return List of contacts for the specified accountUuid
     */
    private fun getContactsForAccount(accountUuid: String?): MutableList<String?> {
        val db = this.writableDatabase
        val childContacts = ArrayList<String?>()
        val columns = arrayOf(MetaContactGroup.CONTACT_JID)
        val args = arrayOf(accountUuid)
        val cursor = db.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
            MetaContactGroup.ACCOUNT_UUID + "=?", args, null, null, null)
        while (cursor.moveToNext()) {
            val contact = cursor.getString(0)
            if (!TextUtils.isEmpty(contact)) childContacts.add(contact)
        }
        cursor.close()
        return childContacts
    }

    private class RealMigrationsHelper(var mProvider: ProtocolProviderService?) : MigrationsHelper {
        override val accountId: AccountID
            get() = mProvider!!.accountID

        //        @Override
        override val context: Context
            get() = aTalkApp.globalContext
        //        public String serializeFlags(List<Flag> flags) {
        //            return LocalStore.serializeFlags(flags);
        //        }
    }

    companion object {
        /**
         * Name of the database and its version number
         * Increment DATABASE_VERSION when there is a change in database records
         */
        const val DATABASE_NAME = "dbRecords.db"
        private const val DATABASE_VERSION = 6
        private var instance: DatabaseBackend? = null

        // Create preKeys table
        var CREATE_OMEMO_DEVICES_STATEMENT = ("CREATE TABLE "
                + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME + "("
                + SQLiteOmemoStore.OMEMO_JID + " TEXT, "
                + SQLiteOmemoStore.OMEMO_REG_ID + " INTEGER, "
                + SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID + " INTEGER, "
                + SQLiteOmemoStore.LAST_PREKEY_ID + " INTEGER, UNIQUE("
                + SQLiteOmemoStore.OMEMO_JID
                + ") ON CONFLICT REPLACE);")

        // Create preKeys table
        var CREATE_PREKEYS_STATEMENT = ("CREATE TABLE "
                + SQLiteOmemoStore.PREKEY_TABLE_NAME + "("
                + SQLiteOmemoStore.BARE_JID + " TEXT, "
                + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
                + SQLiteOmemoStore.PRE_KEY_ID + " INTEGER, "
                + SQLiteOmemoStore.PRE_KEYS + " TEXT, UNIQUE("
                + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID + ", "
                + SQLiteOmemoStore.PRE_KEY_ID
                + ") ON CONFLICT REPLACE);")

        // Create signed preKeys table
        var CREATE_SIGNED_PREKEYS_STATEMENT = ("CREATE TABLE "
                + SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME + "("
                + SQLiteOmemoStore.BARE_JID + " TEXT, "
                + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
                + SQLiteOmemoStore.SIGNED_PRE_KEY_ID + " INTEGER, "
                + SQLiteOmemoStore.SIGNED_PRE_KEYS + " TEXT, "
                + SQLiteOmemoStore.LAST_RENEWAL_DATE + " NUMBER, UNIQUE("
                + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID + ", "
                + SQLiteOmemoStore.SIGNED_PRE_KEY_ID
                + ") ON CONFLICT REPLACE);")

        // Create identities table
        var CREATE_IDENTITIES_STATEMENT = ("CREATE TABLE "
                + SQLiteOmemoStore.IDENTITIES_TABLE_NAME + "("
                + SQLiteOmemoStore.BARE_JID + " TEXT, "
                + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
                + SQLiteOmemoStore.FINGERPRINT + " TEXT, "
                + SQLiteOmemoStore.CERTIFICATE + " BLOB, "
                + SQLiteOmemoStore.TRUST + " TEXT, "
                + SQLiteOmemoStore.ACTIVE + " NUMBER, "
                + SQLiteOmemoStore.LAST_ACTIVATION + " NUMBER, "
                + SQLiteOmemoStore.LAST_DEVICE_ID_PUBLISH + " NUMBER, "
                + SQLiteOmemoStore.LAST_MESSAGE_RX + " NUMBER, "
                + SQLiteOmemoStore.MESSAGE_COUNTER + " INTEGER, "
                + SQLiteOmemoStore.IDENTITY_KEY + " TEXT, UNIQUE("
                + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID
                + ") ON CONFLICT REPLACE);")

        // Create session table
        var CREATE_SESSIONS_STATEMENT = ("CREATE TABLE "
                + SQLiteOmemoStore.SESSION_TABLE_NAME + "("
                + SQLiteOmemoStore.BARE_JID + " TEXT, "
                + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
                + SQLiteOmemoStore.SESSION_KEY + " TEXT, UNIQUE("
                + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID
                + ") ON CONFLICT REPLACE);")

        // Chat session information table
        var CREATE_CHAT_SESSIONS_STATEMENT = ("CREATE TABLE "
                + ChatSession.TABLE_NAME + " ("
                + ChatSession.SESSION_UUID + " TEXT PRIMARY KEY, "
                + ChatSession.ACCOUNT_UUID + " TEXT, "
                + ChatSession.ACCOUNT_UID + " TEXT, "
                + ChatSession.ENTITY_JID + " TEXT, "
                + ChatSession.CREATED + " NUMBER, "
                + ChatSession.STATUS + " NUMBER DEFAULT " + ChatFragment.MSGTYPE_OMEMO + ", "
                + ChatSession.MODE + " NUMBER, "
                + ChatSession.MAM_DATE + " NUMBER DEFAULT " + Date().time + ", "
                + ChatSession.ATTRIBUTES + " TEXT, FOREIGN KEY("
                + ChatSession.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + ChatSession.ACCOUNT_UUID
                + ", " + ChatSession.ENTITY_JID
                + ") ON CONFLICT REPLACE);")

        /**
         * Get an instance of the DataBaseBackend and create one if new
         *
         * @param context context
         *
         * @return DatabaseBackend instance
         */
        @Synchronized
        fun getInstance(context: Context): DatabaseBackend {
            if (instance == null) {
                instance = DatabaseBackend(context)
            }
            return instance!!
        }

        val writableDB: SQLiteDatabase
            get() = instance!!.writableDatabase

        val readableDB: SQLiteDatabase
            get() = instance!!.readableDatabase
    }
}