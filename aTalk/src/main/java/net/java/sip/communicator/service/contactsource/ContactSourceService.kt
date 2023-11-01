/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.contactsource

/**
 * The `ContactSourceService` interface is meant to be implemented by modules supporting
 * large lists of contacts and wanting to enable searching from other modules.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ContactSourceService {
    /**
     * Returns the type of this contact source.
     *
     * @return the type of this contact source
     */
    val type: Int

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    val displayName: String?

    /**
     * Creates and returns new `ContactQuery` instance.
     *
     * @param queryString the string to search for
     *
     * @return new `ContactQuery` instance.
     */
    fun createContactQuery(queryString: String): ContactQuery?

    /**
     * Creates and returns new `ContactQuery` instance.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return new `ContactQuery` instance.
     */
    fun createContactQuery(queryString: String, contactCount: Int): ContactQuery?

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    val index: Int

    companion object {

        /**
         * Type of a default source.
         */
        const val DEFAULT_TYPE = 0

        /**
         * Type of a search source. Queried only when searches are performed.
         */
        const val SEARCH_TYPE = 1

        /**
         * Type of a history source. Queries only when history should be shown.
         */
        const val HISTORY_TYPE = 2

        /**
         * Type of a contact list source. Queries to be shown in the contact list.
         */
        const val CONTACT_LIST_TYPE = 3
    }
}