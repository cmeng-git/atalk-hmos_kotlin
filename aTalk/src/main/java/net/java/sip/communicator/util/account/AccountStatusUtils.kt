/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account

import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.UtilActivator

/**
 * The `AccountStatusUtils` provides utility methods for account status management.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class AccountStatusUtils {
    /**
     * Returns the last contact status saved in the configuration.
     *
     * @param protocolProvider the protocol provider to which the status corresponds
     * @return the last contact status saved in the configuration.
     */
    fun getLastStatusString(protocolProvider: ProtocolProviderService): String {
        return globalStatusService!!.getLastStatusString(protocolProvider)!!
    }

    companion object {
        /**
         * Returns the `GlobalStatusService` obtained from the bundle context.
         *
         * @return the `GlobalStatusService` obtained from the bundle context
         */
        var globalStatusService: GlobalStatusService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(UtilActivator.bundleContext,
                            GlobalStatusService::class.java)
                }
                return field
            }
            private set

        /**
         * If the protocol provider supports presence operation set searches the last status which
         * was selected, otherwise returns null.
         *
         * @param protocolProvider the protocol provider we're interested in.
         * @return the last protocol provider presence status, or null if this provider doesn't
         * support presence operation set
         */
        fun getProtocolProviderLastStatus(protocolProvider: ProtocolProviderService): Any? {
            return if (getProtocolPresenceOpSet(protocolProvider) != null) {
                getLastPresenceStatus(protocolProvider)
            }
            else {
                globalStatusService!!.getLastStatusString(protocolProvider)
            }
        }

        /**
         * Returns the presence operation set for the given protocol provider.
         *
         * @param protocolProvider The protocol provider for which the presence operation set is searched.
         * @return the presence operation set for the given protocol provider.
         */
        fun getProtocolPresenceOpSet(protocolProvider: ProtocolProviderService): OperationSetPresence? {
            return protocolProvider.getOperationSet(OperationSetPresence::class.java)
        }

        /**
         * Returns the last status that was stored in the configuration xml for the given protocol provider.
         *
         * @param protocolProvider the protocol provider
         * @return the last status that was stored in the configuration xml for the given protocol provider
         */
        fun getLastPresenceStatus(protocolProvider: ProtocolProviderService): PresenceStatus? {
            return if (globalStatusService != null) {
                globalStatusService!!.getLastPresenceStatus(protocolProvider)
            }
            else {
                null
            }
        }

        /**
         * Returns the current status for protocol provider.
         *
         * @param protocolProvider the protocol provider
         * @return the current status for protocol provider
         */
        fun getPresenceStatus(protocolProvider: ProtocolProviderService): PresenceStatus? {
            var status: PresenceStatus? = null
            val opSet = protocolProvider.getOperationSet(OperationSetPresence::class.java)
            if (opSet != null) status = opSet.getPresenceStatus()
            return status
        }

        /**
         * Returns the online status of provider.
         *
         * @param protocolProvider the protocol provider
         * @return the online status of provider.
         */
        fun getOnlineStatus(protocolProvider: ProtocolProviderService): PresenceStatus? {
            var onlineStatus: PresenceStatus? = null
            val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)

            // presence can be not supported
            if (presence != null) {
                for (status in presence.getSupportedStatusSet()) {
                    val connectivity = status!!.status
                    if ((onlineStatus != null && onlineStatus.status < connectivity || onlineStatus == null && connectivity > 50) && connectivity < 80) {
                        onlineStatus = status
                    }
                }
            }
            return onlineStatus
        }

        /**
         * Returns the offline status of provider.
         *
         * @param protocolProvider the protocol provider
         * @return the offline status of provider.
         */
        fun getOfflineStatus(protocolProvider: ProtocolProviderService): PresenceStatus? {
            var offlineStatus: PresenceStatus? = null
            val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)
            // presence can be not supported
            if (presence != null) {
                for (status in presence.getSupportedStatusSet()) {
                    val connectivity = status!!.status
                    if (connectivity < 1) {
                        offlineStatus = status
                    }
                }
            }
            return offlineStatus
        }
    }
}