/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.actionbar

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.text.method.ScrollingMovementMethod
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.AndroidImageUtil
import timber.log.Timber

/**
 * The `ActionBarUtil` provides utility methods for setting action bar avatar and display name.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object ActionBarUtil {
    /**
     * Sets the action bar title for the given activity.
     *
     * @param activity the `Activity`, for which we set the action bar title
     * @param title the title string to set
     */
    fun setTitle(activity: AppCompatActivity?, title: CharSequence?) {
        val actionBar = activity!!.supportActionBar
        // Some activities don't have ActionBar
        if (actionBar != null) {
            if (actionBar.customView != null) {
                val actionBarText = activity.findViewById<TextView>(R.id.actionBarTitle)
                if (actionBarText != null) actionBarText.text = title
            } else actionBar.setTitle(title)
        }
    }
    //    public static void setPrefTitle(AppCompatActivity activity, int resId) {
    //        ActionBar actionBar = activity.getSupportActionBar();
    //        String title = activity.getResources().getString(resId);
    //
    //        // Some activities don't have ActionBar
    //        if (actionBar != null) {
    //            if (actionBar.getCustomView() != null) {
    //                TextView titleText = activity.findViewById(R.R.id.actionBarTitle);
    //                if (titleText != null) {
    //                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) titleText.getLayoutParams();
    //                    params.addRule(RelativeLayout.CENTER_IN_PARENT);
    //                    titleText.setLayoutParams(params);
    //
    //                    titleText.setTextSize(Dimension.PX, 20f);
    //                    titleText.setText(title);
    //                }
    //            }
    //            else
    //                actionBar.setTitle(title);
    //        }
    //    }
    /**
     * Sets the action bar subtitle for the given activity. The text may contain
     * a, Account user online status
     * b. The chat buddy last seen date or online status
     * c. Callee Jid during media call
     *
     * @param activity the `Activity`, for which we set the action bar subtitle
     * @param subtitle the subtitle string to set
     */
    fun setSubtitle(activity: AppCompatActivity, subtitle: String?) {
        val actionBar = activity.supportActionBar
        if (actionBar != null) {
            val statusText = activity.findViewById<TextView>(R.id.actionBarStatus)
            // statusText is null while search option is selected
            if (statusText != null) {
                statusText.text = subtitle
                statusText.movementMethod = ScrollingMovementMethod()
            }
        }
    }

    /**
     * Gets the action bar subTitle for the given activity.
     *
     * @param activity the `Activity`, for which we get the action bar title
     *
     * @return the title string
     */
    fun getStatus(activity: AppCompatActivity?): String? {
        if (activity != null) {
            activity.supportActionBar ?: return null

            // Some activities don't have ActionBar
            val actionBarText = activity.findViewById<TextView>(R.id.actionBarStatus)
            return actionBarText.text.toString()
        }
        return null
    }

    /**
     * Get the user offline status during the selected Locale.
     * Quiet messy to use this method as the user online status is being updated from multiple places
     * including server presence status sending etc.
     *
     * @param activity the caller context
     *
     * @return use online status
     */
    fun isOffline(activity: AppCompatActivity): Boolean {
        val offlineLabel = activity.resources.getString(R.string.service_gui_OFFLINE)
        return offlineLabel == getStatus(activity)
    }

    /**
     * Set the action bar status for the given activity.
     *
     * @param activity the `Activity`, for which we get the action bar title
     * @param statusIcon display Icon per the user status
     */
    fun setStatusIcon(activity: AppCompatActivity, statusIcon: ByteArray?) {
        val actionBar = activity.supportActionBar
        if (actionBar != null) {
            val avatarStatusBmp = AndroidImageUtil.bitmapFromBytes(statusIcon)
            if (avatarStatusBmp != null) {
                val actionBarStatus = activity.findViewById<ImageView>(R.id.globalStatusIcon)
                // actionBarStatus is null while search option is selected
                actionBarStatus?.setImageBitmap(avatarStatusBmp)
            }
        }
    }

    /**
     * Sets the avatar icon of the action bar.
     *
     * @param activity the current activity where the status should be displayed
     * @param avatar the avatar to display
     */
    fun setAvatar(activity: AppCompatActivity?, avatar: ByteArray?) {
        // The default avatar drawable for display on ActionBar
        val avatarDrawable = getDefaultAvatarDrawable(activity)

        // cmeng: always clear old avatar picture when pager scroll to different chat fragment
        // and invalidate Drawable for scrolled page to update Logo properly
        // cmeng: 20200312: seems no necessary anymore? so disable it seems ok now
        // avatarDrawable.invalidateDrawable(avatarDrawable);
        var avatarBmp: BitmapDrawable? = null
        if (avatar != null) {
            if (avatar.size < 256 * 1024) {
                avatarBmp = AndroidImageUtil.roundedDrawableFromBytes(avatar)
            } else {
                Timber.e("Avatar image is too large: %s", avatar.size)
            }
            if (avatarBmp != null) {
                avatarDrawable!!.setDrawableByLayerId(R.id.avatarDrawable, avatarBmp)
            } else {
                Timber.e("Failed to get avatar drawable from bytes")
            }
        }
        // set Logo is only available when there is no customView attached or during search
        val actionBar = activity!!.supportActionBar
        if (actionBar != null) {
            if (actionBar.customView == null) actionBar.setLogo(avatarDrawable) else {
                val logo = activity.findViewById<ImageView>(R.id.logo)
                logo?.setImageDrawable(avatarDrawable)
            }
        }
    }

    fun setAvatar(activity: AppCompatActivity, @DrawableRes resId: Int) {
        val actionBar = activity.supportActionBar
        if (actionBar != null) {
            if (actionBar.customView == null) actionBar.setLogo(resId) else {
                val logo = activity.findViewById<ImageView>(R.id.logo)
                logo?.setImageResource(resId)
            }
        }
    }

    /**
     * Returns the default avatar [Drawable]
     *
     * @return the default avatar [Drawable]
     */
    private fun getDefaultAvatarDrawable(activity: AppCompatActivity?): LayerDrawable? {
        val res = activity!!.resources
        return ResourcesCompat.getDrawable(res, R.drawable.avatar_layer_drawable, null) as LayerDrawable?
    }
}