/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.widget.TextView
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.CallPeerState
import net.java.sip.communicator.util.GuiUtils
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiFragment
import timber.log.Timber
import java.util.*

/**
 * Fragment implements the logic responsible for updating call duration timer. It is expected that parent
 * `Activity` contains `TextView` with `R.id.callTime` ID.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallTimerFragment : OSGiFragment() {
    /**
     * Indicates if the call timer has been started.
     */
    private var isCallTimerStarted = false

    /**
     * The start date time of the call.
     */
    private var callStartDate: Date? = null

    /**
     * A timer to count call duration.
     */
    private val callDurationTimer = Timer()

    /**
     * Must be called in order to initialize and start the timer.
     *
     * @param callPeer the `CallPeer` for which we're tracking the call duration.
     */
    fun callPeerAdded(callPeer: CallPeer) {
        val currentState = callPeer.getState()
        if ((currentState === CallPeerState.CONNECTED || CallPeerState.isOnHold(currentState)) && !isCallTimerStarted()) {
            callStartDate = Date(callPeer.getCallDurationStartTime())
            startCallTimer()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        doUpdateCallDuration()
    }

    /**
     * Called when an activity is destroyed.
     */
    override fun onDestroy() {
        if (isCallTimerStarted()) {
            stopCallTimer()
        }
        super.onDestroy()
    }

    /**
     * Updates the call duration string. Invoked on UI thread.
     */
    fun updateCallDuration() {
        runOnUiThread { doUpdateCallDuration() }
    }

    /**
     * Updates the call duration string.
     */
    private fun doUpdateCallDuration() {
        if (callStartDate == null || activity == null)
            return

        val timeStr = GuiUtils.formatTime(callStartDate!!.time, System.currentTimeMillis())
        val callTime = activity!!.findViewById<TextView>(R.id.callTime)
        callTime.text = timeStr
        VideoCallActivity.callState.callDuration = timeStr
    }

    /**
     * Starts the timer that counts call duration.
     */
    fun startCallTimer() {
        if (callStartDate == null) {
            callStartDate = Date()
        }

        // Do not schedule if it is already started (pidgin sends 4 session-accept on user accept incoming call)
        if (!isCallTimerStarted) {
            try {
                callDurationTimer.schedule(CallTimerTask(), Date(System.currentTimeMillis()), 1000)
                isCallTimerStarted = true
            } catch (e: IllegalStateException) {  // Timer already canceled.
                Timber.w("Start call timber error: %s", e.message)
            }
        }
    }

    /**
     * Stops the timer that counts call duration.
     */
    fun stopCallTimer() {
        callDurationTimer.cancel()
    }

    /**
     * Returns `true</code> if the call timer has been started, otherwise returns <code>false`.
     *
     * @return `true</code> if the call timer has been started, otherwise returns <code>false`
     */
    fun isCallTimerStarted(): Boolean {
        return isCallTimerStarted
    }

    /**
     * Each second refreshes the time label to show to the user the exact duration of the call.
     */
    private inner class CallTimerTask : TimerTask() {
        override fun run() {
            updateCallDuration()
        }
    }
}