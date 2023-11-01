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

import java.util.*

/**
 * @author Damian Minkov
 */
interface LanguagePack : ResourcePack {
    /**
     * Returns a `Map`, containing all [key, value] pairs for the given
     * locale.
     *
     * @param locale The `Locale` we're looking for.
     * @return a `Map`, containing all [key, value] pairs for the given
     * locale.
     */
    fun getResources(locale: Locale?): MutableMap<String?, String?>?

    /**
     * Returns a Set of the keys contained only in the ResourceBundle for
     * locale.
     * @param locale the locale for which the keys are requested
     * @return a Set of the keys contained only in the ResourceBundle for
     * locale
     */
    fun getResourceKeys(locale: Locale?): Set<String?>?

    /**
     * All the locales in the language pack.
     * @return all the locales this Language pack contains.
     */
    fun getAvailableLocales(): Iterator<Locale?>?

    companion object {
        const val RESOURCE_NAME_DEFAULT_VALUE = "DefaultLanguagePack"
    }
}