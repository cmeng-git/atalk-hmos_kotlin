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
package net.java.sip.communicator.impl.resources

import net.java.sip.communicator.util.ServiceUtils.getService
import net.java.sip.communicator.util.SimpleServiceActivator
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext

/**
 * Starts Resource Management Service.
 *
 * @author Damian Minkov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
/**
 * Creates new instance of `ResourceManagementActivator`
 */
class ResourceManagementActivator
    : SimpleServiceActivator<ResourceManagementServiceImpl?>(ResourceManagementService::class.java, "Resource manager") {

    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        super.start(bc)
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the osgi bundle context
     * @throws Exception
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        bundleContext.removeServiceListener(serviceImpl)
    }

    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): ResourceManagementServiceImpl {
        return ResourceManagementServiceImpl()
    }

    companion object {
        var bundleContext: BundleContext? = null

        /**
         * Returns the `ConfigurationService` obtained from the bundle context.
         *
         * @return the `ConfigurationService` obtained from the bundle context
         */
        var configService: ConfigurationService? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, ConfigurationService::class.java)
                }
                return field
            }
            private set
    }
}