/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

import org.bouncycastle.crypto.BlockCipher

/**
 * Defines the application programming interface (API) of a factory of
 * `org.bouncycastle.crypto.BlockCipher` instances.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface BlockCipherFactory {
    /**
     * Initializes a new `BlockCipher` instance.
     *
     * @param keySize AES key size (16, 24, 32 bytes)
     * @return a new `BlockCipher` instance
     * @throws Exception if anything goes wrong while initializing a new `BlockCipher` instance.
     */
    @Throws(Exception::class)
    fun createBlockCipher(keySize: Int): BlockCipher
}