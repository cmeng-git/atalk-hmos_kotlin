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
package net.java.sip.communicator.service.contactsource

import net.java.sip.communicator.service.protocol.PhoneNumberI18nService
import net.java.sip.communicator.util.ServiceUtils.getService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * @author Damian Minkov
 */
class ContactSourceActivator : BundleActivator {
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        Companion.bundleContext = bundleContext
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    companion object {
        /**
         * OSGi bundle context.
         */
        var bundleContext: BundleContext? = null
        /**
         * Returns the PhoneNumberI18nService.
         *
         * @return returns the PhoneNumberI18nService.
         */
        /**
         * The registered PhoneNumberI18nService.
         */
        var phoneNumberI18nService: PhoneNumberI18nService? = null
            get() {
                if (field == null) {
                    field = getService(
                            bundleContext,
                            PhoneNumberI18nService::class.java)
                }
                return field
            }
            private set
    }
}