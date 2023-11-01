/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist

import net.java.sip.communicator.service.contactlist.MetaContactListService
import net.java.sip.communicator.service.protocol.AccountManager
import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ContactlistActivator : BundleActivator {
    private var mclServiceImpl: MetaContactListServiceImpl? = null

    /**
     * Called when this bundle is started.
     *
     * @param context The execution context of the bundle being started.
     * @throws Exception If
     */
    @Throws(Exception::class)
    override fun start(context: BundleContext) {
        bundleContext = context
        mclServiceImpl = MetaContactListServiceImpl()

        // reg the icq account man.
        context.registerService(MetaContactListService::class.java.name, mclServiceImpl, null)
        mclServiceImpl!!.start(context)
        Timber.d("Service Impl: %s [REGISTERED]", javaClass.name)
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the bundle-specific activities necessary to stop the bundle.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the Framework will remove the bundle's
     * listeners, unregister all services registered by the bundle, and release all services used by the bundle.
     */
    @Throws(Exception::class)
    override fun stop(context: BundleContext) {
        Timber.log(TimberLog.FINER, "Stopping the contact list.")
        if (mclServiceImpl != null) mclServiceImpl!!.stop(context)
    }

    companion object {
        /**
         * Returns the `FileAccessService` obtained from the bundle context.
         *
         * @return the `FileAccessService` obtained from the bundle context
         */
        var fileAccessService: FileAccessService? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, FileAccessService::class.java)
                }
                return field
            }
            private set

        /**
         * Returns the `AccountManager` obtained from the bundle context.
         *
         * @return the `AccountManager` obtained from the bundle context
         */
        var accountManager: AccountManager? = null
            get() {
                if (field == null) {
                    field = getService(bundleContext, AccountManager::class.java)
                }
                return field
            }
            private set
        private var resourcesService: ResourceManagementService? = null
        private var bundleContext: BundleContext? = null

        /**
         * Returns the `ResourceManagementService`, through which we will access all resources.
         *
         * @return the `ResourceManagementService`, through which we will access all resources.
         */
        val resources: ResourceManagementService?
            get() {
                if (resourcesService == null) {
                    resourcesService = getService(bundleContext, ResourceManagementService::class.java)
                }
                return resourcesService
            }
    }
}