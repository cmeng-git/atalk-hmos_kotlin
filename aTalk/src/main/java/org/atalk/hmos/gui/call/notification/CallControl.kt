/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.CallManager
import org.atalk.hmos.gui.call.CallManager.getActiveCall
import org.atalk.hmos.gui.call.CallManager.hangupCall
import org.atalk.hmos.gui.call.CallManager.isLocallyOnHold
import org.atalk.hmos.gui.call.CallManager.isMute
import org.atalk.hmos.gui.call.CallManager.setMute
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber

/**
 * `BroadcastReceiver` that listens for [.CALL_CTRL_ACTION] action
 * and performs few basic operations(mute, hangup...) on the call.<br></br>
 * Target call must be specified by ID passed as extra argument under [.EXTRA_CALL_ID] key.
 * The IDs are managed by [CallManager].<br></br>
 * Specific operation must be passed under [.EXTRA_ACTION] key. Currently supported operations:<br></br>
 * [.ACTION_TOGGLE_SPEAKER] - toggles between speaker on / off. <br></br>
 * [.ACTION_TOGGLE_MUTE] - toggles between muted and not muted call state. <br></br>
 * [.ACTION_TOGGLE_ON_HOLD] - toggles the on hold call state.
 * [.ACTION_HANGUP] - ends the call. <br></br>
 * [.ACTION_TRANSFER_CALL] - start call transfer dialog. <br></br>
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallControl : BroadcastReceiver() {
    /**
     * {@inheritDoc}
     */
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        if (callId == null) {
            Timber.e("Extra call ID is null")
            return
        }
        val call = getActiveCall(callId)
        if (call == null) {
            Timber.e("Call with id: %s does not exists", callId)
            return
        }
        val action = intent.getIntExtra(EXTRA_ACTION, -1)
        if (action == -1) {
            Timber.e("No action supplied")
            return
        }
        Timber.d("CallNotification received action: %s (%s): %s", action, callId, call.getCallPeers().next().getAddress())
        when (action) {
            ACTION_TOGGLE_SPEAKER -> {
                Timber.log(TimberLog.FINER, "Action TOGGLE SPEAKER")
                val audioManager = aTalkApp.globalContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.isSpeakerphoneOn = !audioManager.isSpeakerphoneOn
            }
            ACTION_TOGGLE_MUTE -> {
                Timber.log(TimberLog.FINER, "Action TOGGLE MUTE")
                val isMute = isMute(call)
                setMute(call, !isMute)
            }
            ACTION_TOGGLE_ON_HOLD -> {
                Timber.log(TimberLog.FINER, "Action TOGGLE ON HOLD")
                val isOnHold = isLocallyOnHold(call)
                CallManager.putOnHold(call, !isOnHold)
            }
            ACTION_HANGUP -> {
                Timber.log(TimberLog.FINER, "Action HANGUP")
                hangupCall(call)
            }
            else -> Timber.w("No valid action supplied")
        }
    }

    companion object {
        /**
         * Call control action name
         */
        const val CALL_CTRL_ACTION = "org.atalk.call.control"

        /**
         * Extra key for callId managed by [CallManager].
         */
        const val EXTRA_CALL_ID = "call_id"

        /**
         * Extra key that identifies call action.
         */
        const val EXTRA_ACTION = "action"

        /**
         * Toggle speakerphone action value.
         */
        private const val ACTION_TOGGLE_SPEAKER = 1

        /**
         * The toggle mute action value. Toggles between muted/not muted call state.
         */
        const val ACTION_TOGGLE_MUTE = 2

        /**
         * The toggle on hold status action value.
         */
        const val ACTION_TOGGLE_ON_HOLD = 3

        /**
         * The hangup action value. Ends the call.
         */
        const val ACTION_HANGUP = 5

        /**
         * Creates the `Intent` for [.ACTION_HANGUP].
         *
         * @param callId the ID of target call.
         * @return the `Intent` for [.ACTION_HANGUP].
         */
        fun getHangupIntent(callId: String): Intent {
            return createIntent(callId, ACTION_HANGUP)
        }

        /**
         * Creates the `Intent` for [.ACTION_TOGGLE_MUTE].
         *
         * @param callId the ID of target call.
         * @return the `Intent` for [.ACTION_TOGGLE_MUTE].
         */
        fun getToggleMuteIntent(callId: String): Intent {
            return createIntent(callId, ACTION_TOGGLE_MUTE)
        }

        /**
         * Creates the `Intent` for [.ACTION_TOGGLE_ON_HOLD].
         *
         * @param callId the ID of target call.
         * @return the `Intent` for [.ACTION_TOGGLE_ON_HOLD].
         */
        fun getToggleOnHoldIntent(callId: String): Intent {
            return createIntent(callId, ACTION_TOGGLE_ON_HOLD)
        }

        /**
         * Creates the `Intent` for [.ACTION_TOGGLE_ON_HOLD].
         *
         * @param callId the ID of target call.
         * @return the `Intent` for [.ACTION_TOGGLE_ON_HOLD].
         */
        fun getToggleSpeakerIntent(callId: String): Intent {
            return createIntent(callId, ACTION_TOGGLE_SPEAKER)
        }

        /**
         * Creates new `Intent` for given call `action` value that will be performed on the
         * call identified by `callId`.
         *
         * @param callId target call ID managed by [CallManager].
         * @param action the action value that will be used.
         * @return new `Intent` for given call `action` value that will be performed on the
         * call identified by `callId`.
         */
        private fun createIntent(callId: String, action: Int): Intent {
            val intent = Intent()
            intent.action = CALL_CTRL_ACTION
            intent.putExtra(EXTRA_CALL_ID, callId)
            intent.putExtra(EXTRA_ACTION, action)
            return intent
        }
    }
}