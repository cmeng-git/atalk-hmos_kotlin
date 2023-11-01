/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import org.atalk.impl.neomedia.control.AbstractControls
import org.atalk.impl.neomedia.protocol.InbandDTMFDataSource
import org.atalk.impl.neomedia.protocol.MuteDataSource
import org.atalk.service.neomedia.DTMFInbandTone
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import javax.media.CaptureDeviceInfo
import javax.media.Time
import javax.media.control.BufferControl
import javax.media.control.FormatControl
import javax.media.protocol.CaptureDevice
import javax.media.protocol.DataSource
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.PushBufferStream

/**
 * Represents a `PushBufferDataSource` which provides a single `PushBufferStream`
 * containing the result of the audio mixing of `DataSource`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioMixingPushBufferDataSource
/**
 * Initializes a new `AudioMixingPushBufferDataSource` instance which gives access to
 * the result of the audio mixing performed by a specific `AudioMixer`.
 *
 * @param audioMixer the `AudioMixer` performing audio mixing, managing the input
 * `DataSource`s and pushing the data of the new output `PushBufferDataSource`
 */
(
    /**
     * The `AudioMixer` performing the audio mixing, managing the input `DataSource`s
     * and pushing the data of this output `PushBufferDataSource`.
     */
    private val audioMixer: AudioMixer,
) : PushBufferDataSource(), CaptureDevice, MuteDataSource, InbandDTMFDataSource {

    /**
     * The indicator which determines whether this `DataSource` is connected.
     */
    private var connected = false

    /**
     * The indicator which determines whether this `DataSource` is set to transmit "silence"
     * instead of the actual media.
     */
    override var isMute = false

    /**
     * The one and only `PushBufferStream` this `PushBufferDataSource` provides to
     * its clients and containing the result of the audio mixing performed by `audioMixer`.
     */
    private var outStream: AudioMixingPushBufferStream? = null

    /**
     * The indicator which determines whether this `DataSource` is started.
     */
    private var started = false

    /**
     * The tones to send via inband DTMF, if not empty.
     */
    private val tones = LinkedList<DTMFInbandTone>()

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    override fun addDTMF(tone: DTMFInbandTone) {
        tones.add(tone)
    }

    /**
     * Adds a new input `DataSource` to be mixed by the associated `AudioMixer` of
     * this instance and to not have its audio contributions included in the mixing output
     * represented by this `DataSource`.
     *
     * @param inDataSource a `DataSource` to be added for mixing to the `AudioMixer` associate with
     * this instance and to not have its audio contributions included in the mixing output
     * represented by this `DataSource`
     */
    fun addInDataSource(inDataSource: DataSource?) {
        audioMixer.addInDataSource(inDataSource, this)
    }

    /**
     * Implements [DataSource.connect]. Lets the `AudioMixer` know that one of its
     * output `PushBufferDataSources` has been connected and marks this `DataSource` as connected.
     *
     * @throws IOException if the `AudioMixer` fails to connect
     */
    @Synchronized
    @Throws(IOException::class)
    override fun connect() {
        if (!connected) {
            audioMixer.connect()
            connected = true
        }
    }

    /**
     * Implements [DataSource.disconnect]. Marks this `DataSource` as disconnected
     * and notifies the `AudioMixer` that one of its output `PushBufferDataSources`
     * has been disconnected.
     */
    @Synchronized
    override fun disconnect() {
        try {
            stop()
        } catch (ioex: IOException) {
            throw UndeclaredThrowableException(ioex)
        }
        if (connected) {
            outStream = null
            connected = false
            audioMixer.disconnect()
        }
    }

    /**
     * Gets the `BufferControl` available for this `DataSource`. Delegates to the
     * `AudioMixer` because this instance is just a facet to it.
     *
     * @return the `BufferControl` available for this `DataSource`
     */
    private val bufferControl: BufferControl?
        get() = audioMixer.bufferControl

    /**
     * Implements [CaptureDevice.getCaptureDeviceInfo]. Delegates to the associated
     * `AudioMixer` because it knows which `CaptureDevice` is being wrapped.
     *
     * @return the `CaptureDeviceInfo` of the `CaptureDevice` of the `AudioMixer`
     */
    override fun getCaptureDeviceInfo(): CaptureDeviceInfo {
        return audioMixer.captureDeviceInfo
    }

    /**
     * Implements [DataSource.getContentType]. Delegates to the associated
     * `AudioMixer` because it manages the inputs and knows their characteristics.
     *
     * @return a `String` value which represents the type of the content being made
     * available by this `DataSource` i.e. the associated `AudioMixer`
     */
    override fun getContentType(): String {
        return audioMixer.contentType
    }

    /**
     * Implements [DataSource.getControl].
     *
     * @param controlType a `String` value which names the type of the control of this instance to be retrieved
     * @return an `Object` which represents the control of this instance with the specified
     * type if such a control is available; otherwise, `null`
     */
    override fun getControl(controlType: String): Any? {
        return AbstractControls.getControl(this, controlType)
    }

//    fun getControls2(): Array<out FormatControl?> {
//        val bufferControl = bufferControl
//        val formatControls = formatControls // as Array<out FormatControl>?
//
//        return if (bufferControl == null) {
//            formatControls
//        } else {
//            if (formatControls.isEmpty()) arrayOf<FormatControl>(bufferControl)
//            else {
//                val controls = arrayOfNulls<FormatControl>(1 + formatControls.size)
//                controls[0] = bufferControl
//                System.arraycopy(formatControls, 0, controls, 1, formatControls.size)
//                controls
//            }
//        }
//    }

    /**
     * Implements {@link DataSource#getControls()}. Gets an array of <code>Object</code>s which
     * represent the controls available for this <code>DataSource</code>.
     *
     * @return an array of <code>Object</code>s which represent the controls available for this <code>DataSource</code>
     */
    override fun getControls(): Array<Any?>? {
        val bufferControl = bufferControl
        val formatControls = formatControls as Array<Any?>?

        return when {
            bufferControl == null -> formatControls
            formatControls == null || formatControls.isEmpty() -> arrayOf(bufferControl)
            else -> {
                val controls = arrayOfNulls<Any>(1 + formatControls.size)
                controls[0] = bufferControl
                System.arraycopy(formatControls, 0, controls, 1, formatControls.size)
                controls
            }
        }
    }

    /**
     * Implements [DataSource.getDuration]. Delegates to the associated `AudioMixer`
     * because it manages the inputs and knows their characteristics.
     *
     * @return a `Time` value which represents the duration of the media being made
     * available through this `DataSource`
     */
    override fun getDuration(): Time {
        return audioMixer.duration
    }

    /**
     * Implements [CaptureDevice.getFormatControls]. Delegates to the associated
     * `AudioMixer` because it knows which `CaptureDevice` is being wrapped.
     *
     * @return an array of `FormatControl`s of the `CaptureDevice` of the associated `AudioMixer`
     */
    override fun getFormatControls(): Array<out FormatControl> {
        return audioMixer.formatControls
    }

    /**
     * Gets the next inband DTMF tone signal.
     *
     * @param sampleRate The sampling frequency (codec clock rate) in Hz of the stream which will encapsulate
     * this signal.
     * @param sampleSizeInBits The size of each sample (8 for a byte, 16 for a short and 32 for an int)
     * @return The data array containing the DTMF signal.
     */
    fun getNextToneSignal(sampleRate: Double, sampleSizeInBits: Int): ShortArray {
        return tones.poll()!!.getAudioSamples(sampleRate, sampleSizeInBits)
    }

    /**
     * Implements [PushBufferDataSource.getStreams]. Gets a `PushBufferStream` which
     * reads data from the associated `AudioMixer` and mixes its inputs.
     *
     * @return an array with a single `PushBufferStream` which reads data from the
     * associated `AudioMixer` and mixes its inputs if this `DataSource` is
     * connected; otherwise, an empty array
     */
    @Synchronized
    override fun getStreams(): Array<out PushBufferStream?> {
        if (connected && outStream == null) {
            val audioMixerOutStream = audioMixer.outStream
            if (audioMixerOutStream != null) {
                outStream = AudioMixingPushBufferStream(audioMixerOutStream, this)
                if (started) try {
                    outStream!!.start()
                } catch (ioex: IOException) {
                    Timber.e(ioex, "Failed to start %s  with hashCode %s",
                            outStream!!.javaClass.simpleName, outStream.hashCode())
                }
            }
        }
        return if (outStream == null) arrayOfNulls<PushBufferStream>(0) else arrayOf<PushBufferStream>(outStream!!)
    }

    /**
     * Determines whether this `DataSource` sends a DTMF tone.
     *
     * @return `true` if this `DataSource` is sending a DTMF tone; otherwise,
     * `false`.
     */
    val isSendingDTMF: Boolean
        get() = !tones.isEmpty()

    /**
     * Implements [DataSource.start]. Starts the output `PushBufferStream` of this
     * `DataSource` (if it exists) and notifies the `AudioMixer` that one of its
     * output `PushBufferDataSources` has been started.
     *
     * @throws IOException if anything wrong happens while starting the output `PushBufferStream` of this
     * `DataSource`
     */
    @Synchronized
    @Throws(IOException::class)
    override fun start() {
        if (!started) {
            started = true
            if (outStream != null) outStream!!.start()
        }
    }

    /**
     * Implements [DataSource.stop]. Notifies the `AudioMixer` that one of its
     * output `PushBufferDataSources` has been stopped and stops the output
     * `PushBufferStream` of this `DataSource` (if it exists).
     *
     * @throws IOException if anything wrong happens while stopping the output `PushBufferStream` of this
     * `DataSource`
     */
    @Synchronized
    @Throws(IOException::class)
    override fun stop() {
        if (started) {
            started = false
            if (outStream != null) outStream!!.stop()
        }
    }

    /**
     * The input `DataSource` has been updated.
     *
     * @param inDataSource the `DataSource` that was updated.
     */
    fun updateInDataSource(inDataSource: DataSource?) {
        // just update the input streams
        audioMixer.outStream
    }
}