/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetChangePassword
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.iqregister.AccountManager
import org.jxmpp.jid.impl.JidCreate
import timber.log.Timber

/**
 * A jabber implementation of the password change operation set.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class OperationSetChangePasswordJabberImpl
/**
 * Sets the object protocolProvider to the one given.
 *
 * @param protocolProvider the protocolProvider to use.
 */ internal constructor(
        /**
         * The `ProtocolProviderService` whose password we'll change.
         */
        private val protocolProvider: ProtocolProviderServiceJabberImpl) : OperationSetChangePassword {
    /**
     * Changes the jabber account password of protocolProvider to newPass.
     *
     * @param newPass the new password.
     * @throws IllegalStateException if the account is not registered.
     * @throws OperationFailedException if the server does not support password changes.
     */
    @Throws(IllegalStateException::class, OperationFailedException::class)
    override fun changePassword(newPass: String?) {
        val accountManager = AccountManager.getInstance(protocolProvider.connection)
        try {
            try {
                accountManager.changePassword(newPass)
            } catch (e: NoResponseException) {
                e.printStackTrace()
            } catch (e: NotConnectedException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        } catch (e: XMPPException) {
            Timber.i(e, "Tried to change jabber password, but the server does not support inband password changes")
            throw OperationFailedException("In-band password changes not supported",
                    OperationFailedException.NOT_SUPPORTED_OPERATION, e)
        }
    }

    /**
     * Returns true if the server supports password changes. Checks for XEP-0077 (inband
     * registrations) support via disco#info.
     *
     * @return True if the server supports password changes, false otherwise.
     */
    override fun supportsPasswordChange(): Boolean {
        return try {
            val discoverInfo = protocolProvider.discoveryManager!!.discoverInfo(
                    JidCreate.from(protocolProvider.accountID.service))
            discoverInfo!!.containsFeature(ProtocolProviderServiceJabberImpl.URN_REGISTER)
        } catch (e: Exception) {
            Timber.i("Exception occurred while checking InBand registration is are supported. Returning true anyway.")
            /*
             * It makes sense to return true if something goes wrong, because failing later on is
             * not fatal, and registrations are very likely to be supported.
             */
            true
        }
    }
}