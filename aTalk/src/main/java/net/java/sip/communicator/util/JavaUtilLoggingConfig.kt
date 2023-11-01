/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import android.content.res.AssetManager
import org.atalk.service.osgi.OSGiService
import org.atalk.util.OSUtils
import java.io.FileInputStream
import java.io.InputStream
import java.util.logging.LogManager

/**
 * Implements the class which is to have its name specified to [LogManager] via the system property
 * `java.util.logging.config.class` and which is to read in the initial configuration.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JavaUtilLoggingConfig {
    init {
        var `is`: InputStream? = null
        try {
            val propertyName = "java.util.logging.config.class"
            if (System.getProperty(propertyName) == null) {
                System.setProperty(propertyName, JavaUtilLoggingConfig::class.java.name)
            }
            var fileName = System.getProperty("java.util.logging.config.file")
            if (fileName == null) fileName = "lib/logging.properties"
            if (OSUtils.IS_ANDROID) {
                val bundleContext = UtilActivator.bundleContext
                if (bundleContext != null) {
                    val context = ServiceUtils.getService(bundleContext, OSGiService::class.java)
                    if (context != null) {
                        `is` = context.assets.open(fileName, AssetManager.ACCESS_UNKNOWN)
                    }
                }
            } else {
                `is` = FileInputStream(fileName)
            }
            if (`is` != null) {
                LogManager.getLogManager().reset()
                LogManager.getLogManager().readConfiguration(`is`)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            `is`?.close()
        }
    }
}