/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.libjitsi

import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.service.libjitsi.LibJitsi
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock

/**
 * Represents an implementation of the `libjitsi` library which is stand-alone and does not utilize OSGi.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class LibJitsiImpl : LibJitsi() {
    /**
     * The service instances associated with this implementation of the
     * `libjitsi` library mapped by their respective type/class names.
     */
    private val services = HashMap<String, ServiceLock>()

    /**
     * Initializes a new `LibJitsiImpl` instance.
     */
    init {
        /*
         * The AudioNotifierService implementation uses a non-standard package location so work around it.
         */
        val key = "org.atalk.service.audionotifier.AudioNotifierService"
        val value = System.getProperty(key)
        if (value == null || value.isEmpty()) {
            System.setProperty(key, "org.atalk.impl.neomedia.notify.AudioNotifierServiceImpl")
        }
    }

    /**
     * Get a service of a specific type associated with this implementation of the `libjitsi` library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association known to this
     * implementation of the `libjitsi` library; otherwise, `null`
     */
    override fun <T> getService(serviceClass: Class<T>): T? {
        val className = serviceClass.name
        var lock: ServiceLock?

        synchronized(services) {
            lock = services[className]
            if (lock == null) {
                // Do not allow concurrent and/or repeating requests to create an instance of the specified serviceClass.
                lock = ServiceLock()
                services[className] = lock!!
            }
        }
        return lock!!.getService(className, serviceClass)
    }

    /**
     * Associates an OSGi service `Object` and its initialization with a
     * `Lock` in order to prevent concurrent, repeating, and/or recursive
     * initializations are the same OSGi service `Class`.
     */
    private class ServiceLock {
        /**
         * The `Lock` associated with [._service].
         */
        private val _lock = ReentrantLock()

        /**
         * The OSGi service `Object` associated with [._lock].
         */
        private var _service: Any? = null

        /**
         * Gets the OSGi service `Object` associated with [._lock].
         *
         * @param clazz the runtime type of the returned value
         * @return the OSGi service `Object` associated with [._lock]
         */
        fun <T> getService(className: String, clazz: Class<T>): T? {
            // Do not allow repeating/recursive requests to create multiple instances of the specified clazz.
            val initializeService = !_lock.isHeldByCurrentThread
            if (_service == null && initializeService) {
                _lock.lock()
                try {
                    _service = initializeService(className, clazz)
                } finally {
                    _lock.unlock()
                }
            }
//            else if (!initializeService){
//                Timber.w("getService ###: %s => %s : %s", className, _service, _lock)
//            }
            return _service as T?
        }

        companion object {
            /**
             * Initializes a new instance of a specific OSGi service `Class`.
             *
             * @param <T>
             * @param className the `name` of `clazz` which has already
             * been retrieved from `clazz`
             * @param clazz the `Class` of the OSGi service instance to be initialized
             * @return a new instance of the specified OSGi service `clazz`
            </T> */
            private fun <T> initializeService(className: String, clazz: Class<T>): T? {
                // Allow the service implementation class names to be specified as
                // System properties akin to standard Java class factory names.
                var implClassName = System.getProperty(className)
                var suppressClassNotFoundException = false
                if (implClassName == null || implClassName.isEmpty()) {
                    implClassName = className.replace(".service.", ".impl.") + "Impl"
                    // Nobody has explicitly mentioned implClassName, we have just
                    // made it up. If it turns out that it cannot be found, do not
                    // log the resulting ClassNotFountException in order to not
                    // stress the developers and/or the users.
                    suppressClassNotFoundException = true
                }
                var implClass: Class<*>? = null
                var exception: Throwable? = null

                try {
                    implClass = Class.forName(implClassName)
                } catch (cnfe: ClassNotFoundException) {
                    if (!suppressClassNotFoundException) exception = cnfe
                } catch (eiie: ExceptionInInitializerError) {
                    exception = eiie
                } catch (le: LinkageError) {
                    exception = le
                }

                var service: T? = null
                if (implClass != null && clazz.isAssignableFrom(implClass)) {
                    try {
                        service = implClass.newInstance() as T?
                    } catch (t: Throwable) {
                        if (t is ThreadDeath) {
                            throw t
                        }
                        else {
                            exception = t
                            if (t is InterruptedException) Thread.currentThread().interrupt()
                        }
                    }
                }

                if (exception != null) {
                    Timber.d("Failed to initialize service implementation %s. Will continue without it: %s.",
                            implClassName, exception.message)
                    if (implClassName.contains("MediaServiceImpl")) {
                        aTalkApp.showGenericError(R.string.service_gui_CALL_DISABLE_ON_FAULT, implClassName,
                                exception.message)
                        MainMenuActivity.disableMediaServiceOnFault = true
                    }
                }
                return service
            }
        }
    }
}