/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.media.AbstractQualityControlWrapper
import org.atalk.service.neomedia.QualityPreset
import org.jivesoftware.smack.SmackException.NotConnectedException
import timber.log.Timber

/**
 * A wrapper of media quality control.
 *
 * @author Damian Minkov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class QualityControlWrapper
/**
 * Creates quality control for peer.
 *
 * @param peer peer
 */
internal constructor(peer: CallPeerJabberImpl) : AbstractQualityControlWrapper<CallPeerJabberImpl>(peer) {
    /**
     * Changes the current video settings for the peer with the desired quality settings and inform
     * the peer to stream the video with those settings.
     *
     * @param preset the desired video settings
     */
    override fun setPreferredRemoteSendMaxPreset(preset: QualityPreset) {
        val qControls = getMediaQualityControl()
        if (qControls != null) {
            qControls.setRemoteSendMaxPreset(preset)

            // re-invites the peer with the new settings
            try {
                peer.sendModifyVideoResolutionContent()
            } catch (e: NotConnectedException) {
                Timber.e(e, "Could not send modify video resolution of peer")
            } catch (e: InterruptedException) {
                Timber.e(e, "Could not send modify video resolution of peer")
            }
        }
    }
}