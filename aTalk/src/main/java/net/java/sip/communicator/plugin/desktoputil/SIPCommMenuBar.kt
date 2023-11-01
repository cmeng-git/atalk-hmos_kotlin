/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import net.java.sip.communicator.plugin.desktoputil.plaf.SIPCommMenuBarUI
import net.java.sip.communicator.util.skin.Skinnable
import javax.swing.JMenuBar
import javax.swing.UIManager

/**
 * The SIPCommMenuBar is a `JMenuBar` without border decoration that can
 * be used as a container for other components, like selector boxes that won't
 * need a menu decoration.
 *
 * @author Yana Stamcheva
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
class SIPCommMenuBar : JMenuBar(), Skinnable {
    /**
     * Creates an instance of `SIPCommMenuBar`.
     */
    init {
        loadSkin()
    }

    /**
     * Reload UI defs.
     */
    override fun loadSkin() {}

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
        /**
         * Returns the name of the L&F class that renders this component.
         *
         * @return the string "TreeUI"
         * @see JComponent.getUIClassID
         *
         * @see UIDefaults.getUI
         */
        /**
         * Class id key used in UIDefaults.
         */
        val uIClassID = "SIPCommMenuBarUI"

        /**
         * Adds the ui class to UIDefaults.
         */
        init {
            UIManager.getDefaults()[uIClassID] = SIPCommMenuBarUI::class.java.name
        }
    }
}