/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi

import android.content.Intent
import org.atalk.service.osgi.BundleContextHolder
import org.atalk.service.osgi.OSGiService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OSGiServiceActivator : BundleActivator {
    private var bundleActivator: BundleActivator? = null
    private var osgiService: OSGiService? = null
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        startService(bundleContext)
        startBundleContextHolder(bundleContext)
    }

    @Throws(Exception::class)
    private fun startBundleContextHolder(bundleContext: BundleContext) {
        val serviceReference = bundleContext.getServiceReference(BundleContextHolder::class.java)
        if (serviceReference != null) {
            val bundleContextHolder = bundleContext.getService(serviceReference)
            if (bundleContextHolder is BundleActivator) {
                val bundleActivator = bundleContextHolder as BundleActivator
                this.bundleActivator = bundleActivator
                bundleActivator.start(bundleContext)
            }
        }
    }

    @Throws(Exception::class)
    private fun startService(bundleContext: BundleContext) {
        val serviceReference = bundleContext.getServiceReference(OSGiService::class.java)
        if (serviceReference != null) {
            val osgiService = bundleContext.getService(serviceReference)
            if (osgiService != null) {
                val componentName = osgiService.startService(Intent(osgiService, OSGiService::class.java))
                if (componentName != null) this.osgiService = osgiService
            }
        }
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        try {
            stopBundleContextHolder(bundleContext)
        } finally {
            stopService(bundleContext)
        }
    }

    @Throws(Exception::class)
    private fun stopBundleContextHolder(bundleContext: BundleContext) {
        if (bundleActivator != null) {
            try {
                bundleActivator!!.stop(bundleContext)
            } finally {
                bundleActivator = null
            }
        }
    }

    @Throws(Exception::class)
    private fun stopService(bundleContext: BundleContext) {
        if (osgiService != null) {
            try {
                // Triggers service shutdown and removes the notification
                osgiService!!.stopForegroundService()
            } finally {
                osgiService = null
            }
        }
    }
}