/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat.conference

import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.ProtocolIcon
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocChatRoomProviderWrapper
/**
 * Creates an instance of `AdHocChatRoomProviderWrapper` by specifying the protocol provider, corresponding to the ad-hoc multi
 * user chat account.
 *
 * @param protocolProvider
 * protocol provider, corresponding to the ad-hoc multi user chat account.
 */
(
        /**
         * Returns the protocol provider service corresponding to this server wrapper.
         *
         * @return the protocol provider service corresponding to this server wrapper.
         */
        val protocolProvider: ProtocolProviderService) {
    private val chatRoomsOrderedCopy: MutableList<AdHocChatRoomWrapper?> = LinkedList()

    /**
     * Returns the name of this ad-hoc chat room provider.
     *
     * @return the name of this ad-hoc chat room provider.
     */
    val name: String
        get() = protocolProvider.protocolDisplayName
    val icon: ByteArray?
        get() = protocolProvider.protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_64x64)
    val image: ByteArray?
        get() {
            var logoImage: ByteArray? = null
            val protocolIcon = protocolProvider.protocolIcon
            if (protocolIcon.isSizeSupported(ProtocolIcon.ICON_SIZE_64x64)) logoImage = protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_64x64) else if (protocolIcon.isSizeSupported(ProtocolIcon.ICON_SIZE_48x48)) logoImage = protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_48x48)
            return logoImage
        }

    /**
     * Adds the given ad-hoc chat room to this chat room provider.
     *
     * @param adHocChatRoom
     * the ad-hoc chat room to add.
     */
    fun addAdHocChatRoom(adHocChatRoom: AdHocChatRoomWrapper?) {
        chatRoomsOrderedCopy.add(adHocChatRoom)
    }

    /**
     * Removes the given ad-hoc chat room from this provider.
     *
     * @param adHocChatRoom
     * the ad-hoc chat room to remove.
     */
    fun removeChatRoom(adHocChatRoom: AdHocChatRoomWrapper?) {
        chatRoomsOrderedCopy.remove(adHocChatRoom)
    }

    /**
     * Returns `true</code> if the given ad-hoc chat room is contained in this provider, otherwise - returns <code>false`.
     *
     * @param adHocChatRoom
     * the ad-hoc chat room to search for.
     * @return `true</code> if the given ad-hoc chat room is contained in this provider, otherwise - returns <code>false`.
     */
    fun containsAdHocChatRoom(adHocChatRoom: AdHocChatRoomWrapper?): Boolean {
        synchronized(chatRoomsOrderedCopy) { return chatRoomsOrderedCopy.contains(adHocChatRoom) }
    }

    /**
     * Returns the ad-hoc chat room wrapper contained in this provider that corresponds to the given ad-hoc chat room.
     *
     * @param adHocChatRoom
     * the ad-hoc chat room we're looking for.
     * @return the ad-hoc chat room wrapper contained in this provider that corresponds to the given ad-hoc chat room.
     */
    fun findChatRoomWrapperForAdHocChatRoom(adHocChatRoom: AdHocChatRoom): AdHocChatRoomWrapper? {
        // compare ids, cause saved ad-hoc chatrooms don't have AdHocChatRoom
        // object but Id's are the same
        for (chatRoomWrapper in chatRoomsOrderedCopy) {
            if (chatRoomWrapper!!.adHocChatRoomID == adHocChatRoom.getIdentifier()) {
                return chatRoomWrapper
            }
        }
        return null
    }

    /**
     * Returns the number of ad-hoc chat rooms contained in this provider.
     *
     * @return the number of ad-hoc chat rooms contained in this provider.
     */
    fun countAdHocChatRooms(): Int {
        return chatRoomsOrderedCopy.size
    }

    fun getAdHocChatRoom(index: Int): AdHocChatRoomWrapper? {
        return chatRoomsOrderedCopy[index]
    }

    /**
     * Returns the index of the given chat room in this provider.
     *
     * @param chatRoomWrapper
     * the chat room to search for.
     *
     * @return the index of the given chat room in this provider.
     */
    fun indexOf(chatRoomWrapper: AdHocChatRoomWrapper?): Int {
        return chatRoomsOrderedCopy.indexOf(chatRoomWrapper)
    }
}