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

import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.ProtocolProviderService

/**
 * The `ProtocolAwareContactSourceService` extends the basic
 * `ContactSourceService` interface to provide a protocol aware contact
 * source. In other words a preferred `ProtocolProviderService` can be
 * set for a given `OperationSet` class that would affect the query
 * result by excluding source contacts that has a preferred provider different
 * from the one specified as a preferred provider.
 *
 * @author Yana Stamcheva
 */
interface ProtocolAwareContactSourceService : ContactSourceService {
    /**
     * Sets the preferred protocol provider for this contact source. The
     * preferred `ProtocolProviderService` set for a given
     * `OperationSet` class would affect the query result by excluding
     * source contacts that has a preferred provider different from the one
     * specified here.
     *
     * @param opSetClass the `OperationSet` class, for which the
     * preferred provider is set
     * @param protocolProvider the `ProtocolProviderService` to set
     */
    fun setPreferredProtocolProvider(
            opSetClass: Class<out OperationSet?>?,
            protocolProvider: ProtocolProviderService?)
}