/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.sip

import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil
import net.java.sip.communicator.service.protocol.ProtocolNames
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration
import net.java.sip.communicator.util.ServiceUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.service.neomedia.MediaService
import org.osgi.framework.BundleContext
import java.io.Serializable

/**
 * The `SIPAccountRegistration` is used to store all user input data through the
 * `SIPAccountRegistrationWizard`.
 *
 * @author Yana Stamcheva
 * @author Grigorii Balutsel
 * @author Boris Grozev
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class SIPAccountRegistration
/**
 * Initializes a new SIPAccountRegistration.
 */
    : SipAccountID(null, HashMap(), ProtocolNames.SIP), Serializable {
    var defaultDomain: String? = null

    /**
     * Indicates if the password should be remembered.
     */
    var isRememberPassword = true

    /**
     * The encodings registration object.
     */
    val encodingsRegistration = EncodingsRegistrationUtil()

    /**
     * The security registration object.
     */
    private val securityAccountRegistration = object : SecurityAccountRegistration() {
        /**
         * Sets the method used for RTP/SAVP indication.
         */
        override fun setSavpOption(savpOption: Int) {
            putAccountProperty(ProtocolProviderFactory.SAVP_OPTION, savpOption.toString())
        }

        /**
         * Returns the method used for RTP/SAVP indication.
         *
         * @return the method used for RTP/SAVP indication.
         */
        override fun getSavpOption(): Int {
            val savpOption = getAccountPropertyString(ProtocolProviderFactory.SAVP_OPTION)
            return savpOption!!.toInt()
        }
    }

    /**
     * Returns security registration object holding security configuration.
     *
     * @return `SecurityAccountRegistration` object holding security configuration.
     */
    fun getSecurityRegistration(): SecurityAccountRegistration {
        return securityAccountRegistration
    }

    /**
     * Loads configuration properties from given `accountID`.
     *
     * @param accountID the account identifier that will be used.
     * @param bundleContext the OSGI bundle context required for some operations.
     */
    fun loadAccount(accountID: AccountID, password_: String?, bundleContext: BundleContext?) {
        mergeProperties(accountID.accountProperties, mAccountProperties)
        val userID = when (serverAddress) {
            null -> accountID.mUserID
            else -> accountID.getAccountPropertyString(ProtocolProviderFactory.USER_ID)
        }

        setUserID(userID!!)
        password = password_
        isRememberPassword = password_ != null

        // Password must be copied from credentials storage
        setClistOptionPassword(accountID.getAccountPropertyString(OPT_CLIST_PASSWORD))
        securityAccountRegistration.loadAccount(accountID)
        encodingsRegistration.loadAccount(accountID, ServiceUtils.getService(bundleContext, MediaService::class.java)!!)
    }

    /**
     * Stores configuration properties held by this object into given `accountProperties`
     * map.
     *
     * @param userName_ the user name that will be used.
     * @param passwd the password that will be used.
     * @param protocolIconPath the path to the protocol icon is used
     * @param accountIconPath the path to the account icon if used
     * @param isModification flag indication if it's modification process(has impact on some properties).
     * @param accountProperties the map that will hold the configuration.
     */
    fun storeProperties(userName_: String, passwd: String?, protocolIconPath: String?,
            accountIconPath: String?, isModification: Boolean, accountProperties: MutableMap<String, String?>) {
        var userName = userName_
        password = if (isRememberPassword) passwd else null

        var serverAddress: String? = null
        if (this.serverAddress != null) serverAddress = this.serverAddress

        val serverFromUsername = getServerFromUserName(userName)
        if (serverFromUsername == null && defaultDomain != null) {
            // we have only a username and we want to add a default domain
            userName = "$userName@$defaultDomain"
            if (serverAddress == null) serverAddress = defaultDomain
        } else if (serverAddress == null && serverFromUsername != null) {
            serverAddress = serverFromUsername
        }

        if (serverAddress != null) {
            accountProperties[ProtocolProviderFactory.SERVER_ADDRESS] = serverAddress
            if (!userName.contains(serverAddress)) accountProperties[ProtocolProviderFactory.IS_SERVER_OVERRIDDEN] = java.lang.Boolean.toString(true)
        }

        if (isProxyAutoConfigure()) {
            removeAccountProperty(ProtocolProviderFactory.PROXY_ADDRESS)
            removeAccountProperty(ProtocolProviderFactory.PROXY_PORT)
            removeAccountProperty(ProtocolProviderFactory.PREFERRED_TRANSPORT)
        }

        // when we are creating register-less account make sure that we don't use PA
        if (serverAddress == null) {
            setForceP2PMode(true)
        }
        securityAccountRegistration.storeProperties(mAccountProperties)
        encodingsRegistration.storeProperties(mAccountProperties)

        if (isMessageWaitingIndicationsEnabled()) {
            if (StringUtils.isNotBlank(getVoicemailURI())) accountProperties[ProtocolProviderFactory.VOICEMAIL_URI] = getVoicemailURI() else if (isModification) accountProperties[ProtocolProviderFactory.VOICEMAIL_URI] = ""
            if (StringUtils.isNotBlank(getVoicemailCheckURI())) accountProperties[ProtocolProviderFactory.VOICEMAIL_CHECK_URI] = getVoicemailCheckURI() else if (isModification) accountProperties[ProtocolProviderFactory.VOICEMAIL_CHECK_URI] = ""
            if (isModification) {
                // remove the property as true is by default, and null removes property
                accountProperties[ProtocolProviderFactory.VOICEMAIL_ENABLED] = null
            }
        } else if (isModification) {
            accountProperties[ProtocolProviderFactory.VOICEMAIL_ENABLED] = java.lang.Boolean.FALSE.toString()
        }
        super.storeProperties(protocolIconPath, accountIconPath, accountProperties)
    }
}