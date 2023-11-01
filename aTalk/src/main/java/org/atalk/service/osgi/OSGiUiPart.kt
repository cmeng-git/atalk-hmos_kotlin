/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi

import org.osgi.framework.BundleContext

/**
 * Interface should be implemented by all `Fragments` that want to make use of OSGi and live inside
 * `OSGiActivities`. Methods [.start] and [.stop] are fired
 * automatically when OSGI context is available.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
interface OSGiUiPart {
    /**
     * Fired when OSGI is started and the `bundleContext` is available.
     *
     * @param bundleContext the OSGI bundle context.
     */
    @Throws(Exception::class)
    fun start(bundleContext: BundleContext?)

    /**
     * Fired when parent `OSGiActivity` is being stopped or this fragment is being detached.
     *
     * @param bundleContext the OSGI bundle context.
     */
    @Throws(Exception::class)
    fun stop(bundleContext: BundleContext?)
}