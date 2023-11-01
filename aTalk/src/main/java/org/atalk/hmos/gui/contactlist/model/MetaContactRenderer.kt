/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.contactlist.model

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations.SubscriptionStatus
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.util.StatusUtil.getContactStatusIcon
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.util.AndroidImageUtil.drawableFromBytes
import org.atalk.hmos.gui.util.AndroidImageUtil.roundedDrawableFromBytes
import org.atalk.hmos.gui.util.DrawableCache
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.jxmpp.jid.DomainBareJid

/**
 * Class used to obtain UI specific data for `MetaContact` instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MetaContactRenderer : UIContactRenderer {
    override fun isSelected(contactImpl: Any?): Boolean {
        return MetaContactListAdapter.isContactSelected(contactImpl as MetaContact?)
    }

    override fun getDisplayName(contactImpl: Any): String? {
        return (contactImpl as MetaContact).getDisplayName()
    }

    override fun getStatusMessage(contactImpl: Any): String {
        val metaContact = contactImpl as MetaContact
        val displayDetails = getDisplayDetails(metaContact)
        return displayDetails ?: ""
    }

    override fun isDisplayBold(contactImpl: Any?): Boolean {
        return ChatSessionManager.getActiveChat(contactImpl as MetaContact?) != null
    }

    override fun getAvatarImage(contactImpl: Any): Drawable? {
        return getAvatarDrawable(contactImpl as MetaContact)
    }

    override fun getStatusImage(contactImpl: Any): Drawable? {
        return getStatusDrawable(contactImpl as MetaContact)
    }

    override fun isShowVideoCallBtn(contactImpl: Any): Boolean {
        // Disable video call option if there is no camera support on the device
        return isShowButton(contactImpl as MetaContact, OperationSetVideoTelephony::class.java) && AndroidCamera.cameras.isNotEmpty()
    }

    override fun isShowCallBtn(contactImpl: Any?): Boolean {
        // Handle only if contactImpl instanceof MetaContact; DomainJid always show call button option
        if (contactImpl is MetaContact) {
            var isDomainJid = false
            if (contactImpl.getDefaultContact() != null) isDomainJid = contactImpl.getDefaultContact()!!.contactJid is DomainBareJid
            return isDomainJid || isShowButton(contactImpl, OperationSetBasicTelephony::class.java)
        }
        return false
    }

    override fun isShowFileSendBtn(contactImpl: Any): Boolean {
        return isShowButton(contactImpl as MetaContact, OperationSetFileTransfer::class.java)
    }

    private fun isShowButton(metaContact: MetaContact, opSetClass: Class<out OperationSet?>): Boolean {
        return metaContact.getOpSetSupportedContact(opSetClass) != null
    }

    override fun getDefaultAddress(contactImpl: Any): String {
        return (contactImpl as MetaContact).getDefaultContact()!!.address
    }

    companion object {
        /**
         * Returns the display details for the underlying `MetaContact`.
         *
         * @param metaContact the `MetaContact`, which details we're looking for
         * @return the display details for the underlying `MetaContact`
         */
        private fun getDisplayDetails(metaContact: MetaContact): String? {
            var subscribed = false
            var displayDetails: String? = null
            var subscriptionDetails: String? = null
            val protoContacts = metaContact.getContacts()
            while (protoContacts.hasNext()) {
                val protoContact = protoContacts.next()
                val authOpSet = protoContact!!.protocolProvider.getOperationSet(OperationSetExtendedAuthorizations::class.java)
                val status = authOpSet!!.getSubscriptionStatus(protoContact)
                if (SubscriptionStatus.Subscribed != status) {
                    if (SubscriptionStatus.SubscriptionPending == status) subscriptionDetails = aTalkApp.getResString(R.string.service_gui_WAITING_AUTHORIZATION) else if (SubscriptionStatus.NotSubscribed == status) subscriptionDetails = aTalkApp.getResString(R.string.service_gui_NOT_AUTHORIZED)
                } else if (StringUtils.isNotEmpty(protoContact.statusMessage)) {
                    displayDetails = protoContact.statusMessage
                    subscribed = true
                    break
                } else {
                    subscribed = true
                }
            }
            if (StringUtils.isEmpty(displayDetails) && !subscribed
                    && StringUtils.isNotEmpty(subscriptionDetails)) displayDetails = subscriptionDetails
            return displayDetails
        }

        /**
         * Returns the avatar `Drawable` for the given `MetaContact`.
         *
         * @param metaContact the `MetaContact`, which status drawable we're looking for
         * @return a `BitmapDrawable` object representing the status of the given `MetaContact`
         */
        fun getAvatarDrawable(metaContact: MetaContact): BitmapDrawable? {
            return getCachedAvatarFromBytes(metaContact.getAvatar())
        }

        /**
         * Returns avatar `BitmapDrawable` with rounded corners. Bitmap will be cached in app global drawable cache.
         *
         * @param avatar raw avatar image data.
         * @return avatar `BitmapDrawable` with rounded corners
         */
        fun getCachedAvatarFromBytes(avatar: ByteArray?): BitmapDrawable? {
            if (avatar == null) return null
            val bmpKey = avatar.hashCode().toString()
            val cache = aTalkApp.imageCache
            var avatarImage = cache.getBitmapFromMemCache(bmpKey)
            if (avatarImage == null) {
                val roundedAvatar = roundedDrawableFromBytes(avatar)
                if (roundedAvatar != null) {
                    avatarImage = roundedAvatar
                    cache.cacheImage(bmpKey, avatarImage)
                }
            }
            return avatarImage
        }

        /**
         * Returns the status `Drawable` for the given `MetaContact`.
         *
         * @param metaContact the `MetaContact`, which status drawable we're looking for
         * @return a `Drawable` object representing the status of the given `MetaContact`
         */
        fun getStatusDrawable(metaContact: MetaContact): Drawable? {
            val statusImage = getStatusImage(metaContact)
            return if (statusImage != null && statusImage.isNotEmpty()) drawableFromBytes(statusImage) else null
        }

        /**
         * Returns the array of bytes representing the status image of the given `MetaContact`.
         *
         * @return the array of bytes representing the status image of the given `MetaContact`
         */
        private fun getStatusImage(metaContact: MetaContact): ByteArray? {
            var status: PresenceStatus? = null
            val contactsIter = metaContact.getContacts()
            while (contactsIter.hasNext()) {
                val protoContact = contactsIter.next()
                val contactStatus = protoContact!!.presenceStatus
                status = if (status == null) contactStatus else if (contactStatus > status) contactStatus else status
            }
            return getContactStatusIcon(status)
        }
    }
}