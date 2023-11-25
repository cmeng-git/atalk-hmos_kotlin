/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer

import org.atalk.impl.neomedia.control.ControlsAdapter
import timber.log.Timber
import javax.media.Format
import javax.media.Renderer

/**
 * Provides an abstract base implementation of `Renderer` in order to facilitate extenders.
 *
 * @param <T> the type of `Format` of the media data processed as input by `AbstractRenderer`
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
</T> */
abstract class AbstractRenderer<T : Format> : ControlsAdapter(), Renderer {
    /**
     * The `Format` of the media data processed as input by this `Renderer`.
     */
    protected var inputFormat: T? = null

    /**
     * Resets the state of this `PlugIn`.
     */
    override fun reset() {
        // TODO Auto-generated method stub
    }

    /**
     * Sets the `Format` of the media data to be rendered by this `Renderer`.
     *
     * @param format the `Format` of the media data to be rendered by this `Renderer`
     * @return `null` if the specified `format` is not compatible with this
     * `Renderer`; otherwise, the `Format` which has been successfully set
     */
    override fun setInputFormat(format: Format): Format? {
        var matchingFormat: Format? = null
        for (supportedInputFormat in supportedInputFormats) {
            if (supportedInputFormat.matches(format)) {
                matchingFormat = supportedInputFormat.intersects(format)
                break
            }
        }

        if (matchingFormat == null) return null
        val t = matchingFormat as T
        inputFormat = t
        return inputFormat
    }

    companion object {
        /**
         * Changes the priority of the current thread to a specific value.
         *
         * @param threadPriority the priority to set the current thread to
         */
        fun useThreadPriority(threadPriority: Int) {
            try {
                Thread.currentThread().priority = threadPriority
            } catch (ex: Exception) {
                when (ex) {
                    is IllegalArgumentException,
                    is SecurityException,
                    -> {
                        Timber.w(ex, "Failed to use thread priority: %s", threadPriority)
                    }
                }
            }
        }
    }
}