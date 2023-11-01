/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.AvatarEvent
import net.java.sip.communicator.service.protocol.event.AvatarListener
import timber.log.Timber

/**
 * Represents a default implementation of [OperationSetAvatar] in order to make it easier for
 * implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Damien Roth
 * @author Eng Chong Meng
 */
abstract class AbstractOperationSetAvatar<T : ProtocolProviderService?> protected constructor(
        /**
         * The provider that created us.
         */
        private val parentProvider: T,
        private val accountInfoOpSet: OperationSetServerStoredAccountInfo,
        /**
         * The maximum avatar width. Zero mean no maximum
         */
        private val maxWidth: Int,
        /**
         * The maximum avatar height. Zero mean no maximum
         */
        private val maxHeight: Int,
        /**
         * The maximum avatar size. Zero mean no maximum
         */
        private val maxSize: Int) : OperationSetAvatar {
    /**
     * The list of listeners interested in `AvatarEvent`s.
     */
    private val avatarListeners = ArrayList<AvatarListener>()

    override fun getMaxWidth(): Int {
        return maxWidth
    }

    override fun getMaxHeight(): Int {
        return maxHeight
    }

    override fun getMaxSize(): Int {
        return maxSize
    }

    override fun getAvatar(): ByteArray? {
        return AccountInfoUtils.getImage(accountInfoOpSet)
    }

    override fun setAvatar(avatar: ByteArray) {
        var oldDetail: ServerStoredDetails.ImageDetail? = null
        val newDetail = ServerStoredDetails.ImageDetail("avatar", avatar)
        val imageDetails = accountInfoOpSet.getDetails(ServerStoredDetails.ImageDetail::class.java)
        if (imageDetails!!.hasNext()) {
            oldDetail = imageDetails.next() as ServerStoredDetails.ImageDetail
        }
        try {
            if (oldDetail == null) accountInfoOpSet.addDetail(newDetail) else accountInfoOpSet.replaceDetail(oldDetail, newDetail)
            accountInfoOpSet.save()
        } catch (e: OperationFailedException) {
            Timber.w(e, "Unable to set new avatar")
        }
        fireAvatarChanged(avatar)
    }

    override fun addAvatarListener(listener: AvatarListener) {
        synchronized(avatarListeners) { if (!avatarListeners.contains(listener)) avatarListeners.add(listener) }
    }

    override fun removeAvatarListener(listener: AvatarListener) {
        synchronized(avatarListeners) { if (avatarListeners.contains(listener)) avatarListeners.remove(listener) }
    }

    /**
     * Notifies all registered listeners of the new event.
     *
     * @param newAvatar the new avatar
     */
    private fun fireAvatarChanged(newAvatar: ByteArray) {
        var listeners: Collection<AvatarListener>
        synchronized(avatarListeners) { listeners = ArrayList(avatarListeners) }
        if (!listeners.isEmpty()) {
            val event = AvatarEvent(this, parentProvider as ProtocolProviderService, newAvatar)
            for (l in listeners) l.avatarChanged(event)
        }
    }
}