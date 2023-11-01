package org.atalk.util

import android.view.View
import androidx.fragment.app.FragmentActivity

/**
 * Class responsible for changing the view from full screen to non-full screen and vice versa.
 *
 * @author Pierfrancesco Soffritti
 * @author Eng Chong Meng
 */
class FullScreenHelper(private val context: FragmentActivity, vararg views: View) {
    private val views: Array<out View>

    /**
     * @param context
     * @param views to hide/show
     */
    init {
        this.views = views
    }

    /**
     * call this method to enter full screen
     */
    fun enterFullScreen() {
        val decorView = context.window.decorView
        hideSystemUi(decorView)
        for (view in views) {
            view.visibility = View.GONE
            view.invalidate()
        }
    }

    /**
     * call this method to exit full screen
     */
    fun exitFullScreen() {
        val decorView = context.window.decorView
        showSystemUi(decorView)
        for (view in views) {
            view.visibility = View.VISIBLE
            view.invalidate()
        }
    }

    private fun hideSystemUi(mDecorView: View) {
        mDecorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun showSystemUi(mDecorView: View) {
        mDecorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}