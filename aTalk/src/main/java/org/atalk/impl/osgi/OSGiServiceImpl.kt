/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.IBinder
import android.text.TextUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.osgi.framework.AsyncExecutor
import org.atalk.impl.osgi.framework.launch.FrameworkFactoryImpl
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.osgi.BundleContextHolder
import org.atalk.service.osgi.OSGiService
import org.atalk.util.OSUtils
import org.osgi.framework.BundleException
import org.osgi.framework.Constants
import org.osgi.framework.launch.Framework
import org.osgi.framework.startlevel.BundleStartLevel
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Implements the actual, internal functionality of [OSGiService].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OSGiServiceImpl
/**
 * Initializes a new `OSGiServiceImpl` instance which is to be used by a specific
 * Android `OSGiService` as its very implementation.
 *
 * service the Android `OSGiService` which is to use the new instance as its very implementation
 */
(
        /**
         * The Android [Service] which uses this instance as its very implementation.
         */
        private val service: OSGiService,
) {
    private val bundleContextHolder = OSGiServiceBundleContextHolder()
    private val executor = AsyncExecutor<Runnable>(5, TimeUnit.MINUTES)

    /**
     * The `org.osgi.framework.launch.Framework` instance which represents the OSGi
     * instance launched by this `OSGiServiceImpl`.
     */
    private var framework: Framework? = null

    /**
     * The `Object` which synchronizes the access to [.framework].
     */
    private val frameworkSyncRoot = Any()

    /**
     * Invoked by the Android system to initialize a communication channel to [.service]. Returns an implementation
     * of the public API of the `OSGiService` i.e. [BundleContextHolder] in the form of an [IBinder].
     *
     * @param intent the `Intent` which was used to bind to `service`
     * @return an `IBinder` through which clients may call on to the public API of `OSGiService`
     * @see Service.onBind
     */
    fun onBind(intent: Intent?): IBinder {
        return bundleContextHolder
    }

    /**
     * Invoked by the Android system when [.service] is first created. Asynchronously starts
     * the OSGi framework (implementation) represented by this instance.
     *
     * @see Service.onCreate
     */
    fun onCreate() {
        try {
            setScHomeDir()
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
        }
        try {
            setJavaUtilLoggingConfigFile()
        } catch (t: Throwable) {
            if (t is ThreadDeath) throw t
        }
        executor.execute(OnCreateCommand())
    }

    /**
     * Invoked by the Android system when [.service] is no longer used and is being removed.
     * Asynchronously stops the OSGi framework (implementation) represented by this instance.
     *
     * @see Service.onDestroy
     */
    fun onDestroy() {
        synchronized(executor) {
            executor.execute(OnDestroyCommand())
            executor.shutdown()
        }
    }

    /**
     * Invoked by the Android system every time a client explicitly starts [.service] by
     * calling [Context.startService]. Always returns [Service.START_STICKY].
     *
     * intent the `Intent` supplied to `Context.startService(Intent}` flags additional data about the start request
     * startId a unique integer which represents this specific request to start
     * @return a value which indicates what semantics the Android system should use for`service`'s current started state
     * @see Service.onStartCommand
     */
    fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    /**
     * Sets up `java.util.logging.LogManager` by assigning values to the system properties
     * which allow more control over reading the initial configuration.
     */
    private fun setJavaUtilLoggingConfigFile() {}
    private fun setScHomeDir() {
        var name: String? = null
        if (System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION) == null) {
            val filesDir = service.filesDir
            val location = filesDir.parentFile?.absolutePath
            name = filesDir.name
            System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, location)
        }
        if (System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME) == null) {
            if (TextUtils.isEmpty(name)) {
                val info = service.applicationInfo
                name = info.name
                if (TextUtils.isEmpty(name)) name = aTalkApp.getResString(R.string.APPLICATION_NAME)
            }
            System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME, name)
        }

        // Set log dir location to PNAME_SC_HOME_DIR_LOCATION
        if (System.getProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION) == null) {
            val homeDir = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, null)
            System.setProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION, homeDir)
        }
        // Set cache dir location to Context.getCacheDir()
        if (System.getProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION) == null) {
            val cacheDir = service.cacheDir
            val location = cacheDir.parentFile?.absolutePath
            System.setProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION, location)
        }

        /*
         * Set the System property user.home as well because it may be relied upon (e.g. FMJ).
         */
        val location = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION)
        if (location != null && location.isNotEmpty()) {
            name = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
            if (name != null && name.isNotEmpty()) {
                System.setProperty("user.home", File(location, name).absolutePath)
            }
        }
    }

    /**
     * Asynchronously starts the OSGi framework (implementation) represented by this instance.
     */
    private inner class OnCreateCommand : Runnable {
        override fun run() {
            val frameworkFactory = FrameworkFactoryImpl()
            val configuration = HashMap<String, String>()
            val bundles = getBundlesConfig(service)
            configuration[Constants.FRAMEWORK_BEGINNING_STARTLEVEL] = bundles.lastKey().toString()
            val framework = frameworkFactory.newFramework(configuration)
            try {
                framework.init()
                val bundleContext = framework.bundleContext
                bundleContext.registerService(OSGiService::class.java, service, null)
                bundleContext.registerService(BundleContextHolder::class.java, bundleContextHolder, null)
                for ((startLevel, value) in bundles) {
                    for (location in value) {
                        val bundle = bundleContext.installBundle(location)
                        if (bundle != null) {
                            val bundleStartLevel = bundle.adapt(BundleStartLevel::class.java)
                            if (bundleStartLevel != null) bundleStartLevel.startLevel = startLevel
                        }
                    }
                }
                framework.start()
            } catch (be: BundleException) {
                throw RuntimeException(be)
            }
            synchronized(frameworkSyncRoot) { this@OSGiServiceImpl.framework = framework }
            service.onOSGiStarted()
        }

        /**
         * Loads bundles configuration from the configured or default file name location.
         *
         * @param context the context to use
         * @return the locations of the OSGi bundles (or rather of the class files of their
         * `BundleActivator` implementations) comprising the Jitsi core/library and the
         * application which is currently using it. And the corresponding start levels.
         */
        private fun getBundlesConfig(context: Context?): TreeMap<Int, List<String>> {
            var fileName = System.getProperty("osgi.config.properties")
            if (fileName == null) fileName = "lib/osgi.client.run.properties"
            var `is`: InputStream? = null
            val props = Properties()
            try {
                if (OSUtils.IS_ANDROID) {
                    if (context != null) {
                        `is` = context.assets.open(fileName, AssetManager.ACCESS_UNKNOWN)
                    }
                } else {
                    `is` = FileInputStream(fileName)
                }
                if (`is` != null) props.load(`is`)
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            } finally {
                try {
                    `is`?.close()
                } catch (ignore: IOException) {
                }
            }
            val startLevels = TreeMap<Int, List<String>>()
            for ((key, value1) in props) {
                val prop = key.toString().trim { it <= ' ' }
                var value: Any? = null
                if (prop.contains("auto.start.") && value1.also { value = it } != null) {
                    val startLevelStr = prop.substring("auto.start.".length)
                    try {
                        val startLevelInt = startLevelStr.toInt()
                        val classTokens = StringTokenizer("$value", " ")
                        val classNames = ArrayList<String>()
                        while (classTokens.hasMoreTokens()) {
                            val className = classTokens.nextToken().trim { it <= ' ' }
                            if (!TextUtils.isEmpty(className) && !className.startsWith("#")) classNames.add(className)
                        }
                        if (classNames.isNotEmpty()) startLevels[startLevelInt] = classNames
                    } catch (t: Throwable) {
                        if (t is ThreadDeath) throw t
                    }
                }
            }
            return startLevels
        }
    }

    /**
     * Asynchronously stops the OSGi framework (implementation) represented by this instance.
     */
    private inner class OnDestroyCommand : Runnable {
        override fun run() {
            var framework: Framework?
            synchronized(frameworkSyncRoot) {
                framework = this@OSGiServiceImpl.framework
                this@OSGiServiceImpl.framework = null
            }
            if (framework != null) try {
                framework!!.stop()
            } catch (be: BundleException) {
                throw RuntimeException(be)
            }
        }
    }
}