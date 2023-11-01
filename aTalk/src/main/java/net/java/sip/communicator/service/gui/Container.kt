/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * The `Container` wraps a string which is meant to point
 * to a plugin container. The plugin container is a GUI container that contains
 * plugin components.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class Container
/**
 * Creates a `Container` from the given container name.
 *
 * @param containerName the name of the container.
 *
 */
(
        /**
         * The name of the container.
         */
        val iD: String) {
    /**
     * Returns the String identifier of this `Container`.
     *
     * @return the String identifier of this `Container`.
     */

    /**
     * Indicates whether some other object is "equal to" this one which in terms
     * of containers translates to having equal identifiers. If the given object
     * is a String we'll compare it directly to the identifier of our container.
     *
     *
     * @param   obj   the reference object with which to compare.
     * @return  `true` if this container has the same id as that of
     * the `obj` argument or if the object argument is the id of this
     * container.
     */
    override fun equals(obj: Any?): Boolean {
        if (obj == null) return false
        return if (obj is Container) {
            iD == obj.iD
        } else if (obj is String) {
            iD == obj
        } else false
    }

    override fun hashCode(): Int {
        return iD.hashCode()
    }

    companion object {
        const val CONTAINER_ID = "CONTAINER_ID"

        /**
         * Main application window "file menu" container.
         */
        val CONTAINER_FILE_MENU = Container("CONTAINER_FILE_MENU")

        /**
         * Main application window "tools menu" container.
         */
        val CONTAINER_TOOLS_MENU = Container("CONTAINER_TOOLS_MENU")

        /**
         * Main application window "view menu" container.
         */
        val CONTAINER_VIEW_MENU = Container("CONTAINER_VIEW_MENU")

        /**
         * Main application window "help menu" container.
         */
        val CONTAINER_HELP_MENU = Container("CONTAINER_HELP_MENU")

        /**
         * Main application window "settings menu" container.
         */
        val CONTAINER_SETTINGS_MENU = Container("CONTAINER_SETTINGS_MENU")

        /**
         * Main application window main toolbar container.
         */
        val CONTAINER_MAIN_TOOL_BAR = Container("CONTAINER_MAIN_TOOL_BAR")

        /**
         * The container added on the south of the account panel above the
         * contact list.
         */
        val CONTAINER_ACCOUNT_SOUTH = Container("CONTAINER_ACCOUNT_SOUTH")

        /**
         * Main application window main tabbedpane container.
         */
        val CONTAINER_MAIN_TABBED_PANE = Container("CONTAINER_MAIN_TABBED_PANE")

        /**
         * Chat window toolbar container.
         */
        val CONTAINER_CHAT_TOOL_BAR = Container("CONTAINER_CHAT_TOOL_BAR")

        /**
         * Main application window "right button menu" over a contact container.
         */
        val CONTAINER_CONTACT_RIGHT_BUTTON_MENU = Container("CONTAINER_CONTACT_RIGHT_BUTTON_MENU")

        /**
         * Accounts window "right button menu" over an account.
         */
        val CONTAINER_ACCOUNT_RIGHT_BUTTON_MENU = Container("CONTAINER_ACCOUNT_RIGHT_BUTTON_MENU")

        /**
         * Main application window "right button menu" over a group container.
         */
        val CONTAINER_GROUP_RIGHT_BUTTON_MENU = Container("CONTAINER_GROUP_RIGHT_BUTTON_MENU")

        /**
         * Chat write panel container.
         */
        val CONTAINER_CHAT_WRITE_PANEL = Container("CONTAINER_CHAT_WRITE_PANEL")

        /**
         * Chat window "menu bar" container.
         */
        val CONTAINER_CHAT_MENU_BAR = Container("CONTAINER_CHAT_MENU_BAR")

        /**
         * Chat window "file menu" container.
         */
        val CONTAINER_CHAT_FILE_MENU = Container("CONTAINER_CHAT_FILE_MENU")

        /**
         * Chat window "edit menu" container.
         */
        val CONTAINER_CHAT_EDIT_MENU = Container("CONTAINER_CHAT_EDIT_MENU")

        /**
         * Chat window "settings menu" container.
         */
        val CONTAINER_CHAT_SETTINGS_MENU = Container("CONTAINER_CHAT_SETTINGS_MENU")

        /**
         * Chat window "help menu" container.
         */
        val CONTAINER_CHAT_HELP_MENU = Container("CONTAINER_CHAT_HELP_MENU")

        /**
         * Chat window container.
         */
        val CONTAINER_CHAT_WINDOW = Container("CONTAINER_CHAT_WINDOW")

        /**
         * Main window container.
         */
        val CONTAINER_MAIN_WINDOW = Container("CONTAINER_MAIN_WINDOW")

        /**
         * The contact list panel.
         */
        val CONTAINER_CONTACT_LIST = Container("CONTAINER_CONTACT_LIST")

        /**
         * Call history panel container.
         */
        val CONTAINER_CALL_HISTORY = Container("CONTAINER_CALL_HISTORY")

        /**
         * Call dialog container.
         */
        val CONTAINER_CALL_DIALOG = Container("CONTAINER_CALL_DIALOG")

        /**
         * Call panel container.
         */
        val CONTAINER_CALL_BUTTONS_PANEL = Container("CONTAINER_CALL_BUTTONS_PANEL")

        /**
         * Status bar container.
         */
        val CONTAINER_STATUS_BAR = Container("CONTAINER_STATUS_BAR")

        /**
         * Status bar container.
         */
        val CONTAINER_CHAT_STATUS_BAR = Container("CONTAINER_CHAT_STATUS_BAR")
        /*
     * Constraints
     */
        /**
         * Indicates the most left/top edge of a container.
         */
        const val START = "Start"

        /**
         * Indicates the most right/bottom edge of a container.
         */
        const val END = "End"

        /**
         * Indicates the top edge of a container.
         */
        const val TOP = "Top"

        /**
         * Indicates the bottom edge of a container.
         */
        const val BOTTOM = "Bottom"

        /**
         * Indicates the left edge of a container.
         */
        const val LEFT = "Left"

        /**
         * Indicates the right edge of a container.
         */
        const val RIGHT = "Right"
    }
}