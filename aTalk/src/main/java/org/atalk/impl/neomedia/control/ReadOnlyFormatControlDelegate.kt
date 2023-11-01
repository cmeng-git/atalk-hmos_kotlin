/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import java.awt.Component
import javax.media.Control
import javax.media.Format
import javax.media.control.FormatControl

/**
 * Represents a wrapper of a specific `FormatControl` instance which does not allow setting
 * its format using [FormatControl.setFormat].
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class ReadOnlyFormatControlDelegate
/**
 * Initializes a new `ReadOnlyFormatControlDelegate` instance which is to wrap a
 * specific `FormatControl` in order to prevent calls to its
 * [FormatControl.setFormat].
 *
 * @param formatControl the `FormatControl` which is to have calls to its
 * `FormatControl#setFormat(Format)` prevented
 */
(
        /**
         * The `FormatControl` wrapped by this instance.
         */
        private val formatControl: FormatControl) : AbstractFormatControl() {
    /**
     * Implements [Control.getControlComponent].
     *
     * @return a `Component` which represents UI associated with this instance if any;
     * otherwise, `null`
     */
    override fun getControlComponent(): Component {
        return formatControl.controlComponent
    }

    /**
     * Gets the `Format` of the owner of this `FormatControl`. Delegates to the
     * wrapped `FormatControl`.
     *
     * @return the `Format` of the owner of this `FormatControl`
     */
    override fun getFormat(): Format {
        return formatControl.format
    }

    /**
     * Gets the `Format`s supported by the owner of this `FormatControl`.
     * Delegates to the wrapped `FormatControl`.
     *
     * @return an array of `Format`s supported by the owner of this `FormatControl`
     */
    override fun getSupportedFormats(): Array<Format> {
        return formatControl.supportedFormats
    }

    /**
     * Implements [FormatControl.isEnabled].
     *
     * @return `true` if this track is enabled; otherwise, `false`
     */
    override fun isEnabled(): Boolean {
        return formatControl.isEnabled
    }

    /**
     * Implements [FormatControl.setEnabled].
     *
     * @param enabled `true` if this track is to be enabled; otherwise, `false`
     */
    override fun setEnabled(enabled: Boolean) {
        // Ignore the request because this instance is read-only.
    }

    /**
     * Implements [FormatControl.setFormat]. Not supported and just returns the
     * currently set format if the specified `Format` is supported and `null` if
     * it is not supported.
     *
     * @param format the `Format` to be set on this instance
     * @return the currently set `Format` after the attempt to set it on this instance if
     * `format` is supported by this instance and regardless of whether it was
     * actually set; `null` if `format` is not supported by this instance
     */
    override fun setFormat(format: Format): Format {
        return setFormat(this, format)!!
    }
}