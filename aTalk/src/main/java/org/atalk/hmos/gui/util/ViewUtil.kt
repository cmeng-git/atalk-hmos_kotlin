/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * Utility class that encapsulates common operations on some `View` types.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object ViewUtil {
    /**
     * Sets given `text` on the `TextView` identified by the `id`. The
     * `TextView` must be inside `container` view hierarchy.
     *
     * @param container the `View` that contains the `TextView`.
     * @param id the id of `TextView` we want to edit.
     * @param text string value that will be set on the `TextView`.
     */
    fun setTextViewValue(container: View, id: Int, text: String?) {
        val tv = container.findViewById<TextView>(id)
        tv.text = text
    }

    //	public static void setTextViewHtml(View container, int id, String text)
    //	{
    //		TextView tv = (TextView) container.findViewById(id);
    //		tv.setText(android.text.Html.fromHtml(text));
    //	}
    fun setTextViewValue(container: View, tag: String?, text: String?) {
        val tv = container.findViewWithTag<TextView>(tag)
        tv.text = text
    }

    fun setTextViewColor(container: View, id: Int, color: Int) {
        val tv = container.findViewById<TextView>(id)
        tv.setTextColor(aTalkApp.appResources.getColor(color, null))
    }

    fun setTextViewAlpha(container: View, id: Int, alpha: Float) {
        val tv = container.findViewById<TextView>(id)
        tv.alpha = alpha
    }

    fun getTextViewValue(container: View, id: Int): String? {
        return toString(container.findViewById(id))
    }

    fun isCompoundChecked(container: View, id: Int): Boolean {
        return (container.findViewById<View>(id) as CompoundButton).isChecked
    }

    fun setCompoundChecked(container: View, id: Int, isChecked: Boolean) {
        (container.findViewById<View>(id) as CompoundButton).isChecked = isChecked
    }

    fun setCompoundChecked(container: View, tag: String?, isChecked: Boolean) {
        (container.findViewWithTag<View>(tag) as CompoundButton).isChecked = isChecked
    }

    /**
     * Sets image identified by `drawableId` resource id on the `ImageView`.
     * `ImageView` must exist in `container` view hierarchy.
     *
     * @param container the container `View`.
     * @param imageViewId id of `ImageView` that will be used.
     * @param drawableId the resource id of drawable that will be set.
     */
    fun setImageViewIcon(container: View, imageViewId: Int, drawableId: Int) {
        val imageView = container.findViewById<ImageView>(imageViewId)
        imageView.setImageResource(drawableId)
    }

    /**
     * Ensures that the `View` is currently in visible or hidden state which depends on
     * `isVisible` flag.
     *
     * @param container parent `View` that contains displayed `View`.
     * @param viewId the id of `View` that will be shown/hidden.
     * @param isVisible flag telling whether the `View` has to be shown or hidden.
     */
    fun ensureVisible(container: View, viewId: Int, isVisible: Boolean) {
        val view = container.findViewById<View>(viewId)
        if (isVisible && view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
        } else if (!isVisible && view.visibility != View.GONE) {
            view.visibility = View.GONE
        }
    }

    /**
     * Ensures that the `View` is currently in enabled or disabled state.
     *
     * @param container parent `View` that contains displayed `View`.
     * @param viewId the id of `View` that will be enabled/disabled.
     * @param isEnabled flag telling whether the `View` has to be enabled or disabled.
     */
    fun ensureEnabled(container: View, viewId: Int, isEnabled: Boolean) {
        val view = container.findViewById<View>(viewId)
        if (isEnabled && !view.isEnabled) {
            view.isEnabled = isEnabled
        } else if (!isEnabled && view.isEnabled) {
            view.isEnabled = isEnabled
        }
    }

    /**
     * Ensures that the `View` is currently in enabled or disabled state.
     *
     * @param container parent `View` that contains displayed `View`.
     * @param tag the tag of `View` that will be enabled/disabled.
     * @param isEnabled flag telling whether the `View` has to be enabled or disabled.
     */
    fun ensureEnabled(container: View, tag: String?, isEnabled: Boolean) {
        val view = container.findViewWithTag<View>(tag)
        if (isEnabled && !view.isEnabled) {
            view.isEnabled = isEnabled
        } else if (!isEnabled && view.isEnabled) {
            view.isEnabled = isEnabled
        }
    }

    /**
     * Sets given `view` visibility state using it's handler.
     *
     * @param view the view which visibility state will be changed.
     * @param visible new visibility state o set.
     */
    fun setViewVisible(view: View, visible: Boolean) {
        val newState = if (visible) View.VISIBLE else View.GONE
        if (view.visibility == newState) {
            return
        }
        val viewHandler = view.handler
        if (viewHandler == null) {
            Timber.w("Handler not available for view %s", view)
            return
        }
        viewHandler.post { view.visibility = newState }
    }

    /**
     * get the textView string value or null (length == 0)
     *
     * @param textView TextView or EditText
     * @return String or null
     */
    fun toString(textView: TextView?): String? {
        val editText = textView?.text
        val text = editText?.toString()?.trim { it <= ' ' }
        return if (text == null || text.isEmpty()) null else text
    }

    /**
     * get the textView string value or null (length == 0)
     *
     * @param textView TextView or EditText
     * @return String or null
     */
    fun toCharArray(textView: TextView?): CharArray? {
        val text = toString(textView)
        return text?.toCharArray()
    }

    /**
     * Show or hide password
     *
     * @param view the password EditText view
     * @param show `true` set password visible to user
     */
    fun showPassword(view: EditText?, show: Boolean) {
        val cursorPosition = view!!.selectionStart
        if (show) {
            view.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            view.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        view.setSelection(cursorPosition)
    }

    /**
     * Hide soft keyboard
     *
     * @param context context
     * @param view the reference view
     */
    fun hideKeyboard(context: Context?, view: View) {
        val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}