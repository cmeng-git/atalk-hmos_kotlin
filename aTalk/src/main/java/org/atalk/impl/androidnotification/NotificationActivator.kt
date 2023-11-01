/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification

import net.java.sip.communicator.service.notification.NotificationService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import timber.log.Timber

/**
 * Bundle adds Android specific notification handlers.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class NotificationActivator : BundleActivator {
    /**
     * Vibrate handler instance.
     */
    private var vibrateHandler: VibrateHandlerImpl? = null

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        // Get the notification service implementation
        val notifyReference = bc.getServiceReference(NotificationService::class.java.name)
        val notificationService = bc.getService<Any>(notifyReference as ServiceReference<Any>) as NotificationService
        vibrateHandler = VibrateHandlerImpl()
        notificationService.addActionHandler(vibrateHandler)
        Timber.i("Android notification handler Service...[REGISTERED]")
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        notificationService?.removeActionHandler(vibrateHandler!!.actionType)
        Timber.d("Android notification handler Service ...[STOPPED]")
    }

    companion object {
        /**
         * OSGI bundle context.
         */
        protected var bundleContext: BundleContext? = null

        /**
         * Notification service instance.
         */
        private var notificationService: NotificationService? = null
    }
}