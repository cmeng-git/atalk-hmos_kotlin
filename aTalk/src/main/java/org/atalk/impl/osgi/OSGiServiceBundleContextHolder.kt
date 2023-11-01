/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi

import android.os.Binder
import org.atalk.service.osgi.BundleContextHolder
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OSGiServiceBundleContextHolder : Binder(), BundleActivator, BundleContextHolder {
    private val bundleActivators = ArrayList<BundleActivator>()
    override var bundleContext: BundleContext? = null
        get() = synchronized(bundleActivators) { return field }

    override fun addBundleActivator(bundleActivator: BundleActivator?) {
        if (bundleActivator == null) throw NullPointerException("bundleActivator") else {
            synchronized(bundleActivators) {
                if (!bundleActivators.contains(bundleActivator) && bundleActivators.add(bundleActivator) && bundleContext != null) {
                    try {
                        bundleActivator.start(bundleContext)
                    } catch (t: Throwable) {
                        Timber.e(t, "Error starting bundle: %s", bundleActivator)
                        if (t is ThreadDeath) throw t
                    }
                }
            }
        }
    }

    override fun removeBundleActivator(bundleActivator: BundleActivator?) {
        if (bundleActivator != null) {
            synchronized(bundleActivators) { bundleActivators.remove(bundleActivator) }
        }
    }

    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        synchronized(bundleActivators) {
            this.bundleContext = bundleContext
            val bundleActivatorIter = bundleActivators.iterator()
            while (bundleActivatorIter.hasNext()) {
                val bundleActivator = bundleActivatorIter.next()
                try {
                    bundleActivator.start(bundleContext)
                } catch (t: Throwable) {
                    Timber.e(t, "Error starting bundle: %s", bundleActivator)
                    if (t is ThreadDeath) throw t
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        synchronized(bundleActivators) {
            try {
                val bundleActivatorIter = bundleActivators.iterator()
                while (bundleActivatorIter.hasNext()) {
                    val bundleActivator = bundleActivatorIter.next()
                    try {
                        bundleActivator.stop(bundleContext)
                    } catch (t: Throwable) {
                        if (t is ThreadDeath) throw t
                    }
                }
            } finally {
                this.bundleContext = null
            }
        }
    }
}