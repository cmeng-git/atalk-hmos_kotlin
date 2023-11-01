/*
 * Copyright @ 2015 - present 8x8, Inc
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
package org.atalk.util

/**
 * A simple interface that encapsulates all the information needed for byte buffer access.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
interface ByteArrayBuffer {
    /**
     * Gets the byte buffer that supports this instance.
     *
     * @return the byte buffer that supports this instance.
     */
    var buffer: ByteArray
    /**
     * Gets the offset in the byte buffer where the actual data starts.
     *
     * @return the offset in the byte buffer where the actual data starts.
     */
    /**
     * Sets the offset of the data in the buffer.
     *
     * @param off the offset of the data in the buffer.
     */
    var offset: Int
    /**
     * Gets the length of the data in the buffer.
     *
     * @return the length of the data in the buffer.
     */
    /**
     * Sets the length of the data in the buffer.
     *
     * @param len the length of the data in the buffer.
     */
    var length: Int

    /**
     * Perform checks on the byte buffer represented by this instance and
     * return `true` if it is found to be invalid.
     *
     * @return `true` if the byte buffer represented by this
     * instance is found to be invalid, `false` otherwise.
     */
    val isInvalid: Boolean

    /**
     * Copies `len` bytes from the given offset in this buffer to the given `outBuf`
     * @param off the offset relative to the beginning of this buffer's data from where to start copying.
     * @param len the number of bytes to copy.
     * @param outBuf the array to copy to
     */
    fun readRegionToBuff(off: Int, len: Int, outBuf: ByteArray?)

    /**
     * Increases the size of this buffer by `howMuch`. This may result in a new `byte[]` to be allocated
     * if the existing one is not large enough.
     * @param howMuch
     */
    fun grow(howMuch: Int)

    /**
     * Appends `len` bytes from `data` to the end of this buffer. This grows the buffer and as a result
     * a new `byte[]` may be allocated.
     * @param data
     * @param len
     */
    fun append(data: ByteArray?, len: Int)

    /**
     * Shrinks the length of this buffer by `len`
     * @param len
     */
    fun shrink(len: Int)
}