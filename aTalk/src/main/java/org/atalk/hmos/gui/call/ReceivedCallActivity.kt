/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallState
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber

/**
 * The `ReceivedCallActivity` is the activity that corresponds to the screen shown on incoming call.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class ReceivedCallActivity : OSGiActivity(), CallChangeListener {
    /**
     * The identifier of the call.
     */
    private var mSid: String? = null

    // Jingle Message incoming call parameters
    private val mAutoAccept = false

    /**
     * The corresponding call.
     */
    private var call: Call<*>? = null

    /**
     * Called when the activity is starting. Initializes the call identifier.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the
     * data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.call_received)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val hangupView = findViewById<ImageView>(R.id.hangupButton)
        hangupView.setOnClickListener { v: View? -> hangupCall() }
        val mCallButton = findViewById<ImageView>(R.id.callButton)
        mCallButton.setOnClickListener { v: View? -> answerCall(call, false) }

        // Proceed with video call only if camera permission is granted.
        val mVideoCallButton = findViewById<ImageView>(R.id.videoCallButton)
        mVideoCallButton.setOnClickListener { v: View? ->
            answerCall(call, aTalk.hasPermission(this, false, aTalk.PRC_CAMERA, Manifest.permission.CAMERA))
        }
        val extras = intent.extras
        Timber.d("ReceivedCall onCreate!!!")
        if (extras != null) {
            mSid = extras.getString(CallManager.CALL_SID)

            // Handling the incoming JingleCall
            call = CallManager.getActiveCall(mSid)
            if (call != null) {
                // call.setAutoAnswer(mAutoAccept);
                val callee = CallUIUtils.getCalleeAddress(call!!)
                val addressView = findViewById<TextView>(R.id.calleeAddress)
                addressView.text = callee
                val avatar = CallUIUtils.getCalleeAvatar(call)
                if (avatar != null) {
                    val bitmap = BitmapFactory.decodeByteArray(avatar, 0, avatar.size)
                    val avatarView = findViewById<ImageView>(R.id.calleeAvatar)
                    avatarView.setImageBitmap(bitmap)
                }
            } else {
                Timber.e("There is no call with ID: %s", mSid)
                finish()
                return
            }
            if (extras.getBoolean(CallManager.AUTO_ACCEPT, false)) mVideoCallButton.performClick()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        // Call is null for call via JingleMessage <propose/>
        if (call != null) {
            if (call!!.callState == CallState.CALL_ENDED) {
                finish()
            } else {
                call!!.addCallChangeListener(this)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        if (call != null) {
            call!!.removeCallChangeListener(this)
        }
        NotificationPopupHandler.removeCallNotification(mSid!!)
        super.onPause()
    }

    /**
     * Answers the given call and launches the call user interface.
     *
     * @param call the call to answer
     * @param isVideoCall indicates if video shall be usede
     */
    private fun answerCall(call: Call<*>?, isVideoCall: Boolean) {
        CallManager.answerCall(call, isVideoCall)
        runOnUiThread {
            val videoCall = VideoCallActivity.createVideoCallIntent(this@ReceivedCallActivity, mSid)
            startActivity(videoCall)
            finish()
        }
    }

    /**
     * Hangs up the call and finishes this `Activity`.
     */
    private fun hangupCall() {
        CallManager.hangupCall(call)
        finish()
    }

    /**
     * {@inheritDoc}
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Block the back key action to end this activity.
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            // hangupCall();
            true
        } else super.onKeyUp(keyCode, event)
    }

    /**
     * Indicates that a new call peer has joined the source call.
     *
     * @param evt the `CallPeerEvent` containing the source call and call peer.
     */
    override fun callPeerAdded(evt: CallPeerEvent) {}

    /**
     * Indicates that a call peer has left the source call.
     *
     * @param evt the `CallPeerEvent` containing the source call and call peer.
     */
    override fun callPeerRemoved(evt: CallPeerEvent) {}

    /**
     * Indicates that a change has occurred in the state of the source call.
     *
     * @param evt the `CallChangeEvent` instance containing the source calls and its old and new state.
     */
    override fun callStateChanged(evt: CallChangeEvent) {
        val callState = evt.newValue
        if (CallState.CALL_ENDED == callState) {
            finish()
        }
    }
}