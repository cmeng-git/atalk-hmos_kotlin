/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import org.atalk.hmos.R
import org.atalk.hmos.gui.call.CallManager
import org.atalk.hmos.gui.call.CallManager.getActiveCall
import org.atalk.hmos.gui.call.CallUIUtils.getCalleeAvatar
import org.atalk.hmos.gui.call.VideoCallActivity
import org.atalk.hmos.gui.util.AndroidImageUtil.bitmapFromBytes
import org.atalk.impl.androidnotification.AndroidNotifications
import org.atalk.impl.androidtray.NotificationPopupHandler.Companion.getPendingIntentFlag
import timber.log.Timber
import java.util.*

/**
 * Class manages currently running call control notifications. Those are displayed when [VideoCallActivity] is
 * minimized or closed and the call is still active. They allow user to do basic call operations like mute, put on hold
 * and hang up directly from the status bar.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallNotificationManager
/**
 * Private constructor
 */
private constructor(
        /**
         * The call ID that will be used in this `Instance`, and the `Intents` binding.
         * The ID is managed by [CallManager].
         */
        private val mCallId: String) {

    /**
     * Active running notificationHandler if not null.
     */
    private var mNotificationHandler: CtrlNotificationThread? = null

    /**
     * Android system NOTIFICATION_SERVICE manager
     */
    private var mNotificationManager: NotificationManager? = null

    /**
     * Back to call pending intent, to allow trigger from message chat send button
     */
    private var pVideo: PendingIntent? = null

    /**
     * Displays notification allowing user to control the call state directly from the status bar.
     *
     * @param context the Android context.
     */
    @Synchronized
    fun showCallNotification(context: Context) {
        val call = getActiveCall(mCallId)
                ?: throw IllegalArgumentException("There's no call with id: $mCallId")

        // Sets call peer display name and avatar in content view
        val contentView = RemoteViews(context.packageName, R.layout.call_status_bar_notification)
        val callPeer = call.getCallPeers().next()
        val avatar = getCalleeAvatar(call)
        if (avatar != null) {
            contentView.setImageViewBitmap(R.id.avatarView, bitmapFromBytes(avatar))
        }
        contentView.setTextViewText(R.id.calleeDisplayName, callPeer.getDisplayName())

        // Binds pending intents using the requestCodeBase to avoid being cancel; aTalk can have 2 callNotifications.
        val requestCodeBase = if (requestCodes.containsValue(10)) 0 else 10
        requestCodes[mCallId] = requestCodeBase
        setIntents(context, contentView, requestCodeBase)
        val notification = NotificationCompat.Builder(context, AndroidNotifications.CALL_GROUP)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.missed_call)
                .setContent(contentView) // Sets the content view
                .build()

        // Must use random Id, else notification cancel() may not work properly
        val id = (System.currentTimeMillis() % 10000).toInt()
        mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager!!.notify(id, notification)
        mNotificationHandler = CtrlNotificationThread(context, call, id, notification)
        call.addCallChangeListener(object : CallChangeListener {
            override fun callPeerAdded(evt: CallPeerEvent) {}
            override fun callPeerRemoved(evt: CallPeerEvent) {
                stopNotification()
                call.removeCallChangeListener(this)
            }

            override fun callStateChanged(evt: CallChangeEvent) {}
        })

        // Starts notification update thread
        mNotificationHandler!!.start()
    }

    /**
     * Binds pending intents to all control `Views`.
     *
     * @param ctx Android context.
     * @param contentView notification root `View`.
     * @param mCallId the call ID that will be used in the `Intents`
     */
    private fun setIntents(ctx: Context, contentView: RemoteViews, requestCodeBase: Int) {
        // Speakerphone button
        var requestCodeBase = requestCodeBase
        val pSpeaker = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getToggleSpeakerIntent(mCallId),
                getPendingIntentFlag(false, false))
        contentView.setOnClickPendingIntent(R.id.button_speakerphone, pSpeaker)

        // Mute button
        val pMute = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getToggleMuteIntent(mCallId),
                getPendingIntentFlag(false, false))
        contentView.setOnClickPendingIntent(R.id.button_mute, pMute)

        // Hold button
        val pHold = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getToggleOnHoldIntent(mCallId),
                getPendingIntentFlag(false, false))
        contentView.setOnClickPendingIntent(R.id.button_hold, pHold)

        // Hangup button
        val pHangup = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getHangupIntent(mCallId),
                getPendingIntentFlag(false, false))
        contentView.setOnClickPendingIntent(R.id.button_hangup, pHangup)

        // Transfer call via VideoCallActivity, and execute in place to show VideoCallActivity (note-10)
        // Call via broadcast receiver has problem of CallTransferDialog keeps popping up
        val pTransfer = Intent(ctx, VideoCallActivity::class.java)
        pTransfer.putExtra(CallManager.CALL_SID, mCallId)
        pTransfer.putExtra(CallManager.CALL_TRANSFER, true)
        pVideo = PendingIntent.getActivity(ctx, requestCodeBase++, pTransfer, getPendingIntentFlag(false, false))
        contentView.setOnClickPendingIntent(R.id.button_transfer, pVideo)

        // Show video call Activity on click; pendingIntent executed in place i.e. no via Broadcast receiver
        val videoCall = Intent(ctx, VideoCallActivity::class.java)
        videoCall.putExtra(CallManager.CALL_SID, mCallId)
        videoCall.putExtra(CallManager.CALL_TRANSFER, false)
        pVideo = PendingIntent.getActivity(ctx, requestCodeBase, videoCall, getPendingIntentFlag(false, false))
        contentView.setOnClickPendingIntent(R.id.button_back_to_call, pVideo)

        // Binds launch VideoCallActivity to the whole area
        contentView.setOnClickPendingIntent(R.id.notificationContent, pVideo)
    }

    /**
     * Stops the notification running for the call with Id stored in mNotificationHandler.
     */
    @Synchronized
    fun stopNotification() {
        if (mNotificationHandler != null) {
            Timber.d("Call Notification Panel removed: %s; id: %s", mCallId, mNotificationHandler!!.getCtrlId())
            // Stop NotificationHandler and remove the notification from system notification bar
            mNotificationHandler!!.stop()
            mNotificationManager!!.cancel(mNotificationHandler!!.getCtrlId())
            mNotificationHandler = null
            INSTANCES.remove(mCallId)
            requestCodes.remove(mCallId)
        }
    }

    /**
     * Checks if there is notification running for a call.
     *
     * @return `true` if there is notification running in this instance.
     */
    @get:Synchronized
    val isNotificationRunning: Boolean
        get() = mNotificationHandler != null

    fun backToCall() {
        if (pVideo != null) {
            try {
                pVideo!!.send()
            } catch (e: PendingIntent.CanceledException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        /**
         * Map content contains callId to CallNotificationManager instance.
         */
        private val INSTANCES = WeakHashMap<String, CallNotificationManager>()

        /**
         * Map to facilitate the toggle of requestCodeBase between 0 and 10 to avoid existing PendingIntents get cancel:
         * FLAG_CANCEL_CURRENT [PendingIntent](https://developer.android.com/reference/android/app/PendingIntent).
         */
        private val requestCodes = HashMap<String, Int>()

        /**
         * Returns call control notifications manager for the given callId.
         *
         * @return the `CallNotificationManager`.
         */
        @Synchronized
        fun getInstanceFor(callId: String): CallNotificationManager {
            var callNotificationManager = INSTANCES[callId]
            if (callNotificationManager == null) {
                callNotificationManager = CallNotificationManager(callId)
                INSTANCES[callId] = callNotificationManager
            }
            return callNotificationManager
        }
    }
}