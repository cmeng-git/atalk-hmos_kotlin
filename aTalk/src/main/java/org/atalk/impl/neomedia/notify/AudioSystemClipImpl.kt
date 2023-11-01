/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.notify

import org.atalk.impl.neomedia.codec.audio.speex.SpeexResampler
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.service.audionotifier.AbstractSCAudioClip
import org.atalk.service.audionotifier.AudioNotifierService
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import javax.media.Buffer
import javax.media.Codec
import javax.media.Format
import javax.media.Renderer
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implementation of SCAudioClip using device AudioSystem.
 *
 * @author Damyian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioSystemClipImpl
/**
 * Creates the audio clip and initializes the listener used from the loop timer.
 *
 * @param url the URL pointing to the audio file
 * @param audioNotifier the audio notify service
 * @param playback to use playback or notification device
 * @throws IOException cannot audio clip with supplied URL.
 */
(url: String, audioNotifier: AudioNotifierService, private val audioSystem: AudioSystem, private val playback: Boolean) : AbstractSCAudioClip(url, audioNotifier) {
    private var buffer: Buffer? = null
    private var bufferData: ByteArray? = null
    private var renderer: Renderer? = null

    /**
     * {@inheritDoc}
     */
    override fun enterRunInPlayThread() {
        buffer = Buffer()
        bufferData = ByteArray(DEFAULT_BUFFER_DATA_LENGTH)
        buffer!!.data = bufferData
        renderer = audioSystem.createRenderer(playback)
    }

    /**
     * {@inheritDoc}
     */
    override fun exitRunInPlayThread() {
        buffer = null
        bufferData = null
        renderer = null
    }

    /**
     * {@inheritDoc}
     */
    override fun exitRunOnceInPlayThread() {
        try {
            renderer?.stop()
        } finally {
            renderer?.close()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun runOnceInPlayThread(): Boolean {
        if (renderer == null || buffer == null) {
            return false
        }

        var audioStream: InputStream? = null
        try {
            audioStream = audioSystem.getAudioInputStream(uri)
        } catch (ioex: IOException) {
            Timber.e(ioex, "Failed to get audio stream %s", uri)
        }
        if (audioStream == null)
            return false

        var resampler: Codec? = null
        var success = true
        var audioStreamFormat: AudioFormat? = null
        var audioStreamLength = 0
        var rendererProcessStartTime = 0L
        try {
            audioStreamFormat = audioSystem.getFormat(audioStream)
            var rendererFormat: Format? = audioStreamFormat ?: return false

            var resamplerFormat: Format? = null
            if (renderer!!.setInputFormat(rendererFormat) == null) {
                /*
                 * Try to negotiate a resampling of the audioStream to one of the formats supported
                 * by the renderer.
                 */
                resampler = SpeexResampler()
                resamplerFormat = rendererFormat
                resampler.setInputFormat(resamplerFormat!!)

                val supportedResamplerFormats = resampler.getSupportedOutputFormats(resamplerFormat)
                for (supportedRendererFormat in renderer!!.supportedInputFormats) {
                    for (supportedResamplerFormat in supportedResamplerFormats) {
                        if (supportedRendererFormat.matches(supportedResamplerFormat)) {
                            rendererFormat = supportedRendererFormat
                            resampler.setOutputFormat(rendererFormat)
                            renderer!!.setInputFormat(rendererFormat)
                            break
                        }
                    }
                }
            }

            val rendererBuffer = buffer
            val resamplerBuffer: Buffer?
            rendererBuffer!!.format = rendererFormat
            if (resampler == null)
                resamplerBuffer = null
            else {
                resamplerBuffer = Buffer()
                var bufferDataLength = DEFAULT_BUFFER_DATA_LENGTH
                if (resamplerFormat is AudioFormat) {
                    val af = resamplerFormat
                    val frameSize = af.sampleSizeInBits / 8 * af.channels
                    bufferDataLength = bufferDataLength / frameSize * frameSize
                }
                bufferData = ByteArray(bufferDataLength)
                resamplerBuffer.data = bufferData
                resamplerBuffer.format = resamplerFormat
                resampler.open()
            }

            try {
                renderer!!.open()
                renderer!!.start()

                var bufferLength = 0
                while (isStarted && audioStream.read(bufferData).also { bufferLength = it } != -1) {
                    audioStreamLength += bufferLength
                    if (resampler == null) {
                        rendererBuffer.length = bufferLength
                        rendererBuffer.offset = 0
                    } else {
                        resamplerBuffer!!.length = bufferLength
                        resamplerBuffer.offset = 0
                        rendererBuffer.length = 0
                        rendererBuffer.offset = 0
                        resampler.process(resamplerBuffer, rendererBuffer)
                    }

                    var rendererProcess: Int
                    if (rendererProcessStartTime == 0L)
                        rendererProcessStartTime = System.currentTimeMillis()
                    do {
                        rendererProcess = renderer!!.process(rendererBuffer)
                        if (rendererProcess == Renderer.BUFFER_PROCESSED_FAILED) {
                            Timber.e("Failed to render audio stream %s", uri)
                            success = false
                            break
                        }
                    } while (rendererProcess and Renderer.INPUT_BUFFER_NOT_CONSUMED
                            == Renderer.INPUT_BUFFER_NOT_CONSUMED)
                }
            } catch (ioex: IOException) {
                Timber.e(ioex, "Failed to read from audio stream %s", uri)
                success = false
            } catch (ruex: ResourceUnavailableException) {
                Timber.e(ruex, "Failed to open %s", renderer!!.javaClass.name)
                success = false
            }
        } catch (ruex: ResourceUnavailableException) {
            if (resampler != null) {
                Timber.e("ruex, Failed to open %s", resampler.javaClass.name)
                success = false
            }
        } finally {
            try {
                audioStream.close()
            } catch (ioex: IOException) {
                /*
                 * The audio stream failed to close but it doesn't mean the URL will fail to open
                 * again so ignore the exception.
                 */
            }
            resampler?.close()

            /*
             * XXX We do not know whether the Renderer implementation of the stop method will wait
             * for the playback to complete.
             */
            if (success
                    && audioStreamFormat != null
                    && audioStreamLength > 0
                    && rendererProcessStartTime > 0
                    && isStarted) {
                var audioStreamDuration = (audioStreamFormat.computeDuration(audioStreamLength.toLong()) + 999999) / 1000000
                if (audioStreamDuration > 0) {
                    /*
                     * XXX The estimation is not accurate because we do not know, for example, how
                     * much the Renderer may be buffering before it starts the playback.
                     */
                    audioStreamDuration += MIN_AUDIO_STREAM_DURATION
                    var interrupted = false
                    synchronized(sync) {
                        while (isStarted) {
                            val timeout = System.currentTimeMillis() - rendererProcessStartTime
                            if (timeout >= audioStreamDuration || timeout <= 0) {
                                break
                            } else {
                                try {
                                    sync.wait(timeout)
                                } catch (ie: InterruptedException) {
                                    interrupted = true
                                }
                            }
                        }
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt()
                }
            }
        }
        return success
    }

    companion object {
        /**
         * The default length of [.bufferData].
         */
        private const val DEFAULT_BUFFER_DATA_LENGTH = 8 * 1024

        /**
         * The minimum duration in milliseconds to be assumed for the audio streams played by
         * `AudioSystemClipImpl` in order to ensure that they are played back long enough to be heard.
         */
        private const val MIN_AUDIO_STREAM_DURATION = 200L
    }
}