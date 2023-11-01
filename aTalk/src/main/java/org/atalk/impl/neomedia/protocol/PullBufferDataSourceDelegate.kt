/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import java.io.IOException
import javax.media.MediaLocator
import javax.media.Time
import javax.media.protocol.*

/**
 * Implements most of `PullBufferDataSource` for a particular `DataSource` and
 * requires extenders to only implement [PullBufferDataSource.getStreams]. Intended to allow
 * easier overriding of the streams returned by a `DataSource`.
 *
 * @param <T>
 * the very type of `DataSource` to be wrapped in a
 * `PullBufferDataSourceDelegate`
 * @author Damian Minkov
</T> */
abstract class PullBufferDataSourceDelegate<T : DataSource?>(dataSource: T?) : CaptureDeviceDelegatePullBufferDataSource(if (dataSource is CaptureDevice) dataSource else null) {
    /**
     * Gets the `DataSource` wrapped by this instance.
     *
     * @return the `DataSource` wrapped by this instance
     */
    /**
     * The wrapped `DataSource` this instance delegates to.
     */
    val dataSource: T

    /**
     * Initializes a new `PullBufferDataSourceDelegate` which is to delegate to a specific
     * `DataSource`.
     *
     * @param dataSource
     * the `DataSource` the new instance is to delegate to
     */
    init {
        if (dataSource == null) throw NullPointerException("dataSource")
        this.dataSource = dataSource
    }

    /**
     * Implements [DataSource.connect]. Delegates to the wrapped `DataSource`.
     * Overrides [CaptureDeviceDelegatePullBufferDataSource.connect] because the wrapped
     * `DataSource` may not be a `CaptureDevice` yet it still needs to be connected.
     *
     * @throws IOException
     * if the wrapped `DataSource` throws such an exception
     */
    @Throws(IOException::class)
    override fun connect() {
        dataSource!!.connect()
    }

    /**
     * Implements [DataSource.disconnect]. Delegates to the wrapped `DataSource`.
     * Overrides [CaptureDeviceDelegatePullBufferDataSource.disconnect] because the wrapped
     * `DataSource` may not be a `CaptureDevice` yet it still needs to be
     * disconnected.
     */
    override fun disconnect() {
        dataSource!!.disconnect()
    }

    /**
     * Implements [DataSource.getContentType]. Delegates to the wrapped
     * `DataSource`. Overrides
     * [CaptureDeviceDelegatePullBufferDataSource.getContentType] because the
     * wrapped `DataSource` may not be a `CaptureDevice` yet it still needs to report
     * the content type.
     *
     * @return a `String` value which describes the content type of the wrapped
     * `DataSource`
     */
    override fun getContentType(): String {
        return dataSource!!.contentType
    }

    /**
     * Implements [DataSource.getLocator]. Delegates to the wrapped `DataSource`.
     *
     * @return a `MediaLocator` value which describes the locator of the wrapped
     * `DataSource`
     */
    override fun getLocator(): MediaLocator {
        return dataSource!!.locator
    }

    /**
     * Implements [DataSource.getControl]. Delegates to the wrapped
     * `DataSource`. Overrides
     * [CaptureDeviceDelegatePullBufferDataSource.getControl] because the wrapped
     * `DataSource` may not be a `CaptureDevice` yet it still needs to give access to
     * the control.
     *
     * @param controlType
     * a `String` value which names the type of the control to be retrieved
     * @return an `Object` which represents the control of the requested
     * `controlType`
     * of the wrapped `DataSource`
     */
    override fun getControl(controlType: String): Any? {
        return dataSource!!.getControl(controlType)
    }

    /**
     * Implements [DataSource.getControls]. Delegates to the wrapped
     * `PullBufferDataSource`. Overrides
     * [CaptureDeviceDelegatePullBufferDataSource.getControls] because the wrapped
     * `DataSource` may not be a `CaptureDevice` yet it still needs to give access to
     * the controls.
     *
     * @return an array of `Objects` which represent the controls of the wrapped
     * `DataSource`
     */
    override fun getControls(): Array<Any> {
        return dataSource!!.controls
    }

    /**
     * Implements [DataSource.getDuration]. Delegates to the wrapped `DataSource`.
     * Overrides [CaptureDeviceDelegatePullBufferDataSource.getDuration] because the
     * wrapped `DataSource` may not be a `CaptureDevice` yet it still needs to
     * report the duration.
     *
     * @return the duration of the wrapped `DataSource`
     */
    override fun getDuration(): Time {
        return dataSource!!.duration
    }

    /**
     * Gets the `PullBufferStream`s through which this `PullBufferDataSource` gives
     * access to its media data.
     *
     * @return an array of `PullBufferStream`s through which this
     * `PullBufferDataSource` gives access to its media data
     */
    abstract override fun getStreams(): Array<PullBufferStream>

    /**
     * Implements [DataSource.start]. Delegates to the wrapped `DataSource`.
     * Overrides [CaptureDeviceDelegatePullBufferDataSource.start] because the wrapped
     * `DataSource` may not be a `CaptureDevice` yet it still needs to be started.
     *
     * @throws IOException
     * if the wrapped `DataSource` throws such an exception
     */
    @Throws(IOException::class)
    override fun start() {
        dataSource!!.start()
    }

    /**
     * Implements [DataSource.stop]. Delegates to the wrapped `DataSource`.
     * Overrides [CaptureDeviceDelegatePullBufferDataSource.stop] because the wrapped
     * `DataSource` may not be a `CaptureDevice` yet it still needs to be stopped.
     *
     * @throws IOException
     * if the wrapped `DataSource` throws such an exception
     */
    @Throws(IOException::class)
    override fun stop() {
        dataSource!!.stop()
    }
}