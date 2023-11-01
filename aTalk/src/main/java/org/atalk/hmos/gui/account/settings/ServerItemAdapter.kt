/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.widget.BaseAdapter
import androidx.fragment.app.DialogFragment

/**
 * Class is used in [ServerListActivity] to handle list model. It also provides the edit dialog fragment for it's
 * items.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class ServerItemAdapter
/**
 * Creates new instance of [ServerItemAdapter]
 *
 * @param parent
 * the parent [Activity] used as a context
 */
(
        /**
         * Parent [android.app.Activity] used as a context
         */
        protected val parent: Activity) : BaseAdapter() {
    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    /**
     * Request list repaint
     */
    protected fun refresh() {
        parent.runOnUiThread { notifyDataSetChanged() }
    }

    /**
     * Factory method should return a [DialogFragment] that will allow user to edit list item at specified
     * `position`.
     *
     * @param position
     * the position of item to edit
     * @return the [DialogFragment] that should wil be displayed when item is clicked
     */
    abstract fun createItemEditDialogFragment(position: Int): DialogFragment
}