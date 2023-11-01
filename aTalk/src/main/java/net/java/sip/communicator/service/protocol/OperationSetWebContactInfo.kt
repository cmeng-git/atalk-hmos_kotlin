/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import java.net.URL

/**
 * The operation set is a very simplified version of the server stored info operation sets, allowing
 * protocol providers to implement a quick way of showing user information, by simply returning a
 * URL where the information of a specific user is to be found.
 */
interface OperationSetWebContactInfo : OperationSet {
    /**
     * Returns the URL of a page containing information on `contact`
     *
     * @param contact
     * the `Contact` that we'd like to get information about.
     * @return the URL of a page containing information on the specified contact.
     */
    fun getWebContactInfo(contact: Contact?): URL?

    /**
     * Returns the URL of a page containing information on the contact with the specified
     * `contactAddress`.
     *
     * @param contactAddress
     * the `contactAddress` that we'd like to get information about.
     * @return the URL of a page containing information on the specified contact.
     */
    fun getWebContactInfo(contactAddress: String?): URL?
}