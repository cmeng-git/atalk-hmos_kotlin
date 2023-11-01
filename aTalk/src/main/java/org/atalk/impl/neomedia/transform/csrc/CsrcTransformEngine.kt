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
import org.atalk.impl.neomedia.transform.SinglePacketTransformer
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ConfigUtils

/**
 * We use this engine to add the list of CSRC identifiers in RTP packets that we send to conference
 * participants during calls where we are the mixer.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class CsrcTransformEngine(
        /**
         * The `MediaStreamImpl` that this transform engine was created to transform packets for.
         */
        private val mediaStream: MediaStreamImpl) : SinglePacketTransformer(), TransformEngine {
    /**
     * The direction that we are supposed to handle audio levels in.
     */
    private var csrcAudioLevelDirection = MediaDirection.INACTIVE

    /**
     * The number currently assigned to CSRC audio level extensions or `-1` if no such ID
     * has been set and audio level extensions should not be transmitted.
     */
    private var csrcAudioLevelExtID: Byte = -1

    /**
     * The dispatcher that is delivering audio levels to the media steam.
     */
    private var csrcAudioLevelDispatcher: CsrcAudioLevelDispatcher? = null

    /**
     * The buffer that we use to encode the csrc audio level extensions.
     */
    private var extensionBuff: ByteArray? = null

    /**
     * Indicates the length that we are currently using in the `extensionBuff` buffer.
     */
    private var extensionBuffLen = 0

    /**
     * Creates an engine instance that will be adding CSRC lists to the specified `stream`.
     *
     * mediaStream that `MediaStream` whose RTP packets we are going to be adding CSRC lists. to
     */
    init {

        /*
         * Take into account that RTPExtension.CSRC_AUDIO_LEVEL_URN may have already been activated.
         */
        val activeRTPExtensions = mediaStream.getActiveRTPExtensions()
        if (activeRTPExtensions.isNotEmpty()) {
            for ((extID, rtpExtension) in activeRTPExtensions) {
                val uri = rtpExtension.uri.toString()
                if (RTPExtension.CSRC_AUDIO_LEVEL_URN == uri) {
                    setCsrcAudioLevelExtensionID(
                            extID ?: -1, rtpExtension.direction)
                }
            }
        }

        // Audio levels are received in RTP audio streams only.
        csrcAudioLevelDispatcher = if (mediaStream is AudioMediaStreamImpl) {
            CsrcAudioLevelDispatcher(
                    mediaStream)
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
     * Creates a audio level extension buffer containing the level extension header and the audio
     * levels corresponding to (and in the same order as) the `CSRC` IDs in the `csrcList`
     *
     * @param csrcList the list of CSRC IDs whose level we'd like the extension to contain.
     * @return the extension buffer in the form that it should be added to the RTP packet.
     */
    private fun createLevelExtensionBuffer(csrcList: LongArray): ByteArray {
        val extensionBuff = getExtensionBuff(csrcList.size)
        for (i in csrcList.indices) {
            val csrc = csrcList[i]
            val level = (mediaStream as AudioMediaStreamImpl).getLastMeasuredAudioLevel(csrc).toByte()
            extensionBuff[i] = level
        }
        return extensionBuff
    }

    /**
     * Returns a reusable byte array which is guaranteed to have the requested
     * `ensureCapacity` length and sets our internal length keeping var.
     *
     * @param ensureCapacity the minimum length that we need the returned buffer to have.
     * @return a reusable `byte[]` array guaranteed to have a length equal to or greater
     * than `ensureCapacity`.
     */
    private fun getExtensionBuff(ensureCapacity: Int): ByteArray {
        if (extensionBuff == null || extensionBuff!!.size < ensureCapacity) extensionBuff = ByteArray(ensureCapacity)
        extensionBuffLen = ensureCapacity
        return extensionBuff!!
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
     * @return a reference to `this` instance of the `CsrcTransformEngine`.
     */
    override val rtpTransformer: PacketTransformer
        get() = this

    /**
     * Extracts the list of CSRC identifiers and passes it to the `MediaStream` associated
     * with this engine. Other than that the method does not do any transformations since CSRC
     * lists are part of RFC 3550 and they shouldn't be disrupting the rest of the application.
     *
     * @param pkt the RTP `RawPacket` that we are to extract a CSRC list from.
     * @return the same `RawPacket` that was received as a parameter since we don't need to
     * worry about hiding the CSRC list from the rest of the RTP stack.
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        if (csrcAudioLevelExtID > 0 && csrcAudioLevelDirection.allowsReceiving() && csrcAudioLevelDispatcher != null) {
            // extract the audio levels and send them to the dispatcher.
            val levels = pkt!!.extractCsrcAudioLevels(csrcAudioLevelExtID)
            if (levels != null) csrcAudioLevelDispatcher!!.addLevels(levels, pkt.timestamp)
        }
        return pkt
    }

    /**
     * Sets the ID that this transformer should be using for audio level extensions or disables
     * audio level extensions if `extID` is `-1`.
     *
     * @param extID ID that this transformer should be using for audio level extensions or `-1` if
     * audio level extensions should be disabled
     * @param dir the direction that we are expected to hand this extension in.
     */
    fun setCsrcAudioLevelExtensionID(extID: Byte, dir: MediaDirection) {
        csrcAudioLevelExtID = extID
        csrcAudioLevelDirection = dir
    }

    /**
     * Extracts the list of CSRC identifiers representing participants currently contributing to
     * the media being sent by the `MediaStream` associated with this engine and (unless the
     * list is empty) encodes them into the `RawPacket`.
     *
     * @param pkt the RTP `RawPacket` that we need to add a CSRC list to.
     * @return the updated `RawPacket` instance containing the list of CSRC identifiers.
     */
    @Synchronized
    override fun transform(pkt: RawPacket): RawPacket {
        // Only transform RTP packets (and not ZRTP/DTLS, etc)
        if (pkt.version != RTPHeader.VERSION) return pkt
        val csrcList = mediaStream.localContributingSourceIDs
        if (csrcList == null || csrcList.isEmpty() || discardContributingSrcs) {
            // nothing to do.
            return pkt
        }
        pkt.setCsrcList(csrcList)

        // attach audio levels if we are expected to do so.
        if (csrcAudioLevelExtID > 0 && csrcAudioLevelDirection.allowsSending()
                && mediaStream is AudioMediaStreamImpl) {
            val levelsExt = createLevelExtensionBuffer(csrcList)
            pkt.addExtension(csrcAudioLevelExtID, levelsExt, extensionBuffLen)
        }
        return pkt
    }

    companion object {
        /**
         * The flag that determines whether the list of CSRC identifiers are to be
         * discarded in all packets. The CSRC count will be 0 as well. The default
         * value is `false`.
         */
        private var discardContributingSrcs = false

        /**
         * The name of the `ConfigurationService` and/or `System`
         * property which indicates whether the list of CSRC identifiers are to
         * be discarded from all packets. The default value is `false`.
         */
        private val DISCARD_CONTRIBUTING_SRCS_PNAME = CsrcTransformEngine::class.java.name + ".DISCARD_CONTRIBUTING_SOURCES"

        init {
            val cfg = LibJitsi.configurationService
            discardContributingSrcs = ConfigUtils.getBoolean(cfg, DISCARD_CONTRIBUTING_SRCS_PNAME, false)
        }
    }
}