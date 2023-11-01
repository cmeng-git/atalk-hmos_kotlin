/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import org.atalk.impl.neomedia.MediaStreamImpl
import org.atalk.impl.neomedia.protocol.TranscodingDataSource
import timber.log.Timber
import java.io.IOException
import javax.media.Format
import javax.media.protocol.DataSource
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.SourceStream

/**
 * Describes additional information about a specific input `DataSource` of an
 * `AudioMixer` so that the `AudioMixer` can, for example, quickly discover the output
 * `AudioMixingPushBufferDataSource` in the mix of which the contribution of the
 * `DataSource` is to not be included.
 *
 *
 * Private to `AudioMixer` and `AudioMixerPushBufferStream` but extracted into its own
 * file for the sake of clarity.
 *
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class InDataSourceDesc
/**
 * Initializes a new `InDataSourceDesc` instance which is to describe additional
 * information about a specific input `DataSource` of an `AudioMixer`. Associates
 * the specified `DataSource` with the `AudioMixingPushBufferDataSource` in which
 * the mix contributions of the specified input `DataSource` are to not be included.
 *
 * @param inDataSource a `DataSource` for which additional information is to be described by the new
 * instance
 * @param outDataSource the `AudioMixingPushBufferDataSource` in which the mix contributions of
 * `inDataSource` are to not be included
 */
(
        /**
         * The `DataSource` for which additional information is described by this instance.
         */
        val inDataSource: DataSource,
        /**
         * The `AudioMixingPushBufferDataSource` in which the mix contributions of
         * [.inDataSource] are to not be included.
         */
        val outDataSource: AudioMixingPushBufferDataSource?,
) {
    /**
     * The indicator which determines whether the effective input `DataSource` described by
     * this instance is currently connected.
     */
    private var connected = false

    /**
     * The `Thread` which currently executes [DataSource.connect] on the effective
     * input `DataSource` described by this instance.
     */
    private var connectThread: Thread? = null

    /**
     * Connects the effective input `DataSource` described by this instance upon request
     * from a specific `AudioMixer`. If the effective input `DataSource` is to be
     * asynchronously connected, the completion of the connect procedure will be reported to the
     * specified `AudioMixer` by calling its [AudioMixer.connected].
     *
     * @param audioMixer the `AudioMixer` requesting the effective input `DataSource` described
     * by this instance to be connected
     * @throws IOException if anything wrong happens while connecting the effective input `DataSource`
     * described by this instance
     */
    @Synchronized
    @Throws(IOException::class)
    fun connect(audioMixer: AudioMixer) {
        val effectiveInDataSource = if (transcodingDataSource == null) inDataSource else transcodingDataSource!!
        if (effectiveInDataSource is TranscodingDataSource) {
            if (connectThread == null) {
                connectThread = object : Thread() {
                    override fun run() {
                        try {
                            audioMixer.connect(effectiveInDataSource, inDataSource)
                            synchronized(this@InDataSourceDesc) { connected = true }
                            audioMixer.connected(this@InDataSourceDesc)
                        } catch (ioex: IOException) {
                            Timber.e(ioex, "Failed to connect to inDataSource %s", MediaStreamImpl.toString(inDataSource))
                        } finally {
                            synchronized(this@InDataSourceDesc) { if (connectThread === currentThread()) connectThread = null }
                        }
                    }
                }
                connectThread!!.isDaemon = true
                connectThread!!.start()
            }
        } else {
            audioMixer.connect(effectiveInDataSource, inDataSource)
            connected = true
        }
    }

    /**
     * Creates a `DataSource` which attempts to transcode the tracks of the input
     * `DataSource` described by this instance into a specific output `Format`.
     *
     * @param outFormat the `Format` in which the tracks of the input `DataSource` described by
     * this instance are to be transcoded
     * @return `true` if a new transcoding `DataSource` has been created for the
     * input `DataSource` described by this instance; otherwise, `false`
     */
    @Synchronized
    fun createTranscodingDataSource(outFormat: Format): Boolean {
        return if (transcodingDataSource == null) {
            transcodingDataSource = TranscodingDataSource(inDataSource, outFormat)
            true
        } else false
    }

    /**
     * Disconnects the effective input `DataSource` described by this instance if it is
     * already connected.
     */
    @Synchronized
    fun disconnect() {
        if (connected) {
            effectiveInDataSource?.disconnect()
            connected = false
        }
    }

    /**
     * Gets the control available for the effective input `DataSource` described by this
     * instance with a specific type.
     *
     * @param controlType a `String` value which specifies the type of the control to be retrieved
     * @return an `Object` which represents the control available for the effective input
     * `DataSource` described by this instance with the specified
     * `controlType` if such a control exists; otherwise, `null`
     */
    @Synchronized
    fun getControl(controlType: String?): Any? {
        return this.effectiveInDataSource?.getControl(controlType)
    }

    /**
     * Gets the actual `DataSource` from which the associated `AudioMixer` directly
     * reads in order to retrieve the mix contribution of the `DataSource` described by this
     * instance.
     *
     * @return the actual `DataSource` from which the associated `AudioMixer`
     * directly reads in order to retrieve the mix contribution of the `DataSource` described
     * by this instance
     */
    @get:Synchronized
    val effectiveInDataSource: DataSource?
        get() = when (transcodingDataSource) {
            null -> inDataSource
            else -> if (connected) transcodingDataSource else null
        }

    /**
     * Gets the `SourceStream`s of the effective input `DataSource` described by this
     * instance.
     *
     * @return an array of the `SourceStream`s of the effective input `DataSource`
     * described by this instance
     */
    @get:Synchronized
    val streams: Array<out SourceStream?>?
        get() {
            if (!connected) return EMPTY_STREAMS

            return when (val inDataSource = effectiveInDataSource) {
                is PushBufferDataSource -> inDataSource.streams
                is PullBufferDataSource -> inDataSource.streams
                is TranscodingDataSource -> inDataSource.streams
                else -> null
            }
        }

    /**
     * The `DataSource`, if any, which transcodes the tracks of the input `DataSource`
     * described by this instance in the output `Format` of the associated `AudioMixer`.
     */
    var transcodingDataSource: TranscodingDataSource? = null
        set(transcodingDataSource) {
            field = transcodingDataSource
            connected = false

        }

    /**
     * Starts the effective input `DataSource` described by this instance if it is connected.
     *
     * @throws IOException if starting the effective input `DataSource` described by this instance fails
     */
    @Synchronized
    @Throws(IOException::class)
    fun start() {
        if (connected) effectiveInDataSource?.start()
    }

    /**
     * Stops the effective input `DataSource` described by this instance if it is connected.
     *
     * @throws IOException if stopping the effective input `DataSource` described by this instance fails
     */
    @Synchronized
    @Throws(IOException::class)
    fun stop() {
        if (connected) effectiveInDataSource?.stop()
    }

    companion object {
        /**
         * The constant which represents an empty array with `SourceStream` element type.
         * Explicitly defined in order to avoid unnecessary allocations.
         */
        private val EMPTY_STREAMS = arrayOfNulls<SourceStream>(0)
    }
}