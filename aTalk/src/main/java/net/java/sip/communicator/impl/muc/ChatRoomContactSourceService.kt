/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactSourceService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * Contact source service for chat rooms.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ChatRoomContactSourceService : ContactSourceService {
    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    override val type: Int
        get() = ContactSourceService.CONTACT_LIST_TYPE

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String
        get() = aTalkApp.getResString(R.string.service_gui_CHAT_ROOMS)

    /**
     * Creates query for the given `queryString`.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery {
        return createContactQuery(queryString, -1)
    }

    /**
     * Creates query for the given `queryString`.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery {
        var iQueryString = queryString
        if (iQueryString == null) iQueryString = ""
        return ChatRoomQuery(iQueryString, this)
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = 1
}