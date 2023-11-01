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

/**
 * Wraps a `TransformerEngine` (allows the wrapped instance to be swapped without
 * modifications to the `RTPConnector`'s transformer engine chain.
 *
 * @author George Politis
 * @author Lyubomir Marinov
 */
class TransformEngineWrapper<T : TransformEngine> : TransformEngine {
    /**
     * The wrapped instance.
     */
    var wrapped: T? = null

    /**
     * Determines whether this `TransformEngineWrapper` contains a specific `TransformEngine`.
     *
     * @param t the `TransofmrEngine` to check whether it is contained in this `TransformEngineWrapper`
     * @return `true` if `t` equals [.wrapped] or `t` is contained in the
     * `chain` of `wrapped` (if `wrapped` is a `TransformEngineChain`); otherwise, `false`
     */
    operator fun contains(t: T): Boolean {
        val wrapped = wrapped
        if (t == wrapped) {
            return true
        } else if (wrapped is TransformEngineChain) {
            val chain = wrapped.getEngineChain()
            if (chain.isNotEmpty()) {
                for (c in chain) {
                    if (t == c) return true
                }
            }
        }
        return false
    }

    /**
     * {@inheritDoc}
     */
    override val rtcpTransformer: PacketTransformer?
        get() {
            return wrapped?.rtcpTransformer
        }

    /**
     * {@inheritDoc}
     */
    override val rtpTransformer: PacketTransformer?
        get() {
            return wrapped?.rtpTransformer
        }
}