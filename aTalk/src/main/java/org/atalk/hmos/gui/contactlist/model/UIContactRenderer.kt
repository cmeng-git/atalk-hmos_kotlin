/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.graphics.drawable.Drawable

/**
 * Interface used to obtain data required to display contacts. Implementing classes can expect to receive their
 * implementation specific objects in calls to any method of this interface.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
interface UIContactRenderer {
    /**
     * Return `true` if given contact is considered to be currently selected.
     *
     * @param contactImpl contact instance.
     * @return `true` if given contact is considered to be currently selected.
     */
    fun isSelected(contactImpl: Any?): Boolean

    /**
     * Returns contact display name.
     *
     * @param contactImpl contact instance.
     * @return contact display name.
     */
    fun getDisplayName(contactImpl: Any): String?

    /**
     * Returns contact status message.
     *
     * @param contactImpl contact instance.
     * @return contact status message.
     */
    fun getStatusMessage(contactImpl: Any): String?

    /**
     * Returns `true` if given contact name should be displayed in bold.
     *
     * @param contactImpl contact instance.
     * @return `true` if given contact name should be displayed in bold.
     */
    fun isDisplayBold(contactImpl: Any?): Boolean

    /**
     * Returns contact avatar image.
     *
     * @param contactImpl contact instance.
     * @return contact avatar image.
     */
    fun getAvatarImage(contactImpl: Any): Drawable?

    /**
     * Returns contact status image.
     *
     * @param contactImpl contact instance.
     * @return contact status image.
     */
    fun getStatusImage(contactImpl: Any): Drawable?

    /**
     * Returns `true` if video call button should be displayed for given contact. That is if contact has valid
     * default address that can be used to make video calls.
     *
     * @param contactImpl contact instance.
     * @return `true` if video call button should be displayed for given contact.
     */
    fun isShowVideoCallBtn(contactImpl: Any): Boolean

    /**
     * Returns `true` if call button should be displayed next to the contact. That means that it will returns
     * valid default address that can be used to make audio calls.
     *
     * @param contactImpl contact instance.
     * @return `true` if call button should be displayed next to the contact.
     */
    fun isShowCallBtn(contactImpl: Any?): Boolean
    fun isShowFileSendBtn(contactImpl: Any): Boolean

    /**
     * Returns default contact address that can be used to establish an outgoing connection.
     *
     * @param contactImpl contact instance.
     * @return default contact address that can be used to establish an outgoing connection.
     */
    fun getDefaultAddress(contactImpl: Any): String?
}