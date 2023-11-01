/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ByteArrayBuffer
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Predicate

/**
 * Extends the `PacketTransformer` interface with methods which allow the transformation of a
 * single packet into a single packet.
 *
 * Eases the implementation of `PacketTransformer`-s which transform each
 * packet into a single transformed packet (as opposed to an array of possibly more than one packet).
 *
 * Need API-24 for new implementation (Use Function from smack).
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
`` */
abstract class SinglePacketTransformer
/**
 * Ctor.
 *
 * XXX At some point ideally we would get rid of this ctor and all the inheritors will use the
 * parametrized ctor. Also, we might want to move this check inside the `TransformEngineChain`
 * so that we only make the check once per packet: The RTCP transformer is only supposed only to (reverse)
 * transform RTCP packets and the RTP transformer is only supposed to modify RTP packets.
 */
@JvmOverloads constructor(
        /**
         * The idea is to have `PacketTransformer` implementations strictly associated with a
         * `Predicate` so that they only process packets that they're supposed to process. For
         * example, transformers that transform RTP packets should not transform RTCP packets, if, by
         * mistake, they happen to be passed RTCP packets.
         */
        private val packetPredicate: Predicate<ByteArrayBuffer>? = null,
) : PacketTransformer {
    /**
     * The number of exceptions caught in [.reverseTransform].
     */
    private val exceptionsInReverseTransform = AtomicInteger()

    /**
     * The number of exceptions caught in [.transform].
     */
    private val exceptionsInTransform = AtomicInteger()

    /*
     * A cached link to {@link #reverseTransform(RawPacket)} method to reduce calling overhead on hotpath.
     * Need API-24 (Use Function from smack)
     */
    private val cachedReverseTransform = Function { pkt: RawPacket -> this.reverseTransform(pkt) }

    /*
     * A cached link to {@link #transform(RawPacket)} method to reduce calling overhead on hotpath.
     * Need API-24 (Use Function from smack)
     */
    private val cachedTransform = Function { pkt: RawPacket -> this.transform(pkt) }

    /**
     * Ctor.
     *
     * @param packetPredicate the `PacketPredicate` to use to match packets to (reverse) transform.
     */
    /**
     * {@inheritDoc}
     *
     * The (default) implementation of `SinglePacketTransformer` does nothing.
     */
    override fun close() {}

    /**
     * Reverse-transforms a specific packet.
     *
     * @param pkt the transformed packet to be restored.
     * @return the reversed transformed packet.
     */
    abstract fun reverseTransform(pkt: RawPacket): RawPacket?

    /**
     * {@inheritDoc}
     *
     * Reverse-transforms an array of packets by calling [.reverseTransform] on each one.
     */
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return transformArray(pkts, cachedReverseTransform, exceptionsInReverseTransform, "reverseTransform")!!
    }

    /**
     * Transforms a specific packet.
     *
     * @param pkt the packet to be transformed.
     * @return the transformed packet.
     */
    abstract fun transform(pkt: RawPacket): RawPacket?

    /**
     * {@inheritDoc}
     *
     * Transforms an array of packets by calling [.transform] on each one.
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?>? {
        return transformArray(pkts, cachedTransform, exceptionsInTransform, "transform")
    }

    /*
     * cmeng - for API-24 implementation - use Function from smack
     * Applies a specific transformation function to an array of {@link RawPacket}s.
     *
     * @param pkts the array to transform.
     * @param transformFunction the function to apply to each (non-null) element of the array.
     * @param exceptionCounter a counter of the number of exceptions encountered.
     * @param logMessage a name of the transformation function, to be used when logging exceptions.
     * @return {@code pkts}.
     */
    private fun transformArray(
            pkts: Array<RawPacket?>?, transformFunction: Function<RawPacket, RawPacket?>,
            exceptionCounter: AtomicInteger, logMessage: String,
    ): Array<RawPacket?>? {
        if (pkts != null) {
            for (i in pkts.indices) {
                val pkt = pkts[i]
                if (pkt != null
                        && (packetPredicate == null || packetPredicate.test(pkt))) {
                    try {
                        pkts[i] = transformFunction.apply(pkt)
                    } catch (t: Throwable) {
                        exceptionCounter.incrementAndGet()
                        if (exceptionCounter.get() % EXCEPTIONS_TO_LOG == 0
                                || exceptionCounter.get() == 1) {
                            Timber.e(t, "Failed to %s RawPacket(s)!", logMessage)
                        }
                        if (t is Error) {
                            throw t
                        } else {
                            throw (t as RuntimeException)
                        }
                    }
                }
            }
        }
        return pkts
    }

    companion object {
        /**
         * The number of `Throwable`s to log with a single call to `logger`. If every
         * `Throwable` is logged in either of [.reverseTransform] and
         * [.transform], the logging may be overwhelming.
         */
        private const val EXCEPTIONS_TO_LOG = 1000
    }
}