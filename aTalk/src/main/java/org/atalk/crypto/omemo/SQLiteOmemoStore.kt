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

import android.util.LruCache
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.persistance.DatabaseBackend
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.element.OmemoDeviceListElement
import org.jivesoftware.smackx.omemo.element.OmemoDeviceListElement_VAxolotl
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList
import org.jivesoftware.smackx.omemo.internal.OmemoDevice
import org.jivesoftware.smackx.omemo.signal.SignalCachingOmemoStore
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback
import org.jivesoftware.smackx.omemo.trust.TrustState
import org.jivesoftware.smackx.omemo.util.OmemoConstants
import org.jivesoftware.smackx.pep.PepManager
import org.jivesoftware.smackx.pubsub.PayloadItem
import org.jivesoftware.smackx.pubsub.PubSubManager
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import timber.log.Timber
import java.io.IOException
import java.util.*

/**
 * The extension of the OMEMO signal Store that uses SQLite database to support the storage of
 * OMEMO information for:
 * - Omemo devices
 * - PreKeys
 * - Signed preKeys
 * - Identities & fingerprints and its trust status
 * - Omemo sessions
 *
 * @author Eng Chong Meng
 */
class SQLiteOmemoStore : SignalCachingOmemoStore() {
    private val mDB = DatabaseBackend.getInstance(aTalkApp.globalContext)!!

    /*
     * mDevice is used by overridden method create(String fingerprint) for trustCache self update
     * @see LruCache#create(Object)
     */
    private var mDevice: OmemoDevice? = null

    /**
     * Cache of a map fingerPrint string to FingerprintStatus
     */
    private val trustCache = object : LruCache<String, FingerprintStatus?>(NUM_TRUSTS_TO_CACHE) {
        override fun create(fingerprint: String?): FingerprintStatus? {
            return mDB.getFingerprintStatus(mDevice!!, fingerprint)
        }
    }
    // --------------------------------------
    // FingerprintStatus utilities
    // --------------------------------------
    /**
     * Get the fingerprint status for the specified device
     *
     * Need to pass device to create(String fingerprint) for trustCache
     *
     * @param device omemoDevice for which its fingerprint status is to be retrieved
     * @param fingerprint fingerprint to check
     * @return the fingerprint status for the specified device
     * @see LruCache.create
     */
    fun getFingerprintStatus(device: OmemoDevice?, fingerprint: String?): FingerprintStatus? {
        /* Must setup mDevice for FingerprintStatus#create(String fingerprint) */
        mDevice = device
        return if (fingerprint == null) null else trustCache[fingerprint]
    }

    private fun setFingerprintStatus(device: OmemoDevice, fingerprint: String, status: FingerprintStatus) {
        mDB.setIdentityKeyTrust(device, fingerprint, status)
        trustCache.remove(fingerprint) // clear old status in trustCache
    }
    //======================= OMEMO Store =========================================
    // --------------------------------------
    // OMEMO Devices Store
    // --------------------------------------
    /**
     * Returns a sorted set of all the deviceIds, the localUser has had data stored under in the store.
     * Basically this returns the deviceIds of all "accounts" of localUser, which are known to the store.
     *
     * @param localUser BareJid of the user.
     * @return set of deviceIds with available data.
     */
    override fun localDeviceIdsOf(localUser: BareJid): SortedSet<Int> {
        return mDB.loadDeviceIdsOf(localUser)
    }

    /**
     * Set the default deviceId of a user if it does not exist.
     *
     * @param user user
     * @param defaultDeviceId defaultDeviceId
     */
    fun setDefaultDeviceId(user: BareJid, defaultDeviceId: Int) {
        mDB.storeOmemoRegId(user, defaultDeviceId)
    }
    // --------------------------------------
    // PreKey Store
    // --------------------------------------
    /**
     * Return all our current OmemoPreKeys.
     *
     * @param userDevice our OmemoDevice.
     * @return Map containing our preKeys
     */
    override fun loadOmemoPreKeys(userDevice: OmemoDevice): TreeMap<Int, PreKeyRecord> {
        return mDB.loadPreKeys(userDevice)
    }

    /**
     * Load the preKey with id 'preKeyId' from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param preKeyId id of the key to be loaded
     * @return loaded preKey
     */
    override fun loadOmemoPreKey(userDevice: OmemoDevice, preKeyId: Int): PreKeyRecord {
        val record = mDB.loadPreKey(userDevice, preKeyId)
        if (record == null) {
            Timber.w("There is no PreKeyRecord for: %s", preKeyId)
        }
        return record!!
    }

    /**
     * Store a contact PreKey in storage.
     *
     * @param userDevice our OmemoDevice.
     * @param preKeyId id of the key
     * @param preKeyRecord ths PreKeyRecord
     */
    override fun storeOmemoPreKey(userDevice: OmemoDevice, preKeyId: Int, preKeyRecord: PreKeyRecord) {
        mDB.storePreKey(userDevice, preKeyId, preKeyRecord)
    }

    /**
     * remove a preKey from storage. This is called, when a contact used one of our preKeys to establish a session
     * with us.
     *
     * @param userDevice our OmemoDevice.
     * @param preKeyId id of the used key to be deleted
     */
    override fun removeOmemoPreKey(userDevice: OmemoDevice, preKeyId: Int) {
        mDB.deletePreKey(userDevice, preKeyId)
    }
    // --------------------------------------
    // SignedPreKeyStore
    // --------------------------------------
    /**
     * Return the signedPreKey with the id 'singedPreKeyId'.
     *
     * @param userDevice our OmemoDevice.
     * @param signedPreKeyId id of the key
     * @return key
     */
    override fun loadOmemoSignedPreKey(userDevice: OmemoDevice, signedPreKeyId: Int): SignedPreKeyRecord {
        val record = mDB.loadSignedPreKey(userDevice, signedPreKeyId)
        if (record == null) {
            Timber.w("There is no SignedPreKeyRecord for: %s", signedPreKeyId)
        }
        return record!!
    }

    /**
     * Load all our signed PreKeys.
     *
     * @param userDevice our OmemoDevice.
     * @return HashMap of our singedPreKeys
     */
    override fun loadOmemoSignedPreKeys(userDevice: OmemoDevice): TreeMap<Int, SignedPreKeyRecord> {
        return mDB.loadSignedPreKeys(userDevice)
    }

    /**
     * Store a signedPreKey in storage.
     *
     * @param userDevice our OmemoDevice.
     * @param signedPreKeyId id of the signedPreKey
     * @param signedPreKey the key itself
     */
    override fun storeOmemoSignedPreKey(
            userDevice: OmemoDevice, signedPreKeyId: Int,
            signedPreKey: SignedPreKeyRecord,
    ) {
        mDB.storeSignedPreKey(userDevice, signedPreKeyId, signedPreKey)
    }

    /**
     * Remove a signedPreKey from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param signedPreKeyId key with the specified id will be removed
     */
    override fun removeOmemoSignedPreKey(userDevice: OmemoDevice, signedPreKeyId: Int) {
        mDB.deleteSignedPreKey(userDevice, signedPreKeyId)
    }

    /**
     * Set the date in millis of the last time the signed preKey was renewed.
     *
     * @param userDevice our OmemoDevice.
     * @param date date
     */
    override fun setDateOfLastSignedPreKeyRenewal(userDevice: OmemoDevice, date: Date) {
        mDB.setLastSignedPreKeyRenewal(userDevice, date)
    }

    /**
     * Get the date in millis of the last time the signed preKey was renewed.
     *
     * @param userDevice our OmemoDevice.
     * @return date if existent, otherwise null
     */
    override fun getDateOfLastSignedPreKeyRenewal(userDevice: OmemoDevice): Date {
        return mDB.getLastSignedPreKeyRenewal(userDevice)!!
    }
    // --------------------------------------
    // IdentityKeyStore
    // --------------------------------------
    /**
     * Load our identityKeyPair from storage.
     * Return null, if we have no identityKeyPair.
     *
     * @param userDevice our OmemoDevice.
     * @return identityKeyPair Omemo identityKeyPair
     * @throws CorruptedOmemoKeyException Thrown, if the stored key is damaged (*hands up* not my fault!)
     */
    @Throws(CorruptedOmemoKeyException::class)
    override fun loadOmemoIdentityKeyPair(userDevice: OmemoDevice): IdentityKeyPair? {
        val identityKeyPair = try {
            mDB.loadIdentityKeyPair(userDevice)
        } catch (e: CorruptedOmemoKeyException) {
            Timber.e("Corrupted Omemo IdentityKeyPair: %s", e.message)
            throw CorruptedOmemoKeyException(e.message)
        }
        if (identityKeyPair == null) {
            aTalkApp.showToastMessage(R.string.omemo_identity_keypairs_missing, userDevice)
        }
        return identityKeyPair
    }

    /**
     * Store our identityKeyPair in storage. It would be a cool feature, if the key could be stored in an encrypted
     * database or something similar.
     *
     * @param userDevice our OmemoDevice.
     * @param identityKeyPair identityKeyPair
     */
    override fun storeOmemoIdentityKeyPair(userDevice: OmemoDevice, identityKeyPair: IdentityKeyPair) {
        val fingerprint = keyUtil().getFingerprintOfIdentityKeyPair(identityKeyPair).toString()
        Timber.i("Store omemo identityKeyPair for :%s", userDevice)
        mDB.storeIdentityKeyPair(userDevice, identityKeyPair, fingerprint)
    }

    /**
     * Remove the identityKeyPair of a user.
     *
     * @param userDevice our device.
     */
    override fun removeOmemoIdentityKeyPair(userDevice: OmemoDevice) {
        Timber.e(Exception("Removed device IdentityKeyPair: $userDevice"))
        mDB.deleteIdentityKey(userDevice)
    }

    /**
     * Load the public identityKey of a device.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice the device of which we want to load the identityKey.
     * @return identityKey - may be null
     * @throws CorruptedOmemoKeyException when the key in question is corrupted and cannot be deserialized.
     */
    @Throws(CorruptedOmemoKeyException::class)
    override fun loadOmemoIdentityKey(userDevice: OmemoDevice, contactDevice: OmemoDevice): IdentityKey? {
        val identityKey = try {
            mDB.loadIdentityKey(contactDevice)
        } catch (e: CorruptedOmemoKeyException) {
            // throw only if key is corrupted else return null
            Timber.e("Corrupted Omemo IdentityKey: %s", e.message)
            throw CorruptedOmemoKeyException(e.message)
        }
        return identityKey
    }

    /**
     * Store the public identityKey of the device.
     *
     * If new device, initialize its fingerprint trust status basing on:
     * - found no previously manually verified fingerprints for the contact AND
     * - pending user option BlindTrustBeforeVerification.
     * Otherwise, just set its status to active and update lastActivation to current.
     *
     * Daniel Gultsch wrote a nice article about BTBV. Basically BTBV works as follows:
     * When a new key k is received for a Jid J, then k is only considered automatically trusted,
     * when there is no other key n of J, which has been manually trusted (verified). As soon as
     * there is such a key, k will be considered undecided. So a new key does only get considered
     * blindly trusted, when no other key has been manually trusted.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device.
     * @param contactKey identityKey belonging to the contactsDevice.
     */
    override fun storeOmemoIdentityKey(userDevice: OmemoDevice, contactDevice: OmemoDevice, contactKey: IdentityKey) {
        if (userDevice == contactDevice) {
            Timber.w("Attempt to overwrite own device IdentityKeyPars with IdentityKey; ignore request!: %s",
                contactDevice)
            return
        }
        val bareJid = contactDevice.jid.toString()
        val fingerprint = keyUtil().getFingerprintOfIdentityKey(contactKey).toString()
        if (!mDB.loadIdentityKeys(contactDevice).contains(contactKey)) {
            Timber.i("Update identityKey for: %s; %s; %s", contactDevice, contactKey.toString(), fingerprint)
            var fpStatus = getFingerprintStatus(contactDevice, fingerprint)
            fpStatus = if (fpStatus == null) {
                val mConfig = AndroidGUIActivator.configurationService
                if (mConfig.isBlindTrustBeforeVerification
                        && mDB.numTrustedKeys(bareJid) == 0L) {
                    Timber.i("Blind trusted fingerprint for: %s", contactDevice)
                    FingerprintStatus.createActiveTrusted()
                }
                else {
                    FingerprintStatus.createActiveUndecided()
                }
            }
            else {
                fpStatus.toActive()
            }
            mDB.storeIdentityKey(contactDevice, contactKey, fingerprint, fpStatus)
            trustCache.remove(fingerprint)
        }
        // else {
        //     // Timber.d("Skip Update duplicated identityKey for: %s; %s; %s", contactDevice, contactKey.toString(), fingerprint);
        //     // Code for testing only
        //     if (contactDevice.getJid().toString().contains("atalkuser1")) {
        //         contactDevice = new OmemoDevice(contactDevice.getJid(),1367773246);
        //         removeOmemoIdentityKey(null, contactDevice);
        //     }
        // }
    }

    /**
     * Removes the identityKey of a device.
     *
     * @param userDevice our omemoDevice.
     * @param contactDevice device of which we want to delete the identityKey.
     */
    override fun removeOmemoIdentityKey(userDevice: OmemoDevice, contactDevice: OmemoDevice) {
        mDB.deleteIdentityKey(contactDevice)
    }

    /**
     * Trust Callback used to make trust decisions on identities.
     */
    var trustCallBack = object : OmemoTrustCallback {
        /*
         * Determine the identityKey of a remote client's device is in which TrustState based on the stored
         * value in the database.
         *
         * If you want to use this module, you should memorize, whether the user has trusted this key
         * or not, since the owner of the identityKey will be able to read sent messages when this
         * method returned 'trusted' for their identityKey. Either you let the user decide whether you
         * trust a key every time you see a new key, or you implement something like 'blind trust'
         * (see https://gultsch.de/trust.html).
         *
         * By default, aTalk trust state implementation is that (BTBV option enabled)
         * TextSecure protocol is 'trust on first use' an identity key is considered 'trusted' if
         * there is no entry for the recipient in the local store, or if it matches the saved key for
         * a recipient in the local store. Only if it mismatches an entry in the local store is it
         * considered 'untrusted.'
         */
        override fun getTrust(device: OmemoDevice, fingerprint: OmemoFingerprint): TrustState {
            val fpStatus = getFingerprintStatus(device, fingerprint.toString())
            return if (fpStatus != null) {
                val trustState = fpStatus.trust
                if (fpStatus.isTrusted) /* VERIFIED OR TRUSTED */ TrustState.trusted
                else if (FingerprintStatus.Trust.UNDECIDED == trustState) {
                    TrustState.undecided
                }
                else if (FingerprintStatus.Trust.UNTRUSTED == trustState) {
                    TrustState.untrusted
                }
                else {
                    TrustState.trusted
                }
            }
            else {
                val mConfig = AndroidGUIActivator.configurationService
                if (mConfig.isBlindTrustBeforeVerification
                        && mDB.numTrustedKeys(device.jid.toString()) == 0L) {
                    TrustState.trusted
                }
                else TrustState.undecided
            }
        }

        /**
         * setTrust an OmemoIdentity to the specified trust state.
         *
         * In aTalk, will only be set to Trust.VERIFIED on user manual verification.
         * Trust.TRUSTED state is used only for Blind trusted before verification
         *
         * Distrust an OmemoIdentity. This involved marking the key as distrusted or undecided if previously is null
         */
        override fun setTrust(device: OmemoDevice, identityKeyFingerprint: OmemoFingerprint, state: TrustState) {
            val fingerprint = identityKeyFingerprint.toString()
            var fpStatus = getFingerprintStatus(device, fingerprint)
            fpStatus = when (state) {
                TrustState.undecided -> FingerprintStatus.createActiveUndecided()
                TrustState.trusted -> {
                    val mConfig = AndroidGUIActivator.configurationService
                    if (mConfig.isBlindTrustBeforeVerification
                            && mDB.numTrustedKeys(device.jid.toString()) == 0L) {
                        FingerprintStatus.createActiveTrusted()
                    }
                    else {
                        fpStatus!!.toVerified()
                    }
                }
                TrustState.untrusted -> fpStatus?.toUntrusted() ?: FingerprintStatus.createActiveUndecided()
            }
            setFingerprintStatus(device, fingerprint, fpStatus)
            trustCache.put(fingerprint, fpStatus)
        }
    }

    /**
     * Load a list of deviceIds from contact 'contact' from the local cache.
     * static final String DEVICE_LIST_ACTIVE = "activeDevices"; // identities.active = 1
     * static final String DEVICE_LIST_INACTIVE = "inactiveDevices";  // identities.active = 0
     *
     * @param userDevice our OmemoDevice.
     * @param contact contact we want to get the deviceList of
     * @return CachedDeviceList of the contact
     */
    override fun loadCachedDeviceList(userDevice: OmemoDevice?, contact: BareJid): OmemoCachedDeviceList? {
        // OmemoCachedDeviceList list = mDB.loadCachedDeviceList(contact);
        // Timber.d("Cached list for active (inActive): %s (%s)", list.getActiveDevices(), list.getInactiveDevices());
        // return list;
        if (userDevice == null || contact == null)
            Timber.e("userDevice: %s, contact: %s", userDevice, contact)
        return mDB.loadCachedDeviceList(contact)
    }

    /**
     * Store the DeviceList of the contact in local storage.
     * See this as a cache.
     *
     * @param userDevice our OmemoDevice.
     * @param contact Contact
     * @param contactDeviceList list of the contact devices' ids.
     */
    override fun storeCachedDeviceList(
            userDevice: OmemoDevice, contact: BareJid,
            contactDeviceList: OmemoCachedDeviceList,
    ) {
        mDB.storeCachedDeviceList(userDevice, contact, contactDeviceList)
    }
    // --------------------------------------
    // SessionStore
    // --------------------------------------
    /**
     * Load the crypto-lib specific session object of the device from storage.
     * A null session record will trigger a fresh session rebuild by omemoService
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device whose session we want to load
     * @return crypto related session; null if none if found
     */
    override fun loadRawSession(userDevice: OmemoDevice, contactDevice: OmemoDevice): SessionRecord? {
        return mDB.loadSession(contactDevice)
    }

    /**
     * Load all crypto-lib specific session objects of contact 'contact'.
     *
     * @param userDevice our OmemoDevice.
     * @param contact BareJid of the contact we want to get all sessions from
     * @return TreeMap of deviceId and sessions of the contact
     */
    override fun loadAllRawSessionsOf(userDevice: OmemoDevice, contact: BareJid): HashMap<Int, SessionRecord?> {
        return mDB.getSubDeviceSessions(contact)
    }

    /**
     * Store a crypto-lib specific session to storage.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice OmemoDevice whose session we want to store
     * @param session session
     */
    override fun storeRawSession(userDevice: OmemoDevice, contactDevice: OmemoDevice, session: SessionRecord) {
        mDB.storeSession(contactDevice, session)
    }

    /**
     * Remove a crypto-lib specific session from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device whose session we want to delete
     */
    override fun removeRawSession(userDevice: OmemoDevice, contactDevice: OmemoDevice) {
        mDB.deleteSession(contactDevice)
    }

    /**
     * Remove all crypto-lib specific session of a contact.
     *
     * @param userDevice our OmemoDevice.
     * @param contact BareJid of the contact
     */
    override fun removeAllRawSessionsOf(userDevice: OmemoDevice, contact: BareJid) {
        mDB.deleteAllSessions(contact)
    }

    /**
     * Return true, if we have a session with the device, otherwise false.
     * Hint for Signal: Do not try 'return getSession() != null' since this will create a new session.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device
     * @return true if we have session, otherwise false
     */
    override fun containsRawSession(userDevice: OmemoDevice, contactDevice: OmemoDevice): Boolean {
        return mDB.containsSession(contactDevice)
    }

    /**
     * Set the date of the last message that was received from a device.
     *
     * @param userDevice our omemoDevice.
     * @param contactDevice device in question
     * @param date date of the last received message
     */
    override fun setDateOfLastReceivedMessage(userDevice: OmemoDevice, contactDevice: OmemoDevice, date: Date) {
        mDB.setLastMessageReceiveDate(contactDevice, date)
    }

    /**
     * Return the date of the last message that was received from device 'from'.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device in question
     * @return date if existent, otherwise null
     */
    override fun getDateOfLastReceivedMessage(userDevice: OmemoDevice, contactDevice: OmemoDevice): Date {
        return mDB.getLastMessageReceiveDate(contactDevice)!!
    }

    /**
     * Set the date of the last time the deviceId was published. This method only gets called, when the deviceId
     * was inactive/non-existent before it was published.
     *
     * @param userDevice our OmemoDevice
     * @param contactDevice OmemoDevice in question
     * @param date date of the last publication after not being published
     */
    override fun setDateOfLastDeviceIdPublication(userDevice: OmemoDevice, contactDevice: OmemoDevice, date: Date) {
        mDB.setLastDeviceIdPublicationDate(contactDevice, date)
    }

    /**
     * Return the date of the last time the deviceId was published after previously being not published.
     * (Point in time, where the status of the deviceId changed from inactive/non-existent to active).
     *
     * @param userDevice our OmemoDevice
     * @param contactDevice OmemoDevice in question
     * @return date of the last publication after not being published
     */
    override fun getDateOfLastDeviceIdPublication(userDevice: OmemoDevice, contactDevice: OmemoDevice): Date? {
        return mDB.getLastDeviceIdPublicationDate(contactDevice)
    }

    /**
     * Store the number of messages we sent to a device since we last received a message back.
     * This counter gets reset to 0 whenever we receive a message from the contacts device.
     *
     * @param userDevice our omemoDevice.
     * @param contactsDevice device of which we want to set the message counter.
     * @param counter counter value.
     */
    override fun storeOmemoMessageCounter(userDevice: OmemoDevice, contactsDevice: OmemoDevice, counter: Int) {
        mDB.setOmemoMessageCounter(contactsDevice, counter)
    }

    /**
     * Return the current value of the message counter.
     * This counter represents the number of message we sent to the contactsDevice without getting a reply back.
     * The default value for this counter is 0.
     *
     * @param userDevice our omemoDevice
     * @param contactsDevice device of which we want to get the message counter.
     * @return counter value.
     */
    override fun loadOmemoMessageCounter(userDevice: OmemoDevice, contactsDevice: OmemoDevice): Int {
        return mDB.getOmemoMessageCounter(contactsDevice)
    }
    // ========== aTalk methods to handle omemo specific tasks ==========
    /**
     * Delete this device's IdentityKey, PreKeys, SignedPreKeys and Sessions.
     *
     * @param userDevice our OmemoDevice.
     */
    override fun purgeOwnDeviceKeys(userDevice: OmemoDevice) {
        mDB.purgeOmemoDb(userDevice)
        trustCache.evictAll()
    }

    /**
     * Clean up omemo bundle and devicelist on the server for the specified omemoDevice:
     *
     * @param connection XMPPConnection
     * @param userJid Account userJid
     * @param omemoDevice of which the bundle and devicelist items are to be removed from server
     */
    private fun purgeBundleDeviceList(connection: XMPPConnection, userJid: BareJid, omemoDevice: OmemoDevice) {
        Timber.d("Purge server bundle and deviceList for old omemo device: %s", omemoDevice)
        val pubsubManager = PubSubManager.getInstanceFor(connection, userJid)
        val pepManager = PepManager.getInstanceFor(connection)

        // First refresh omemo devicelist on the server i.e. removed old id
        var deviceIds = emptySet<Int>()
        val any = try {
            val nodeName = OmemoConstants.PEP_NODE_DEVICE_LIST
            val leafNode = pubsubManager.getLeafNode(nodeName)
            if (leafNode != null) {
                val items = leafNode.getItems<PayloadItem<OmemoDeviceListElement>>()
                if (items.isNotEmpty()) {
                    // These will completely remove the deviceList - may not be good
                    // leafNode.deleteAllItems();
                    // pubsubManager.deleteNode(nodeName);
                    val publishedList = items[items.size - 1].payload
                    deviceIds = publishedList.copyDeviceIds() // need a copy of the unmodifiable list
                    deviceIds.remove(omemoDevice.deviceId)
                }
            }
            val deviceList = OmemoDeviceListElement_VAxolotl(deviceIds)
            pepManager.publish(nodeName, PayloadItem(deviceList))
        } catch (e: SmackException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: InterruptedException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: XMPPErrorException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        }

        // Only then purge omemo preKeys table/bundles on server
        try {
            val nodeName = omemoDevice.bundleNodeName
            val leafNode = pubsubManager.getLeafNode(nodeName)
            if (leafNode != null) {
                leafNode.deleteAllItems()
                pubsubManager.deleteNode(nodeName)
            }
        } catch (e: SmackException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: InterruptedException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: XMPPErrorException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        }
    }

    /**
     * Regenerate new omemo device identity for the specified user accountId in the following order
     * 1. Purge account Omemo info in the local database
     * 2. Call via AndroidOmemoService() to:
     * a. create new Omemo deviceId
     * b. generate fresh user identityKeyPairs, preKeys and save to local DB
     * c. publish it to the server.
     * 3. Purge server bundle data and devicelist for the old omemoDevice
     */
    fun regenerate(accountId: AccountID) {
        val pps = accountId.protocolProvider
        if (pps != null) {
            val connection = pps.connection
            if (connection != null && connection.isAuthenticated) {
                val userJid = accountId.bareJid
                val omemoManager = OmemoManager.getInstanceFor(connection)
                // stop old omemo manager to update cached data
                omemoManager.stopStanzaAndPEPListeners()

                // Purge bundle and refresh devicelist for the old omemoDevice
                val omemoDevice = omemoManager.ownDevice
                purgeBundleDeviceList(connection, userJid!!, omemoDevice)

                // Purge all omemo devices info in the local database for the specified accountId
                mDB.purgeOmemoDb(accountId)
                trustCache.evictAll()

                // Create new omemoDeice
                AndroidOmemoService(pps).initOmemoDevice()
            }
        }
    }

    /**
     * The method performs the following functions:
     * 1. Purge all inactive devices and devices with null IdentityKeys;
     * 2. Remove all inactive devices associated table data if any;
     * 3. Refresh devicelist on the server with own device and cached active devices
     * 4. Clean up any omemo orphan data
     *
     * Remove the associated local database include the followings:
     * a. preKeyPairs
     * b. signed preKeys
     * c. identities table entries
     * d. session table entries
     *
     * @param pps protocolProvider of the user account.
     */
    fun purgeInactiveUserDevices(pps: ProtocolProviderService?) {
        if (pps != null) {
            val connection = pps.connection
            if (connection != null && connection.isAuthenticated) {
                val omemoManager = OmemoManager.getInstanceFor(connection)
                val userJid = omemoManager.ownDevice.jid
                var userDevice: OmemoDevice
                val deviceList = mDB.loadCachedDeviceList(userJid)
                Timber.d("Purge inactive devices associated data for: %s %s", userJid, deviceList!!.inactiveDevices)

                // remove the local inactive devices from identities table and all their associated data if any
                for (deviceId in deviceList.inactiveDevices) {
                    userDevice = OmemoDevice(userJid, deviceId)
                    purgeOwnDeviceKeys(userDevice)
                }

                // Also delete all devices with null Identity key - omemoService will re-create them if needed
                val count = mDB.deleteNullIdentityKeyDevices()
                Timber.d("Number of null identities deleted: %s", count)

                // publish a new device list with our own deviceId and cached active devices
                try {
                    omemoManager.purgeDeviceList()
                } catch (e: SmackException) {
                    aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, userJid)
                } catch (e: InterruptedException) {
                    aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, userJid)
                } catch (e: XMPPErrorException) {
                    aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, userJid)
                } catch (e: IOException) {
                    aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, userJid)
                }
            }
        }
        // Cleanup orphan's Omemo Tables
        cleanUpOmemoDB()
    }

    /**
     * Remove the corrupted omemoDevice from the table; remove null identities and clean up the server devicelist
     *
     * @param omemoManager OmemoManager
     * @param omemoDevice the corrupted device
     */
    fun purgeCorruptedOmemoKey(omemoManager: OmemoManager?, omemoDevice: OmemoDevice) {
        Timber.d("Purging corrupted KeyIdentity for omemo device: %s", omemoDevice)

        // remove the local corrupted device from db first; in case network access throws exception
        purgeOwnDeviceKeys(omemoDevice)

        // Also delete all devices with null Identity key - omemoService will re-create them if needed
        val count = mDB.deleteNullIdentityKeyDevices()
        Timber.d("Number of null identities deleted: %s", count)

        // publish a new device list with our own deviceId and cached active devices
        try {
            omemoManager!!.purgeDeviceList()
        } catch (e: SmackException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: InterruptedException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: XMPPErrorException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        } catch (e: IOException) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, omemoDevice)
        }
    }

    /**
     * Clean up omemo local database, and omemo bundle and devicelist on the server for the specified accountId:
     * 1. When a user account is deleted.
     * 2. During Regenerate OMEMO identities
     *
     * @param accountId the omemo local database/server of the accountID to be purged.
     */
    fun purgeUserOmemoData(accountId: AccountID) {
        // Retain a copy of the old device to purge data on server
        val userJid = accountId.bareJid!!
        val deviceIds = localDeviceIdsOf(userJid)
        if (deviceIds.size == 0) return
        val deviceId = deviceIds.first()
        val omemoDevice = OmemoDevice(userJid, deviceId)

        // Must first remove the omemoDevice and associated data from local database
        // Purge local omemo database for the specified account
        mDB.purgeOmemoDb(accountId)
        trustCache.evictAll()

        // Purge server omemo bundle nodes for the deleted account (only if online and authenticated)
        val pps = accountId.protocolProvider
        if (pps != null) {
            val connection = pps.connection
            if (connection != null && connection.isAuthenticated) {
                purgeBundleDeviceList(connection, userJid, omemoDevice)
            }
        }
    }

    /**
     * Method helps to clean up omemo database of accounts that have been removed
     */
    private fun cleanUpOmemoDB() {
        val userIds = mDB.allAccountIDs
        val omemoIDs = mDB.loadAllOmemoRegIds()
        for ((userId, deviceId) in omemoIDs) {
            if (userIds.contains(userId)) continue
            try {
                val bareJid = JidCreate.bareFrom(userId)
                val userDevice = OmemoDevice(bareJid, deviceId)
                // server data???
                purgeOwnDeviceKeys(userDevice)
                Timber.i("Clean up omemo database for: %s", userDevice)
            } catch (e: XmppStringprepException) {
                Timber.e("Error in clean omemo database for: %s: %s", userId, deviceId)
            }
        }
    }

    companion object {
        // omemoDevices Table
        const val OMEMO_DEVICES_TABLE_NAME = "omemo_devices"
        const val OMEMO_JID = "omemoJid" // account user
        const val OMEMO_REG_ID = "omemoRegId" // defaultDeviceId
        const val CURRENT_SIGNED_PREKEY_ID = "currentSignedPreKeyId"
        const val LAST_PREKEY_ID = "lastPreKeyId"

        // PreKeys Table
        const val PREKEY_TABLE_NAME = "preKeys"

        // public static final String BARE_JID = "bareJid";
        // public static final String DEVICE_ID = "deviceId";
        const val PRE_KEY_ID = "preKeyId"
        const val PRE_KEYS = "preKeys"

        // Signed PreKeys Table
        const val SIGNED_PREKEY_TABLE_NAME = "signed_preKeys"

        // public static final String BARE_JID = "bareJid";
        // public static final String DEVICE_ID = "deviceId";
        const val SIGNED_PRE_KEY_ID = "signedPreKeyId"
        const val SIGNED_PRE_KEYS = "signedPreKeys" // signedPreKeyPublic?
        const val LAST_RENEWAL_DATE = "lastRenewalDate" // lastSignedPreKeyRenewal

        // Identity Table
        const val IDENTITIES_TABLE_NAME = "identities"
        const val BARE_JID = "bareJid"
        const val DEVICE_ID = "deviceId"
        const val FINGERPRINT = "fingerPrint"
        const val CERTIFICATE = "certificate"
        const val TRUST = "trust"
        const val ACTIVE = "active"
        const val LAST_ACTIVATION = "last_activation" // lastMessageReceivedDate
        const val LAST_DEVICE_ID_PUBLISH = "last_deviceid_publish" // DateOfLastDeviceIdPublication
        const val LAST_MESSAGE_RX = "last_message_received" // DateOfLastReceivedMessage
        const val MESSAGE_COUNTER = "message_counter" // message counter
        const val IDENTITY_KEY = "identityKey" // or identityKeyPair

        // Sessions Table
        const val SESSION_TABLE_NAME = "sessions"

        // public static final String BARE_JID = "bareJid";
        // public static final String DEVICE_ID = "deviceId";
        const val SESSION_KEY = "key"
        private const val NUM_TRUSTS_TO_CACHE = 100
    }
}