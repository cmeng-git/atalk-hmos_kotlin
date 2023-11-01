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

import org.atalk.impl.neomedia.transform.TransformEngineChain.PacketTransformerChain
import org.atalk.service.neomedia.RawPacket
import javax.media.rtp.OutputDataStream

/**
 * Facilitates `OutputDataStream` in the implementation of the interface `TransformOutputStream`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class TransformOutputStreamImpl
/**
 * Initializes a new `TransformOutputStreamImpl` which is to facilitate a specific
 * `OutputDataStream` in the implementation of the interface `TransformOutputStream`
 *
 * @param outputDataStream the `OutputDataStream` the new instance is to facilitate in the implementation
 * of the interface `TransformOutputStream`
 */
    (
        /**
         * The `OutputDataStream` this instance facilitates in the implementation of the interface
         * `TransformOutputStream`.
         */
        private val _outputDataStream: OutputDataStream,
) : AbstractTransformOutputStream() {

    /**
     * The view of [._transformer] as a `TransformEngineChain.PacketTransformerChain`.
     */
    private var _transformerAsChain: PacketTransformerChain? = null

    /**
     * {@inheritDoc}
     */
    override var transformer: PacketTransformer?
        get() = super.transformer
        set(transformer) {
            // var transformer = super.transformer
            if (super.transformer != transformer) {
                super.transformer = transformer
                _transformerAsChain = if (transformer is PacketTransformerChain) transformer else null
            }
        }

    /**
     * Transforms a specified array of `RawPacket`s using the `PacketTransformer` associated with this instance (if any).
     *
     * @param pkts  the `RawPacket`s to transform
     * @param after
     * @return an array of `RawPacket`s which are the result of the transformation of the
     * specified `pkts` using the `PacketTransformer` associated with this instance.
     * If there is no `PacketTransformer` associated with this instance, returns `pkts`.
     */
    fun transform(pkts: Array<RawPacket?>, after: Any?): Array<RawPacket?> {
        if (after != null) {
            val transformerAsChain = _transformerAsChain
            if (transformerAsChain != null) {
                return transformerAsChain.transform(pkts, after as TransformEngine?)
            }
        }
        return transform(pkts)
    }

    /**
     * {@inheritDoc}
     */
    override fun write(buf: ByteArray, off: Int, len: Int): Int {
        return _outputDataStream.write(buf, off, len)
    }
}