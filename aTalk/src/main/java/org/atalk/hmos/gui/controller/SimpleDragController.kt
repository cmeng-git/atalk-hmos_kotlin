/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.controller

import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener

/**
 * The controller when set as `View.OnTouchListener` of given `View` makes it draggable on the screen.
 *
 * @author Pawel domas
 * @author Eng Chong Meng
 */
class SimpleDragController : OnTouchListener {
    /**
     * {@inheritDoc}
     */
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        val action = motionEvent.action
        if (action == MotionEvent.ACTION_MOVE) {
            view.x = view.x + motionEvent.x - view.width / 2
            view.y = view.y + motionEvent.y - view.height / 2
        }
        return true
    }
}