/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.util.CollectionAdapter
import org.atalk.hmos.gui.util.event.EventListener


/**
 * This adapter displays all `MetaContactGroup` items. If in the constructor `AdapterView` id
 * will be passed it will include "create new group" functionality. That means extra item "create group.."
 * will be appended on the last position and when selected create group dialog will popup automatically.
 * When a new group is created, it is implicitly included into this adapter.
 *
 * Use setItemLayout and setDropDownLayout to change the spinner style if need to.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class MetaContactGroupAdapter : CollectionAdapter<Any?> {
    /**
     * Item layout
     */
    private var itemLayout = 0

    /**
     * Drop down item layout
     */
    private var dropDownLayout = 0

    /**
     * Instance of used `AdapterView`.
     */
    private var adapterView: AdapterView<*>? = null

    /**
     * Creates a new instance of `MetaContactGroupAdapter`. It will be filled with all
     * currently available `MetaContactGroup`.
     *
     * @param parent the parent `Activity`.
     * @param adapterViewId id of the `AdapterView`.
     * @param includeRoot `true` if "No group" item should be included
     * @param includeCreate `true` if "Create group" item should be included
     */
    constructor(parent: Activity?, adapterViewId: Int, includeRoot: Boolean, includeCreate: Boolean) : super(parent!!, getAllContactGroups(includeRoot, includeCreate).iterator()) {
        if (adapterViewId != -1) init(adapterViewId)
    }

    /**
     * Creates a new instance of `MetaContactGroupAdapter`. It will be filled with all
     * currently available `MetaContactGroup`.
     *
     * @param parent the parent `Activity`.
     * @param adapterView the `AdapterView` that will be used.
     * @param includeRoot `true` if "No group" item should be included
     * @param includeCreate `true` if "Create group" item should be included
     */
    constructor(parent: Activity?, adapterView: AdapterView<*>, includeRoot: Boolean, includeCreate: Boolean) : super(parent!!, getAllContactGroups(includeRoot, includeCreate).iterator()) {
        init(adapterView)
    }

    private fun init(adapterViewId: Int) {
        val aView = parentActivity.findViewById<AdapterView<*>>(adapterViewId)
        init(aView)
    }

    private fun init(adapterView: AdapterView<*>) {
        this.adapterView = adapterView
        itemLayout = R.layout.simple_spinner_item
        dropDownLayout = R.layout.simple_spinner_dropdown_item

        // Handle add new group action
        adapterView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val item = parent.adapter.getItem(position)
                if (item === ADD_NEW_OBJECT) {
                    AddGroupDialog.showCreateGroupDialog(parentActivity, object : EventListener<MetaContactGroup?> {
                        override fun onChangeEvent(eventObject: MetaContactGroup?) {
                            onNewGroupCreated(eventObject)
                        }
                    })
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getView(isDropDown: Boolean, item: Any?, parent: ViewGroup, inflater: LayoutInflater): View {
        val rowResId = if (isDropDown) dropDownLayout else itemLayout
        val rowView = inflater.inflate(rowResId, parent, false)
        val tv = rowView.findViewById<TextView>(android.R.id.text1)
        if (item == ADD_NEW_OBJECT) {
            tv.setText(R.string.service_gui_CREATE_GROUP)
        } else if (item == AndroidGUIActivator.contactListService.getRoot()) {
            // Root - Contacts
            tv.setText(R.string.service_gui_SELECT_NO_GROUP)
        } else {
            tv.text = (item as MetaContactGroup).getGroupName()
        }
        return rowView
    }

    /**
     * Handles on new group created event by append item into the list and notifying about data set change.
     *
     * @param newGroup new contact group if was created or `null` if user cancelled the dialog.
     */
    private fun onNewGroupCreated(newGroup: MetaContactGroup?) {
        if (newGroup == null) return
        val pos = count - 1
        insert(pos, newGroup)
        adapterView!!.setSelection(pos)
        notifyDataSetChanged()
    }

    /**
     * Sets to caller defined item layout resource id.
     *
     * @param itemLayout the item layout resource id to set.
     */
    fun setItemLayout(itemLayout: Int) {
        this.itemLayout = itemLayout
    }

    /**
     * Set to caller defined drop down item layout resource id.
     *
     * @param dropDownLayout the drop down item layout resource id to set.
     */
    fun setDropDownLayout(dropDownLayout: Int) {
        this.dropDownLayout = dropDownLayout
    }

    companion object {
        /**
         * Object instance used to identify "Create group..." item.
         */
        private val ADD_NEW_OBJECT = Any()

        /**
         * Returns the list of all currently available `MetaContactGroup`.
         *
         * @param includeRoot indicates whether "No group" item should be included in the list.
         * @param includeCreateNew indicates whether "create new group" item should be included in the list.
         * @return the list of all currently available `MetaContactGroup`.
         */
        private fun getAllContactGroups(includeRoot: Boolean, includeCreateNew: Boolean): List<Any?> {
            val contactListService = AndroidGUIActivator.contactListService
            val root = contactListService.getRoot()
            val groupList = ArrayList<Any?>()
            if (includeRoot) {
                groupList.add(root)
            }
            val mcGroups = root.getSubgroups()
            while (mcGroups!!.hasNext()) {
                groupList.add(mcGroups.next())
            }

            // Add new group item
            if (includeCreateNew) groupList.add(ADD_NEW_OBJECT)
            return groupList
        }
    }
}