/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * A window that could be shown, hidden, resized, moved, etc. Meant to be used
 * from other services to show an application window, like for example a
 * "Configuration" or "Add contact" window.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ExportedWindow {
    /**
     * Returns the WindowID corresponding to this window. The window id should
     * be one of the defined in this class XXX_WINDOW constants.
     *
     * @return the WindowID corresponding to this window
     */
    val identifier: WindowID?
    /**
     * Returns TRUE if the component is visible and FALSE otherwise.
     *
     * @return `true` if the component is visible and
     * `false` otherwise.
     */
    /**
     * Shows or hides this component.
     * @param isVisible indicates whether to set this window visible or hide it
     */
    var isVisible: Boolean

    /**
     * Returns TRUE if this component is currently the focused component,
     * FALSE - otherwise.
     * @return TRUE if this component is currently the focused component,
     * FALSE - otherwise.
     */
    val isFocused: Boolean

    /**
     * Brings the focus to this window.
     */
    fun bringToFront()

    /**
     * Resizes the window with the given width and height.
     *
     * @param width The new width.
     * @param height The new height.
     */
    fun setSize(width: Int, height: Int)

    /**
     * Moves the window to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    fun setLocation(x: Int, y: Int)

    /**
     * Minimizes the window.
     */
    fun minimize()

    /**
     * Maximizes the window.
     */
    fun maximize()

    /**
     * The source of the window
     * @return the source of the window
     */
    val source: Any?

    /**
     * This method can be called to pass any params to the exported window. This
     * method will be automatically called by
     * [UIService.getExportedWindow] in order to set
     * the parameters passed.
     *
     * @param windowParams the parameters to pass.
     */
    fun setParams(windowParams: Array<Any?>?)

    companion object {
        /**
         * The add contact window identifier.
         */
        val ADD_CONTACT_WINDOW = WindowID("AddContactWindow")

        /**
         * The about window identifier.
         */
        val ABOUT_WINDOW = WindowID("AboutWindow")

        /**
         * The chat window identifier.
         */
        val CHAT_WINDOW = WindowID("ChatWindow")

        /**
         * The main (contact list) window identifier.
         */
        val MAIN_WINDOW = WindowID("MainWindow")
    }
}