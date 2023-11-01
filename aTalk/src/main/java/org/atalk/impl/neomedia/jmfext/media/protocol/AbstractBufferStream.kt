/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import org.atalk.impl.neomedia.control.AbstractControls
import org.atalk.impl.neomedia.control.ControlsAdapter
import timber.log.Timber
import java.io.IOException
import javax.media.Format
import javax.media.control.FormatControl
import javax.media.protocol.*

/**
 * Provides a base implementation of `SourceStream` in order to facilitate implementers by
 * taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractBufferStream<T : DataSource?>
/**
 * Initializes a new `AbstractBufferStream` instance which is to have its `Format`
 * -related information abstracted by a specific `FormatControl`.
 *
 * @param dataSource
 * the `DataSource` which is creating the new instance so that it becomes one of
 * its `streams`
 * @param formatControl
 * the `FormatControl` which is to abstract the `Format`-related
 * information of the new instance
 */
protected constructor(
        /**
         * The `DataSource` which has created this instance and which contains it as one of its
         * `streams`.
         */
        protected val dataSource: T,
        /**
         * The `FormatControl` which gives access to the `Format` of the media data
         * provided by this `SourceStream` and which, optionally, allows setting it.
         */
        protected val formatControl: FormatControl?,
) : AbstractControls(), SourceStream {
    /**
     * Releases the resources used by this instance throughout its existence and makes it available
     * for garbage collection. This instance is considered unusable after closing.
     *
     *
     * **Warning**: The method is not invoked by the framework, extenders may choose to invoke
     * it.
     *
     */
    open fun close() {
        try {
            stop()
        } catch (ioex: IOException) {
            Timber.e(ioex, "Failed to stop %s", javaClass.simpleName)
        }
    }

    /**
     * Gets the `Format` of this `AbstractBufferStream` as directly known by it.
     * Allows extenders to override the `Format` known to the `DataSource` which
     * created this instance and possibly provide more details on the currently set `Format`.
     *
     * @return the `Format` of this `AbstractBufferStream` as directly known by it or
     * `null` if this `AbstractBufferStream` does not directly know its
     * `Format` and it relies on the `DataSource` which created it to report
     * its `Format`
     */
    protected open fun doGetFormat(): Format? {
        return null
    }

    /**
     * Attempts to set the `Format` of this `AbstractBufferStream`. Allows extenders
     * to enable setting the `Format` of an existing `AbstractBufferStream` (in
     * contract to setting it before the `AbstractBufferStream` is created by the
     * `DataSource` which will provide it).
     *
     * @param format
     * the `Format` to be set as the format of this `AbstractBufferStream`
     * @return the `Format` of this `AbstractBufferStream` or `null` if the
     * attempt to set the `Format` did not succeed and any last-known `Format`
     * is to be left in effect
     */
    protected open fun doSetFormat(format: Format): Format? {
        return null
    }

    /**
     * Determines whether the end of this `SourceStream` has been reached. The
     * `AbstractBufferStream` implementation always returns `false`.
     *
     * @return `true` if the end of this `SourceStream` has been reached; otherwise,
     * `false`
     */
    override fun endOfStream(): Boolean {
        return false
    }

    /**
     * Gets a `ContentDescriptor` which describes the type of the content made available by
     * this `SourceStream`. The `AbstractBufferStream` implementation always
     * returns a `ContentDescriptor` with content type equal to
     * `ContentDescriptor#RAW`.
     *
     * @return a `ContentDescriptor` which describes the type of the content made available
     * by this `SourceStream`
     */
    override fun getContentDescriptor(): ContentDescriptor {
        return CONTENT_DESCRIPTOR
    }

    /**
     * Gets the length in bytes of the content made available by this `SourceStream`. The
     * `AbstractBufferStream` implementation always returns `LENGTH_UNKNOWN`.
     *
     * @return the length in bytes of the content made available by this `SourceStream`
     * if it is known; otherwise, `LENGTH_UKNOWN`
     */
    override fun getContentLength(): Long {
        return SourceStream.LENGTH_UNKNOWN
    }

    /**
     * Implements [javax.media.protocol.Controls.getControls]. Gets the controls available
     * for this instance.
     *
     * @return an array of `Object`s which represent the controls available for this instance
     */
    override fun getControls(): Array<Any> {
        return formatControl?.let { arrayOf(it) } ?: ControlsAdapter.EMPTY_CONTROLS
    }

    /**
     * Gets the `Format` of the media data made available by this `AbstractBufferStream`.
     */
    open fun getFormat(): Format? {
        return formatControl?.format
    }

    /**
     * Gets the `Format` of this `AbstractBufferStream` as directly known by it.
     *
     * @return the `Format` of this `AbstractBufferStream` as directly known by it
     */
    fun internalGetFormat(): Format? {
        return doGetFormat()
    }

    /**
     * Attempts to set the `Format` of this `AbstractBufferStream`.
     *
     * @param format
     * the `Format` to be set as the format of this `AbstractBufferStream`
     * @return the `Format` of this `AbstractBufferStream` or `null` if the
     * attempt to set the `Format` did not succeed and any last-known `Format`
     * is to be left in effect
     */
    fun internalSetFormat(format: Format): Format? {
        return doSetFormat(format)
    }

    /**
     * Starts the transfer of media data from this `AbstractBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while starting the transfer of media data from this
     * `AbstractBufferStream`
     */
    @Throws(IOException::class)
    open fun start() {
    }

    /**
     * Stops the transfer of media data from this `AbstractBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while stopping the transfer of media data from this
     * `AbstractBufferStream`
     */
    @Throws(IOException::class)
    open fun stop() {
    }

    companion object {
        /**
         * The (default) `ContentDescriptor` of the `AbstractBufferStream` instances.
         */
        private val CONTENT_DESCRIPTOR = ContentDescriptor(ContentDescriptor.RAW)
    }
}