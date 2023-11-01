/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import java.awt.Component
import javax.media.Controls
import javax.media.Format
import javax.media.control.FormatControl

/**
 * Provides an abstract implementation of `FormatControl` which facilitates implementers by requiring
 * them to implement just [FormatControl.getSupportedFormats] and [FormatControl.getFormat].
 * https://docs.oracle.com/cd/E17802_01/j2se/javase/technologies/desktop/media/jmf/2.1.1/apidocs/javax/media/control/FormatControl.html
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractFormatControl : FormatControl {
    /**
     * The `Format` of this `FormatControl` and, respectively, of the media data of its owner.
     */
    protected var mFormat: Format? = null

    /**
     * The indicator which determines whether this track is enabled.
     */
    private var enabled = false

    /**
     * Implements Control.getControlComponent. Returns `null`.
     *
     * @return a `Component` which represents UI associated with this instance if any; otherwise, `null`
     */
    override fun getControlComponent(): Component? {
        // No Component is exported by this instance.
        return null
    }

    /**
     * Implements [FormatControl.isEnabled].
     *
     * @return `true` if this track is enabled; otherwise, `false`
     */
    override fun isEnabled(): Boolean {
        return enabled
    }

    /**
     * Implements [FormatControl.setEnabled].
     *
     * @param enabled `true` if this track is to be enabled; otherwise, `false`
     */
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Obtain the format that this object is set to.
     *
     * @return the current format.
     */
    override fun getFormat(): Format? {
        return mFormat
    }

    /**
     * Implements [FormatControl.setFormat]. Not supported and just returns the currently
     * set format if the specified `Format` is supported and `null` if it is not supported.
     *
     * @param format the `Format` to be set on this instance
     * @return the currently set `Format` after the attempt to set it on this instance if
     * `format` is supported by this instance and regardless of whether it was
     * actually set; `null` if `format` is not supported by this instance
     */
    override fun setFormat(format: Format): Format? {
        return setFormat(this, format)
    }

    /**
     * Implements setting the `Format` of a specific `FormatControl` as documented for
     * [for JMF][FormatControl.setFormat] in the case of not supporting `Format` setting.
     *
     * https://docs.oracle.com/cd/E17802_01/j2se/javase/technologies/desktop/media/jmf/2.1.1/apidocs/javax/media/control/FormatControl.html
     * Sets the data format. The method returns null if the format is not supported. Otherwise, it returns the format that's actually set.
     *
     * @param formatControl the `FormatControl` for which the functionality is implemented
     * @param format the `Format` specified to be set to `formatControl` and which will be
     * ignored in accord with the documentation of `FormatControl#setFormat(Format)`
     * @return null if the format is not supported; otherwise return the format that's actually set
     */
    fun setFormat(formatControl: FormatControl, format: Format?): Format? {
        mFormat = null
        if (format != null) {
            // Determine whether the specified format is supported by this instance
            for (sFormat in formatControl.supportedFormats) if (sFormat.matches(format)) {
                mFormat = sFormat
                break
            }
        }
        return mFormat
    }

    companion object {
        /**
         * Gets an array of `FormatControl` instances from the list of controls available for a
         * specific `Controls` implementation.
         *
         * @param controlsImpl the `Controls` implementation from which the `FormatControl`
         * instances are to be retrieved
         * @return an array of `FormatControl` instances from the list of controls available for
         * the specified `Controls` implementation
         */
        fun getFormatControls(controlsImpl: Controls): Array<FormatControl> {
            val formatControls = ArrayList<FormatControl>()
            for (control in controlsImpl.controls) {
                if (control is FormatControl) formatControls.add(control)
            }
            return formatControls.toTypedArray()
        }
    }
}