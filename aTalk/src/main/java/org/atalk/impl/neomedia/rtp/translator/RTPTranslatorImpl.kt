/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import net.sf.fmj.media.rtp.RTCPHeader
import net.sf.fmj.media.rtp.RTPHeader
import net.sf.fmj.media.rtp.SSRCCache
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.jmfext.media.rtp.RTPSessionMgr
import org.atalk.impl.neomedia.rtp.StreamRTPManager
import org.atalk.service.neomedia.AbstractRTPTranslator
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.SSRCFactory
import org.atalk.util.RTPUtils
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.media.Buffer
import javax.media.Format
import javax.media.format.UnsupportedFormatException
import javax.media.protocol.DataSource
import javax.media.rtp.GlobalReceptionStats
import javax.media.rtp.GlobalTransmissionStats
import javax.media.rtp.RTPConnector
import javax.media.rtp.RTPManager
import javax.media.rtp.ReceiveStream
import javax.media.rtp.ReceiveStreamListener
import javax.media.rtp.RemoteListener
import javax.media.rtp.SendStream
import javax.media.rtp.SendStreamListener
import javax.media.rtp.SessionListener
import javax.media.rtp.event.ReceiveStreamEvent

/**
 * Implements `RTPTranslator` which represents an RTP translator which forwards RTP and RTCP
 * traffic between multiple `MediaStream`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class RTPTranslatorImpl : AbstractRTPTranslator(), ReceiveStreamListener {
    /**
     * The `RTPConnector` which is used by [.manager] and which delegates to the
     * `RTPConnector`s of the `StreamRTPManager`s attached to this instance.
     */
    private var connector: RTPConnectorImpl? = null

    /**
     * A local SSRC for this `RTPTranslator`. This overrides the SSRC of the
     * `RTPManager` and it does not deal with SSRC collisions. TAG(cat4-local-ssrc-hurricane).
     */
    private var localSSRC = -1L

    /**
     * The `ReadWriteLock` which synchronizes the access to and/or modification of the state
     * of this instance. Replaces `synchronized` blocks in order to reduce the number of
     * exclusive locks and, therefore, the risks of superfluous waiting.
     */
    private val _lock = ReentrantReadWriteLock()

    /**
     * The `RTPManager` which implements the actual RTP management of this instance.
     */
    private val manager = RTPManager.newInstance()
    /**
     * Gets the `RTCPFeedbackMessageSender` which should be used for sending RTCP Feedback
     * Messages from this `RTPTranslator`.
     *
     * @return the `RTCPFeedbackMessageSender` which should be used for sending RTCP
     * Feedback Messages from this `RTPTranslator`.
     */
    /**
     * An instance which can be used to send RTCP Feedback Messages, using as 'packet sender SSRC'
     * the SSRC of (the `RTPManager` of) this `RTPTranslator`.
     */
    val rtcpFeedbackMessageSender = RTCPFeedbackMessageSender(this)

    /**
     * The `SendStream`s created by the `RTPManager` and the
     * `StreamRTPManager` -specific views to them.
     */
    private val sendStreams = LinkedList<SendStreamDesc>()

    /**
     * The list of `StreamRTPManager`s i.e. `MediaStream`s which this instance
     * forwards RTP and RTCP traffic between.
     */
    private val mStreamRTPManagers = ArrayList<StreamRTPManagerDesc>()

    /**
     * Initializes a new `RTPTranslatorImpl` instance.
     */
    init {
        manager.addReceiveStreamListener(this)
    }

    /**
     * Specifies the RTP payload type (number) to be used for a specific `Format`. The
     * association between the specified `format` and the specified `payloadType` is
     * being added by a specific `StreamRTPManager` but effects the `RTPTranslatorImpl` globally.
     *
     * @param streamRTPManager the `StreamRTPManager` that is requesting the association of `format` to
     * `payloadType`
     * @param format the `Format` which is to be associated with the specified RTP payload type (number)
     * @param payloadType the RTP payload type (number) to be associated with the specified `format`
     */
    fun addFormat(streamRTPManager: StreamRTPManager, format: Format, payloadType: Int) {
        val lock = _lock.writeLock()
        lock.lock()
        val desc = try {

            // XXX RTPManager.addFormat is NOT thread-safe. It appears we have
            // decided to provide thread-safety at least on our side. Which may be
            // insufficient in all use cases but it still sounds reasonable in our current use cases.
            manager.addFormat(format, payloadType)
            getStreamRTPManagerDesc(streamRTPManager, true)
        } finally {
            lock.unlock()
        }
        // StreamRTPManager.addFormat is thread-safe.
        desc!!.addFormat(format, payloadType)
    }

    /**
     * Adds a `ReceiveStreamListener` to be notified about `ReceiveStreamEvent`s
     * related to a specific neomedia `MediaStream` (expressed as a `StreamRTPManager`
     * for the purposes of and in the terms of `RTPTranslator`). If the specified
     * `listener` has already been added, the method does nothing.
     *
     * @param streamRTPManager the `StreamRTPManager` which specifies the neomedia `MediaStream` with
     * which the `ReceiveStreamEvent`s delivered to the specified `listener`
     * are to be related. In other words, a `ReceiveStreamEvent` received by
     * `RTPTranslatorImpl` is first examined to determine which
     * `StreamRTPManager` it is related to and then it is delivered to the
     * `ReceiveStreamListener`s which have been added to this
     * `RTPTranslatorImpl` by that `StreamRTPManager`.
     * @param listener the `ReceiveStreamListener` to be notified about `ReceiveStreamEvent`s
     * related to the specified `streamRTPManager`
     */
    fun addReceiveStreamListener(streamRTPManager: StreamRTPManager, listener: ReceiveStreamListener?) {
        getStreamRTPManagerDesc(streamRTPManager, true)!!.addReceiveStreamListener(listener)
    }

    /**
     * Adds a `RemoteListener` to be notified about `RemoteEvent`s received by this
     * `RTPTranslatorImpl`. Though the request is being made by a specific
     * `StreamRTPManager`, the addition of the specified `listener` and the deliveries
     * of the `RemoteEvent`s are performed irrespective of any `StreamRTPManager`.
     *
     * @param streamRTPManager the `StreamRTPManager` which is requesting the addition of the specified
     * `RemoteListener`
     * @param listener the `RemoteListener` to be notified about `RemoteEvent`s received by
     * this `RTPTranslatorImpl`
     */
    fun addRemoteListener(streamRTPManager: StreamRTPManager?, listener: RemoteListener?) {
        manager.addRemoteListener(listener)
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    fun addSendStreamListener(streamRTPManager: StreamRTPManager?, listener: SendStreamListener?) {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    fun addSessionListener(streamRTPManager: StreamRTPManager?, listener: SessionListener?) {
        // TODO Auto-generated method stub
    }

    /**
     * Closes a specific `SendStream`.
     *
     * @param sendStreamDesc a `SendStreamDesc` instance that specifies the `SendStream` to be closed
     */
    fun closeSendStream(sendStreamDesc: SendStreamDesc) {
        // XXX Here we could potentially start with a read lock and upgrade to a write lock, if
        // the sendStreamDesc is in the sendStreams collection, but does it worth it?
        val lock = _lock.writeLock()
        lock.lock()
        try {
            if (sendStreams.contains(sendStreamDesc) && sendStreamDesc.sendStreamCount < 1) {
                val sendStream = sendStreamDesc.sendStream
                try {
                    sendStream!!.close()
                } catch (npe: NullPointerException) {
                    // Refer to MediaStreamImpl#stopSendStreams(Iterable<SendStream>, boolean) for
                    // an explanation about the swallowing of the exception.
                    Timber.e(npe, "Failed to close send stream")
                }
                sendStreams.remove(sendStreamDesc)
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Creates a `SendStream` from the stream of a specific `DataSource` that is at a
     * specific zero-based position within the array/list of streams of that `DataSource`.
     *
     * @param streamRTPManager the `StreamRTPManager` which is requesting the creation of a
     * `SendStream`. Since multiple `StreamRTPManager` may request the creation
     * of a `SendStream` from one and the same combination of `dataSource` and
     * `streamIndex`, the method may not create a completely new `SendStream`
     * but may return a `StreamRTPManager`-specific view of an existing `SendStream`.
     * @param dataSource the `DataSource` which provides the stream from which a `SendStream` is
     * to be created
     * @param streamIndex the zero-based position within the array/list of streams of the specified
     * `dataSource` of the stream from which a `SendStream` is to be created
     * @return a `SendStream` created from the specified `dataSource` and
     * `streamIndex`. The returned `SendStream` implementation is a
     * `streamRTPManager`-dedicated view to an actual `SendStream` which may
     * have been created during a previous execution of the method
     * @throws IOException if an error occurs during the execution of
     * [RTPManager.createSendStream]
     * @throws UnsupportedFormatException if an error occurs during the execution of
     * `RTPManager.createSendStream(DataSource, int)`
     */
    @Throws(IOException::class, UnsupportedFormatException::class)
    fun createSendStream(streamRTPManager: StreamRTPManager, dataSource: DataSource, streamIndex: Int): SendStream? {
        // XXX Here we could potentially start with a read lock and upgrade to a write lock, if
        // the sendStreamDesc is not in sendStreams collection, but does it worth it?
        val lock = _lock.writeLock()
        val ret: SendStream?
        lock.lock()
        try {
            var sendStreamDesc: SendStreamDesc? = null
            for (s in sendStreams) {
                if (s.dataSource === dataSource && s.streamIndex == streamIndex) {
                    sendStreamDesc = s
                    break
                }
            }
            if (sendStreamDesc == null) {
                val sendStream = manager.createSendStream(dataSource, streamIndex)
                if (sendStream != null) {
                    sendStreamDesc = SendStreamDesc(this, dataSource, streamIndex, sendStream)
                    sendStreams.add(sendStreamDesc)
                }
            }
            ret = sendStreamDesc?.getSendStream(streamRTPManager, true)
        } finally {
            lock.unlock()
        }
        return ret
    }

    /**
     * Notifies this instance that an RTP or RTCP packet has been received from a peer represented
     * by a specific `PushSourceStreamDesc`.
     *
     * @param streamDesc a `PushSourceStreamDesc` which identifies the peer from which an RTP or RTCP
     * packet has been received
     * @param buf the buffer which contains the bytes of the received RTP or RTCP packet
     * @param off the zero-based index in `buf` at which the bytes of the received RTP or RTCP packet begin
     * @param len the number of bytes in `buf` beginning at `off` which represent the
     * received RTP or RTCP packet
     * @param flags `Buffer.FLAG_XXX`
     * @return the number of bytes in `buf` beginning at `off` which represent the
     * received RTP or RTCP packet
     * @throws IOException if an I/O error occurs while the method processes the specified RTP or RTCP packet
     */
    @Throws(IOException::class)
    fun didRead(streamDesc: PushSourceStreamDesc?, buf: ByteArray, off: Int, len: Int, flags: Int): Int {
        val lock = _lock.readLock()
        lock.lock()
        try {
            val data = streamDesc!!.data
            val streamRTPManager = streamDesc.connectorDesc.streamRTPManagerDesc
            var format: Format? = null
            if (data) {
                // Ignore RTP packets coming from peers whose MediaStream's
                // direction does not allow receiving.
                if (!streamRTPManager!!.streamRTPManager.mediaStream.direction.allowsReceiving()) {
                    // FIXME We are ignoring RTP packets received from peers who we
                    // do not want to receive from ONLY in the sense that we are not
                    // translating/forwarding them to the other peers. Do not we
                    // want to not receive them locally as well?
                    return len
                }

                // We flag an RTP packet with Buffer.FLAG_SILENCE when we want to
                // ignore its payload. Because the payload may have skipped
                // decryption as a result of the flag, it is unwise to translate/forward it.
                if (flags and Buffer.FLAG_SILENCE == Buffer.FLAG_SILENCE) return len

                // Do the bytes in the specified buffer resemble (a header of) an
                // RTP packet?
                if (len >= RTPHeader.SIZE && /* v */
                        buf[off].toInt() and 0xc0 ushr 6 == RTPHeader.VERSION) {
                    val ssrc = RTPUtils.readInt(buf, off + 8)
                    if (!streamRTPManager.containsReceiveSSRC(ssrc)) {
                        if (findStreamRTPManagerDescByReceiveSSRC(ssrc, streamRTPManager) == null) {
                            streamRTPManager.addReceiveSSRC(ssrc)
                        } else {
                            return 0
                        }
                    }
                    val pt = buf[off + 1].toInt() and 0x7f
                    format = streamRTPManager.getFormat(pt)

                    // Pass the packet to the feedback message sender to give it
                    // a chance to inspect the received packet and decide whether
                    // or not it should keep asking for a key frame or stop.
                    rtcpFeedbackMessageSender.maybeStopRequesting(
                            streamRTPManager, ssrc.toLong() and 0xffffffffL, buf, off, len)
                }
            } else if (TimberLog.isTraceEnable) {
                logRTCP(this, "read", buf, off, len)
            }
            val outputStream = if (data) connector!!.dataOutputStream else connector!!.controlOutputStream
            outputStream.write(buf, off, len, format, streamRTPManager)
        } finally {
            lock.unlock()
        }
        return len
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     */
    override fun dispose() {
        val lock = _lock.writeLock()
        lock.lock()
        try {
            rtcpFeedbackMessageSender.dispose()
            manager.removeReceiveStreamListener(this)
            try {
                manager.dispose()
            } catch (t: Throwable) {
                if (t is ThreadDeath) {
                    throw t
                } else {
                    // RTPManager.dispose() often throws at least a NullPointerException in relation to some RTP BYE.
                    Timber.e(t, "Failed to dispose of RTPManager")
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Releases the resources allocated by this instance for the purposes of the functioning of a
     * specific `StreamRTPManager` in the course of its execution and prepares that
     * `StreamRTPManager` to be garbage collected (as far as this `RTPTranslatorImpl` is concerned).
     */
    fun dispose(streamRTPManager: StreamRTPManager) {
        // XXX Here we could potentially start with a read lock and upgrade to
        // a write lock, if the streamRTPManager is in the streamRTPManagers
        // collection. Not sure about the up/down grading performance hit though.
        val lock = _lock.writeLock()
        lock.lock()
        try {
            val streamRTPManagerIter = mStreamRTPManagers.iterator()
            while (streamRTPManagerIter.hasNext()) {
                val streamRTPManagerDesc = streamRTPManagerIter.next()
                if (streamRTPManagerDesc.streamRTPManager == streamRTPManager) {
                    val connectorDesc = streamRTPManagerDesc.connectorDesc
                    if (connectorDesc != null) {
                        if (connector != null) connector!!.removeConnector(connectorDesc)
                        connectorDesc.connector!!.close()
                        streamRTPManagerDesc.connectorDesc = null
                    }
                    streamRTPManagerIter.remove()
                    break
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun findStreamRTPManagerByReceiveSSRC(receiveSSRC: Int): StreamRTPManager? {
        val desc = findStreamRTPManagerDescByReceiveSSRC(receiveSSRC, null)
        return desc?.streamRTPManager
    }

    /**
     * Finds the first `StreamRTPManager` which is related to a specific receive/remote SSRC.
     *
     * @param receiveSSRC the receive/remote SSRC to which the returned `StreamRTPManager` is to be related
     * @param exclusion the `StreamRTPManager`, if any, to be excluded from the search
     * @return the first `StreamRTPManager` which is related to the specified `receiveSSRC`
     */
    private fun findStreamRTPManagerDescByReceiveSSRC(receiveSSRC: Int, exclusion: StreamRTPManagerDesc?): StreamRTPManagerDesc? {
        val lock = _lock.readLock()
        var ret: StreamRTPManagerDesc? = null
        lock.lock()
        try {
            var i = 0
            val count = mStreamRTPManagers.size
            while (i < count) {
                val s = mStreamRTPManagers[i]
                if (s != exclusion && s.containsReceiveSSRC(receiveSSRC)) {
                    ret = s
                    break
                }
                i++
            }
        } finally {
            lock.unlock()
        }
        return ret
    }

    /**
     * Exposes [RTPManager.getControl] on the internal/underlying `RTPManager`.
     *
     * @param streamRTPManager ignored
     * @param controlType
     * @return the return value of the invocation of `RTPManager.getControl(String)` on the
     * internal/underlying `RTPManager`
     */
    fun getControl(streamRTPManager: StreamRTPManager?, controlType: String?): Any {
        return manager.getControl(controlType)
    }

    /**
     * Exposes [RTPManager.getGlobalReceptionStats] on the internal/underlying `RTPManager`.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of `RTPManager.getGlobalReceptionStats()`
     * on the internal/underlying `RTPManager`
     */
    fun getGlobalReceptionStats(streamRTPManager: StreamRTPManager?): GlobalReceptionStats {
        return manager.globalReceptionStats
    }

    /**
     * Exposes [RTPManager.getGlobalReceptionStats] on the internal/underlying `RTPManager`.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of
     * `RTPManager.getGlobalTransmissionStats()` on the internal/underlying `RTPManager`
     */
    fun getGlobalTransmissionStats(streamRTPManager: StreamRTPManager?): GlobalTransmissionStats {
        return manager.globalTransmissionStats
    }

    /**
     * Exposes [RTPSessionMgr.getLocalSSRC] on the internal/underlying `RTPSessionMgr`.
     *
     * @param streamRTPManager ignored
     * @return the return value of the invocation of `RTPSessionMgr.getLocalSSRC()` on the
     * internal/underlying `RTPSessionMgr`
     */
    fun getLocalSSRC(streamRTPManager: StreamRTPManager?): Long {
        // if (streamRTPManager == null)
        // return localSSRC;
        // return ((RTPSessionMgr) manager).getLocalSSRC();

        // XXX(gp) it makes (almost) no sense to use the FMJ SSRC because, at
        // least in the case of jitsi-videobridge, it's not announced to the
        // peers, resulting in Chrome's discarding the RTP/RTCP packets with
        // ((RTPSessionMgr) manager).getLocalSSRC(); as the media sender SSRC.
        // This makes the ((RTPSessionMgr) manager).getLocalSSRC() useless in
        // 95% of the use cases (hence the "almost" in the beginning of this comment).
        return localSSRC
    }

    /**
     * Gets the `ReceiveStream`s associated with/related to a neomedia `MediaStream`
     * (specified in the form of a `StreamRTPManager` instance for the purposes of and in
     * the terms of `RTPManagerImpl`).
     *
     * @param streamRTPManager the `StreamRTPManager` to which the returned `ReceiveStream`s are to be related
     * @return the `ReceiveStream`s related to/associated with the specified `streamRTPManager`
     */
    fun getReceiveStreams(streamRTPManager: StreamRTPManager): Vector<ReceiveStream>? {
        val lock = _lock.readLock()
        var receiveStreams: Vector<ReceiveStream>? = null
        lock.lock()
        try {
            val streamRTPManagerDesc = getStreamRTPManagerDesc(streamRTPManager, false)
            if (streamRTPManagerDesc != null) {
                val managerReceiveStreams = manager.receiveStreams
                if (managerReceiveStreams != null) {
                    receiveStreams = Vector<ReceiveStream>(managerReceiveStreams.size)
                    for (s in managerReceiveStreams) {
                        val receiveStream = s as ReceiveStream
                        // FMJ stores the synchronization source (SSRC) identifiers
                        // as 32-bit signed values.
                        val receiveSSRC = receiveStream.ssrc.toInt()
                        if (streamRTPManagerDesc.containsReceiveSSRC(receiveSSRC)) receiveStreams.add(receiveStream)
                    }
                }
            }
        } finally {
            lock.unlock()
        }
        return receiveStreams
    }

    /**
     * Gets the `SendStream`s associated with/related to a neomedia `MediaStream`
     * (specified in the form of a `StreamRTPManager` instance for the purposes of and in
     * the terms of `RTPManagerImpl`).
     *
     * @param streamRTPManager the `StreamRTPManager` to which the returned `SendStream`s are to be related
     * @return the `SendStream`s related to/associated with the specified `streamRTPManager`
     */
    fun getSendStreams(streamRTPManager: StreamRTPManager): Vector<SendStream>? {
        val lock = _lock.readLock()
        var sendStreams: Vector<SendStream>? = null
        lock.lock()
        try {
            val managerSendStreams = manager.sendStreams
            if (managerSendStreams != null) {
                sendStreams = Vector<SendStream>(managerSendStreams.size)
                for (sendStreamDesc in this.sendStreams) {
                    if (managerSendStreams.contains(sendStreamDesc.sendStream)) {
                        val sendStream = sendStreamDesc.getSendStream(streamRTPManager, false)
                        if (sendStream != null) sendStreams.add(sendStream)
                    }
                }
            }
        } finally {
            lock.unlock()
        }
        return sendStreams
    }

    private fun getStreamRTPManagerDesc(streamRTPManager: StreamRTPManager, create: Boolean): StreamRTPManagerDesc? {
        val lock = if (create) _lock.writeLock() else _lock.readLock()
        var ret: StreamRTPManagerDesc? = null
        lock.lock()
        try {
            for (s in mStreamRTPManagers) {
                if (s.streamRTPManager == streamRTPManager) {
                    ret = s
                    break
                }
            }
            if (ret == null && create) {
                ret = StreamRTPManagerDesc(streamRTPManager)
                mStreamRTPManagers.add(ret)
            }
        } finally {
            lock.unlock()
        }
        return ret
    }

    /**
     * {@inheritDoc}
     */
    override fun getStreamRTPManagers(): List<StreamRTPManager> {
        val ret = ArrayList<StreamRTPManager>(mStreamRTPManagers.size)
        for (streamRTPManagerDesc in mStreamRTPManagers) {
            ret.add(streamRTPManagerDesc.streamRTPManager)
        }
        return ret
    }

    fun initialize(streamRTPManager: StreamRTPManager, connector: RTPConnector?) {
        val w = _lock.writeLock()
        val r = _lock.readLock()
        var lock: Lock // the lock which is to eventually be unlocked
        w.lock()
        lock = w
        try {
            if (this.connector == null) {
                this.connector = RTPConnectorImpl(this)
                manager.initialize(this.connector)
            }
            val streamRTPManagerDesc = getStreamRTPManagerDesc(streamRTPManager, true)

            // We got the connector and the streamRTPManagerDesc. We can now
            // downgrade the lock on this translator.
            r.lock()
            w.unlock()
            lock = r

            // We're managing access to the streamRTPManagerDesc.
            synchronized(streamRTPManagerDesc!!) {
                var connectorDesc = streamRTPManagerDesc.connectorDesc
                if (connectorDesc == null || connectorDesc.connector != connector) {
                    if (connectorDesc != null) {
                        // The connector is thread-safe.
                        this.connector!!.removeConnector(connectorDesc)
                    }
                    connectorDesc = if (connector == null) null else RTPConnectorDesc(streamRTPManagerDesc, connector)
                    streamRTPManagerDesc.connectorDesc = connectorDesc
                }
                if (connectorDesc != null) {
                    // The connector is thread-safe.
                    this.connector!!.addConnector(connectorDesc)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Removes a `ReceiveStreamListener` to no longer be notified about
     * `ReceiveStreamEvent`s related to a specific neomedia `MediaStream` (expressed
     * as a `StreamRTPManager` for the purposes of and in the terms of `RTPTranslator`).
     * Since [.addReceiveStreamListener] does not
     * add equal `ReceiveStreamListener`s, a single removal is enough to reverse multiple
     * additions of equal `ReceiveStreamListener`s.
     *
     * @param streamRTPManager the `StreamRTPManager` which specifies the neomedia `MediaStream` with
     * which the `ReceiveStreamEvent`s delivered to the specified `listener` are to be related
     * @param listener the `ReceiveStreamListener` to no longer be notified about
     * `ReceiveStreamEvent`s related to the specified `streamRTPManager`
     */
    fun removeReceiveStreamListener(streamRTPManager: StreamRTPManager, listener: ReceiveStreamListener?) {
        val desc = getStreamRTPManagerDesc(streamRTPManager, false)
        desc?.removeReceiveStreamListener(listener)
    }

    /**
     * Removes a `RemoteListener` to no longer be notified about `RemoteEvent`s
     * received by this `RTPTranslatorImpl`. Though the request is being made by a specific
     * `StreamRTPManager`, the addition of the specified `listener` and the deliveries
     * of the `RemoteEvent`s are performed irrespective of any `StreamRTPManager` so
     * the removal follows the same logic.
     *
     * @param streamRTPManager the `StreamRTPManager` which is requesting the removal of the specified
     * `RemoteListener`
     * @param listener the `RemoteListener` to no longer be notified about `RemoteEvent`s
     * received by this `RTPTranslatorImpl`
     */
    fun removeRemoteListener(streamRTPManager: StreamRTPManager?, listener: RemoteListener?) {
        manager.removeRemoteListener(listener)
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     * (Additionally, [.addSendStreamListener] is not
     * implemented for the same reason.)
     */
    fun removeSendStreamListener(streamRTPManager: StreamRTPManager?, listener: SendStreamListener?) {
        // TODO Auto-generated method stub
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     * (Additionally, [.addSessionListener] is not
     * implemented for the same reason.)
     */
    fun removeSessionListener(streamRTPManager: StreamRTPManager?, listener: SessionListener?) {
        // TODO Auto-generated method stub
    }

    /**
     * Sets the local SSRC for this `RTPTranslatorImpl`.
     *
     * @param localSSRC the SSRC to set.
     */
    fun setLocalSSRC(localSSRC: Long) {
        this.localSSRC = localSSRC
    }

    /**
     * Sets the `SSRCFactory` which is to generate new synchronization source (SSRC)
     * identifiers.
     *
     * @param ssrcFactory the `SSRCFactory` which is to generate new synchronization source (SSRC)
     * identifiers or `null` if this `MediaStream` is to employ internal logic
     * to generate new synchronization source (SSRC) identifiers
     */
    fun setSSRCFactory(ssrcFactory: SSRCFactory?) {
        val manager = manager
        if (manager is RTPSessionMgr) {
            manager.sSRCFactory = ssrcFactory
        }
    }

    /**
     * Notifies this `ReceiveStreamListener` about a specific event related to a
     * `ReceiveStream`.
     *
     * @param event a `ReceiveStreamEvent` which contains the specifics of the event this
     * `ReceiveStreamListener` is being notified about
     * @see ReceiveStreamListener.update
     */
    override fun update(event: ReceiveStreamEvent) {
        /*
         * Because NullPointerException was seen during testing, be thorough with the null checks.
         */
        if (event != null) {
            val receiveStream = event.receiveStream
            if (receiveStream != null) {
                /*
                 * FMJ stores the synchronization source (SSRC) identifiers as 32-bit signed
                 * values.
                 */
                val receiveSSRC = receiveStream.ssrc.toInt()
                val streamRTPManagerDesc = findStreamRTPManagerDescByReceiveSSRC(receiveSSRC, null)
                if (streamRTPManagerDesc != null) {
                    for (listener in streamRTPManagerDesc.receiveStreamListeners) {
                        listener!!.update(event)
                    }
                }
            }
        }
    }

    /**
     * Notifies this `RTPTranslator` that a `buffer` from a `source` will be written into a `destination`.
     *
     * @param source the source of `buffer`
     * @param pkt the packet from `source` which is to be written into `destination`
     * @param destination the destination into which `buffer` is to be written
     * @param data `true` for data/RTP or `false` for control/RTCP
     * @return `true` if the writing is to continue or `false` if the writing is to abort
     */
    fun willWrite(source: StreamRTPManagerDesc?, pkt: RawPacket?,
            destination: StreamRTPManagerDesc?, data: Boolean): Boolean {
        val src = source?.streamRTPManager?.mediaStream
        val dst = destination!!.streamRTPManager.mediaStream
        return willWrite(src, pkt, dst, data)
    }

    /**
     * Writes an `RTCPFeedbackMessage` into a destination identified by a specific
     * `MediaStream`.
     *
     * @param controlPayload
     * @param destination
     * @return `true` if the `controlPayload` was written into the
     * `destination`; otherwise, `false`
     */
    fun writeControlPayload(controlPayload: Payload, destination: MediaStream): Boolean {
        val connector = connector
        return connector != null && connector.writeControlPayload(controlPayload, destination)
    }

    /**
     * Provides access to the underlying `SSRCCache` that holds statistics information about
     * each SSRC that we receive.
     *
     * @return the underlying `SSRCCache` that holds statistics information about each SSRC
     * that we receive.
     */
    override val sSRCCache: SSRCCache
        get() = (manager as RTPSessionMgr).ssrcCache

    companion object {
        /**
         * Logs information about an RTCP packet for debugging purposes.
         *
         * @param obj the object which is the source of the log request
         * @param methodName the name of the method on `obj` which is the source of the log request
         * @param buf the `byte`s which (possibly) represent an RTCP packet to be logged for debugging purposes
         * @param off the position within `buf` at which the valid data begins
         * @param len the number of bytes in `buf` which constitute the valid data
         */
        fun logRTCP(obj: Any, methodName: String?, buf: ByteArray?, off: Int, len: Int) {
            // Do the bytes in the specified buffer resemble (a header of) an RTCP packet?
            if (len >= 8 /* BYE */) {
                val b0 = buf!![off]
                val v = b0.toInt() and 0xc0 ushr 6
                if (v == RTCPHeader.VERSION) {
                    val b1 = buf[off + 1]
                    val pt = b1.toInt() and 0xff
                    if (pt == 203 /* BYE */) {
                        // Verify the length field.
                        val rtcpLength = (RTPUtils.readUint16AsInt(buf, off + 2) + 1) * 4
                        if (rtcpLength <= len) {
                            val sc = b0.toInt() and 0x1f
                            var o = off + 4
                            var i = 0
                            val end = off + len
                            while (i < sc && o + 4 <= end) {
                                val ssrc = RTPUtils.readInt(buf, o)
                                Timber.log(TimberLog.FINER, "%s.%s: RTCP BYE SSRC/CSRC %s",
                                        obj.javaClass.name, methodName, (ssrc.toLong() and 0xffffffffL).toString())
                                ++i
                                o += 4
                            }
                        }
                    }
                }
            }
        }
    }
}