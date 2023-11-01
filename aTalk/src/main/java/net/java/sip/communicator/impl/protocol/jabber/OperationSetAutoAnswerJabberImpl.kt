/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.AbstractOperationSetBasicAutoAnswer
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.OperationSetBasicAutoAnswer
import org.atalk.hmos.gui.call.JingleMessageSessionImpl
import org.atalk.service.neomedia.MediaDirection
import org.atalk.util.MediaType
import org.jivesoftware.smackx.jingle.element.Jingle
import timber.log.Timber

/**
 * An Operation Set defining option to unconditionally auto answer incoming calls.
 *
 * @author Damian Minkov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
class OperationSetAutoAnswerJabberImpl(protocolProvider: ProtocolProviderServiceJabberImpl?) : AbstractOperationSetBasicAutoAnswer(protocolProvider!!) {
    /**
     * Creates this operation set, loads stored values, populating local variable settings.
     *
     * protocolProvider the parent Protocol Provider.
     */
    init {
        load()
    }

    /**
     * Save values to account properties.
     */
    override fun save() {
        val acc = mPPS.accountID
        val accProps = acc.accountProperties

        // let's clear anything before saving :)
        accProps[OperationSetBasicAutoAnswer.AUTO_ANSWER_UNCOND_PROP] = false.toString()
        if (mAnswerUnconditional) accProps[OperationSetBasicAutoAnswer.AUTO_ANSWER_UNCOND_PROP] = true.toString()
        accProps[OperationSetBasicAutoAnswer.AUTO_ANSWER_WITH_VIDEO_PROP] = mAnswerWithVideo.toString()
        acc.setAccountProperties(accProps)
        JabberActivator.protocolProviderFactory.storeAccount(acc)
    }

    /**
     * Checks if the call satisfy the auto answer conditions.
     *
     * call The new incoming call to auto-answer if needed.
     * @return `true` if the call satisfy the auto answer conditions. `False` otherwise.
     */
    override fun satisfyAutoAnswerConditions(call: Call<*>): Boolean {
        // The jabber implementation does not support advanced auto answer functionality.
        // We only need to check if the specific Call object knows it has to be auto-answered.
        return call.isAutoAnswer
    }

    /**
     * Auto answer to a call with "audio only" or "audio/video" if the incoming call is a video call.
     *
     * call The new incoming call to auto-answer if needed.
     * directions The media type (audio / video) stream directions.
     * jingleSessionInit Jingle session-initiate is used to check if incoming call is via JingleMessage accept
     * @return `true` if we have processed and no further processing is needed, `false` otherwise.
     */
    fun autoAnswer(call: Call<*>, directions: Map<MediaType, MediaDirection>, jingleSessionInit: Jingle?): Boolean {
        // 2022/11/29 (v3.0.5): Allow proceed to auto-answer the call if it is already accepted in JingleMessageSessionImpl
        // JingleMessage <propose/> will call via ReceivedCallActivity UI when android is in locked screen;
        // Check aTalkApp.isForeground for incoming alert to continue.
//        answerOnJingleMessageAccept = aTalkApp.isForeground && (jingleSessionInit != null)
//                && JingleMessageSessionImpl.isJingleMessageAccept(jingleSessionInit);
        answerOnJingleMessageAccept = (jingleSessionInit != null
                && JingleMessageSessionImpl.isJingleMessageAccept(jingleSessionInit))
        Timber.d("OnJingleMessageAccept (auto answer): %s", answerOnJingleMessageAccept)
        var isVideoCall = false
        val direction = directions[MediaType.VIDEO]
        if (direction != null) {
            isVideoCall = direction === MediaDirection.SENDRECV
        }
        return super.autoAnswer(call, isVideoCall)
    }
}