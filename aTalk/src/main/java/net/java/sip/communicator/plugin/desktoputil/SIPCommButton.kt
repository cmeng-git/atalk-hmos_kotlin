/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import java.awt.Dimension
import java.awt.Image
import java.awt.event.ActionListener
import javax.swing.JButton

/**
 * The `SIPCommButton` is a very flexible `JButton` that allows
 * to configure its background, its icon, the look when a mouse is over it, etc.
 *
 * @author Yana Stamcheva
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
class SIPCommButton(`object`: Any?, object2: Any?) : JButton() {
    override fun setEnabled(b: Boolean) {
        // TODO Auto-generated method stub
    }

    override fun setPreferredSize(dimension: Dimension) {
        // TODO Auto-generated method stub
    }

    fun setToolTipText(i18nString: String?) {
        // TODO Auto-generated method stub
    }

    override fun addActionListener(actionListener: ActionListener) {
        // TODO Auto-generated method stub
    }

    fun setIconImage(image: Image?) {
        // TODO Auto-generated method stub
    }

    override fun repaint() {
        // TODO Auto-generated method stub
    }
}