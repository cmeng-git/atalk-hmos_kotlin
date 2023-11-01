/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * This interface is addition to the persistence presence operation set, meant to provide per group
 * permissions for modification of the contacts and groups. Can make the contact list read only or
 * only some groups in it.
 *
 * @author Damian Minkov
 */
interface OperationSetPersistentPresencePermissions : OperationSet {
    /**
     * Is the whole contact list for the current provider readonly.
     *
     * @return `true` if the whole contact list is readonly, otherwise `false`.
     */
    fun isReadOnly(): Boolean

    /**
     * Checks whether the `contact` can be edited, removed, moved. If the parent group is
     * readonly.
     *
     * @param contact
     * the contact to check.
     * @return `true` if the contact is readonly, otherwise `false`.
     */
    fun isReadOnly(contact: Contact?): Boolean

    /**
     * Checks whether the `group` is readonly.
     *
     * @param group
     * the group to check.
     * @return `true` if the group is readonly, otherwise `false`.
     */
    fun isReadOnly(group: ContactGroup?): Boolean
}