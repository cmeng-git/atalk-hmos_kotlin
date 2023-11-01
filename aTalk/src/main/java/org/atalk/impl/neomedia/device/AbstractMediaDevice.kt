/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.service.neomedia.RTPExtension
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import java.io.IOException
import javax.media.*
import javax.media.protocol.DataSource

/**
 * Defines the interface for `MediaDevice` required by the `org.atalk.impl.neomedia`
 * implementation of `org.atalk.service.neomedia`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractMediaDevice : MediaDevice {
    /**
     * Connects to a specific `CaptureDevice` given in the form of a `DataSource`.
     * Explicitly defined in order to allow extenders to customize the connect procedure.
     *
     * @param captureDevice the `CaptureDevice` to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified `captureDevice`
     */
    @Throws(IOException::class)
    open fun connect(captureDevice: DataSource) {
        // if (captureDevice == null) throw NullPointerException("captureDevice")
        try {
            captureDevice.connect()
        } catch (npe: NullPointerException) {
            /*
             * The old media says it happens when the operating system does not support the operation.
             */
            throw IOException(npe)
        }
    }

    /**
     * Creates a `DataSource` instance for this `MediaDevice` which gives access to the captured media.
     *
     * @return a `DataSource` instance which gives access to the media captured by this `MediaDevice`
     */
    abstract fun createOutputDataSource(): DataSource?

    /**
     * Initializes a new `Processor` instance which is to be used to play back media on this
     * `MediaDevice` . Allows extenders to, for example, disable the playback on this
     * `MediaDevice` by completely overriding and returning `null`.
     *
     * @param dataSource the `DataSource` which is to be played back by the new `Processor` instance
     * @return a new `Processor` instance which is to be used to play back the media provided by the specified
     * `dataSource` or `null` if the specified `dataSource` is to not be played back
     * @throws Exception if an exception is thrown by [DataSource.connect],
     * [Manager.createProcessor], or [DataSource.disconnect]
     */
    @Throws(Exception::class)
    open fun createPlayer(dataSource: DataSource): Processor? {
        var player: Processor? = null

        // A Player is documented to be created on a connected DataSource.
        dataSource.connect()
        player = try {
            Manager.createProcessor(dataSource)
        } finally {
            if (player == null) dataSource.disconnect()
        }
        return player
    }

    /**
     * Initializes a new `Renderer` instance which is to play back media on this
     * `MediaDevice`. Allows extenders to initialize a specific `Renderer` instance.
     * The implementation of `AbstractMediaDevice` returns `null` which means that it
     * is left to FMJ to choose a suitable `Renderer` irrespective of this`MediaDevice`.
     *
     * @return a new `Renderer` instance which is to play back media on this`MediaDevice`
     * or `null` if a suitable `Renderer` is to be chosen irrespective of this `MediaDevice`
     */
    open fun createRenderer(): Renderer? {
        return null
    }

    /**
     * Creates a new `MediaDeviceSession` instance which is to represent the use of this
     * `MediaDevice` by a `MediaStream`.
     *
     * @return a new `MediaDeviceSession` instance which is to represent the use of this
     * `MediaDevice` by a `MediaStream`
     */
    open fun createSession(): MediaDeviceSession {
        return when (mediaType) {
            MediaType.VIDEO -> VideoMediaDeviceSession(this)
            else -> AudioMediaDeviceSession(this)
        }
    }

    /**
     * Returns a `List` containing (at the time of writing) a single extension descriptor
     * indicating `RECVONLY` support for mixer-to-client audio levels.
     *
     * @return a `List` containing the `CSRC_AUDIO_LEVEL_URN` extension descriptor.
     */
    override val supportedExtensions: List<RTPExtension>?
        get() = null

    /**
     * Gets a list of `MediaFormat`s supported by this `MediaDevice`.
     *
     * @return the list of `MediaFormat`s supported by this device
     * @see MediaDevice.getSupportedFormats
     */
    override val supportedFormats: List<MediaFormat>
        get() = getSupportedFormats(null, null)
}