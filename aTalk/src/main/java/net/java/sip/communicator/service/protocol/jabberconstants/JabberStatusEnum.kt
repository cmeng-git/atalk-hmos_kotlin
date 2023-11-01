/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.jabberconstants

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import org.atalk.service.resources.ResourceManagementService
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.*

/**
 * The `JabberStatusEnum` gives access to presence states for the Sip protocol. All status
 * icons corresponding to presence states are located with the help of the `imagePath` parameter
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class JabberStatusEnum private constructor(iconPath: String) {
    /**
     * The Online status. Indicate that the user is able and willing to communicate.
     */
    private val availableStatus: JabberPresenceStatus

    /**
     * The Away status. Indicates that the user has connectivity but might not be able to
     * immediately act upon initiation of communication.
     */
    private val awayStatus: JabberPresenceStatus

    /**
     * The DND status. Indicates that the user has connectivity but prefers not to be contacted.
     */
    private val doNotDisturbStatus: JabberPresenceStatus

    /**
     * The Free For Chat status. Indicates that the user is eager to communicate.
     */
    private val freeForChatStatus: JabberPresenceStatus

    /**
     * Indicates an Offline status or status with 0 connectivity.
     */
    private val offlineStatus: JabberPresenceStatus

    /**
     * Indicates an On The Phone status.
     */
    private val onThePhoneStatus: JabberPresenceStatus

    /**
     * Indicates an On The Phone status.
     */
    private val inMeetingStatus: JabberPresenceStatus

    /**
     * Indicates an Extended Away status or status.
     */
    private val extendedAwayStatus: JabberPresenceStatus

    /**
     * The supported status set stores all statuses supported by this protocol implementation.
     */
    private val supportedStatusSet: MutableList<PresenceStatus> = LinkedList<PresenceStatus>()

    /**
     * The Unknown status. Indicate that we don't know if the user is present or not.
     */
    private val unknownStatus: JabberPresenceStatus

    /**
     * Creates a new instance of JabberStatusEnum using iconPath as the root path where it
     * should be reading status icons from.
     *
     * iconPath the location containing the status icons that should be used by this enumeration.
     */
    init {
        offlineStatus = JabberPresenceStatus(0, OFFLINE,
                loadIcon("$iconPath/status16x16-offline.png"))
        doNotDisturbStatus = JabberPresenceStatus(30, DO_NOT_DISTURB,
                loadIcon("$iconPath/status16x16-dnd.png"))
        onThePhoneStatus = JabberPresenceStatus(31, ON_THE_PHONE,
                loadIcon("$iconPath/status16x16-phone.png"))
        inMeetingStatus = JabberPresenceStatus(32, IN_A_MEETING,
                loadIcon("$iconPath/status16x16-meeting.png"))
        extendedAwayStatus = JabberPresenceStatus(35, EXTENDED_AWAY,
                loadIcon("$iconPath/status16x16-xa.png"))
        awayStatus = JabberPresenceStatus(40, AWAY,
                loadIcon("$iconPath/status16x16-away.png"))
        availableStatus = JabberPresenceStatus(65, AVAILABLE,
                loadIcon("$iconPath/status16x16-online.png"))
        freeForChatStatus = JabberPresenceStatus(85, FREE_FOR_CHAT,
                loadIcon("$iconPath/status16x16-ffc.png"))
        unknownStatus = JabberPresenceStatus(1, UNKNOWN,
                loadIcon("$iconPath/status16x16-offline.png"))

        // Initialize the list of supported status states.
        supportedStatusSet.add(freeForChatStatus)
        supportedStatusSet.add(availableStatus)
        supportedStatusSet.add(awayStatus)
        supportedStatusSet.add(onThePhoneStatus)
        supportedStatusSet.add(inMeetingStatus)
        supportedStatusSet.add(extendedAwayStatus)
        supportedStatusSet.add(doNotDisturbStatus)
        supportedStatusSet.add(offlineStatus)
    }

    /**
     * Returns the offline Jabber status.
     *
     * @param statusName the name of the status.
     * @return the offline Jabber status.
     */
    fun getStatus(statusName: String): JabberPresenceStatus {
        return if (statusName == AVAILABLE) availableStatus else if (statusName == OFFLINE) offlineStatus else if (statusName == FREE_FOR_CHAT) freeForChatStatus else if (statusName == DO_NOT_DISTURB) doNotDisturbStatus else if (statusName == AWAY) awayStatus else if (statusName == ON_THE_PHONE) onThePhoneStatus else if (statusName == IN_A_MEETING) inMeetingStatus else if (statusName == EXTENDED_AWAY) extendedAwayStatus else unknownStatus
    }

    /**
     * Returns an iterator over all status instances supported by the sip provider.
     *
     * @return an `Iterator` over all status instances supported by the sip provider.
     */
    fun getSupportedStatusSet(): List<PresenceStatus> {
        return supportedStatusSet
    }

    /**
     * An implementation of `PresenceStatus` that enumerates all states that a Jabber contact
     * can currently have.
     */
    class JabberPresenceStatus
    /**
     * Creates an instance of `JabberPresenceStatus` with the specified parameters.
     *
     * @param status the connectivity level of the new presence status instance
     * @param statusName the name of the presence status.
     * @param statusIcon the icon associated with this status
     */(status: Int, statusName: String, statusIcon: ByteArray?) : PresenceStatus(status, statusName, statusIcon)

    companion object {
        /**
         * The Online status. Indicate that the user is able and willing to communicate.
         */
        const val AVAILABLE = "Available"

        /**
         * The Away status. Indicates that the user has connectivity but might not be able to
         * immediately act upon initiation of communication.
         */
        const val AWAY = "Away"

        /**
         * The DND status. Indicates that the user has connectivity but prefers not to be contacted.
         */
        const val DO_NOT_DISTURB = "Do Not Disturb"

        /**
         * The Free For Chat status. Indicates that the user is eager to communicate.
         */
        const val FREE_FOR_CHAT = "Free For Chat"

        /**
         * On The Phone Chat status. Indicates that the user is talking to the phone.
         */
        const val ON_THE_PHONE = "On the phone"

        /**
         * In meeting Chat status. Indicates that the user is in meeting.
         */
        const val IN_A_MEETING = "In a meeting"

        /**
         * The Free For Chat status. Indicates that the user is eager to communicate.
         */
        const val EXTENDED_AWAY = "Extended Away"

        /**
         * Indicates an Offline status or status with 0 connectivity.
         */
        const val OFFLINE = "Offline"

        /**
         * The Unknown status. Indicate that we don't know if the user is present or not.
         */
        const val UNKNOWN = "Unknown"
        private val existingEnums: MutableMap<String, JabberStatusEnum> = Hashtable()

        /**
         * Returns an instance of JabberStatusEnum for the specified `iconPath` or creates a new
         * one if it doesn't already exist.
         *
         * @param iconPath the location containing the status icons that should be used by this enumeration.
         * @return the newly created JabberStatusEnum instance.
         */
        fun getJabberStatusEnum(iconPath: String): JabberStatusEnum {
            var statusEnum = existingEnums[iconPath]
            if (statusEnum != null) return statusEnum
            statusEnum = JabberStatusEnum(iconPath)
            existingEnums[iconPath] = statusEnum
            return statusEnum
        }

        /**
         * Get all status name as array.
         *
         * @return array of `String` representing the different status name
         */
        fun getStatusNames(): Array<String> {
            return arrayOf(OFFLINE, DO_NOT_DISTURB, AWAY, AVAILABLE, FREE_FOR_CHAT)
        }
        /**
         * Loads the icon.
         *
         * @param imagePath path of the image
         * @param clazz class name
         * @return the image bytes
         */
        /**
         * Loads an image from a given image path.
         *
         * @param imagePath The path to the image resource.
         * @return The image extracted from the resource at the specified path.
         */
        @JvmOverloads
        fun loadIcon(imagePath: String, clazz: Class<*> = JabberStatusEnum::class.java): ByteArray? {
            val `is` = getResourceAsStream(imagePath, clazz)
                    ?: return null
            var icon: ByteArray? = null
            try {
                icon = ByteArray(`is`.available())
                `is`.read(icon)
            } catch (exc: IOException) {
                Timber.e(exc, "Failed to load icon: %s", imagePath)
            } finally {
                try {
                    `is`.close()
                } catch (ex: IOException) {
                    /*
                 * We're closing an InputStream so there shouldn't be data loss because of it (in
                 * contrast to an OutputStream) and a warning in the log should be enough.
                 */
                    Timber.w(ex, "Failed to close the InputStream of icon: %s", imagePath)
                }
            }
            return icon
        }

        private fun getResourceAsStream(name: String, clazz: Class<*>): InputStream {
            if (name.contains("://")) {
                try {
                    return URL(name).openStream()
                } catch (ex: IOException) {
                    /*
                 * Well, we didn't really know whether the specified name represented an URL so we
                 * just tried. We'll resort to Class#getResourceAsStream then.
                 */
                }
            }
            val resourcesService: ResourceManagementService? = ProtocolProviderActivator.getResourceService()
            return resourcesService!!.getImageInputStreamForPath(name)!!
        }
    }
}