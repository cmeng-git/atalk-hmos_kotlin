/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp

import gnu.java.zrtp.ZrtpCodes.MessageSeverity
import gnu.java.zrtp.utils.ZrtpUtils
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.service.neomedia.AbstractSrtpControl
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.service.neomedia.ZrtpControl
import org.atalk.util.MediaType
import org.jxmpp.jid.BareJid
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.*

/**
 * Controls zrtp in the MediaStream.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 * @author MilanKral
 */
class ZrtpControlImpl
/**
 * Creates the control.
 */
(var myZid: ByteArray) : AbstractSrtpControl<ZRTPTransformEngine>(SrtpControlType.ZRTP), ZrtpControl {
    /**
     * Additional info codes for and data to support ZRTP4J. These could be added to the library.
     * However they are specific for this implementation, needing them for various GUI changes.
     */
    enum class ZRTPCustomInfoCodes {
        ZRTPDisabledByCallEnd, ZRTPEnabledByDefault, ZRTPEngineInitFailure, ZRTPNotEnabledByUser
    }

    /**
     * Whether current is master session.
     */
    private var masterSession = false

    /**
     * This is the connector, required to send ZRTP packets via the DatagramSocket.
     */
    private var zrtpConnector: AbstractRTPConnector? = null

    /**
     * Cleans up the current zrtp control and its engine.
     */
    public override fun doCleanup() {
        super.doCleanup()
        zrtpConnector = null
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getCiperString()
     */
    override val cipherString: String?
        get() {
            return transformEngine!!.userCallback!!.cipherString
        }

    /**
     * Get negotiated ZRTP protocol version.
     *
     * @return the integer representation of the negotiated ZRTP protocol version.
     */
    override val currentProtocolVersion: Int
        get() {
            val zrtpEngine = transformEngine
            return zrtpEngine?.currentProtocolVersion ?: 0
        }

    /**
     * Return the zrtp hello hash String.
     *
     * @param index Hello hash of the Hello packet identified by index. Index must be
     * 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return String the zrtp hello hash.
     */
    override fun getHelloHash(index: Int): String {
        return transformEngine!!.getHelloHash(index)
    }

    /**
     * Get the ZRTP Hello Hash data - separate strings.
     *
     * @param index Hello hash of the Hello packet identified by index.
     * Index must be 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return String array containing the version string at offset 0, the Hello hash value as
     * hex-digits at offset 1. Hello hash is available immediately after class
     * instantiation. Returns `null` if ZRTP is not available.
     */
    override fun getHelloHashSep(index: Int): Array<String>? {
        return transformEngine!!.getHelloHashSep(index)
    }

    /**
     * Get number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    override val numberSupportedVersions: Int
        get() {
            val zrtpEngine = transformEngine
            return zrtpEngine?.numberSupportedVersions ?: 0
        }

    /**
     * Get the peer's Hello Hash data.
     *
     * Use this method to get the peer's Hello Hash data. The method returns the data as a string.
     *
     * @return a String containing the Hello hash value as hex-digits. Peer Hello hash is available
     * after we received a Hello packet from our peer. If peer's hello hash is not available return null.
     */
    override val peerHelloHash: String
        get() {
            val zrtpEngine = transformEngine
            return zrtpEngine?.peerHelloHash ?: ""
        }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getPeerZid ()
     */
    override val peerZid: ByteArray?
        get() {
            return transformEngine!!.peerZid
        }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getPeerZidString()
     */
    override val peerZidString: String
        get() {
            val zid = peerZid
            return String(ZrtpUtils.bytesToHexString(zid, zid!!.size))
        }

    /**
     * Method for getting the default secure status value for communication
     *
     * @return the default enabled/disabled status value for secure communication
     */
    override val secureCommunicationStatus: Boolean
        get() {
            val zrtpEngine = transformEngine
            return zrtpEngine != null && zrtpEngine.secureCommunicationStatus
        }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#getSecurityString ()
     */
    override val securityString: String?
        get() {
            return transformEngine!!.userCallback!!.securityString
        }

    /**
     * Returns the timeout value that will we will wait and fire timeout secure event if call is
     * not secured. The value is in milliseconds.
     *
     * @return the timeout value that will we will wait and fire timeout secure event if call is not secured.
     */
    override val timeoutValue: Long
        get() {
            // this is the default value as mentioned in rfc6189
            // we will later grab this setting from zrtp
            return 3750
        }

    /**
     * Initializes a new `ZRTPTransformEngine` instance to be associated with and used by
     * this `ZrtpControlImpl` instance.
     *
     * @return a new `ZRTPTransformEngine` instance to be associated with and used by this
     * `ZrtpControlImpl` instance
     */
    override fun createTransformEngine(): ZRTPTransformEngine {
        val transformEngine = ZRTPTransformEngine()

        // NOTE: set paranoid mode before initializing
        // zrtpEngine.setParanoidMode(paranoidMode);
        val zidFilename = "GNUZRTP4J_" + BigInteger(myZid).toString(32) + ".zid"
        transformEngine.initialize(zidFilename, false, ZrtpConfigureUtils.zrtpConfiguration, myZid)
        transformEngine.userCallback = SecurityEventManager(this)
        return transformEngine
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#isSecurityVerified ()
     */
    override val isSecurityVerified: Boolean
        get() {
            return transformEngine!!.userCallback!!.isSecurityVerified
        }

    /**
     * Returns false, ZRTP exchanges is keys over the media path.
     *
     * @return false
     */
    override fun requiresSecureSignalingTransport(): Boolean {
        return false
    }

    /**
     * Sets the `RTPConnector` which is to use or uses this ZRTP engine.
     *
     * @param connector the `RTPConnector` which is to use or uses this ZRTP engine
     */
    override fun setConnector(connector: AbstractRTPConnector?) {
        zrtpConnector = connector
    }

    /**
     * When in multistream mode, enables the master session.
     *
     * @param masterSession whether current control, controls the master session
     */
    override fun setMasterSession(masterSession: Boolean) {
        // by default its not master, change only if set to be master
        // sometimes (jingle) streams are re-initing and
        // we must reuse old value (true) event that false is submitted
        if (masterSession) this.masterSession = masterSession
    }

    /**
     * Start multi-stream ZRTP sessions. After the ZRTP Master (DH) session reached secure state
     * the SCCallback calls this method to start the multi-stream ZRTP sessions. Enable
     * auto-start mode (auto-sensing) to the engine.
     *
     * @param master master SRTP data
     */
    override fun setMultistream(master: SrtpControl?) {
        if (master == null || master === this) return
        require(master is ZrtpControlImpl) { "master is no ZRTP control" }
        val engine = transformEngine!!
        engine.multiStrParams = master.transformEngine!!.multiStrParams
        engine.isEnableZrtp = true
        engine.userCallback!!.setMasterEventManager(master.transformEngine!!.userCallback)
    }

    /**
     * Sets the SAS verification
     *
     * @param verified the new SAS verification status
     */
    override fun setSASVerification(verified: Boolean) {
        val engine = transformEngine!!
        if (verified) engine.SASVerified() else engine.resetSASVerified()
    }

    /**
     * Starts and enables zrtp in the stream holding this control.
     *
     * @param mediaType the media type of the stream this control controls.
     */
    override fun start(mediaType: MediaType) {
        val zrtpAutoStart: Boolean

        // ZRTP engine initialization
        val engine = transformEngine!!

        // Create security user callback for each peer.
        val securityEventManager = engine.userCallback

        // Decide if this will become the ZRTP Master session:
        // - Statement: audio media session will be started before video media session
        // - if no other audio session was started before then this will become ZRTP Master session
        // - only the ZRTP master sessions start in "auto-sensing" mode to immediately catch ZRTP communication from other client
        // - after the master session has completed its key negotiation it will start other media sessions (see SCCallback)
        if (masterSession) {
            zrtpAutoStart = true

            // we know that audio is considered as master for zrtp
            securityEventManager!!.setSessionType(mediaType)
        } else {
            // check whether video was not already started
            // it may happen when using multistreams, audio has initiated and started video
            // initially engine has value enableZrtp = false
            zrtpAutoStart = engine.isEnableZrtp
            securityEventManager!!.setSessionType(mediaType)
        }
        engine.setConnector(zrtpConnector)
        securityEventManager.setSrtpListener(srtpListener!!)

        // tells the engine whether to autostart(enable)
        // zrtp communication, if false it just passes packets without transformation
        engine.isEnableZrtp = zrtpAutoStart
        engine.sendInfo(MessageSeverity.Info, EnumSet.of(ZRTPCustomInfoCodes.ZRTPEnabledByDefault))
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#setReceivedSignaledZRTPVersion(String)
     */
    override fun setReceivedSignaledZRTPVersion(version: String) {
        transformEngine!!.setReceivedSignaledZRTPVersion(version)
    }

    /*
     * (non-Javadoc)
     *
     * @see net.java.sip.communicator.service.neomedia.ZrtpControl#isSecurityVerified(String)
     */
    override fun setReceivedSignaledZRTPHashValue(value: String) {
        transformEngine!!.setReceivedSignaledZRTPHashValue(value)
    }

    companion object {
        /**
         * Compute own ZID from salt value stored in accountID and peer JID.
         *
         * @param accountID Use the ZID salt value for this account
         * @param peerJid peer JID. Muss be a base JID, without resources part, because the resource can change too often.
         * @return computed ZID
         */
        @JvmStatic
        fun generateMyZid(accountID: AccountID, peerJid: BareJid): ByteArray {
            val ZIDSalt = getAccountZIDSalt(accountID)
            val zid = ByteArray(12)
            try {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(BigInteger(ZIDSalt, 32).toByteArray())
                md.update(peerJid.toString().toByteArray(StandardCharsets.UTF_8))
                val result = md.digest()
                System.arraycopy(result, 0, zid, 0, 12)
            } catch (e: NumberFormatException) {
                aTalkApp.showToastMessage(R.string.reset_ZID_summary)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalArgumentException("generateMyZid")
            }
            return zid
        }

        /**
         * Generate a new ZID salt if none is defined for the accountId (happen in testing.
         *
         * @param accountID Use the ZID salt value for this account
         * @return the found or new ZIDSalt
         */
        fun getAccountZIDSalt(accountID: AccountID): String {
            var ZIDSalt = accountID.getAccountPropertyString(ProtocolProviderFactory.ZID_SALT)
            if (ZIDSalt == null) {
                ZIDSalt = BigInteger(256, SecureRandom()).toString(32)
                accountID.storeAccountProperty(ProtocolProviderFactory.ZID_SALT, ZIDSalt)
            }
            return ZIDSalt!!
        }
    }
}