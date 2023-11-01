/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.atalk.hmos.gui.contactlist

import net.java.sip.communicator.service.gui.UIContactDetail
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * The `UIContactDetail` implementation
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
open class UIContactDetailImpl : UIContactDetail {
    /**
     * The status icon of this contact detail.
     */
    var statusIcon: ByteArray?

    /**
     * Creates a `UIContactDetailImpl` by specifying the contact `address`, the `displayName` and `preferredProvider`.
     *
     * @param address the contact address
     * @param displayName the contact display name
     * @param statusIcon the status icon of this contact detail
     * @param descriptor the underlying object that this class is wrapping
     */
    constructor(address: String, displayName: String, statusIcon: ByteArray?, descriptor: Any?) : super(address, displayName, null, null, null, null, descriptor) {
        this.statusIcon = statusIcon
    }

    /**
     * Creates a `UIContactDetailImpl` by specifying the contact `address`, the `displayName` and
     * `preferredProvider`.
     *
     * @param address the contact address
     * @param displayName the contact display name
     * @param category the category of the underlying contact detail
     * @param labels the collection of labels associated with this detail
     * @param statusIcon the status icon of this contact detail
     * @param preferredProviders the preferred protocol providers
     * @param preferredProtocols the preferred protocols if no protocol provider is set
     * @param descriptor the underlying object that this class is wrapping
     */
    constructor(address: String, displayName: String, category: String?, labels: Collection<String?>?,
            statusIcon: ByteArray?, preferredProviders: MutableMap<Class<out OperationSet?>, ProtocolProviderService>?,
            preferredProtocols: MutableMap<Class<out OperationSet?>, String>?, descriptor: Any?) : super(address, displayName, category, labels, preferredProviders, preferredProtocols, descriptor) {
        this.statusIcon = statusIcon
    }

    override val presenceStatus: PresenceStatus? = null
}