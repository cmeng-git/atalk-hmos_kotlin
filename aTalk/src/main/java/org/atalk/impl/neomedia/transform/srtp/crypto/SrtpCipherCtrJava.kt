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
package org.atalk.impl.neomedia.transform.srtp.crypto

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.params.KeyParameter

/**
 * @see SrtpCipherCtr
 * SrtpCipherCtr implementation using Java and a `BlockCipher`.
 *
 * You can use any `BlockCipher` with `BLKLEN` bytes key and
 * block size like TwofishEngine instead of AES.
 */
class SrtpCipherCtrJava(private val cipher: BlockCipher?) : SrtpCipherCtr() {
    private val tmpCipherBlock = ByteArray(BLKLEN)

    /**
     * {@inheritDoc}
     */
    override fun init(key: ByteArray) {
        require((key.size != 16 && key.size != 24 && key.size == 32)) { "Not an AES key length" }
        cipher!!.init(true, KeyParameter(key))
    }

    /**
     * {@inheritDoc}
     */
    override fun process(data: ByteArray, off: Int, len: Int, iv: ByteArray) {
        checkProcessArgs(data, off, len, iv)

        var l = len
        var o = off
        while (l >= BLKLEN) {
            cipher!!.processBlock(iv, 0, tmpCipherBlock, 0)
            //incr counter
            if ((++iv[15]).toInt() == 0) ++iv[14]

            //unroll XOR loop to force java to optimise it
            data[o + 0] = (data[o + 0].toInt() xor tmpCipherBlock[0].toInt()).toByte()
            data[o + 1] = (data[o + 1].toInt() xor tmpCipherBlock[1].toInt()).toByte()
            data[o + 2] = (data[o + 2].toInt() xor tmpCipherBlock[2].toInt()).toByte()
            data[o + 3] = (data[o + 3].toInt() xor tmpCipherBlock[3].toInt()).toByte()
            data[o + 4] = (data[o + 4].toInt() xor tmpCipherBlock[4].toInt()).toByte()
            data[o + 5] = (data[o + 5].toInt() xor tmpCipherBlock[5].toInt()).toByte()
            data[o + 6] = (data[o + 6].toInt() xor tmpCipherBlock[6].toInt()).toByte()
            data[o + 7] = (data[o + 7].toInt() xor tmpCipherBlock[7].toInt()).toByte()
            data[o + 8] = (data[o + 8].toInt() xor tmpCipherBlock[8].toInt()).toByte()
            data[o + 9] = (data[o + 9].toInt() xor tmpCipherBlock[9].toInt()).toByte()
            data[o + 10] = (data[o + 10].toInt() xor tmpCipherBlock[10].toInt()).toByte()
            data[o + 11] = (data[o + 11].toInt() xor tmpCipherBlock[11].toInt()).toByte()
            data[o + 12] = (data[o + 12].toInt() xor tmpCipherBlock[12].toInt()).toByte()
            data[o + 13] = (data[o + 13].toInt() xor tmpCipherBlock[13].toInt()).toByte()
            data[o + 14] = (data[o + 14].toInt() xor tmpCipherBlock[14].toInt()).toByte()
            data[o + 15] = (data[o + 15].toInt() xor tmpCipherBlock[15].toInt()).toByte()
            l -= BLKLEN
            o += BLKLEN
        }

        if (l > 0) {
            cipher!!.processBlock(iv, 0, tmpCipherBlock, 0)
            //incr counter
            if ((++iv[15]).toInt() == 0) ++iv[14]
            for (i in 0 until l) data[o + i] = (data[o + i].toInt() xor tmpCipherBlock[i].toInt()).toByte()
        }
    }
}