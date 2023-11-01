/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import java.util.*

/**
 * Class keeps up to date list of services that implement given interface.
 * Can be used as a replacement for expensive calls to `getServiceReferences`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ServiceObserver<T>
/**
 * Creates new instance of `ServiceObserver` that will observe services of given `className`.
 *
 * @param clazz the `Class` of the service to observe.
 */
(
        /**
         * Service class name.
         */
        private val clazz: Class<T>) : ServiceListener {
    /**
     * The OSGi context.
     */
    private var context: BundleContext? = null

    /**
     * Service instances list.
     */
    private val services = ArrayList<T>()

    /**
     * Returns list of services compatible with service class observed by this instance.
     *
     * @return list of services compatible with service class observed by this instance.
     */
    fun getServices(): List<T> {
        return Collections.unmodifiableList(services)
    }

    /**
     * {@inheritDoc}
     */
    override fun serviceChanged(serviceEvent: ServiceEvent) {
        val service = context!!.getService(serviceEvent.serviceReference)
        if (!clazz.isInstance(service)) {
            return
        }
        val eventType = serviceEvent.type
        if (eventType == ServiceEvent.REGISTERED) {
            services.add(service as T)
        } else if (eventType == ServiceEvent.UNREGISTERING) {
            services.remove(service)
        }
    }

    /**
     * This method must be called when OSGi i s starting to initialize the  observer.
     *
     * @param ctx the OSGi bundle context.
     */
    fun start(ctx: BundleContext) {
        context = ctx
        ctx.addServiceListener(this)
        val refs = ServiceUtils.getServiceReferences(ctx, clazz) as Array<ServiceReference<T>?>?
        for (ref in refs!!) services.add(ctx.getService(ref))
    }

    /**
     * This method should be called on bundle shutdown to properly release the resources.
     *
     * @param ctx OSGi context
     */
    fun stop(ctx: BundleContext) {
        ctx.removeServiceListener(this)
        services.clear()
        context = null
    }
}