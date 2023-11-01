/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceReference

/**
 * Gathers utility functions related to OSGi services such as getting a service registered in a BundleContext.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object ServiceUtils {
    /**
     * Gets an OSGi service registered in a specific `BundleContext` by its `Class`
     *
     * @param <T> the very type of the OSGi service to get
     * @param bundleContext the `BundleContext` in which the service to get has been registered
     * @param serviceClass the `Class` with which the service to get has been registered in the
     * `bundleContext`
     * @return the OSGi service registered in `bundleContext` with the specified
     * `serviceClass` if such a service exists there; otherwise, `null`
    </T> */
    @JvmStatic
    fun <T> getService(bundleContext: BundleContext?, serviceClass: Class<T>?): T? {
        var serviceReference: ServiceReference<T>? = null
        if (bundleContext != null && serviceClass != null) serviceReference = bundleContext.getServiceReference(serviceClass)
        return if (serviceReference == null) null else bundleContext!!.getService(serviceReference)
    }

    /**
     * Gets an OSGi service references registered in a specific `BundleContext` by its `Class`.
     *
     * @param bundleContext the `BundleContext` in which the services to get have been registered
     * @param serviceClass the `Class` of the OSGi service references to get
     * @return the OSGi service references registered in `bundleContext` with the specified
     * `serviceClass` if such a services exists there; otherwise, an empty `Collection`
     */
    fun getServiceReferences(bundleContext: BundleContext, serviceClass: Class<*>): Array<ServiceReference<*>?> {
        var serviceReferences: Array<ServiceReference<*>?>?
        serviceReferences = try {
            bundleContext.getServiceReferences(serviceClass.name, null)
        } catch (ex: InvalidSyntaxException) {
            null
        } catch (ex: NullPointerException) {
            null
        }
        if (serviceReferences == null) serviceReferences = arrayOfNulls<ServiceReference<*>?>(0) // Collections.emptyList();
        return serviceReferences
    }
}