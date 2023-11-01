/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui

import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.UtilActivator
import net.java.sip.communicator.util.account.AccountStatusUtils
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference

/**
 * Class takes care of setting/restoring proper presence statuses. When protocol registers for
 * the first time makes sure to set online state. When protocol provider reconnects uses
 * GlobalStatusService to restore last status set by the user.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class PresenceStatusHandler : ServiceListener, RegistrationStateChangeListener {
    /**
     * Start the handler with given OSGI context.
     *
     * @param bundleContext OSGI context to be used.
     */
    fun start(bundleContext: BundleContext) {
        bundleContext.addServiceListener(this)
        val pps = ServiceUtils.getServiceReferences(bundleContext, ProtocolProviderService::class.java)
        for (sRef in pps) {
            val provider = bundleContext.getService(sRef)
            updateStatus(provider as ProtocolProviderService)
            provider.addRegistrationStateChangeListener(this)
        }
    }

    /**
     * Stops the handler.
     *
     * @param bundleContext OSGI context to be used by this instance.
     */
    fun stop(bundleContext: BundleContext) {
        bundleContext.removeServiceListener(this)
    }

    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        // There is nothing we can do when account is registering...
        if (evt.getNewState() == RegistrationState.REGISTERING) {
            // startConnecting(protocolProvider);
        } else {
            updateStatus(evt.getProvider())
        }
    }

    override fun serviceChanged(event: ServiceEvent) {
        val serviceRef = event.serviceReference

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.bundle.state == Bundle.STOPPING) {
            return
        }
        val service = UtilActivator.bundleContext!!.getService(serviceRef) as? ProtocolProviderService
                ?: return

        // we don't care if the source service is not a protocol provider
        when (event.type) {
            ServiceEvent.REGISTERED -> service.addRegistrationStateChangeListener(this)
            ServiceEvent.UNREGISTERING -> service.removeRegistrationStateChangeListener(this)
        }
    }

    /**
     * Updates presence status on given `protocolProvider` depending on its state.
     *
     * @param protocolProvider the protocol provider for which new status will be adjusted.
     */
    private fun updateStatus(protocolProvider: ProtocolProviderService) {
        val presence = AccountStatusUtils.getProtocolPresenceOpSet(protocolProvider)
        var offlineStatus: PresenceStatus? = null
        var onlineStatus: PresenceStatus? = null
        for (status in presence!!.getSupportedStatusSet()) {
            val connectivity = status!!.status
            if (connectivity < 1) {
                offlineStatus = status
            } else if ((onlineStatus != null && onlineStatus.status < connectivity || onlineStatus == null && connectivity > 50) && connectivity < 80) {
                onlineStatus = status
            }
        }
        var presenceStatus: PresenceStatus?
        if (!protocolProvider.isRegistered) presenceStatus = offlineStatus else {
            presenceStatus = AccountStatusUtils.getLastPresenceStatus(protocolProvider)
            if (presenceStatus == null) presenceStatus = onlineStatus
        }
        val gbsService = AndroidGUIActivator.globalStatusService
        if (protocolProvider.isRegistered && gbsService != null
                && presence.getPresenceStatus() != presenceStatus) {
            gbsService.publishStatus(protocolProvider, presenceStatus!!, false)
        }
    }
}