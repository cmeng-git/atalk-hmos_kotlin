/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import org.atalk.impl.neomedia.rtp.StreamRTPManager
import java.io.IOException
import java.util.*
import javax.media.protocol.*
import javax.media.rtp.SendStream

/**
 * Describes a `SendStream` created by the `RTPManager` of an
 * `RTPTranslatorImpl`. Contains information about the `DataSource` and its stream
 * index from which the `SendStream` has been created so that various
 * `StreamRTPManager` receive different views of one and the same `SendStream`.
 *
 * @author Lyubomir Marinov
 */
class SendStreamDesc(private val translator: RTPTranslatorImpl,
        /**
         * The `DataSource` from which [.sendStream] has been created.
         */
        val dataSource: DataSource,
        /**
         * The index of the stream of [.dataSource] from which [.sendStream] has been
         * created.
         */
        val streamIndex: Int,
        /**
         * The `SendStream` created from the stream of [.dataSource] at index
         * [.streamIndex].
         */
        val sendStream: SendStream?) {
    /**
     * The list of `StreamRTPManager`-specific views to [.sendStream].
     */
    private val sendStreams: MutableList<SendStreamImpl> = LinkedList()

    /**
     * The number of `StreamRTPManager`s which have started their views of
     * [.sendStream].
     */
    private var started = 0
    fun close(sendStream: SendStreamImpl) {
        var close = false
        synchronized(this) {
            if (sendStreams.contains(sendStream)) {
                sendStreams.remove(sendStream)
                close = sendStreams.isEmpty()
            }
        }
        if (close) translator.closeSendStream(this)
    }

    @Synchronized
    fun getSendStream(streamRTPManager: StreamRTPManager,
            create: Boolean): SendStreamImpl? {
        for (sendStream in sendStreams) if (sendStream.streamRTPManager == streamRTPManager) return sendStream
        return if (create) {
            val sendStream = SendStreamImpl(streamRTPManager, this)
            sendStreams.add(sendStream)
            sendStream
        } else null
    }

    @get:Synchronized
    val sendStreamCount: Int
        get() = sendStreams.size

    @Synchronized
    @Throws(IOException::class)
    fun start(sendStream: SendStreamImpl) {
        if (sendStreams.contains(sendStream)) {
            if (started < 1) {
                this.sendStream!!.start()
                started = 1
            } else started++
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun stop(sendStream: SendStreamImpl) {
        if (sendStreams.contains(sendStream)) {
            if (started == 1) {
                this.sendStream!!.stop()
                started = 0
            } else if (started > 1) started--
        }
    }
}