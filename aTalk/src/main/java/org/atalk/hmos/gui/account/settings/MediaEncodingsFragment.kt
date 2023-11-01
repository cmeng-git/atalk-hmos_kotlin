/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.hmos.gui.widgets.TouchInterceptor
import org.atalk.service.osgi.OSGiPreferenceFragment
import java.io.Serializable

/**
 * The fragment allows user to edit encodings and their priorities.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MediaEncodingsFragment : OSGiPreferenceFragment(), TouchInterceptor.DropListener {
    /**
     * Adapter encapsulating manipulation of encodings list and their priorities
     */
    private var adapter: OrderListAdapter? = null

    /**
     * List of encodings
     */
    var encodings: MutableList<String>? = null

    /**
     * List of priorities
     */
    var priorities: MutableList<Int>? = null

    /**
     * Flag holding enabled status for the fragment. All views will be grayed out if the fragment is not enabled.
     */
    private var isEnabled = true

    /**
     * Flag tells us if there were any changes made.
     */
    private var hasChanges = false

    /**
     * Sets enabled status for this fragment.
     *
     * @param isEnabled `true` to enable the fragment.
     */
    fun setEnabled(isEnabled: Boolean) {
        this.isEnabled = isEnabled
        adapter!!.invalidate()
    }

    /**
     * Returns `true` if this fragment is holding any uncommitted changes
     *
     * @return `true` if this fragment is holding any uncommitted changes
     */
    fun hasChanges(): Boolean {
        return hasChanges
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val bundle = savedInstanceState ?: arguments
        encodings = bundle!!.get(ARG_ENCODINGS) as MutableList<String>?
        priorities = bundle.get(ARG_PRIORITIES) as MutableList<Int>?
        if (encodings!!.contains("VP8/90000")) setPrefTitle(R.string.service_gui_settings_VIDEO_CODECS_TITLE) else setPrefTitle(R.string.service_gui_settings_AUDIO_CODECS_TITLE)
        val content = inflater.inflate(R.layout.encoding, container, false)

        /**
         * The [TouchInterceptor] widget that allows user to drag items to set their order
         */
        val listWidget = content.findViewById<View>(R.id.encodingList) as TouchInterceptor
        adapter = OrderListAdapter(R.layout.encoding_item)
        listWidget.adapter = adapter
        listWidget.setDropListener(this)
        return content
    }

    /**
     * {@inheritDoc}
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(ARG_ENCODINGS, encodings as Serializable?)
        outState.putSerializable(ARG_PRIORITIES, priorities as Serializable?)
    }

    /**
     * Implements [TouchInterceptor.DropListener]
     *
     * @param from index indicating source position
     * @param to index indicating destination position
     */
    override fun drop(from: Int, to: Int) {
        adapter!!.swapItems(from, to)
        hasChanges = true
    }

    /**
     * Utility method for calculating encodings priorities.
     *
     * @param idx encoding index in the list
     * @return the priority value for given encoding index.
     */
    private fun calcPriority(idx: Int): Int {
        return calcPriority(encodings, idx)
    }

    /**
     * Class implements encodings model for the list widget. Enables/disables each encoding and sets its priority.
     * It is also responsible for creating Views for list rows.
     */
    internal inner class OrderListAdapter
    /**
     * Creates a new instance of [OrderListAdapter].
     */
    (
            /**
             * ID of the list row layout
             */
            private val viewResId: Int) : BaseAdapter() {

        /**
         * Swaps encodings on the list and changes their priorities
         *
         * @param from source item position
         * @param to destination items position
         */
        fun swapItems(from: Int, to: Int) {
            // Swap positions
            val swap = encodings!![from]
            val swapPrior = priorities!![from]
            encodings!!.removeAt(from)
            priorities!!.removeAt(from)

            // Swap priorities
            encodings!!.add(to, swap)
            priorities!!.add(to, swapPrior)
            for (i in encodings!!.indices) {
                priorities!![i] = if (priorities!![i] > 0) calcPriority(i) else 0
            }

            // Update the UI
            invalidate()
        }

        /**
         * Refresh the list on UI thread
         */
        fun invalidate() {
            activity!!.runOnUiThread { this.notifyDataSetChanged() }
        }

        override fun getCount(): Int {
            return encodings!!.size
        }

        override fun getItem(i: Int): Any {
            return encodings!![i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
            // Creates the list row view
            val gv = activity!!.layoutInflater.inflate(viewResId, viewGroup, false) as ViewGroup
            // Creates the enable/disable button
            val cb = gv.findViewById<CompoundButton>(android.R.id.checkbox)
            cb.isChecked = priorities!![i] > 0
            cb.setOnCheckedChangeListener { cButton: CompoundButton?, isChecked: Boolean ->
                priorities!![i] = if (isChecked) calcPriority(i) else 0
                hasChanges = true
            }

            // Create string for given format entry
            val mf = encodings!![i]
            val tv = gv.findViewById<TextView>(android.R.id.text1)
            tv.text = mf
            // Creates the drag handle view(used to grab list entries)
            val iv = gv.findViewById<ImageView>(R.id.dragHandle)
            if (!isEnabled) gv.removeView(iv)
            cb.isEnabled = isEnabled
            tv.isEnabled = isEnabled
            return gv
        }
    }

    companion object {
        /**
         * Argument key for list of encodings as strings (see [MediaEncodingActivity] for utility methods.)
         */
        const val ARG_ENCODINGS = "arg.encodings"

        /**
         * Argument key for encodings priorities.
         */
        const val ARG_PRIORITIES = "arg.priorities"

        /**
         * Function used to calculate priority based on item index
         *
         * @param idx the index of encoding on the list
         * @return encoding priority value for given `idx`
         */
        fun calcPriority(encodings: MutableCollection<*>?, idx: Int): Int {
            return encodings!!.size - idx
        }

        /**
         * Creates new `EncodingsFragment` for given list of encodings and priorities.
         *
         * @param encodings list of encodings as strings.
         * @param priorities list of encodings priorities.
         * @return parametrized instance of `EncodingsFragment`.
         */
        fun newInstance(encodings: List<String>?, priorities: List<Int>?): MediaEncodingsFragment {
            val fragment = MediaEncodingsFragment()
            val args = Bundle()
            args.putSerializable(ARG_ENCODINGS, encodings as Serializable?)
            args.putSerializable(ARG_PRIORITIES, priorities as Serializable?)
            fragment.setArguments(args)
            return fragment
        }
    }
}