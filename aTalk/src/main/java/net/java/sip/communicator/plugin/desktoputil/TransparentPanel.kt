/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import java.awt.LayoutManager

/**
 * Provides compatibility with source code written prior to the inception of
 * libjitsi.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class TransparentPanel : org.atalk.util.swing.TransparentPanel {
    constructor() {}
    constructor(layout: LayoutManager?) : super(layout) {}

    companion object {
        private const val serialVersionUID = 0L
    }
}