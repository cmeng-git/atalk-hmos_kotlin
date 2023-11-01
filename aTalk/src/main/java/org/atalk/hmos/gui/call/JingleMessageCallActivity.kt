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

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.atalk.service.osgi.OSGiActivity
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jxmpp.jid.Jid

/**
 * The process to handle the incoming and outgoing call for `Jingle Message` states changes.
 * Note: incoming call is via ReceivedCallActivity instead due to android-12 constraint.
 *
 * Implementation for aTalk v3.0.5:
 * Starting with Android 12 notifications will not work if they do not start activities directly
 * NotificationService: Indirect notification activity start (trampoline) from org.atalk.hmos blocked
 * [Notification trampoline restrictions-Android12](https://proandroiddev.com/notification-trampoline-restrictions-android12-7d2a8b15bbe2)
 * Heads-up notification launches ReceivedCallActivity directly; failed if launches JingleMessageCallActivity => ReceivedCallActivity;
 * ActivityTaskManager: Background activity start will failed for android-12 and above.
 *
 * @author Eng Chong Meng
 */
class JingleMessageCallActivity : OSGiActivity(), JingleMessageSessionImpl.JmEndListener {
    private var peerAvatar: ImageView? = null
    private var mSid: String? = null

    /**
     * Create the UI with call hang up button to retract call for outgoing call.
     * Incoming JingleMessage <propose></propose> will only sendJingleAccept(mSid), automatically only
     * if aTalk is not in locked screen; else show UI for user choice to accept or reject call.
     * Note: hedds-up notification is not shown when device is in locked screen.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.call_received)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // Implementation not supported currently
        findViewById<ImageButton>(R.id.videoCallButton).visibility = View.GONE
        val callButton = findViewById<ImageButton>(R.id.callButton)
        val hangUpButton = findViewById<ImageButton>(R.id.hangupButton)
        peerAvatar = findViewById(R.id.calleeAvatar)
        JingleMessageSessionImpl.setJmEndListener(this)
        val extras = intent.extras
        if (extras != null) {
            // Jingle Message / Jingle Session sid
            mSid = extras.getString(CallManager.CALL_SID)
            val eventType = extras.getString(CallManager.CALL_EVENT)
            val isIncomingCall = NotificationManager.INCOMING_CALL == eventType
            val autoAccept = extras.getBoolean(CallManager.AUTO_ACCEPT, false)
            if (isIncomingCall && autoAccept) {
                JingleMessageSessionImpl.sendJingleAccept(mSid)
                return
            }
            val remote = JingleMessageSessionImpl.getRemote()
            findViewById<TextView>(R.id.calleeAddress).text = remote
            setPeerImage(remote)
            if (isIncomingCall) {
                // Call accepted, send Jingle Message <accept/> to inform caller.
                callButton.setOnClickListener { v: View? -> JingleMessageSessionImpl.sendJingleAccept(mSid) }

                // Call rejected, send Jingle Message <reject/> to inform caller.
                hangUpButton.setOnClickListener { v: View? -> JingleMessageSessionImpl.sendJingleMessageReject(mSid) }
            } else { // NotificationManager.OUTGOING_CALL
                // Call retract, send Jingle Message <retract/> to inform caller.
                hangUpButton.setOnClickListener { v: View? ->
                    // NPE: Get triggered with remote == null at time???
                    if (remote != null) {
                        JingleMessageSessionImpl.sendJingleMessageRetract(remote, mSid)
                    }
                }
                callButton.visibility = View.GONE
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Hangs up the call when back is pressed as this Activity will not be displayed again.
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else super.onKeyUp(keyCode, event)
    }

    /**
     * Bring aTalk to foreground, and end JingleMessageCallActivity UI; else user is prompted with
     * both heads-up notification and ReceivedCallActivity UI to take action, this confuses user;
     * Also to avoid failure arises on launching ...CallActivity from background;
     *
     * Note: Due to android design constraints i.e. only activity launch is allowed when android is in locked screen.
     * Hence two UI are still being shown on call received i.e. JingleMessageCallActivity and VideoCallActivity
     */
    override fun onJmEndCallback() {
        NotificationPopupHandler.removeCallNotification(mSid!!)
        startActivity(aTalk::class.java)

        // Must destroy JingleMessageCallActivity UI, else remain visible after end call.
        finish()
    }

    /**
     * Sets the peer avatar.
     *
     * @param callee the avatar of the callee
     */
    private fun setPeerImage(callee: Jid?) {
        if (callee == null) return
        val avatar = AvatarManager.getAvatarImageByJid(callee.asBareJid())
        if (avatar != null && avatar.isNotEmpty()) {
            peerAvatar!!.setImageBitmap(AndroidImageUtil.bitmapFromBytes(avatar))
        }
    }
}