/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.contactlist.contactsource

import android.graphics.drawable.Drawable
import net.java.sip.communicator.plugin.desktoputil.SIPCommButton
import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.contactsource.ContactDetail.SubCategory
import net.java.sip.communicator.service.contactsource.PrefixedContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.gui.UIContactDetail
import net.java.sip.communicator.service.gui.UIGroup
import net.java.sip.communicator.service.protocol.OperationNotSupportedException
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.PhoneNumberI18nService
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.contactlist.ContactNode
import org.atalk.hmos.gui.contactlist.UIContactDetailImpl
import org.atalk.hmos.gui.contactlist.UIContactImpl
import org.atalk.hmos.gui.util.AndroidImageUtil.getScaledRoundedIcon
import java.awt.Color
import java.util.*
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * The `SourceUIContact` is the implementation of the UIContact for the `ExternalContactSource`.
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
open class SourceUIContact(contact: SourceContact) : UIContactImpl() {
    /**
     * The corresponding `SourceContact`, on which this abstraction is based.
     */
    private val sourceContact: SourceContact
    /**
     * Returns the corresponding `ContactNode` from the contact list component.
     *
     * @return the corresponding `ContactNode`
     */// uiGroup.getParentUISource().removeUIContact(sourceContact);
    /**
     * Sets the corresponding `ContactNode`.
     *
     * contactNode the corresponding `ContactNode`
     */
    /**
     * The corresponding `ContactNode` in the contact list component.
     */
    override var contactNode: ContactNode? = null
        set(contactNode) {
            field = contactNode
            if (contactNode == null) {
                // uiGroup.getParentUISource().removeUIContact(sourceContact);
            }
        }
    /**
     * The parent `UIGroup`.
     */
    // private ExternalContactSource.SourceUIGroup uiGroup;
    /**
     * The search strings for this `UIContact`.
     */
    override val searchStrings = LinkedList<String?>()

    /**
     * Creates an instance of `SourceUIContact` by specifying the `SourceContact`,
     * on which this abstraction is based and the parent `UIGroup`.
     *
     * contact the `SourceContact`, on which this abstraction is based
     * // parentGroup the parent `UIGroup`
     */
    init  // , ExternalContactSource.SourceUIGroup parentGroup)
    {
        sourceContact = contact
        // this.uiGroup = parentGroup;
        if (null != contact.contactDetails) for (detail in contact.contactDetails!!) {
            if (detail.detail != null) searchStrings.add(detail.detail)
        }
        searchStrings.add(contact.displayName)
    }

    /**
     * Returns the display name of the underlying `SourceContact`.
     *
     * @return the display name
     */
    override val displayName: String?
        get() = sourceContact.displayName// uiGroup;
    /**
     * The parent group of source contacts could not be changed.
     *
     * parentGroup the parent group to set
     */
    /**
     * Returns the parent `UIGroup`.
     *
     * @return the parent `UIGroup`
     */
    override var parentGroup: UIGroup?
        get() = null // uiGroup;
        set(parentGroup) {}

    /**
     * Returns -1 to indicate that the source index of the underlying `SourceContact` is
     * unknown.
     *
     * @return -1
     */
    override val sourceIndex: Int
        get() {
            val contactIndex = sourceContact.index
            val groupIndex = parentGroup!!.sourceIndex
            return if (contactIndex == -1) -1 else if (groupIndex == -1) contactIndex else groupIndex + contactIndex
        }

    /**
     * Returns null to indicate unknown status of the underlying `SourceContact`.
     *
     * @return null
     */
    override val statusIcon: ByteArray?
        get() {
            val status = sourceContact.presenceStatus
            return if (status != null) status.statusIcon else GlobalStatusEnum.OFFLINE.statusIcon
        }

    fun getAvatar(): ByteArray? {
        return sourceContact.image
    }

    /**
     * Returns the image corresponding to the underlying `SourceContact`.
     *
     * isSelected indicates if the contact is currently selected in the contact list component
     * width the desired image width
     * height the desired image height
     * @return the image
     */
    override fun getScaledAvatar(isSelected: Boolean, width: Int, height: Int): Drawable {
        return getScaledRoundedIcon(sourceContact.image!!, width, height)
    }

    /**
     * Returns the default `ContactDetail` to use for any operations depending to the given
     * `OperationSet` class.
     *
     * opSetClass the `OperationSet` class we're interested in
     * @return the default `ContactDetail` to use for any operations depending to the given
     * `OperationSet` class
     */
    override fun getDefaultContactDetail(opSetClass: Class<out OperationSet?>?): UIContactDetail? {
        val details = getContactDetailsForOperationSet(opSetClass)
        return if (details.isNotEmpty()) details[0] else null
    }

    /**
     * Returns the underlying `SourceContact` this abstraction is about.
     *
     * @return the underlying `SourceContact`
     */
    override val descriptor: Any
        get() = sourceContact

    /**
     * Returns the display details for the underlying `SourceContact`.
     *
     * @return the display details for the underlying `SourceContact`
     */
    override val displayDetails: String?
        get() = sourceContact.displayDetails

    /**
     * Returns a list of all contained `UIContactDetail`s.
     *
     * @return a list of all contained `UIContactDetail`s
     */
    override val contactDetails: List<UIContactDetail>
        get() {
            val resultList = LinkedList<UIContactDetail>()
            for (detail in sourceContact.contactDetails!!) {
                resultList.add(SourceContactDetail(detail, getInternationalizedLabel(detail.category),
                        getInternationalizedLabels(detail.getSubCategories()), null, sourceContact))
            }
            return resultList
        }

    /**
     * Returns a list of `UIContactDetail`s supporting the given `OperationSet`
     * class.
     *
     * opSetClass the `OperationSet` class we're interested in
     * @return a list of `UIContactDetail`s supporting the given `OperationSet` class
     */
    override fun getContactDetailsForOperationSet(opSetClass: Class<out OperationSet?>?): List<UIContactDetail?> {
        val resultList = LinkedList<UIContactDetail?>()
        val details = sourceContact.contactDetails!!.iterator()
        val phoneNumberService = AndroidGUIActivator.phoneNumberI18nService
        val filterToNumbers = AndroidGUIActivator.configurationService
                .getBoolean(FILTER_CALL_DETAILS_TO_NUMBERS_PROP, false)

        while (details.hasNext()) {
            val detail = details.next()
            val supportedOperationSets = detail.supportedOperationSets
            if (supportedOperationSets != null && supportedOperationSets.contains(opSetClass)) {
                if (filterToNumbers && opSetClass == OperationSetBasicTelephony::class.java && !phoneNumberService!!.isPhoneNumber(detail.detail)) {
                    continue
                }
                resultList.add(SourceContactDetail(detail, getInternationalizedLabel(detail.category),
                        getInternationalizedLabels(detail.getSubCategories()), opSetClass, sourceContact))
            }
        }
        return resultList
    }

    /**
     * Returns an `Iterator` over a list of strings, which can be used to find this contact.
     *
     * @return an `Iterator` over a list of search strings
     */
    override fun getSearchStringIter(): Iterator<String?> {
        return searchStrings.iterator()
    }

    /**
     * The implementation of the `UIContactDetail` interface for the external source
     * `ContactDetail`s.
     */
    private class SourceContactDetail : UIContactDetailImpl {
        /**
         * Creates an instance of `SourceContactDetail` by specifying the underlying
         * `detail` and the `OperationSet` class for it.
         *
         * detail the underlying `ContactDetail`
         * category detail category string
         * subCategories the detail list of sub-categories
         * opSetClass the `OperationSet` class for the preferred protocol provider
         * sourceContact the source contact
         */
        constructor(detail: ContactDetail, category: String?, subCategories: Collection<String?>?,
                opSetClass: Class<out OperationSet?>?, sourceContact: SourceContact?) : super(detail.detail!!, detail.detail!!, category, subCategories,
                null, null, null, detail) {
            val contactSource = sourceContact!!.contactSource
            if (contactSource is PrefixedContactSourceService) {
                val prefix = (contactSource as PrefixedContactSourceService).phoneNumberPrefix
                if (prefix != null) this.prefix = prefix
            }
            addPreferredProtocolProvider(opSetClass!!, detail.getPreferredProtocolProvider(opSetClass)!!)
            addPreferredProtocol(opSetClass, detail.getPreferredProtocol(opSetClass)!!)
        }

        /**
         * Creates an instance of `SourceContactDetail` by specifying the underlying
         * `detail` and the `OperationSet` class for it.
         *
         * displayName the display name
         * sourceContact the source contact
         */
        constructor(displayName: String, sourceContact: SourceContact?) : super(displayName, displayName, null, null, null, null, null, sourceContact) {}

        /**
         * Returns null to indicate that this detail doesn't support presence.
         *
         * @return null
         */
        override val presenceStatus: PresenceStatus?
            get() = null
    }
    // new SourceContactRightButtonMenu(this);

    /**
     * Returns the `JPopupMenu` opened on a right button click over this
     * `SourceUIContact`.
     *
     * @return the `JPopupMenu` opened on a right button click over this SourceUIContact`
     */
    override val rightButtonMenu: JPopupMenu?
        get() = null
    // new SourceContactRightButtonMenu(this);

    /**
     * Returns the tool tip opened on mouse over.
     *
     * @return the tool tip opened on mouse over
     */
    inner class ExtendedTooltip(b: Boolean) {
        fun setImage(imageIcon: ImageIcon?) {
            // TODO Auto-generated method stub
        }

        fun addLine(jLabels: Array<JLabel>?) {
            // TODO Auto-generated method stub
        }
    }// if there is no telephony

    // Categories aren't supported. This is the case for history records.
// tip.setImage(new ImageIcon(avatarImage));

    // tip.setTitle(sourceContact.getDisplayName());
    // @Override
    val toolTip: ExtendedTooltip
        get() {
            val tip = ExtendedTooltip(true)
            val avatar = getAvatar()
            if (avatar != null && avatar.isNotEmpty()) {
                // tip.setImage(new ImageIcon(avatarImage));
            }

            // tip.setTitle(sourceContact.getDisplayName());
            val displayDetails = displayDetails
            if (displayDetails != null) tip.addLine(arrayOf(JLabel(this.displayDetails)))
            try {
                var details = sourceContact.getContactDetails(ContactDetail.Category.Phone)
                if (details != null && details.isNotEmpty()) addDetailsToToolTip(details, aTalkApp.getResString(R.string.service_gui_PHONES), tip)
                details = sourceContact.getContactDetails(ContactDetail.Category.Email)
                if (details != null && details.isNotEmpty()) addDetailsToToolTip(details, aTalkApp.getResString(R.string.service_gui_EMAILS), tip)
                details = sourceContact.getContactDetails(ContactDetail.Category.InstantMessaging)
                if (details != null && details.isNotEmpty()) addDetailsToToolTip(details, aTalkApp.getResString(R.string.service_gui_INSTANT_MESSAGINGS), tip)
            } catch (e: OperationNotSupportedException) {
                val telDetails = sourceContact.getContactDetails(OperationSetBasicTelephony::class.java)
                // if there is no telephony
                if (telDetails == null || telDetails.isEmpty()) return tip

                // Categories aren't supported. This is the case for history records.
                val allDetails = sourceContact.contactDetails
                addDetailsToToolTip(allDetails!!, aTalkApp.getResString(R.string.service_gui_CALL_WITH), tip)
            }
            return tip
        }

    private fun addDetailsToToolTip(details: List<ContactDetail>, i18nString: String, tip: ExtendedTooltip) {
        // TODO Auto-generated method stub
    }

    private fun addDetailsToToolTip(details: List<ContactDetail>, category: String) // , ExtendedTooltip toolTip)
    {
        var contactDetail: ContactDetail

        // JLabel categoryLabel = new JLabel(category, null, JLabel.LEFT);
        // categoryLabel.setFont(categoryLabel.getFont().deriveFont(Font.BOLD));
        // categoryLabel.setForeground(Color.DARK_GRAY);

        // toolTip.addLine(null, " ");
        // toolTip.addLine(new JLabel[] { categoryLabel });
        for (detail in details) {
            contactDetail = detail
            val subCategories = contactDetail.getSubCategories()
            val jLabels = arrayOfNulls<JLabel>(subCategories.size + 1)
            var i = 0
            for (subCategory in subCategories) {
                val label = JLabel(getInternationalizedLabel(subCategory))
                //label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setForeground(Color.GRAY)
                jLabels[i] = label
                i++
            }
            var labelText: String?
            if (ConfigurationUtils.isHideAddressInCallHistoryTooltipEnabled) {
                labelText = contactDetail.displayName
                if (StringUtils.isEmpty(labelText)) labelText = contactDetail.detail
            } else {
                labelText = contactDetail.detail
            }
            jLabels[i] = JLabel(filterAddressDisplay(labelText!!))
            // toolTip.addLine(jLabels);
        }
    }

    /**
     * Returns the internationalized category corresponding to the given `ContactDetail
     * .Category`.
     *
     * category the `ContactDetail.SubCategory`, for which we would like to obtain an
     * internationalized label
     * @return the internationalized label corresponding to the given category
     */
    protected fun getInternationalizedLabel(category: ContactDetail.Category?): String? {
        if (category == null) return null
        var categoryString: String? = null
        when (category) {
            ContactDetail.Category.Address -> categoryString = aTalkApp.getResString(R.string.service_gui_ADDRESS)
            ContactDetail.Category.Email -> categoryString = aTalkApp.getResString(R.string.service_gui_EMAIL)
            ContactDetail.Category.Personal -> categoryString = aTalkApp.getResString(R.string.service_gui_PERSONAL)
            ContactDetail.Category.Organization -> categoryString = aTalkApp.getResString(R.string.service_gui_ORGANIZATION)
            ContactDetail.Category.Phone -> categoryString = aTalkApp.getResString(R.string.service_gui_PHONE)
            ContactDetail.Category.InstantMessaging -> categoryString = aTalkApp.getResString(R.string.service_gui_IM)
            else -> {}
        }
        return categoryString
    }

    /**
     * Returns a collection of internationalized string corresponding to the given subCategories.
     *
     * subCategories an Iterator over a list of `ContactDetail.SubCategory`s
     * @return a collection of internationalized string corresponding to the given subCategories
     */
    private fun getInternationalizedLabels(subCategories: Collection<SubCategory?>): Collection<String?> {
        val labels = LinkedList<String?>()
        for (subCategory in subCategories) {
            labels.add(getInternationalizedLabel(subCategory))
        }
        return labels
    }

    /**
     * Returns the internationalized label corresponding to the given category.
     *
     * subCategory the `ContactDetail.SubCategory`, for which we would like to obtain an
     * internationalized label
     * @return the internationalized label corresponding to the given category
     */
    protected fun getInternationalizedLabel(subCategory: SubCategory?): String? {
        if (subCategory == null) return null
        val label = when (subCategory) {
            SubCategory.City -> aTalkApp.getResString(R.string.service_gui_CITY)
            SubCategory.Country -> aTalkApp.getResString(R.string.service_gui_COUNTRY)
            SubCategory.Fax -> aTalkApp.getResString(R.string.service_gui_FAX)
            SubCategory.Home -> aTalkApp.getResString(R.string.service_gui_HOME)
            SubCategory.HomePage -> aTalkApp.getResString(R.string.service_gui_HOME_PAGE)
            SubCategory.JobTitle -> aTalkApp.getResString(R.string.service_gui_JOB_TITLE)
            SubCategory.LastName -> aTalkApp.getResString(R.string.service_gui_LAST_NAME)
            SubCategory.Mobile -> aTalkApp.getResString(R.string.service_gui_MOBILE_PHONE)
            SubCategory.Name -> aTalkApp.getResString(R.string.service_gui_NAME)
            SubCategory.Nickname -> aTalkApp.getResString(R.string.service_gui_NICKNAME)
            SubCategory.Other -> aTalkApp.getResString(R.string.service_gui_OTHER)
            SubCategory.PostalCode -> aTalkApp.getResString(R.string.service_gui_POSTAL_CODE)
            SubCategory.Street -> aTalkApp.getResString(R.string.service_gui_STREET)
            SubCategory.Work -> aTalkApp.getResString(R.string.service_gui_WORK_PHONE)
            SubCategory.AIM, SubCategory.ICQ, SubCategory.Jabber, SubCategory.Yahoo, SubCategory.Skype, SubCategory.GoogleTalk -> subCategory.value()
            else -> null
        }
        return label
    }// uiGroup.getParentUISource().getContactCustomActionButtons(sourceContact);

    /**
     * Returns all custom action buttons for this notification contact.
     *
     * @return a list of all custom action buttons for this notification contact
     */
    override val contactCustomActionButtons: Collection<SIPCommButton>?
        get() = if (sourceContact != null) null else null

    /**
     * Returns all custom action menu items for this contact.
     *
     * initActions if `true` the actions will be reloaded.
     * @return a list of all custom action menu items for this contact.
     */
    override fun getContactCustomActionMenuItems(initActions: Boolean): Collection<JMenuItem>? {
        return if (sourceContact != null) null else null
        // uiGroup.getParentUISource().getContactCustomActionMenuItems(sourceContact, initActions);
    }

    companion object {
        /**
         * Whether we should filter all call details only to numbers.
         */
        private const val FILTER_CALL_DETAILS_TO_NUMBERS_PROP = "gui.contactlist.contactsource.FILTER_CALL_DETAILS_TO_NUMBERS"
    }
}