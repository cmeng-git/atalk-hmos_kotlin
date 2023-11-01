/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.app.Activity
import android.view.*
import android.widget.BaseAdapter

/**
 * Convenience class wrapping set of elements into [Adapter]
 *
 * @param <T> class of the elements contained in this adapter
 * @author Pawel Domas
 * @author Eng Chong Meng
</T> */
abstract class CollectionAdapter<T> : BaseAdapter {
    /**
     * List of elements handled by this adapter
     */
    private var items: MutableList<T>? = null
    /**
     * The parent [android.app.Activity]
     */
    /**
     * The parent [Activity]
     */
    protected val parentActivity: Activity

    /**
     * Creates a new instance of [CollectionAdapter]
     *
     * @param parent the parent [Activity]
     */
    constructor(parent: Activity) {
        parentActivity = parent
    }

    /**
     * Creates new instance of [CollectionAdapter]
     *
     * @param parent the parent [Activity]
     * @param items iterator of [T] items
     */
    constructor(parent: Activity, items: Iterator<T>) {
        parentActivity = parent
        setIterator(items)
    }

    /**
     * The method that accepts [Iterator] as a source set of objects
     *
     * @param iterator source of [T] instances that will be contained in this [CollectionAdapter]
     */
    private fun setIterator(iterator: Iterator<T>) {
        items = ArrayList()
        while (iterator.hasNext()) (items as ArrayList<T>).add(iterator.next())
    }

    /**
     * Accepts [List] as a source set of [T]
     *
     * @param collection the [List] that will be included in this [CollectionAdapter]
     */
    protected fun setList(collection: List<T>?) {
        items = ArrayList()
        (items as ArrayList<T>).addAll(collection!!)
    }

    /**
     * Returns total count of items contained in this adapter
     *
     * @return the count of [T] stored in this [CollectionAdapter]
     */
    override fun getCount(): Int {
        return items!!.size
    }

    override fun getItem(i: Int): T {
        return items!![i]
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    /**
     * Convenience method for retrieving [T] instances
     *
     * @param i the index of [T] that will be retrieved
     * @return the [T] object located at `i` position
     */
    protected fun getObject(i: Int): T {
        return items!![i]
    }

    /**
     * Adds `object` to the adapter
     *
     * @param object instance of [T] that will be added to this adapter
     */
    fun add(`object`: T) {
        if (!items!!.contains(`object`)) {
            items!!.add(`object`)
            doRefreshList()
        }
    }

    /**
     * Insert given object at specified position without notifying about adapter data change.
     *
     * @param pos the position at which given object will be inserted.
     * @param object the object to insert into adapter's list.
     */
    protected fun insert(pos: Int, `object`: T) {
        items!!.add(pos, `object`)
    }

    /**
     * Removes the `object` from this adapter
     *
     * @param object instance of [T] that will be removed from the adapter
     */
    fun remove(`object`: T) {
        // Remove item on UI thread to make sure it's not being painted at the same time
        parentActivity.runOnUiThread {
            if (items!!.remove(`object`)) {
                doRefreshList()
            }
        }
    }

    /**
     * Runs list change notification on the UI thread
     */
    protected fun doRefreshList() {
        parentActivity.runOnUiThread { notifyDataSetChanged() }
    }

    /**
     * {@inheritDoc}
     */
    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        return getView(false, items!![i], viewGroup, parentActivity.layoutInflater)
    }

    /**
     * {@inheritDoc}
     */
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(true, items!![position], parent, parentActivity.layoutInflater)
    }

    /**
     * Convenience method for creating new [View]s for each adapter's object
     *
     * @param isDropDown `true` if the `View` should be created for drop down spinner item
     * @param item the item for which a new View shall be created
     * @param parent [ViewGroup] parent View
     * @param inflater the [LayoutInflater] for creating new Views
     * @return a [View] for given `item`
     */
    protected abstract fun getView(isDropDown: Boolean, item: T, parent: ViewGroup, inflater: LayoutInflater): View
}