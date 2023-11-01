/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.portaudio

import org.atalk.impl.neomedia.device.*
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream
import timber.log.Timber
import java.io.IOException
import javax.media.Format
import javax.media.MediaLocator
import javax.media.control.FormatControl

/**
 * Implements `DataSource` and `CaptureDevice` for PortAudio.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DataSource : AbstractPullBufferCaptureDevice {
    /**
     * The indicator which determines whether this `DataSource` will use audio quality
     * improvement in accord with the preferences of the user.
     */
    private val audioQualityImprovement: Boolean

    /**
     * The list of `Format`s in which this `DataSource` is capable of capturing audio
     * data.
     */
    private val supportedFormats: Array<Format>?

    /**
     * Initializes a new `DataSource` instance.
     */
    constructor() {
        supportedFormats = null
        audioQualityImprovement = true
    }
    /**
     * Initializes a new `DataSource` instance from a specific `MediaLocator` and
     * which has a specific list of `Format` in which it is capable of capturing audio data
     * overriding its registration with JMF and optionally uses audio quality improvement in accord
     * with the preferences of the user.
     *
     * @param locator the `MediaLocator` to create the new instance from
     * @param supportedFormats the list of `Format`s in which the new instance is
     * to be capable of capturing audio data
     * @param audioQualityImprovement `true` if audio quality improvement is to be enabled in accord
     * with the preferences of the user or `false` to completely disable audio quality improvement
     */
    @JvmOverloads
    constructor(locator: MediaLocator?, supportedFormats: Array<Format>? = null,
            audioQualityImprovement: Boolean = true) : super(locator) {
        this.supportedFormats = supportedFormats?.clone()
        this.audioQualityImprovement = audioQualityImprovement
    }

    /**
     * Creates a new `PullBufferStream` which is to be at a specific zero-based index in the
     * list of streams of this `PullBufferDataSource`. The `Format`-related
     * information of the new instance is to be abstracted by a specific `FormatControl`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` in the list of streams of this
     * `PullBufferDataSource`
     * @param formatControl the `FormatControl` which is to abstract the `Format`-related
     * information of the new instance
     * @return a new `PullBufferStream` which is to be at the specified `streamIndex`
     * in the list of streams of this `PullBufferDataSource` and which has its
     * `Format`-related information abstracted by the specified `formatControl`
     * @see AbstractPullBufferCaptureDevice.createStream
     */
    override fun createStream(streamIndex: Int, formatControl: FormatControl?): AbstractPullBufferStream<*> {
        return PortAudioStream(this, formatControl, audioQualityImprovement)
    }

    /**
     * Opens a connection to the media source specified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the `MediaLocator` of this `DataSource`
     * @see AbstractPullBufferCaptureDevice.doConnect
     */
    @Throws(IOException::class)
    override fun doConnect() {
        super.doConnect()
        val deviceID = this.deviceID
        synchronized(streamSyncRoot) { for (stream in streams) (stream as PortAudioStream).setDeviceID(deviceID) }
    }

    /**
     * Closes the connection to the media source specified by the `MediaLocator` of this
     * `DataSource`. Allows extenders to override and be sure that there will be no request
     * to close a connection if the connection has not been opened yet.
     */
    override fun doDisconnect() {
        try {
            synchronized(streamSyncRoot) {
                val streams = streams()
                if (streams != null) {
                    for (stream in streams) {
                        try {
                            (stream as PortAudioStream?)!!.setDeviceID(null)
                        } catch (ioex: IOException) {
                            Timber.e(ioex, "Failed to close %s", stream.javaClass.simpleName)
                        }
                    }
                }
            }
        } finally {
            super.doDisconnect()
        }
    }

    /**
     * Gets the device index of the PortAudio device identified by the `MediaLocator` of this
     * `DataSource`.
     *
     * @return the device index of a PortAudio device identified by the `MediaLocator` of
     * this `DataSource`
     * @throws IllegalStateException if there is no `MediaLocator` associated with this `DataSource`
     */
    private val deviceID: String
        get() {
            val locator = locator
            return if (locator == null) throw IllegalStateException("locator") else getDeviceID(locator)
        }

    /**
     * Gets the `Format`s which are to be reported by a `FormatControl` as supported
     * formats for a `PullBufferStream` at a specific zero-based index in the list of streams
     * of this `PullBufferDataSource`.
     *
     * @param streamIndex the zero-based index of the `PullBufferStream` for which the specified
     * `FormatControl` is to report the list of supported `Format`s
     * @return an array of `Format`s to be reported by a `FormatControl` as the
     * supported formats for the `PullBufferStream` at the specified
     * `streamIndex` in the list of streams of this `PullBufferDataSource`
     * @see AbstractPullBufferCaptureDevice.getSupportedFormats
     */
    override fun getSupportedFormats(streamIndex: Int): Array<Format> {
        return supportedFormats ?: super.getSupportedFormats(streamIndex)
    }

    companion object {
        /**
         * Gets the device index of a PortAudio device from a specific `MediaLocator` identifying
         * it.
         *
         * @param locator the `MediaLocator` identifying the device index of a PortAudio device to get
         * @return the device index of a PortAudio device identified by `locator`
         */
        fun getDeviceID(locator: MediaLocator?): String {
            return if (locator == null) {
                /*
             * Explicitly throw a NullPointerException because the implicit one does not have a
             * message and is thus a bit more difficult to debug.
             */
                throw NullPointerException("locator")
            } else if (AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO.equals(locator.protocol, ignoreCase = true)) {
                var remainder = locator.remainder
                if (remainder != null && remainder[0] == '#') remainder = remainder.substring(1)
                remainder
            } else {
                throw IllegalArgumentException("locator.protocol")
            }
        }
    }
}