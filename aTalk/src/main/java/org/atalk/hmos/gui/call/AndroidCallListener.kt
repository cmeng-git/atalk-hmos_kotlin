/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.content.Intent
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.plugin.notificationwiring.NotificationWiringActivator
import net.java.sip.communicator.service.notification.NotificationData
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallState
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.CallListener
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import net.java.sip.communicator.service.systray.SystrayService
import net.java.sip.communicator.util.GuiUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.util.*
import java.util.concurrent.Callable

/**
 * A utility implementation of the [CallListener] interface which delivers
 * the `CallEvent`s to the AWT event dispatching thread.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidCallListener : CallListener, CallChangeListener {
    /**
     * {@inheritDoc}
     *
     * Delivers the `CallEvent` to the AWT event dispatching thread.
     */
    override fun incomingCallReceived(event: CallEvent) {
        onCallEvent(event)
        event.sourceCall.addCallChangeListener(this)
    }

    /**
     * {@inheritDoc}
     *
     * Delivers the `CallEvent` to the AWT event dispatching thread.
     */
    override fun outgoingCallCreated(event: CallEvent) {
        onCallEvent(event)
    }

    /**
     * {@inheritDoc}
     *
     * Delivers the `CallEvent` to the AWT event dispatching thread.
     */
    override fun callEnded(event: CallEvent) {
        onCallEvent(event)
        event.sourceCall.removeCallChangeListener(this)
    }

    /**
     * Notifies this `CallListener` about a specific `CallEvent`. Executes in whichever
     * thread brought the event to this listener. Delivers the event to the AWT event dispatching thread.
     *
     * evt the `CallEvent` this `CallListener` is being notified about
     */
    private fun onCallEvent(evt: CallEvent) {
        val call = evt.sourceCall
        Timber.d("Received CallEvent: %s: %s", evt.eventID, call.callId)
        when (evt.eventID) {
            CallEvent.CALL_INITIATED -> {
                storeSpeakerPhoneStatus()
                clearVideoCallState()
                val sid = CallManager.addActiveCall(call)
                startVideoCallActivity(sid)
            }
            CallEvent.CALL_RECEIVED ->                 // cmeng 20220529: Allow two active calls to support call in waiting
                if (CallManager.getActiveCallsCount() > 1) {
                    CallManager.hangupCall(call)
                } else {
                    if (CallManager.getActiveCallsCount() == 0) {
                        storeSpeakerPhoneStatus()
                        clearVideoCallState()
                    }

                    // cmeng - answer call and on hold current - mic not working
                    // Launch UI for user selection of audio or video call
                    val sid = CallManager.addActiveCall(call)
                    val jmCall = call.callId == JingleMessageSessionImpl.getSid()
                    Timber.d("aTalk jmCall: %s;  ForeGround: %s; LockScreen: %s; %s",
                            jmCall, aTalkApp.isForeground, aTalkApp.isDeviceLocked, sid)
                    if (aTalkApp.isForeground) {
                        // For incoming call accepted via Jingle Message propose session.
                        if (jmCall) {
                            // Accept call via VideoCallActivity UI to allow auto-answer the Jingle Call
                            startVideoCallActivity(sid)

                            // Accept call via ReceivedCallActivity for user choice to start audio/video call
                            // This also be executed if android is in locked screen
                            // startReceivedCallActivity(sid);
                        } else {
                            startReceivedCallActivity(sid)
                        }
                    } else {
                        val peerJid = call.getCallPeers().next().getContact()!!.contactJid
                        val msgType = if (jmCall || !aTalkApp.isDeviceLocked) SystrayService.HEADS_UP_INCOMING_CALL else SystrayService.JINGLE_INCOMING_CALL
                        Timber.d("Call msgType: %s", msgType)
                        startIncomingCallNotification(peerJid, sid, msgType, evt.isVideoCall)
                    }

                    // merge call - exception; It will end up with a conference call.
                    // CallManager.answerCallInFirstExistingCall(evt.getSourceCall());
                }
            CallEvent.CALL_ENDED -> endCall(call)
        }
    }

    /**
     * Clears call state stored in previous calls.
     */
    private fun clearVideoCallState() {
        VideoCallActivity.callState = VideoCallActivity.CallStateHolder()
    }

    /**
     * Stores speakerphone status for the call duration; to be restored after the call has ended.
     */
    private fun storeSpeakerPhoneStatus() {
        val audioManager = aTalkApp.audioManager
        speakerPhoneBeforeCall = audioManager.isSpeakerphoneOn
        // Timber.d("Storing speakerphone status: %s", speakerPhoneBeforeCall);
    }

    override fun callPeerAdded(evt: CallPeerEvent) {}
    override fun callPeerRemoved(evt: CallPeerEvent) {}
    override fun callStateChanged(evt: CallChangeEvent) {
        val callState = evt.newValue
        val call = evt.sourceCall
        if (CallState.CALL_ENDED == callState) {
            // remove heads-up notification in case the call end is by remote retract.
            NotificationPopupHandler.removeCallNotification(call.callId)
            if (CallState.CALL_INITIALIZATION == evt.oldValue) {
                if (evt.cause != null && evt.cause.reasonCode != CallPeerChangeEvent.NORMAL_CALL_CLEARING) {
                    // Missed call
                    fireMissedCallNotification(evt)
                }
            }
        }
    }

    /**
     * Starts the video call UI when:
     * a. an incoming call is accepted via Jingle Message Initiation or
     * b. an outgoing call has been initiated.
     *
     * call the `Call` to be handled
     */
    private fun startVideoCallActivity(sid: String?) {
        // Check for resource permission before continue; min mic is enabled
        if (aTalk.isMediaCallAllowed(false)) {
            val videoCall = VideoCallActivity.createVideoCallIntent(appContext, sid)
            videoCall.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            appContext.startActivity(videoCall)
        }
    }

    /**
     * Starts the receive call UI when in legacy jingle call
     *
     * call the `Call` to be handled
     */
    private fun startReceivedCallActivity(sid: String) {
        val intent = Intent(appContext, ReceivedCallActivity::class.java)
                .putExtra(CallManager.CALL_SID, sid)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    /**
     * End the specific call.
     *
     * call the call to end
     */
    private fun endCall(call: Call<*>) {
        // Clears all inCall notification
        AndroidUtils.clearGeneralNotification(appContext)
        // NotificationPopupHandler.removeCallNotification(call.getCallId()); // Called by Jingle call only

        // Removes the call from active calls list and restores speakerphone status
        CallManager.removeActiveCall(call)
        restoreSpeakerPhoneStatus()
    }

    /**
     * Fires missed call notification for given `CallChangeEvent`.
     *
     * evt the `CallChangeEvent` that describes missed call.
     */
    private fun fireMissedCallNotification(evt: CallChangeEvent) {
        val notificationService = NotificationWiringActivator.getNotificationService()
        val contact = evt.cause!!.getSourceCallPeer().getContact()
        if (contact == null || notificationService == null) {
            Timber.w("No contact found - missed call notification skipped")
            return
        }
        val extras = HashMap<String, Any>()
        extras[NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA] = contact
        val contactIcon = contact.image
        val message = contact.displayName + " " + GuiUtils.formatDateTime(Date())
        notificationService.fireNotification(NotificationManager.MISSED_CALL, SystrayService.MISSED_CALL_MESSAGE_TYPE,
                aTalkApp.getResString(R.string.service_gui_CALL_MISSED_CALL), message, contactIcon, extras)
    }

    companion object {
        var VIDEO = "(video): "
        var AUDIO = "(audio): "

        /**
         * The application context.
         */
        private val appContext = aTalkApp.globalContext

        /*
     * Flag stores speakerphone status to be restored to initial value once the call has ended.
     */
        private var speakerPhoneBeforeCall: Boolean? = null

        /**
         * Restores speakerphone status.
         */
        private fun restoreSpeakerPhoneStatus() {
            if (speakerPhoneBeforeCall != null) {
                val audioManager = aTalkApp.audioManager
                audioManager.isSpeakerphoneOn = speakerPhoneBeforeCall!!
                // Timber.d("Restoring speakerphone to: %s", speakerPhoneBeforeCall);
                speakerPhoneBeforeCall = null
            }
        }

        /**
         * Start the heads-up notifications for incoming call when aTalk is not in focus for user to accept or dismiss the call.
         *
         * caller the caller who initial the call
         * sid the JingleMessage sid / call id
         * msgType the Message type:  SystrayService.JINGLE_MESSAGE_PROPOSE / JINGLE_INCOMING_CALL
         * isVideoCall video call if true, audio call otherwise
         */
        fun startIncomingCallNotification(caller: Jid?, sid: String, msgType: Int, isVideoCall: Boolean) {
            val extras = HashMap<String, Any>()
            extras[NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA] = sid
            extras[NotificationData.SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA] = Callable { NotificationPopupHandler.getCallNotificationId(sid) != null } as Callable<Boolean>
            val contactIcon = AvatarManager.getAvatarImageByJid(caller!!.asBareJid())
            val message = (if (isVideoCall) VIDEO else AUDIO) + GuiUtils.formatDateTimeShort(Date())
            val notificationService = NotificationWiringActivator.getNotificationService()
            notificationService.fireNotification(NotificationManager.INCOMING_CALL, msgType,
                    aTalkApp.getResString(R.string.service_gui_CALL_INCOMING, caller.asBareJid()),
                    message, contactIcon, extras)
        }

        /**
         * Answers the given call and launches the call user interface.
         *
         * call the call to answer
         * isVideoCall indicates if video shall be used.
         */
        fun answerCall(call: Call<*>, isVideoCall: Boolean) {
            object : Thread() {
                override fun run() {
                    CallManager.answerCall(call, isVideoCall)
                    val callIdentifier = CallManager.addActiveCall(call)
                    val videoCall = VideoCallActivity.createVideoCallIntent(appContext, callIdentifier)
                    videoCall.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appContext.startActivity(videoCall)
                }
            }.start()
        }
    }
}