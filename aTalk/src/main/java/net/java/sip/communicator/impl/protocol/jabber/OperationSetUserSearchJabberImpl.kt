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

import net.java.sip.communicator.service.protocol.OperationSetUserSearch
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.event.UserSearchProviderEvent
import net.java.sip.communicator.service.protocol.event.UserSearchProviderListener
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smackx.search.ReportedData
import org.jivesoftware.smackx.search.UserSearch
import org.jivesoftware.smackx.search.UserSearchManager
import org.jivesoftware.smackx.xdata.FormField
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import timber.log.Timber

/**
 * This operation set provides utility methods for user search implementation.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class OperationSetUserSearchJabberImpl(
        /**
         * The `ProtocolProviderService` instance.
         */
        private val provider: ProtocolProviderServiceJabberImpl) : OperationSetUserSearch, RegistrationStateChangeListener {
    /**
     * The `UserSearchManager` instance which actually implements the user search.
     */
    private var searchManager: UserSearchManager? = null

    /**
     * The user search service name.
     */
    private var serviceName: DomainBareJid? = null

    /**
     * Whether the user search service is enabled or not.
     */
    private var userSearchEnabled = false

    /**
     * Last received search form from the server.
     */
    private val userSearchForm: UserSearch? = null

    /**
     * A list of `UserSearchProviderListener` listeners which will be notified when the
     * provider user search feature is enabled or disabled.
     */
    private val listeners: MutableList<UserSearchProviderListener?> = ArrayList()

    /**
     * Constructs new `OperationSetUserSearchJabberImpl` instance.
     *
     * provider the provider associated with the operation set.
     */
    init {
        try {
            serviceName = JidCreate.domainBareFrom(provider.accountID
                    .getAccountPropertyString(USER_SEARCH_SERVICE_NAME, ""))
        } catch (e: XmppStringprepException) {
            e.printStackTrace()
        }
        if (serviceName!!.equals("")) {
            provider.addRegistrationStateChangeListener(this)
        } else {
            setUserSearchEnabled(true)
        }
    }

    /**
     * Sets the `userSearchEnabled` property and fires `UserSearchProviderEvent` event.
     *
     * @param isEnabled the value to be set.
     */
    private fun setUserSearchEnabled(isEnabled: Boolean) {
        userSearchEnabled = isEnabled
        val type = if (isEnabled) UserSearchProviderEvent.PROVIDER_ADDED else UserSearchProviderEvent.PROVIDER_REMOVED
        fireUserSearchProviderEvent(UserSearchProviderEvent(provider, type))
    }

    /**
     * Fires `UserSearchProviderEvent` event.
     *
     * @param event the event to be fired.
     */
    private fun fireUserSearchProviderEvent(event: UserSearchProviderEvent) {
        var tmpListeners: List<UserSearchProviderListener?>
        synchronized(listeners) { tmpListeners = ArrayList(listeners) }
        for (l in tmpListeners) l!!.onUserSearchProviderEvent(event)
    }

    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        if (evt.getNewState() == RegistrationState.REGISTERED) {
            discoverSearchService()
        } else if (evt.getNewState() === RegistrationState.UNREGISTERED || evt.getNewState() === RegistrationState.AUTHENTICATION_FAILED || evt.getNewState() === RegistrationState.CONNECTION_FAILED) {
            synchronized(userSearchEnabled) { setUserSearchEnabled(false) }
        }
    }

    /**
     * Tries to discover the user search service name.
     */
    private fun discoverSearchService() {
        object : Thread() {
            override fun run() {
                synchronized(userSearchEnabled) {
                    var serviceNames: List<DomainBareJid?>? = null
                    try {
                        serviceNames = searchManager!!.searchServices
                    } catch (e: NoResponseException) {
                        Timber.e(e, "Failed to search for service names")
                    } catch (e: InterruptedException) {
                        Timber.e(e, "Failed to search for service names")
                    } catch (e: NotConnectedException) {
                        Timber.e(e, "Failed to search for service names")
                    } catch (e: XMPPErrorException) {
                        Timber.e(e, "Failed to search for service names")
                    }
                    if (serviceNames!!.isNotEmpty()) {
                        serviceName = serviceNames.iterator().next()
                        setUserSearchEnabled(true)
                    } else {
                        setUserSearchEnabled(false)
                    }
                }
            }
        }.start()
    }

    /**
     * Creates the `UserSearchManager` instance.
     */
    override fun createSearchManager() {
        if (searchManager == null) {
            searchManager = UserSearchManager(provider.connection)
        }
    }

    /**
     * Releases the `UserSearchManager` instance.
     */
    override fun removeSearchManager() {
        searchManager = null
    }

    /**
     * Performs user search for the searched string and returns the JIDs of the found contacts.
     *
     * @param searchedString the text we want to query the server.
     * @return the list of found JIDs
     */
    override fun search(searchedString: String?): List<CharSequence?>? {
        val data: ReportedData? = try {
            val form = searchManager!!.getSearchForm(serviceName)
            searchManager!!.getSearchResults(form, serviceName)
        } catch (e: XMPPException) {
            Timber.e(e)
            return null
        } catch (e: NotConnectedException) {
            Timber.e(e)
            return null
        } catch (e: InterruptedException) {
            Timber.e(e)
            return null
        } catch (e: NoResponseException) {
            Timber.e(e)
            return null
        }
        if (data == null) {
            Timber.e("No data have been received from server.")
            return null
        }
        val columns = data.columns
        val rows = data.rows
        if (columns == null || rows == null) {
            Timber.e("The received data is corrupted.")
            return null
        }
        var jidColumn: ReportedData.Column? = null
        for (tmpCollumn in columns) {
            if (tmpCollumn.type == FormField.Type.jid_single) {
                jidColumn = tmpCollumn
                break
            }
        }
        if (jidColumn == null) {
            Timber.e("No jid collumn provided by the server.")
            return null
        }
        val result: MutableList<CharSequence?> = ArrayList()
        for (row in rows) {
            result.add(row.getValues(jidColumn.variable)[0])
        }
        return result
    }

    /**
     * Adds `UserSearchProviderListener` instance to the list of listeners.
     *
     * @param l the listener to be added
     */
    override fun addUserSearchProviderListener(l: UserSearchProviderListener?) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
    }

    /**
     * Removes `UserSearchProviderListener` instance from the list of listeners.
     *
     * @param l the listener to be removed
     */
    override fun removeUserSearchProviderListener(l: UserSearchProviderListener?) {
        synchronized(listeners) { listeners.remove(l) }
    }

    /**
     * Returns `true` if the user search service is enabled.
     *
     * @return `true` if the user search service is enabled.
     */
    override fun isEnabled(): Boolean {
        return userSearchEnabled
    }

    companion object {
        /**
         * The property name of the user search service name.
         */
        private const val USER_SEARCH_SERVICE_NAME = "USER_SEARCH_SERVICE_NAME"
    }
}