/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import java.awt.Image
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.image.BufferedImage

/**
 * AnimatedImage will display a series of Images in a predetermined sequence.
 * This sequence can be configured to keep repeating or stop after a specified
 * number of cycles.
 *
 *
 * An AnimatedImage cannot be shared by different components. However,
 * the Images added to the AnimatedImage can be shared.
 *
 *
 * The animation sequence is a simple sequential display of each Image. When the
 * end is reached the animation restarts at the first Image. Images are
 * displayed in the order in which they were added. To create custom animation
 * sequences you will need to override the getNextIconIndex() and
 * isCycleCompleted() methods.
 *
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class AnimatedImage : BufferedImage, ActionListener {
    constructor(width: Int, height: Int, imageType: Int) : super(width, height, imageType) {        // TODO Auto-generated constructor stub
    }

    constructor(button: SIPCommButton?, i1: Image?, i2: Image?, i3: Image?) : super(0, 0, 0) {        // TODO Auto-generated constructor stub
    }

    override fun actionPerformed(paramActionEvent: ActionEvent) {
        // TODO Auto-generated method stub
    }

    fun pause() {
        // TODO Auto-generated method stub
    }

    fun start() {
        // TODO Auto-generated method stub
    }
}