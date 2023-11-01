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
package net.java.sip.communicator.impl.fileaccess

import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.libjitsi.LibJitsi
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * Invoke "Service Binder" to parse the service XML and register all services.
 *
 * @author Alexander Pelov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class FileAccessActivator : BundleActivator {
    /**
     * Initialize and start file service
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if initializing and starting file service fails
     */
    @Throws(Exception::class)
    public override fun start(bundleContext: BundleContext) {
        val fileAccessService = LibJitsi.fileAccessService
        if (fileAccessService != null) {
            bundleContext.registerService(FileAccessService::class.java.name, fileAccessService, null)
        }
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the `BundleContext`
     * @throws Exception if the stop operation goes wrong
     */
    @Throws(Exception::class)
    public override fun stop(bundleContext: BundleContext) {
    }
}