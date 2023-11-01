/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.rtp

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.concurrent.MonotonicAtomicLong
import org.atalk.util.logging.Logger
import timber.log.Timber
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.Map.Entry

/**
 * An simple interface which allows a packet to be retrieved from a
 * cache/storage by an SSRC identifier and a sequence number.
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
class RawPacketCache
/**
 * Initializes a new CachingTransformer instance.
 *
 * @param streamId the identifier of the owning stream.
 */
(
        /**
         * The hash code or other identifier of the owning stream, if any. Only used for logging.
         */
        private val streamId: Int) : AutoCloseable {
    /**
     * The pool of `RawPacket`s which we use to avoid allocation and GC.
     */
    private val pool: Queue<RawPacket?> = LinkedBlockingQueue(POOL_SIZE)

    /**
     * A cache of unused [Container] instances.
     */
    private val containersPool: Queue<Container> = LinkedBlockingQueue(POOL_SIZE)

    /**
     * An object used to synchronize access to [.sizeInBytes],
     * [.maxSizeInBytes], [.sizeInPackets] and
     * [.maxSizeInPackets].
     */
    private val sizesSyncRoot = Any()

    /**
     * The current size in bytes of the cache (for all SSRCs combined).
     */
    private var sizeInBytes = 0

    /**
     * The maximum reached size in bytes of the cache (for all SSRCs combined).
     */
    private var maxSizeInBytes = 0

    /**
     * The current number of packets in the cache (for all SSRCs combined).
     */
    private var sizeInPackets = 0

    /**
     * The maximum reached number of packets in the cache (for all SSRCs combined).
     */
    private var maxSizeInPackets = 0

    /**
     * Counts the number of requests (calls to [.get]) which
     * the cache was able to answer.
     */
    private val totalHits = AtomicInteger(0)

    /**
     * Counts the number of requests (calls to [.get]) which
     * the cache was not able to answer.
     */
    private val totalMisses = AtomicInteger(0)

    /**
     * Counts the total number of packets added to this cache.
     */
    private val totalPacketsAdded = AtomicInteger(0)

    /**
     * Contains a `Cache` instance for each SSRC.
     */
    private val caches = HashMap<Long, Cache?>()

    /**
     * The age in milliseconds of the oldest packet retrieved from any of the
     * [Cache]s of this instance.
     */
    private val oldestHit = MonotonicAtomicLong()

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun close() {
        if (totalPacketsAdded.get() > 0) {
            Timber.i("%s closed,stream=%d max_size_bytes=%s,max_size_packets=%s,total_hits=%d, total_misses=%d, total_packets=%d, oldest_hit_ms=%s",
                    Logger.Category.STATISTICS, streamId, maxSizeInBytes, maxSizeInPackets, totalHits.get(),
                    totalMisses.get(), totalPacketsAdded.get(), oldestHit)
        }
        synchronized(caches) { caches.clear() }
        pool.clear()
        containersPool.clear()
    }

    /**
     * Gets the packet, encapsulated in a [Container] with the given SSRC
     * and RTP sequence number from the cache. If no such packet is found, returns `null`.
     *
     * @param ssrc The SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @return the packet, encapsulated in a [Container] with the given
     * SSRC and RTP sequence number from the cache. If no such packet is found,
     * returns `null`.
     */
    fun getContainer(ssrc: Long, seq: Int): Container? {
        val cache = getCache(ssrc and 0xffffffffL, false)
        val container = cache?.get(seq)
        if (container != null) {
            if (container.timeAdded > 0) {
                oldestHit.increase(System.currentTimeMillis() - container.timeAdded)
            }
            totalHits.incrementAndGet()
        } else {
            totalMisses.incrementAndGet()
        }
        return container
    }

    /**
     * Gets the packet with the given SSRC and RTP sequence number from the
     * cache. If no such packet is found, returns `null`.
     *
     * @param ssrc The SSRC of the packet.
     * @param seq the RTP sequence number of the packet.
     * @return the packet with the given SSRC and RTP sequence number from the cache. If no such
     * packet is found, returns `null`.
     */
    operator fun get(ssrc: Long, seq: Int): RawPacket? {
        val container = getContainer(ssrc, seq)
        return container?.pkt
    }

    /**
     * Gets the [Cache] instance which caches packets with SSRC
     * `ssrc`, creating if `create` is set and creation is
     * possible (the maximum number of caches hasn't been reached).
     *
     * @param ssrc the SSRC.
     * @param create whether to create an instance if one doesn't already exist.
     * @return the cache for `ssrc` or `null`.
     */
    private fun getCache(ssrc: Long, create: Boolean): Cache? {
        synchronized(caches) {
            var cache = caches[ssrc]
            if (cache == null && create) {
                if (caches.size < MAX_SSRC_COUNT) {
                    cache = Cache()
                    caches[ssrc] = cache
                } else {
                    Timber.w("Not creating a new cache for SSRC %s: too many SSRCs already cached.", ssrc)
                }
            }
            return cache
        }
    }

    /**
     * Saves a packet in the cache.
     *
     * @param pkt the packet to save.
     */
    fun cachePacket(pkt: RawPacket) {
        val cache = getCache(pkt.getSSRCAsLong(), true)
        if (cache != null) {
            Timber.log(TimberLog.FINER, "Caching a packet.SSRC = %s seq = %s",
                    pkt.getSSRCAsLong(), pkt.sequenceNumber)
            cache.insert(pkt)
            totalPacketsAdded.incrementAndGet()
        }
    }

    /**
     * Gets an unused `RawPacket` with at least `len` bytes of
     * buffer space.
     *
     * @param len the minimum available length
     * @return An unused `RawPacket` with at least `len` bytes of
     * buffer space.
     */
    private fun getFreePacket(len: Int): RawPacket {
        var pkt = pool.poll()
        if (pkt == null) pkt = RawPacket(ByteArray(len), 0, 0)
        if (pkt.buffer == null || pkt.buffer.size < len) pkt.buffer = ByteArray(len)
        pkt.offset = 0
        pkt.length = 0
        return pkt
    }

    /**
     * @return an unused [Container] instance.
     */
    private val freeContainer: Container
        get() {
            var container = containersPool.poll()
            if (container == null) {
                container = Container()
            }
            return container
        }

    /**
     * Checks for [Cache] instances which have not received new packets
     * for a period longer than [.SSRC_TIMEOUT_MILLIS] and removes them.
     */
    fun clean(now: Long) {
        synchronized(caches) {
            Timber.log(TimberLog.FINER, "Cleaning CachingTransformer %s", hashCode())
            val iter = caches.entries.iterator()
            while (iter.hasNext()) {
                val (key, cache) = iter.next()
                if (cache!!.lastInsertTime + SSRC_TIMEOUT_MILLIS < now) {
                    Timber.log(TimberLog.FINER, "Removing cache for SSRC %s", key)
                    cache.empty()
                    iter.remove()
                }
            }
        }
    }

    /**
     * Returns a [Container] and its [RawPacket] to the list of free containers (and
     * packets).
     *
     * @param container the container to return.
     */
    private fun returnContainer(container: Container?) {
        if (container != null) {
            if (container.pkt != null) {
                pool.offer(container.pkt)
            }
            container.pkt = null
            containersPool.offer(container)
        }
    }

    /**
     * Gets the most recent packets from the cache that pertains to the SSRC
     * that is specified as an argument, not exceeding the number of bytes
     * specified as an argument.
     *
     * @param ssrc the SSRC whose most recent packets to retrieve.
     * @param bytes the maximum total size of the packets to retrieve.
     * @return the set of the most recent packets to retrieve, not exceeding the
     * number of bytes specified as an argument, or null if there are no packets
     * in the cache
     */
    fun getMany(ssrc: Long, bytes: Int): Set<Container>? {
        val cache = getCache(ssrc and 0xffffffffL, false)
        return cache?.getMany(bytes)
    }

    /**
     * Updates the timestamp of the packet in the cache with SSRC `ssrc`
     * and sequence number `seq`, if such a packet exists in the cache,
     * setting it to `ts`.
     *
     * @param ssrc the SSRC of the packet.
     * @param seq the sequence number of the packet.
     * @param ts the timestamp to set.
     */
    fun updateTimestamp(ssrc: Long, seq: Int, ts: Long) {
        val cache = getCache(ssrc, false)
        if (cache != null) {
            synchronized(cache) {
                val container = cache.doGet(seq)
                if (container != null) {
                    container.timeAdded = ts
                }
            }
        }
    }

    /**
     * Implements a cache for the packets of a specific SSRC.
     */
    private inner class Cache {
        /**
         * The underlying container. It maps a packet index (based on its RTP
         * sequence number, in the same way as used in SRTP (RFC3711)) to the
         * `RawPacket` with the packet contents.
         */
        private val cache = TreeMap<Int, Container?>()

        /**
         * Last system time of insertion of a packet in this cache.
         */
        var lastInsertTime = -1L

        /**
         * A Roll Over Counter (as in by RFC3711).
         */
        private var ROC = 0

        /**
         * The highest received sequence number (as in RFC3711).
         */
        private var s_l = -1

        /**
         * Inserts a packet into this `Cache`.
         *
         * @param pkt the packet to insert.
         */
        @Synchronized
        fun insert(pkt: RawPacket) {
            val len = pkt.length
            val cachePacket = getFreePacket(len)
            System.arraycopy(pkt.buffer, pkt.offset, cachePacket.buffer, 0, len)
            cachePacket.length = len
            val index = calculateIndex(pkt.sequenceNumber)
            val container = freeContainer
            container.pkt = cachePacket
            container.timeAdded = System.currentTimeMillis()

            // If the packet is already in the cache, we want to update the
            // timeAdded field for retransmission purposes. This is implemented
            // by simply replacing the old packet.
            val oldContainer = cache.put(index, container)
            synchronized(sizesSyncRoot) {
                sizeInPackets++
                sizeInBytes += len
                if (oldContainer != null) {
                    sizeInPackets--
                    sizeInBytes -= oldContainer.pkt!!.length
                }
                if (sizeInPackets > maxSizeInPackets) maxSizeInPackets = sizeInPackets
                if (sizeInBytes > maxSizeInBytes) maxSizeInBytes = sizeInBytes
            }
            returnContainer(oldContainer)
            lastInsertTime = System.currentTimeMillis()
            clean()
        }

        /**
         * Calculates the index of an RTP packet based on its RTP sequence
         * number and updates the `s_l` and `ROC` fields. Based
         * on the procedure outlined in RFC3711
         *
         * @param seq the RTP sequence number of the RTP packet.
         * @return the index of the RTP sequence number with sequence number
         * `seq`.
         */
        private fun calculateIndex(seq: Int): Int {
            if (s_l == -1) {
                s_l = seq
                return seq
            }
            var v = ROC
            if (s_l < 0x8000) if (seq - s_l > 0x8000) v = ((ROC - 1).toLong() and 0xffffffffL).toInt() else if (s_l - 0x10000 > seq) v = ((ROC + 1).toLong() and 0xffffffffL).toInt()
            if (v == ROC && seq > s_l) s_l = seq else if (v.toLong() == (ROC + 1).toLong() and 0xffffffffL) {
                s_l = seq
                ROC = v
            }
            return seq + v * 0x10000
        }

        /**
         * Returns a copy of the RTP packet with sequence number `seq`
         * from the cache, or `null` if the cache does not contain a
         * packet with this sequence number.
         *
         * @param seq the RTP sequence number of the packet to get.
         * @return a copy of the RTP packet with sequence number `seq`
         * from the cache, or `null` if the cache does not contain a
         * packet with this sequence number.
         */
        @Synchronized
        operator fun get(seq: Int): Container? {
            val container = doGet(seq)
            return if (container == null) null else Container(RawPacket(container.pkt!!.buffer.clone(),
                    container.pkt!!.offset,
                    container.pkt!!.length),
                    container.timeAdded)
        }

        /**
         * Returns the RTP packet with sequence number `seq`
         * from the cache, or `null` if the cache does not contain a
         * packet with this sequence number.
         *
         * @param seq the RTP sequence number of the packet to get.
         * @return the RTP packet with sequence number `seq`
         * from the cache, or `null` if the cache does not contain a
         * packet with this sequence number.
         */
        @Synchronized
        fun doGet(seq: Int): Container? {
            // Since sequence numbers wrap at 2^16, we can't know with absolute
            // certainty which packet the request refers to. We assume that it
            // is for the latest packet (i.e. the one with the highest index).
            var container = cache[seq + ROC * 0x10000]

            // Maybe the ROC was just bumped recently.
            if (container == null && ROC > 0) container = cache[seq + (ROC - 1) * 0x10000]

            // Since the cache only stores <code>SIZE_MILLIS</code> milliseconds of
            // packets, we assume that it doesn't contain packets spanning
            // more than one ROC.
            return container
        }

        /**
         * Drops the oldest packets from the cache until:
         * 1. The cache contains at most [.MAX_SIZE_PACKETS] packets, and
         * 2. The cache only contains packets at most [.SIZE_MILLIS]
         * milliseconds older than the newest packet in the cache.
         */
        @Synchronized
        private fun clean() {
            var size = cache.size
            if (size <= 0) return
            val cleanBefore = System.currentTimeMillis() - SIZE_MILLIS
            val iter = cache.entries.iterator()
            var removedPackets = 0
            var removedBytes = 0
            while (iter.hasNext()) {
                val container = iter.next().value
                val pkt = container!!.pkt
                if (size > MAX_SIZE_PACKETS) {
                    // Remove until we go under the max size, regardless of the timestamps.
                    size--
                } else if (container.timeAdded >= 0
                        && container.timeAdded > cleanBefore) {
                    // We reached a packet with a timestamp after 'cleanBefore'.
                    // The rest of the packets are even more recent.
                    break
                }
                iter.remove()
                removedBytes += pkt!!.length
                removedPackets++
                returnContainer(container)
            }
            synchronized(sizesSyncRoot) {
                sizeInBytes -= removedBytes
                sizeInPackets -= removedPackets
            }
        }

        @Synchronized
        fun empty() {
            var removedBytes = 0
            for (container in cache.values) {
                removedBytes += container!!.pkt!!.buffer.size
                returnContainer(container)
            }
            synchronized(sizesSyncRoot) {
                sizeInPackets -= cache.size
                sizeInBytes -= removedBytes
            }
            cache.clear()
        }

        /**
         * Gets the most recent packets from this cache, not exceeding the
         * number of bytes specified as an argument.
         *
         * @param bytes the maximum number of bytes to retrieve.
         * @return the set of the most recent packets to retrieve, not exceeding
         * the number of bytes specified as an argument, or null if there are
         * no packets in the cache.
         */
        @Synchronized
        fun getMany(bytes: Int): Set<Container>? {
            var bytes_ = bytes
            if (cache.isEmpty() || bytes_ < 1) {
                return null
            }

            // XXX(gp) This is effectively Copy-on-Read and is inefficient. We
            // should implement a Copy-on-Write method or something else that is
            // more efficient than this..
            val set = HashSet<Container>()
            val it = cache.descendingMap().entries.iterator()
            while (it.hasNext() && bytes_ > 0) {
                val container = it.next().value
                if (container?.pkt != null) {
                    set.add(container)
                    bytes_ -= container.pkt!!.length
                }
            }
            return set
        }
    }

    /**
     * A container for packets in the cache.
     */
    inner class Container
    /**
     * Initializes a new empty [Container] instance.
     */ @JvmOverloads constructor(
            /**
             * The [RawPacket] which this container holds.
             */
            var pkt: RawPacket? = null,
            /**
             * The time (in milliseconds since the epoch) that the packet was
             * added to the cache.
             */
            var timeAdded: Long = -1) {
        /**
         * Initializes a new [Container] instance.
         *
         * @param pkt the packet to hold.
         * @param timeAdded the time the packet was added.
         */
    }

    companion object {
        /**
         * The `ConfigurationService` used to load caching configuration.
         */
        private val cfg = LibJitsi.configurationService

        /**
         * Configuration property for number of streams to cache
         */
        const val NACK_CACHE_SIZE_STREAMS = "neomedia.transform.CachingTransformer.CACHE_SIZE_STREAMS"

        /**
         * Configuration property number of packets to cache.
         */
        const val NACK_CACHE_SIZE_PACKETS = "neomedia.transform.CachingTransformer.CACHE_SIZE_PACKETS"

        /**
         * Configuration property for nack cache size in milliseconds.
         */
        const val NACK_CACHE_SIZE_MILLIS = "neomedia.transform.CachingTransformer.CACHE_SIZE_MILLIS"

        /**
         * Packets added to the cache more than `SIZE_MILLIS` ago might be cleared from the cache.
         *
         * FIXME(gp) the cache size should be adaptive based on the RTT.
         */
        private val SIZE_MILLIS = cfg.getInt(NACK_CACHE_SIZE_MILLIS, 1000)

        /**
         * The maximum number of different SSRCs for which a cache will be created.
         */
        private val MAX_SSRC_COUNT = cfg.getInt(NACK_CACHE_SIZE_STREAMS, 50)

        /**
         * The maximum number of packets cached for each SSRC. A 1080p stream maxes
         * out at around 500 packets per second (pps). Assuming an RTT of 500ms, a
         * 250packets/500ms packet cache is just enough. In order to be on the safe
         * side, we use the double as defaults.
         */
        private val MAX_SIZE_PACKETS = cfg.getInt(NACK_CACHE_SIZE_PACKETS, 500)

        /**
         * The size of [.pool] and [.containersPool].
         */
        private const val POOL_SIZE = 100

        /**
         * The amount of time, after which the cache for an SSRC will be cleared,
         * unless new packets have been inserted.
         */
        private val SSRC_TIMEOUT_MILLIS = SIZE_MILLIS + 50
    }
}