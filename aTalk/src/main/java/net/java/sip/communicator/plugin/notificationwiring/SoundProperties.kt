/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.notificationwiring

import org.atalk.service.resources.ResourceManagementService

/**
 * Manages the access to the properties file containing all sounds paths.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object SoundProperties {
    /**
     * The incoming message sound id.
     */
    var INCOMING_MESSAGE: String? = null

    /**
     * The incoming file sound id.
     */
    var INCOMING_FILE: String? = null

    /**
     * The incoming file sound id.
     */
    var INCOMING_INVITATION: String? = null

    /**
     * The outgoing call sound id.
     */
    var OUTGOING_CALL: String? = null

    /**
     * The incoming call sound id.
     */
    var INCOMING_CALL: String? = null

    /**
     * The busy sound id.
     */
    var BUSY: String? = null

    /**
     * The dialing sound id.
     */
    var DIALING: String? = null

    /**
     * The sound id of the sound played when call security is turned on.
     */
    var CALL_SECURITY_ON: String? = null

    /**
     * The sound id of the sound played when a call security error occurs.
     */
    var CALL_SECURITY_ERROR: String? = null

    /**
     * The hang up sound id.
     */
    var HANG_UP: String? = null

    /*
     * Call NotificationActivator.getResources() once because
     * (1) it's not a trivial getter, it caches the reference so it always checks whether
     * the cache has already been built and
     * (2) accessing a local variable is supposed to be faster than calling a method
     * (even if the method is a trivial getter and it's inlined at runtime, it's still
     * supposed to be slower because it will be accessing a field, not a local variable).
     */
    init {
        val resources = NotificationWiringActivator.resources

        INCOMING_FILE = resources.getSoundPath("INCOMING_FILE")
        INCOMING_INVITATION = resources.getSoundPath("INCOMING_INVITATION")
        INCOMING_MESSAGE = resources.getSoundPath("INCOMING_MESSAGE")
        INCOMING_CALL = resources.getSoundPath("INCOMING_CALL")
        OUTGOING_CALL = resources.getSoundPath("OUTGOING_CALL")
        BUSY = resources.getSoundPath("BUSY")
        DIALING = resources.getSoundPath("DIAL")
        HANG_UP = resources.getSoundPath("HANG_UP")
        CALL_SECURITY_ON = resources.getSoundPath("CALL_SECURITY_ON")
        CALL_SECURITY_ERROR = resources.getSoundPath("CALL_SECURITY_ERROR")
    }

    /**
     * Get the aTalk default sound descriptor - for ringtone user default selection
     * @param eventType sound event type
     * @return the default aTalk sound descriptor
     */
    fun getSoundDescriptor(eventType: String?): String? {
        return when (eventType) {
            NotificationManager.INCOMING_FILE -> INCOMING_FILE
            NotificationManager.INCOMING_INVITATION -> INCOMING_INVITATION
            NotificationManager.INCOMING_MESSAGE -> INCOMING_MESSAGE
            NotificationManager.INCOMING_CALL -> INCOMING_CALL
            NotificationManager.OUTGOING_CALL -> OUTGOING_CALL
            NotificationManager.BUSY_CALL -> BUSY
            NotificationManager.DIALING -> DIALING
            NotificationManager.HANG_UP -> HANG_UP
            NotificationManager.CALL_SECURITY_ON -> CALL_SECURITY_ON
            NotificationManager.CALL_SECURITY_ERROR -> CALL_SECURITY_ERROR
            else -> null
        }
    }
}