/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallPeerAdapter
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent
import timber.log.Timber

/**
 * An Abstract Operation Set defining option to unconditionally auto answer incoming calls.
 *
 * @author Damian Minkov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
abstract class AbstractOperationSetBasicAutoAnswer
/**
 * Creates this operation set, loads stored values, populating local variable settings.
 *
 * protocolProvider the parent Protocol Provider.
 */
(
        /**
         * The parent Protocol Provider.
         */
        protected val mPPS: ProtocolProviderService) : OperationSetBasicAutoAnswer {
    /**
     * Should we unconditionally answer.
     */
    protected var answerOnJingleMessageAccept = false

    /**
     * Should we unconditionally answer.
     */
    protected var mAnswerUnconditional = false

    /**
     * Should we answer video calls with video.
     */
    protected var mAnswerWithVideo = false

    /**
     * Load values from account properties.
     */
    protected fun load() {
        val acc = mPPS.accountID
        mAnswerUnconditional = acc.getAccountPropertyBoolean(OperationSetBasicAutoAnswer.AUTO_ANSWER_UNCOND_PROP, false)
        mAnswerWithVideo = acc.getAccountPropertyBoolean(OperationSetBasicAutoAnswer.AUTO_ANSWER_WITH_VIDEO_PROP, false)
    }

    /**
     * Save values to account properties.
     */
    protected abstract fun save()

    /**
     * Clear local settings.
     */
    private fun clearLocal() {
        mAnswerUnconditional = false
    }

    /**
     * Clear any previous settings.
     */
    override fun clear() {
        clearLocal()
        mAnswerWithVideo = false
        save()
    }

    /**
     * Make a check after creating call locally, should we answer it.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @param isVideoCall Indicates if the remote peer which has created this call wish to have a video call.
     * @return `true` if we have processed and no further processing is needed, `false` otherwise.
     */
    fun autoAnswer(call: Call<*>, isVideoCall: Boolean): Boolean {
        if (answerOnJingleMessageAccept || mAnswerUnconditional || satisfyAutoAnswerConditions(call)) {
            answerCall(call, isVideoCall)
            return true
        }
        return false
    }

    /**
     * Answers call if peer in correct state or wait for it.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @param isVideoCall Indicates if the remote peer which has created this call wish to have a video call.
     */
    private fun answerCall(call: Call<*>, isVideoCall: Boolean) {
        // We are here because we satisfy the conditional, or unconditional is true.
        val peers = call.getCallPeers()
        while (peers.hasNext()) {
            AutoAnswerThread(peers.next(), isVideoCall)
        }
    }

    /**
     * Checks if the call satisfy the auto answer conditions.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @return `true` if the call satisfy the auto answer conditions. `False` otherwise.
     */
    protected abstract fun satisfyAutoAnswerConditions(call: Call<*>): Boolean

    /**
     * Sets the auto answer option to unconditionally answer all incoming calls.
     */
    override fun setAutoAnswerUnconditional() {
        clearLocal()
        mAnswerUnconditional = true
        save()
    }

    /**
     * Is the auto answer option set to unconditionally answer all incoming calls.
     *
     * @return is auto answer set to unconditional.
     */
    override fun isAutoAnswerUnconditionalSet(): Boolean {
        return mAnswerUnconditional
    }

    /**
     * Sets the auto answer with video to video calls.
     *
     * @param answerWithVideo A boolean set to true to activate the auto answer with video
     * when receiving a video call. False otherwise.
     */
    override fun setAutoAnswerWithVideo(answerWithVideo: Boolean) {
        mAnswerWithVideo = answerWithVideo
        save()
    }

    /**
     * Returns if the auto answer with video to video calls is activated.
     *
     * @return A boolean set to true if the auto answer with video when receiving a video call is
     * activated. False otherwise.
     */
    override fun isAutoAnswerWithVideoSet(): Boolean {
        return mAnswerWithVideo
    }

    /**
     * Waits for peer to switch into INCOMING_CALL state, before auto-answering the call in a new thread.
     */
    private inner class AutoAnswerThread(
            /**
             * The call peer which has generated the call.
             */
            private val peer: CallPeer?,
            /**
             * Indicates if the remote peer which has created this call wish to have a video call.
             */
            private val isVideoCall: Boolean) : CallPeerAdapter(), Runnable {
        /**
         * Wait for peer to switch into INCOMING_CALL state, before auto-answering the call in a new thread.
         *
         * peer The call peer which has generated the call.
         * isVideoCall Indicates if the remote peer which has created this call wish to have a video call.
         */
        init {
            if (peer!!.getState() === CallPeerState.INCOMING_CALL) {
                Thread(this).start()
            } else {
                peer!!.addCallPeerListener(this)
            }
        }

        /**
         * Answers the call.
         */
        override fun run() {
            val opSetBasicTelephony = mPPS.getOperationSet(OperationSetBasicTelephony::class.java)
            val opSetVideoTelephony = mPPS.getOperationSet(OperationSetVideoTelephony::class.java)
            try {
                // If user has configured to answer video call with video, then create a video call.
                // Always answer with video for incoming video call via Jingle Message accept.
                if (isVideoCall && (answerOnJingleMessageAccept || mAnswerWithVideo) && opSetVideoTelephony != null) {
                    opSetVideoTelephony.answerVideoCallPeer(peer)
                } else opSetBasicTelephony?.answerCallPeer(peer)
            } catch (e: OperationFailedException) {
                Timber.e("Failed to auto answer call from: %s; %s", peer, e.message)
            }
        }

        /**
         * If our peer was not in proper state wait for it and then answer.
         *
         * @param evt the `CallPeerChangeEvent` instance containing the
         */
        override fun peerStateChanged(evt: CallPeerChangeEvent) {
            val newState = evt.newValue as CallPeerState
            if (newState === CallPeerState.INCOMING_CALL) {
                evt.getSourceCallPeer()!!.removeCallPeerListener(this)
                Thread(this).start()
            } else if (newState === CallPeerState.DISCONNECTED || newState === CallPeerState.FAILED) {
                evt.getSourceCallPeer()!!.removeCallPeerListener(this)
            }
        }
    }
}