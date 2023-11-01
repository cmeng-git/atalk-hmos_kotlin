/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.call

import net.java.sip.communicator.service.contactsource.ContactDetail
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.ProtocolIcon
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.UtilActivator
import net.java.sip.communicator.util.account.AccountUtils
import net.java.sip.communicator.util.skin.Skinnable
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatTransport
import org.atalk.hmos.gui.contactlist.UIContactDetailImpl
import org.atalk.hmos.gui.contactlist.UIContactImpl
import org.atalk.hmos.gui.dialogs.DialogActivity
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JMenuItem

/**
 * The `ChooseCallAccountDialog` is the dialog shown when calling a
 * contact in order to let the user choose the account he'd prefer to use in
 * order to call this contact.
 *
 * @author Yana Stamcheva
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
open class ChooseCallAccountPopupMenu /* extends SIPCommPopupMenu */ : Skinnable {
    /**
     * The invoker component.
     */
    private val invoker: JComponent

    /**
     * The call interface listener, which would be notified once the call interface is created.
     */
    private var callInterfaceListener: CallInterfaceListener? = null

    /**
     * The `MetaContact` we're calling.
     */
    private var uiContact: UIContactImpl? = null

    /**
     * Creates this dialog.
     *
     * @param invoker the invoker of this pop up menu
     * @param contactToCall the contact to call
     * @param telephonyProviders a list of all possible telephony providers
     * @param l `CallInterfaceListener` instance
     */
    constructor(
            invoker: JComponent,
            contactToCall: String?,
            telephonyProviders: List<ProtocolProviderService>,
            l: CallInterfaceListener?) : this(invoker, contactToCall, telephonyProviders, OperationSetBasicTelephony::class.java) {
        callInterfaceListener = l
    }
    /**
     * Creates this dialog.
     *
     * invoker the invoker of this pop up menu
     * contactToCall the contact to call
     * telephonyProviders a list of all possible telephony providers
     * opSetClass the operation set class indicating what operation
     * would be performed when a given item is selected from the menu
     */
    /**
     * Creates this dialog.
     *
     * @param invoker the invoker of this pop up menu
     * @param contactToCall the contact to call
     * @param telephonyProviders a list of all possible telephony providers
     */
    @JvmOverloads
    constructor(
            invoker: JComponent,
            contactToCall: String?,
            telephonyProviders: List<ProtocolProviderService>,
            opSetClass: Class<out OperationSet> = OperationSetBasicTelephony::class.java) {
        this.invoker = invoker
        init(UtilActivator.resources.getI18NString(getI18NKeyCallVia())!!)
        for (provider in telephonyProviders) {
            addTelephonyProviderItem(provider, contactToCall, opSetClass)
        }
    }
    /**
     * Creates this dialog by specifying a list of telephony contacts to choose
     * from.
     *
     * invoker the invoker of this pop up
     * telephonyObjects the list of telephony contacts to select through
     * opSetClass the operation class, which indicates what action would
     * be performed if an item is selected from the list
     */
    /**
     * Creates this dialog by specifying a list of telephony contacts to choose
     * from.
     *
     * @param invoker the invoker of this pop up
     * @param telephonyObjects the list of telephony contacts to select through
     */
    @JvmOverloads
    constructor(invoker: JComponent, telephonyObjects: List<*>, opSetClass: Class<out OperationSet> = OperationSetBasicTelephony::class.java) {
        this.invoker = invoker
        init(UtilActivator.resources.getI18NString(getI18NKeyChooseContact())!!)
        for (o in telephonyObjects) {
            if (o is UIContactDetailImpl) addTelephonyContactItem(o, opSetClass) else if (o is ChatTransport) addTelephonyChatTransportItem(o, opSetClass)
        }
    }

    /**
     * Returns the key to use for choose contact string. Can be overridden
     * by extenders.
     *
     * @return the key to use for choose contact string.
     */
    private fun getI18NKeyChooseContact(): String {
        return "service.gui.CHOOSE_CONTACT"
    }

    /**
     * Returns the key to use for choose contact string. Can be overridden
     * by extenders.
     *
     * @return the key to use for choose contact string.
     */
    private fun getI18NKeyCallVia(): String {
        return "service.gui.CALL_VIA"
    }

    /**
     * Initializes and add some common components.
     *
     * @param infoString the string we'd like to show on the top of this
     * popup menu
     */
    private fun init(infoString: String) {
        // setInvoker(invoker);
        // this.add(createInfoLabel(infoString));
        // this.addSeparator();
        // this.setFocusable(true);
    }

    /**
     * Adds the given `telephonyProvider` to the list of available
     * telephony providers.
     *
     * @param telephonyProvider the provider to add.
     * @param contactString the contact to call when the provider is selected
     * @param opSetClass the operation set class indicating what action would
     * be performed when an item is selected
     */
    private fun addTelephonyProviderItem(
            telephonyProvider: ProtocolProviderService,
            contactString: String?,
            opSetClass: Class<out OperationSet>) {
        val providerItem = ProviderMenuItem(telephonyProvider)
        providerItem.addActionListener {
            if (uiContact != null) itemSelected(opSetClass, providerItem.getProtocolProvider(), contactString, uiContact) else itemSelected(opSetClass, providerItem.getProtocolProvider(), contactString)
            if (callInterfaceListener != null) callInterfaceListener!!.callInterfaceStarted()

            // ChooseCallAccountPopupMenu.this.setVisible(false);
        }
        // this.add(providerItem);
    }

    /**
     * Adds the given `telephonyContact` to the list of available telephony contact.
     *
     * @param telephonyContact the telephony contact to add
     * @param opSetClass the operation set class, that indicates the action that
     * would be performed when an item is selected
     */
    private fun addTelephonyContactItem(telephonyContact: UIContactDetailImpl,
            opSetClass: Class<out OperationSet>) {
        val contactItem = ContactMenuItem(telephonyContact)
        contactItem.addActionListener(ActionListener {
            val providers = AccountUtils.getOpSetRegisteredProviders(opSetClass,
                    telephonyContact.getPreferredProtocolProvider(opSetClass),
                    telephonyContact.getPreferredProtocol(opSetClass))
            if (providers == null || providers.isEmpty()) {
                DialogActivity.showDialog(aTalkApp.globalContext,
                        R.string.service_gui_CALL_FAILED, R.string.service_gui_NO_ONLINE_TELEPHONY_ACCOUNT)
                return@ActionListener
            } else if (providers.size > 1) {
                itemSelected(opSetClass, providers, telephonyContact.address)
            } else  // providersCount == 1
            {
                val provider = providers[0]
                val contactAddress = telephonyContact.address
                if (uiContact != null) itemSelected(opSetClass, provider, contactAddress, uiContact) else itemSelected(opSetClass, provider, contactAddress)
            }
            // ChooseCallAccountPopupMenu.this.setVisible(false);
        })
        val category = telephonyContact.category
        if (category != null && category == ContactDetail.Category.Phone.toString()) {
            val index = findPhoneItemIndex()
            // if (index < 0)
            //     add(contactItem);
            // else
            //     insert(contactItem, findPhoneItemIndex());
        } else {
            // Component lastComp = getComponent(getComponentCount() - 1);
            // if (lastComp instanceof ContactMenuItem)
            //     category = ((ContactMenuItem) lastComp).getCategory();
            //
            // if (category != null
            //     && category.equals(ContactDetail.Category.Phone))
            //     addSeparator();
            //
            // add(contactItem);
        }
    }

    /**
     * Returns the index of a phone menu item.
     *
     * @return the index of a phone menu item
     */
    private fun findPhoneItemIndex(): Int {
        // for (int i = getComponentCount() - 1; i > 1; i--)
        // {
        // Component c = getComponent(i);
        //
        // if (c instanceof ContactMenuItem)
        // {
        //     String category = ((ContactMenuItem) c).getCategory();
        //     if (category == null
        //         || !category.equals(ContactDetail.Category.Phone))
        //     continue;
        // }
        // else if (c instanceof JSeparator)
        //     index = i - 1;
        // else
        //     return index;
        // }
        return -1
    }

    /**
     * Adds the given `ChatTransport` to the list of available
     * telephony chat transports.
     *
     * @param telTransport the telephony chat transport to add
     * @param opSetClass the class of the operation set indicating the operation
     * to be executed in the item is selected
     */
    private fun addTelephonyChatTransportItem(telTransport: ChatTransport,
            opSetClass: Class<out OperationSet>) {
        // final ChatTransportMenuItem transportItem
        // = new ChatTransportMenuItem(telTransport);

        // transportItem.addActionListener(new ActionListener()
        // {
        // public void actionPerformed(ActionEvent e)
        // {
        //     ProtocolProviderService provider
        //         = telTransport.getProtocolProvider();
        //     String contactAddress = telTransport.getName();

        //     if (uiContact != null)
        //         CallManager.createCall(
        //             opSetClass, provider, contactAddress, uiContact);
        //     else
        //         CallManager.createCall(
        //             opSetClass, provider, contactAddress);
        //
        //     ChooseCallAccountPopupMenu.this.setVisible(false);
        // }
        // });

        // this.add(transportItem);
    }

    /**
     * Shows the dialog at the given location.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    fun showPopupMenu(x: Int, y: Int) {
        // setLocation(x, y);
        // setVisible(true);
    }

    /**
     * Shows this popup menu regarding to its invoker location.
     */
    fun showPopupMenu() {
        val location = Point(invoker.x, invoker.y + invoker.height)

        // SwingUtilities
        // .convertPointToScreen(location, invoker.getParent());
        // setLocation(location);
        // setVisible(true);
    }

    /**
     * Sets the `UIContactImpl` we're currently calling.
     *
     * @param uiContact the `UIContactImpl` we're currently calling
     */
    fun setUIContact(uiContact: UIContactImpl?) {
        this.uiContact = uiContact
    }

    /**
     * Creates the info label.
     *
     * @param infoString the string we'd like to show on the top of this
     * popup menu
     * @return the created info label
     */
    private fun createInfoLabel(infoString: String): Component? {
        val infoLabel = JMenuItem()
        infoLabel.setEnabled(false)
        // infoLabel.setFocusable(false);
        infoLabel.setText("<html><b>$infoString</b></html>")
        return null //infoLabel;
    }

    /**
     * Item was selected, give a chance for extenders to override.
     *
     * @param opSetClass the operation set to use.
     * @param protocolProviderService the protocol provider
     * @param contact the contact address
     * @param uiContact the `MetaContact` selected
     */
    protected fun itemSelected(opSetClass: Class<out OperationSet>?,
            protocolProviderService: ProtocolProviderService?, contact: String?, uiContact: UIContactImpl?) {
        // CallManager.createCall(
        // opSetClass,
        // protocolProviderService,
        // contact,
        // uiContact);
    }

    /**
     * Item was selected, give a chance for extenders to override.
     *
     * @param opSetClass the operation set to use.
     * @param protocolProviderService the protocol provider
     * @param contact the contact address selected
     */
    protected fun itemSelected(opSetClass: Class<out OperationSet>?,
            protocolProviderService: ProtocolProviderService?, contact: String?) {
        // CallManager.createCall(
        // opSetClass,
        // protocolProviderService,
        // contact);
    }

    /**
     * Item was selected, give a chance for extenders to override.
     *
     * @param opSetClass the operation set to use.
     * @param providers list of available protocol providers
     * @param contact the contact address selected
     */
    private fun itemSelected(opSetClass: Class<out OperationSet>?,
            providers: List<ProtocolProviderService?>?, contact: String?) {
        // ChooseCallAccountDialog callAccountDialog = new ChooseCallAccountDialog(contact, opSetClass, providers);
        //
        // if (uiContact != null)
        // callAccountDialog.setUIContact(uiContact);
        // callAccountDialog.setVisible(true);
    }

    /**
     * A custom menu item corresponding to a specific
     * `ProtocolProviderService`.
     */
    private inner class ProviderMenuItem(private val protocolProvider: ProtocolProviderService) : JMenuItem(), Skinnable {
        init {
            setText(protocolProvider.accountID.displayName)
            loadSkin()
        }

        fun getProtocolProvider(): ProtocolProviderService {
            return protocolProvider
        }

        /**
         * Reloads protocol icon.
         */
        override fun loadSkin() {
            val protocolIcon = protocolProvider.protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_16x16)

            // if (protocolIcon != null)
            //  this.setIcon(ImageLoader.getIndexedProtocolIcon(
            //    ImageUtils.getBytesInImage(protocolIcon),
            //    protocolProvider));
        }
    }

    /**
     * A custom menu item corresponding to a specific protocol `Contact`.
     */
    private inner class ContactMenuItem(private val contact: UIContactDetailImpl) : JMenuItem(), Skinnable {
        init {
            val itemName = StringBuilder("<html>")
            val labels = contact.getLabels()
            if (labels != null && labels.hasNext()) while (labels.hasNext()) itemName.append("<b style=\"color: gray\">")
                    .append(labels.next()!!.lowercase())
                    .append("</b> ")
            itemName.append(contact.address).append("</html>")
            setText(itemName.toString())
            loadSkin()
        }

        /**
         * Returns the category of the underlying contact detail.
         *
         * @return the category of the underlying contact detail
         */
        fun getCategory(): String? {
            return contact.category
        }

        /**
         * Reloads contact icon.
         */
        override fun loadSkin() {
            // ImageIcon contactIcon = contact.getStatusIcon();
            //
            // if (contactIcon == null)
            // {
            //  PresenceStatus status = contact.getPresenceStatus();
            //
            //  BufferedImage statusIcon = null;
            //  if (status != null)
            //  statusIcon = Constants.getStatusIcon(status);
            //
            //  if (statusIcon != null)
            //  contactIcon = ImageLoader.getIndexedProtocolIcon(
            //   statusIcon,
            //   contact.getPreferredProtocolProvider(null));
            // }
            //
            // if (contactIcon != null)
            //  this.setIcon(ImageLoader.getIndexedProtocolIcon(
            //  contactIcon.getImage(),
            //  contact.getPreferredProtocolProvider(null)));
        }
    }

    /**
     * A custom menu item corresponding to a specific `ChatTransport`.
     */
    private inner class ChatTransportMenuItem(private val chatTransport: ChatTransport) : JMenuItem(), Skinnable {
        init {
            setText(chatTransport.name)
            loadSkin()
        }

        /**
         * Reloads transport icon.
         */
        override fun loadSkin() {
            val status = chatTransport.status
            val statusIconBytes = status!!.statusIcon
            val statusIcon: Icon? = null
            if (statusIconBytes != null && statusIconBytes.isNotEmpty()) {
                //  statusIcon = ImageLoader.getIndexedProtocolIcon(
                //  ImageUtils.getBytesInImage(statusIconBytes),
                //  chatTransport.getProtocolProvider());
            }

            // if (statusIcon != null)
            //  this.setIcon(statusIcon);
        }
    }

    /**
     * Reloads all menu items.
     */
    override fun loadSkin() {
        // Component[] components = getComponents();
        // for(Component component : components) {
        //      if(component instanceof Skinnable) {
        //          Skinnable skinnableComponent = (Skinnable) component;
        //          skinnableComponent.loadSkin();
        //      }
        // }
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}