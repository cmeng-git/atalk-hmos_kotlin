/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiFragment
import java.io.Serializable

/**
 * Fragment can be used to display indeterminate progress dialogs.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ProgressDialogFragment : OSGiFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val progressView = inflater.inflate(R.layout.progress_dialog, container, false)
        setTextViewValue(progressView, R.id.textView, arguments!!.getString(ARG_MESSAGE))
        return progressView
    }

    companion object {
        /**
         * Argument used to retrieve the message that will be displayed next to the progress bar.
         */
        private const val ARG_MESSAGE = "progress_dialog_message"

        /**
         * Displays indeterminate progress dialog.
         *
         * @param title dialog's title
         * @param message the message to be displayed next to the progress bar.
         * @return dialog id that can be used to close the dialog
         * [DialogActivity.closeDialog].
         */
        fun showProgressDialog(title: String?, message: String?): Long {
            val extras = HashMap<String?, Serializable?>()
            extras[DialogActivity.EXTRA_CANCELABLE] = false
            extras[DialogActivity.EXTRA_REMOVE_BUTTONS] = true
            val args = Bundle()
            args.putString(ARG_MESSAGE, message)
            return DialogActivity.showCustomDialog(aTalkApp.globalContext, title,
                    ProgressDialogFragment::class.java.name, args, null, null, extras)
        }
    }
}