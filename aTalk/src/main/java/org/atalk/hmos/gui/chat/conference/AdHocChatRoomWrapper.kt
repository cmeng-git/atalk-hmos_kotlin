/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat.conference

import net.java.sip.communicator.service.protocol.AdHocChatRoom
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * The `AdHocChatRoomWrapper` is the representation of the `AdHocChatRoom` in the GUI. It stores the information for the
 * ad-hoc chat room even when the corresponding protocol provider is not connected.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocChatRoomWrapper
/**
 * Creates a `AdHocChatRoomWrapper` by specifying the protocol provider, the identifier and the name of the ad-hoc chat room.
 *
 * @param parentProvider the protocol provider to which the corresponding ad-hoc chat room belongs
 * @param adHocChatRoomID the identifier of the corresponding ad-hoc chat room
 */
(
        /**
         * Returns the parent protocol provider.
         *
         * @return the parent protocol provider
         */
        val parentProvider: AdHocChatRoomProviderWrapper,
        /**
         * Returns the identifier of the ad-hoc chat room.
         *
         * @return the identifier of the ad-hoc chat room
         */
        // private final String adHocChatRoomName;
        val adHocChatRoomID: String) {

    /**
     * Returns the `AdHocChatRoom` that this wrapper represents.
     *
     * @return the `AdHocChatRoom` that this wrapper represents.
     */
    /**
     * Sets the `AdHocChatRoom` that this wrapper represents.
     *
     * @param adHocChatRoom
     * the ad-hoc chat room
     */
    var adHocChatRoom: AdHocChatRoom? = null

    /**
     * Creates a `ChatRoomWrapper` by specifying the corresponding chat room.
     *
     * @param adHocChatRoom
     * the chat room to which this wrapper corresponds.
     */
    constructor(parentProvider: AdHocChatRoomProviderWrapper,
            adHocChatRoom: AdHocChatRoom) : this(parentProvider, adHocChatRoom.getIdentifier()) {
        this.adHocChatRoom = adHocChatRoom
    }

    /**
     * Returns the ad-hoc chat room name.
     *
     * @return the ad-hoc chat room name
     */
    val adHocChatRoomName: String?
        get() = if (adHocChatRoom != null) adHocChatRoom!!.getName() else adHocChatRoomID

    /**
     * Returns the protocol provider service.
     *
     * @return the protocol provider service
     */
    val protocolProvider: ProtocolProviderService
        get() = parentProvider.protocolProvider
}