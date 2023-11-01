/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.controller

import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiFragment
import timber.log.Timber
import java.util.*

/**
 * The fragment is a controller which hides the given `View` after specified delay interval. To reset
 * and prevent from hiding for another period of call `show` method. This method will also instantly
 * display controlled `View` if it's currently hidden.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AutoHideController : OSGiFragment(), Animation.AnimationListener {
    // private Animation inAnimation;
    /**
     * Hide animation
     */
    private var outAnimation: Animation? = null

    /**
     * Controlled `View`
     */
    private var view: View? = null

    /**
     * Timer used for the hide task scheduling
     */
    private var autoHideTimer: Timer? = null

    /**
     * Hide `View` timeout
     */
    private var hideTimeout = 0L

    /**
     * Listener object
     */
    private var listener: AutoHideListener? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (activity is AutoHideListener) {
            listener = activity as AutoHideListener?
        }
        view = activity!!.findViewById(arguments!!.getInt(ARG_VIEW_ID))
        if (view == null) throw NullPointerException("The view is null")
        hideTimeout = arguments!!.getLong(ARG_HIDE_TIMEOUT)
        // inAnimation = AnimationUtils.loadAnimation(getActivity(),
        // R.anim.show_from_bottom);
        // inAnimation.setAnimationListener(this);
        outAnimation = AnimationUtils.loadAnimation(activity, R.anim.hide_to_bottom)
        outAnimation!!.setAnimationListener(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        show()
    }

    /**
     * Makes sure that hide task is scheduled. Cancels the previous one if is currently scheduled.
     */
    private fun reScheduleAutoHideTask() {
        // Cancel pending task if exists
        cancelAutoHideTask()
        autoHideTimer = Timer()
        autoHideTimer!!.schedule(AutoHideTask(), hideTimeout)
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        super.onPause()
        cancelAutoHideTask()
    }

    /**
     * Makes sure the hide task is cancelled.
     */
    private fun cancelAutoHideTask() {
        if (autoHideTimer != null) {
            autoHideTimer!!.cancel()
            autoHideTimer = null
        }
    }

    /**
     * Hides controlled `View`
     */
    fun hide() {
        if (!isViewVisible) return

        // This call is required to clear the timer task
        cancelAutoHideTask()
        // Starts hide animation
        view!!.startAnimation(outAnimation)
    }

    /**
     * Shows controlled `View` and/or resets hide delay timer.
     */
    fun show() {
        if (view == null) {
            Timber.e("The view has not been created yet")
            return
        }
        // This means that the View is hidden or animation is in progress
        if (autoHideTimer == null) {
            view!!.clearAnimation()
            // Need to re-layout the View
            view!!.visibility = View.GONE
            view!!.visibility = View.VISIBLE
            if (listener != null) {
                listener!!.onAutoHideStateChanged(this, View.VISIBLE)
            }
        }
        reScheduleAutoHideTask()
    }

    /**
     * Returns `true` if controlled `View` is currently visible.
     *
     * @return `true` if controlled `View` is currently visible.
     */
    private val isViewVisible: Boolean
        get() = view!!.visibility == View.VISIBLE

    /**
     * {@inheritDoc}
     */
    override fun onAnimationStart(animation: Animation) {
        // if(animation == inAnimation)
        // {
        // view.setVisibility(View.VISIBLE);
        // reScheduleAutoHideTask();
        // }
    }

    /**
     * {@inheritDoc}
     */
    override fun onAnimationEnd(animation: Animation) {
        // If it's hide animation and the task wasn't cancelled
        if (animation === outAnimation && autoHideTimer == null) {
            view!!.visibility = View.GONE
            if (listener != null) {
                listener!!.onAutoHideStateChanged(this, View.GONE)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onAnimationRepeat(animation: Animation) {}

    /**
     * Hide `View` timer task class.
     */
    internal inner class AutoHideTask : TimerTask() {
        override fun run() {
            runOnUiThread { hide() }
        }
    }

    /**
     * Interface which can be used for listening to controlled view visibility state changes. Must be implemented by
     * the parent `Activity`, which will be registered as a listener when this fragment is created.
     */
    interface AutoHideListener {
        /**
         * Fired when controlled `View` visibility is changed by this controller.
         *
         * @param source the source `AutoHideController` of the event.
         * @param visibility controlled `View` visibility state.
         */
        fun onAutoHideStateChanged(source: AutoHideController, visibility: Int)
    }

    companion object {
        /**
         * Argument key for the identifier of `View` that will be auto hidden. It must exist in the parent
         * `Activity` view hierarchy.
         */
        private const val ARG_VIEW_ID = "view_id"

        /**
         * Argument key for the delay interval, before the `View` will be hidden
         */
        private const val ARG_HIDE_TIMEOUT = "hide_timeout"

        /**
         * Creates new parametrized instance of `AutoHideController`.
         *
         * @param viewId identifier of the `View` that will be auto hidden
         * @param hideTimeout auto hide delay in ms
         * @return new parametrized instance of `AutoHideController`.
         */
        fun getInstance(viewId: Int, hideTimeout: Long): AutoHideController {
            val ahCtrl = AutoHideController()
            val args = Bundle()
            args.putInt(ARG_VIEW_ID, viewId)
            args.putLong(ARG_HIDE_TIMEOUT, hideTimeout)
            ahCtrl.arguments = args
            return ahCtrl
        }
    }
}