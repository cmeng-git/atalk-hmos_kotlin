/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

/**
 * Class generalize few event types related with accounts. At the moment these are:<br></br>
 * - [AvatarEvent]<br></br>
 * - [RegistrationStateChangeEvent]<br></br>
 * - [ProviderPresenceStatusChangeEvent]<br></br>
 *
 * Object interested in any of these notification can be bound to the [Account] instance.
 * It will provide default values as well as will handle [ProtocolProviderService]'s
 * registration/unregistration events.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AccountEvent
/**
 * Creates new instance of [AccountEvent]
 */
(
        /**
         * The source [Account] of the event
         */
        val source: Account,
        /**
         * The event type
         */
        val eventType: Int) {

    companion object {
        /**
         * The protocol provider's registration state change event type
         */
        const val REGISTRATION_CHANGE = 0

        /**
         * Presence status change event type
         */
        const val PRESENCE_STATUS_CHANGE = 1

        /**
         * Presence status message change event type
         */
        const val STATUS_MSG_CHANGE = 2

        /**
         * Avatar change event type
         */
        const val AVATAR_CHANGE = 3
    }
}