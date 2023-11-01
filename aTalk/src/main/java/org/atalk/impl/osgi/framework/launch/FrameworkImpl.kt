/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.launch

import org.atalk.impl.osgi.framework.BundleImpl
import org.atalk.impl.osgi.framework.ServiceRegistrationImpl
import org.atalk.impl.osgi.framework.startlevel.FrameworkStartLevelImpl
import org.osgi.framework.*
import org.osgi.framework.Bundle.START_ACTIVATION_POLICY
import org.osgi.framework.Bundle.START_TRANSIENT
import org.osgi.framework.Bundle.STOP_TRANSIENT
import org.osgi.framework.launch.Framework
import org.osgi.framework.startlevel.BundleStartLevel
import org.osgi.framework.startlevel.FrameworkStartLevel
import timber.log.Timber
import java.io.InputStream
import java.util.*
import kotlin.system.exitProcess

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
@Suppress("UNCHECKED_CAST")
class FrameworkImpl(private val configuration: Map<String, String>?) : BundleImpl(null, 0, null), Framework {
    private val bundles: MutableList<BundleImpl> = LinkedList()
    private var eventDispatcher: EventDispatcher? = null
    private var frameworkStartLevel: FrameworkStartLevelImpl? = null
    private var nextBundleId = 1L
        get() = field++

    private var nextServiceId = 1L
    private val serviceRegistrations: MutableList<ServiceRegistrationImpl> = LinkedList()

    init {
        bundles.add(this)
    }

    override fun <A> adapt(type: Class<A>): A? {
        var adapt: Any?
        if (FrameworkStartLevel::class.java == type) {
            synchronized(this) {
                if (frameworkStartLevel == null) frameworkStartLevel = FrameworkStartLevelImpl(this)
                adapt = frameworkStartLevel
            }
        } else adapt = null

        return adapt as A? ?: super.adapt(type)
    }

    fun addBundleListener(origin: BundleImpl?, listener: BundleListener) {
        if (eventDispatcher != null) eventDispatcher!!.addListener(origin, BundleListener::class.java, listener)
    }

    fun addServiceListener(origin: BundleImpl?, listener: ServiceListener, filter: Filter?) {
        if (eventDispatcher != null) eventDispatcher!!.addListener(origin, ServiceListener::class.java, listener)
    }

    fun fireBundleEvent(type: Int, bundle: Bundle?) {
        fireBundleEvent(type, bundle, bundle)
    }

    private fun fireBundleEvent(type: Int, bundle: Bundle?, origin: Bundle?) {
        if (eventDispatcher != null) eventDispatcher!!.fireBundleEvent(BundleEvent(type, bundle, origin))
    }

    // private fun fireFrameworkEvent(type: Int, listeners: Array<out FrameworkListener>) {
    private fun fireFrameworkEvent(type: Int, vararg listeners: FrameworkListener) {
        if (listeners.isNotEmpty()) {
            val event = FrameworkEvent(type, this, null)
            for (listener in listeners) try {
                listener.frameworkEvent(event)
            } catch (t: Throwable) {
                if (type != FrameworkEvent.ERROR) {
                    // TODO Auto-generated method stub
                    Timber.e("fireFrameworkEvent: %s", t.message)
                }
                Timber.e(t, "Error firing framework event")
            }
        }
    }

    private fun fireServiceEvent(type: Int, reference: ServiceReference<*>?) {
        if (eventDispatcher != null) eventDispatcher!!.fireServiceEvent(ServiceEvent(type, reference))
    }

    fun getBundle(id: Long): BundleImpl? {
        return if (id == 0L) this else {
            synchronized(bundles) { for (bundle in bundles) if (bundle.bundleId == id) return bundle }
            null
        }
    }

    private fun getBundlesByStartLevel(startLevel: Int): List<BundleImpl> {
        val bundles: MutableList<BundleImpl> = LinkedList()
        synchronized(this.bundles) {
            for (bundle in this.bundles) {
                val bundleStartLevel = bundle.adapt(BundleStartLevel::class.java)
                if (bundleStartLevel != null && bundleStartLevel.startLevel == startLevel) bundles.add(bundle)
            }
        }
        return bundles
    }

    @Throws(InvalidSyntaxException::class)
    fun getServiceReferences(
            origin: BundleImpl, clazz: Class<*>, className: String,
            filter: Filter?, checkAssignable: Boolean,
    ): Collection<ServiceReference<*>> {
        val classNameFilter = FrameworkUtil.createFilter('(' + Constants.OBJECTCLASS + '=' + (className ?: '*') + ')')

        val serviceReferences = LinkedList<ServiceReference<*>>()
        synchronized(serviceRegistrations) {
            for (serviceRegistration in serviceRegistrations) {
                val serviceReference = serviceRegistration.reference
                if (classNameFilter.match(serviceReference)
                        && (filter == null || filter.match(serviceReference))) {
                    val serviceReferenceS = serviceRegistration.getReference(clazz)
                    serviceReferences.add(serviceReferenceS)
                }
            }
        }
        return serviceReferences
    }

    override val framework: FrameworkImpl
        get() = this

    @Throws(BundleException::class)
    override fun init() {
        state = Framework.STARTING
    }

    @Throws(BundleException::class)
    override fun init(vararg listeners: FrameworkListener) {
    }

    @Throws(BundleException::class)
    fun installBundle(origin: BundleImpl?, location: String?, input: InputStream?): Bundle? {
        if (location == null) throw BundleException("location")

        var bundle: BundleImpl? = null
        var fireBundleEvent = false
        synchronized(bundles) {
            for (existing in bundles) if (existing.location == location) {
                bundle = existing
                break
            }
            if (bundle == null) {
                bundle = BundleImpl(this.framework, nextBundleId, location)
                bundles.add(bundle!!)
                fireBundleEvent = true
            }
        }
        if (fireBundleEvent) fireBundleEvent(BundleEvent.INSTALLED, bundle, origin)
        return bundle
    }

//    fun registerService(
//            origin: BundleImpl, clazz: Class<*>?, classNames: Array<String>,
//            service: ServiceRegistration<*>, properties: Dictionary<String, *>,
//    ): ServiceRegistration<*> {
//        require((classNames.isNotEmpty())) { "classNames" }
//
//        var serviceId: Long
//        synchronized(serviceRegistrations) { serviceId = nextServiceId++ }
//        val serviceRegistration = ServiceRegistrationImpl(origin, serviceId, classNames, service, properties)
//        synchronized(serviceRegistrations) { serviceRegistrations.add(serviceRegistration) }
//        fireServiceEvent(ServiceEvent.REGISTERED, serviceRegistration.reference)
//        return serviceRegistration
//    }

    fun registerService(
            origin: BundleImpl?, clazz: Class<*>?, classNames: Array<String>,
            service: Any, properties: Dictionary<String, *>?,
    ): ServiceRegistration<*> {
        require(classNames.isNotEmpty()) { "classNames" }

        val serviceClass: Class<*> = service.javaClass
        if (!ServiceFactory::class.java.isAssignableFrom(serviceClass)) {
            val classLoader = serviceClass.classLoader
            for (className in classNames) {
                var illegalArgumentException = true
                var cause: Throwable? = null
                try {
                    if (Class.forName(className, false, classLoader).isAssignableFrom(serviceClass)) {
                        illegalArgumentException = false
                    }
                } catch (eiie: ClassNotFoundException) {
                    cause = eiie
                } catch (eiie: LinkageError) {
                    cause = eiie
                }
                if (illegalArgumentException) throw IllegalArgumentException(className, cause)
            }
        }

        var serviceId: Long
        synchronized(serviceRegistrations) { serviceId = nextServiceId++ }
        val serviceRegistration = ServiceRegistrationImpl(origin!!, serviceId, classNames, service, properties)
        synchronized(serviceRegistrations) { serviceRegistrations.add(serviceRegistration) }
        fireServiceEvent(ServiceEvent.REGISTERED, serviceRegistration.reference)
        return serviceRegistration
    }


    fun removeBundleListener(origin: BundleImpl, listener: BundleListener) {
        if (eventDispatcher != null) eventDispatcher!!.removeListener(origin, BundleListener::class.java, listener)
    }

    fun removeServiceListener(origin: BundleImpl, listener: ServiceListener) {
        if (eventDispatcher != null) eventDispatcher!!.removeListener(origin, ServiceListener::class.java, listener)
    }

    @Throws(BundleException::class)
    override fun start(options: Int) {
        var state = state
        if (state == Framework.INSTALLED || state == Framework.RESOLVED) {
            init()
            state = getState()
        }
        if (state == Framework.STARTING) {
            var startLevel = 1
            if (configuration != null) {
                val s = configuration[Constants.FRAMEWORK_BEGINNING_STARTLEVEL]
                if (s != null) try {
                    startLevel = s.toInt()
                } catch (ignore: NumberFormatException) {
                }
            }
            val frameworkStartLevel = adapt(FrameworkStartLevel::class.java)
            val listener = object : FrameworkListener {
                override fun frameworkEvent(event: FrameworkEvent) {
                    synchronized(this) { (this as Object).notifyAll() }
                }
            }
            frameworkStartLevel!!.setStartLevel(startLevel, listener)
            synchronized(listener) {
                var interrupted = false
                while (frameworkStartLevel.startLevel < startLevel) try {
                    (listener as Object).wait()
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
                if (interrupted) Thread.currentThread().interrupt()
            }
            setState(Framework.ACTIVE)
        }
    }

    fun startLevelChanged(oldStartLevel: Int, newStartLevel: Int, vararg listeners: FrameworkListener) {
        if (oldStartLevel < newStartLevel) {
            for (bundle in getBundlesByStartLevel(newStartLevel)) {
                try {
                    val bundleStartLevel = bundle.adapt(BundleStartLevel::class.java)
                    var options = START_TRANSIENT
                    if (bundleStartLevel!!.isActivationPolicyUsed) options = options or START_ACTIVATION_POLICY
                    bundle.start(options)
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t
                    Timber.e(t, "Error changing start level")
                }
            }
        }
        fireFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, *listeners)
    }

    fun startLevelChanging(oldStartLevel: Int, newStartLevel: Int, vararg listeners: FrameworkListener?) {
        if (oldStartLevel > newStartLevel) {
            for (bundle in getBundlesByStartLevel(oldStartLevel)) {
                try {
                    bundle.stop(STOP_TRANSIENT)
                } catch (t: Throwable) {
                    if (t is ThreadDeath) throw t
                    Timber.e(t, "Error changing start level")
                }
            }
        }
    }

    override fun stateChanged(oldState: Int, newState: Int) {
        when (newState) {
            Framework.RESOLVED -> {
                if (eventDispatcher != null) {
                    eventDispatcher!!.stop()
                    eventDispatcher = null
                }
                synchronized(this) {
                    if (frameworkStartLevel != null) {
                        frameworkStartLevel!!.stop()
                        frameworkStartLevel = null
                    }
                }
            }
            Framework.STARTING -> eventDispatcher = EventDispatcher()
        }
        super.stateChanged(oldState, newState)
    }

    @Throws(BundleException::class)
    override fun stop(options: Int) {
        val frameworkStartLevel = adapt(FrameworkStartLevel::class.java) as FrameworkStartLevelImpl
        object : Thread(javaClass.name + ".stop") {
            override fun run() {
                val framework = this@FrameworkImpl
                framework.state = Framework.STOPPING
                val listener = object : FrameworkListener {
                    override fun frameworkEvent(event: FrameworkEvent) {
                        synchronized(this) { (this as Object).notifyAll() }
                    }
                }
                frameworkStartLevel.internalSetStartLevel(0, listener)
                synchronized(listener) {
                    var interrupted = false
                    while (frameworkStartLevel.startLevel != 0) try {
                        (listener as Object).wait()
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                    if (interrupted) currentThread().interrupt()
                }
                framework.state = Framework.RESOLVED
                // Kills the process to clear static fields before next restart
                exitProcess(0)
            }
        }.start()
    }

    fun unregisterService(origin: BundleImpl?, serviceRegistration: ServiceRegistration<*>) {
        var removed: Boolean
        synchronized(serviceRegistrations) { removed = serviceRegistrations.remove(serviceRegistration) }
        if (removed) {
            fireServiceEvent(ServiceEvent.UNREGISTERING, serviceRegistration.reference)
        } else {
            // Just log an warning, do not throw?
            Timber.w("%s is not registered with ServiceRegistration", serviceRegistration)
            throw IllegalStateException("serviceRegistrations")
        }
    }

    override fun getRegisteredServices(): Array<ServiceReference<*>?> {
        // cmeng: ArrayIndexOutOfBoundsException: length=40; index=40 cause contactList to be empty
        synchronized(serviceRegistrations) {
            val references = arrayOfNulls<ServiceReference<*>?>(serviceRegistrations.size)
            for (i in serviceRegistrations.indices) {
                references[i] = serviceRegistrations[i].reference
            }
            return references
        }
    }

    @Throws(InterruptedException::class)
    override fun waitForStop(timeout: Long): FrameworkEvent? {
        // TODO Auto-generated method stub
        return null
    }
}