/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.plugin.loggingutils

import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.log.LogUploadService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration

/**
 * Creates and registers logging config form.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class LoggingUtilsActivator : BundleActivator {
    /**
     * The Log Upload service registration.
     */
    private var logUploadServReg: ServiceRegistration<*>? = null

    /**
     * `LogUploadService` impl instance for android.
     */
    private var logUploadImpl: LogUploadServiceImpl? = null

    /**
     * Creates and register logging configuration.
     *
     * @param bundleContext OSGI bundle context
     * @throws Exception if error creating configuration.
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        configurationService!!.setProperty(DISABLED_PROP, "true")
        logUploadImpl = LogUploadServiceImpl()
        logUploadServReg = bundleContext.registerService(LogUploadService::class.java.name, logUploadImpl, null)
    }

    /**
     * Stops the Logging utils bundle
     *
     * @param bundleContext the OSGI bundle context
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        logUploadServReg!!.unregister()
        logUploadImpl!!.dispose()
    }

    companion object {
        /**
         * The OSGI bundle context.
         */
        private var bundleContext: BundleContext? = null
        /**
         * Returns the `ResourceManagementService` obtained from the bundle context.
         *
         * @return the `ResourceManagementService` obtained from the bundle context
         */
        /**
         * The resource service.
         */
        var resourceService: ResourceManagementService? = null
            get() {
                if (field == null) {
                    val resourceReference = bundleContext!!.getServiceReference(ResourceManagementService::class.java.name)
                    field = bundleContext!!.getService(resourceReference as ServiceReference<Any>) as ResourceManagementService
                }
                return field
            }
            private set
        /**
         * Returns a reference to a ConfigurationService implementation currently
         * registered in the bundle context or null if no such implementation was found.
         *
         * @return a currently valid implementation of the ConfigurationService.
         */
        /**
         * The configuration service.
         */
        var configurationService: ConfigurationService? = null
            get() {
                if (field == null) {
                    val serviceReference = bundleContext!!.getServiceReference(ConfigurationService::class.java.name)
                    field = bundleContext!!.getService(serviceReference as ServiceReference<Any>) as ConfigurationService
                }
                return field
            }
            private set
        /*
     * (cmeng: for android)
     * Returns a reference to a FileAccessService implementation currently registered in the bundle context
      * or null if no such implementation was found.
     *
     * @return a currently valid implementation of the FileAccessService .
     *
     * Returns the <code>FileAccessService</code> obtained from the bundle context.
     *
     * @return the <code>FileAccessService</code> obtained from the bundle context
     */
        /**
         * The service giving access to files.
         */
        var fileAccessService: FileAccessService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, FileAccessService::class.java)
                }
                return field
            }
            private set
        /**
         * Returns the `NotificationService` obtained from the bundle context.
         *
         * @return the `NotificationService` obtained from the bundle context
         */
        /**
         * Notification service.
         */
        var notificationService: NotificationService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, NotificationService::class.java)
                }
                return field
            }
            private set

        /**
         * Indicates if the logging configuration form should be disabled, i.e. not visible to the user.
         */
        private const val DISABLED_PROP = "loggingconfig.DISABLED"
    }
}