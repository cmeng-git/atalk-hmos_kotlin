/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference

import java.lang.ref.SoftReference
import javax.media.Buffer
import javax.media.protocol.SourceStream

/**
 * Describes additional information about a specific input audio `SourceStream` of an
 * `AudioMixer` so that the `AudioMixer` can, for example, quickly discover the output
 * `AudioMixingPushBufferDataSource` in the mix of which the contribution of the
 * `SourceStream` is to not be included.
 *
 *
 * Private to `AudioMixer` and `AudioMixerPushBufferStream` but extracted into its own
 * file for the sake of clarity.
 *
 *
 * @author Lyubomir Marinov
 */
class InStreamDesc
/**
 * Initializes a new `InStreamDesc` instance which is to describe additional information
 * about a specific input audio `SourceStream` of an `AudioMixer`. Associates the
 * specified `SourceStream` with the `DataSource` which created it and additional
 * information about it.
 *
 * @param inStream
 * a `SourceStream` for which additional information is to be described by the new
 * instance
 * @param inDataSourceDesc
 * the `DataSource` which created the `SourceStream` to be described by the
 * new instance and additional information about it
 */
(
        /**
         * The `SourceStream` for which additional information is described by this instance.
         */
        inStream: SourceStream,
        /**
         * The `DataSource` which created the `SourceStream` described by this instance
         * and additional information about it.
         */
        val inDataSourceDesc: InDataSourceDesc,
) {
    var inStream = inStream
        /**
         * Sets the `SourceStream` to be described by this instance.
         */
        set(inStream) {
            if (field != inStream) {
                field = inStream

                /*
                 * Since the inStream has changed, one may argue that the Buffer of the old value is
                  * not optimal for the new value.
                 */
                setBuffer(null)
            }
        }

    /**
     * The `Buffer` into which media data is to be read from [.inStream].
     */
    private var buffer: SoftReference<Buffer>? = null

    /**
     * Gets the `Buffer` into which media data is to be read from the `SourceStream`
     * described by this instance.
     *
     * @param create
     * the indicator which determines whether the `Buffer` is to be created in case it
     * does not exist
     * @return the `Buffer` into which media data is to be read from the
     * `SourceStream` described by this instance
     */
    fun getBuffer(create: Boolean): Buffer? {
        var buffer = if (buffer == null) null else buffer!!.get()
        if (buffer == null && create) {
            buffer = Buffer()
            setBuffer(buffer)
        }
        return buffer
    }

    /**
     * Gets the `AudioMixingPushBufferDataSource` in which the mix contribution of the
     * `SourceStream` described by this instance is to not be included.
     *
     * @return the `AudioMixingPushBufferDataSource` in which the mix contribution of the
     * `SourceStream` described by this instance is to not be included
     */
    val outDataSource: AudioMixingPushBufferDataSource?
        get() = inDataSourceDesc.outDataSource

    /**
     * Sets the `Buffer` into which media data is to be read from the `SourceStream`
     * described by this instance.
     *
     * @param buffer the `Buffer` into which media data is to be read from the `SourceStream`
     * described by this instance
     */
    private fun setBuffer(buffer: Buffer?) {
        this.buffer = if (buffer == null) null else SoftReference(buffer)
    }
}