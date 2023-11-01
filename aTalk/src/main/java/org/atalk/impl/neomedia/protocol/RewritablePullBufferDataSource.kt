/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import org.atalk.impl.neomedia.control.AbstractControls
import org.atalk.service.neomedia.DTMFInbandTone
import java.io.IOException
import java.util.*
import javax.media.*
import javax.media.protocol.PullBufferDataSource
import javax.media.protocol.PullBufferStream

/**
 * Implements a `PullBufferDataSource` wrapper which provides mute support for the wrapped
 * instance.
 *
 *
 * Because the class wouldn't work for our use case without it, `CaptureDevice` is
 * implemented and is being delegated to the wrapped `DataSource` (if it supports the
 * interface in question).
 *
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class RewritablePullBufferDataSource
/**
 * Initializes a new `RewritablePullBufferDataSource` instance which is to provide mute
 * support for a specific `PullBufferDataSource`.
 *
 * @param dataSource
 * the `PullBufferDataSource` the new instance is to provide mute support for
 */
(dataSource: PullBufferDataSource?) : PullBufferDataSourceDelegate<PullBufferDataSource?>(dataSource), MuteDataSource, InbandDTMFDataSource {
    /**
     * Determines whether this `DataSource` is mute.
     *
     * @return `true` if this `DataSource` is mute; otherwise, `false`
     */
    /**
     * Sets the mute state of this `DataSource`.
     *
     * @param mute
     * `true` to mute this `DataSource`; otherwise, `false`
     */
    /**
     * The indicator which determines whether this `DataSource` is mute.
     */
    override var isMute = false

    /**
     * The tones to send via inband DTMF, if not empty.
     */
    private val tones = LinkedList<DTMFInbandTone>()

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone
     * the DTMF tone to send.
     */
    override fun addDTMF(tone: DTMFInbandTone) {
        tones.add(tone)
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
     * Get wrapped DataSource.
     *
     * @return wrapped DataSource
     */
    val wrappedDataSource: PullBufferDataSource
        get() = dataSource!!

    /**
     * {@inheritDoc}
     *
     *
     * Overrides the super implementation to include the type hierarchy of the very wrapped
     * `dataSource` instance into the search for the specified `controlType`.
     */
    override fun getControl(controlType: String): Any? {
        return if (InbandDTMFDataSource::class.java.name == controlType
                || MuteDataSource::class.java.name == controlType) {
            this
        } else {
            /*
			 * The super implements a delegate so we can be sure that it delegates the
			 * invocation of Controls#getControl(String) to the wrapped dataSource.
			 */
            AbstractControls.queryInterface(dataSource, controlType)
        }
    }

    /**
     * Implements [PullBufferDataSource.getStreams]. Wraps the streams of the wrapped
     * `PullBufferDataSource` into `MutePullBufferStream` instances in order to
     * provide mute support to them.
     *
     * @return an array of `PullBufferStream` instances with enabled mute support
     */
    override fun getStreams(): Array<PullBufferStream> {
        val streams = dataSource!!.streams
        if (streams != null) for (streamIndex in streams.indices) streams[streamIndex] = MutePullBufferStream(streams[streamIndex])
        return streams
    }

    /**
     * Implements a `PullBufferStream` wrapper which provides mute support for the wrapped
     * instance.
     */
    private inner class MutePullBufferStream
    /**
     * Initializes a new `MutePullBufferStream` instance which is to provide mute
     * support for a specific `PullBufferStream`.
     *
     * @param stream
     * the `PullBufferStream` the new instance is to provide mute support for
     */
    (stream: PullBufferStream) : SourceStreamDelegate<PullBufferStream?>(stream), PullBufferStream {
        /**
         * Implements [PullBufferStream.getFormat]. Delegates to the wrapped
         * `PullBufferStream`.
         *
         * @return the `Format` of the wrapped `PullBufferStream`
         */
        override fun getFormat(): Format {
            return stream!!.format
        }

        /**
         * Implements PullBufferStream#read(Buffer). If this instance is muted (through its owning
         * RewritablePullBufferDataSource), overwrites the data read from the wrapped
         * PullBufferStream with silence data.
         *
         * @param buffer
         * which data will be filled. @throws IOException Thrown if an error occurs while
         * reading.
         */
        @Throws(IOException::class)
        override fun read(buffer: Buffer) {
            stream!!.read(buffer)
            if (isSendingDTMF) RewritablePushBufferDataSource.Companion.sendDTMF(buffer, tones.poll()) else if (isMute) RewritablePushBufferDataSource.Companion.mute(buffer)
        }

        /**
         * Implements PullBufferStream#willReadBlock(). Delegates to the wrapped PullSourceStream.
         *
         * @return `true` if read would block; otherwise returns `false`.
         */
        override fun willReadBlock(): Boolean {
            return stream!!.willReadBlock()
        }
    }
}