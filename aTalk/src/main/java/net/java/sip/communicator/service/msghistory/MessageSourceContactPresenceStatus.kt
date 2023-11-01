/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.msghistory

import net.java.sip.communicator.service.protocol.PresenceStatus

/**
 * Special message source contact status, can be used to display only one (online) status for all message source
 * contacts.
 *
 * @author Damian Minkov
 */
class MessageSourceContactPresenceStatus
/**
 * Constructs special message source contact status.
 *
 * @param status
 * @param statusName
 */
protected constructor(status: Int, statusName: String?) : PresenceStatus(status, statusName!!) {
    /**
     * Returns an image that graphically represents the status.
     *
     * @return a byte array containing the image that graphically represents the status or null if no such image is
     * available.
     */
    /**
     * Sets the icon.
     *
     * @param statusIcon
     */
    /**
     * An image that graphically represents the status.
     */
    override var statusIcon: ByteArray? = null

    override val isOnline: Boolean
        get() = true

    companion object {
        /**
         * An integer for this status.
         */
        const val MSG_SRC_CONTACT_ONLINE_THRESHOLD = 89

        /**
         * Indicates that is connected and ready to communicate.
         */
        const val ONLINE_STATUS = "Online"

        /**
         * The Online status. Indicate that the user is able and willing to communicate in the chat room.
         */
        val MSG_SRC_CONTACT_ONLINE = MessageSourceContactPresenceStatus(MSG_SRC_CONTACT_ONLINE_THRESHOLD,
                ONLINE_STATUS)
    }
}