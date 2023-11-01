/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidbrowserlauncher

import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService
import net.java.sip.communicator.util.SimpleServiceActivator

/**
 * Browser launcher bundle activator.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class BrowserLauncherActivator : SimpleServiceActivator<Any?>(BrowserLauncherService::class.java, "Android Browser Launcher") {
    override fun createServiceImpl(): Any {
        return AndroidBrowserLauncher()
    }
}