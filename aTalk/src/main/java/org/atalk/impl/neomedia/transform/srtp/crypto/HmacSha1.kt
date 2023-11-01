/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp.crypto

import org.bouncycastle.crypto.Mac
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.macs.HMac

/**
 * Implements a factory for an HMAC-SHA1 `org.bouncycastle.crypto.Mac`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object HmacSha1 {
    /**
     * Initializes a new `org.bouncycastle.crypto.Mac` instance which
     * implements a keyed-hash message authentication code (HMAC) with SHA-1.
     *
     * @return a new `org.bouncycastle.crypto.Mac` instance which
     * implements a keyed-hash message authentication code (HMAC) with SHA-1
     */
    fun createMac(): Mac {
        return if (OpenSslWrapperLoader.isLoaded) {
            OpenSslHmac(OpenSslHmac.SHA1)
        } else {
            // Fallback to BouncyCastle.
            HMac(SHA1Digest())
        }
    }
}