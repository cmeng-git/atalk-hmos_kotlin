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

import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicInstantMessagingJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.OperationSetMultiUserChatJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.omemo.OmemoManager
import org.jivesoftware.smackx.omemo.OmemoManager.InitializationFinishedCallback
import org.jivesoftware.smackx.omemo.OmemoService
import org.jxmpp.jid.BareJid
import timber.log.Timber

/**
 * Android Omemo service.
 *
 * @author Eng Chong Meng
 */
class AndroidOmemoService(pps: ProtocolProviderService) : InitializationFinishedCallback {
    private val mOmemoManager: OmemoManager
    private val mConnection: XMPPConnection

    init {
        mConnection = pps.connection!!
        mOmemoManager = initOmemoManager(pps)
        Timber.i("### Registered omemo messageListener for: %s", pps.accountID.mUserID)
        val imOpSet = pps.getOperationSet(OperationSetBasicInstantMessaging::class.java)
        (imOpSet as OperationSetBasicInstantMessagingJabberImpl).registerOmemoListener(mOmemoManager)
        val mucOpSet = pps.getOperationSet(OperationSetMultiUserChat::class.java)
        (mucOpSet as OperationSetMultiUserChatJabberImpl).registerOmemoMucListener(mOmemoManager)
    }

    /**
     * Initialize store for the specific protocolProvider and Initialize the OMEMO Manager
     *
     * @param pps protocolProvider for the current user
     * @return instance of OMEMO Manager
     */
    private fun initOmemoManager(pps: ProtocolProviderService): OmemoManager {
        val mOmemoStore = OmemoService.getInstance().omemoStoreBackend
        val userJid = if (mConnection.user != null) {
            mConnection.user.asBareJid()
        } else {
            pps.accountID.bareJid!!
        }
        val defaultDeviceId: Int
        val deviceIds = mOmemoStore.localDeviceIdsOf(userJid)
        if (deviceIds.isEmpty()) {
            defaultDeviceId = OmemoManager.randomDeviceId()
            (mOmemoStore as SQLiteOmemoStore).setDefaultDeviceId(userJid, defaultDeviceId)
        } else {
            defaultDeviceId = deviceIds.first()
        }

        // OmemoManager omemoManager = OmemoManager.getInstanceFor(mConnection); - not working for aTalk
        val omemoManager = OmemoManager.getInstanceFor(mConnection, defaultDeviceId)
        try {
            omemoManager.setTrustCallback((mOmemoStore as SQLiteOmemoStore).trustCallBack)
        } catch (e: Exception) {
            Timber.w("SetTrustCallBack Exception: %s", e.message)
        }
        return omemoManager
    }

    /**
     * The method should only be called upon user authentication
     * Init smack reply timeout for omemo prekey publish whose reply takes 7(normal) to 11s(bosh)
     * on Note3 & Note10 with remote server; but takes only 2s on aTalk server
     */
    fun initOmemoDevice() {
        isOmemoInitSuccessful = false
        mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_OMEMO_INIT_TIMEOUT
        mOmemoManager.initializeAsync(this)
    }

    override fun initializationFinished(manager: OmemoManager) {
        isOmemoInitSuccessful = true
        mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
        Timber.d("Initialize OmemoManager successful for %s", manager.ownDevice)
    }

    override fun initializationFailed(cause: Exception) {
        isOmemoInitSuccessful = false
        mConnection.replyTimeout = ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT
        val title = aTalkApp.getResString(R.string.omemo_init_failed_title)
        val errMsg = cause.message
        Timber.w("%s: %s", title, errMsg)
        if (errMsg != null) {
            if (errMsg.contains("Invalid IdentityKeyPairs") || errMsg.contains("CorruptedOmemoKeyException")) {
                val msg = aTalkApp.getResString(R.string.omemo_init_failed_CorruptedOmemoKeyException,
                        mOmemoManager.ownDevice, errMsg)
                DialogActivity.showDialog(aTalkApp.globalContext, title, msg)
            } else {
                aTalkApp.showToastMessage(R.string.omemo_init_failed_noresponse, mOmemoManager.ownDevice)
            }
        }
    }

    companion object {
        var isOmemoInitSuccessful = false
    }
}