/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.util

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum

object StatusUtil {
    /**
     * Returns the image corresponding to the given presence status.
     *
     * @param status The presence status.
     * @return the image corresponding to the given presence status.
     */
    fun getStatusIcon(status: PresenceStatus?): ByteArray? {
        return if (status != null) {
            val connectivity = status.status
            when {
                connectivity < PresenceStatus.ONLINE_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_OFFLINE_ICON")
                }
                connectivity < PresenceStatus.EXTENDED_AWAY_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_DND_ICON")
                }
                connectivity < PresenceStatus.AWAY_THRESHOLD -> {
                    // the special status On The Phone is state between DND and AWAY states.
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_EXTENDED_AWAY_ICON")
                }
                connectivity < PresenceStatus.AVAILABLE_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_AWAY_ICON")
                }
                connectivity < PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_ONLINE_ICON")
                }
                connectivity < PresenceStatus.MAX_STATUS_VALUE -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_FFC_ICON")
                }
                else -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_OFFLINE_ICON")
                }
            }
        } else {
            UtilActivator.resources.getImageInBytes("service.gui.statusicons.USER_OFFLINE_ICON")
        }
    }

    /**
     * Returns the image corresponding to the given presence status.
     *
     * @param status The presence status.
     * @return the image corresponding to the given presence status.
     */
    fun getContactStatusIcon(status: PresenceStatus?): ByteArray? {
        return if (status != null) {
            val connectivity = status.status
            when {
                connectivity < PresenceStatus.ONLINE_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_OFFLINE_ICON")
                }
                connectivity < PresenceStatus.EXTENDED_AWAY_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_DND_ICON")
                }
                connectivity < PresenceStatus.AWAY_THRESHOLD -> {
                    val statusName = status.statusName
                    if (JabberStatusEnum.ON_THE_PHONE == statusName)
                        UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_ON_THE_PHONE_ICON")
                    else if (JabberStatusEnum.IN_A_MEETING == statusName) UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_IN_A_MEETING_ICON") else UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_EXTENDED_AWAY_ICON")
                }
                connectivity < PresenceStatus.AVAILABLE_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_AWAY_ICON")
                }
                connectivity < PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_ONLINE_ICON")
                }
                connectivity < PresenceStatus.MAX_STATUS_VALUE -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_FFC_ICON")
                }
                else -> {
                    UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_OFFLINE_ICON")
                }
            }
        } else {
            UtilActivator.resources.getImageInBytes("service.gui.statusicons.CONTACT_OFFLINE_ICON")
        }
    }
}