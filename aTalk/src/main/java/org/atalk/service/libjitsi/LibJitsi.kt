/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.libjitsi

import org.atalk.impl.libjitsi.LibJitsiImpl
import org.atalk.impl.libjitsi.LibJitsiOSGiImpl
import org.atalk.service.audionotifier.AudioNotifierService
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.neomedia.MediaService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Represents the entry point of the `LibJitsi` library.
 *
 * The [.start] method is to be called to initialize/start the use of
 * the library. Respectively, the [.stop] method is to be called to
 * uninitialize/stop the use of the library (i.e. to release the resources
 * acquired by the library during its execution). The `getXXXService()`
 * methods may be called only after the `start()` method returns
 * successfully and before the `stop()` method is called.
 *
 * The `libjitsi` library may be utilized both with and without OSGi. If
 * the library detects during the execution of the `start()` method that
 * (a) the `LibJitsi` class has been loaded as part of an OSGi `Bundle` and
 * (b) successfully retrieves the associated `BundleContext`, it will look for the references to the
 * implementations of the supported service classes in the retrieved `BundleContext`.
 * Otherwise, the library will stand alone without relying on OSGi functionality. In the case of successful
 * detection of OSGi, the library will not register the supported service class instances in the retrieved `BundleContext`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class LibJitsi
/**
 * Initializes a new `LibJitsi` instance.
 */
protected constructor() {
    /**
     * Gets a service of a specific type associated with this implementation of the `libjitsi` library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association
     * known to this implementation of the `LibJitsi` library; otherwise, `null`
     */
    protected abstract fun <T> getService(serviceClass: Class<T>): T?

    companion object {
        /**
         * The `LibJitsi` instance which is provides the implementation of the `getXXXService` methods.
         */
        private var impl: LibJitsi? = null

        /**
         * Gets the `AudioNotifierService` instance. If no existing
         * `AudioNotifierService` instance is known to the library, tries to
         * initialize a new one. (Such a try to initialize a new instance is
         * performed just once while the library is initialized.)
         *
         * @return the `AudioNotifierService` instance known to the library
         * or `null` if no `AudioNotifierService` instance is known to the library
         */
        val audioNotifierService: AudioNotifierService
            get() = invokeGetServiceOnImpl(AudioNotifierService::class.java)!!

        /**
         * Gets the `ConfigurationService` instance. If no existing
         * `ConfigurationService` instance is known to the library, tries to
         * initialize a new one. (Such a try to initialize a new instance is
         * performed just once while the library is initialized.)
         *
         * @return the `ConfigurationService` instance known to the library
         * or `null` if no `ConfigurationService` instance is known to the library
         */
        val configurationService: ConfigurationService
            get() = invokeGetServiceOnImpl(ConfigurationService::class.java)!!

        /**
         * Gets the `FileAccessService` instance. If no existing
         * `FileAccessService` instance is known to the library, tries to
         * initialize a new one. (Such a try to initialize a new instance is
         * performed just once while the library is initialized.)
         *
         * @return the `FileAccessService` instance known to the library or
         * `null` if no `FileAccessService` instance is known to the library
         */
        val fileAccessService: FileAccessService
            get() = invokeGetServiceOnImpl(FileAccessService::class.java)!!

        /**
         * Gets the `MediaService` instance. If no existing
         * `MediaService` instance is known to the library, tries to
         * initialize a new one. (Such a try to initialize a new instance is
         * performed just once while the library is initialized.)
         *
         * @return the `MediaService` instance known to the library or
         * `null` if no `MediaService` instance is known to the library
         */
        val mediaService: MediaService?
            get() = invokeGetServiceOnImpl(MediaService::class.java)

        /**
         * Gets the `ResourceManagementService` instance. If no existing
         * `ResourceManagementService` instance is known to the library,
         * tries to initialize a new one. (Such a try to initialize a new instance
         * is performed just once while the library is initialized.)
         *
         * @return the `ResourceManagementService` instance known to the library or
         * `null` if no `ResourceManagementService` instance is known to the library.
         */
        val resourceManagementService: ResourceManagementService
            get() = invokeGetServiceOnImpl(ResourceManagementService::class.java)!!

        /**
         * Invokes [.getService] on [.impl].
         *
         * @param serviceClass the class of the service to be retrieved.
         * @return a service of the specified type if such a service is associated with the library.
         * @throws IllegalStateException if the library is not currently initialized.
         */
        private fun <T> invokeGetServiceOnImpl(serviceClass: Class<T>): T? {
            val impl = impl
            return if (impl == null) throw IllegalStateException("impl")
            else
                impl.getService(serviceClass)
        }

        /**
         * Starts/initializes the use of the `LibJitsi` library.
         */
        fun start() {
            start(null)
        }

        /**
         * Starts/initializes the use of the `libjitsi` library.
         *
         * @param context an OSGi [BundleContext].
         */
        fun start(context: BundleContext?): LibJitsi {
            if (null != impl) {
                Timber.d("LibJitsi already started, using as implementation: %s",
                    impl!!.javaClass.canonicalName)
                return impl!!
            }

            /*
             * LibJitsi implements multiple backends and tries to choose the most
             * appropriate at run time. For example, an OSGi-aware backend is used
             * if it is detected that an OSGi implementation is available.
             */
            impl = if (context == null) {
                LibJitsiImpl()
            }
            else {
                LibJitsiOSGiImpl(context)
            }

            Timber.d("Successfully started LibJitsi using implementation: %s", impl!!.javaClass.canonicalName)
            return impl!!
        }

        /**
         * Stops/un-initializes the use of the `LibJitsi` library.
         */
        fun stop() {
            impl = null
        }
    }
}