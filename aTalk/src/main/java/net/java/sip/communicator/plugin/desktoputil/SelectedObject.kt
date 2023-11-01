/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil

import javax.swing.Icon

/**
 * A convenience class used to store combobox complex objects.
 * The `SelectedObject` is used for all account and status combo boxes
 * throughout this gui implementation.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class SelectedObject {
    /**
     * Returns the text of this `SelectedObject`.
     *
     * @return the text of this `SelectedObject`.
     */
    var text: String? = null
        private set

    /**
     * Returns the icon of this `SelectedObject`.
     *
     * @return the icon of this `SelectedObject`.
     */
    var icon: Icon
        private set

    /**
     * Returns the real object behind this `SelectedObject`.
     *
     * @return the real object behind this `SelectedObject`.
     */
    var `object`: Any
        private set

    /**
     * Creates an instance of `SelectedObject` by specifying the text,
     * icon and object associated with it.
     *
     * @param text The text.
     * @param icon The icon.
     * @param object The object.
     */
    constructor(text: String?, icon: Icon, `object`: Any) {
        this.text = text
        this.icon = icon
        this.`object` = `object`
    }

    /**
     * Creates an instance of `SelectedObject` by specifying the
     * icon and object associated with it.
     *
     * @param icon The icon.
     * @param object The object.
     */
    constructor(icon: Icon, `object`: Any) {
        this.icon = icon
        this.`object` = `object`
    }
}