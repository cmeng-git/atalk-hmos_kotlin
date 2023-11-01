/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import android.widget.CheckedTextView
import android.widget.LinearLayout

/*
 * This class implements <code>Checkable</code> interface in order to provide custom <code>ListView</code> row layouts that can
 * be checked. The layout retrieves first child <code>CheckedTextView</code> and serves as a proxy between the ListView.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CheckableLinearLayout
/**
 * Creates new instance of `CheckableRelativeLayout`.
 *
 * @param context the context
 * @param attrs attributes set
 */
(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs), Checkable {
    /**
     * Instance of `CheckedTextView` to which this layout delegates `Checkable` interface calls.
     */
    private var checkbox: CheckedTextView? = null

    /**
     * Overrides in order to retrieve `CheckedTextView`.
     */
    override fun onFinishInflate() {
        super.onFinishInflate()
        val chCount = childCount
        for (i in 0 until chCount) {
            val v = getChildAt(i)
            if (v is CheckedTextView) {
                checkbox = v
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun isChecked(): Boolean {
        return checkbox != null && checkbox!!.isChecked
    }

    /**
     * {@inheritDoc}
     */
    override fun setChecked(checked: Boolean) {
        if (checkbox != null) {
            checkbox!!.isChecked = checked
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun toggle() {
        if (checkbox != null) {
            checkbox!!.toggle()
        }
    }
}