/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import java.util.*

/**
 * The Jabber implementation of the Volatile ContactGroup interface.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class VolatileContactGroupJabberImpl
/**
 * Creates an Jabber group using the specified group name
 *
 * @param groupName String groupName
 * @param ssclCallback a callback to the server stored contact list we're creating.
 */ internal constructor(
        /**
         * This contact group name
         */
        private val contactGroupName: String, ssclCallback: ServerStoredContactListJabberImpl?) : ContactGroupJabberImpl(null, Collections.emptyIterator(), ssclCallback!!, false) {
    /**
     * Returns the name of this group.
     *
     * @return a String containing the name of this group.
     */
    override fun getGroupName(): String? {
        return contactGroupName
    }

    /**
     * Returns a string representation of this group, in the form
     * JabberGroup.GroupName[size]{buddy1.toString(), buddy2.toString(), ...}.
     *
     * @return a String representation of the object.
     */
    override fun toString(): String {
        val buff = StringBuilder("VolatileJabberGroup.")
        buff.append(getGroupName())
        buff.append(", childContacts=" + countContacts() + ":[")
        val contacts = contacts()
        while (contacts!!.hasNext()) {
            val contact = contacts.next()
            buff.append(contact.toString())
            if (contacts.hasNext()) buff.append(", ")
        }
        return buff.append("]").toString()
    }

    /**
     * Determines whether or not this contact group is to be stored in local DB. Non persistent
     * contact groups exist for the sole purpose of containing non persistent contacts.
     *
     * @return true if the contact group is persistent and false otherwise.
     */
    override fun isPersistent(): Boolean {
        return true
    }
}