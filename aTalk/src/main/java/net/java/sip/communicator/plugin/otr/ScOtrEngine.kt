/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.otr4j.OtrPolicy
import net.java.otr4j.session.InstanceTag
import net.java.otr4j.session.Session
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact
import net.java.sip.communicator.service.protocol.Contact
import java.security.PublicKey

/**
 * This interface must be implemented by classes that provide the Off-the-Record
 * functionality.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
interface ScOtrEngine {
    // Proxy methods OtrEngine.
    /**
     * Initializes Smp negotiation.
     *
     * @param contact The contact with whom we want to start the Smp negotiation
     * @param question The question that is asked during the Smp negotiation
     * @param secret The secret answer for the question.
     * @See [https://en.wikipedia.org/wiki/Socialist_Millionaire_Problem](https://en.wikipedia.org/wiki/Socialist_Millionaire_Problem)
     */
    fun initSmp(contact: OtrContact, question: String?, secret: String?)

    /**
     * Responds to a question that is asked during the Smp negotiation process.
     *
     * @param contact The contact for whom we want to respond to a question
     * during the Smp negotiation process.
     * @param receiverTag The instance tag of the intended receiver of the SMP
     * response
     * @param question The question that is asked during the Smp negotiation.
     * @param secret The secret answer for the question.
     * @See [https://en.wikipedia.org/wiki/Socialist_Millionaire_Problem](https://en.wikipedia.org/wiki/Socialist_Millionaire_Problem)
     */
    fun respondSmp(contact: OtrContact, receiverTag: InstanceTag?, question: String?, secret: String?)

    /**
     * Aborts the Smp negotiation process.
     *
     * @param contact The contact with whom we want to abort the
     * Smp negotiation process.
     * @See [https://en.wikipedia.org/wiki/Socialist_Millionaire_Problem](https://en.wikipedia.org/wiki/Socialist_Millionaire_Problem)
     */
    fun abortSmp(contact: OtrContact)

    /**
     * Transforms an outgoing message.
     *
     * @param contact the destination [OtrContact].
     * @param content the original message content.
     * @return the transformed message content.
     */
    fun transformSending(contact: OtrContact?, content: String?): Array<String>?

    /**
     * Transforms an incoming message.
     *
     * @param contact the source [OtrContact].
     * @param content the original message content.
     * @return the transformed message content.
     */
    fun transformReceiving(contact: OtrContact?, content: String?): String?

    /**
     * Starts the Off-the-Record session for the given [OtrContact], if it's
     * not already started.
     *
     * @param contact the [OtrContact] with whom we want to start an OTR
     * session.
     */
    fun startSession(contact: OtrContact)

    /**
     * Ends the Off-the-Record session for the given [OtrContact], if it is
     * not already started.
     *
     * @param contact the [OtrContact] with whom we want to end the OTR
     * session.
     */
    fun endSession(contact: OtrContact)

    /**
     * Refreshes the Off-the-Record session for the given [OtrContact]. If
     * the session does not exist, a new session is established.
     *
     * @param contact the [OtrContact] with whom we want to refresh the OTR
     * session.
     */
    fun refreshSession(contact: OtrContact?)

    /**
     * Get the outgoing OTRv3 `Session`. This could be the 'master'
     * session as well as a 'slave' session.
     * This method could also be safely used for OTRv2 sessions. In the case of
     * version 2 the master session itself will always be returned.
     *
     * @param contact the [OtrContact] for whom we want to get the
     * outgoing OTR session.
     * @return the `Session` that is currently transforming outgoing all
     * messages.
     */
    fun getOutgoingSession(contact: OtrContact?): Session?

    /**
     * Some IM networks always relay all messages to all sessions of a client
     * who is logged in multiple times. OTR version 3 deals with this problem
     * with introducing instance tags.
     * [
 * https://otr.cypherpunks.ca/Protocol-v3-4.0.0.html](https://otr.cypherpunks.ca/Protocol-v3-4.0.0.html)
     *
     *
     * Returns a list containing all instances of a session. The 'master'
     * session is always first in the list.
     *
     * @param contact the [OtrContact] for whom we want to get the instances
     * @return A list of all instances of the session for the specified contact.
     */
    fun getSessionInstances(contact: OtrContact?): List<Session>

    /**
     * Some IM networks always relay all messages to all sessions of a client
     * who is logged in multiple times. OTR version 3 deals with this problem
     * with introducing instance tags.
     * [
 * https://otr.cypherpunks.ca/Protocol-v3-4.0.0.html](https://otr.cypherpunks.ca/Protocol-v3-4.0.0.html)
     *
     *
     * When the client wishes to start sending OTRv3 encrypted messages to a
     * specific session of his buddy who is logged in multiple times, he can set
     * the outgoing instance of his buddy by specifying his `InstanceTag`.
     *
     * @param contact the [OtrContact] to whom we want to set the outgoing
     * instance tag.
     * @param tag the outgoing [InstanceTag]
     * @return true if an outgoing session with such [InstanceTag] exists
     * . Otherwise false
     */
    fun setOutgoingSession(contact: OtrContact?, tag: InstanceTag?): Boolean

    /**
     * Gets the [ScSessionStatus] for the given [OtrContact].
     *
     * @param contact the [OtrContact] whose [ScSessionStatus] we are
     * interested in.
     * @return the [ScSessionStatus].
     */
    fun getSessionStatus(contact: OtrContact?): ScSessionStatus?
    // New Methods (Misc)
    /**
     * Gets weather the passed in messageUID is injected by the engine or not.
     * If it is injected, it shouldn't be re-transformed.
     *
     * @param messageUID the messageUID which is to be determined whether it is
     * injected by the engine or not
     * @return `true` if the passed in messageUID is injected by the
     * engine; `false`, otherwise
     */
    fun isMessageUIDInjected(messageUID: String): Boolean

    /**
     * Registers an [ScOtrEngineListener].
     *
     * @param listener the [ScOtrEngineListener] to register.
     */
    fun addListener(listener: ScOtrEngineListener)

    /**
     * Unregisters an [ScOtrEngineListener].
     *
     * @param listener the [ScOtrEngineListener] to unregister.
     */
    fun removeListener(listener: ScOtrEngineListener)
    fun getRemotePublicKey(otrContact: OtrContact?): PublicKey?
    // New Methods (Policy management)
    /**
     * Gets the global [OtrPolicy].
     *
     * @return the global [OtrPolicy]
     */
    /**
     * Sets the global policy.
     *
     * @param policy the global policy
     */
    var globalPolicy: OtrPolicy?

    /**
     * Gets a [Contact] specific policy.
     *
     * @param contact the [Contact] whose policy we want.
     * @return The [Contact] specific OTR policy. If the specified
     * [Contact] has no policy, the global policy is returned.
     */
    fun getContactPolicy(contact: Contact): OtrPolicy

    /**
     * Sets the contact specific policy
     *
     * @param contact the [Contact] whose policy we want to set
     * @param policy the [OtrPolicy]
     */
    fun setContactPolicy(contact: Contact, policy: OtrPolicy?)

    /**
     * Launches the help page.
     */
    fun launchHelp()
}