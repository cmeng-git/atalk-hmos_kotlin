package org.atalk.hmos.gui.widgets

import android.graphics.*
import android.graphics.drawable.Drawable

/**
 * Action item, displayed as menu with icon and text.
 *
 * @author Lorensius. W. L. T <lorenz></lorenz>@londatiga.net>
 * Contributors: - Kevin Peck <kevinwpeck></kevinwpeck>@gmail.com>
 * @author Eng Chong Meng
 */
class ActionMenuItem(actionId: Int,
        /**
         * Set action title
         *
         * @param title action title
         */
        var title: String,
        /**
         * Set action icon
         *
         * @param icon [Drawable] action icon
         */
        var icon: Drawable) {
    /**
     * Get thumb image
     *
     * @return Thumb image
     */
    /**
     * Set thumb
     *
     * @param thumb Thumb image
     */
    var thumb: Bitmap? = null
    /**
     * Get action icon
     *
     * @return [Drawable] action icon
     */
    /**
     * Get action title
     *
     * @return action title
     */
    /**
     * @return Our action id
     */
    /**
     * Set action id
     *
     * @param actionId Action id for this action
     */
    var actionId = -1
    /**
     * Check if item is selected
     *
     * @return true or false
     */
    /**
     * Set selected flag;
     *
     * @param selected Flag to indicate the item is selected
     */
    var isSelected = false
    /**
     * @return true if button is sticky, menu stays visible after press
     */
    /**
     * Set sticky status of button
     *
     * @param sticky true for sticky, pop up sends event but does not disappear
     */
    var isSticky = false

    /**
     * Constructor
     *
     * @param actionId Action id for case statements
     * @param title Title
     * @param icon Icon to use
     */
    init {
        this.actionId = actionId
    }
}