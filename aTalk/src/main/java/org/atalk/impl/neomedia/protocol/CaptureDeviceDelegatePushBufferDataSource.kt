/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import org.atalk.impl.neomedia.control.ControlsAdapter
import java.io.IOException
import javax.media.CaptureDeviceInfo
import javax.media.Time
import javax.media.control.FormatControl
import javax.media.protocol.*

/**
 * Represents a `PushBufferDataSource` which is also a `CaptureDevice` through
 * delegation to a specific `CaptureDevice` .
 *
 * @author Lubomir Marinov
 */
open class CaptureDeviceDelegatePushBufferDataSource
/**
 * Initializes a new `CaptureDeviceDelegatePushBufferDataSource` instance which
 * delegates to a specific `CaptureDevice` in order to implement its
 * `CaptureDevice` functionality.
 *
 * @param captureDevice the `CaptureDevice` the new instance is to delegate to in order to provide its
 * `CaptureDevice` functionality
 */
(
        /**
         * The `CaptureDevice` this instance delegates to in order to implement its
         * `CaptureDevice` functionality.
         */
        protected val captureDevice: CaptureDevice?) : PushBufferDataSource(), CaptureDevice {
    /**
     * Implements [CaptureDevice.connect]. Delegates to the wrapped `CaptureDevice`
     * if available; otherwise, does nothing.
     *
     * @throws IOException if the wrapped `CaptureDevice` throws such an exception
     */
    @Throws(IOException::class)
    override fun connect() {
        captureDevice?.connect()
    }

    /**
     * Implements [CaptureDevice.disconnect]. Delegates to the wrapped
     * `CaptureDevice` if available; otherwise, does nothing.
     */
    override fun disconnect() {
        captureDevice?.disconnect()
    }

    /**
     * Implements [CaptureDevice.getCaptureDeviceInfo]. Delegates to the wrapped
     * `CaptureDevice` if available; otherwise, returns `null`.
     *
     * @return the `CaptureDeviceInfo` of the wrapped `CaptureDevice` if available;
     * otherwise, `null`
     */
    override fun getCaptureDeviceInfo(): CaptureDeviceInfo? {
        return captureDevice?.captureDeviceInfo
    }

    /**
     * Implements [DataSource.getContentType]. Delegates to the wrapped
     * `CaptureDevice` if it implements `DataSource`; otherwise, returns
     * [ContentDescriptor.CONTENT_UNKNOWN].
     *
     * @return a `String` value which describes the content type of the wrapped
     * `CaptureDevice` if it implements `DataSource`; otherwise,
     * `ContentDescriptor#CONTENT_UNKNOWN`
     */
    override fun getContentType(): String {
        return if (captureDevice is DataSource) (captureDevice as DataSource).contentType else ContentDescriptor.CONTENT_UNKNOWN
    }

    /**
     * Implements [DataSource.getControl]. Delegates to the wrapped
     * `CaptureDevice` if it implements `DataSource`; otherwise, returns `null`.
     *
     * @param controlType a `String` value which names the type of the control to be retrieved
     * @return an `Object` which represents the control of the requested
     * `controlType` of the wrapped `CaptureDevice` if it implements
     * `DataSource`; otherwise, `null`
     */
    override fun getControl(controlType: String): Any? {
        return if (captureDevice is DataSource) (captureDevice as DataSource).getControl(controlType) else null
    }

    /**
     * Implements [DataSource.getControls]. Delegates to the wrapped `CaptureDevice`
     * if it implements `DataSource`; otherwise, returns an empty array with `Object`
     * element type.
     *
     * @return the array of controls for the wrapped `CaptureDevice` if it implements
     * `DataSource`; otherwise, an empty array with `Object` element type
     */
    override fun getControls(): Array<Any> {
        return if (captureDevice is DataSource) (captureDevice as DataSource).controls else ControlsAdapter.EMPTY_CONTROLS
    }

    /**
     * Implements [DataSource.getDuration]. Delegates to the wrapped `CaptureDevice`
     * if it implements `DataSource`; otherwise, returns
     * [DataSource.DURATION_UNKNOWN].
     *
     * @return the duration of the wrapped `CaptureDevice` as returned by its implementation
     * of `DataSource` if any; otherwise, returns `DataSource#DURATION_UNKNOWN`
     */
    override fun getDuration(): Time {
        return if (captureDevice is DataSource) (captureDevice as DataSource).duration else DURATION_UNKNOWN
    }

    /**
     * Implements [CaptureDevice.getFormatControls]. Delegates to the wrapped
     * `CaptureDevice` if available; otherwise, returns an empty array with
     * `FormatControl` element type.
     *
     * @return the array of `FormatControl`s of the wrapped `CaptureDevice` if
     * available; otherwise, an empty array with `FormatControl` element type
     */
    override fun getFormatControls(): Array<FormatControl?> {
        return if (captureDevice != null) captureDevice.formatControls else arrayOfNulls(0)
    }

    /**
     * Implements [PushBufferDataSource.getStreams]. Delegates to the wrapped
     * `CaptureDevice` if it implements `PushBufferDataSource`; otherwise, returns an
     * empty array with `PushBufferStream` element type.
     *
     * @return an array of `PushBufferStream`s as returned by the wrapped
     * `CaptureDevice` if it implements `PushBufferDataSource`; otherwise, an
     * empty array with `PushBufferStream` element type
     */
    override fun getStreams(): Array<PushBufferStream> {
        return if (captureDevice is PushBufferDataSource) (captureDevice as PushBufferDataSource).streams else EMPTY_STREAMS
    }

    /**
     * Implements [CaptureDevice.start]. Delegates to the wrapped `CaptureDevice` if
     * available; otherwise, does nothing.
     *
     * @throws IOException if the wrapped `CaptureDevice` throws such an exception
     */
    @Throws(IOException::class)
    override fun start() {
        captureDevice?.start()
    }

    /**
     * Implements [CaptureDevice.stop]. Delegates to the wrapped `CaptureDevice` if
     * available; otherwise, does nothing.
     *
     * @throws IOException if the wrapped `CaptureDevice` throws such an exception
     */
    @Throws(IOException::class)
    override fun stop() {
        captureDevice?.stop()
    }

    companion object {
        /**
         * The constant which represents an empty array with `PushBufferStream` element type.
         * Explicitly defined in order to reduce unnecessary allocations.
         */
        @JvmStatic
        protected val EMPTY_STREAMS = emptyList<PushBufferStream>().toTypedArray()
    }
}