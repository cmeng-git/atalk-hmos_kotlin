/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.argdelegation.UriHandler
import net.java.sip.communicator.service.gui.ExportedWindow
import net.java.sip.communicator.service.gui.PopupDialog
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.AccountManagerEvent
import net.java.sip.communicator.service.protocol.event.AccountManagerListener
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jxmpp.stringprep.XmppStringprepException
import org.osgi.framework.*
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.lang.NullPointerException
import java.util.*
import java.util.regex.Pattern

/**
 * The jabber implementation of the URI handler. This class handles xmpp URIs by trying to establish
 * a chat with them or add you to a chatRoom.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class UriHandlerJabberImpl(protoFactory: ProtocolProviderFactory?) : UriHandler, ServiceListener, AccountManagerListener {
    /**
     * The protocol provider factory that created us.
     */
    private val protoFactory: ProtocolProviderFactory

    /**
     * A reference to the OSGi registration we create with this handler.
     */
    private var ourServiceRegistration: ServiceRegistration<*>? = null

    /**
     * The object that we are using to synchronize our service registration.
     */
    private val registrationLock = Any()

    /**
     * The `AccountManager` which loads the stored accounts of [.protoFactory] and
     * to be monitored when the mentioned loading is complete so that any pending [.uris] can be handled
     */
    private var accountManager: AccountManager? = null

    /**
     * The indicator (and its synchronization lock) which determines whether the stored accounts of
     * [.protoFactory] have already been loaded.
     *
     * Before the loading of the stored accounts (even if there're none) of the
     * `protoFactory` is completed, no handling of URIs is to be performed because
     * there's neither information which account to handle the URI in case there're stored accounts
     * available nor ground for warning the user a registered account is necessary to handle
     * URIs at all in case there're no stored accounts.
     */
    private val storedAccountsAreLoaded = BooleanArray(1)

    /**
     * The list of URIs which have received requests for handling before the stored accounts of the
     * [.protoFactory] have been loaded. They will be handled as soon as the mentioned loading completes.
     */
    private var uris: MutableList<String?>? = null

    /**
     * Marks network fails in order to avoid endless loops.
     */
    private var networkFailReceived = false

    /**
     * Creates an instance of this uri handler, so that it would start handling URIs by passing
     * them to the providers registered by `protoFactory` .
     *
     * protoFactory the provider that created us.
     * @throws NullPointerException if `protoFactory` is `null`.
     */
    init {
        if (protoFactory == null) {
            throw NullPointerException("The ProtocolProviderFactory that a UriHandler is created with cannot be null.")
        }
        this.protoFactory = protoFactory
        hookStoredAccounts()
        this.protoFactory.bundleContext.addServiceListener(this)
        /*
         * Registering the UriHandler isn't strictly necessary if the requirement to register the
         * protoFactory after creating this instance is met.
         */
        registerHandlerService()
    }

    /**
     * Disposes of this `UriHandler` by, for example, removing the listeners it has
     * added in its constructor (in order to prevent memory leaks, for one).
     */
    fun dispose() {
        protoFactory.bundleContext.removeServiceListener(this)
        unregisterHandlerService()
        unhookStoredAccounts()
    }

    /**
     * Sets up (if not set up already) listening for the loading of the stored accounts of
     * [.protoFactory] in order to make it possible to discover when the prerequisites for handling URIs are met.
     */
    private fun hookStoredAccounts() {
        if (accountManager == null) {
            val bundleContext = protoFactory.bundleContext
            accountManager = bundleContext.getService(
                    bundleContext.getServiceReference(AccountManager::class.java.name)) as AccountManager
            accountManager!!.addListener(this)
        }
    }

    /**
     * Reverts (if not reverted already) the setup performed by a previous chat to [.hookStoredAccounts].
     */
    private fun unhookStoredAccounts() {
        if (accountManager != null) {
            accountManager!!.removeListener(this)
            accountManager = null
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.protocol.event.AccountManagerListener#handleAccountManagerEvent
     * (net.java.sip.communicator.service.protocol.event.AccountManagerEvent)
     */
    override fun handleAccountManagerEvent(event: AccountManagerEvent) {
        /*
         * When the loading of the stored accounts of protoFactory is complete, the prerequisites
         * for handling URIs have been met so it's time to load any handling requests which have
         * come before the loading and were thus delayed in uris.
         */
        if (AccountManagerEvent.STORED_ACCOUNTS_LOADED == event.type && protoFactory === event.factory) {
            var uris: List<String?>? = null
            synchronized(storedAccountsAreLoaded) {
                storedAccountsAreLoaded[0] = true
                if (this.uris != null) {
                    uris = this.uris
                    this.uris = null
                }
            }
            unhookStoredAccounts()
            if (uris != null) {
                for (uri in uris!!) {
                    handleUri(uri)
                }
            }
        }
    }

    /**
     * Registers this UriHandler with the bundle context so that it could start handling URIs
     */
    private fun registerHandlerService() {
        synchronized(registrationLock) {
            if (ourServiceRegistration != null) {
                // ... we are already registered (this is probably happening during startup)
                return
            }
            val registrationProperties = Hashtable<String, String>()
            for (protocol in protocol) {
                registrationProperties[UriHandler.PROTOCOL_PROPERTY] = protocol
            }
            ourServiceRegistration = JabberActivator.bundleContext.registerService(
                    UriHandler::class.java.name, this, registrationProperties)
        }
    }

    /**
     * Unregisters this UriHandler from the bundle context.
     */
    private fun unregisterHandlerService() {
        synchronized(registrationLock) {
            if (ourServiceRegistration != null) {
                ourServiceRegistration!!.unregister()
                ourServiceRegistration = null
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override val protocol: Array<String>
        get() = arrayOf("xmpp")

    /**
     * Parses the specified URI and creates a chat with the currently active im operation set.
     *
     * uri the xmpp URI that we have to handle.
     */
    override fun handleUri(uri: String?) {
        /*
         * TODO If the requirement to register the factory service after creating this instance is
         * broken, we'll end up not handling the URIs.
         */
        synchronized(storedAccountsAreLoaded) {
            if (!storedAccountsAreLoaded[0]) {
                if (uris == null) {
                    uris = LinkedList()
                }
                uris!!.add(uri)
                return
            }
        }
        val provider: ProtocolProviderService? = try {
            selectHandlingProvider(uri)
        } catch (exc: OperationFailedException) {
            // The operation has been canceled by the user. Bail out.
            Timber.log(TimberLog.FINER, "User canceled handling of uri %s", uri)
            return
        }

        // if provider is null then we need to tell the user to create an account
        if (provider == null) {
            showErrorMessage("You need to configure at least one XMPP account to be able to call $uri", null)
            return
        }
        if (!uri!!.contains("?")) {
            val presenceOpSet = provider.getOperationSet(OperationSetPersistentPresence::class.java)
            val contactId = uri.substring(uri.indexOf(':') + 1)
            // todo check url!!
            // Set the email pattern string
            val p = Pattern.compile(".+@.+")
            if (!p.matcher(contactId).matches()) {
                showErrorMessage("Wrong contact id : $uri", null)
                return
            }
            val contact = presenceOpSet!!.findContactByID(contactId)
            if (contact == null) {
                val result = JabberActivator.uIService!!.popupDialog!!.showConfirmPopupDialog(
                        "Do you want to add the contact : $contactId ?",
                        "Add contact", PopupDialog.YES_NO_OPTION)
                if (result == PopupDialog.YES_OPTION) {
                    val ex = JabberActivator.uIService!!.getExportedWindow(
                            ExportedWindow.ADD_CONTACT_WINDOW, arrayOf(contactId))
                    ex!!.isVisible = true
                }
                return
            }
            JabberActivator.uIService!!.getChat(contact)!!.setChatVisible(true)
        } else {
            var cRoom = uri.replaceFirst((protocol.contentToString() + ":").toRegex(), "")
            val ix = cRoom.indexOf("?")
            val param = cRoom.substring(ix + 1)
            cRoom = cRoom.substring(0, ix)
            if (param.equals("join", ignoreCase = true)) {
                val mchatOpSet = provider.getOperationSet(OperationSetMultiUserChat::class.java)
                try {
                    val room = mchatOpSet!!.findRoom(cRoom)
                    room?.join()
                } catch (exc: OperationFailedException) {
                    // if we are not online we get this error will wait for it and then will try
                    // to handle once again
                    if (exc.getErrorCode() == OperationFailedException.NETWORK_FAILURE
                            && !networkFailReceived) {
                        networkFailReceived = true
                        val presenceOpSet = provider.getOperationSet(OperationSetPresence::class.java)
                        presenceOpSet!!.addProviderPresenceStatusListener(ProviderStatusListener(uri, presenceOpSet))
                    } else showErrorMessage("Error joining to  $cRoom", exc)
                } catch (exc: OperationNotSupportedException) {
                    showErrorMessage("Join to $cRoom, not supported!", exc)
                } catch (e: XmppStringprepException) {
                    e.printStackTrace()
                }
            } else showErrorMessage("Unknown param : $param", null)
        }
    }

    /**
     * The point of implementing a service listener here is so that we would only register our own
     * uri handling service and thus only handle URIs while the factory is available as an OSGi
     * service. We remove ourselves when our factory unregisters its service reference.
     *
     * event the OSGi `ServiceEvent`
     */
    override fun serviceChanged(event: ServiceEvent) {
        val sourceService = JabberActivator.bundleContext.getService(event.serviceReference)

        // ignore anything but our protocol factory.
        if (sourceService != protoFactory) {
            return
        }

        when (event.type) {
            ServiceEvent.REGISTERED ->                 // our factory has just been registered as a service ...
                registerHandlerService()
            ServiceEvent.UNREGISTERING ->                 // our factory just died - seppuku.
                unregisterHandlerService()
            else -> {}
        }
    }

    /**
     * Uses the `UIService` to show an error `message` and log and `exception`.
     *
     * message the message that we'd like to show to the user.
     * exc the exception that we'd like to log
     */
    private fun showErrorMessage(message: String, exc: Exception?) {
        //		JabberActivator.getUIService().getPopupDialog()
        //				.showMessagePopupDialog(message, "Failed to create chat!",
        //						PopupDialog.ERROR_MESSAGE);
        DialogActivity.showDialog(aTalkApp.globalContext, "Failed to create chat!", message)
        Timber.e(exc, "%s", message)
    }

    /**
     * Returns the default provider that we are supposed to handle URIs through or null if there
     * aren't any. Depending on the implementation this method may require user intervention so
     * make sure you don't rely on a quick outcome when chatting it.
     *
     * uri the uri that we'd like to handle with the provider that we are about to select.
     * @return the provider that we should handle URIs through.
     * @throws OperationFailedException with code `OPERATION_CANCELED` if the users.
     */
    @Throws(OperationFailedException::class)
    fun selectHandlingProvider(uri: String?): ProtocolProviderService? {
        val registeredAccounts = protoFactory.getRegisteredAccounts()

        // if we don't have any providers - return null.
        if (registeredAccounts.size == 0) {
            return null
        }

        // if we only have one provider - select it
        if (registeredAccounts.size == 1) {
            val providerReference = protoFactory.getProviderForAccount(registeredAccounts[0])
            return JabberActivator.bundleContext.getService(providerReference) as ProtocolProviderService
        }

        // otherwise - ask the user.
        val providers = ArrayList<ProviderComboBoxEntry>()
        for (accountID in registeredAccounts) {
            val providerReference = protoFactory.getProviderForAccount(accountID)
            val provider = JabberActivator.bundleContext.getService(providerReference) as ProtocolProviderService
            providers.add(ProviderComboBoxEntry(provider))
        }
        val msg = "Please select the account that you would like \nto use to chat with $uri"
        val title = "Account Selection"
        val result = JabberActivator.uIService!!.popupDialog!!
                .showInputPopupDialog(msg, title, PopupDialog.OK_CANCEL_OPTION, providers.toTypedArray(), providers[0])
                ?: throw OperationFailedException("Operation cancelled", OperationFailedException.OPERATION_CANCELED)
        return (result as ProviderComboBoxEntry).provider
    }

    /**
     * A class that we use to wrap providers before showing them to the user through a selection
     * popup dialog from the UIService.
     */
    private class ProviderComboBoxEntry(val provider: ProtocolProviderService) {
        /**
         * Returns a human readable `String` representing the provider encapsulated by this
         * class.
         *
         * @return a human readable string representing the provider.
         */
        override fun toString(): String {
            return provider.accountID.accountJid
        }
    }

    /**
     * Waiting for the provider to become online and then handle the uri.
     */
    private inner class ProviderStatusListener(private val uri: String?, private val parentOpSet: OperationSetPresence?) : ProviderPresenceStatusListener {
        override fun providerStatusChanged(evt: ProviderPresenceStatusChangeEvent) {
            if (evt.getNewStatus().isOnline) {
                parentOpSet!!.removeProviderPresenceStatusListener(this)
                handleUri(uri)
            }
        }

        override fun providerStatusMessageChanged(evt: PropertyChangeEvent) {}
    }
}