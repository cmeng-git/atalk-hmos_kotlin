/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui

import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.contactsource.SourceContact

/**
 * The user interface representation of a contact source.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface UIContactSource {
    /**
     * Returns the UI group for this contact source. There's only one group
     * descriptor per external source.
     *
     * @return the group descriptor
     */
    val uIGroup: UIGroup?

    /**
     * Returns the `UIContact` corresponding to the given
     * `sourceContact`.
     *
     * @param sourceContact the `SourceContact`, for which we search a
     * corresponding `UIContact`
     * @return the `UIContact` corresponding to the given
     * `sourceContact`
     */
    fun createUIContact(sourceContact: SourceContact?): UIContact?

    /**
     * Removes the `UIContact` from the given `sourceContact`.
     * @param sourceContact the `SourceContact`, which corresponding UI
     * contact we would like to remove
     */
    fun removeUIContact(sourceContact: SourceContact?)

    /**
     * Returns the `UIContact` corresponding to the given
     * `SourceContact`.
     * @param sourceContact the `SourceContact`, which corresponding UI
     * contact we're looking for
     * @return the `UIContact` corresponding to the given
     * `MetaContact`
     */
    fun getUIContact(sourceContact: SourceContact?): UIContact?

    /**
     * Returns the corresponding `ContactSourceService`.
     *
     * @return the corresponding `ContactSourceService`
     */
    val contactSourceService: ContactSourceService?

    /**
     * Sets the contact source index.
     *
     * @param contactSourceIndex the contact source index to set
     */
    fun setContactSourceIndex(contactSourceIndex: Int)
}