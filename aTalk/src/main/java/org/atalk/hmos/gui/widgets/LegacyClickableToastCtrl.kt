/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.widgets

import android.os.*
import android.text.TextUtils
import android.view.*
import android.widget.*
import org.atalk.hmos.R.id

/**
 * The controller used for displaying a custom toast that can be clicked.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class LegacyClickableToastCtrl @JvmOverloads constructor(
        /**
         * The toast `View` container.
         */
        protected var toastView: View,
        /**
         * The listener that will be notified when the toast is clicked.
         */
        private val clickListener: View.OnClickListener, toastButtonId: Int = id.toast_msg) {
    /**
     * The `TextView` displaying message text.
     */
    private val messageView = toastView.findViewById<TextView>(id.toast_msg)

    /**
     * Handler object used for hiding the toast if it's not clicked.
     */
    private val hideHandler = Handler()

    /**
     * State object for message text.
     */
    private var toastMessage: CharSequence? = null

    /**
     * Shows the toast.
     *
     * @param immediate if `true` there wil be no animation.
     * @param message the toast text to use.
     */
    open fun showToast(immediate: Boolean, message: CharSequence?) {
        toastMessage = message
        messageView.text = toastMessage
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, DISPLAY_DURATION)
        toastView.visibility = View.VISIBLE
    }

    /**
     * Hides the toast.
     *
     * @param immediate if `true` no animation will be used.
     */
    open fun hideToast(immediate: Boolean) {
        hideHandler.removeCallbacks(hideRunnable)
        if (immediate) {
            onHide()
        }
    }

    /**
     * Performed to hide the toast view.
     */
    protected open fun onHide() {
        toastView.visibility = View.GONE
        toastMessage = null
    }

    /**
     * {@inheritDoc}
     */
    fun onSaveInstanceState(outState: Bundle) {
        outState.putCharSequence("toast_message", toastMessage)
    }

    /**
     * {@inheritDoc}
     */
    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            toastMessage = savedInstanceState.getCharSequence("toast_message")
            if (!TextUtils.isEmpty(toastMessage)) {
                showToast(true, toastMessage)
            }
        }
    }

    /**
     * Hides the toast after delay.
     */
    private val hideRunnable = Runnable { hideToast(false) }
    /**
     * Creates new instance of `ClickableToastController`.
     *
     * @param toastView the `View` that will be animated. Must contain `R.id.toast_msg` `TextView`.
     * @param clickListener the click listener that will be notified when the toast is clicked.
     * @param toastButtonId the id of `View` contained in `toastView
    ` *  that will be used as a button.
     */
    /**
     * Creates new instance of `ClickableToastController`.
     *
     * @param toastView the `View` that will be animated. Must contain `R.id.toast_msg` `TextView`.
     * @param clickListener the click listener that will be notified when the toast is clicked.
     */
    init {
        toastView.findViewById<View>(toastButtonId).setOnClickListener { view: View? ->
            hideToast(false)
            clickListener.onClick(view)
        }
        hideToast(true)
    }

    companion object {
        /**
         * How long the toast will be displayed.
         */
        private const val DISPLAY_DURATION = 10000L
    }
}