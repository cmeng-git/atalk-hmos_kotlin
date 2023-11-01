/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.transform.zrtp.ZRTPTransformEngine
import org.atalk.service.neomedia.RawPacket

/**
 * The engine chain allows using numerous `TransformEngine`s on a single stream.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class TransformEngineChain : TransformEngine {
    /**
     * The sequence of `TransformEngine`s whose `PacketTransformer`s this engine chain
     * will be applying to RTP and RTCP packets. Implemented as copy-on-write storage for the
     * purposes of performance.
     */
    private lateinit var engineChain: Array<TransformEngine>

    /**
     * The view of [.engineChain] as a `List`.
     */
    private lateinit var engineChainAsList: List<TransformEngine>

    /**
     * The sequence of `PacketTransformer`s that this engine chain will be applying to RTCP
     * packets.
     */
    private var rtcpTransformChain: PacketTransformerChain? = null

    /**
     * The sequence of `PacketTransformer`s that this engine chain will be applying to RTP
     * packets.
     */
    private var rtpTransformChain: PacketTransformerChain? = null

    /**
     * Creates a new `TransformEngineChain` using the `engineChain` array. Engines
     * will be applied in the order specified by the `engineChain` array for outgoing packets
     * and in the reverse order for incoming packets.
     *
     * @param engineChain an array containing `TransformEngine`s in the order that they are to be applied
     * on outgoing packets.
     */
    constructor(engineChain: Array<TransformEngine>) {
        setEngineChain(engineChain.clone())
    }

    /**
     * Creates a new `TransformEngineChain` without initializing the array of transformers to
     * be used. Allows extending classes to initialize the array on their own.
     */
    private constructor() {
        // Extenders must initialize this.engineChain
    }

    /**
     * Appends a [TransformEngine] to this chain.
     * @param engine the engine to add.
     * @return `true` if the engine was added, and `false` if the
     * engine was not added because it is already a member of the chain.
     */
    fun addEngine(engine: TransformEngine?): Boolean {
        if (engine == null)
            throw NullPointerException("engine")

        synchronized(this) {
            val oldValue = engineChain
            for (e in oldValue) {
                if (engine == e)
                    return false
            }
            addEngine(engine, oldValue.size)
        }
        return true
    }

    /**
     * Adds a [TransformEngine] to this chain, at the position after the `after` instance.
     * @param engine the engine to add.
     * @param after the [TransformEngine] instance from this chain, after which `engine` should be inserted.
     * @return `true` if the engine was added, and `false` if the
     * engine was not added because it is already a member of the chain.
     */
    fun addEngine(engine: TransformEngine?, after: TransformEngine?): Boolean {
        if (engine == null)
            throw NullPointerException("engine")

        synchronized(this) {
            val oldValue = engineChain
            for (e in oldValue) {
                if (engine == e)
                    return false
            }

            var position = -1
            if (after == null) {
                position = 0
            }
            for (i in oldValue.indices) {
                if (oldValue[i] === after) {
                    position = i + 1
                    break
                }
            }

            if (position == -1) {
                return false
            } else {
                addEngine(engine, position)
            }
        }
        return true
    }

    /**
     * Adds a [TransformEngine] at a specific position in this chain.
     * @param engine the engine to add.
     * @param position the position at which to add the engine.
     */
    private fun addEngine(engine: TransformEngine?, position: Int) {
        if (engine == null)
            throw NullPointerException("engine")

        synchronized(this) {
            val oldValue = engineChain
            require(!(position < 0 || position > oldValue.size)) { "position=" + position + "; len=" + oldValue.size }

            val newValue = Array<TransformEngine>(oldValue.size + 1) { ZRTPTransformEngine() }
            System.arraycopy(oldValue, 0, newValue, 0, position)
            System.arraycopy(
                    oldValue, position,
                    newValue, position + 1,
                    oldValue.size - position)
            newValue[position] = engine
            setEngineChain(newValue)
        }
    }

    /**
     * Gets the sequence of `TransformEngine`s whose `PacketTransformer`s this engine
     * chain applies to RTP and RTCP packets.
     *
     * @return the sequence of `TransformEngine`s whose `PacketTransformer`s this
     * engine chain applies to RTP and RTCP packets
     */
    fun getEngineChain(): Array<TransformEngine> {
        return engineChain.clone()
    }

    /*
     * XXX Certain TransformEngine implementations in engineChain may postpone the
     * initialization of their PacketTransformer until it is requested for the first time AND
     * may have to send packets at that very moment, not earlier (e.g. DTLS-SRTP may have to
     * send Client Hello). Make sure that, when the PacketTransformer of this TransformEngine is
     * requested for the first time, the same method will be invoked on each of the
     * TransformEngines in engineChain.
     */
    /**
     * Returns the meta `PacketTransformer` that will be applying RTCP transformations from
     * all engines registered in this `TransformEngineChain`.
     *
     * @return a `PacketTransformerChain` over all RTCP transformers in this engine chain.
     */
    override val rtcpTransformer: PacketTransformer?
        get() {
            /*
		 * XXX Certain TransformEngine implementations in engineChain may postpone the
		 * initialization of their PacketTransformer until it is requested for the first time AND
		 * may have to send packets at that very moment, not earlier (e.g. DTLS-SRTP may have to
		 * send Client Hello). Make sure that, when the PacketTransformer of this TransformEngine is
		 * requested for the first time, the same method will be invoked on each of the
		 * TransformEngines in engineChain.
		 */
            var invokeOnEngineChain: Boolean
            var rtpTransformer: PacketTransformer?

            synchronized(this) {
                if (rtcpTransformChain == null) {
                    rtcpTransformChain = PacketTransformerChain(false)
                    invokeOnEngineChain = true
                } else {
                    invokeOnEngineChain = false
                }
                rtpTransformer = rtcpTransformChain
            }
            if (invokeOnEngineChain) {
                for (engine in engineChain)
                    engine.rtcpTransformer
            }
            return rtpTransformer
        }/*
		 * XXX Certain TransformEngine implementations in engineChain may postpone the
		 * initialization of their PacketTransformer until it is requested for the first time AND
		 * may have to send packets at that very moment, not earlier (e.g. DTLS-SRTP may have to
		 * send Client Hello). Make sure that, when the PacketTransformer of this TransformEngine is
		 * requested for the first time, the same method will be invoked on each of the
		 * TransformEngines in engineChain.
		 */

    /**
     * Returns the meta `PacketTransformer` that will be applying RTCP transformations from
     * all engines registered in this `TransformEngineChain`.
     *
     * @return a `PacketTransformerChain` over all RTP transformers in this engine chain.
     */
    override val rtpTransformer: PacketTransformer?
        get() {
            /*
             * XXX Certain TransformEngine implementations in engineChain may postpone the
             * initialization of their PacketTransformer until it is requested for the first time AND
             * may have to send packets at that very moment, not earlier (e.g. DTLS-SRTP may have to
             * send Client Hello). Make sure that, when the PacketTransformer of this TransformEngine is
             * requested for the first time, the same method will be invoked on each of the
             * TransformEngines in engineChain.
             */
            var invokeOnEngineChain: Boolean
            var rtpTransformer: PacketTransformer?

            synchronized(this) {
                if (rtpTransformChain == null) {
                    rtpTransformChain = PacketTransformerChain(true)
                    invokeOnEngineChain = true
                } else {
                    invokeOnEngineChain = false
                }
                rtpTransformer = rtpTransformChain
            }
            if (invokeOnEngineChain) {
                for (engine in engineChain)
                    engine.rtpTransformer
            }
            return rtpTransformer
        }

    /**
     * Sets the sequence of `TransformEngine`s whose `PacketTransformer`s this engine
     * chain will be applying to RTP and RTCP packets.
     *
     * @param engineChain
     * the sequence of `TransformEngine`s whose `PacketTransformer`s this engine
     * chain will be applying to RTP and RTCP packets
     */
    private fun setEngineChain(engineChain: Array<TransformEngine>) {
        this.engineChain = engineChain
        engineChainAsList = listOf(*engineChain)
    }

    /**
     * A `PacketTransformerChain` is a meta `PacketTransformer` that applies all
     * transformers present in this engine chain. The class respects the order of the engine chain
     * for outgoing packets and reverses it for incoming packets.
     */
    inner class PacketTransformerChain
    /**
     * Creates an instance of this packet transformer and prepares it to deal with RTP or RTCP
     * according to the `isRtp` argument.
     *
     * @param rtp
     * `true` if this transformer will be dealing with RTP (i.e. will transform packets
     * via the RTP transformers in this chain rather than the RTCP ones) and false` otherwise.
     */
    (
        /**
         * Indicates whether this transformer will be dealing with RTP or, in other words, whether
         * it will transform packets via the RTP transformers in this chain rather than the RTCP ones.
         */

        private val rtp: Boolean) : PacketTransformer {
        /**
         * Close the transformer and underlying transform engines.
         *
         * Propagate the close to all transformers in chain.
         */
        override fun close() {
            for (engine in engineChain) {
                val pTransformer = if (rtp) engine.rtpTransformer
                else engine.rtcpTransformer

                // the packet transformer may be null if for example the engine
                // only does RTP transformations and this is an RTCP transformer.
                pTransformer?.close()
            }
        }

        /**
         * {@inheritDoc}
         *
         * Reverse-transforms the given packets using each of the `TransformEngine`-s in the
         * engine chain in reverse order.
         */
        override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
            var pkts = pkts
            val engineChain = engineChain
            for (i in engineChain.indices.reversed()) {
                val engine = engineChain[i]
                val pTransformer = if (rtp) engine.rtpTransformer
                else engine.rtcpTransformer

                // the packet transformer may be null if for example the engine
                // only does RTP transformations and this is an RTCP transformer.
                if (pTransformer != null)
                    pkts = pTransformer.reverseTransform(pkts)
            }
            return pkts
        }

        /**
         * {@inheritDoc}
         *
         * Transforms the specified `pkts` using each of the `TransformEngine`s in the chain in order.
         */
        override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
            return transform(pkts,  /* after */null)
        }

        /**
         * Transforms the specified `pkts` using the `TransformEngine`s in the chain in
         * order starting after a specific `TransformEngine`.
         *
         * @param pkts the array of `RawPacket`s to transform
         * @param after the `TransformEngine` in the chain after which the transformation is to
         * begin. If `after` is not in the chain, the transformation executes through the whole chain.
         * @return the array of `RawPacket`s that is the result of the transformation of
         * `pkts` using the `TransformEngine`s in the chain
         */
        fun transform(pkts: Array<RawPacket?>, after: TransformEngine?): Array<RawPacket?> {
            // If the specified after is in the transformation chain, the
            // transformation is to start after it.
            var pkts = pkts
            var lookForAfter = after != null && engineChainAsList.contains(after)

            for (engine in engineChain) {
                // Start the transformation after the specified TransformEngine.
                if (lookForAfter) {
                    if (engine == after) {
                        lookForAfter = false
                    }
                    continue
                }
                // Transform.
                val transformer = if (rtp) engine.rtpTransformer
                else engine.rtcpTransformer

                // The transformer may be null if for example the engine does
                // RTP transformations only and this is an RTCP transformer.
                if (transformer != null)
                    pkts = transformer.transform(pkts)!!
            }
            return pkts
        }
    }
}