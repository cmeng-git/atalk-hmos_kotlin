/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * The `WindowID` wraps a string which is meant to point to an
 * application dialog, like per example a "Configuration" dialog or
 * "Add contact" dialog.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class WindowID
/**
 * Creates a new WindowID.
 * @param dialogName the name of the dialog
 */
(
        /**
         * Get the ID.
         *
         * @return the ID
         */
        val iD: String)