/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

/**
 * The `ContactResourceListener` listens for events related to `ContactResource`-s. It
 * is notified each time a `ContactResource` has been added, removed or modified.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface ContactResourceListener {
    /**
     * Called when a new `ContactResource` has been added to the list of available `Contact` resources.
     *
     * @param event the `ContactResourceEvent` that notified us
     */
    fun contactResourceAdded(event: ContactResourceEvent?)

    /**
     * Called when a `ContactResource` has been removed to the list of available `Contact` resources.
     *
     * @param event the `ContactResourceEvent` that notified us
     */
    fun contactResourceRemoved(event: ContactResourceEvent?)

    /**
     * Called when a `ContactResource` in the list of available `Contact` resources has been modified.
     *
     * @param event the `ContactResourceEvent` that notified us
     */
    fun contactResourceModified(event: ContactResourceEvent?)
}