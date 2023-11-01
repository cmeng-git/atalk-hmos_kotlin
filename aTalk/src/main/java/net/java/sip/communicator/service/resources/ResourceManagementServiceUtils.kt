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
package net.java.sip.communicator.service.resources

import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.resources.ResourceManagementService
import org.osgi.framework.BundleContext
import java.util.*

/**
 * @author Lubomir Marinov
 */
object ResourceManagementServiceUtils {
    /**
     * Constructs a new `Locale` instance from a specific locale
     * identifier which can either be a two-letter language code or contain a
     * two-letter language code and a two-letter country code in the form
     * `<language>_<country>`.
     *
     * @param localeId the locale identifier describing the new `Locale`
     * instance to be created
     * @return a new `Locale` instance with language and country (if
     * specified) matching the given locale identifier
     */
    fun getLocale(localeId: String): Locale {
        val underscoreIndex = localeId.indexOf('_')
        val language: String
        val country: String
        if (underscoreIndex == -1) {
            language = localeId
            country = ""
        } else {
            language = localeId.substring(0, underscoreIndex)
            country = localeId.substring(underscoreIndex + 1)
        }
        return Locale(language, country)
    }

    /**
     * Gets the `ResourceManagementService` instance registered in a
     * specific `BundleContext` (if any).
     *
     * @param bundleContext the `BundleContext` to be checked for a
     * registered `ResourceManagementService`
     * @return a `ResourceManagementService` instance registered in
     * the specified `BundleContext` if any; otherwise, `null`
     */
    fun getService(bundleContext: BundleContext?): ResourceManagementService? {
        return ServiceUtils.getService(bundleContext, ResourceManagementService::class.java)
    }
}