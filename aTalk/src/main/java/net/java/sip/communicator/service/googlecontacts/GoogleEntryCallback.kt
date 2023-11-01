/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts

/**
 * Defines the interface for a callback function which is called by the
 * `GoogleContactsService` when a new `GoogleContactsEntry` has
 * been found during a search.
 */
interface GoogleEntryCallback {
    /**
     * Notifies this `GoogleEntryCallback` when a new
     * `GoogleContactsEntry` has been found.
     *
     * @param entry the `GoogleContactsEntry` found
     */
    fun callback(entry: GoogleContactsEntry?)
}