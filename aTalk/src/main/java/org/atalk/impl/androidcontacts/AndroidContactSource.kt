/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts

import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.ExtendedContactSourceService
import net.java.sip.communicator.service.contactsource.PrefixedContactSourceService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import java.util.regex.Pattern

/**
 * Android contact source implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidContactSource : ExtendedContactSourceService, PrefixedContactSourceService {
    /**
     * Queries this search source for the given `searchPattern`.
     *
     * @param queryPattern the pattern to search for
     * @return the created query
     */
    override fun createContactQuery(queryPattern: Pattern?): ContactQuery {
        return AndroidContactQuery(this, "%" + queryPattern.toString() + "%")
    }

    /**
     * Queries this search source for the given `query`.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery {
        return createContactQuery(Pattern.compile(queryString, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
    }

    /**
     * Queries this search source for the given `query`.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery {
        return createContactQuery(Pattern.compile(queryString, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
    }// return AddrBookActivator.getConfigService().getString(OUTLOOK_ADDR_BOOK_PREFIX);

    /**
     * Returns the global phone number prefix to be used when calling contacts from this contact source.
     *
     * @return the global phone number prefix
     */
    override val phoneNumberPrefix: String?
        get() = null

    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    override val type: Int
        get() = ContactSourceService.SEARCH_TYPE

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String
        get() = aTalkApp.getResString(R.string.service_gui_PHONEBOOK)

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = -1
}