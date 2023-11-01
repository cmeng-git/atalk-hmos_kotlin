/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.conference.AudioMixer
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.RTPExtension
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import timber.log.Timber
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import javax.media.CaptureDeviceInfo
import javax.media.Format
import javax.media.Renderer
import javax.media.control.BufferControl
import javax.media.format.AudioFormat
import javax.media.protocol.CaptureDevice
import javax.media.protocol.DataSource

/**
 * Extends `MediaDeviceImpl` with audio-specific functionality.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class AudioMediaDeviceImpl : MediaDeviceImpl {
    /**
     * The `AudioMixer` which enables sharing an exclusive `CaptureDevice` such as
     * JavaSound between multiple `CaptureDevice` users.
     */
    private var captureDeviceSharing: AudioMixer? = null

    /**
     * The `List` of RTP extensions supported by this device (at the time of writing this
     * list is only filled for audio devices and is `null` otherwise).
     */
    private var rtpExtensions: MutableList<RTPExtension>? = null

    /**
     * Initializes a new `AudioMediaDeviceImpl` instance which represents a `MediaDevice` with
     * `MediaType` `AUDIO` and a `MediaDirection` which does not allow sending.
     */
    constructor() : super(MediaType.AUDIO) {}

    /**
     * Initializes a new `AudioMediaDeviceImpl` which is to provide an implementation of
     * `MediaDevice` with `MediaType` `AUDIO` to a `CaptureDevice` with a specific `CaptureDeviceInfo`.
     *
     * @param captureDeviceInfo the `CaptureDeviceInfo` of the `CaptureDevice` to which the new instance
     * is to provide an implementation of `MediaDevice`
     */
    constructor(captureDeviceInfo: CaptureDeviceInfo?) : super(captureDeviceInfo, MediaType.AUDIO) {}

    /**
     * Connects to a specific `CaptureDevice` given in the form of a `DataSource`.
     *
     * @param captureDevice the `CaptureDevice` to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified `captureDevice`
     * @see AbstractMediaDevice.connect
     */
    @Throws(IOException::class)
    override fun connect(captureDevice: DataSource) {
        super.connect(captureDevice)

        /*
         * 1. Changing the buffer length to 30 ms. The default buffer size (for JavaSound) is
         * 125 ms (i.e. 1/8 sec). On Mac OS X, this leads to an exception and no audio capture. A
         * value of 30 ms for the buffer length fixes the problem and is OK when using some PSTN gateways.
         *
         * 2. Changing to 60 ms. When it is 30 ms, there are some issues with Asterisk and NAT (we don't start to
         * send a/the stream and Asterisk's RTP functionality doesn't notice that we're behind NAT).
         *
         * 3. Do not set buffer length on Linux as it completely breaks audio capture.
         */
        if (!OSUtils.IS_LINUX) {
            val bufferControl = captureDevice.getControl(BufferControl::class.java.name) as BufferControl?
            if (bufferControl != null) bufferControl.bufferLength = 60
        }
    }

    /**
     * Creates the JMF `CaptureDevice` this instance represents and provides an implementation of `MediaDevice` for.
     *
     * @return the JMF `CaptureDevice` this instance represents and provides an
     * implementation of `MediaDevice` for; `null` if the creation fails
     */
    @Synchronized
    public override fun createCaptureDevice(): CaptureDevice {
        var captureDevice: CaptureDevice? = null
        if (direction.allowsSending()) {
            if (captureDeviceSharing == null) {
                val protocol = getCaptureDeviceInfoLocatorProtocol()
                var createCaptureDeviceIfNull = true
                if (AudioSystem.LOCATOR_PROTOCOL_JAVASOUND.equals(protocol, ignoreCase = true)
                        || AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO.equals(protocol, ignoreCase = true)) {
                    captureDevice = superCreateCaptureDevice()
                    createCaptureDeviceIfNull = false
                    if (captureDevice != null) {
                        captureDeviceSharing = createCaptureDeviceSharing(captureDevice)
                        captureDevice = captureDeviceSharing!!.createOutDataSource()
                    }
                }
                if (captureDevice == null && createCaptureDeviceIfNull) captureDevice = superCreateCaptureDevice()
            } else captureDevice = captureDeviceSharing!!.createOutDataSource()
        }
        return captureDevice!!
    }

    /**
     * Creates a new `AudioMixer` which is to enable the sharing of a specific explicit `CaptureDevice`
     *
     * @param captureDevice an exclusive `CaptureDevice` for which sharing is to be enabled
     * @return a new `AudioMixer` which enables the sharing of the specified exclusive `captureDevice`
     */
    private fun createCaptureDeviceSharing(captureDevice: CaptureDevice): AudioMixer {
        return object : AudioMixer(captureDevice) {
            @Throws(IOException::class)
            override fun connect(dataSource: DataSource, inDataSource: DataSource) {
                /*
                 * CaptureDevice needs special connecting as defined by AbstractMediaDevice and,
                 * especially, MediaDeviceImpl.
                 */
                when (inDataSource) {
                    captureDevice -> this@AudioMediaDeviceImpl.connect(dataSource)
                    else -> super.connect(dataSource, inDataSource)
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * Tries to delegate the initialization of a new `Renderer` instance to the
     * `AudioSystem` which provides the `CaptureDevice` of this instance. This way
     * both the capture and the playback are given a chance to happen within the same
     * `AudioSystem`. If the discovery of the delegate fails, the implementation of
     * `MediaDeviceImpl` is executed and it currently leaves it to FMJ to choose a
     * `Renderer` irrespective of this `MediaDevice`.
     */
    override fun createRenderer(): Renderer? {
        var renderer: Renderer? = null
        try {
            val locatorProtocol = getCaptureDeviceInfoLocatorProtocol()
            if (locatorProtocol != null) {
                val audioSystem = AudioSystem.getAudioSystem(locatorProtocol)
                if (audioSystem != null) renderer = audioSystem.createRenderer(true)
            }
        } finally {
            if (renderer == null)
                renderer = super.createRenderer()
        }
        return renderer
    }
    // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and never changes.

    /**
     * Returns a `List` containing extension descriptor indicating `RECVONLY` support for mixer-to-client audio
     * levels, and extension descriptor indicating `SENDRECV` support for client-to-mixer audio levels.
     * We add the ssrc audio levels as first element, in order when making offer
     * to be the first one (id 1) as some other systems have this hardcoded it as 1 (jicofo).
     *
     * @return a `List` containing the `CSRC_AUDIO_LEVEL_URN` and `SSRC_AUDIO_LEVEL_URN` extension descriptor.
     */
    override val supportedExtensions: List<RTPExtension>
        get() {
            if (rtpExtensions == null) {
                rtpExtensions = ArrayList(1)
                var ssrcAudioLevelURN: URI?
                var csrcAudioLevelURN: URI?
                try {
                    ssrcAudioLevelURN = URI(RTPExtension.SSRC_AUDIO_LEVEL_URN)
                    csrcAudioLevelURN = URI(RTPExtension.CSRC_AUDIO_LEVEL_URN)
                } catch (e: URISyntaxException) {
                    // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and never changes.
                    Timber.i(e, "Aha! Someone messed with the source!")
                    ssrcAudioLevelURN = null
                    csrcAudioLevelURN = null
                }
                if (ssrcAudioLevelURN != null) {
                    rtpExtensions!!.add(RTPExtension(ssrcAudioLevelURN, MediaDirection.SENDRECV))
                }
                if (csrcAudioLevelURN != null) {
                    rtpExtensions!!.add(RTPExtension(csrcAudioLevelURN, MediaDirection.RECVONLY))
                }
            }
            return rtpExtensions!!
        }

    private fun isLessThanOrEqualToMaxAudioFormat(format: Format): Boolean {
        if (format is AudioFormat) {
            val audioFormat = format
            val channels = audioFormat.channels
            if (channels == Format.NOT_SPECIFIED || MediaUtils.MAX_AUDIO_CHANNELS == Format.NOT_SPECIFIED || channels <= MediaUtils.MAX_AUDIO_CHANNELS) {
                val sampleRate = audioFormat.sampleRate
                if (sampleRate == Format.NOT_SPECIFIED.toDouble()
                        || MediaUtils.MAX_AUDIO_SAMPLE_RATE == Format.NOT_SPECIFIED.toDouble()
                        || sampleRate <= MediaUtils.MAX_AUDIO_SAMPLE_RATE) {
                    val sampleSizeInBits = audioFormat.sampleSizeInBits
                    return sampleSizeInBits == Format.NOT_SPECIFIED
                            || MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS == Format.NOT_SPECIFIED
                            || sampleSizeInBits <= MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS
                }
            }
        }
        return false
    }

    /**
     * Invokes the super (with respect to the `AudioMediaDeviceImpl` class)
     * implementation of [MediaDeviceImpl.createCaptureDevice]. Allows this instance to
     * customize the very `CaptureDevice` which is to be possibly further wrapped by this instance.
     *
     * @return the `CaptureDevice` returned by the call to the super implementation of
     * `MediaDeviceImpl#createCaptureDevice`.
     */
    private fun superCreateCaptureDevice(): CaptureDevice? {
        val captureDevice = super.createCaptureDevice()
        if (captureDevice != null) {
            /*
             * Try to default the captureDevice to a Format which does not exceed the maximum
             * quality known to MediaUtils.
             */
            try {
                val formatControls = captureDevice.formatControls
                if (formatControls != null && formatControls.isNotEmpty()) {
                    for (formatControl in formatControls) {
                        val format = formatControl.format
                        if (isLessThanOrEqualToMaxAudioFormat(format)) continue
                        val supportedFormats = formatControl.supportedFormats
                        lateinit var supportedFormatToSet: AudioFormat
                        if (supportedFormats != null && supportedFormats.isNotEmpty()) {
                            for (supportedFormat in supportedFormats) {
                                if (isLessThanOrEqualToMaxAudioFormat(supportedFormat)) {
                                    supportedFormatToSet = supportedFormat as AudioFormat
                                    break
                                }
                            }
                        }
                        if (!supportedFormatToSet.matches(format)) {
                            var channels = supportedFormatToSet.channels
                            var sampleRate = supportedFormatToSet.sampleRate
                            var sampleSizeInBits = supportedFormatToSet.sampleSizeInBits
                            if (channels == Format.NOT_SPECIFIED) channels = MediaUtils.MAX_AUDIO_CHANNELS
                            if (sampleRate == Format.NOT_SPECIFIED.toDouble()) sampleRate = MediaUtils.MAX_AUDIO_SAMPLE_RATE
                            if (sampleSizeInBits == Format.NOT_SPECIFIED) {
                                sampleSizeInBits = MediaUtils.MAX_AUDIO_SAMPLE_SIZE_IN_BITS
                                /*
                                 * TODO A great deal of the neomedia-contributed audio Codecs,
                                 * CaptureDevices, DataSources and Renderers deal with 16-bit
                                 * samples.
                                 */
                                if (sampleSizeInBits == Format.NOT_SPECIFIED) sampleSizeInBits = 16
                            }
                            if (channels != Format.NOT_SPECIFIED && sampleRate != Format.NOT_SPECIFIED.toDouble() && sampleSizeInBits != Format.NOT_SPECIFIED) {
                                val formatToSet = AudioFormat(
                                        supportedFormatToSet.encoding, sampleRate,
                                        sampleSizeInBits, channels)
                                if (supportedFormatToSet.matches(formatToSet)) formatControl.format = supportedFormatToSet.intersects(formatToSet)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t
                /*
                 * We tried to default the captureDevice to a Format which does not exceed the
                 * maximum quality known to MediaUtils and we failed but it does not mean that the
                 * captureDevice will not be successfully used.
                 */
            }
        }
        return captureDevice
    }
}