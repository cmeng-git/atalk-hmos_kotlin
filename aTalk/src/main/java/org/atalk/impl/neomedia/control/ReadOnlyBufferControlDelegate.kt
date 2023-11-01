/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import java.awt.Component
import javax.media.control.BufferControl

/**
 * Represents a wrapper of a specific `BufferControl` which does not call the setters of the
 * wrapped instance and calls only the getters.
 *
 * @author Lubomir Marinov
 */
class ReadOnlyBufferControlDelegate
/**
 * Initializes a new `ReadOnlyBufferControlDelegate` instance which is to wrap a
 * specific `BufferControl` and call only its getters.
 *
 * @param bufferControl
 * the `BufferControl` to be wrapped by the new instance
 */
(
        /**
         * The `BufferControl` wrapped by this instance.
         */
        private val bufferControl: BufferControl) : BufferControl {
    /**
     * Implements [BufferControl.getBufferLength]. Gets the length in milliseconds of the
     * buffering performed by the owner of the wrapped `BufferControl`.
     *
     * @return the length in milliseconds of the buffering performed by the owner of the wrapped
     * `BufferControl`
     */
    override fun getBufferLength(): Long {
        return bufferControl.bufferLength
    }

    /**
     * Implements [javax.media.Control.getControlComponent]. Gets the UI `Component`
     * representing this instance and exported by the owner of the wrapped `BufferControl`.
     *
     * @return the UI `Component` representing the wrapped `BufferControl` and
     * exported by its owner if such a `Component` is available; otherwise,
     * `null`
     */
    override fun getControlComponent(): Component {
        return bufferControl.controlComponent
    }

    /**
     * Implements [BufferControl.getEnabledThreshold]. Gets the indicator of the wrapped
     * `BufferControl` which determines whether threshold calculations are enabled.
     *
     * @return `true` if threshold calculations are enabled in the wrapped
     * `BufferControl`; otherwise, `false`
     */
    override fun getEnabledThreshold(): Boolean {
        return bufferControl.enabledThreshold
    }

    /**
     * Implements [BufferControl.getMinimumThreshold]. Gets the minimum threshold in
     * milliseconds for the buffering performed by the owner of the wrapped `BufferControl`.
     *
     * @return the minimum threshold in milliseconds for the buffering performed by the owner of
     * the wrapped `BufferControl`
     */
    override fun getMinimumThreshold(): Long {
        return bufferControl.minimumThreshold
    }

    /**
     * Implements [BufferControl.setBufferLength]. Ignores the request because this
     * instance provides read-only support and returns the value actually in effect.
     *
     * @param bufferLength
     * the length in milliseconds of the buffering to be performed by the owner of the
     * wrapped `BufferControl`
     * @return the length in milliseconds of the buffering performed by the owner of the wrapped
     * `BufferControl` that is actually in effect
     */
    override fun setBufferLength(bufferLength: Long): Long {
        return getBufferLength()
    }

    /**
     * Implements [BufferControl.setEnabledThreshold]. Ignores the set request
     * because this instance provides read-only support.
     *
     * @param enabledThreshold
     * `true` if threshold calculations are to be enabled; otherwise, `false`
     */
    override fun setEnabledThreshold(enabledThreshold: Boolean) {}

    /**
     * Implements [BufferControl.setMinimumThreshold]. Ignores the set request because
     * this instance provides read-only support and returns the value actually in effect.
     *
     * @param minimumThreshold
     * the minimum threshold in milliseconds for the buffering to be performed by the owner
     * of the wrapped `BufferControl`
     * @return the minimum threshold in milliseconds for the buffering performed by the owner of
     * the wrapped `BufferControl` that is actually in effect
     */
    override fun setMinimumThreshold(minimumThreshold: Long): Long {
        return getMinimumThreshold()
    }
}