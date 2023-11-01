/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.widgets

import android.animation.Animator
import android.animation.ObjectAnimator
import android.os.*
import android.view.*
import org.atalk.hmos.R.id

/**
 * Animated version of [LegacyClickableToastCtrl]
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ClickableToastController @JvmOverloads constructor(toastView: View, clickListener: View.OnClickListener, toastButtonId: Int = id.toast_msg) : LegacyClickableToastCtrl(toastView, clickListener, toastButtonId) {
    /**
     * The animator object used to animate toast `View` alpha property.
     */
    private val mToastAnimator = ObjectAnimator()

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

        // Initialize animator
        mToastAnimator.setPropertyName("alpha")
        mToastAnimator.target = toastView
    }

    /**
     * Shows the toast.
     *
     * @param immediate if `true` there wil be no animation.
     * @param message the toast text to use.
     */
    override fun showToast(immediate: Boolean, message: CharSequence?) {
        // Must process in UI thread as caller can be from background
        Handler(Looper.getMainLooper()).post {
            super.showToast(immediate, message)
            if (!immediate) {
                mToastAnimator.cancel()
                mToastAnimator.setFloatValues(0f, 1f)
                mToastAnimator.duration = SHOW_DURATION
                mToastAnimator.start()
            }
        }
    }

    /**
     * Hides the toast.
     *
     * @param immediate if `true` no animation will be used.
     */
    override fun hideToast(immediate: Boolean) {
        super.hideToast(immediate)
        if (!immediate) {
            mToastAnimator.cancel()
            mToastAnimator.setFloatValues(1f, 0f)
            mToastAnimator.duration = HIDE_DURATION
            mToastAnimator.start()
            mToastAnimator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onHide()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onHide() {
        super.onHide()
        toastView.alpha = 0f
    }

    companion object {
        /**
         * Show animation length
         */
        private const val SHOW_DURATION = 2000L

        /**
         * Hide animation length
         */
        private const val HIDE_DURATION = 2000L
    }
}