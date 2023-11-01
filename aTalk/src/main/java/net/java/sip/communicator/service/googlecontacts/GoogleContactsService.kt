/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts

/**
 * Google Contacts service.
 *
 * @author Sebastien Vincent
 */
interface GoogleContactsService {
    /**
     * Perform a search for a contact using regular expression.
     *
     * @param cnx `GoogleContactsConnection` to perform the query
     * @param query Google query
     * @param count maximum number of matched contacts
     * @param callback object that will be notified for each new
     * `GoogleContactsEntry` found
     * @return list of `GoogleContactsEntry`
     */
    fun searchContact(cnx: GoogleContactsConnection?,
            query: GoogleQuery?, count: Int, callback: GoogleEntryCallback?): List<GoogleContactsEntry?>?

    /**
     * Get a `GoogleContactsConnection`.
     *
     * @param login login to connect to the service
     * @param password password to connect to the service
     * @return `GoogleContactsConnection`.
     */
    fun getConnection(login: String?,
            password: String?): GoogleContactsConnection?

    /**
     * Get the full contacts list.
     *
     * @return list of `GoogleContactsEntry`
     */
    fun getContacts(): List<GoogleContactsEntry?>?

    /**
     * Add a contact source service with the specified
     * `GoogleContactsConnection`.
     *
     * @param login login
     * @param password password
     */
    fun addContactSource(login: String?, password: String?)

    /**
     * Add a contact source service with the specified
     * `GoogleContactsConnection`.
     *
     * @param cnx `GoogleContactsConnection`
     * @param googleTalk if the contact source has been created as GoogleTalk
     * account or via external Google Contacts
     */
    fun addContactSource(cnx: GoogleContactsConnection?,
            googleTalk: Boolean)

    /**
     * Remove a contact source service with the specified
     * `GoogleContactsConnection`.
     *
     * @param cnx `GoogleContactsConnection`.
     */
    fun removeContactSource(cnx: GoogleContactsConnection?)

    /**
     * Remove a contact source service with the specified
     * `GoogleContactsConnection`.
     *
     * @param login login
     */
    fun removeContactSource(login: String?)
}