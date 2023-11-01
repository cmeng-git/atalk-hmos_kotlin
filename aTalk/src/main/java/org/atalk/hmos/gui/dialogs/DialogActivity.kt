/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.dialogs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.internal.notifyAll
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiActivity
import java.io.Serializable

/**
 * `DialogActivity` can be used to display alerts without having parent `Activity`
 * (from services). <br></br> Simple alerts can be displayed using static method `showDialog(...)
` * .<br></br> Optionally confirm button's text and the listener can be supplied. It allows to
 * react to users actions. For this purpose use method `showConfirmDialog(...)`.<br></br>
 * For more sophisticated use cases content fragment class with it's arguments can be specified
 * in method `showCustomDialog()`. When they're present the alert message will be replaced
 * by the [Fragment]'s `View`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class DialogActivity : OSGiActivity() {
    /**
     * The dialog listener.
     */
    private var mListener: DialogListener? = null
    /**
     * Get the listener Id for the dialog
     *
     * @return the current mListenerId
     */
    /**
     * Dialog listener's id used to identify listener in [.listenersMap].
     * The value get be retrieved for use in caller reference when user click the button.
     */
    var listenerID = 0L
        private set

    /**
     * Flag remembers if the dialog was confirmed.
     */
    private var confirmed = false

    /**
     * `BroadcastReceiver` that listens for close dialog action.
     */
    private var commandIntentListener: CommandDialogListener? = null
    private var cancelable = false
    private var mContent: View? = null

    /**
     * {@inheritDoc}
     * cmeng: the onCreate get retrigger by android when developer option on keep activity is enabled
     * then the new dialog is not disposed.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        setContentView(R.layout.alert_dialog)
        mContent = findViewById(android.R.id.content)
        title = intent.getStringExtra(EXTRA_TITLE)

        // Message or custom content
        val contentFragment = intent.getStringExtra(EXTRA_CONTENT_FRAGMENT)
        if (contentFragment != null) {
            // Hide alert text
            ViewUtil.ensureVisible(mContent!!, R.id.alertText, false)

            // Display content fragment
            if (savedInstanceState == null) {
                try {
                    // Instantiate content fragment
                    val contentClass = Class.forName(contentFragment)
                    val fragment = contentClass.newInstance() as Fragment

                    // Set fragment arguments
                    fragment.arguments = intent.getBundleExtra(EXTRA_CONTENT_ARGS)

                    // Insert the fragment
                    supportFragmentManager.beginTransaction().replace(R.id.alertContent, fragment).commit()
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
            }
        } else {
            setTextViewValue(findViewById(android.R.id.content), R.id.alertText,
                    intent.getStringExtra(EXTRA_MESSAGE))
        }

        // Confirm button text
        val confirmTxt = intent.getStringExtra(EXTRA_CONFIRM_TXT)
        if (confirmTxt != null) {
            setTextViewValue(mContent!!, R.id.okButton, confirmTxt)
        }

        // Show cancel button if confirm label is not null
        ViewUtil.ensureVisible(mContent!!, R.id.cancelButton, confirmTxt != null)

        // Sets the listener
        listenerID = intent.getLongExtra(EXTRA_LISTENER_ID, -1)
        if (listenerID != -1L) {
            mListener = listenersMap[listenerID]
        }
        cancelable = intent.getBooleanExtra(EXTRA_CANCELABLE, false)
        // Prevents from closing the dialog on outside touch
        setFinishOnTouchOutside(cancelable)

        // Removes the buttons
        if (intent.getBooleanExtra(EXTRA_REMOVE_BUTTONS, false)) {
            ViewUtil.ensureVisible(mContent!!, R.id.okButton, false)
            ViewUtil.ensureVisible(mContent!!, R.id.cancelButton, false)
        }

        // Close this dialog on ACTION_CLOSE_DIALOG broadcast
        val dialogId = intent.getLongExtra(EXTRA_DIALOG_ID, -1L)
        if (dialogId != -1L) {
            commandIntentListener = CommandDialogListener(dialogId)
            val intentFilter = IntentFilter(ACTION_CLOSE_DIALOG)
            intentFilter.addAction(ACTION_FOCUS_DIALOG)
            localBroadcastManager.registerReceiver(commandIntentListener!!, intentFilter)

            // Adds this dialog to active dialogs list and notifies all waiting threads.
            synchronized(displayedDialogs) {
                displayedDialogs.add(dialogId)
                displayedDialogs.notifyAll()
            }
        }
    }

    /**
     * Returns the content fragment. It can contain alert message or be the custom fragment class instance.
     *
     * @return dialog content fragment.
     */
    val contentFragment: Fragment?
        get() = supportFragmentManager.findFragmentById(R.id.alertContent)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return if (!cancelable &&
                keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else super.onKeyUp(keyCode, event)
    }

    /**
     * Fired when the confirm button is clicked.
     *
     * @param v the confirm button view.
     */
    fun onOkClicked(v: View?) {
        if (mListener != null) {
            if (!mListener!!.onConfirmClicked(this)) {
                return
            }
        }
        confirmed = true
        finish()
    }

    /**
     * Fired when cancel button is clicked.
     *
     * @param v the cancel button view.
     */
    fun onCancelClicked(v: View?) {
        finish()
    }

    /**
     * Removes listener from the map.
     */
    override fun onDestroy() {
        super.onDestroy()
        /*
         * cmeng: cannot do here as this is triggered when dialog is obscured by other activity
         * when developer do keep activity is enable
        if (commandIntentListener != null) {
            localBroadcastManager.unregisterReceiver(commandIntentListener);

           // Notify about dialogs list change
           synchronized (displayedDialogs) {
                displayedDialogs.remove(listenerID);
                displayedDialogs.notifyAll();
            }
        }
        */
        // Notify that dialog was cancelled if confirmed == false
        if (mListener != null && !confirmed) {
            mListener!!.onDialogCancelled(this)
        }

        // Removes the listener from map
        if (listenerID != -1L) {
            listenersMap.remove(listenerID)
        }
    }

    /**
     * Broadcast Receiver to act on command received in #intent.getExtra()
     */
    internal inner class CommandDialogListener(private val mDialogId: Long) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getLongExtra(EXTRA_DIALOG_ID, -1) == mDialogId) {
                if (ACTION_CLOSE_DIALOG == intent.action) {
                    // Unregistered listener and finish this activity with dialogId
                    if (commandIntentListener != null) {
                        localBroadcastManager.unregisterReceiver(commandIntentListener!!)

                        // Notify about dialogs list change
                        synchronized(displayedDialogs) {
                            displayedDialogs.remove(listenerID)
                            displayedDialogs.notifyAll()
                        }
                        commandIntentListener = null
                    }
                    finish()
                } else if (ACTION_FOCUS_DIALOG == intent.action) {
                    mContent!!.bringToFront()
                }
            }
        }
    }

    /**
     * The listener that will be notified when user clicks the confirm button or dismisses the dialog.
     */
    interface DialogListener {
        /**
         * Fired when user clicks the dialog's confirm button.
         *
         * @param dialog source `DialogActivity`.
         */
        fun onConfirmClicked(dialog: DialogActivity): Boolean

        /**
         * Fired when user dismisses the dialog.
         *
         * @param dialog source `DialogActivity`
         */
        fun onDialogCancelled(dialog: DialogActivity)
    }

    companion object {
        /**
         * Dialog title extra.
         */
        const val EXTRA_TITLE = "title"

        /**
         * Dialog message extra.
         */
        const val EXTRA_MESSAGE = "message"

        /**
         * Optional confirm button label extra.
         */
        const val EXTRA_CONFIRM_TXT = "confirm_txt"

        /**
         * Dialog id extra used to listen for close dialog broadcast intents.
         */
        private const val EXTRA_DIALOG_ID = "dialog_id"

        /**
         * Optional listener ID extra(can be supplied only using method static `showConfirmDialog`.
         */
        const val EXTRA_LISTENER_ID = "listener_id"

        /**
         * Optional content fragment's class name that will be used instead of text message.
         */
        const val EXTRA_CONTENT_FRAGMENT = "fragment_class"

        /**
         * Optional content fragment's argument `Bundle`.
         */
        const val EXTRA_CONTENT_ARGS = "fragment_args"

        /**
         * Prevents from closing this activity on outside touch events and blocks the back key if set to `true`.
         */
        const val EXTRA_CANCELABLE = "cancelable"

        /**
         * Hide all buttons.
         */
        const val EXTRA_REMOVE_BUTTONS = "remove_buttons"

        /**
         * Static map holds listeners for currently displayed dialogs.
         */
        private val listenersMap = HashMap<Long, DialogListener>()

        /**
         * Static list holds existing dialog instances (since onCreate() until onDestroy()). Only
         * dialogs with valid id are listed here.
         */
        private val displayedDialogs = ArrayList<Long>()
        private val localBroadcastManager = LocalBroadcastManager.getInstance(aTalkApp.globalContext)

        /**
         * Name of the action which can be used to close dialog with given id supplied in
         * [.EXTRA_DIALOG_ID].
         */
        const val ACTION_CLOSE_DIALOG = "org.atalk.gui.close_dialog"

        /**
         * Name of the action which can be used to focus dialog with given id supplied in
         * [.EXTRA_DIALOG_ID].
         */
        const val ACTION_FOCUS_DIALOG = "org.atalk.gui.focus_dialog"

        /**
         * Fires [.ACTION_CLOSE_DIALOG] broadcast action in order to close the dialog identified
         * by given `dialogId`.
         *
         * @param dialogId dialog identifier returned when the dialog was created.
         */
        fun closeDialog(dialogId: Long) {
            val intent = Intent(ACTION_CLOSE_DIALOG)
            intent.putExtra(EXTRA_DIALOG_ID, dialogId)
            localBroadcastManager.sendBroadcast(intent)
        }

        /**
         * Fires [.ACTION_FOCUS_DIALOG] broadcast action in order to focus the dialog identified
         * by given `dialogId`.
         *
         * @param dialogId dialog identifier returned when the dialog was created.
         */
        fun focusDialog(dialogId: Long) {
            val intent = Intent(ACTION_FOCUS_DIALOG)
            intent.putExtra(EXTRA_DIALOG_ID, dialogId)
            localBroadcastManager.sendBroadcast(intent)
        }

        /**
         * Creates an `Intent` that will display a dialog with given `title` and content `message`.
         *
         * @param ctx Android context.
         * @param title dialog title that will be used
         * @param message dialog message that wil be used.
         *
         * @return an `Intent` that will display a dialog.
         */
        fun getDialogIntent(ctx: Context?, title: String?, message: String?): Intent {
            val alert = Intent(ctx, DialogActivity::class.java)
            alert.putExtra(EXTRA_TITLE, title)
            alert.putExtra(EXTRA_MESSAGE, message)
            alert.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            return alert
        }

        /**
         * Show simple alert that will be disposed when user presses OK button.
         *
         * @param ctx Android context.
         * @param title the dialog title that will be used.
         * @param message the dialog message that will be used.
         */
        fun showDialog(ctx: Context, title: String?, message: String?) {
            val alert = getDialogIntent(ctx, title, message)
            ctx.startActivity(alert)
        }

        /**
         * Shows a dialog for the given context and a title given by `titleId` and
         * message given by `messageId` with its optional arg.
         *
         * @param ctx the android `Context`
         * @param titleId the title identifier in the resources
         * @param messageId the message identifier in the resources
         * @param arg optional arg for the message expansion.
         */
        @JvmStatic
        fun showDialog(ctx: Context, titleId: Int, messageId: Int, vararg arg: Any?) {
            val alert = getDialogIntent(ctx, ctx.getString(titleId), ctx.getString(messageId, *arg))
            ctx.startActivity(alert)
        }

        /**
         * Shows confirm dialog allowing to handle confirm action using supplied `listener`.
         *
         * @param context Android context.
         * @param title dialog title that will be used
         * @param message dialog message that wil be used.
         * @param confirmTxt confirm button label.
         * @param listener the confirm action listener.
         */
        fun showConfirmDialog(context: Context, title: String?, message: String?,
                              confirmTxt: String?, listener: DialogListener?): Long {
            val alert = Intent(context, DialogActivity::class.java)
            val listenerId = System.currentTimeMillis()
            if (listener != null) {
                listenersMap[listenerId] = listener
                alert.putExtra(EXTRA_LISTENER_ID, listenerId)
            }
            alert.putExtra(EXTRA_TITLE, title)
            alert.putExtra(EXTRA_MESSAGE, message)
            alert.putExtra(EXTRA_CONFIRM_TXT, confirmTxt)
            alert.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(alert)
            return listenerId
        }

        /**
         * Shows confirm dialog allowing to handle confirm action using supplied `listener`.
         *
         * @param context the android context.
         * @param title dialog title Res that will be used
         * @param message the message identifier in the resources
         * @param confirmTxt confirm button label Res.
         * @param listener the `DialogInterface.DialogListener` to attach to the confirm button
         * @param arg optional arg for the message resource arg.
         */
        fun showConfirmDialog(context: Context, title: Int, message: Int,
                              confirmTxt: Int, listener: DialogListener?, vararg arg: Any?) {
            val res = context.resources
            showConfirmDialog(context, res.getString(title), res.getString(message, *arg),
                    res.getString(confirmTxt), listener)
        }

        /**
         * Show custom dialog. Alert text will be replaced by the [Fragment] created from
         * `fragmentClass` name. Optional `fragmentArguments` `Bundle` will be
         * supplied to created instance.
         *
         * @param context Android context.
         * @param title the title that will be used.
         * @param fragmentClass `Fragment`'s class name that will be used instead of text message.
         * @param fragmentArguments optional `Fragment` arguments `Bundle`.
         * @param confirmTxt the confirm button's label.
         * @param listener listener that will be notified on user actions.
         * @param extraArguments additional arguments with keys defined in [DialogActivity].
         */
        fun showCustomDialog(context: Context, title: String?, fragmentClass: String?,
                             fragmentArguments: Bundle?, confirmTxt: String?,
                             listener: DialogListener?, extraArguments: Map<String?, Serializable?>?): Long {
            val alert = Intent(context, DialogActivity::class.java)
            val dialogId = System.currentTimeMillis()
            alert.putExtra(EXTRA_DIALOG_ID, dialogId)
            if (listener != null) {
                listenersMap[dialogId] = listener
                alert.putExtra(EXTRA_LISTENER_ID, dialogId)
            }
            alert.putExtra(EXTRA_TITLE, title)
            alert.putExtra(EXTRA_CONFIRM_TXT, confirmTxt)
            alert.putExtra(EXTRA_CONTENT_FRAGMENT, fragmentClass)
            alert.putExtra(EXTRA_CONTENT_ARGS, fragmentArguments)
            if (extraArguments != null) {
                for (key in extraArguments.keys) {
                    alert.putExtra(key, extraArguments[key])
                }
            }
            alert.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            alert.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            context.startActivity(alert)
            return dialogId
        }

        /**
         * Waits until the dialog with given `dialogId` is opened.
         *
         * @param dialogId the id of the dialog we want to wait for.
         *
         * @return `true` if dialog has been opened or `false` if the dialog had not
         * been opened within 10 seconds after call to this method.
         */
        fun waitForDialogOpened(dialogId: Long): Boolean {
            synchronized(displayedDialogs) {
                return if (!displayedDialogs.contains(dialogId)) {
                    try {
                        (displayedDialogs as Object).wait(10000)
                        displayedDialogs.contains(dialogId)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                } else {
                    true
                }
            }
        }
    }
}