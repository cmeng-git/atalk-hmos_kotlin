/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import org.atalk.util.OSUtils
import java.awt.event.ActionListener
import javax.swing.JCheckBox

/**
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class SIPCommCheckBox(i18nString: String?) : JCheckBox() {
    fun setSelected(otrEnabled: Boolean) {
        // TODO Auto-generated method stub
    }

    fun setEnabled(otrEnabled: Boolean) {
        // TODO Auto-generated method stub
    }

    fun addActionListener(actionListener: ActionListener?) {
        // TODO Auto-generated method stub
    }

    companion object {
        private const val serialVersionUID = 0L
        private val setContentAreaFilled = OSUtils.IS_WINDOWS || OSUtils.IS_LINUX
    }
}