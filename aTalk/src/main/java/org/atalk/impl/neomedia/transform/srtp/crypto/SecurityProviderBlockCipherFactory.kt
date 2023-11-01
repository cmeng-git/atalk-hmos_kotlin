/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

import org.bouncycastle.crypto.BlockCipher
import java.security.Provider
import java.security.Security
import javax.crypto.Cipher

/**
 * Implements a `BlockCipherFactory` which initializes `BlockCipher`s that are
 * implemented by a `java.security.Provider`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class SecurityProviderBlockCipherFactory(transformation: String?, provider: Provider?) : BlockCipherFactory {
    /**
     * The `java.security.Provider` which provides the implementations of the
     * `BlockCipher`s to be initialized by this instance.
     */
    private val provider: Provider

    /**
     * The name of the transformation.
     */
    private val transformation: String

    /**
     * Initializes a new `SecurityProvider` instance which is to initialize
     * `BlockCipher`s that are implemented by a specific `java.security.Provider`.
     */
    init {
        if (transformation == null) throw NullPointerException("transformation")
        require(transformation.isNotEmpty()) { "transformation" }
        if (provider == null) throw NullPointerException("provider")

        this.transformation = transformation
        this.provider = provider
    }

    /**
     * Initializes a new `SecurityProvider` instance which is to initialize
     * `BlockCipher`s that are implemented by a specific `java.security.Provider`.
     *
     * @param transformation the name of the transformation
     * @param providerName the name of the `java.security.Provider` which provides the implementations of
     * the `BlockCipher`s to be initialized by the new instance.
     */
    constructor(transformation: String?, providerName: String?) : this(transformation, Security.getProvider(providerName))

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun createBlockCipher(keySize: Int): BlockCipher {
        return BlockCipherAdapter(
                Cipher.getInstance(transformation.replaceFirst("<size>".toRegex(), (keySize * 8).toString()), provider)
        )
    }
}