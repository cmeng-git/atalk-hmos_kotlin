/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.csrc

import net.sf.fmj.media.rtp.RTPHeader
import org.atalk.impl.neomedia.AudioMediaStreamImpl
import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformerAdapter
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.RawPacket
import javax.media.Buffer

/**
 * Implements read-only support for &quot;A Real-Time Transport Protocol (RTP) Header Extension for
 * Client-to-Mixer Audio Level Indication&quot;. Optionally, drops RTP packets indicated to be
 * generated from a muted audio source in order to avoid wasting processing power such as
 * decrypting, decoding and audio mixing.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SsrcTransformEngine(mediaStream: MediaStreamImpl) : SinglePacketTransformerAdapter(), TransformEngine {
    /**
     * The dispatcher that is delivering audio levels to the media steam.
     */
    private var csrcAudioLevelDispatcher: CsrcAudioLevelDispatcher?

    /**
     * The `MediaDirection` in which this RTP header extension is active.
     */
    private var ssrcAudioLevelDirection = MediaDirection.INACTIVE

    /**
     * The negotiated ID of this RTP header extension.
     */
    private var ssrcAudioLevelExtID: Byte = -1

    /**
     * Initializes a new `SsrcTransformEngine` to be utilized by a specific `MediaStreamImpl`.
     *
     * mediaStream the `MediaStreamImpl` to utilize the new instance
     */
    init {
        /*
         * Take into account that RTPExtension.SSRC_AUDIO_LEVEL_URN may have already been activated.
         */
        val activeRTPExtensions = mediaStream.getActiveRTPExtensions()
        if (activeRTPExtensions != null && activeRTPExtensions.isNotEmpty()) {
            for ((extID, rtpExtension) in activeRTPExtensions) {
                val uri = rtpExtension.uri.toString()
                if (RTPExtension.SSRC_AUDIO_LEVEL_URN == uri) {
                    setSsrcAudioLevelExtensionID(extID ?: -1, rtpExtension.direction)
                }
            }
        }
        readConfigurationServicePropertiesOnce()

        // Audio levels are received in RTP audio streams only.
        csrcAudioLevelDispatcher = if (mediaStream is AudioMediaStreamImpl) {
            CsrcAudioLevelDispatcher(mediaStream)
        } else {
            null
        }
    }

    /**
     * Closes this `PacketTransformer` i.e. releases the resources allocated by it and
     * prepares it for garbage collection.
     */
    override fun close() {
        if (csrcAudioLevelDispatcher != null) csrcAudioLevelDispatcher!!.close()
    }

    /**
     * Always returns `null` since this engine does not require any RTCP transformations.
     *
     * @return `null` since this engine does not require any RTCP transformations.
     */
    override val rtcpTransformer: PacketTransformer?
        get() = null

    /**
     * Returns a reference to this class since it is performing RTP transformations in here.
     *
     * @return a reference to `this` instance of the `SsrcTransformEngine`.
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * Extracts the list of CSRC identifiers and passes it to the `MediaStream` associated
     * with this engine. Other than that the method does not do any transformations since CSRC
     * lists are part of RFC 3550 and they shouldn't be disrupting the rest of the application.
     *
     * @param pkt the RTP `RawPacket` that we are to extract a SSRC list from.
     * @return the same `RawPacket` that was received as a parameter since we don't need to
     * worry about hiding the SSRC list from the rest of the RTP stack.
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        var dropPkt = false
        if ((ssrcAudioLevelExtID > 0 && ssrcAudioLevelDirection.allowsReceiving()
                        && !pkt!!.isInvalid) && RTPHeader.VERSION == pkt.version) {
            val level = pkt.extractSsrcAudioLevel(ssrcAudioLevelExtID)
            if (level.toInt() == 127 /* a muted audio source */) {
                if (dropMutedAudioSourceInReverseTransform) {
                    dropPkt = true
                } else {
                    pkt.flags = Buffer.FLAG_SILENCE or pkt.flags
                }
            }

            /*
             * Notify the AudioMediaStream associated with this instance about the received audio level.
             */
            if (!dropPkt && csrcAudioLevelDispatcher != null && level >= 0) {
                val levels = LongArray(2)
                levels[0] = pkt.getSSRCAsLong()
                levels[1] = (127 - level).toLong()
                csrcAudioLevelDispatcher!!.addLevels(levels, pkt.timestamp)
            }
        }
        if (dropPkt) {
            pkt!!.flags = Buffer.FLAG_DISCARD or pkt.flags
        }
        return pkt
    }

    fun setSsrcAudioLevelExtensionID(extID: Byte, dir: MediaDirection) {
        ssrcAudioLevelExtID = extID
        ssrcAudioLevelDirection = dir
    }

    companion object {
        /**
         * The name of the `ConfigurationService` property which specifies whether
         * `SsrcTransformEngine` is to drop RTP packets indicated as generated from a muted
         * audio source in [.reverseTransform].
         */
        val DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM = SsrcTransformEngine::class.java.name + ".dropMutedAudioSourceInReverseTransform"

        /**
         * The indicator which determines whether `SsrcTransformEngine` is to drop RTP packets
         * indicated as generated from a muted audio source in [.reverseTransform].
         */
        private var dropMutedAudioSourceInReverseTransform = false

        /**
         * The indicator which determines whether the method
         * [.readConfigurationServicePropertiesOnce] is to read the values of certain
         * `ConfigurationService` properties of concern to `SsrcTransformEngine` once
         * during the initialization of the first instance.
         */
        private var readConfigurationServicePropertiesOnce = true

        /**
         * Reads the values of certain `ConfigurationService` properties of concern to
         * `SsrcTransformEngine` once during the initialization of the first instance.
         */
        @Synchronized
        private fun readConfigurationServicePropertiesOnce() {
            if (readConfigurationServicePropertiesOnce) readConfigurationServicePropertiesOnce = false else return
            val cfg = LibJitsi.configurationService
            if (cfg != null) {
                dropMutedAudioSourceInReverseTransform = cfg.getBoolean(
                        DROP_MUTED_AUDIO_SOURCE_IN_REVERSE_TRANSFORM,
                        dropMutedAudioSourceInReverseTransform)
            }
        }
    }
}