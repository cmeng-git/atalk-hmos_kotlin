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

/**
 * Default Skin Pack interface.
 *
 * @author Adam Netocny
 */
interface SkinPack : ResourcePack {
    /**
     * Returns a `Map`, containing all [key, value] pairs for image
     * resource pack.
     *
     * @return a `Map`, containing all [key, value] pairs for image
     * resource pack.
     */
    fun getImageResources(): Map<String?, String?>?

    /**
     * Returns a `Map`, containing all [key, value] pairs for style
     * resource pack.
     *
     * @return a `Map`, containing all [key, value] pairs for style
     * resource pack.
     */
    fun getStyleResources(): Map<String?, String?>?

    /**
     * Returns a `Map`, containing all [key, value] pairs for color
     * resource pack.
     *
     * @return a `Map`, containing all [key, value] pairs for color
     * resource pack.
     */
    fun getColorResources(): Map<String?, String?>?

    /**
     * Returns a `Map`, containing all [key, value] pairs for settings
     * resource pack.
     *
     * @return a `Map`, containing all [key, value] pairs for settings
     * resource pack.
     */
    fun getSettingsResources(): Map<String?, String?>?

    companion object {
        /**
         * Default resource name.
         */
        const val RESOURCE_NAME_DEFAULT_VALUE = "SkinPack"
    }
}