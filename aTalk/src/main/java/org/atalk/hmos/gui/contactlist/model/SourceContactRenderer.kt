/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.graphics.drawable.Drawable
import net.java.sip.communicator.service.contactsource.SourceContact

/**
 * Class used to obtain UI specific data for `SourceContact` instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class SourceContactRenderer private constructor() : UIContactRenderer {
    override fun isSelected(contactImpl: Any?): Boolean {
        return false
    }

    override fun getDisplayName(contactImpl: Any): String? {
        val contact = contactImpl as SourceContact
        return contact.displayName
    }

    override fun getStatusMessage(contactImpl: Any): String? {
        val contact = contactImpl as SourceContact
        return contact.displayDetails
    }

    override fun isDisplayBold(contactImpl: Any?): Boolean {
        return false
    }

    override fun getAvatarImage(contactImpl: Any): Drawable? {
        val contact = contactImpl as SourceContact
        return MetaContactRenderer.getCachedAvatarFromBytes(contact.image)
    }

    override fun getStatusImage(contactImpl: Any): Drawable? {
        return null
    }

    override fun isShowVideoCallBtn(contactImpl: Any): Boolean {
        return false
    }

    override fun isShowCallBtn(contactImpl: Any?): Boolean {
        return true
    }

    override fun isShowFileSendBtn(contactImpl: Any): Boolean {
        return true
    }

    override fun getDefaultAddress(contactImpl: Any): String? {
        return (contactImpl as SourceContact).contactAddress
    }

    companion object {
        /**
         * Class is stateless and does not take any parameters so we can use single instance for now.
         */
        val instance = SourceContactRenderer()
    }
}