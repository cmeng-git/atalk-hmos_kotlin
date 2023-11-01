/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.libjitsi

import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import java.util.*

/**
 * Represents an implementation of the `libjitsi` library which utilizes OSGi.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class LibJitsiOSGiImpl(bundleContext: BundleContext) : LibJitsiImpl() {
    /**
     * The `BundleContext` discovered by this instance during its initialization and
     * used to look for registered services.
     */
    private val bundleContext: BundleContext

    /**
     * Initializes a new `LibJitsiOSGiImpl` instance with a specific `BundleContext`.
     *
     * @param bundleContext the `BundleContext` to be used by the new
     * instance to look for registered services
     */
    init {
        this.bundleContext = Objects.requireNonNull(bundleContext, "bundleContext")
    }

    /**
     * Gets a service of a specific type associated with this implementation of the `libjitsi` library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association known to this
     * implementation of the `libjitsi` library; otherwise, `null`
     */
    override fun <T> getService(serviceClass: Class<T>): T? {
        val serviceReference = bundleContext.getServiceReference(serviceClass)
        var service = if (serviceReference == null)
            null
        else
            bundleContext.getService<Any>(serviceReference as ServiceReference<Any>) as T

        if (service == null)
            service = super.getService(serviceClass)
        return service
    }
}