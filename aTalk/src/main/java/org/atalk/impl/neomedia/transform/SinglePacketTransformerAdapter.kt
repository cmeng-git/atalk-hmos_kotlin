/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.transform

import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ByteArrayBuffer
import java.util.function.Predicate

/**
 * @author Lyubomir Marinov
 */
open class SinglePacketTransformerAdapter : SinglePacketTransformer {
    /**
     * Ctor.
     */
    constructor() : super()

    /**
     * Ctor.
     *
     * @param packetPredicate the `PacketPredicate` uses to match packets to (reverse) transform.
     */
    constructor(packetPredicate: Predicate<ByteArrayBuffer>) : super(packetPredicate)

    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        return pkt
    }

    override fun transform(pkt: RawPacket): RawPacket? {
        return pkt
    }
}