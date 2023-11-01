/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chatroomslist

import org.atalk.hmos.gui.chat.conference.AdHocChatRoomWrapper
import java.util.*

/**
 * Parent class for gui ad-hoc chat room events indicating addition and removal of ad-hoc chat
 * rooms in the gui ad-hoc chat rooms list.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
class AdHocChatRoomListChangeEvent(source: AdHocChatRoomWrapper?, eventID: Int) : EventObject(source) {
    private var eventID = -1

    /**
     * Creates a new `AdHocChatRoom` event according to the specified parameters.
     *
     * source the `AdHocChatRoom` instance that is added to the AdHocChatRoomsList
     * eventID one of the AD_HOC_CHATROOM_XXX static fields indicating the nature of the event.
     */
    init {
        this.eventID = eventID
    }

    /**
     * Returns the source `AdHocChatRoom`.
     *
     * @return the source `AdHocChatRoom`.
     */
    fun getSourceAdHocChatRoom(): AdHocChatRoomWrapper {
        return getSource() as AdHocChatRoomWrapper
    }

    /**
     * Returns a String representation of this `GuiAdHocChatRoomEvent`.
     *
     * @return A String representation of this `GuiAdHocChatRoomEvent`.
     */
    override fun toString(): String {
        val buff = StringBuffer("GuiAdHocChatRoomEvent-[ AdHocChatRoomID=")
        buff.append(getSourceAdHocChatRoom().adHocChatRoomName)
        buff.append(", eventID=").append(getEventID())
        buff.append(", ProtocolProvider=")
        return buff.toString()
    }

    /**
     * Returns an event id specifying whether the type of this event (e.g. CHAT_ROOM_ADDED or
     * CHAT_ROOM_REMOVED)
     *
     * @return one of the CHAT_ROOM_XXX int fields of this class.
     */
    fun getEventID(): Int {
        return eventID
    }

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 1L

        /**
         * Indicates that the AdHocChatRoomListChangeEvent instance was triggered by adding a
         * AdHocChatRoom in the gui.
         */
        const val AD_HOC_CHATROOM_ADDED = 1

        /**
         * Indicates that the AdHocChatRoomListChangeEvent instance was triggered by removing a
         * AdHocChatRoom from the gui.
         */
        const val AD_HOC_CHATROOM_REMOVED = 2

        /**
         * Indicates that the AdHocChatRoomListChangeEvent instance was triggered by changing a
         * AdHocChatRoom in the gui (like changing its status, etc.).
         */
        const val AD_HOC_CHATROOM_CHANGED = 3
    }
}