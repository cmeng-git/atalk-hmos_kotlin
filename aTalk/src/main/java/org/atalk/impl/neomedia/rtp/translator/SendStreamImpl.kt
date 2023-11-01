/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator

import org.atalk.impl.neomedia.rtp.StreamRTPManager
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import javax.media.protocol.*
import javax.media.rtp.*
import javax.media.rtp.rtcp.SenderReport
import javax.media.rtp.rtcp.SourceDescription

/**
 * Implements a `SendStream` which is an endpoint-specific view of an actual
 * `SendStream` of the `RTPManager` of an `RTPTranslatorImpl`. When the last
 * endpoint-specific view of an actual `SendStream` is closed, the actual `SendStream`
 * is closed.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SendStreamImpl(val streamRTPManager: StreamRTPManager, val sendStreamDesc: SendStreamDesc) : SendStream {
    private var closed = false
    private var started = false
    override fun close() {
        if (!closed) {
            try {
                if (started) stop()
            } catch (ioe: IOException) {
                throw UndeclaredThrowableException(ioe)
            } finally {
                sendStreamDesc.close(this)
                closed = true
            }
        }
    }

    override fun getDataSource(): DataSource {
        return sendStreamDesc.sendStream!!.dataSource
    }

    override fun getParticipant(): Participant {
        return sendStreamDesc.sendStream!!.participant
    }

    override fun getSenderReport(): SenderReport {
        return sendStreamDesc.sendStream!!.senderReport
    }

    override fun getSourceTransmissionStats(): TransmissionStats {
        return sendStreamDesc.sendStream!!.sourceTransmissionStats
    }

    override fun getSSRC(): Long {
        return sendStreamDesc.sendStream!!.ssrc
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun setBitRate(bitRate: Int): Int {
        // TODO Auto-generated method stub
        return 0
    }

    /**
     * Not implemented because there are currently no uses of the underlying functionality.
     */
    override fun setSourceDescription(sourceDescription: Array<SourceDescription>) {
        // TODO Auto-generated method stub
    }

    @Throws(IOException::class)
    override fun start() {
        if (closed) {
            throw IOException("Cannot start SendStream after it has been closed.")
        }
        if (!started) {
            sendStreamDesc.start(this)
            started = true
        }
    }

    @Throws(IOException::class)
    override fun stop() {
        if (!closed && started) {
            sendStreamDesc.stop(this)
            started = false
        }
    }
}