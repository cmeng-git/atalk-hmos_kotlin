/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.protocol.OperationSetDTMF
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import org.apache.commons.lang3.StringUtils
import org.atalk.service.neomedia.DTMFMethod
import org.atalk.service.neomedia.codec.Constants
import org.atalk.util.MediaType

/**
 * Represents a default/base implementation of `OperationSetDTMF` which attempts to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific
 * functionality.
 *
 * @author Damian Minkov
 */
abstract class AbstractOperationSetDTMF(pps: ProtocolProviderService) : OperationSetDTMF {
    /**
     * The DTMF method used to send tones.
     */
    protected var dtmfMethod: DTMFMethod

    /**
     * The minimal tone duration.
     */
    protected var minimalToneDuration: Int

    /**
     * The maximal tone duration.
     */
    protected var maximalToneDuration: Int

    /**
     * The tone volume.
     */
    protected var volume: Int

    /**
     * Creates the `AbstractOperationSetDTMF` and initialize some settings.
     *
     * @param pps the protocol provider.
     */
    init {
        dtmfMethod = getDTMFMethod(pps)
        minimalToneDuration = getMinimalToneDurationSetting(pps)
        maximalToneDuration = getMaximalToneDurationSetting()
        volume = getVolumeSetting(pps)
    }

    companion object {
        /**
         * Gets the minimal DTMF tone duration for this account.
         *
         * @param pps the Protocol provider service
         * @return The minimal DTMF tone duration for this account.
         */
        private fun getMinimalToneDurationSetting(pps: ProtocolProviderService): Int {
            val accountID = pps.accountID
            val minimalToneDurationString = accountID.getAccountPropertyString("DTMF_MINIMAL_TONE_DURATION")
            var minimalToneDuration = OperationSetDTMF.DEFAULT_DTMF_MINIMAL_TONE_DURATION
            // Check if there is a specific value for this account.
            if (StringUtils.isNotEmpty(minimalToneDurationString)) {
                minimalToneDuration = Integer.valueOf(minimalToneDurationString!!)
            } else {
                val cfg = ProtocolProviderActivator.getConfigurationService()
                // Check if there is a custom value for the minimal tone duration.
                if (cfg != null) {
                    minimalToneDuration = cfg.getInt(
                            OperationSetDTMF.PROP_MINIMAL_RTP_DTMF_TONE_DURATION, minimalToneDuration)
                }
            }
            return minimalToneDuration
        }

        /**
         * Gets the maximal DTMF tone duration for this account.
         *
         * @return The maximal DTMF tone duration for this account.
         */
        private fun getMaximalToneDurationSetting(): Int {
            var maximalToneDuration = OperationSetDTMF.DEFAULT_DTMF_MAXIMAL_TONE_DURATION

            // Look at the global property.
            val cfg = ProtocolProviderActivator.getConfigurationService()
            // Check if there is a custom value for the maximal tone duration.
            if (cfg != null) {
                maximalToneDuration = cfg.getInt(OperationSetDTMF.PROP_MAXIMAL_RTP_DTMF_TONE_DURATION,
                        maximalToneDuration)
            }
            return maximalToneDuration
        }

        /**
         * Returns the corresponding DTMF method used for this account.
         *
         * @param pps the Protocol provider service
         * @return the DTMFEnum corresponding to the DTMF method set for this account.
         */
        private fun getDTMFMethod(pps: ProtocolProviderService): DTMFMethod {
            val accountID = pps.accountID
            var dtmfString = accountID.getAccountPropertyString("DTMF_METHOD")

            // Verifies that the DTMF_METHOD property string is correctly set.
            // If not, sets this account to the "auto" DTMF method and corrects the
            // property string.
            if (dtmfString == null
                    || (dtmfString != "AUTO_DTMF" && dtmfString != "RTP_DTMF"
                            && dtmfString != "SIP_INFO_DTMF" && dtmfString != "INBAND_DTMF")) {
                dtmfString = "AUTO_DTMF"
                accountID.putAccountProperty("DTMF_METHOD", dtmfString)
            }
            return if (dtmfString == "AUTO_DTMF") {
                DTMFMethod.AUTO_DTMF
            } else if (dtmfString == "RTP_DTMF") {
                DTMFMethod.RTP_DTMF
            } else if (dtmfString == "SIP_INFO_DTMF") {
                DTMFMethod.SIP_INFO_DTMF
            } else  // if(dtmfString.equals(INBAND_DTMF"))
            {
                DTMFMethod.INBAND_DTMF
            }
        }

        /**
         * Checks whether rfc4733 is negotiated for this call.
         *
         * @param peer the call peer.
         * @return whether we can use rfc4733 in this call.
         */
        @JvmStatic
        protected fun isRFC4733Active(peer: MediaAwareCallPeer<*, *, *>): Boolean {
            val iter = peer.mediaHandler!!.getStream(MediaType.AUDIO)!!
                    .getDynamicRTPPayloadTypes().values.iterator()
            while (iter.hasNext()) {
                val mediaFormat = iter.next()
                if (Constants.TELEPHONE_EVENT == mediaFormat.encoding) return true
            }
            return false
        }

        /**
         * Gets the DTMF tone volume for this account.
         *
         * @return The DTMF tone volume for this account.
         */
        private fun getVolumeSetting(pps: ProtocolProviderService): Int {
            val accountID = pps.accountID
            val volumeString = accountID.getAccountPropertyString("DTMF_TONE_VOLUME")
            var vol = OperationSetDTMF.DEFAULT_DTMF_TONE_VOLUME
            // Check if there is a specific value for this account.
            if (StringUtils.isNotEmpty(volumeString)) {
                vol = volumeString!!.toInt()
            }
            return vol
        }
    }
}