/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo.DetailsResponseListener
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
import timber.log.Timber
import java.util.*

/**
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class OperationSetServerStoredContactInfoJabberImpl(
        /**
         * Returns the info retriever.
         *
         * @return the info retriever.
         */
        val infoRetriever: InfoRetriever) : OperationSetServerStoredContactInfo {

    /**
     * If we got several listeners for the same contact lets retrieve once but deliver result to all.
     */
    private val listenersForDetails = Hashtable<String?, MutableList<DetailsResponseListener?>>()

    /**
     * returns the user details from the specified class or its descendants the class is one from
     * the net.java.sip.communicator.service.protocol.ServerStoredDetails or implemented one in the
     * operation set for the user info
     *
     * @param contact Contact
     * @param detailClass Class
     * @return Iterator
     */
    override fun <T : GenericDetail?> getDetailsAndDescendants(contact: Contact?, detailClass: Class<T>?): Iterator<T> {
        if (isPrivateMessagingContact(contact) || contact !is ContactJabberImpl) {
            return LinkedList<T>().iterator()
        }

        val details: List<GenericDetail?>? = infoRetriever.getUserDetails(contact.contactJid!!.asBareJid())
        val result: MutableList<T> = LinkedList()
        if (details == null) return result.iterator()

        for (item in details) {
            if (detailClass!!.isInstance(item)) {
                val t = item as T?
                result.add(t!!)
            }
        }
        return result.iterator()
    }

    /**
     * returns the user details from the specified class exactly that class not its descendants
     *
     * @param contact Contact
     * @param detailClass Class
     * @return Iterator
     */
    override fun getDetails(contact: Contact?, detailClass: Class<out GenericDetail?>?): Iterator<GenericDetail?> {
        if (isPrivateMessagingContact(contact) || contact !is ContactJabberImpl) return LinkedList<GenericDetail>().iterator()
        val details: List<GenericDetail?>? = infoRetriever.getUserDetails(contact.contactJid!!.asBareJid())
        val result: MutableList<GenericDetail?> = LinkedList()
        if (details == null) return result.iterator()
        for (item in details) if (detailClass == item!!.javaClass) result.add(item)
        return result.iterator()
    }

    /**
     * request the full info for the given uin waits and return this details
     *
     * @param contact The requester Contact
     * @return Iterator
     */
    override fun getAllDetailsForContact(contact: Contact?): Iterator<GenericDetail?>? {
        if (isPrivateMessagingContact(contact) || contact !is ContactJabberImpl) return Collections.emptyIterator()
        val details: List<GenericDetail?>? = infoRetriever.getUserDetails(contact.contactJid!!.asBareJid())
        return if (details == null) Collections.emptyIterator() else LinkedList(details).iterator()
    }

    /**
     * Requests all details for the specified contact. Always fetch online info: do not
     * use cached info as any contact vCard changes is not event triggered; user must logout and
     * login to retrieve any new update from an online contact
     *
     * @param contact the specified contact or account
     * @return Iterator over all details existing for the specified contact.
     */
    override fun requestAllDetailsForContact(contact: Contact?, listener: DetailsResponseListener?): Iterator<GenericDetail?>? {
        if (contact !is ContactJabberImpl) {
            return null
        }
        synchronized(listenersForDetails) {
            var ls = listenersForDetails[contact.address]
            var isFirst = false
            if (ls == null) {
                ls = ArrayList()
                isFirst = true
                listenersForDetails[contact.address] = ls
            }
            if (!ls.contains(listener)) ls.add(listener)

            // there is already scheduled retrieve, will deliver at listener.
            if (!isFirst) return null
        }

        Thread({
            val result: List<GenericDetail?>? = infoRetriever.retrieveDetails(contact.contactJid!!.asBareJid())
            var listeners: List<DetailsResponseListener?>?
            synchronized(listenersForDetails) { listeners = listenersForDetails.remove(contact.address) }
            if (listeners != null && result != null) {
                for (l in listeners!!) {
                    try {
                        l!!.detailsRetrieved(result.iterator())
                    } catch (t: Throwable) {
                        Timber.e(t, "Error delivering for retrieved details")
                    }
                }
            }
        }, javaClass.name + ".RetrieveDetails").start()

        // return null as there is no cache and we will try to retrieve
        return null
    }

    /**
     * Checks whether a contact is a private messaging contact for chat rooms.
     *
     * @param contact the contact to check.
     * @return `true` if contact is private messaging contact for chat room.
     */
    private fun isPrivateMessagingContact(contact: Contact?): Boolean {
        return if (contact is VolatileContactJabberImpl) contact.isPrivateMessagingContact else false
    }
}