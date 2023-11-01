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
package net.java.sip.communicator.impl.credentialsstorage

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService
import net.java.sip.communicator.service.credentialsstorage.MasterPasswordInputService
import net.java.sip.communicator.util.ServiceUtils
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import timber.log.Timber

/**
 * Activator for the [CredentialsStorageService].
 *
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 */
class CredentialsStorageActivator : BundleActivator {
    /**
     * The [CredentialsStorageService] implementation.
     */
    private var impl: CredentialsStorageServiceImpl? = null

    /**
     * Starts the credentials storage service
     *
     * @param bundleContext the `BundleContext` as provided from the OSGi framework
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
        impl = CredentialsStorageServiceImpl()
        impl!!.start(bundleContext)
        bundleContext.registerService(CredentialsStorageService::class.java.name, impl, null)
        Timber.i("Service Impl: %s [REGISTERED]", javaClass.name)
    }

    /**
     * Unregisters the credentials storage service.
     *
     * @param bundleContext BundleContext
     * @throws Exception if anything goes wrong
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
        impl!!.stop()
        Timber.i("The CredentialsStorageService stop method has been called.")
    }

    companion object {
        /**
         * The [BundleContext].
         */
        private var bundleContext: BundleContext? = null

        /**
         * Returns service to show master password input dialog.
         *
         * @return return master password service to display input dialog.
         */
        fun getMasterPasswordInputService(): MasterPasswordInputService? {
            return ServiceUtils.getService(bundleContext, MasterPasswordInputService::class.java)
        }
    }
}