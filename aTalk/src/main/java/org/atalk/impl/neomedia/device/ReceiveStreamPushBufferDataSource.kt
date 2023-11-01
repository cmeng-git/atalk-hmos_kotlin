/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.protocol.NullBufferTransferHandler
import org.atalk.impl.neomedia.protocol.PushBufferDataSourceDelegate
import javax.media.protocol.DataSource
import javax.media.protocol.PushBufferDataSource
import javax.media.protocol.PushBufferStream
import javax.media.rtp.ReceiveStream

/**
 * Wraps the `DataSource` of a specific `ReceiveStream` so that calls to its
 * [DataSource.disconnect] can be explicitly controlled. It is introduced because it seems
 * that after the `DataSource` of a `ReceiveStream` is disconnected, it cannot be
 * connected to or started and if a `Processor` is created on it, it freezes in the
 * [javax.media.Processor.Configuring] state.
 *
 * @author Lyubomir Marinov
 */
class ReceiveStreamPushBufferDataSource(
        receiveStream: ReceiveStream,
        dataSource: PushBufferDataSource,
) : PushBufferDataSourceDelegate<PushBufferDataSource>(dataSource) {
    /**
     * The `ReceiveStream` which has its `DataSource` wrapped by this instance.
     * Currently, remembered just to be made available to callers in case they need it and not used
     * by this instance.
     */
    val receiveStream: ReceiveStream

    /**
     * The indicator which determines whether [DataSource.disconnect] is to be called on the
     * wrapped `DataSource` when it is called on this instance.
     */
    private var suppressDisconnect = false

    /**
     * Initializes a new `ReceiveStreamPushBufferDataSource` instance which is to wrap a
     * specific `DataSource` of a specific `ReceiveStream` for the purposes of
     * enabling explicitly control of calls to its DataSource.disconnect.
     *
     * receiveStream: the `ReceiveStream` which is to have its `DataSource`
     * dataSource the `DataSource` of `receiveStream` which is to be wrapped by this instance
     */
    init {
        this.receiveStream = receiveStream
    }

    /**
     * Initializes a new `ReceiveStreamPushBufferDataSource` instance which is to wrap a
     * specific `DataSource` of a specific `ReceiveStream` for the purposes of
     * enabling explicitly control of calls to its [DataSource.disconnect] and, optionally,
     * activates the suppresses the call in question.
     *
     * @param receiveStream
     * the `ReceiveStream` which is to have its `DataSource`
     * @param dataSource the `DataSource` of `receiveStream` which is to be wrapped by this instance
     * @param suppressDisconnect
     * `true` if calls to `DataSource#disconnect()` on the wrapped
     * `dataSource` are to be suppressed when there are such calls on the new
     * instance; otherwise, `false`
     */
    constructor(
            receiveStream: ReceiveStream,
            dataSource: PushBufferDataSource,
            suppressDisconnect: Boolean,
    ) : this(receiveStream, dataSource) {
        setSuppressDisconnect(suppressDisconnect)
    }

    /**
     * Implements [DataSource.disconnect]. Disconnects the wrapped `DataSource` if it
     * has not been explicitly suppressed by setting the `suppressDisconnect` property of
     * this instance.
     */
    override fun disconnect() {
        if (!suppressDisconnect) super.disconnect()
    }

    /**
     * Implements [PushBufferDataSource.getStreams]. Delegates to the wrapped `DataSource` of the `ReceiveStream`.
     *
     * @return an array of the `PushBufferStream`s of the wrapped `DataSource` of the `ReceiveStream`
     */
    override fun getStreams(): Array<PushBufferStream> {
        return dataSource.streams
    }

    /**
     * Sets the indicator which determines whether calls to [DataSource.disconnect] on the
     * wrapped `DataSource` are to be suppressed when there are such calls on this instance.
     *
     * @param suppressDisconnect
     * `true` to suppress calls to `DataSource#disconnect()` on the wrapped
     * `DataSource` when there are such calls on this instance; otherwise,
     * `false`
     */
    private fun setSuppressDisconnect(suppressDisconnect: Boolean) {
        this.suppressDisconnect = suppressDisconnect
    }

    companion object {
        /**
         * Sets a `BufferTransferHandler` on a specific `ReceiveStream` which reads data
         * as soon as possible and throws it away.
         *
         * @param receiveStream
         * the `ReceiveStream` on which to set a `BufferTransferHandler` which
         * reads data as soon as possible and throws it away
         */
        fun setNullTransferHandler(receiveStream: ReceiveStream) {
            val dataSource = receiveStream.dataSource
            if (dataSource != null) {
                if (dataSource is PushBufferDataSource) {
                    val streams = dataSource.streams
                    if (streams != null && streams.isNotEmpty()) {
                        for (stream in streams) {
                            stream.setTransferHandler(NullBufferTransferHandler())
                        }
                    }

                    // If data is to be read as soon as possible and thrown away,
                    // it sounds reasonable that buffering while stopped should be
                    // disabled.
                    if (dataSource is net.sf.fmj.media.protocol.rtp.DataSource) {
                        dataSource.setBufferWhenStopped(false)
                    }
                }
            }
        }
    }
}