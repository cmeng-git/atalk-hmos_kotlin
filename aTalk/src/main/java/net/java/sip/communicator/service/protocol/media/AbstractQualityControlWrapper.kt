/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.impl.protocol.jabber.CallPeerJabberImpl
import org.atalk.service.neomedia.MediaException
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.QualityControl
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.VideoMediaStream
import org.atalk.util.MediaType

/**
 * A wrapper of media quality control.
 *
 * @param <T> `MediaAwareCallPeer`
 * @author Damian Minkov
 * @author Sebastien Vincent
</T> */
abstract class AbstractQualityControlWrapper<T : CallPeerJabberImpl>
/**
 * Creates quality control for peer.
 *
 * @param peer
 */
protected constructor(
        /**
         * The peer we are controlling.
         */
        protected val peer: T,
) : QualityControl {

    /**
     * The media quality control.
     */
    private var qualityControl: QualityControl? = null

    /**
     * The currently used video quality preset.
     */
    private var remoteSendMaxPreset: QualityPreset? = null

    /**
     * The frame rate.
     */
    private var maxFrameRate = -1f

    /**
     * Checks and obtains quality control from media stream.
     *
     * @return
     */
    protected fun getMediaQualityControl(): QualityControl? {
        if (qualityControl != null) return qualityControl

        val stream: MediaStream? = peer.mediaHandler!!.getStream(MediaType.VIDEO)
        if (stream is VideoMediaStream)
            qualityControl = (stream as VideoMediaStream?)!!.qualityControl

        return qualityControl
    }

    override fun getRemoteReceivePreset(): QualityPreset? {
        return getMediaQualityControl()?.getRemoteReceivePreset()
    }

    /**
     * The minimum preset that the remote party is sending and we are receiving. Not Used.
     */
    override fun getRemoteSendMinPreset(): QualityPreset? {
        return getMediaQualityControl()?.getRemoteSendMinPreset()
    }

    /**
     * The maximum preset that the remote party is sending and we are receiving.
     */
    override fun getRemoteSendMaxPreset(): QualityPreset? {
        val qControls = getMediaQualityControl() ?: return remoteSendMaxPreset
        var qp = qControls.getRemoteSendMaxPreset()

        // there is info about max frame rate
        if (qp != null && maxFrameRate > 0)
            qp = QualityPreset(qp.resolution, maxFrameRate.toInt().toFloat())

        return qp
    }

    /**
     * Changes local value of frame rate, the one we have received from remote party.
     *
     * @param f new frame rate.
     */
    fun setMaxFrameRate(f: Float) {
        maxFrameRate = f
    }

    /**
     * Changes remote send preset. This doesn't have impact of current stream. But will have on next
     * media changes. With this we can try to change the resolution that the remote part is sending.
     *
     * @param preset the new preset value.
     */
    override fun setRemoteSendMaxPreset(preset: QualityPreset) {
        val qControls = getMediaQualityControl()

        if (qControls != null)
            qControls.setRemoteSendMaxPreset(preset)
        else
            remoteSendMaxPreset = preset
    }

    /**
     * Changes the current video settings for the peer with the desired quality settings and inform
     * the peer to stream the video with those settings.
     *
     * @param preset the desired video settings
     * @throws MediaException
     */
    @Throws(MediaException::class)
    abstract override fun setPreferredRemoteSendMaxPreset(preset: QualityPreset)
}