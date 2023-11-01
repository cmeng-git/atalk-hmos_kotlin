/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

import javax.media.protocol.ContentDescriptor
import javax.media.protocol.Controls
import javax.media.protocol.SourceStream

/**
 * Implements a `SourceStream` which wraps a specific `SourceStream`.
 *
 * @param <T>
 * the very type of the `SourceStream` wrapped by `SourceStreamDelegate`
 * @author Lyubomir Marinov
</T> */
open class SourceStreamDelegate<T : SourceStream?>
/**
 * Initializes a new `SourceStreamDelegate` instance which is to wrap a specific
 * `SourceStream`.
 *
 * @param stream
 * the `SourceStream` the new instance is to wrap
 */
(
        /**
         * The `SourceStreamDelegate` wrapped by this instance.
         */
        protected val stream: T) : SourceStream {
    /**
     * Implements [SourceStream.endOfStream]. Delegates to the wrapped
     * `SourceStream`
     * .
     *
     * @return `true` if the wrapped `SourceStream` has reached the end the
     * content it makes available
     */
    override fun endOfStream(): Boolean {
        return stream!!.endOfStream()
    }

    /**
     * Implements [SourceStream.getContentDescriptor]. Delegates to the wrapped
     * `SourceStream`.
     *
     * @return a `ContentDescriptor` which describes the content made available by the
     * wrapped `SourceStream`
     */
    override fun getContentDescriptor(): ContentDescriptor {
        return stream!!.contentDescriptor
    }

    /**
     * Implements [SourceStream.getContentLength]. Delegates to the wrapped
     * `SourceStream`.
     *
     * @return the length of the content made available by the wrapped `SourceStream`
     */
    override fun getContentLength(): Long {
        return stream!!.contentLength
    }

    /**
     * Implements [Controls.getControl]. Delegates to the wrapped
     * `SourceStream`.
     *
     * @param controlType
     * a `String` value which specifies the type of the control to be retrieved
     * @return an `Object` which represents the control of the wrapped `SourceStream`
     * of the specified type if such a control is available; otherwise, `null`
     */
    override fun getControl(controlType: String): Any {
        return stream!!.getControl(controlType)
    }

    /**
     * Implements [Controls.getControls]. Delegates to the wrapped `SourceStream`.
     *
     * @return an array of `Object`s which represent the controls available for the wrapped
     * `SourceStream`
     */
    override fun getControls(): Array<Any> {
        return stream!!.controls
    }
}