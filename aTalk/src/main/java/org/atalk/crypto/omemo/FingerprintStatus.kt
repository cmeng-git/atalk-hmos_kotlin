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
package org.atalk.crypto.omemo

import android.content.ContentValues
import android.database.Cursor
import org.apache.commons.lang3.StringUtils
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException

class FingerprintStatus private constructor() : Comparable<FingerprintStatus> {
    var trust = Trust.UNTRUSTED
        private set
    var omemoDevice: OmemoDevice? = null
        private set
    var fingerPrint: String? = null
        private set
    var isActive = false
        private set
    var lastActivation = DO_NOT_OVERWRITE
        private set

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as FingerprintStatus
        return isActive == that.isActive && trust == that.trust
    }

    override fun hashCode(): Int {
        var result = trust.hashCode()
        result = 31 * result + if (isActive) 1 else 0
        return result
    }

    fun toContentValues(): ContentValues {
        val contentValues = ContentValues()
        contentValues.put(SQLiteOmemoStore.Companion.TRUST, trust.toString())
        contentValues.put(SQLiteOmemoStore.Companion.ACTIVE, if (isActive) 1 else 0)
        if (lastActivation != DO_NOT_OVERWRITE) {
            contentValues.put(SQLiteOmemoStore.Companion.LAST_ACTIVATION, lastActivation)
        }
        return contentValues
    }

    val isTrustedAndActive: Boolean
        get() = isActive && isTrusted
    val isTrusted: Boolean
        get() = trust == Trust.TRUSTED || isVerified
    val isVerified: Boolean
        get() = trust == Trust.VERIFIED || trust == Trust.VERIFIED_X509
    val isCompromised: Boolean
        get() = trust == Trust.COMPROMISED

    fun toActive(): FingerprintStatus {
        val status = FingerprintStatus()
        status.trust = trust
        if (!status.isActive) {
            status.lastActivation = System.currentTimeMillis()
        }
        status.isActive = true
        return status
    }

    fun toInactive(): FingerprintStatus {
        val status = FingerprintStatus()
        status.trust = trust
        status.isActive = false
        return status
    }

    fun toVerified(): FingerprintStatus {
        val status = FingerprintStatus()
        status.isActive = isActive
        status.trust = Trust.VERIFIED
        return status
    }

    fun toUntrusted(): FingerprintStatus {
        val status = FingerprintStatus()
        status.isActive = isActive
        status.trust = Trust.UNTRUSTED
        // status.trust = Trust.UNDECIDED; // testing only
        return status
    }

    override fun compareTo(o: FingerprintStatus): Int {
        return if (isActive == o.isActive) {
            if (lastActivation > o.lastActivation) {
                -1
            } else if (lastActivation < o.lastActivation) {
                1
            } else {
                0
            }
        } else if (isActive) {
            -1
        } else {
            1
        }
    }

    enum class Trust {
        COMPROMISED, UNDECIDED, UNTRUSTED, TRUSTED, VERIFIED, VERIFIED_X509
    }

    companion object {
        private const val DO_NOT_OVERWRITE = -1L
        fun fromCursor(cursor: Cursor): FingerprintStatus? {
            val status = FingerprintStatus()
            try {
                val bareJid = JidCreate.bareFrom(cursor.getString(cursor.getColumnIndexOrThrow(SQLiteOmemoStore.Companion.BARE_JID)))
                val deviceId = cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteOmemoStore.Companion.DEVICE_ID))
                status.omemoDevice = OmemoDevice(bareJid, deviceId)
            } catch (e: XmppStringprepException) {
                e.printStackTrace()
            }
            status.fingerPrint = cursor.getString(cursor.getColumnIndexOrThrow(SQLiteOmemoStore.Companion.FINGERPRINT))
            if (StringUtils.isEmpty(status.fingerPrint)) return null
            try {
                val trust = cursor.getString(cursor.getColumnIndexOrThrow(SQLiteOmemoStore.Companion.TRUST))
                if (StringUtils.isEmpty(trust)) status.trust = Trust.UNDECIDED else status.trust = Trust.valueOf(trust)
            } catch (e: IllegalArgumentException) {
                status.trust = Trust.UNTRUSTED
            }
            status.isActive = cursor.getInt(cursor.getColumnIndexOrThrow(SQLiteOmemoStore.Companion.ACTIVE)) > 0
            status.lastActivation = cursor.getLong(cursor.getColumnIndexOrThrow(SQLiteOmemoStore.Companion.LAST_ACTIVATION))
            return status
        }

        fun createActiveUndecided(): FingerprintStatus {
            val status = FingerprintStatus()
            status.trust = Trust.UNDECIDED
            status.isActive = true
            status.lastActivation = System.currentTimeMillis()
            return status
        }

        fun createActiveTrusted(): FingerprintStatus {
            val status = FingerprintStatus()
            status.trust = Trust.TRUSTED
            status.isActive = true
            status.lastActivation = System.currentTimeMillis()
            return status
        }

        fun createActiveVerified(x509: Boolean): FingerprintStatus {
            val status = FingerprintStatus()
            status.trust = if (x509) Trust.VERIFIED_X509 else Trust.VERIFIED
            status.isActive = true
            return status
        }

        fun createActive(trusted: Boolean): FingerprintStatus {
            val status = FingerprintStatus()
            status.trust = if (trusted) Trust.TRUSTED else Trust.UNTRUSTED
            status.isActive = true
            return status
        }

        fun createInactiveVerified(): FingerprintStatus {
            val status = FingerprintStatus()
            status.trust = Trust.VERIFIED
            status.isActive = false
            return status
        }
    }
}