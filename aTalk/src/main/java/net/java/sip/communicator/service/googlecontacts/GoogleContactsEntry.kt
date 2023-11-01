/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts

/**
 * Entry of Google Contacts directory.
 *
 * @author Sebastien Vincent
 */
interface GoogleContactsEntry {
    /**
     * The supported IM protocol
     */
    enum class IMProtocol {
        /**
         * Google Talk protocol.
         */
        GOOGLETALK,

        /**
         * AIM protocol.
         */
        AIM,

        /**
         * ICQ protocol.
         */
        ICQ,

        /**
         * Jabber protocol.
         */
        JABBER,

        /**
         * Skype protocol.
         */
        SKYPE,

        /**
         * Other protocol (i.e. not supported).
         */
        OTHER
    }

    /**
     * Get the full name.
     *
     * @return full name
     */
    fun getFullName(): String?

    /**
     * Get the family name.
     *
     * @return family name
     */
    fun getFamilyName(): String?

    /**
     * Get the given name.
     *
     * @return given name
     */
    fun getGivenName(): String?

    /**
     * Returns mails.
     *
     * @return mails
     */
    fun getAllMails(): List<String?>?

    /**
     * Adds a home mail address.
     *
     * @param mail the mail address
     */
    fun addHomeMail(mail: String?)

    /**
     * Returns home mail addresses.
     *
     * @return home mail addresses
     */
    fun getHomeMails(): List<String?>?

    /**
     * Adds a work mail address.
     *
     * @param mail the mail address
     */
    fun addWorkMails(mail: String?)

    /**
     * Returns work mail addresses.
     *
     * @return work mail addresses
     */
    fun getWorkMails(): List<String?>?

    /**
     * Returns telephone numbers.
     *
     * @return telephone numbers
     */
    fun getAllPhones(): List<String?>?

    /**
     * Adds a work telephone number.
     *
     * @param telephoneNumber the work telephone number
     */
    fun addWorkPhone(telephoneNumber: String?)

    /**
     * Returns work telephone numbers.
     *
     * @return work telephone numbers
     */
    fun getWorkPhones(): List<String?>?

    /**
     * Adds a mobile telephone numbers.
     *
     * @param telephoneNumber the mobile telephone number
     */
    fun addMobilePhone(telephoneNumber: String?)

    /**
     * Returns mobile telephone numbers.
     *
     * @return mobile telephone numbers
     */
    fun getMobilePhones(): List<String?>?

    /**
     * Adds a home telephone numbers.
     *
     * @param telephoneNumber the home telephone number
     */
    fun addHomePhone(telephoneNumber: String?)

    /**
     * Returns home telephone numbers.
     *
     * @return home telephone numbers
     */
    fun getHomePhones(): List<String?>?

    /**
     * Get the photo full URI.
     *
     * @return the photo URI or null if there isn't
     */
    fun getPhoto(): String?

    /**
     * Returns IM addresses.
     *
     * @return Map where key is IM address and value is IM protocol (MSN, ...)
     */
    fun getIMAddresses(): Map<String?, IMProtocol?>?

    /**
     * Adds an IM address.
     *
     * @param imAddress IM address
     * @param protocol IM protocol
     */
    fun addIMAddress(imAddress: String?, protocol: IMProtocol?)
}