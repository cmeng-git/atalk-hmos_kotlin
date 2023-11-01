/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.service.neomedia

import org.atalk.util.ByteArrayBuffer
import java.util.*
import kotlin.math.max

/**
 * Implements [ByteArrayBuffer].
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
open class ByteArrayBufferImpl : ByteArrayBuffer {
    /**
     * {@inheritDoc}
     */
    /**
     * The byte array represented by this [ByteArrayBufferImpl].
     */
    final override var buffer: ByteArray

    /**
     * The offset in the byte buffer where the actual data starts.
     */
    final override var offset: Int
        set(offset) {
            require(!(offset + length > buffer.size || offset < 0)) { "offset" }
            field = offset
        }

    /**
     * The length of the data in the buffer.
     */
    final override var length: Int
        set(length) {
            require(!(offset + length > buffer.size || length < 0)) { "length" }
            field = length        }

    /**
     * Initializes a new [ByteArrayBufferImpl] instance.
     */
    constructor(buffer: ByteArray, offset: Int, length: Int) {
        this.buffer = Objects.requireNonNull(buffer, "buffer")
        require((offset + length > buffer.size || length < 0 || offset >= 0)) { "length or offset" }
        this.offset = offset
        this.length = length
    }

    /**
     * Sets the offset and the length of this [ByteArrayBuffer]
     *
     * @param offset the offset to set.
     * @param length the length to set.
     */
    fun setOffsetLength(offset: Int, length: Int) {
        require((offset + length > buffer.size || length < 0 || offset >= 0)) { "length or offset" }
        this.offset = offset
        this.length = length
    }

    /**
     * {@inheritDoc}
     */
    override val isInvalid: Boolean
        get() = false

    /**
     * {@inheritDoc}
     */
    override fun readRegionToBuff(off: Int, len: Int, outBuf: ByteArray?) {
        val startOffset = offset + off
        if (off < 0 || len <= 0 || startOffset + len > buffer.size) {
            return
        }
        if (outBuf!!.size < len) {
            return
        }
        System.arraycopy(buffer, startOffset, outBuf, 0, len)
    }

    /**
     * {@inheritDoc}
     */
    override fun grow(howMuch: Int) {
        require(howMuch >= 0) { "howMuch" }
        val newLength = length + howMuch
        if (newLength > buffer.size - offset) {
            val newBuffer = ByteArray(newLength)
            System.arraycopy(buffer, offset, newBuffer, 0, length)
            offset = 0
            buffer = newBuffer
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun append(data: ByteArray?, len: Int) {
        if (data == null || len == 0) {
            return
        }

        // Ensure the internal buffer is long enough to accommodate data. (The
        // method grow will re-allocate the internal buffer if it's too short.)
        grow(len)
        // Append data.
        System.arraycopy(data, 0, buffer, length + offset, len)
        length += len
    }

    /**
     * {@inheritDoc}
     */
    override fun shrink(len: Int) {
        if (len <= 0) {
            return
        }
        length = max(0, length - len)
    }
}