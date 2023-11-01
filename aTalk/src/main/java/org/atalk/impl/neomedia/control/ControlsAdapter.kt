/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

/**
 * Provides a default implementation of `Controls` which does not expose any controls.
 *
 * @author Lyubomir Marinov
 */
open class ControlsAdapter : AbstractControls() {
    /**
     * Implements [javax.media.Controls.getControls]. Gets the controls available for the
     * owner of this instance. The current implementation returns an empty array because it has no
     * available controls.
     *
     * @return an array of `Object`s which represent the controls available for the owner of this instance
     */
    override fun getControls(): Array<Any> {
        return EMPTY_CONTROLS
    }

    companion object {
        /**
         * The constant which represents an empty array of controls. Explicitly defined in order to
         * avoid unnecessary allocations.
         */
        val EMPTY_CONTROLS = emptyArray<Any>()
    }
}