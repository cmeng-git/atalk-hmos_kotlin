/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp

import org.atalk.impl.neomedia.jmfext.media.rtp.RTPSessionMgr
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RTPTranslator
import org.atalk.service.neomedia.SSRCFactory
import timber.log.Timber
import java.io.IOException
import java.util.*
import javax.media.Format
import javax.media.format.UnsupportedFormatException
import javax.media.protocol.DataSource
import javax.media.rtp.GlobalReceptionStats
import javax.media.rtp.GlobalTransmissionStats
import javax.media.rtp.RTPConnector
import javax.media.rtp.RTPManager
import javax.media.rtp.ReceiveStreamListener
import javax.media.rtp.RemoteListener
import javax.media.rtp.SendStream
import javax.media.rtp.SendStreamListener
import javax.media.rtp.SessionListener

/**
 * Implements the `RTPManager` interface as used by a `MediaStream`.
 * The media steam is either handled via rtpManager or rtpTranslator (encrypted); cannot have both null
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class StreamRTPManager(stream: MediaStream, translator: RTPTranslator?) {
    /**
     * The `MediaStream` that uses this `StreamRTPManager`
     */
    private val stream: MediaStream

    /**
     * The `RTPManager` this instance is to delegate to when it is not attached to an `RTPTranslator`.
     */
    private val rtpManager: RTPManager?

    /**
     * The `RTPTranslator` which this instance is attached to and which forwards the RTP and
     * RTCP flows of the `MediaStream` associated with this instance to other `MediaStream`s.
     */
    private val rtpTranslator: RTPTranslatorImpl?

    /**
     * Initializes a new `StreamRTPManager` instance which is, optionally,
     * attached to a specific `RTPTranslator` which is to forward the RTP and
     * RTCP flows of the associated `MediaStream` to other `MediaStream`s.
     *
     * stream the `MediaStream` that created this `StreamRTPManager`.
     * translator the `RTPTranslator` to attach the new instance to or `null`
     * if the new instance is to not be attached to any `RTPTranslator`
     */
    init {
        this.stream = stream
        rtpTranslator = translator as RTPTranslatorImpl?
        rtpManager = if (rtpTranslator == null) RTPManager.newInstance() else null
    }

    fun addFormat(format: Format?, payloadType: Int) {
        if (rtpTranslator == null) {
            rtpManager!!.addFormat(format, payloadType)
        }
        else {
            rtpTranslator.addFormat(this, format!!, payloadType)
        }
    }

    fun addReceiveStreamListener(listener: ReceiveStreamListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.addReceiveStreamListener(listener)
        }
        else {
            rtpTranslator.addReceiveStreamListener(this, listener)
        }
    }

    fun addRemoteListener(listener: RemoteListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.addRemoteListener(listener)
        }
        else {
            rtpTranslator.addRemoteListener(this, listener)
        }
    }

    fun addSendStreamListener(listener: SendStreamListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.addSendStreamListener(listener)
        }
        else {
            rtpTranslator.addSendStreamListener(this, listener)
        }
    }

    fun addSessionListener(listener: SessionListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.addSessionListener(listener)
        }
        else {
            rtpTranslator.addSessionListener(this, listener)
        }
    }

    @Throws(IOException::class, UnsupportedFormatException::class, NullPointerException::class)
    fun createSendStream(dataSource: DataSource?, streamIndex: Int): SendStream {
        // NullPointerException: Attempt to invoke virtual method 'SessionAddress.getDataPort()' on a null object reference.
        // Happen when call is terminated in middle of setting up.
        return if (rtpTranslator == null) {
            rtpManager!!.createSendStream(dataSource, streamIndex)
        }
        else {
            rtpTranslator.createSendStream(this, dataSource!!, streamIndex)!!
        }
    }

    fun dispose() {
        Timber.d("Stream RTP Manager disposing: x = %s; m = %s", rtpTranslator, rtpManager)
        if (rtpTranslator == null) rtpManager!!.dispose() else rtpTranslator.dispose(this)
    }

    /**
     * Gets a control of a specific type over this instance. Invokes [.getControl].
     *
     * @param controlType a `Class` which specifies the type of the control over this instance to get
     * @return a control of the specified `controlType` over this instance
     * if this instance supports such a control; otherwise, `null`
     */
    fun <T> getControl(controlType: Class<T>): T {
        return getControl(controlType.name) as T
    }

    /**
     * Gets a control of a specific type over this instance.
     *
     * @param controlType a `String` which specifies the type (i.e. the name of the class)
     * of the control over this instance to get
     * @return a control of the specified `controlType` over this instance if this instance
     * supports such a control; otherwise, `null`
     */
    fun getControl(controlType: String?): Any {
        return rtpTranslator?.getControl(this, controlType) ?: rtpManager!!.getControl(controlType)
    }

    val globalReceptionStats: GlobalReceptionStats
        get() = rtpTranslator?.getGlobalReceptionStats(this) ?: rtpManager!!.globalReceptionStats

    val globalTransmissionStats: GlobalTransmissionStats
        get() = rtpTranslator?.getGlobalTransmissionStats(this) ?: rtpManager!!.globalTransmissionStats

    val localSSRC: Long
        get() = rtpTranslator?.getLocalSSRC(this) ?: (rtpManager as RTPSessionMgr?)!!.localSSRC

    /**
     * Returns the `MediaStream` that uses this `StreamRTPManager`
     *
     * @return the `MediaStream` that uses this `StreamRTPManager`
     */
    val mediaStream: MediaStream
        get() = stream

    val receiveStreams: Vector<*>
        get() = if (rtpTranslator == null) {
            rtpManager!!.receiveStreams
        }
        else
            rtpTranslator.getReceiveStreams(this) as Vector<*>

    val sendStreams: Vector<*>
        get() = if (rtpTranslator == null) {
            rtpManager!!.sendStreams
        }
        else {
            rtpTranslator.getSendStreams(this) as Vector<*>
        }

    fun initialize(connector: RTPConnector?) {
        if (rtpTranslator == null) {
            rtpManager!!.initialize(connector)
        }
        else {
            rtpTranslator.initialize(this, connector)
        }
    }

    fun removeReceiveStreamListener(listener: ReceiveStreamListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.removeReceiveStreamListener(listener)
        }
        else {
            rtpTranslator.removeReceiveStreamListener(this, listener)
        }
    }

    fun removeRemoteListener(listener: RemoteListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.removeRemoteListener(listener)
        }
        else {
            rtpTranslator.removeRemoteListener(this, listener)
        }
    }

    fun removeSendStreamListener(listener: SendStreamListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.removeSendStreamListener(listener)
        }
        else {
            rtpTranslator.removeSendStreamListener(this, listener)
        }
    }

    fun removeSessionListener(listener: SessionListener?) {
        if (rtpTranslator == null) {
            rtpManager!!.removeSessionListener(listener)
        }
        else {
            rtpTranslator.removeSessionListener(this, listener)
        }
    }

    /**
     * Sets the `SSRCFactory` to be utilized by this instance to generate new synchronization
     * source (SSRC) identifiers.
     *
     * @param ssrcFactory the `SSRCFactory` to be utilized by this instance to generate new
     * synchronization source (SSRC) identifiers or `null` if this instance is to
     * employ internal logic to generate new synchronization source (SSRC) identifiers
     */
    fun setSSRCFactory(ssrcFactory: SSRCFactory?) {
        if (rtpTranslator == null) {
            val m = rtpManager
            if (m is RTPSessionMgr) {
                val sm = m
                sm.sSRCFactory = ssrcFactory
            }
        }
        else {
            rtpTranslator.setSSRCFactory(ssrcFactory)
        }
    }
}