/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Spinner
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.contactlist.MetaContactGroup
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent
import net.java.sip.communicator.service.contactlist.event.MetaContactListAdapter
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.account.AccountUtils.registeredProviders
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.account.Account
import org.atalk.hmos.gui.account.AccountsListAdapter
import org.atalk.hmos.gui.util.ViewUtil.getTextViewValue
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber

/**
 * This activity allows user to add new contacts.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AddContactActivity : OSGiActivity() {
    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_contact)
        setMainTitle(R.string.service_gui_ADD_CONTACT)
        initAccountSpinner()
        initContactGroupSpinner()
    }

    /**
     * Initializes "select account" spinner with existing accounts.
     */
    private fun initAccountSpinner() {
        val accountsSpinner = findViewById<Spinner>(R.id.selectAccountSpinner)
        val providers = registeredProviders
        val accounts = ArrayList<AccountID>()
        var idx = 0
        var selectedIdx = -1
        for (provider in providers) {
            val opSet = provider.getOperationSet(OperationSetPresence::class.java)
            if (opSet != null) {
                val account = provider.accountID
                accounts.add(account)
                if (selectedIdx == -1 && account.isPreferredProvider) {
                    selectedIdx = idx
                }
                idx++
            }
        }
        val accountsAdapter = AccountsListAdapter(this,
                R.layout.select_account_row, R.layout.select_account_dropdown, accounts, true)
        accountsSpinner.adapter = accountsAdapter

        // if we have only select account option and only one account select the available account
        if (accounts.size == 1) accountsSpinner.setSelection(0) else accountsSpinner.setSelection(selectedIdx)
    }

    /**
     * Initializes select contact group spinner with contact groups.
     */
    private fun initContactGroupSpinner() {
        val groupSpinner = findViewById<Spinner>(R.id.selectGroupSpinner)
        val contactGroupAdapter = MetaContactGroupAdapter(this, R.id.selectGroupSpinner, true, true)

        // Already default to use in MetaContactGroupAdapter.
        // contactGroupAdapter.setItemLayout(R.layout.simple_spinner_item);
        // contactGroupAdapter.setDropDownLayout(R.layout.simple_spinner_dropdown_item);
        groupSpinner.adapter = contactGroupAdapter
    }

    /**
     * Method fired when "add" button is clicked.
     *
     * @param v add button's `View`
     */
    fun onAddClicked(v: View?) {
        val accountsSpinner = findViewById<Spinner>(R.id.selectAccountSpinner)
        val selectedAcc = accountsSpinner.selectedItem as Account
        if (selectedAcc == null) {
            Timber.e("No account selected")
            return
        }
        val pps = selectedAcc.protocolProvider
        if (pps == null) {
            Timber.e("No provider registered for account %s", selectedAcc.getAccountName())
            return
        }
        val content = findViewById<View>(android.R.id.content)
        val contactAddress = getTextViewValue(content, R.id.editContactName)
        val displayName = getTextViewValue(content, R.id.editDisplayName)
        if (!TextUtils.isEmpty(displayName)) {
            addRenameListener(pps, null, contactAddress, displayName)
        }
        val groupSpinner = findViewById<Spinner>(R.id.selectGroupSpinner)

        // "Create group .." selected but no entered value
        val mGroup = try {
            groupSpinner.selectedItem as MetaContactGroup
        } catch (e: Exception) {
            aTalkApp.showToastMessage(R.string.service_gui_CREATE_GROUP_INVALID, e.message)
            return
        }
        ContactListUtils.addContact(pps, mGroup, contactAddress)
        finish()
    }

    fun onCancelClicked(v: View?) {
        finish()
    }

    /**
     * Adds a rename listener.
     *
     * @param protocolProvider the protocol provider to which the contact was added
     * @param metaContact the `MetaContact` if the new contact was added to an existing meta contact
     * @param contactAddress the address of the newly added contact
     * @param displayName the new display name
     */
    private fun addRenameListener(protocolProvider: ProtocolProviderService, metaContact: MetaContact?,
            contactAddress: String?, displayName: String?) {
        AndroidGUIActivator.contactListService.addMetaContactListListener(
                object : MetaContactListAdapter() {
                    override fun metaContactAdded(evt: MetaContactEvent) {
                        if (evt.getSourceMetaContact().getContact(contactAddress, protocolProvider) != null) {
                            renameContact(evt.getSourceMetaContact(), displayName)
                        }
                    }

                    override fun protoContactAdded(evt: ProtoContactEvent) {
                        if (metaContact != null && evt.getNewParent() == metaContact) {
                            renameContact(metaContact, displayName)
                        }
                    }
                })
    }

    /**
     * Renames the given meta contact.
     *
     * @param metaContact the `MetaContact` to rename
     * @param displayName the new display name
     */
    private fun renameContact(metaContact: MetaContact, displayName: String?) {
        object : Thread() {
            override fun run() {
                AndroidGUIActivator.contactListService.renameMetaContact(metaContact, displayName)
            }
        }.start()
    }
}