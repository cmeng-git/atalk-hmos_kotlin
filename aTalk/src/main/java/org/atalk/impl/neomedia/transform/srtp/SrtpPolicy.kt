/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.srtp

/**
 * SRTPPolicy holds the SRTP encryption / authentication policy of a SRTP session.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
class SrtpPolicy
/**
 * Construct a SrtpPolicy object based on given parameters.
 * This class acts as a storage class, so all the parameters are passed in
 * through this constructor.
 *
 * @param encType SRTP encryption type
 * @param encKeyLength SRTP encryption key length
 * @param authType SRTP authentication type
 * @param authKeyLength SRTP authentication key length
 * @param authTagLength SRTP authentication tag length
 * @param saltKeyLength SRTP salt key length
 */
(
        /**
         * SRTP encryption type
         */
        var encType: Int,
        /**
         * SRTP encryption key length
         */
        var encKeyLength: Int,
        /**
         * SRTP authentication type
         */
        var authType: Int,
        /**
         * SRTP authentication key length
         */
        var authKeyLength: Int,
        /**
         * SRTP authentication tag length
         */
        var authTagLength: Int,
        /**
         * SRTP salt key length
         */
        var saltKeyLength: Int) {
    /**
     * Get the encryption type
     *
     * @return the encryption type
     */
    /**
     * Set the encryption type
     *
     * @param encType encryption type
     */
    /**
     * Get the encryption key length
     *
     * @return the encryption key length
     */
    /**
     * Set the encryption key length
     *
     * @param encKeyLength the encryption key length
     */
    /**
     * Get the authentication type
     *
     * @return the authentication type
     */
    /**
     * Set the authentication type
     *
     * @param authType the authentication type
     */
    /**
     * Get the authentication key length
     *
     * @return the authentication key length
     */
    /**
     * Set the authentication key length
     *
     * @param authKeyLength the authentication key length
     */
    /**
     * Get the authentication tag length
     *
     * @return the authentication tag length
     */
    /**
     * Set the authentication tag length
     *
     * @param authTagLength the authentication tag length
     */
    /**
     * Get the salt key length
     *
     * @return the salt key length
     */
    /**
     * Set the salt key length
     *
     * @param keyLength the salt key length
     */
    /**
     * Get whether send-side RTP replay protection is enabled.
     *
     * @see .isSendReplayDisabled
     */
    /**
     * Set whether send-side RTP replay protection is to be enabled.
     *
     * Turn this off if you need to send identical packets more than once (e.g., retransmission to a peer that
     * does not support the rtx payload.)  **Note**: Never re-send a packet with a different payload!
     *
     * @param enabled `true` if send-side replay protection is to be enabled; `false` if not.
     */
    /**
     * Whether send-side replay protection is enabled
     */
    var isSendReplayEnabled = true
    /**
     * Get whether receive-side RTP replay protection is enabled.
     *
     * @see .isReceiveReplayDisabled
     */
    /**
     * Set whether receive-side RTP replay protection is to be enabled.
     *
     * Turn this off if you need to be able to receive identical packets more than once (e.g., if you are
     * an RTP translator, with peers that are doing retransmission without using the rtx payload.)
     * **Note**: You must make sure your packet handling is idempotent!
     *
     * @param enabled `true` if receive-side replay protection is to be enabled; `false` if not.
     */
    /**
     * Whether receive-side replay protection is enabled
     */
    var isReceiveReplayEnabled = true

    /**
     * Get whether send-side RTP replay protection is disabled.
     *
     * @see .isSendReplayEnabled
     */
    val isSendReplayDisabled: Boolean
        get() = !isSendReplayEnabled

    /**
     * Get whether receive-side RTP replay protection is enabled.
     *
     * @see .isReceiveReplayEnabled
     */
    val isReceiveReplayDisabled: Boolean
        get() = !isReceiveReplayEnabled

    companion object {
        /**
         * Null Cipher, does not change the content of RTP payload
         */
        const val NULL_ENCRYPTION = 0

        /**
         * Counter Mode AES Cipher, defined in Section 4.1.1, RFC3711
         */
        const val AESCM_ENCRYPTION = 1

        /**
         * Counter Mode TwoFish Cipher
         */
        const val TWOFISH_ENCRYPTION = 3

        /**
         * F8 mode AES Cipher, defined in Section 4.1.2, RFC 3711
         */
        const val AESF8_ENCRYPTION = 2

        /**
         * F8 Mode TwoFish Cipher
         */
        const val TWOFISHF8_ENCRYPTION = 4

        /**
         * Null Authentication, no authentication
         */
        const val NULL_AUTHENTICATION = 0

        /**
         * HAMC SHA1 Authentication, defined in Section 4.2.1, RFC3711
         */
        const val HMACSHA1_AUTHENTICATION = 1

        /**
         * Skein Authentication
         */
        const val SKEIN_AUTHENTICATION = 2
    }
}