/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.icqconstants

import net.java.sip.communicator.service.protocol.PresenceStatus
import timber.log.Timber
import java.io.IOException

/**
 * An enumeration containing all status instances that MUST be supported by an implementation of the
 * ICQ (Oscar) protocol. Implementations may support other forms of PresenceStatus but they MUST ALL
 * support those enumerated here.
 *
 *
 * For testing purposes, this class also provides a `List` containing all of the status
 * fields.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class IcqStatusEnum
/**
 * Creates a status with the specified connectivity coeff, name and icon.
 *
 * @param status the connectivity coefficient for the specified status
 * @param statusName String
 * @param statusIcon the icon associated with this status
 */
protected constructor(status: Int, statusName: String, statusIcon: ByteArray?) : PresenceStatus(status, statusName, statusIcon) {
    companion object {
        /**
         * The Free For Chat ICQ status. Indicates that the user is eager to communicate.
         */
        val FREE_FOR_CHAT = IcqStatusEnum(85, "Free For Chat",
                loadIcon("resources/images/protocol/icq/icq16x16-ffc.png"))

        /**
         * The Online ICQ status. Indicate that the user is able and willing to communicate.
         */
        val ONLINE = IcqStatusEnum(65, "Online",
                loadIcon("resources/images/protocol/icq/icq16x16-online.png"))

        /**
         * The Away ICQ status. Indicates that the user has connectivity but might not be able to
         * immediately act upon initiation of communication.
         */
        val AWAY = IcqStatusEnum(48, "Away",
                loadIcon("resources/images/protocol/icq/icq16x16-away.png"))

        /**
         * The Invisible ICQ status. Indicates that the user has connectivity even though it may appear
         * otherwise to others, to whom she would appear to be offline.
         */
        val INVISIBLE = IcqStatusEnum(45, "Invisible",
                loadIcon("resources/images/protocol/icq/icq16x16-invisible.png"))

        /**
         * The Not Available ICQ status. Indicates that the user has connectivity but might not be able
         * to immediately act (i.e. even less immediately than when in an Away status ;-P ) upon
         * initiation of communication.
         */
        val NOT_AVAILABLE = IcqStatusEnum(35, "Not Available",
                loadIcon("resources/images/protocol/icq/icq16x16-na.png"))

        /**
         * The DND ICQ status. Indicates that the user has connectivity but prefers not to be contacted.
         */
        val DO_NOT_DISTURB = IcqStatusEnum(30, "Do Not Disturb",
                loadIcon("resources/images/protocol/icq/icq16x16-dnd.png"))

        /**
         * The Occupied ICQ status. Indicates that the user has connectivity and communication is
         * particularly unwanted.
         */
        val OCCUPIED = IcqStatusEnum(25, "Occupied",
                loadIcon("resources/images/protocol/icq/icq16x16-occupied.png"))

        /**
         * The Offline ICQ status. Indicates the user does not seem to be connected to the ICQ network
         * or at least does not want us to know she is
         */
        val OFFLINE = IcqStatusEnum(0, "Offline",
                loadIcon("resources/images/protocol/icq/icq16x16-offline.png"))

        /**
         * The minimal set of states that any ICQ implementation must support.
         */
        val icqStatusSet = ArrayList<IcqStatusEnum>()

        init {
            icqStatusSet.add(FREE_FOR_CHAT)
            icqStatusSet.add(ONLINE)
            icqStatusSet.add(INVISIBLE)
            icqStatusSet.add(AWAY)
            icqStatusSet.add(NOT_AVAILABLE)
            icqStatusSet.add(DO_NOT_DISTURB)
            icqStatusSet.add(OCCUPIED)
            icqStatusSet.add(OFFLINE)
        }

        /**
         * Loads an image from a given image path.
         *
         * @param imagePath The identifier of the image.
         * @return The image for the given identifier.
         */
        fun loadIcon(imagePath: String?): ByteArray? {
            val `is` = IcqStatusEnum::class.java.classLoader.getResourceAsStream(imagePath)
                    ?: return null
            var icon: ByteArray? = null
            try {
                icon = ByteArray(`is`.available())
                `is`.read(icon)
            } catch (e: IOException) {
                Timber.e(e, "Failed to load icon: %s", imagePath)
            }
            return icon
        }
    }
}