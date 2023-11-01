/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chatroomslist

/**
 * Listener that dispatches events coming from the ad-hoc chat room list.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
interface AdHocChatRoomListChangeListener {
    /**
     * Indicates that a change has occurred in the ad-hoc chat room data list.
     */
    fun contentChanged(evt: AdHocChatRoomListChangeEvent?)
}