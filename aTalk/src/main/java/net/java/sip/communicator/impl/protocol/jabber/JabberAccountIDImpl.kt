/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.StunServerDescriptor
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID

/**
 * The Jabber implementation of a sip-communicator AccountID
 *
 * @author Damian Minkov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class JabberAccountIDImpl
/**
 * Creates an account id from the specified id and account properties.
 *
 * @param id
 * the id identifying this account
 * @param accountProperties
 * any other properties necessary for the account.
 */
internal constructor(id: String, accountProperties: MutableMap<String, String?>) : JabberAccountID(id, accountProperties) {
    // If we don't find a stun server with the given index, it means that they're no
    // more servers left in the table so we've nothing more to do here.
    val stunServers: List<StunServerDescriptor>
        /**
         * Returns the list of STUN servers that this account is currently configured to use.
         */
        get() {
            val accountProperties = accountProperties
            val serList = ArrayList<StunServerDescriptor>()
            for (i in 0 until StunServerDescriptor.MAX_STUN_SERVER_COUNT) {
                val stunServer = StunServerDescriptor.loadDescriptor(
                        accountProperties, ProtocolProviderFactory.STUN_PREFIX + i) ?: break

                // If we don't find a stun server with the given index, it means that they're no
                // more servers left in the table so we've nothing more to do here.
                val password = this.loadStunPassword(ProtocolProviderFactory.STUN_PREFIX + i)
                if (password != null) stunServer.setPassword(password)
                serList.add(stunServer)
            }
            return serList
        }

    /**
     * Load password for this STUN descriptor.
     *
     * @param namePrefix name prefix
     * @return password or null if empty
     */
    private fun loadStunPassword(namePrefix: String): String? {
        val className = ProtocolProviderServiceJabberImpl::class.java.name
        val packageSourceName = className.substring(0, className.lastIndexOf('.'))
        val accountPrefix = ProtocolProviderFactory.findAccountPrefix(
                JabberActivator.bundleContext, this, packageSourceName)
        val credentialsService = JabberActivator.credentialsStorageService

        val password = try {
            credentialsService!!.loadPassword("$accountPrefix.$namePrefix")
        } catch (e: Exception) {
            return null
        }
        return password
    }
}