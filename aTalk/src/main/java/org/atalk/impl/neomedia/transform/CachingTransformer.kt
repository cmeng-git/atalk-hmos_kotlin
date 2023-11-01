/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.impl.neomedia.rtp.RawPacketCache
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.concurrent.RecurringRunnable
import timber.log.Timber

/**
 * Implements a cache of outgoing RTP packets.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class CachingTransformer(stream: MediaStreamImpl) : SinglePacketTransformerAdapter(RTPPacketPredicate.INSTANCE), TransformEngine, RecurringRunnable {

    /**
     * The outgoing packet cache.
     */
    val outgoingRawPacketCache: RawPacketCache

    /**
     * The incoming packet cache.
     */
    private val incomingRawPacketCache: RawPacketCache

    /**
     * Whether or not this `TransformEngine` has been closed.
     */
    private var closed = false

    /**
     * Whether caching packets is enabled or disabled. Note that the default value is `false`.
     */
    private var enabled = false

    /**
     * The last time [.run] was called.
     */
    private var lastUpdateTime = -1L

    /**
     * Initializes a new CachingTransformer instance.
     *
     * stream the owning stream.
     */
    init {
        outgoingRawPacketCache = RawPacketCache(stream.hashCode())
        incomingRawPacketCache = RawPacketCache(-1)
    }

    /**
     * {@inheritDoc}
     */
    override fun close() {
        if (closed)
            return
        closed = true

        try {
            outgoingRawPacketCache.close()
        } catch (e: Exception) {
            Timber.e(e)
        }

        try {
            incomingRawPacketCache.close()
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * Transforms an outgoing packet.
     */
    override fun transform(pkt: RawPacket): RawPacket {
        if (enabled && !closed) {
            outgoingRawPacketCache.cachePacket(pkt)
        }
        return pkt
    }

    override fun reverseTransform(pkt: RawPacket): RawPacket {
        if (enabled && !closed) {
            incomingRawPacketCache.cachePacket(pkt)
        }
        return pkt
    }

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * {@inheritDoc}
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * {@inheritDoc}
     */
    override val timeUntilNextRun: Long
        get() {
            return if (lastUpdateTime < 0L) 0L
            else lastUpdateTime + PROCESS_INTERVAL_MS - System.currentTimeMillis()
        }

    /**
     * {@inheritDoc}
     */
    override fun run() {
        lastUpdateTime = System.currentTimeMillis()
        outgoingRawPacketCache.clean(lastUpdateTime)
        incomingRawPacketCache.clean(lastUpdateTime)
    }

    /**
     * Enables/disables the caching of packets.
     *
     * @param enabled `true` if the caching of packets is to be enabled or
     * `false` if the caching of packets is to be disabled
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Timber.d("%s CachingTransformer %s", if (enabled) "Enabling" else "Disabling", hashCode())
    }

    companion object {
        /**
         * The period of time between calls to [.run] will be requested if this [CachingTransformer] is enabled.
         */
        private const val PROCESS_INTERVAL_MS = 10000
    }
}