/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.util.GuiUtils
import okhttp3.internal.notifyAll
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.CallManager

/**
 * Class runs the thread that updates call control notification.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
internal class CtrlNotificationThread
/**
 * Creates new instance of [CtrlNotificationThread].
 *
 * @param ctx the Android context.
 * @param call the call that is controlled by current notification.
 * @param id the notification ID.
 * @param notification call control notification that will be updated by this thread.
 */
(
        /**
         * The Android context.
         */
        private val ctx: Context,
        /**
         * The call that is controlled by notification.
         */
        private val call: Call<*>,
        /**
         * The notification ID.
         */
        private val id: Int,
        /**
         * The call control notification that is being updated by this thread.
         */
        private val notification: Notification) {
    /**
     * The thread that does the updates.
     */
    private var thread: Thread? = null

    /**
     * Flag used to stop the thread.
     */
    private var run = true

    /**
     * Starts notification update thread.
     */
    fun start() {
        thread = Thread { notificationLoop() }
        thread!!.start()
    }

    private fun notificationLoop() {
        val mNotificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        val micEnabled = (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)
        while (run) {
            // Timber.log(TimberLog.FINER, "Running control notification thread " + hashCode());

            // Update call duration timer on call notification
            var callStartDate = CallPeer.CALL_DURATION_START_TIME_UNKNOWN
            val peers = call.getCallPeers()
            if (peers.hasNext()) {
                callStartDate = peers.next()!!.getCallDurationStartTime()
            }
            if (callStartDate != CallPeer.CALL_DURATION_START_TIME_UNKNOWN) {
                notification.contentView.setTextViewText(R.id.call_duration,
                        GuiUtils.formatTime(callStartDate, System.currentTimeMillis()))
            }
            val isSpeakerphoneOn = aTalkApp.audioManager.isSpeakerphoneOn
            notification.contentView.setImageViewResource(R.id.button_speakerphone, if (isSpeakerphoneOn) R.drawable.call_speakerphone_on_dark else R.drawable.call_receiver_on_dark)

            // Update notification call mute status
            val isMute = !micEnabled || CallManager.isMute(call)
            notification.contentView.setImageViewResource(R.id.button_mute,
                    if (isMute) R.drawable.call_microphone_mute_dark else R.drawable.call_microphone_dark)

            // Update notification call hold status
            val isOnHold = CallManager.isLocallyOnHold(call)
            notification.contentView.setImageViewResource(R.id.button_hold,
                    if (isOnHold) R.drawable.call_hold_on_dark else R.drawable.call_hold_off_dark)
            if (run && mNotificationManager != null) {
                mNotificationManager.notify(id, notification)
            }

            synchronized(this) {
                try {
                    (this as Object).wait(UPDATE_INTERVAL)
                } catch (e: InterruptedException) {
                    return
                }
            }
        }
    }

    fun getCtrlId(): Int {
        return id
    }

    /**
     * Stops notification thread.
     */
    fun stop() {
        run = false
        synchronized(this) { this.notifyAll() }
        try {
            thread!!.join()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        /**
         * Notification update interval.
         */
        private const val UPDATE_INTERVAL = 1000L
    }
}