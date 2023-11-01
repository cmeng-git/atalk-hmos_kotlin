/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp

import gnu.java.zrtp.ZrtpConfigure
import gnu.java.zrtp.ZrtpConstants.*
import java.lang.Enum

/**
 * <a herf="https://xmpp.org/extensions/xep-0262.html"></a>XEP-0262: Use of ZRTP in Jingle RTP Sessions 1.0 (2011-06-15)
 */
object ZrtpConfigureUtils {
    private fun <T : Enum<T>> getPropertyID(algo: T): String {
        val clazz: Class<T> = algo.getDeclaringClass()
        return "net.java.sip.communicator." + clazz.name.replace('$', '_')
    }
    // setupConfigure(ZrtpConstants.SupportedPubKeys.DH2K, active);
    // setupConfigure(ZrtpConstants.SupportedHashes.S256, active);
    // setupConfigure(ZrtpConstants.SupportedSymCiphers.AES1, active);
    // setupConfigure(ZrtpConstants.SupportedSASTypes.B32, active);
    // setupConfigure(ZrtpConstants.SupportedAuthLengths.HS32, active);
    // mandatory v1.10
    // mandatory v1.10
    // mandatory v1.10
    // mandatory v1.10
    /**
     * Improvement made by: MilanKrai on 20200228
     *
     * Upgrade crypto algorithms used in ZRTP to stronger versions:
     * Enable use of SHA-2 384
     *
     * Prefer use of 256 bit ciphers AES-256 and TWOFISH-256
     * For technical details see paper:
     * Daniel J. Bernstein. "Understanding brute force."
     * ECRYPT STVL Workshop on Symmetric Key Encryption.
     * {@see} https://cr.yp.to/snuffle/bruteforce-20050425.pdf
     *
     *
     * Enable elliptic curve crypto using Curve 25519.
     * See the recommendations in paper
     * "Imperfect Forward Secrecy: How Diffie-Hellman Fails in Practice"
     * https://weakdh.org/imperfect-forward-secrecy-ccs15.pdf
     * https://cr.yp.to/newelliptic/nistecc-20160106.pdf
     *
     * cmeng (20200626)
     * {@see} ZRTP: Media Path Key Agreement for Unicast Secure RTP
     * https://tools.ietf.org/html/rfc6189
     * 5.1.3.  Cipher Type Block
     * All ZRTP endpoints MUST support AES-128 (AES1) and MAY support AES-
     * 192 (AES2), AES-256 (AES3), or other Cipher Types. The Advanced
     * Encryption Standard is defined in [FIPS-197].
     *
     * 5.1.4.  Auth Tag Type Block
     * All ZRTP endpoints MUST support HMAC-SHA1 authentication tags for
     * SRTP, with both 32-bit and 80-bit length tags as defined in [RFC3711].
     *
     * 5.1.5.  Key Agreement Type Block
     * All ZRTP endpoints MUST support DH3k, SHOULD support Preshared, and
     * MAY support EC25, EC38, and DH2k.
     *
     * 5.1.6.  SAS Type Block
     * All ZRTP endpoints MUST support the base32 and MAY support the
     * base256 rendering schemes for the Short Authentication String, and
     * other SAS rendering schemes.  See Section 4.5.2 for how the sasvalue
     * is computed and Section 7 for how the SAS is used.
     *
     * Use the longer HS80 and SK64 MACs.
     *
     * @return ZrtpConfigure
     */
    val zrtpConfiguration: ZrtpConfigure
        get() {
            val active = ZrtpConfigure()

            // setupConfigure(ZrtpConstants.SupportedPubKeys.DH2K, active);
            // setupConfigure(ZrtpConstants.SupportedHashes.S256, active);
            // setupConfigure(ZrtpConstants.SupportedSymCiphers.AES1, active);
            // setupConfigure(ZrtpConstants.SupportedSASTypes.B32, active);
            // setupConfigure(ZrtpConstants.SupportedAuthLengths.HS32, active);
            active.addAlgo(SupportedHashes.S384)
            active.addAlgo(SupportedSymCiphers.AES3)
            active.addAlgo(SupportedSymCiphers.TWO3)
            active.addAlgo(SupportedSymCiphers.AES1) // mandatory v1.10
            active.addAlgo(SupportedPubKeys.E255)
            active.addAlgo(SupportedPubKeys.MULT)
            active.addAlgo(SupportedPubKeys.DH3K) // mandatory v1.10
            active.addAlgo(SupportedAuthLengths.HS80)
            active.addAlgo(SupportedAuthLengths.SK64)
            active.addAlgo(SupportedAuthLengths.HS32) // mandatory v1.10
            active.addAlgo(SupportedSASTypes.B32) // mandatory v1.10
            return active
        }

//    private fun <T : Enum<T>> setupConfigure(algo: T, active: ZrtpConfigure) {
//        val cfg = LibJitsi.configurationService
//        var savedConf: String? = null
//        if (cfg != null) {
//            val id = getPropertyID(algo)
//            savedConf = cfg.getString(id)
//        }
//        if (savedConf == null) savedConf = ""
//        val clazz: Class<T> = algo.getDeclaringClass()
//        val savedAlgos = savedConf.split(";")
//
//        // Configure saved algorithms as active
//        for (str in savedAlgos) {
//            try {
//                val algoEnum = Enum.valueOf(clazz, str)
//                if (algoEnum != null) active.addAlgo(algoEnum)
//            } catch (iae: IllegalArgumentException) {
//                // Ignore it and continue the loop.
//            }
//        }
//    }
}