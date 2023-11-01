/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework

import org.atalk.impl.osgi.framework.launch.FrameworkImpl
import org.atalk.impl.osgi.framework.startlevel.BundleStartLevelImpl
import org.osgi.framework.*
import org.osgi.framework.startlevel.BundleStartLevel
import org.osgi.framework.startlevel.FrameworkStartLevel
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class BundleImpl(
        open val framework: FrameworkImpl?,
        private val bundleId: Long,
        private val location: String?) : Bundle {

    private var bundleActivator: BundleActivator? = null
    private var bundleContext: BundleContext? = null
    private var bundleStartLevel: BundleStartLevel? = null
    private var state = Bundle.INSTALLED

    override fun <A> adapt(type: Class<A>): A? {
        var adapt: Any?
        if (BundleStartLevel::class.java == type) {
            when {
                getBundleId() == 0L -> adapt = null
                else -> synchronized(this) {
                    if (bundleStartLevel == null) bundleStartLevel = BundleStartLevelImpl(this)
                    adapt = bundleStartLevel
                }
            }
        } else adapt = null

        return adapt as A?
    }

    override fun compareTo(other: Bundle): Int {
        val thisBundleId = getBundleId()
        val otherBundleId = other.bundleId
        return if (thisBundleId < otherBundleId) -1 else if (thisBundleId == otherBundleId) 0 else 1
    }

    override fun findEntries(path: String, filePattern: String, recurse: Boolean): Enumeration<URL>? {
        return null
    }

    override fun getBundleContext(): BundleContext? {
        return when (getState()) {
            Bundle.STARTING, Bundle.ACTIVE, Bundle.STOPPING -> bundleContext!!
            else -> null
        }
    }

    override fun getBundleId(): Long {
        return bundleId
    }

    override fun getDataFile(filename: String): File? {
        return null
    }

    override fun getEntry(path: String): URL? {
        return null
    }

    override fun getEntryPaths(path: String): Enumeration<String>? {
        return null
    }

    override fun getHeaders(): Dictionary<String, String>? {
        return getHeaders(null)
    }

    override fun getHeaders(locale: String?): Dictionary<String, String>? {
        return null
    }

    override fun getLastModified(): Long {
        return 0
    }

    override fun getLocation(): String? {
        return if (getBundleId() == 0L) Constants.SYSTEM_BUNDLE_LOCATION else location
    }

    override fun getRegisteredServices(): Array<ServiceReference<*>?>? {
        return framework!!.registeredServices
    }

    override fun getResource(name: String): URL? {
        return null
    }

    @Throws(IOException::class)
    override fun getResources(name: String): Enumeration<URL>? {
        return null
    }

    override fun getServicesInUse(): Array<ServiceReference<*>>? {
        return null
    }

    override fun getSignerCertificates(signersType: Int): Map<X509Certificate, List<X509Certificate>>? {
        return null
    }

    override fun getState(): Int {
        return state
    }

    override fun getSymbolicName(): String? {
        return null
    }

    override fun getVersion(): Version? {
        return null
    }

    override fun hasPermission(permission: Any): Boolean {
        return false
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*>? {
        return try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            // Tries to load class from library dex file
            Timber.e(e, "Tries to load class: %s", name)
            null
            // return LibDexLoader.instance.loadClass(name);
        }
    }

    private fun setBundleContext(bundleContext: BundleContext?) {
        this.bundleContext = bundleContext
    }

    protected fun setState(state: Int) {
        val oldState = getState()
        if (oldState != state) {
            this.state = state
            val newState = getState()
            if (oldState != newState) stateChanged(oldState, newState)
        }
    }

    @Throws(BundleException::class)
    override fun start() {
        start(0)
    }

    @Throws(BundleException::class)
    override fun start(options: Int) {
        check(getState() != Bundle.UNINSTALLED) { "Bundle.UNINSTALLED" }
        val bundleStartLevel = adapt(BundleStartLevel::class.java)
        val frameworkStartLevel = framework!!.adapt(FrameworkStartLevel::class.java)

        if (bundleStartLevel != null && bundleStartLevel.startLevel > frameworkStartLevel!!.startLevel) {
            if (options and Bundle.START_TRANSIENT == Bundle.START_TRANSIENT) throw BundleException("startLevel") else return
        }

        if (getState() == Bundle.ACTIVE) return
        if (getState() == Bundle.INSTALLED) setState(Bundle.RESOLVED)
        setState(Bundle.STARTING)
        val location = getLocation()
        if (location != null) {
            var bundleActivator: BundleActivator? = null
            var exception: Throwable? = null
            try {
                bundleActivator = loadClass(location.replace('/', '.'))!!.newInstance() as BundleActivator
                bundleActivator.start(getBundleContext())
            } catch (t: Throwable) {
                Timber.e(t, "Error starting bundle: %s", location)
                exception = if (t is ThreadDeath) throw t else t
            }
            if (exception == null) this.bundleActivator = bundleActivator else {
                setState(Bundle.STOPPING)
                setState(Bundle.RESOLVED)
                framework!!.fireBundleEvent(BundleEvent.STOPPED, this)
                throw BundleException("BundleActivator.start", exception)
            }
        }
        check(getState() != Bundle.UNINSTALLED) { "Bundle.UNINSTALLED" }
        setState(Bundle.ACTIVE)
    }

    protected open fun stateChanged(oldState: Int, newState: Int) {
        when (newState) {
            Bundle.ACTIVE -> framework!!.fireBundleEvent(BundleEvent.STARTED, this)
            Bundle.RESOLVED -> setBundleContext(null)
            Bundle.STARTING -> setBundleContext(BundleContextImpl(framework!!, this))
            Bundle.STOPPING -> {}
        }
    }

    @Throws(BundleException::class)
    override fun stop() {
        stop(0)
    }

    @Throws(BundleException::class)
    override fun stop(options: Int) {
        var wasActive = false
        when (getState()) {
            Bundle.ACTIVE, Bundle.STARTING -> {
                if (Bundle.ACTIVE == getState()) {
                    wasActive = true
                }

                setState(Bundle.STOPPING)
                var exception: Throwable? = null
                if (wasActive && bundleActivator != null) {
                    try {
                        bundleActivator!!.stop(getBundleContext())
                    } catch (t: Throwable) {
                        exception = if (t is ThreadDeath) throw t else t
                    }
                    bundleActivator = null
                }
                if (getState() == Bundle.UNINSTALLED) throw BundleException("Bundle.UNINSTALLED")
                setState(Bundle.RESOLVED)
                framework!!.fireBundleEvent(BundleEvent.STOPPED, this)
                if (exception != null) throw BundleException("BundleActivator.stop", exception)
            }

            Bundle.UNINSTALLED -> throw IllegalStateException("Bundle.UNINSTALLED")
            else -> {}
        }
    }

    @Throws(BundleException::class)
    override fun uninstall() {
        // TODO Auto-generated method stub
    }

    @Throws(BundleException::class)
    override fun update() {
        update(null)
    }

    @Throws(BundleException::class)
    override fun update(input: InputStream?) {
        // TODO Auto-generated method stub
    }
}