/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.sdes

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute
import ch.imvs.sdes4j.srtp.SrtpCryptoSuite
import ch.imvs.sdes4j.srtp.SrtpSDesFactory
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.service.neomedia.AbstractSrtpControl
import org.atalk.service.neomedia.SDesControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.util.MediaType
import java.security.SecureRandom

/**
 * Default implementation of [SDesControl] that supports the crypto suites of the original
 * RFC4568 and the KDR parameter, but nothing else.
 *
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 * @author MilanKral
 */
class SDesControlImpl : AbstractSrtpControl<SDesTransformEngine>(SrtpControlType.SDES), SDesControl {
    /**
     * List of enabled crypto suites.
     */
    private val enabledCryptoSuites: MutableList<String> = ArrayList(3)

    /**
     * List of supported crypto suites.
     */
    override val supportedCryptoSuites = ArrayList<String>(7)

    private var attributes: Array<SrtpCryptoAttribute>? = null
    private val sdesFactory: SrtpSDesFactory
    private var selectedInAttribute: SrtpCryptoAttribute? = null
    private var selectedOutAttribute: SrtpCryptoAttribute? = null

    /**
     * SDESControl
     */
    init {
        run {
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_80)
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_32)
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_80)
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_32)
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80)
            enabledCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32)
            enabledCryptoSuites.add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80)
        }
        run {
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80)
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32)
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_80)
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_32)
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_80)
            supportedCryptoSuites.add(SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_32)
            supportedCryptoSuites.add(SrtpCryptoSuite.F8_128_HMAC_SHA1_80)
        }
        sdesFactory = SrtpSDesFactory()
        sdesFactory.setRandomGenerator(SecureRandom())
    }

    override val inAttribute: SrtpCryptoAttribute
        get() {
            return selectedInAttribute!!
        }

    /**
     * Returns the crypto attributes enabled on this computer.
     *
     * @return The crypto attributes enabled on this computer.
     */
    override val initiatorCryptoAttributes: Array<SrtpCryptoAttribute>
        get() {
            initAttributes()
            return attributes!!
        }

    override val outAttribute: SrtpCryptoAttribute
        get() {
            return selectedOutAttribute!!
        }

    override val secureCommunicationStatus: Boolean
        get() {
            return transformEngine != null
        }

//    override val supportedCryptoSuites: Iterable<String>
//        get() {
//            return Collections.unmodifiableList(supportedCryptoSuites)
//        }

    /**
     * Initializes a new `SDesTransformEngine` instance to be associated with and used by this `SDesControlImpl` instance.
     *
     * @return a new `SDesTransformEngine` instance to be associated with and used by this `SDesControlImpl` instance
     * @see AbstractSrtpControl.createTransformEngine
     */
    override fun createTransformEngine(): SDesTransformEngine {
        return SDesTransformEngine(selectedInAttribute, selectedOutAttribute)
    }

    /**
     * Initializes the available SRTP crypto attributes containing: the crypto-suite, the key-param
     * and the session-param.
     */
    private fun initAttributes() {
        if (attributes == null) {
            if (selectedOutAttribute != null) {
                attributes = arrayOf(selectedOutAttribute!!)
                return
            }

            val attributes = ArrayList<SrtpCryptoAttribute>()
            for (i in 0 until enabledCryptoSuites.size) {
                attributes.add(sdesFactory.createCryptoAttribute(i + 1, enabledCryptoSuites[i]))
            }
            this.attributes = attributes.toTypedArray()
        }
    }

    /**
     * Select the local crypto attribute from the initial offering (@see
     * [.getInitiatorCryptoAttributes]) based on the peer's first matching cipher suite.
     *
     * @param peerAttributes The peer's crypto offers.
     * @return A SrtpCryptoAttribute when a matching cipher suite was found; `null`, otherwise.
     */
    override fun initiatorSelectAttribute(peerAttributes: Iterable<SrtpCryptoAttribute>): SrtpCryptoAttribute? {
        for (peerCA in peerAttributes) {
            for (localCA in attributes!!) {
                if (localCA.cryptoSuite == peerCA.cryptoSuite) {
                    selectedInAttribute = peerCA
                    selectedOutAttribute = localCA
                    if (transformEngine != null) {
                        transformEngine!!.update(selectedInAttribute, selectedOutAttribute)
                    }
                    return peerCA
                }
            }
        }
        return null
    }

    /**
     * Returns `true`, SDES always requires the secure transport of its keys.
     *
     * @return `true`
     */
    override fun requiresSecureSignalingTransport(): Boolean {
        return true
    }

    /**
     * Chooses a supported crypto attribute from the peer's list of supplied attributes and creates
     * the local crypto attribute. Used when the control is running in the role as responder.
     *
     * @param peerAttributes The peer's crypto attribute offering.
     * @return The local crypto attribute for the answer of the offer or `null` if no
     * matching cipher suite could be found.
     */
    override fun responderSelectAttribute(peerAttributes: Iterable<SrtpCryptoAttribute>): SrtpCryptoAttribute? {
        for (ea in peerAttributes) {
            for (suite in enabledCryptoSuites) {
                if (suite == ea.cryptoSuite.encode()) {
                    selectedInAttribute = ea
                    selectedOutAttribute = sdesFactory.createCryptoAttribute(ea.tag, suite)
                    if (transformEngine != null) {
                        transformEngine!!.update(selectedInAttribute, selectedOutAttribute)
                    }
                    return selectedOutAttribute
                }
            }
        }
        return null
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of `SDesControlImpl` does nothing because `SDesControlImpl` does not utilize the `RTPConnector`.
     */
    override fun setConnector(connector: AbstractRTPConnector?) {}

    // must trim any leading or training spaces, else cause error
    override fun setEnabledCiphers(ciphers: Iterable<String>) {
        enabledCryptoSuites.clear()
        for (c in ciphers) enabledCryptoSuites.add(c.trim { it <= ' ' })
    }

    override fun start(mediaType: MediaType) {
        val srtpListener = srtpListener!!
        // in srtp the started and security event is one after another in some other security mechanisms
        // e.g. zrtp: there can be started and no security one or security timeout event
        srtpListener.securityNegotiationStarted(mediaType, this)
        srtpListener.securityTurnedOn(mediaType, selectedInAttribute!!.cryptoSuite.encode(), this)
    }
}