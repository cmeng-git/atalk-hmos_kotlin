/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.configuration

import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.libjitsi.LibJitsi
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class ConfigurationActivator : BundleActivator {
    /**
     * Starts the configuration service
     *
     * @param bundleContext the `BundleContext` as provided by the OSGi framework.
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        val configurationService = LibJitsi.configurationService

        configurationService.setProperty("protocol.sip.DESKTOP_STREAMING_DISABLED", "true")
        configurationService.setProperty("protocol.jabber.DESKTOP_STREAMING_DISABLED", "true")
        configurationService.setProperty("protocol.jabber.DISABLE_CUSTOM_DIGEST_MD5", "true")
        bundleContext.registerService(ConfigurationService::class.java.name, configurationService, null)
    }

    /**
     * Causes the configuration service to store the properties object and unregisters the configuration service.
     *
     * @param bundleContext `BundleContext`
     * @throws Exception if anything goes wrong while storing the properties managed by the `ConfigurationService`
     * implementation provided by this bundle and while unregistering the service in question
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    companion object {

        /**
         * The `BundleContext` in which the configuration bundle has been started and has not been stopped yet.
         */
        lateinit var bundleContext: BundleContext
    }
}