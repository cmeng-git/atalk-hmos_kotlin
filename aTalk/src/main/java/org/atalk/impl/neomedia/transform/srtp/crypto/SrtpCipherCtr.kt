/*
 * Copyright @ 2016 - present 8x8, Inc
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
package org.atalk.impl.neomedia.transform.srtp.crypto

/**
 * SrtpCipherCtr implementations implement SRTP Counter Mode Encryption.
 *
 * SRTP Counter Mode is standard block cipher counter mode with special key and
 * special counter initial value (iv). We only increment last 16 bits of the
 * counter, so we can only encrypt 2^16 * `BLKLEN` of data.
 *
 * SRTP Counter Mode AES Encryption algorithm is defined in RFC3711, section 4.1.1.
 */
abstract class SrtpCipherCtr {
    /**
     * (Re)Initialize the cipher with key
     *
     * @param key the key. key.length == BLKLEN
     */
    abstract fun init(key: ByteArray)

    /**
     * Process (encrypt/decrypt) data from offset for len bytes iv can be
     * modified by this function but you MUST never reuse an IV so it's ok
     *
     * @param data byte array to be processed
     * @param off the offset
     * @param len the length
     * @param iv initial value of the counter (can be modified). iv.length == BLKLEN
     */
    abstract fun process(data: ByteArray, off: Int, len: Int, iv: ByteArray)

    companion object {
        const val BLKLEN = 16

        /**
         * Check the validity of process function arguments
         */
        @JvmStatic
        protected fun checkProcessArgs(data: ByteArray, off: Int, len: Int, iv: ByteArray) {
            require(iv.size == BLKLEN) { "iv.length != BLKLEN" }
            require(off >= 0) { "off < 0" }
            require(len >= 0) { "len < 0" }
            require((off + len) <= data.size) { "off + len > data.length" }
            /*
             * we increment only the last 16 bits of the iv, so we can encrypt
             * a maximum of 2^16 blocks, ie 1048576 bytes
             */
            require(data.size <= 1048576) { "data.length > 1048576" }
        }
    }
}