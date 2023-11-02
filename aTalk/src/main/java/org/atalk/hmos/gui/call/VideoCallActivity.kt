/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import net.java.sip.communicator.service.gui.call.CallPeerRenderer
import net.java.sip.communicator.service.gui.call.CallRenderer
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.*
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer
import net.java.sip.communicator.util.call.CallPeerAdapter
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.hmos.gui.call.notification.CallControl
import org.atalk.hmos.gui.call.notification.CallNotificationManager
import org.atalk.hmos.gui.controller.AutoHideController
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.widgets.ClickableToastController
import org.atalk.hmos.gui.widgets.LegacyClickableToastCtrl
import org.atalk.impl.androidtray.NotificationPopupHandler
import org.atalk.impl.neomedia.device.util.CameraUtils
import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.CameraStreamBase
import org.atalk.impl.neomedia.transform.sdes.SDesControlImpl
import org.atalk.service.neomedia.*
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.MediaType
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*

/**
 * The `VideoCallActivity` corresponds the call screen.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class VideoCallActivity : OSGiActivity(), CallPeerRenderer, CallRenderer, CallChangeListener, PropertyChangeListener, ZrtpInfoDialog.SasVerificationListener, AutoHideController.AutoHideListener, View.OnClickListener, View.OnLongClickListener, VideoHandlerFragment.OnRemoteVideoChangeListener, FragmentOnAttachListener {
    /**
     * Call notification broadcast receiver for android-O
     */
    private var callNotificationControl: BroadcastReceiver? = null

    /**
     * The ZRTP SAS verification toast control panel.
     */
    private var sasToastControl: LegacyClickableToastCtrl? = null

    /**
     * Call volume control fragment instance.
     */
    private lateinit var callVolumeControl: CallVolumeCtrlFragment

    private lateinit var callTimer: CallTimerFragment

    /**
     * Auto-hide controller fragment for call control buttons. It is attached when remote video
     * covers most part of the screen.
     */
    private var autoHideControl: AutoHideController? = null

    /**
     * The call peer adapter that gives us access to all call peer events.
     */
    private var callPeerAdapter: CallPeerAdapter? = null

    /**
     * The corresponding call.
     */
    private var mCall: Call<*>? = null

    /**
     * The call identifier managed by [CallManager]
     */
    private var mCallIdentifier: String? = null

    /**
     * The [CallConference] instance depicted by this `CallPanel`.
     */
    private var callConference: CallConference? = null

    /**
     * Dialog displaying list of contacts for user selects to transfer the call to.
     */
    private var mTransferDialog: CallTransferDialog? = null

    /**
     * Flag to auto launch callTransfer dialog on resume if true
     */
    private var callTransfer = false

    private var micEnabled = false

    /**
     * Flag indicates if the shutdown Thread has been started
     */
    @Volatile
    private var finishing = false
    private lateinit var peerAvatar: ImageView
    private lateinit var microphoneButton: ImageView
    private lateinit var speakerphoneButton: ImageView
    private lateinit var padlockGroupView: View
    private lateinit var callEndReason: TextView

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.call_video_audio)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val extras = intent.extras
        if (extras != null) {
            mCallIdentifier = extras.getString(CallManager.CALL_SID)
            // End all call notifications in case any, once the call has started.
            NotificationPopupHandler.removeCallNotification(mCallIdentifier!!)
            mCall = CallManager.getActiveCall(mCallIdentifier)
            if (mCall == null) {
                Timber.e("There's no call with id: %s", mCallIdentifier)
                return
            }
            // Check to see if launching call transfer dialog on resume has been requested
            callTransfer = extras.containsKey(CallManager.CALL_TRANSFER) && extras.getBoolean(CallManager.CALL_TRANSFER)
        }
        // Registers as the call state listener
        mCall!!.addCallChangeListener(this)
        callConference = mCall!!.getConference()

        // Initialize callChat button action
        findViewById<ImageButton>(R.id.button_call_back_to_chat).setOnClickListener(this)

        // Initialize speakerphone button action
        speakerphoneButton = findViewById<ImageButton>(R.id.button_speakerphone)
        speakerphoneButton.setOnClickListener(this)
        speakerphoneButton.setOnLongClickListener(this)

        // Initialize the microphone button view.
        microphoneButton = findViewById<ImageButton>(R.id.button_call_microphone)
        microphoneButton.setOnClickListener(this)
        microphoneButton.setOnLongClickListener(this)

        micEnabled = aTalk.hasPermission(aTalk.instance, false,
            aTalk.PRC_RECORD_AUDIO, Manifest.permission.RECORD_AUDIO)

        findViewById<ImageButton>(R.id.button_call_hold).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_call_hangup).setOnClickListener(this)
        findViewById<ImageButton>(R.id.button_call_transfer).setOnClickListener(this)

        // set up clickable toastView for onSaveInstanceState in case phone rotate
        val toastView = findViewById<View>(R.id.clickable_toast)
        sasToastControl = ClickableToastController(toastView, this, R.id.clickable_toast)
        toastView.setOnClickListener(this)

        callEndReason = findViewById(R.id.callEndReason)
        callEndReason.visibility = View.GONE

        peerAvatar = findViewById(R.id.calleeAvatar)
        mBackToChat = false
        padlockGroupView = findViewById(R.id.security_group)
        padlockGroupView.setOnClickListener(this)

        val fragmentManager = supportFragmentManager
        fragmentManager.addFragmentOnAttachListener(this)

        if (savedInstanceState == null) {
            videoFragment = VideoHandlerFragment()
            callVolumeControl = CallVolumeCtrlFragment()
            callTimer = CallTimerFragment()

            /*
             * Adds a fragment that turns on and off the screen when proximity sensor detects FAR/NEAR distance.
             */
            fragmentManager.beginTransaction()
                .add(callVolumeControl, VOLUME_CTRL_TAG)
                .add(ProximitySensorFragment(), PROXIMITY_FRAGMENT_TAG)
                /* Fragment that handles video display logic */
                .add(videoFragment!!, VIDEO_FRAGMENT_TAG)
                /* Fragment that handles call duration logic */
                .add(callTimer, TIMER_FRAGMENT_TAG)
                .commit()
        }
        else {
            // Retrieve restored auto hide fragment
            autoHideControl = fragmentManager.findFragmentByTag(AUTO_HIDE_TAG) as AutoHideController
            callVolumeControl = fragmentManager.findFragmentByTag(VOLUME_CTRL_TAG) as CallVolumeCtrlFragment
            callTimer = fragmentManager.findFragmentByTag(TIMER_FRAGMENT_TAG) as CallTimerFragment
        }
    }

    override fun onAttachFragment(fragmentManager: FragmentManager, fragment: Fragment) {
        // Timber.w("onAttachFragment Tag: %s", fragment);
        if (fragment is VideoHandlerFragment) {
            fragment.setRemoteVideoChangeListener(this)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        sasToastControl?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        sasToastControl?.onRestoreInstanceState(savedInstanceState)
    }

    /**
     * Reinitialize the `Activity` to reflect current call status.
     */
    override fun onResume() {
        super.onResume()

        // Stop call broadcast receiver
        if (callNotificationControl != null) {
            aTalkApp.globalContext.unregisterReceiver(callNotificationControl)
            Timber.d("callNotificationControl unregistered: %s; %s", mCallIdentifier, callNotificationControl)
            callNotificationControl = null
        }

        // Clears the in call notification
        if (CallNotificationManager.getInstanceFor(mCallIdentifier!!).isNotificationRunning) {
            Timber.d("callNotificationControl hide notification panel: %s", mCallIdentifier)
            CallNotificationManager.getInstanceFor(mCallIdentifier!!).stopNotification()
        }

        // Call already ended or not found
        if (mCall == null)
            return

        // To take care when the phone orientation is changed while call is in progress
        if (videoFragment == null)
            videoFragment = supportFragmentManager.findFragmentByTag("video") as VideoHandlerFragment

        // Registers as the call state listener
        mCall!!.addCallChangeListener(this)

        // Checks if call peer has video component
        val peers = mCall!!.getCallPeers()
        if (peers.hasNext()) {
            val callPeer = peers.next()
            addCallPeerUI(callPeer)
        }
        else {
            if (!callState.callEnded) {
                Timber.e("There aren't any peers in the call")
                finish()
            }
            return
        }
        doUpdateHoldStatus()
        doUpdateMuteStatus()
        updateSpeakerphoneStatus()
        initSecurityStatus()
        if (callTransfer) {
            callTransfer = false
            transferCall()
        }
    }

    /**
     * Called when this `Activity` is paused(hidden). Releases all listeners and leaves the
     * in call notification if the call is in progress.
     */
    override fun onPause() {
        super.onPause()
        if (mCall == null)
            return

        mCall!!.removeCallChangeListener(this)
        if (callPeerAdapter != null) {
            val callPeerIter = mCall!!.getCallPeers()
            if (callPeerIter.hasNext()) {
                removeCallPeerUI(callPeerIter.next())
            }
            callPeerAdapter!!.dispose()
            callPeerAdapter = null
        }
        if (mCall!!.callState != CallState.CALL_ENDED) {
            mBackToChat = true
            callNotificationControl = CallControl()
            aTalkApp.globalContext.registerReceiver(callNotificationControl, IntentFilter("org.atalk.call.control"))
            leaveNotification()
            Timber.d("callNotificationControl registered: %s: %s", mCallIdentifier, callNotificationControl)
        }
        else {
            mBackToChat = false
        }
    }

    /*
     * Close the Call Transfer Dialog is shown; else close call UI
     */
    override fun onBackPressed() {
        if (mTransferDialog != null) {
            mTransferDialog!!.closeDialog()
            mTransferDialog = null
        }
        super.onBackPressed()
    }

    /**
     * Called on call ended event. Runs on separate thread to release the EDT Thread and preview
     * surface can be hidden effectively.
     */
    private fun doFinishActivity() {
        if (finishing)
            return

        finishing = true
        callNotificationControl = null

        Thread {
            // Waits for the camera to be stopped
            videoFragment?.ensureCameraClosed()

            runOnUiThread {
                // callState.callDuration = ViewUtil.getTextViewValue(findViewById(android.R.id.content), R.id.callTime)!!
                callState.callEnded = true

                // Remove video fragment
                if (videoFragment != null) {
                    supportFragmentManager.beginTransaction().remove(videoFragment!!).commit()
                }
                // Remove auto hide fragment
                ensureAutoHideFragmentDetached()
                // !!! below is not working in kotlin code; merged with this activity
                // supportFragmentManager.beginTransaction().replace(android.R.id.content, CallEnded()).commit()

                // auto exit 3 seconds after call ended
                // !!! below is not working in kotlin code; merged with this activity
                // getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new CallEnded()).commit();

                // auto exit 3 seconds after call ended
                Handler().postDelayed({ finish() }, 3000)
            }
        }.start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            onRemoteVideoChange(videoFragment != null && videoFragment!!.isRemoteVideoVisible())
        }
    }

    override fun onRemoteVideoChange(isRemoteVideoVisible: Boolean) {
        if (isRemoteVideoVisible) hideSystemUI()
        else showSystemUI()
    }

    private fun hideSystemUI() {
        // Enables regular immersive mode.
        val decorView = window.decorView
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    // Restore the system bars by removing all the flags. On end call,
    // do not request for full screen nor hide navigation bar, let user selected navigation state take control.
    private fun showSystemUI() {
        val decorView = window.decorView
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    /**
     * Handle buttons action events- the `ActionEvent` that notified us
     */
    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_call_back_to_chat -> finish()

            R.id.button_speakerphone -> {
                val audioManager = aTalkApp.audioManager
                audioManager.isSpeakerphoneOn = !audioManager.isSpeakerphoneOn
                updateSpeakerphoneStatus()
            }

            R.id.button_call_microphone ->
                if (micEnabled)
                    CallManager.setMute(mCall, !isMuted())

            // call == null if call setup failed
            R.id.button_call_hold ->
                if (mCall != null)
                    CallManager.putOnHold(mCall!!, !isOnHold())

            // call == null if call setup failed
            R.id.button_call_transfer ->
                if (mCall != null) transferCall()

            // Start the hang up Thread, Activity will be closed later on call ended event
            R.id.button_call_hangup ->
                if (mCall == null || CallState.CALL_ENDED == mCall!!.callState) {
                    finish()
                }
                else {
                    CallManager.hangupCall(mCall)
                    setErrorReason(callState.errorReason)
                }

            R.id.security_group -> showZrtpInfoDialog()
            R.id.clickable_toast -> {
                showZrtpInfoDialog()
                sasToastControl!!.hideToast(true)
            }
        }
    }

    /**
     * Handle buttons longPress action events - the `ActionEvent` that notified us
     */
    override fun onLongClick(v: View): Boolean {
        val newFragment: DialogFragment
        when (v.id) {
            R.id.button_speakerphone -> {
                // Create and show the dialog.
                newFragment = VolumeControlDialog.createOutputVolCtrlDialog()
                newFragment.show(supportFragmentManager, "vol_ctrl_dialog")
                return true
            }
            R.id.button_call_microphone -> {
                // Create and show the mic gain control dialog.

                if (micEnabled) {
                    newFragment = VolumeControlDialog.createInputVolCtrlDialog()
                    newFragment.show(supportFragmentManager, "vol_ctrl_dialog")
                }
                return true
            }
        }
        return false
    }

    /**
     * Transfers the given <tt>callPeer</tt>.
     */
    private fun transferCall() {
        // If the telephony operation set is null we have nothing more to do here.
        mCall!!.pps.getOperationSet(OperationSetAdvancedTelephony::class.java)
                ?: return

        // We support transfer for one-to-one calls only. next() => NoSuchElementException
        try {
            val initialPeer = mCall!!.getCallPeers().next()
            val transferCalls = getTransferCallPeers()
            mTransferDialog = CallTransferDialog(this, initialPeer, transferCalls)
            mTransferDialog!!.show()
        } catch (e: NoSuchElementException) {
            Timber.w("Transferring call: %s", e.message)
        }
    }

    /**
     * Returns the list of transfer call peers.
     *
     * @return the list of transfer call peers
     */
    private fun getTransferCallPeers(): Collection<CallPeer> {
        val transferCalls = LinkedList<CallPeer>()
        for (activeCall in CallManager.getInProgressCalls()) {
            // We're only interested in one to one calls
            if (activeCall != mCall && activeCall!!.callPeerCount == 1) {
                transferCalls.add(activeCall.getCallPeers().next())
            }
        }
        return transferCalls
    }

    /**
     * Updates speakerphone button status.
     */
    private fun updateSpeakerphoneStatus() {
        if (aTalkApp.audioManager.isSpeakerphoneOn) {
            speakerphoneButton.setImageResource(R.drawable.call_speakerphone_on_dark)
            speakerphoneButton.setBackgroundColor(0x50000000)
        }
        else {
            speakerphoneButton.setImageResource(R.drawable.call_receiver_on_dark)
            speakerphoneButton.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * Returns `true` if call is currently muted.
     *
     * @return `true` if call is currently muted.
     */
    private fun isMuted(): Boolean {
        return CallManager.isMute(mCall)
    }

    private fun updateMuteStatus() {
        runOnUiThread { doUpdateMuteStatus() }
    }

    private fun doUpdateMuteStatus() {
        if (!micEnabled || isMuted()) {
            microphoneButton.setImageResource(R.drawable.call_microphone_mute_dark)
            microphoneButton.setBackgroundColor(0x50000000)
        }
        else {
            microphoneButton.setImageResource(R.drawable.call_microphone_dark)
            microphoneButton.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        /*
         * The call to: setVolumeControlStream(AudioManager.STREAM_VOICE_CALL) doesn't work when
         * notification was being played during this Activity creation, so the buttons must be
         * captured, and the voice call level will be manipulated programmatically.
         */
        val action = event.action
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_UP) {
                    callVolumeControl.onKeyVolUp()
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    callVolumeControl.onKeyVolDown()
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    /**
     * Leaves the in call notification.
     */
    private fun leaveNotification() {
        CallNotificationManager.getInstanceFor(mCallIdentifier!!).showCallNotification(this)
    }

    /**
     * Sets the peer name.
     *
     * @param name the name of the call peer
     */
    override fun setPeerName(name: String?) {
        runOnUiThread {
            ActionBarUtil.setTitle(this@VideoCallActivity,
                getString(R.string.service_gui_CALL_WITH) + ": ")
            ActionBarUtil.setSubtitle(this@VideoCallActivity, name)
        }
    }

    /**
     * Sets the peer image.
     *
     * @param image the avatar of the call peer
     */
    override fun setPeerImage(image: ByteArray?) {
        if (image != null) {
            peerAvatar.setImageBitmap(AndroidImageUtil.bitmapFromBytes(image))
        }
    }

    /**
     * Sets the peer state.
     *
     * @param oldState the old peer state
     * @param newState the new peer state
     * @param stateString the state of the call peer
     */
    override fun setPeerState(oldState: CallPeerState?, newState: CallPeerState?, stateString: String?) {
        runOnUiThread {
            val statusName = findViewById<TextView>(R.id.callStatus)
            statusName.text = stateString
        }
    }

    /**
     * Ensures that auto hide fragment is added and started.
     */
    fun ensureAutoHideFragmentAttached() {
        if (autoHideControl != null)
            return

        autoHideControl = AutoHideController.getInstance(R.id.button_Container, AUTO_HIDE_DELAY)
        supportFragmentManager.beginTransaction().add(autoHideControl!!, AUTO_HIDE_TAG).commit()
    }

    /**
     * Removes the auto hide fragment, so that call control buttons will be always visible from now on.
     */
    fun ensureAutoHideFragmentDetached() {
        if (autoHideControl != null) {
            autoHideControl!!.show()

            supportFragmentManager.beginTransaction().remove(autoHideControl!!).commit()
            autoHideControl = null
        }
    }

    /**
     * Shows (or cancels) the auto hide fragment.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        autoHideControl?.show()
    }

    /**
     * Returns `CallVolumeCtrlFragment` if it exists or `null` otherwise.
     *
     * @return `CallVolumeCtrlFragment` if it exists or `null` otherwise.
     */
    fun getVolCtrlFragment(): CallVolumeCtrlFragment {
        return callVolumeControl
    }

    override fun setErrorReason(reason: String) {
        Timber.i("End call reason: %s", reason)
        runOnUiThread {
            callState.errorReason = reason
            callEndReason.text = reason
            callEndReason.visibility = View.VISIBLE
        }
    }

    override fun setMute(mute: Boolean) {
        // Just invoke mute UI refresh
        updateMuteStatus()
    }

    private fun isOnHold(): Boolean {
        var onHold = false
        val callPeers = mCall!!.getCallPeers()
        if (callPeers.hasNext()) {
            val peerState = callPeers.next().getState()
            onHold = CallPeerState.ON_HOLD_LOCALLY == peerState
                    || CallPeerState.ON_HOLD_MUTUALLY == peerState
        }
        else {
            Timber.w("No peer belongs to call: %s", mCall.toString())
        }
        return onHold
    }

    override fun setOnHold(onHold: Boolean) {}

    /**
     * Updates on hold button to represent it's actual state
     */
    private fun updateHoldStatus() {
        runOnUiThread { doUpdateHoldStatus() }
    }

    /**
     * Updates on hold button to represent it's actual state. Called from
     * [.updateHoldStatus].
     */
    private fun doUpdateHoldStatus() {
        val holdButton = findViewById<ImageView>(R.id.button_call_hold)
        if (isOnHold()) {
            holdButton.setImageResource(R.drawable.call_hold_on_dark)
            holdButton.setBackgroundColor(0x50000000)
        }
        else {
            holdButton.setImageResource(R.drawable.call_hold_off_dark)
            holdButton.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun printDTMFTone(dtmfChar: Char) {}

    override fun getCallRenderer(): CallRenderer? {
        return this
    }

    override fun getCallPeerRenderer(callPeer: CallPeer): CallPeerRenderer? {
        return this
    }

    fun setLocalVideoVisible(isVisible: Boolean) {
        // It cannot be hidden here, because the preview surface will be destroyed and camera
        // recording system will crash
    }

    override fun isLocalVideoVisible(): Boolean {
        return videoFragment!!.isLocalVideoVisible()
    }

    fun getCall(): Call<*>? {
        return mCall
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.video_call_menu, menu)

        // Add subMenu items for all supported resolutions
        val mSubMenuRes = menu.findItem(R.id.video_resolution).subMenu
        for (res in CameraUtils.PREFERRED_SIZES) {
            val sResolution = res.getWidth().toInt().toString() + "x" + res.getHeight().toInt()
            mSubMenuRes!!.addSubMenu(0, R.id.video_dimension, Menu.NONE, sResolution)
        }

        // cmeng - hide menu item - not implemented
        val mMenuRes = menu.findItem(R.id.video_resolution)
        mMenuRes.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.video_dimension -> {
                aTalkApp.showToastMessage("Not implemented!")
                true
            }
            R.id.call_info_item -> {
                showCallInfoDialog()
                true
            }
            R.id.call_zrtp_info_item -> {
                showZrtpInfoDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Displays technical call information dialog.
     */
    private fun showCallInfoDialog() {
        val callInfo = CallInfoDialogFragment.newInstance(
            intent.getStringExtra(CallManager.CALL_SID))
        callInfo.show(supportFragmentManager, "callinfo")
    }

    /**
     * Displays ZRTP call information dialog.
     */
    private fun showZrtpInfoDialog() {
        val zrtpInfo = ZrtpInfoDialog.newInstance(intent.getStringExtra(CallManager.CALL_SID))
        zrtpInfo.show(supportFragmentManager, "zrtpinfo")
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
        /*
         * If a Call is added to or removed from the CallConference depicted by this CallPanel, an
         * update of the view from its model will most likely be required.
         */
        if (CallConference.CALLS == evt.propertyName) onCallConferenceEventObject(evt)
    }

    override fun callPeerAdded(evt: CallPeerEvent) {
        val callPeer = evt.getSourceCallPeer()
        addCallPeerUI(callPeer)
        onCallConferenceEventObject(evt)
    }

    override fun callPeerRemoved(evt: CallPeerEvent) {
        val callPeer = evt.getSourceCallPeer()
        if (callPeerAdapter != null) {
            callPeer.addCallPeerListener(callPeerAdapter)
            callPeer.addCallPeerSecurityListener(callPeerAdapter)
            callPeer.addPropertyChangeListener(callPeerAdapter!!)
        }
        setPeerState(callPeer.getState(), callPeer.getState(), callPeer.getState()!!.getLocalizedStateString())
        onCallConferenceEventObject(evt)
    }

    override fun callStateChanged(evt: CallChangeEvent) {
        onCallConferenceEventObject(evt)
    }

    /**
     * Invoked by [CallChangeListener] to notify this instance about an `EventObject`
     * related to the `CallConference` depicted by this `CallPanel`, the
     * `Call`s participating in it, the `CallPeer`s associated with them, the
     * `ConferenceMember`s participating in any telephony conferences organized by them,
     * etc. In other words, notifies this instance about any change which may cause an update to
     * be required so that this view i.e. `CallPanel` depicts the current state of its
     * model i.e. [.callConference].
     *
     * @param ev the `EventObject` this instance is being notified about.
     */
    private fun onCallConferenceEventObject(ev: EventObject) {
        /*
         * The main task is to invoke updateViewFromModel() in order to make sure that this view
         * depicts the current state of its model.
         */
        try {
            /*
             * However, we seem to be keeping track of the duration of the call (i.e. the
             * telephony conference) in the user interface. Stop the Timer which ticks the
             * duration of the call as soon as the telephony conference depicted by this instance
             * appears to have ended. The situation will very likely occur when a Call is
             * removed from the telephony conference or a CallPeer is removed from a Call.
             */
            var tryStopCallTimer = false
            if (ev is CallPeerEvent) {
                tryStopCallTimer = CallPeerEvent.CALL_PEER_REMOVED == ev.getEventID()
            }
            else if (ev is PropertyChangeEvent) {
                tryStopCallTimer = CallConference.CALLS == ev.propertyName && ev.oldValue is Call<*> && ev.newValue == null
            }
            if (tryStopCallTimer && (callConference!!.isEnded || callConference!!.callPeerCount == 0)) {
                stopCallTimer()
                doFinishActivity()
            }
        } finally {
            updateViewFromModel(ev)
        }
    }

    /**
     * Starts the timer that counts call duration.
     */
    override fun startCallTimer() {
        callTimer.startCallTimer()
    }

    /**
     * Stops the timer that counts call duration.
     */
    override fun stopCallTimer() {
        callTimer.stopCallTimer()
    }

    /**
     * Returns `true` if the call timer has been started, otherwise returns `false`.
     *
     * @return `true` if the call timer has been started, otherwise returns `false`
     */
    override fun isCallTimerStarted(): Boolean {
        return callTimer.isCallTimerStarted()
    }

    private fun addCallPeerUI(callPeer: CallPeer) {
        callPeerAdapter = CallPeerAdapter(callPeer, this)
        callPeer.addCallPeerListener(callPeerAdapter)
        callPeer.addCallPeerSecurityListener(callPeerAdapter)
        callPeer.addPropertyChangeListener(callPeerAdapter!!)
        setPeerState(null, callPeer.getState(), callPeer.getState()!!.getLocalizedStateString())
        setPeerName(callPeer.getDisplayName())
        setPeerImage(CallUIUtils.getCalleeAvatar(mCall))
        callTimer.callPeerAdded(callPeer)

        // set for use by CallEnded
        callState.callPeer = callPeer.getPeerJid()
    }

    /**
     * Removes given `callPeer` from UI.
     *
     * @param callPeer the [CallPeer] to be removed from UI.
     */
    private fun removeCallPeerUI(callPeer: CallPeer) {
        callPeer.removeCallPeerListener(callPeerAdapter)
        callPeer.removeCallPeerSecurityListener(callPeerAdapter)
        callPeer.removePropertyChangeListener(callPeerAdapter!!)
    }

    private fun updateViewFromModel(ev: EventObject) {}
    override fun updateHoldButtonState() {
        updateHoldStatus()
    }

    override fun dispose() {}

    override fun securityNegotiationStarted(securityStartedEvent: CallPeerSecurityNegotiationStartedEvent) {}

    /**
     * Initializes current security status displays.
     */
    private fun initSecurityStatus() {
        var isSecure = false
        var isVerified = false
        val zrtpCtrl: ZrtpControl
        var srtpControlType = SrtpControlType.NULL
        val callPeers = mCall!!.getCallPeers()
        if (callPeers.hasNext()) {
            val cpCandidate = callPeers.next()
            if (cpCandidate is MediaAwareCallPeer<*, *, *>) {
                val mediaAwarePeer = cpCandidate
                val srtpCtrl = mediaAwarePeer.mediaHandler!!.getEncryptionMethod(MediaType.AUDIO)
                isSecure = srtpCtrl != null && srtpCtrl.secureCommunicationStatus
                when (srtpCtrl) {
                    is ZrtpControl -> {
                        srtpControlType = SrtpControlType.ZRTP
                        zrtpCtrl = srtpCtrl
                        isVerified = zrtpCtrl.isSecurityVerified
                    }
                    is SDesControl -> {
                        srtpControlType = SrtpControlType.SDES
                        isVerified = true
                    }
                    is DtlsControl -> {
                        srtpControlType = SrtpControlType.DTLS_SRTP
                        isVerified = true
                    }
                }
            }
        }

        // Update padLock status and protocol name label (only if in secure mode)
        doUpdatePadlockStatus(isSecure, isVerified)
        if (isSecure) {
            ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.security_protocol,
                srtpControlType.toString())
        }
    }

    /**
     * Updates padlock status text, icon and it's background color.
     *
     * @param isSecure `true` if the call is secured.
     * @param isVerified `true` if zrtp SAS string is verified.
     */
    @SuppressLint("ResourceAsColor")
    private fun doUpdatePadlockStatus(isSecure: Boolean, isVerified: Boolean) {
        if (isSecure) {
            when {
                isVerified -> {
                    // Security on
                    setPadlockColor(R.color.padlock_green)
                }
                else -> {
                    // Security pending
                    setPadlockColor(R.color.padlock_orange)
                }
            }
            setPadlockSecure(true)
        }
        else {
            // Security off
            setPadlockColor(R.color.padlock_red)
            setPadlockSecure(false)
        }
    }

    /**
     * Sets the security padlock background color.
     *
     * @param colorId the color resource id that will be used.
     */
    private fun setPadlockColor(colorId: Int) {
        padlockGroupView.setOnClickListener(this)
        val color = resources.getColor(colorId, null)
        padlockGroupView.setBackgroundColor(color)
    }

    /**
     * Updates padlock icon based on security status.
     *
     * @param isSecure `true` if the call is secure.
     */
    private fun setPadlockSecure(isSecure: Boolean) {
        ViewUtil.setImageViewIcon(findViewById(android.R.id.content), R.id.security_padlock,
            if (isSecure) R.drawable.secure_on_dark else R.drawable.secure_off_dark)
    }

    /**
     * For ZRTP security
     * {@inheritDoc}
     */
    override fun onSasVerificationChanged(isVerified: Boolean) {
        doUpdatePadlockStatus(true, isVerified)
    }

    /**
     * {@inheritDoc}
     */
    override fun securityPending() {
        runOnUiThread { doUpdatePadlockStatus(isSecure = false, isVerified = false) }
    }

    /**
     * {@inheritDoc}
     */
    override fun securityTimeout(evt: CallPeerSecurityTimeoutEvent) {
        Timber.e("Security timeout: %s", evt.getSessionType())
    }

    /**
     * {@inheritDoc}
     */
    override fun setSecurityPanelVisible(visible: Boolean) {}

    /**
     * Flag for enable/disable DTMF handling
     */
    override var isDtmfToneEnabled = false

    /**
     * {@inheritDoc}
     */
    override fun securityOff(evt: CallPeerSecurityOffEvent) {
        runOnUiThread { doUpdatePadlockStatus(isSecure = false, isVerified = false) }
    }

    /**
     * {@inheritDoc}
     */
    override fun securityOn(evt: CallPeerSecurityOnEvent) {
        val srtpControlType: SrtpControlType
        val isVerified: Boolean
        when (val srtpCtrl = evt.getSecurityController()) {
            is ZrtpControl -> {
                srtpControlType = SrtpControlType.ZRTP
                isVerified = srtpCtrl.isSecurityVerified
                if (!isVerified) {
                    val toastMsg = getString(R.string.service_gui_security_VERIFY_TOAST)
                    sasToastControl!!.showToast(false, toastMsg)
                }
            }
            is SDesControlImpl -> {
                srtpControlType = SrtpControlType.SDES
                isVerified = true
            }
            is DtlsControl -> {
                srtpControlType = SrtpControlType.DTLS_SRTP
                isVerified = true
            }
            else -> {
                isVerified = false
                srtpControlType = SrtpControlType.NULL
            }
        }

        // Timber.d("SRTP Secure: %s = %s", isVerified, srtpControlType.toString());
        runOnUiThread {
            // Update both secure padLock status and protocol name
            doUpdatePadlockStatus(true, isVerified)
            ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.security_protocol,
                srtpControlType.toString())
        }
    }

    /**
     * Updates view alignment which depend on call control buttons group visibility state.
     * {@inheritDoc}
     */
    override fun onAutoHideStateChanged(source: AutoHideController, visibility: Int) {
        // NPE from field report
        videoFragment?.updateCallInfoMargin()
    }

    fun isBackToChat(): Boolean {
        return mBackToChat
    }

    class CallStateHolder {
        var callPeer: Jid? = null
        var callDuration = ""
        var errorReason = ""
        var callEnded = false
    }

    /*
     * This method requires the encoder to support auto-detect remote video size change.
     * App handling of device rotation during video call to:
     * a. Perform camera rotation for swap & flip, for properly video data transformation before sending
     * b. Update camera setDisplayOrientation(rotation)
     *
     * Note: If setRequestedOrientation() in the onCreate() cycle; this method will never get call even
     * it is defined in manifest android:configChanges="orientation|screenSize|screenLayout"
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mCall!!.callState != CallState.CALL_ENDED) {
            // Must update aTalkApp isPortrait before calling; found to have race condition
            aTalkApp.isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
            videoFragment!!.initVideoViewOnRotation()

            val instance = CameraStreamBase.getInstance()
            instance?.initPreviewOnRotation(true)
        }
    }

    companion object {
        /**
         * Tag name for the fragment that handles proximity sensor in order to turn the screen on and off.
         */
        private const val PROXIMITY_FRAGMENT_TAG = "proximity"

        /**
         * Tag name that identifies video handler fragment.
         */
        private const val VIDEO_FRAGMENT_TAG = "video"

        /**
         * Tag name that identifies call timer fragment.
         */
        private const val TIMER_FRAGMENT_TAG = "call_timer"

        /**
         * Tag name that identifies call control buttons auto hide controller fragment.
         */
        private const val AUTO_HIDE_TAG = "auto_hide"

        /**
         * Tag for call volume control fragment.
         */
        private const val VOLUME_CTRL_TAG = "call_volume_ctrl"

        /**
         * The delay for hiding the call control buttons, after the call has started
         */
        private const val AUTO_HIDE_DELAY = 5000L

        /**
         * Instance holds call state to be displayed in `CallEnded` fragment. Call objects will
         * be no longer available after the call has ended.
         */
        var callState = CallStateHolder()

        private var videoFragment: VideoHandlerFragment? = null

        /**
         * Indicates that the user has temporary back to chat window to send chat messages
         */
        private var mBackToChat = false

        /**
         * Creates new video call intent for given `callIdentifier`.
         *
         * @param parent the parent `Context` that will be used to start new `Activity`.
         * @param callIdentifier the call ID managed by [CallManager].
         *
         * @return new video call `Intent` parametrized with given `callIdentifier`.
         */
        fun createVideoCallIntent(parent: Context?, callIdentifier: String?): Intent {
            // Timber.d(new Exception("createVideoCallIntent: " + parent.getPackageName()));
            val videoCallIntent = Intent(parent, VideoCallActivity::class.java)
            videoCallIntent.putExtra(CallManager.CALL_SID, callIdentifier)
            return videoCallIntent
        }

        fun getVideoFragment(): VideoHandlerFragment {
            return videoFragment!!
        }

        fun setBackToChat(state: Boolean) {
            mBackToChat = state
        }
    }
}