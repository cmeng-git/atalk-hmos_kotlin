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
package net.java.sip.communicator.service.protocol.globalstatus

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import net.java.sip.communicator.util.ConfigurationUtils

/**
 * The global statuses available to the system.
 *
 * @author Damian Minkov
 */
class GlobalStatusEnum
/**
 * Creates a status with the specified connectivity coeff, name and icon.
 *
 * @param status the connectivity coefficient for the specified status
 * @param statusName String
 * @param statusIcon the icon associated with this status
 */
private constructor(status: Int, statusName: String, statusIcon: ByteArray?, private val i18NKey: String) : PresenceStatus(status, statusName, statusIcon) {
    companion object {
        /**
         * Indicates that the user is connected and ready to communicate.
         */
        const val ONLINE_STATUS = "Online"

        /**
         * Indicates that the user is disconnected.
         */
        const val OFFLINE_STATUS = "Offline"

        /**
         * Indicates that the user is away.
         */
        const val AWAY_STATUS = "Away"

        /**
         * Indicates that the user is extended away.
         */
        const val EXTENDED_AWAY_STATUS = "Extended Away"

        /**
         * Indicates that the user is connected and eager to communicate.
         */
        const val FREE_FOR_CHAT_STATUS = "Free For Chat"

        /**
         * Indicates that the user is connected and eager to communicate.
         */
        const val DO_NOT_DISTURB_STATUS = "Do Not Disturb"

        /**
         * The Online status. Indicate that the user is able and willing to communicate.
         */
        val ONLINE = GlobalStatusEnum(65, ONLINE_STATUS,
                loadIcon("service.gui.statusicons.USER_ONLINE_ICON"), "service.gui.ONLINE")

        /**
         * The Free For Chat status. Indicates that the user is eager to communicate.
         */
        val FREE_FOR_CHAT = GlobalStatusEnum(85,
                FREE_FOR_CHAT_STATUS, loadIcon("service.gui.statusicons.USER_FFC_ICON"),
                "service.gui.FFC_STATUS")

        /**
         * The Away status. Indicates that the user has connectivity but might not be able to
         * immediately act upon initiation of communication.
         */
        val AWAY = GlobalStatusEnum(48, AWAY_STATUS,
                loadIcon("service.gui.statusicons.USER_AWAY_ICON"), "service.gui.AWAY_STATUS")

        /**
         * The Away status. Indicates that the user has connectivity but might not be able to
         * immediately act upon initiation of communication.
         */
        val EXTENDED_AWAY = GlobalStatusEnum(35,
                EXTENDED_AWAY_STATUS, loadIcon("service.gui.statusicons.USER_EXTENDED_AWAY_ICON"),
                "service.gui.EXTENDED_AWAY_STATUS")

        /**
         * The DND status. Indicates that the user has connectivity but prefers not to be contacted.
         */
        val DO_NOT_DISTURB = GlobalStatusEnum(30,
                DO_NOT_DISTURB_STATUS, loadIcon("service.gui.statusicons.USER_DND_ICON"),
                "service.gui.DND_STATUS")

        /**
         * The Offline status. Indicates the user does not seem to be connected to any network.
         */
        val OFFLINE = GlobalStatusEnum(0, OFFLINE_STATUS,
                loadIcon("service.gui.statusicons.USER_OFFLINE_ICON"), "service.gui.OFFLINE")

        /**
         * The set of states currently supported.
         */
        private val globalStatusSet = ArrayList<GlobalStatusEnum>()

        init {
            globalStatusSet.add(ONLINE)
            globalStatusSet.add(FREE_FOR_CHAT)
            globalStatusSet.add(AWAY)
            if (!ConfigurationUtils.isHideExtendedAwayStatus) globalStatusSet.add(EXTENDED_AWAY)
            globalStatusSet.add(DO_NOT_DISTURB)
            globalStatusSet.add(OFFLINE)
        }

        /**
         * Loads an image from a given image path.
         *
         * @param imagePath The identifier of the image.
         *
         * @return The image for the given identifier.
         */
        private fun loadIcon(imagePath: String): ByteArray? {
            return ProtocolProviderActivator.getResourceService().getImageInBytes(imagePath)
        }

        /**
         * Returns the i18n name of the status.
         *
         * @param status the status.
         *
         * @return the i18n name of the status.
         */
        fun getI18NStatusName(status: GlobalStatusEnum): String? {
            return ProtocolProviderActivator.getResourceService().getI18NString(status.i18NKey)
        }

        /**
         * Finds the status with appropriate name and return it.
         *
         * @param name the name we search for.
         *
         * @return the global status.
         */
        fun getStatusByName(name: String): GlobalStatusEnum? {
            for (gs in globalStatusSet) {
                if (gs.statusName == name) return gs
            }
            return null
        }
    }
}