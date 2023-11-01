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
package org.atalk.hmos.gui.call

import android.content.Context
import android.content.Intent
import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.plugin.notificationwiring.NotificationWiringActivator
import net.java.sip.communicator.service.notification.NotificationData
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.GuiUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.impl.androidnotification.VibrateHandlerImpl
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.atalk.util.MediaType
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.MessageBuilder
import org.jivesoftware.smack.packet.StanzaBuilder
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jivesoftware.smackx.jingle.JingleManager
import org.jivesoftware.smackx.jingle.element.Jingle
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription.*
import org.jivesoftware.smackx.jinglemessage.JingleMessageListener
import org.jivesoftware.smackx.jinglemessage.JingleMessageManager
import org.jivesoftware.smackx.jinglemessage.JingleMessageType
import org.jivesoftware.smackx.jinglemessage.element.JingleMessage
import org.jxmpp.jid.FullJid
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.util.*

/**
 * Handler for the received Jingle Message Listener events.
 * XEP-0353: Jingle Message Initiation 0.3 (2017-09-11)
 *
 * @author Eng Chong Meng
 */
class JingleMessageSessionImpl private constructor(connection: XMPPConnection) : JingleMessageListener {
    init {
        val jingleMessageManager = JingleMessageManager.getInstanceFor(connection)
        jingleMessageManager.addIncomingListener(this)
    }

    /**
     * Call from JingleMessageManager with the received original message and Proceed JingleMessage.
     * The message is returned by the callee, proceed with the call session-initiate using the id as sid
     *
     * @param connection XMPPConnection
     * @param jingleMessage Proceed received
     * @param message the original received Jingle Message
     */
    override fun onJingleMessageProceed(connection: XMPPConnection, jingleMessage: JingleMessage, message: Message) {
        val sid = jingleMessage.id
        mRemote = message.from
        Timber.d("Jingle Message proceed received")
        // notify all listeners in preparation for Jingle RTP session-accept; sid - must use the same;
        // and to make earlier registerJingleSessionHandler() with JingleManager
        notifyOnStateChange(connection, JingleMessageType.proceed, mRemote!!, sid)
        endJmCallProcess(R.string.service_gui_CALL_ANSWER, mRemote!!)
        val telephonyJabber = jmStateListeners[connection]
        if (telephonyJabber != null) AndroidCallUtil.createCall(aTalkApp.globalContext, telephonyJabber.getProtocolProvider(), mRemote, isVideoCall)
    }

    /**
     * Call from JingleMessageManager with the received original message and propose JingleMessage.
     * The Callee has rejected the call. So end the call in progress
     *
     * @param connection XMPPConnection
     * @param jingleMessage Reject received
     * @param message the original received Jingle Message
     */
    override fun onJingleMessageReject(connection: XMPPConnection, jingleMessage: JingleMessage, message: Message) {
        val sid = jingleMessage.id
        mRemote = message.from
        allowSendRetract = false
        notifyOnStateChange(connection, JingleMessageType.reject, mRemote!!, sid)
        endJmCallProcess(R.string.service_gui_CALL_REJECTED, mRemote!!)
    }
    //==================== Incoming Call processes flow ====================//
    /**
     * Call from JingleMessageManager with the received original message and Propose JingleMessage.
     *
     * @param connection XMPPConnection
     * @param jingleMessage propose received
     * @param message the original received Jingle Message
     */
    override fun onJingleMessagePropose(connection: XMPPConnection, jingleMessage: JingleMessage, message: Message) {
        val media = jingleMessage.media
        val isVideoCall = media.contains(MediaType.VIDEO.toString())
        val sid = jingleMessage.id
        mConnection = connection
        mRemote = message.from

        // Check for resource permission before proceed, if mic is enabled at a minimum
        if (aTalk.isMediaCallAllowed(false)) {
            notifyOnStateChange(connection, JingleMessageType.propose, mRemote!!, sid)
            // v3.0.5: always starts with heads-up notification for JingleMessage call propose
            AndroidCallListener.startIncomingCallNotification(mRemote, sid, SystrayService.JINGLE_MESSAGE_PROPOSE, isVideoCall)
        } else {
            sendJingleMessageReject(sid)
        }
    }

    /**
     * Call from JingleMessageManager with the received original message and Accept JingleMessage.
     * The received "accept" message is forward by the server. Check to see if we are the original sender;
     * Request caller to proceed with the call if so
     *
     * @param connection XMPPConnection
     * @param jingleMessage Accept received from the server
     * @param message the original received Jingle Message
     */
    override fun onJingleMessageAccept(connection: XMPPConnection, jingleMessage: JingleMessage, message: Message) {
        // Valid caller found, and we are the sender of the "accept" jingle message, then request sender to proceed
        val callee = message.from
        if (mRemote != null) {
            val sid = jingleMessage.id
            if (connection.user.equals(callee)) {
                // notify all listeners for session-accept; sid - must use the same;
                // and to make earlier registerJingleSessionHandler() with JingleManager
                notifyOnStateChange(connection, JingleMessageType.accept, mRemote!!, sid)
                message.from = mRemote // message actual send to
                val msgProceed = JingleMessage(JingleMessage.ACTION_PROCEED, sid)
                sendJingleMessage(connection, msgProceed, message)
                aTalkApp.showToastMessage(R.string.service_gui_CONNECTING_ACCOUNT, mRemote)
            } else {
                // Dismiss notification if another user instance has accepted the call propose.
                NotificationPopupHandler.removeCallNotification(sid)
            }
            // Display to user who has accepted the call
            endJmCallProcess(R.string.service_gui_CALL_ANSWER, callee)
        }
    }

    /**
     * Call from JingleMessageManager with the received original message and Retract JingleMessage.
     * i.e. when caller decides to abort the call. Send missed call notification.
     *
     * @param connection XMPPConnection
     * @param jingleMessage Retract received
     * @param message the original received Jingle Message
     */
    override fun onJingleMessageRetract(connection: XMPPConnection, jingleMessage: JingleMessage, message: Message) {
        val sid = jingleMessage.id
        NotificationPopupHandler.removeCallNotification(sid)
        if (mRemote != null) {
            notifyOnStateChange(connection, JingleMessageType.retract, mRemote!!, sid)
            endJmCallProcess(R.string.service_gui_CALL_END, message.from)
            onCallRetract(mRemote!!.asEntityFullJidIfPossible())
        }
    }

    private fun onCallRetract(caller: FullJid) {
        // fired a missed call notification
        val extras = HashMap<String, Any>()
        extras[NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA] = caller
        val contactIcon = AvatarManager.getAvatarImageByJid(caller.asBareJid())
        val textMessage = caller.asBareJid().toString() + " " + GuiUtils.formatDateTimeShort(Date())
        val notificationService: NotificationService = NotificationWiringActivator.getNotificationService()
        notificationService.fireNotification(NotificationManager.MISSED_CALL, SystrayService.MISSED_CALL_MESSAGE_TYPE,
                aTalkApp.getResString(R.string.service_gui_CALL_MISSED_CALL), textMessage, contactIcon, extras)
    }

    /**
     * Notify all the registered StateListeners on JingleMessage state change for taking action.
     *
     * @param connection XMPPConnection
     * @param type JingleMessageType enum
     * @param remote The remote caller/callee, should be a FullJid
     * @param sid The Jingle sessionId for session-initiate; must be from negotiated JingleMessage
     */
    private fun notifyOnStateChange(connection: XMPPConnection, type: JingleMessageType?, remote: Jid, sid: String?) {
        jmStateListeners[connection]?.onJmStateChange(type, remote.asEntityFullJidIfPossible(), sid)
    }

    interface JmStateListener {
        fun onJmStateChange(type: JingleMessageType?, remote: FullJid?, sid: String?)
    }

    interface JmEndListener {
        fun onJmEndCallback()
    }

    companion object {
        private val INSTANCES = WeakHashMap<XMPPConnection, JingleMessageSessionImpl>()
        private val jmStateListeners = HashMap<XMPPConnection, OperationSetBasicTelephonyJabberImpl>()
        private var jmEndListener: JmEndListener? = null

        // Both the mConnection and mRemote references will get initialized to the current outgoing / incoming call.
        private var mConnection: XMPPConnection? = null
        private var mRemote // BareJid or FullJid pending on state change update
                : Jid? = null

        // JingleMessageSession call sid.
        private var mSid: String? = null
        private var isVideoCall = false

        // Should send retract to close the loop, when user ends the call and jingle session-initiate has not started
        private var allowSendRetract = false
        @Synchronized
        fun getInstanceFor(connection: XMPPConnection): JingleMessageSessionImpl {
            var jingleMessageSessionImpl = INSTANCES[connection]
            if (jingleMessageSessionImpl == null) {
                jingleMessageSessionImpl = JingleMessageSessionImpl(connection)
                INSTANCES[connection] = jingleMessageSessionImpl
            }
            return jingleMessageSessionImpl
        }
        //==================== Outgoing Call processes flow ====================//
        /**
         * Prepare and send the Jingle Message <propose></propose> to callee.
         *
         * @param connection XMPPConnection
         * @param remote the targeted contact (BareJid) to send the call propose
         * @param videoCall video call if true, audio call otherwise
         */
        fun sendJingleMessagePropose(connection: XMPPConnection, remote: Jid?, videoCall: Boolean) {
            mConnection = connection
            mRemote = remote
            isVideoCall = videoCall
            allowSendRetract = true
            val sid = JingleManager.randomId()
            val msgId = "jm-propose-$sid"
            val msgPropose = JingleMessage(JingleMessage.ACTION_PROPOSE, sid)
            var rtpBuilder = getBuilder()
            rtpBuilder.setMedia("audio")
            msgPropose.addDescriptionExtension(rtpBuilder.build())

            // Add video description if true
            if (videoCall) {
                rtpBuilder = getBuilder()
                rtpBuilder.setMedia("video")
                msgPropose.addDescriptionExtension(rtpBuilder.build())
            }
            val msgBuilder = StanzaBuilder.buildMessage(msgId)
                    .ofType(Message.Type.chat)
                    .from(connection.user)
                    .to(mRemote!!.asBareJid())
                    .setLanguage("us")
                    .addExtension(msgPropose)
            try {
                startJMCallActivity(sid)
                connection.sendStanza(msgBuilder.build())
            } catch (e: SmackException.NotConnectedException) {
                Timber.e("Error in sending jingle message propose to: %s: %s", mRemote, e.message)
            } catch (e: InterruptedException) {
                Timber.e("Error in sending jingle message propose to: %s: %s", mRemote, e.message)
            }
        }

        /**
         * Start JingleMessage Activity with UI allowing caller to retract call.
         *
         * @param sid the unique for Jingle Message / Jingle Session sid
         */
        private fun startJMCallActivity(sid: String) {
            val context = aTalkApp.globalContext
            val intent = Intent(context, JingleMessageCallActivity::class.java)
            intent.putExtra(CallManager.CALL_SID, sid)
            intent.putExtra(CallManager.CALL_EVENT, NotificationManager.OUTGOING_CALL)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }

        /**
         * Prepare Jingle Message Retract and send it to the remote callee if call is retracted by caller.
         *
         * @param remote the remote callee
         * @param sid the intended Jingle Message call id
         */
        fun sendJingleMessageRetract(remote: Jid, sid: String?) {
            if (mConnection != null) {
                allowSendRetract = false
                val msgRetract = JingleMessage(JingleMessage.ACTION_RETRACT, sid)
                val messageBuilder = StanzaBuilder.buildMessage()
                        .ofType(Message.Type.chat)
                        .from(remote.asBareJid())
                        .to(mConnection!!.user)
                sendJingleMessage(mConnection, msgRetract, messageBuilder.build())
                endJmCallProcess(R.string.service_gui_CALL_RETRACTED, mConnection!!.user)
            }
        }

        /**
         * Send Jingle Message Retract if the peer was the targeted remote;
         * Request from CallManager if Jingle RTP has yet to start.
         *
         * @param peer the remote call peer
         */
        fun sendJingleMessageRetract(peer: CallPeer) {
            val jid = peer.getPeerJid()
            if (mRemote != null && mRemote!!.isParentOf(jid) && allowSendRetract) {
                sendJingleMessageRetract(mRemote!!, peer.getCall()!!.callId)
            }
        }

        /**
         * On user accepted call, send an "accept: message to own bareJid; server will then forward to all our resources.
         * Cancel vibrate in progress; The ending of ring tone is handled by the caller
         * i.e. NotificationPopupHandler.removeCallNotification(id);
         *
         * Note: the attached message is with to/from reversed for sendJingleMessage requirements
         *
         * @param sid the intended Jingle Message call id
         */
        fun sendJingleAccept(sid: String?) {
            if (mConnection != null) {
                val local = mConnection!!.user
                mSid = sid
                val msgAccept = JingleMessage(JingleMessage.ACTION_ACCEPT, sid)
                val messageBuilder = StanzaBuilder.buildMessage()
                        .ofType(Message.Type.chat)
                        .from(local.asBareJid()) // the actual message send to
                        .to(local) // the actual message send from
                sendJingleMessage(mConnection, msgAccept, messageBuilder.build())
            }
        }

        /**
         * Local user has rejected the call; prepare Jingle Message reject and send it to the remote.
         *
         * @param sid the intended Jingle Message call id
         */
        fun sendJingleMessageReject(sid: String?) {
            if (mRemote != null && mConnection != null) {
                val msgReject = JingleMessage(JingleMessage.ACTION_REJECT, sid)
                val messageBuilder = StanzaBuilder.buildMessage()
                        .ofType(Message.Type.chat)
                        .from(mRemote!!.asBareJid())
                        .to(mConnection!!.user)
                sendJingleMessage(mConnection, msgReject, messageBuilder.build())
                endJmCallProcess(R.string.service_gui_CALL_REJECTED, mConnection!!.user)
            }
        }
        //==================== Helper common utilities ====================//
        /**
         * Build the message from source and add Jingle Message attachment before sending.
         *
         * @param connection XMPPConnection
         * @param jingleMessage the extension element to be sent
         * @param message the source message for parameters extraction; to and from may have been modified by caller
         */
        private fun sendJingleMessage(connection: XMPPConnection?, jingleMessage: JingleMessage, message: Message) {
            val msgBuilder = StanzaBuilder.buildMessage(message.stanzaId)
                    .ofType(Message.Type.chat)
                    .from(connection!!.user)
                    .to(message.from)
                    .setLanguage(message.language)
                    .addExtension(jingleMessage)
            try {
                connection.sendStanza(msgBuilder.build())
            } catch (e: SmackException.NotConnectedException) {
                Timber.e("Error in sending jingle message: %s: %s : %s", jingleMessage.action,
                        jingleMessage.id, e.message)
            } catch (e: InterruptedException) {
                Timber.e("Error in sending jingle message: %s: %s : %s", jingleMessage.action,
                        jingleMessage.id, e.message)
            }
        }

        /**
         * Check to see if the session-initiate is triggered via JingleMessage `accept`.
         *
         * @param jingleSI incoming Jingle session-initiate for verification
         *
         * @return true if the call was via JingleMessage
         */
        fun isJingleMessageAccept(jingleSI: Jingle): Boolean {
            // see <a href="https://xmpp.org/extensions/xep-0166.html#def">XEP-0166 Jingle#7. Formal Definition</a>
            var initiator = jingleSI.initiator
            if (initiator == null) {
                // conversations excludes initiator attribute in session-initiate
                initiator = jingleSI.from.asEntityFullJidIfPossible()
            }
            return initiator.equals(mRemote)
        }

        /**
         * Disable retract sending once Jingle session-initiate has started
         *
         * @param allow false to disable sending retract when user ends the call
         * CallPeerJabberImpl.initiateSession
         */
        fun setAllowSendRetract(allow: Boolean) {
            allowSendRetract = allow
        }

        /**
         * Get the current active remote in communication.
         *
         * @return Jid of the callee
         */
        fun getRemote(): Jid? {
            return mRemote
        }

        /**
         * legacy call must check for correct sid before assumes JingleMessage session call
         */
        fun getSid(): String? {
            return mSid
        }

        /**
         * This is called when the Jingle Message process cycle has ended i.e. accept, reject or retract etc.
         * Jingle message must stop both the RingTone looping and vibrator independent of Jingle call.
         *
         * @param id String id
         * @param arg arg for the string format
         */
        private fun endJmCallProcess(id: Int?, vararg arg: Any) {
            if (id != null) {
                aTalkApp.showToastMessage(id, *arg)
            }
            VibrateHandlerImpl().cancel()
            if (jmEndListener != null) {
                jmEndListener!!.onJmEndCallback()
                jmEndListener = null
            }
        }

        /**
         * Add JmStateListener.
         *
         * @param basicTelephony OperationSetBasicTelephonyJabberImpl instance
         */
        fun addJmStateListener(basicTelephony: OperationSetBasicTelephonyJabberImpl) {
            val pps = basicTelephony.getProtocolProvider()
            jmStateListeners[pps.connection!!] = basicTelephony
        }

        /**
         * Remove JmStateListener.
         *
         * @param basicTelephony OperationSetBasicTelephonyJabberImpl instance
         */
        fun removeJmStateListener(basicTelephony: OperationSetBasicTelephonyJabberImpl) {
            val pps = basicTelephony.getProtocolProvider()
            jmStateListeners.remove<XMPPConnection?, OperationSetBasicTelephonyJabberImpl>(pps.connection)
        }

        /**
         * Add FinishListener
         *
         * @param fl JmEndListener to finish the activity
         */
        fun setJmEndListener(fl: JmEndListener?) {
            jmEndListener = fl
        }
    }
}