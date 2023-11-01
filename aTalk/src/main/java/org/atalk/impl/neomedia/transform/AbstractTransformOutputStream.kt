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

/**
 * Facilitates the implementation of the interface `TransformOutputStream` .
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractTransformOutputStream : TransformOutputStream {
    /**
     * {@inheritDoc}
     */
    /**
     * {@inheritDoc}
     */
    /**
     * The `PacketTransformer` used by this instance to transform `RawPacket`s.
     */
    override var transformer: PacketTransformer? = null

    /**
     * Transforms a specified array of `RawPacket`s using the `PacketTransformer`
     * associated with this instance (if any).
     *
     * @param pkts the `RawPacket`s to transform
     * @return an array of `RawPacket`s which are the result of the transformation of the
     * specified `pkts` using the `PacketTransformer` associated with this instance.
     * If there is no `PacketTransformer` associated with this instance, returns `pkts`.
     */
    protected fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        var pkts = pkts

        val transformer = transformer
        if (transformer != null) {
            pkts = transformer.transform(pkts)!!
        }
        return pkts
    }
}