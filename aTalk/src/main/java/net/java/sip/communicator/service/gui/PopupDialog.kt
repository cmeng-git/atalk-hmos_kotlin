/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * A configurable popup dialog, that could be used from other services for
 * simple interactions with the user, throught the gui interface. This dialog
 * allows showing error, warning or info messages, prompting the user for simple
 * one field input or choice, or asking the user for certain confirmation.
 *
 * Three types of dialogs are differentiated: Message, Confirm and Input dialog.
 * Each of them has several show methods corresponging, allowing additional
 * specific configuration, like specifying or not a title, confirmation option
 * or initial value.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface PopupDialog : ExportedWindow {
    /**
     * Shows a question-message dialog requesting input from the user.
     *
     * @param message the `Object` to display.
     * @return user's input, or `null` meaning the user canceled the input
     */
    fun showInputPopupDialog(message: Any?): String?

    /**
     * Shows a question-message dialog requesting input from the user, with
     * the input value initialized to `initialSelectionValue`.
     *
     * @param message the `Object` to display
     * @param initialSelectionValue the value used to initialize the input field
     * @return user's input, or `null` meaning the user canceled the input
     */
    fun showInputPopupDialog(message: Any?, initialSelectionValue: String?): String?

    /**
     * Shows a dialog with title `title` and message type
     * `messageType`, requesting input from the user. The message
     * type is meant to be used by the ui implementation to determine the
     * icon of the dialog.
     *
     * @param message the `Object` to display
     * @param title the `String` to display in the dialog title bar
     * @param messageType the type of message that is to be displayed:
     * `ERROR_MESSAGE`,
     * `INFORMATION_MESSAGE`,
     * `WARNING_MESSAGE`,
     * `QUESTION_MESSAGE`,
     * or `PLAIN_MESSAGE`
     * @return user's input, or `null` meaning the user canceled the input
     */
    fun showInputPopupDialog(message: Any?, title: String?,
            messageType: Int): String?

    /**
     * Shows an input dialog, where all options like title, type of message
     * etc., could be configured. The user will be able to choose from
     * `selectionValues`, where `null` implies the
     * users can input whatever they wish.
     * `initialSelectionValue` is the initial value to prompt the user with.
     * It is up to the UI implementation to decide how best to represent the
     * `selectionValues`. In the case of swing per example it could
     * be a `JComboBox`, `JList` or
     * `JTextField`. The message type is meant to be used by the ui
     * implementation to determine the icon of the dialog.
     *
     * @param message the `Object` to display
     * @param title the `String` to display in the
     * dialog title bar
     * @param messageType the type of message to be displayed:
     * `ERROR_MESSAGE`,
     * `INFORMATION_MESSAGE`,
     * `WARNING_MESSAGE`,
     * `QUESTION_MESSAGE`,
     * or `PLAIN_MESSAGE`
     * @param selectionValues an array of `Object`s that
     * gives the possible selections
     * @param initialSelectionValue the value used to initialize the input field
     * @return user's input, or `null` meaning the user canceled the input
     */
    fun showInputPopupDialog(message: Any?, title: String?,
            messageType: Int, selectionValues: Array<Any?>?, initialSelectionValue: Any?): Any?

    /**
     * Shows an information-message dialog titled "Message".
     *
     * @param message the `Object` to display
     */
    fun showMessagePopupDialog(message: Any?)

    /**
     * Shows a dialog that displays a message using a default
     * icon determined by the `messageType` parameter.
     *
     * @param message the `Object` to display
     * @param title the title string for the dialog
     * @param messageType the type of message to be displayed:
     * `ERROR_MESSAGE`,
     * `INFORMATION_MESSAGE`,
     * `WARNING_MESSAGE`,
     * `QUESTION_MESSAGE`,
     * or `PLAIN_MESSAGE`
     */
    fun showMessagePopupDialog(message: Any?, title: String?, messageType: Int)

    /**
     * Shows a dialog that prompts the user for confirmation.
     *
     * @param message the `Object` to display
     * @return one of the YES_OPTION, NO_OPTION,.., XXX_OPTION, indicating the
     * option selected by the user
     */
    fun showConfirmPopupDialog(message: Any?): Int

    /**
     * Shows a dialog where the number of choices is determined
     * by the `optionType` parameter.
     *
     * @param message the `Object` to display
     * @param title the title string for the dialog
     * @param optionType an int designating the options available on the dialog:
     * `YES_NO_OPTION`, or
     * `YES_NO_CANCEL_OPTION`
     * @return one of the YES_OPTION, NO_OPTION,.., XXX_OPTION, indicating the
     * option selected by the user
     */
    fun showConfirmPopupDialog(message: Any?, title: String?, optionType: Int): Int

    /**
     * Shows a dialog where the number of choices is determined
     * by the `optionType` parameter, where the
     * `messageType` parameter determines the icon to display.
     * The `messageType` parameter is primarily used to supply
     * a default icon for the dialog.
     *
     * @param message the `Object` to display
     * @param title the title string for the dialog
     * @param optionType an integer designating the options available
     * on the dialog: `YES_NO_OPTION`,
     * or `YES_NO_CANCEL_OPTION`
     * @param messageType an integer designating the kind of message this is;
     * `ERROR_MESSAGE`,
     * `INFORMATION_MESSAGE`,
     * `WARNING_MESSAGE`,
     * `QUESTION_MESSAGE`,
     * or `PLAIN_MESSAGE`
     * @return one of the YES_OPTION, NO_OPTION,.., XXX_OPTION, indicating the option selected by the user
     */
    fun showConfirmPopupDialog(message: Any?, title: String?, optionType: Int, messageType: Int): Int

    /**
     * Implements the
     * `PopupDialog.showInputPopupDialog(Object, String, int, Object[],
     * Object)` method. Invokes the corresponding
     * `JOptionPane.showInputDialog` method.
     *
     * @param message the message to display
     * @param messageType the type of message to be displayed: ERROR_MESSAGE,
     * INFORMATION_MESSAGE, WARNING_MESSAGE, QUESTION_MESSAGE, or PLAIN_MESSAGE
     * @param title the String to display in the dialog title bar
     * @param selectionValues an array of Objects that gives the possible selections
     * @param initialSelectionValue the value used to initialize the input field
     * @param icon the icon to show in the input window.
     */
    fun showInputPopupDialog(message: Any?, title: String?,
            messageType: Int, selectionValues: Array<Any?>?, initialSelectionValue: Any?, icon: ByteArray?): Any?

    /**
     * Implements the `PopupDialog.showMessagePopupDialog(Object, String,
     * int)` method. Invokes the corresponding
     * `JOptionPane.showMessageDialog` method.
     *
     * @param message the Object to display
     * @param title the title string for the dialog
     * @param messageType the type of message to be displayed: ERROR_MESSAGE,
     * INFORMATION_MESSAGE, WARNING_MESSAGE, QUESTION_MESSAGE, or PLAIN_MESSAGE
     * @param icon the image to display in the message dialog.
     */
    fun showMessagePopupDialog(message: Any?, title: String?, messageType: Int, icon: ByteArray?)

    /**
     * Implements the `PopupDialog.showConfirmPopupDialog(Object, String,
     * int, int)` method. Invokes the corresponding
     * `JOptionPane.showConfirmDialog` method.
     *
     * @param message the Object to display
     * @param title the title string for the dialog
     * @param optionType an integer designating the options available on the
     * dialog: YES_NO_OPTION, or YES_NO_CANCEL_OPTION
     * @param messageType an integer designating the kind of message this is;
     * primarily used to determine the icon from the pluggable Look and Feel:
     * ERROR_MESSAGE, INFORMATION_MESSAGE, WARNING_MESSAGE, QUESTION_MESSAGE, or PLAIN_MESSAGE
     * @param icon the icon to display in the dialog
     */
    fun showConfirmPopupDialog(message: Any?, title: String?, optionType: Int, messageType: Int, icon: ByteArray?): Int

    companion object {
        val WINDOW_GENERAL_POPUP = WindowID("GeneralPopupWindow")
        //
        // Option types
        //
        /**
         * Type used for `showConfirmDialog`.
         */
        const val YES_NO_OPTION = 0

        /**
         * Type used for `showConfirmDialog`.
         */
        const val YES_NO_CANCEL_OPTION = 1

        /**
         * Type used for `showConfirmDialog`.
         */
        const val OK_CANCEL_OPTION = 2
        //
        // Return values.
        //
        /**
         * Return value from class method if YES is chosen.
         */
        const val YES_OPTION = 0

        /**
         * Return value from class method if NO is chosen.
         */
        const val NO_OPTION = 1

        /**
         * Return value from class method if CANCEL is chosen.
         */
        const val CANCEL_OPTION = 2

        /**
         * Return value form class method if OK is chosen.
         */
        const val OK_OPTION = 0

        /**
         * Return value from class method if user closes window without selecting anything.
         */
        const val CLOSED_OPTION = -1
        /*
     * Message types. Meant to be used by the UI implementation to determine
     * what icon to display and possibly what behavior to give based on the type.
     */
        /**
         * Used for error messages.
         */
        const val ERROR_MESSAGE = 0

        /**
         * Used for information messages.
         */
        const val INFORMATION_MESSAGE = 1

        /**
         * Used for warning messages.
         */
        const val WARNING_MESSAGE = 2

        /**
         * Used for questions.
         */
        const val QUESTION_MESSAGE = 3

        /**
         * No icon is used.
         */
        const val PLAIN_MESSAGE = -1
    }
}