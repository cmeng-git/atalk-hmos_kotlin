/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import java.awt.Component
import javax.media.control.FrameProcessingControl

/**
 * Provides a base implementation of `FrameProcessingControl` which keeps track of the
 * `minimalProcessing` property, switches its value to `true` when it's notified that
 * its owner is at least one frame behind and doesn't implement the `controlComponent` and
 * `framesDropped` properties.
 *
 * @author Lyubomir Marinov
 */
class FrameProcessingControlImpl : FrameProcessingControl {
    /**
     * Determines whether the owner of this `FrameProcessingControl` is to perform only the
     * minimum operations necessary to keep it working normally but without producing output.
     *
     * @return `true` if the owner of this `FrameProcessingControl` is to perform
     * only the minimum operations necessary to keep it working normally but without producing
     * output; otherwise, `false`
     */
    /**
     * The indicator which determines whether the owner of this `FrameProcessingControl` is
     * to perform only the minimum operations necessary to keep it working normally but without
     * producing output.
     */
    var isMinimalProcessing = false
        private set

    /**
     * Gets the UI `Component` associated with this `Control` object.
     *
     * @return the UI `Component` associated with this `Control` object
     */
    override fun getControlComponent(): Component? {
        /*
		 * We totally don't care about providing a UI component which controls frame drop from
		 * inside the media implementation.
		 */
        return null
    }

    /**
     * Gets the number of output frames that were dropped during processing since the last call to
     * this method.
     *
     * @return the number of output frame that were dropped during processing since the last
     * call to this method
     */
    override fun getFramesDropped(): Int {
        return 0 // Not implemented.
    }

    /**
     * Sets the number of frames the owner of this `FrameProcessingControl` is lagging
     * behind. It is a hint to do minimal processing for the next `framesBehind` frames in
     * order to catch up.
     *
     * @param framesBehind
     * the number of frames the owner of this `FrameProcessingControl` is lagging
     * behind
     */
    override fun setFramesBehind(framesBehind: Float) {
        setMinimalProcessing(framesBehind > 0)
    }

    /**
     * Sets the indicator which determines whether the owner of this
     * `FrameProcessingControl` is to perform only the minimal operations necessary to
     * keep it working normally but without producing output.
     *
     * @param minimalProcessing
     * `true` if minimal processing mode is to be turned on or `false` if
     * minimal processing mode is to be turned off
     * @return the actual minimal processing mode in effect after the set attempt
     */
    override fun setMinimalProcessing(minimalProcessing: Boolean): Boolean {
        isMinimalProcessing = minimalProcessing
        return isMinimalProcessing
    }
}