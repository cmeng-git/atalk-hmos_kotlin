/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

/**
 * The `AlertUIService` is a service that allows to show error messages and warnings.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface AlertUIService {
    /**
     * Shows an alert dialog with the given title message and exception corresponding to the error.
     *
     * @param title the title of the dialog
     * @param message the message to be displayed
     * @param e the exception corresponding to the error
     */
    fun showAlertDialog(title: String?, message: String?, e: Throwable?)

    /**
     * Shows an alert dialog with the given title, message and type of message.
     *
     * @param title the title of the error dialog
     * @param message the message to be displayed
     * @param type the dialog type (warning or error)
     */
    fun showAlertDialog(title: String?, message: String?, type: Int)

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the error dialog and the notification pop-up
     * @param message the message to be displayed in the error dialog and the pop-up
     */
    fun showAlertPopup(title: String?, message: String?)

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the error dialog and the notification pop-up
     * @param message the message to be displayed in the error dialog and the pop-up
     * @param e the exception that can be shown in the error dialog
     */
    fun showAlertPopup(title: String?, message: String?,
            e: Throwable?)

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the notification pop-up
     * @param message the message of the pop-up
     * @param errorDialogTitle the title of the error dialog
     * @param errorDialogMessage the message of the error dialog
     */
    fun showAlertPopup(title: String?, message: String?, errorDialogTitle: String?, errorDialogMessage: String?)

    /**
     * Shows an notification pop-up which can be clicked. An error dialog is
     * shown when the notification is clicked.
     *
     * @param title the title of the notification pop-up
     * @param message the message of the pop-up
     * @param errorDialogTitle the title of the error dialog
     * @param errorDialogMessage the message of the error dialog
     * @param e the exception that can be shown in the error dialog
     */
    fun showAlertPopup(title: String?, message: String?, errorDialogTitle: String?, errorDialogMessage: String?, e: Throwable?)

    /**
     * Releases the resources acquired by this instance throughout its lifetime and removes the listeners.
     */
    fun dispose()

    companion object {
        /**
         * Indicates that the OK button is pressed.
         */
        const val OK_RETURN_CODE = 0

        /**
         * Indicates that the Cancel button is pressed.
         */
        const val CANCEL_RETURN_CODE = 1

        /**
         * Indicates that the OK button is pressed and the Don't ask check box is checked.
         */
        const val OK_DONT_ASK_CODE = 2

        /**
         * The type of the alert dialog, which displays a warning instead of an error.
         */
        const val WARNING = 1

        /**
         * The type of alert dialog which displays a warning instead of an error.
         */
        const val ERROR = 0
    }
}