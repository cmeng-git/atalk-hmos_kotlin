/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import org.osgi.framework.*

/**
 * Bundle activator that will start the bundle when certain service is available.
 *
 * @author Damian Minkov
 */
abstract class AbstractServiceDependentActivator : BundleActivator {
    /**
     * The service we are dependent on.
     */
    private var dependentService: Any? = null

    /**
     * Starts the bundle.
     *
     * @param bundleContext the currently valid `BundleContext`.
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        setBundleContext(bundleContext)
        if (getDependentService(bundleContext) == null) {
            try {
                bundleContext.addServiceListener(
                        DependentServiceListener(bundleContext),
                        '('.toString() + Constants.OBJECTCLASS + '=' + dependentServiceClass.name + ')')
            } catch (ise: InvalidSyntaxException) {
                // Oh, it should not really happen.
            }
            return
        } else {
            start(getDependentService(bundleContext))
        }
    }

    /**
     * The dependent service is available and the bundle will start.
     *
     * @param dependentService the service this activator is waiting.
     */
    abstract fun start(dependentService: Any?)

    /**
     * The class of the service which this activator is interested in.
     *
     * @return the class name.
     */
    abstract val dependentServiceClass: Class<*>

    /**
     * Setting context to the activator, as soon as we have one.
     *
     * @param context the context to set.
     */
    abstract fun setBundleContext(context: BundleContext)

    /**
     * Obtain the dependent service. Null if missing.
     *
     * @param context the current context to use for obtaining.
     * @return the dependent service object or null.
     */
    private fun getDependentService(context: BundleContext): Any? {
        if (dependentService == null) {
            val serviceRef = context.getServiceReference(dependentServiceClass.name)
            if (serviceRef != null) dependentService = context.getService(serviceRef)
        }
        return dependentService
    }

    /**
     * Implements a `ServiceListener` which waits for an
     * the dependent service implementation to become available, invokes
     * [.start] and un-registers itself.
     */
    private inner class DependentServiceListener internal constructor(private val context: BundleContext) : ServiceListener {
        override fun serviceChanged(serviceEvent: ServiceEvent) {
            val depService = getDependentService(context)
            if (depService != null) {
                /*
                 * This ServiceListener has successfully waited for a Service
                 * implementation to become available so it no longer need to listen.
                 */
                context.removeServiceListener(this)
                start(depService)
            }
        }
    }
}