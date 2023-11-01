/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import javax.swing.ImageIcon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu

/**
 * The `SIPCommMenu` is very similar to a JComboBox. The main
 * component here is a JLabel only with an icon. When user clicks on the icon a
 * popup menu is opened, containing a list of icon-text pairs from which the
 * user could choose one item. When user selects the desired item, the icon of
 * the selected item is set to the main component label.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class SIPCommMenu : JMenu {
    constructor(string: String?) {
        // TODO Auto-generated constructor stub
    }

    constructor() {
        // TODO Auto-generated constructor stub
    }

    override fun removeAll() {
        // TODO Auto-generated method stub
    }

    override fun add(cbEnable: JCheckBoxMenuItem) {
        // TODO Auto-generated method stub
    }

    override fun addSeparator() {
        // TODO Auto-generated method stub
    }

    override fun isPopupMenuVisible(): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun setIcon(image: ImageIcon) {
        // TODO Auto-generated method stub
    }
}