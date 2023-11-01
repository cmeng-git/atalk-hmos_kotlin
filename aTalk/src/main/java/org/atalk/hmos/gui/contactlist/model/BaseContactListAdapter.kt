/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import net.java.sip.communicator.impl.protocol.jabber.ContactJabberImpl
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.protocol.ContactGroup
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.AndroidCallUtil
import org.atalk.hmos.gui.call.telephony.TelephonyFragment
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.widgets.UnreadCountCustomView
import org.atalk.service.osgi.OSGiActivity
import org.jxmpp.jid.DomainBareJid
import timber.log.Timber

/**
 * Base class for contact list adapter implementations.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class BaseContactListAdapter(clFragment: ContactListFragment, mainContactList: Boolean) : BaseExpandableListAdapter(), View.OnClickListener, View.OnLongClickListener {
    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    protected open val uiHandler = OSGiActivity.uiHandler

    /**
     * The contact list view.
     */
    private val contactListFragment: ContactListFragment

    /**
     * The list view.
     */
    private val contactListView: ExpandableListView

    /**
     * A map reference of MetaContact to ContactViewHolder for the unread message count update
     */
    private val mContactViewHolder = HashMap<MetaContact, ContactViewHolder>()

    /**
     * Flag set to true to indicate the view is the main contact list and all available options etc are enabled
     * Otherwise the view is meant for group chat invite, all the following options take effects:
     * a. Hide all media call buttons
     * b. Disabled Context menu (popup menus) i.e. onClick and onLongClick
     * c. Multiple contact selection are allowed
     */
    private val isMainContactList: Boolean
    private val mInflater = LayoutInflater.from(aTalkApp.globalContext)

    /**
     * Creates the contact list adapter.
     *
     * clFragment the parent `ContactListFragment`
     * mainContactList call buttons and other options are only enable when it is the main Contact List view
     */
    init {
        // cmeng - must use this mInflater as clFragment may not always attached to FragmentManager e.g. muc invite dialog
        contactListFragment = clFragment
        isMainContactList = mainContactList
        contactListView = contactListFragment.contactListView
    }

    /**
     * Initializes model data. Is called before adapter is used for the first time.
     */
    abstract fun initModelData()

    /**
     * Filter the contact list with given `queryString`
     *
     * @param queryString the query string we want to match.
     */
    abstract fun filterData(queryString: String)

    /**
     * Returns the `UIContactRenderer` for contacts of group at given `groupIndex`.
     *
     * @param groupIndex index of the contact group.
     * @return the `UIContactRenderer` for contact of group at given `groupIndex`.
     */
    protected abstract fun getContactRenderer(groupIndex: Int): UIContactRenderer?

    /**
     * Returns the `UIGroupRenderer` for group at given `groupPosition`.
     *
     * @param groupPosition index of the contact group.
     * @return the `UIContactRenderer` for group at given `groupPosition`.
     */
    protected abstract fun getGroupRenderer(groupPosition: Int): UIGroupRenderer?

    /**
     * Releases all resources used by this instance.
     */
    open fun dispose() {
        notifyDataSetInvalidated()
    }

    /**
     * Expands all contained groups.
     */
    fun expandAllGroups() {
        // Expand group view only when contactListView is in focus (UI mode)
        // cmeng - do not use isFocused() - may not in sync with actual
        uiHandler.post {
            for (position in 0 until groupCount) {
                contactListView.expandGroup(position)
            }
        }
    }

    /**
     * Refreshes the view with expands group and invalid view.
     */
    fun invalidateViews() {
        contactListFragment.runOnUiThread { contactListView.invalidateViews() }
    }

    /**
     * Updates the contact display name.
     *
     * @param groupIndex the index of the group to update
     * @param contactIndex the index of the contact to update
     */
    protected fun updateDisplayName(groupIndex: Int, contactIndex: Int) {
        val firstIndex = contactListView.firstVisiblePosition
        val contactView = contactListView.getChildAt(getListIndex(groupIndex, contactIndex) - firstIndex)
        if (contactView != null) {
            val metaContact = getChild(groupIndex, contactIndex) as MetaContact
            ViewUtil.setTextViewValue(contactView, R.id.displayName, metaContact.getDisplayName())
        }
    }

    /**
     * Updates the contact avatar.
     *
     * @param groupIndex the index of the group to update
     * @param contactIndex the index of the contact to update
     * @param contactImpl contact implementation object instance
     */
    protected fun updateAvatar(groupIndex: Int, contactIndex: Int, contactImpl: Any) {
        val firstIndex = contactListView.firstVisiblePosition
        val contactView = contactListView.getChildAt(getListIndex(groupIndex, contactIndex) - firstIndex)
        if (contactView != null) {
            val avatarView = contactView.findViewById<ImageView>(R.id.avatarIcon)
            if (avatarView != null) setAvatar(avatarView, getContactRenderer(groupIndex)!!.getAvatarImage(contactImpl))
        }
    }

    /**
     * Updates the contact status indicator.
     *
     * @param groupIndex the index of the group to update
     * @param contactIndex the index of the contact to update
     * @param contactImpl contact implementation object instance
     */
    protected fun updateStatus(groupIndex: Int, contactIndex: Int, contactImpl: Any) {
        val firstIndex = contactListView.firstVisiblePosition
        val contactView = contactListView.getChildAt(getListIndex(groupIndex, contactIndex) - firstIndex)
        if (contactView != null) {
            val statusView = contactView.findViewById<ImageView>(R.id.contactStatusIcon)
            if (statusView == null) {
                Timber.w("No status view found for %s", contactImpl)
                return
            }
            statusView.setImageDrawable(getContactRenderer(groupIndex)!!.getStatusImage(contactImpl))
        }
    }

    /**
     * Updates the contact message unread count. Hide the unread message badge if the count is zero
     *
     * @param metaContact MetaContact object
     * @param count unread message count
     */
    fun updateUnreadCount(metaContact: MetaContact, count: Int) {
        val contactViewHolder = mContactViewHolder[metaContact] ?: return
        if (count == 0) {
            contactViewHolder.unreadCount.visibility = View.GONE
        } else {
            contactViewHolder.unreadCount.visibility = View.VISIBLE
            contactViewHolder.unreadCount.setUnreadCount(count)
        }
    }

    /**
     * Returns the flat list index for the given `groupIndex` and `contactIndex`.
     *
     * @param groupIndex the index of the group
     * @param contactIndex the index of the contact
     * @return an int representing the flat list index for the given `groupIndex` and `contactIndex`
     */
    fun getListIndex(groupIndex: Int, contactIndex: Int): Int {
        val lastIndex = contactListView.lastVisiblePosition
        for (i in 0..lastIndex) {
            val lPosition = contactListView.getExpandableListPosition(i)
            val groupPosition = ExpandableListView.getPackedPositionGroup(lPosition)
            val childPosition = ExpandableListView.getPackedPositionChild(lPosition)
            if (groupIndex == groupPosition && contactIndex == childPosition) {
                return i
            }
        }
        return -1
    }

    /**
     * Returns the identifier of the child contained on the given `groupPosition` and `childPosition`.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     * @return the identifier of the child contained on the given `groupPosition` and `childPosition`
     */
    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    /**
     * Returns the child view for the given `groupPosition`, `childPosition`.
     *
     * @param groupPosition the group position of the desired view
     * @param childPosition the child position of the desired view
     * @param isLastChild indicates if this is the last child
     * convertView the view to fill with data
     * @param parent the parent view group
     */
    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean,
            convertView_: View?, parent: ViewGroup): View {
        // Keeps reference to avoid future findViewById()
        var convertView = convertView_
        val contactViewHolder: ContactViewHolder
        val child = getChild(groupPosition, childPosition)
        // Timber.w("getChildView: %s:%s = %s", groupPosition, childPosition, child);
        if (convertView == null || convertView.tag !is ContactViewHolder) {
            convertView = mInflater.inflate(R.layout.contact_list_row, parent, false)
            contactViewHolder = ContactViewHolder()
            contactViewHolder.displayName = convertView.findViewById(R.id.displayName)
            contactViewHolder.statusMessage = convertView.findViewById(R.id.statusMessage)
            contactViewHolder.avatarView = convertView.findViewById(R.id.avatarIcon)
            contactViewHolder.avatarView.setOnClickListener(this)
            contactViewHolder.avatarView.setOnLongClickListener(this)
            contactViewHolder.statusView = convertView.findViewById(R.id.contactStatusIcon)
            contactViewHolder.unreadCount = convertView.findViewById(R.id.unread_count)
            contactViewHolder.unreadCount.tag = contactViewHolder

            // Create call button listener and add bind holder tag
            contactViewHolder.callButtonLayout = convertView.findViewById(R.id.callButtonLayout)
            contactViewHolder.callButton = convertView.findViewById(R.id.contactCallButton)
            contactViewHolder.callButton.setOnClickListener(this)
            contactViewHolder.callButton.tag = contactViewHolder
            contactViewHolder.callVideoButton = convertView.findViewById(R.id.contactCallVideoButton)
            contactViewHolder.callVideoButton.setOnClickListener(this)
            contactViewHolder.callVideoButton.tag = contactViewHolder
            contactViewHolder.buttonSeparatorView = convertView.findViewById(R.id.buttonSeparatorView)
        } else {
            contactViewHolder = convertView.tag as ContactViewHolder
        }
        contactViewHolder.groupPosition = groupPosition
        contactViewHolder.childPosition = childPosition

        // return and stop further process if child contact may have been removed
        if (child !is MetaContact) return convertView!!

        // Must init child tag here as reused convertView may not necessary contains the correct metaContact
        val contactView = convertView!!.findViewById<View>(R.id.contact_view)
        if (isMainContactList) {
            contactView.setOnClickListener(this)
            contactView.setOnLongClickListener(this)
        }
        contactView.tag = child
        contactViewHolder.avatarView.tag = child
        val renderer = getContactRenderer(groupPosition)
        if (renderer!!.isSelected(child)) {
            convertView.setBackgroundResource(R.drawable.color_blue_gradient)
        } else {
            convertView.setBackgroundResource(R.drawable.list_selector_state)
        }

        // Set display name and status message for contacts or phone book contacts
        var sDisplayName = renderer.getDisplayName(child)
        var statusMessage = renderer.getStatusMessage(child)
        if (child.getDefaultContact() != null) {
            mContactViewHolder[child] = contactViewHolder
            updateUnreadCount(child, child.getUnreadCount())
            val sJid = sDisplayName
            if (TextUtils.isEmpty(statusMessage)) {
                if (sJid!!.contains("@")) {
                    sDisplayName = sJid.split("@")[0]
                    statusMessage = sJid
                } else statusMessage = renderer.getDefaultAddress(child)
            }
        }
        contactViewHolder.displayName.text = sDisplayName
        contactViewHolder.statusMessage.text = statusMessage
        if (renderer.isDisplayBold(child)) {
            contactViewHolder.displayName.typeface = Typeface.DEFAULT_BOLD
        } else {
            contactViewHolder.displayName.typeface = Typeface.DEFAULT
        }

        // Set avatar.
        setAvatar(contactViewHolder.avatarView, renderer.getAvatarImage(child))
        contactViewHolder.statusView.setImageDrawable(renderer.getStatusImage(child))

        // Show both voice and video call buttons.
        val isShowVideoCall = renderer.isShowVideoCallBtn(child)
        val isShowCall = renderer.isShowCallBtn(child)
        if (isMainContactList && (isShowVideoCall || isShowCall)) {
            AndroidUtils.setOnTouchBackgroundEffect(contactViewHolder.callButtonLayout)
            contactViewHolder.callButtonLayout.visibility = View.VISIBLE
            contactViewHolder.callButton.visibility = if (isShowCall) View.VISIBLE else View.GONE
            contactViewHolder.callVideoButton.visibility = if (isShowVideoCall) View.VISIBLE else View.GONE
        } else {
            contactViewHolder.callButtonLayout.visibility = View.INVISIBLE
        }
        return convertView
    }

    /**
     * Returns the group view for the given `groupPosition`.
     *
     * @param groupPosition the group position of the desired view
     * @param isExpanded indicates if the view is currently expanded
     * convertView the view to fill with data
     * @param parent the parent view group
     */
    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView_: View?, parent: ViewGroup): View {
        // Keeps reference to avoid future findViewById()
        var convertView = convertView_
        val groupViewHolder: GroupViewHolder
        val group = getGroup(groupPosition)
        if (convertView == null || convertView.tag !is GroupViewHolder) {
            convertView = mInflater.inflate(R.layout.contact_list_group_row, parent, false)
            groupViewHolder = GroupViewHolder()
            groupViewHolder.groupName = convertView.findViewById(R.id.groupName)
            groupViewHolder.groupName.setOnLongClickListener(this)
            groupViewHolder.indicator = convertView.findViewById(R.id.groupIndicatorView)
            convertView.tag = groupViewHolder
        } else {
            groupViewHolder = convertView.tag as GroupViewHolder
        }
        if (group is MetaContactGroup) {
            val groupRenderer = getGroupRenderer(groupPosition)
            groupViewHolder.groupName.tag = group
            groupViewHolder.groupName.text = groupRenderer!!.getDisplayName(group)
        }

        // Group expand indicator
        val indicatorResId = if (isExpanded) R.drawable.expanded_dark else R.drawable.collapsed_dark
        groupViewHolder.indicator.setImageResource(indicatorResId)
        return convertView!!
    }

    /**
     * Returns the identifier of the group given by `groupPosition`.
     *
     * @param groupPosition the index of the group, which identifier we're looking for
     */
    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    /**
     *
     */
    override fun hasStableIds(): Boolean {
        return true
    }

    /**
     * Indicates that all children are selectable.
     */
    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    /**
     * We keep one instance of view click listener to avoid unnecessary allocations.
     * Clicked positions are obtained from the view holder.
     */
    override fun onClick(view: View) {
        var viewHolder: ContactViewHolder? = null

        // Use by media call button activation
        var objTag = view.tag
        if (objTag is ContactViewHolder) {
            viewHolder = view.tag as ContactViewHolder
            val groupPos = viewHolder.groupPosition
            val childPos = viewHolder.childPosition
            objTag = getChild(groupPos, childPos)
        }
        if (objTag is MetaContact) {
            val metaContact = objTag
            val contact = metaContact.getDefaultContact()
            if (contact != null) {
                val jid = contact.contactJid
                val jidAddress = contact.address
                when (view.id) {
                    R.id.contact_view -> contactListFragment.startChat(metaContact)
                    R.id.contactCallButton -> {
                        if (jid is DomainBareJid) {
                            val extPhone = TelephonyFragment.newInstance(jidAddress)
                            contactListFragment.activity!!.supportFragmentManager.beginTransaction()
                                    .replace(android.R.id.content, extPhone).commit()
                        } else {
                            if (viewHolder != null) {
                                AndroidCallUtil.createCall(aTalkApp.globalContext, metaContact,
                                        false, viewHolder.callVideoButton)
                            }
                        }
                    }
                    R.id.contactCallVideoButton -> if (viewHolder != null) {
                        AndroidCallUtil.createCall(aTalkApp.globalContext, metaContact,
                                true, viewHolder.callVideoButton)
                    }
                    R.id.avatarIcon -> aTalkApp.showToastMessage(jidAddress)
                    else -> {}
                }
            }
        } else {
            Timber.w("Clicked item is not a valid MetaContact")
        }
    }

    /**
     * Retrieve the contact avatar from server when user longClick on the avatar in contact list.
     * Clicked position/contact is derived from the view holder group/child positions.
     */
    override fun onLongClick(view: View): Boolean {
        val clicked = view.tag

        // proceed to retrieve avatar for the clicked contact
        if (clicked is MetaContact) {
            when (view.id) {
                R.id.contact_view -> {
                    contactListFragment.showPopupMenuContact(view, clicked)
                    return true
                }
                R.id.avatarIcon -> {
                    val contact = clicked.getDefaultContact()
                    if (contact != null) {
                        val contactJid = contact.contactJid!!
                        if (contactJid !is DomainBareJid) {
                            (contact as ContactJabberImpl).getAvatar(true)
                            aTalkApp.showToastMessage(R.string.service_gui_AVATAR_RETRIEVING, contactJid)
                        }
                        else {
                            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, contactJid)
                        }
                    }
                    return true
                }
            }
        } else if (clicked is MetaContactGroup) {
            if (view.id == R.id.groupName) {
                if (ContactGroup.ROOT_GROUP_UID == clicked.getMetaUID() || ContactGroup.VOLATILE_GROUP == clicked.getGroupName()) {
                    Timber.w("No action allowed for Group Name: %s", clicked.getGroupName())
                    aTalkApp.showToastMessage(R.string.service_gui_UNSUPPORTED_OPERATION)
                } else {
                    contactListFragment.showPopUpMenuGroup(view, clicked)
                }
                return true
            }
        }
        return false
    }

    /**
     * Sets the avatar icon of the action bar.
     *
     * @param avatarView the avatar image view
     */
    private fun setAvatar(avatarView: ImageView?, avatarImage_: Drawable?) {
        var avatarImage = avatarImage_
        if (avatarImage == null) {
            avatarImage = ResourcesCompat.getDrawable(aTalkApp.appResources,
                    R.drawable.contact_avatar, null)
        }
        avatarView!!.setImageDrawable(avatarImage)
    }

    private class ContactViewHolder {
        lateinit var displayName: TextView
        lateinit var statusMessage: TextView
        lateinit var avatarView: ImageView
        lateinit var statusView: ImageView
        lateinit var callButton: ImageView
        lateinit var callVideoButton: ImageView
        lateinit var buttonSeparatorView: ImageView
        lateinit var callButtonLayout: View
        lateinit var unreadCount: UnreadCountCustomView
        var groupPosition = 0
        var childPosition = 0
    }

    private class GroupViewHolder {
        lateinit var indicator: ImageView
        lateinit var groupName: TextView
    }
}