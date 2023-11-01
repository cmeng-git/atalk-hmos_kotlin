/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp

import gnu.java.zrtp.*
import gnu.java.zrtp.ZrtpCallback.EnableSecurity
import gnu.java.zrtp.ZrtpCodes.*
import gnu.java.zrtp.ZrtpConstants.SupportedSASTypes
import gnu.java.zrtp.zidfile.ZidFile
import okhttp3.internal.notifyAll
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformer
import org.atalk.impl.neomedia.transform.srtp.SRTCPTransformer
import org.atalk.impl.neomedia.transform.srtp.SRTPTransformer
import org.atalk.impl.neomedia.transform.srtp.SrtpContextFactory
import org.atalk.impl.neomedia.transform.srtp.SrtpPolicy
import org.atalk.service.fileaccess.FileCategory
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.RawPacket
import org.atalk.service.neomedia.SrtpControl
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.*

/**
 * JMF extension/connector to support GNU ZRTP4J.
 *
 * ZRTP was developed by Phil Zimmermann and provides functions to negotiate keys and other
 * necessary data (crypto data) to set-up the Secure RTP (SRTP) crypto context. Refer to Phil's ZRTP
 * specification at his [Zfone project](http://zfoneproject.com/) site to get more
 * detailed information about the capabilities of ZRTP.
 *
 * <h3>Short overview of the ZRTP4J implementation</h3>
 *
 * ZRTP is a specific protocol to negotiate encryption algorithms and the required key material.
 * ZRTP uses a RTP session to exchange its protocol messages.
 *
 * A complete GNU ZRTP4J implementation consists of two parts, the GNU ZRTP4J core and specific code
 * that binds the GNU ZRTP core to the underlying RTP/SRTP stack and the operating system:
 *
 *  * The GNU ZRTP core is independent of a specific RTP/SRTP stack and the operating system and
 * consists of the ZRTP protocol state engine, the ZRTP protocol messages, and the GNU ZRTP4J
 * engine. The GNU ZRTP4J engine provides methods to setup ZRTP message and to analyze received ZRTP
 * messages, to compute the crypto data required for SRTP, and to maintain the required hashes and HMAC.
 *  * The second part of an implementation is specific *glue* code the binds the GNU ZRTP
 * core to the actual RTP/SRTP implementation and other operating system specific services such as timers.
 *
 *
 * The GNU ZRTP4J core uses a callback interface class (refer to ZrtpCallback) to access RTP/SRTP or
 * operating specific methods, for example to send data via the RTP/SRTP stack, to access timers,
 * provide mutex handling, and to report events to the application.
 *
 * <h3>The ZRTPTransformEngine</h3>
 *
 * ZRTPTransformEngine implements code that is specific to the JMF implementation.
 *
 * To perform its tasks ZRTPTransformEngine
 *
 *  * extends specific classes to hook into the JMF RTP methods and the RTP/SRTP send and receive queues
 *  * implements the ZrtpCallback interface to provide to enable data send and receive other
 * specific services (timer to GNU ZRTP4J
 *  * provides ZRTP specific methods that applications may use to control and setup GNU ZRTP
 *  * can register and use an application specific callback class (refer to ZrtpUserCallback)
 *
 *
 * After instantiating a GNU ZRTP4J session (see below for a short example) applications may use the
 * ZRTP specific methods of ZRTPTransformEngine to control and setup GNU ZRTP, for example enable or
 * disable ZRTP processing or getting ZRTP status information.
 *
 * GNU ZRTP4J provides a ZrtpUserCallback class that an application may extend and register with
 * ZRTPTransformEngine. GNU ZRTP4J and ZRTPTransformEngine use the ZrtpUserCallback methods to
 * report ZRTP events to the application. The application may display this information to the user
 * or act otherwise.
 *
 * The following figure depicts the relationships between ZRTPTransformEngine, JMF implementation,
 * the GNU ZRTP4J core, and an application that provides an ZrtpUserCallback class.
 *
 * <pre>
 *
 * +---------------------------+
 * |  ZrtpTransformConnector   |
 * | extends TransformConnector|
 * | implements RTPConnector   |
 * +---------------------------+
 * |
 * | uses
 * |
 * +----------------+      +-----+---------------+
 * |  Application   |      |                     |      +----------------+
 * |  instantiates  | uses | ZRTPTransformEngine | uses |                |
 * | a ZRTP Session +------+    implements       +------+   GNU ZRTP4J   |
 * |  and provides  |      |   ZrtpCallback      |      |      core      |
 * |ZrtpUserCallback|      |                     |      | implementation |
 * +----------------+      +---------------------+      |  (ZRtp et al)  |
 * |                |
 * +----------------+
</pre> *
 *
 * The following short code snippets show how an application could instantiate a
 * ZrtpTransformConnector, get the ZRTP4J engine and initialize it. Then the code get a RTP manager
 * instance and initializes it with the ZRTPTransformConnector. Please note: setting the target must
 * be done with the connector, not with the RTP manager.
 *
 * <pre>
 * ...
 * transConnector = (ZrtpTransformConnector)TransformManager.createZRTPConnector(sa);
 * zrtpEngine = transConnector.getEngine();
 * zrtpEngine.setUserCallback(new MyCallback());
 * if (!zrtpEngine.initialize(&quot;test_t.zid&quot;))
 * System.out.println(&quot;initialize failed&quot;);
 *
 * // initialize the RTPManager using the ZRTP connector
 *
 * mgr = RTPManager.newInstance();
 * mgr.initialize(transConnector);
 *
 * mgr.addSessionListener(this);
 * mgr.addReceiveStreamListener(this);
 *
 * transConnector.addTarget(target);
 * zrtpEngine.startZrtp();
 *
 * ...
</pre> *
 *
 * The *demo* folder contains a small example that shows how to use GNU ZRTP4J.
 *
 * This ZRTPTransformEngine documentation shows the ZRTP specific extensions and describes
 * overloaded methods and a possible different behaviour.
 *
 * @author Werner Dittmann &lt;Werner.Dittmann@t-online.de>
 * @author Eng Chong Meng
 * @author MilanKral
 */
class ZRTPTransformEngine : SinglePacketTransformer(), SrtpControl.TransformEngine, ZrtpCallback {
    /**
     * Very simple Timeout provider class.
     *
     * This very simple timeout provider can handle one timeout request at one time only. A second
     * request would overwrite the first one and would lead to unexpected results.
     *
     * @author Werner Dittmann <Werner.Dittmann></Werner.Dittmann>@t-online.de>
     */
    private inner class TimeoutProvider

    /**
     * Constructs Timeout provider.
     *
     * @param name the name of the provider.
     */
    (name: String) : Thread(name) {
        /**
         * The delay to wait before timeout.
         */
        private var nextDelay: Long = 0

        /**
         * Whether to execute the timeout if delay expires.
         */
        private var newTask = false

        /**
         * Whether thread is stopped.
         */
        private var stop = false

        /**
         * synchronizes delays and stop.
         */
        private val sync = Any()

        /**
         * Request timeout after the specified delay.
         *
         * @param delay the delay.
         */
        @Synchronized
        fun requestTimeout(delay: Long) {
            synchronized(sync) {
                nextDelay = delay
                newTask = true
                sync.notifyAll()
            }
        }

        /**
         * Stops the thread.
         */
        fun stopRun() {
            synchronized(sync) {
                stop = true
                sync.notifyAll()
            }
        }

        /**
         * Cancels the last request.
         */
        fun cancelRequest() {
            synchronized(sync) {
                newTask = false
                sync.notifyAll()
            }
        }

        /**
         * The running part of the thread.
         */
        override fun run() {
            while (!stop) {
                synchronized(sync) {
                    while (!newTask && !stop) {
                        try {
                            (sync as Object).wait()
                        } catch (e: InterruptedException) {
                            // e.printStackTrace();
                        }
                    }
                }
                var currentTime = System.currentTimeMillis()
                val endTime = currentTime + nextDelay
                synchronized(sync) {
                    while (currentTime < endTime && newTask && !stop) {
                        try {
                            (sync as Object).wait(endTime - currentTime)
                        } catch (e: InterruptedException) {
                            // e.printStackTrace();
                        }
                        currentTime = System.currentTimeMillis()
                    }
                }
                if (newTask && !stop) {
                    newTask = false
                    handleTimeout()
                }
            }
        }
    }

    /**
     * This is the connector, required to send ZRTP packets via the DatagramSocket.
     */
    private var zrtpConnector: AbstractRTPConnector? = null

    /**
     * We need Out SRTPTransformer to transform RTP to SRTP.
     */
    private var srtpOutTransformer: SRTPTransformer? = null

    /**
     * We need In SRTPTransformer to transform SRTP to RTP.
     */
    private var srtpInTransformer: SRTPTransformer? = null

    /**
     * The user callback used to manage the GUI part of ZRTP
     */
    var userCallback: SecurityEventManager? = null

    /**
     * The ZRTP engine.
     */
    private var zrtpEngine: ZRtp? = null

    /**
     * ZRTP engine enable flag (used for auto-enable at initialization)
     */
    var isEnableZrtp = false

    /**
     * Client ID string initialized with the name of the ZRTP4j library
     */
    private var clientIdString = ZrtpConstants.clientId

    /**
     * SSRC identifier for the ZRTP packets
     */
    private var ownSSRC = 0

    /**
     * ZRTP packet sequence number
     */
    private var senderZrtpSeqNo: Short

    /**
     * The timeout provider instance This is used for handling the ZRTP timers
     */
    private var timeoutProvider: TimeoutProvider? = null
    /**
     * Returns the current status of the ZRTP engine
     *
     * @return the current status of the ZRTP engine
     */
    /**
     * The current condition of the ZRTP engine
     */
    var isStarted = false
        private set

    /**
     * Sometimes we need to start muted so we will discard any packets during some time after the
     * start of the transformer. This is needed when for this time we can receive encrypted packets
     * but we hadn't established a secure communication. This happens when a secure stream is recreated.
     */
    private var muted = false
    /**
     * Check the state of the MitM mode flag.
     * If MitM mode is set to true this ZRTP session acts as MitM, usually enabled by a PBX based client (user agent).
     */
    private var isMitmMode = false
    /**
     * Check status of paranoid mode.
     *
     * @return Returns true if paranoid mode is enabled.
     */
    /**
     * Enables or disables paranoid mode.
     *
     * For further explanation of paranoid mode refer to the documentation of ZRtp class.
     *
     * yesNo If set to true then paranoid mode is enabled.
     */
    /**
     * Enable or disable paranoid mode.
     *
     * The Paranoid mode controls the behaviour and handling of the SAS verify flag. If Paranoid
     * mode is set to false then ZRtp applies the normal handling. If Paranoid mode is set to
     * true then the handling is:
     *
     *
     *  * Force the SAS verify flag to be false at srtpSecretsOn() callback. This gives the user
     * interface (UI) the indication to handle the SAS as **not verified**. See implementation note below.
     *  * Don't set the SAS verify flag in the `Confirm` packets, thus the other also
     * must report the SAS as **not verified**.
     *  * ignore the `SASVerified()` function, thus do not set the SAS to verified in the ZRTP cache.
     *  * Disable the **Trusted PBX MitM** feature. Just send the `SASRelay` packet
     * but do not process the relayed data. This protects the user from a malicious "trusted PBX"
     * .
     *
     * ZRtp performs all other steps during the ZRTP negotiations as usual, in particular it
     * computes, compares, uses, and stores the retained secrets. This avoids unnecessary warning
     * messages. The user may enable or disable the Paranoid mode on a call-by-call basis without
     * breaking the key continuity data.
     *
     * **Implementation note:** An application shall always display the SAS code if the SAS
     * verify flag is `false`. The application shall also use mechanisms to remind the
     * user to compare the SAS code, for example using larger fonts, different colours and other
     * display features.
     */

    private var isParanoidMode = false
    private var zrtcpTransformer: ZRTCPTransformer? = null

    /**
     * Count successfully decrypted SRTP packets. Need to decide if RS2 will become valid.
     *
     * @see .stopZrtp
     */
    private var zrtpUnprotect: Long = 0

    /**
     * The indicator which determines whether [SrtpControl.TransformEngine.cleanup] has
     * been invoked on this instance to prepare it for garbage collection. Disallows
     * [.getRTCPTransformer] to initialize a new `ZRTCPTransformer` instance which
     * cannot possibly be correctly used after the disposal of this instance anyway.
     */
    private var disposed = false

    /**
     * This is the peer ZRTP version received from the signaling layer.
     */
    private var receivedSignaledZRTPVersion: String? = null

    /**
     * This is the peer ZRTP hash value received from the signaling layer.
     */
    private var receivedSignaledZRTPHashValue: String? = null

    /**
     * Construct a ZRTPTransformEngine.
     */
    init {
        val secRand = SecureRandom()
        val random = ByteArray(2)
        secRand.nextBytes(random)
        senderZrtpSeqNo = random[0].toShort()
        senderZrtpSeqNo = (senderZrtpSeqNo.toInt() or (random[1].toInt() shl 8)).toShort()
        senderZrtpSeqNo = (senderZrtpSeqNo.toInt() and 0x7fff).toShort() // to avoid early roll-over
    }

    /**
     * Returns an instance of `ZRTPCTransformer`.
     */
    override val rtcpTransformer: ZRTCPTransformer?
        get() {
            if (zrtcpTransformer == null && !disposed) zrtcpTransformer = ZRTCPTransformer()
            return zrtcpTransformer
        }

    /**
     * Returns this RTPTransformer.
     *
     * @see TransformEngine.rtpTransformer
     */
    override val rtpTransformer: PacketTransformer
        get() {
            return this
        }

    //    /**
    //     * Engine initialization method. Calling this for engine initialization and start it with
    //     * auto-sensing and a given configuration setting.
    //     *
    //     * @param zidFilename The ZID file name
    //     * @param config The configuration data
    //     * @return true if initialization fails, false if succeeds
    //     */
    //    public boolean initialize(String zidFilename, ZrtpConfigure config)
    //    {
    //        return initialize(zidFilename, true, config);
    //    }
    //
    //    /**
    //     * Engine initialization method. Calling this for engine initialization and start it with
    //     * defined auto-sensing and a default configuration setting.
    //     *
    //     * @param zidFilename The ZID file name
    //     * @param autoEnable If true start with auto-sensing mode.
    //     * @return true if initialization fails, false if succeeds
    //     */
    //    public boolean initialize(String zidFilename, boolean autoEnable)
    //    {
    //        return initialize(zidFilename, autoEnable, null);
    //    }
    //
    //    /**
    //     * Default engine initialization method.
    //     *
    //     * Calling this for engine initialization and start it with auto-sensing and default
    //     * configuration setting.
    //     *
    //     * @param zidFilename The ZID file name
    //     * @return true if initialization fails, false if succeeds
    //     */
    //    public boolean initialize(String zidFilename)
    //    {
    //        return initialize(zidFilename, true, null);
    //    }
    /**
     * Custom engine initialization method.
     * This allows to explicit specify if the engine starts with auto-sensing or not.
     *
     * @param zidFilename The ZID file name
     * @param autoEnable Set this true to start with auto-sensing and false to disable it.
     * @param cfg the zrtp config to use
     * @return true if initialization fails, false if succeeds
     */
    @Synchronized
    fun initialize(zidFilename: String, autoEnable: Boolean, cfg: ZrtpConfigure?, myZid: ByteArray?): Boolean {
        // Try to get the ZidFile path through the FileAccessService.
        var config = cfg
        var file: File? = null
        val faService = LibJitsi.fileAccessService
        if (faService != null) {
            try {
                // Create the zid file
                file = faService.getPrivatePersistentFile(zidFilename, FileCategory.PROFILE)
            } catch (e: Exception) {
                Timber.w("Failed to create the zid file.")
            }
        }

        // The ZidFile path should be absolute.
        var zidFilePath: String? = null
        try {
            if (file != null) zidFilePath = file.absolutePath
        } catch (e: SecurityException) {
            Timber.d(e, "Failed to obtain the absolute path of the zid file.")
        }
        val zf = ZidFile.getInstance()
        if (zf.isOpen) {
            if (!Arrays.equals(zf.zid, myZid)) {
                zf.close()
            }
        }
        if (!zf.isOpen) {
            if (zf.open(zidFilePath) < 0) return false
        }
        if (config == null) {
            config = ZrtpConfigure()
            config.setStandardConfig()
        }
        if (isParanoidMode) config.isParanoidMode = isParanoidMode
        zrtpEngine = ZRtp(myZid, this, "", config, isMitmMode)
        if (timeoutProvider == null) {
            timeoutProvider = TimeoutProvider("ZRTP")
            // XXX Daemon only if timeoutProvider is a global singleton.
            // timeoutProvider.setDaemon(true);
            timeoutProvider!!.start()
        }
        isEnableZrtp = autoEnable
        return true
    }

    /**
     * @param startMuted whether to be started as muted if no secure communication is established
     */
    fun setStartMuted(startMuted: Boolean) {
        muted = startMuted
        if (startMuted) {
            // make sure we don't mute for long time as secure communication may fail.
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    muted = false
                }
            }, 1500)
        }
    }

    /**
     * Method for getting the default secure status value for communication
     *
     * @return the default enabled/disabled status value for secure communication
     */
    val secureCommunicationStatus: Boolean
        get() = srtpInTransformer != null || srtpOutTransformer != null

    /**
     * Start the ZRTP stack immediately, not auto-sensing mode.
     */
    private fun startZrtp() {
        if (zrtpEngine != null) {
            zrtpEngine!!.startZrtpEngine()
            isStarted = true
            userCallback!!.securityNegotiationStarted()
        }
    }

    /**
     * Close the transformer and underlying transform engine.
     *
     * The close functions closes all stored crypto contexts. This deletes key data and forces a
     * cleanup of the crypto contexts.
     */
    override fun close() {
        stopZrtp()
    }

    /**
     * Stop ZRTP engine.
     *
     * The ZRTP stack stores RS2 without the valid flag. If we close the ZRTP stream then check if
     * we need to set RS2 to valid. This is the case if we received less than 10 good SRTP packets.
     * In this case we enable RS2 to make sure the ZRTP self-synchronization is active.
     *
     * This handling is needed to comply to an upcoming newer ZRTP RFC.
     */
    private fun stopZrtp() {
        if (zrtpEngine != null) {
            if (zrtpUnprotect < 10) zrtpEngine!!.setRs2Valid()
            zrtpEngine!!.stopZrtp()
            zrtpEngine = null
            isStarted = false
        }
        // The SRTP transformer are usually already closed during security-off
        // processing. Check here again just in case ...
        if (srtpOutTransformer != null) {
            srtpOutTransformer!!.close()
            srtpOutTransformer = null
        }
        if (srtpInTransformer != null) {
            srtpInTransformer!!.close()
            srtpInTransformer = null
        }
        if (zrtcpTransformer != null) {
            zrtcpTransformer!!.close()
            zrtcpTransformer = null
        }
    }

    /**
     * Cleanup function for any remaining timers
     */
    override fun cleanup() {
        disposed = true
        stopZrtp()
        if (timeoutProvider != null) {
            timeoutProvider!!.stopRun()
            timeoutProvider = null
        }
    }

    /**
     * Set the SSRC of the RTP transmitter stream.
     *
     * ZRTP fills the SSRC in the ZRTP messages.
     *
     * @param ssrc SSRC to set
     */
    fun setOwnSSRC(ssrc: Long) {
        ownSSRC = ssrc.toInt()
    }

    /**
     * The data output stream calls this method to transform outgoing packets.
     *
     * @see PacketTransformer.transform
     */
    override fun transform(pkt: RawPacket): RawPacket {
        // Never transform outgoing ZRTP (invalid RTP) packets.
        var packet = pkt
        if (!ZrtpRawPacket.isZrtpData(packet)) {
            // ZRTP needs the SSRC of the sending stream.
            if (isEnableZrtp && ownSSRC == 0) ownSSRC = packet.getSSRC()
            // If SRTP is active then srtpTransformer is set, use it.
            if (srtpOutTransformer != null)
                packet = srtpOutTransformer!!.transform(packet)!!
        }
        return packet
    }

    /**
     * The input data stream calls this method to transform incoming packets.
     *
     * @see PacketTransformer.reverseTransform
     */
    override fun reverseTransform(pkt: RawPacket): RawPacket? {
        // Check if we need to start ZRTP
        if (!isStarted && isEnableZrtp && ownSSRC != 0) startZrtp()

        /*
         * If the incoming packet is not a ZRTP packet, treat it as a normal RTP packet and handle it accordingly.
         */
        if (!ZrtpRawPacket.isZrtpData(pkt)) {
            if (srtpInTransformer == null) return if (muted) null else pkt
            val pkt2 = srtpInTransformer!!.reverseTransform(pkt)
            // if packet was valid (i.e. not null) and ZRTP engine started and in Wait for
            // Confirm2 Ack then emulate a Conf2Ack packet. See ZRTP specification chap. 5.6
            if (pkt2 != null && isStarted
                    && zrtpEngine!!.inState(ZrtpStateClass.ZrtpStates.WaitConfAck)) {
                zrtpEngine!!.conf2AckSecure()
            }
            if (pkt2 != null) zrtpUnprotect++
            return pkt2
        }

        /*
         * Process if ZRTP is enabled. In any case return null because ZRTP packets must never reach the application.
         */
        if (isEnableZrtp && isStarted) {
            val zPkt = ZrtpRawPacket(pkt)
            if (!zPkt.checkCrc()) {
                userCallback!!.showMessage(MessageSeverity.Warning,
                        EnumSet.of(WarningCodes.WarningCRCmismatch))
            } else if (zPkt.hasMagic()) {
                val extHeaderOffset = zPkt.headerLength - zPkt.extensionLength - RawPacket.EXT_HEADER_SIZE
                // zrtp engine need a "pointer" to the extension header, so we
                // give him the extension header and the payload data
                val extHeader = zPkt.readRegion(extHeaderOffset, RawPacket.EXT_HEADER_SIZE
                        + zPkt.extensionLength + zPkt.payloadLength)
                zrtpEngine!!.processZrtpMessage(extHeader, zPkt.getSSRC())
            }
        }
        return null
    }

    /**
     * The callback method required by the ZRTP implementation. First allocate space to hold the
     * complete ZRTP packet, copy the message part in its place, the initialize the header, counter, SSRC and crc.
     *
     * @param data The ZRTP packet data
     * @return true if sending succeeded, false if it failed.
     */
    override fun sendDataZRTP(data: ByteArray): Boolean {
        val totalLength = ZRTP_PACKET_HEADER + data.size
        val tmp = ByteArray(totalLength)
        System.arraycopy(data, 0, tmp, ZRTP_PACKET_HEADER, data.size)
        val packet = ZrtpRawPacket(tmp, 0, tmp.size)
        packet.setSSRC(ownSSRC)
        packet.setSeqNum(senderZrtpSeqNo++)
        packet.setCrc()
        try {
            val outputStream = zrtpConnector!!.dataOutputStream
            outputStream.write(packet.buffer, packet.offset, packet.length)
        } catch (e: IOException) {
            Timber.w("Failed to send ZRTP data: %s", e.message)
            return false
        }
        return true
    }

    /**
     * Switch on the security for the defined part.
     *
     * @param secrets The secret keys and salt negotiated by ZRTP.
     * @param part An enum that defines sender, receiver, or both.
     * @return always return true.
     */
    override fun srtpSecretsReady(secrets: ZrtpSrtpSecrets, part: EnableSecurity): Boolean {
        val srtpPolicy: SrtpPolicy
        var cipher = 0
        var authn = 0
        var authKeyLen = 0
        if (secrets.authAlgorithm == ZrtpConstants.SupportedAuthAlgos.HS) {
            authn = SrtpPolicy.HMACSHA1_AUTHENTICATION
            authKeyLen = 20
        } else if (secrets.authAlgorithm == ZrtpConstants.SupportedAuthAlgos.SK) {
            authn = SrtpPolicy.SKEIN_AUTHENTICATION
            authKeyLen = 32
        }
        if (secrets.symEncAlgorithm == ZrtpConstants.SupportedSymAlgos.AES) {
            cipher = SrtpPolicy.AESCM_ENCRYPTION
        } else if (secrets.symEncAlgorithm == ZrtpConstants.SupportedSymAlgos.TwoFish) {
            cipher = SrtpPolicy.TWOFISH_ENCRYPTION
        }
        if (part == EnableSecurity.ForSender) {
            // To encrypt packets: initiator uses initiator keys, responder uses responder keys
            // Create a "half baked" crypto context first and store it. This is
            // the main crypto context for the sending part of the connection.
            if (secrets.role == ZrtpCallback.Role.Initiator) {
                srtpPolicy = SrtpPolicy(cipher,
                        secrets.initKeyLen / 8,
                        authn, authKeyLen,
                        secrets.srtpAuthTagLen / 8,
                        secrets.initSaltLen / 8
                )
                val engine = SrtpContextFactory(
                        true,
                        secrets.keyInitiator,
                        secrets.saltInitiator,
                        srtpPolicy, srtpPolicy)
                srtpOutTransformer = SRTPTransformer(engine)
                rtcpTransformer!!.setSrtcpOut(SRTCPTransformer(engine))
            } else {
                srtpPolicy = SrtpPolicy(
                        cipher,
                        secrets.respKeyLen / 8,
                        authn, authKeyLen,
                        secrets.srtpAuthTagLen / 8,
                        secrets.respSaltLen / 8
                )
                val engine = SrtpContextFactory(
                        true,
                        secrets.keyResponder,
                        secrets.saltResponder,
                        srtpPolicy, srtpPolicy)
                srtpOutTransformer = SRTPTransformer(engine)
                rtcpTransformer!!.setSrtcpOut(SRTCPTransformer(engine))
            }
        } else if (part == EnableSecurity.ForReceiver) {
            // To decrypt packets: initiator uses responder keys, responder initiator keys
            // See comment above.
            if (secrets.role == ZrtpCallback.Role.Initiator) {
                srtpPolicy = SrtpPolicy(
                        cipher,
                        secrets.respKeyLen / 8,
                        authn, authKeyLen,
                        secrets.srtpAuthTagLen / 8,
                        secrets.respSaltLen / 8
                )
                val engine = SrtpContextFactory(
                        false /* receiver */,
                        secrets.keyResponder,
                        secrets.saltResponder,
                        srtpPolicy, srtpPolicy)
                srtpInTransformer = SRTPTransformer(engine)
                rtcpTransformer!!.setSrtcpIn(SRTCPTransformer(engine))
                muted = false
            } else {
                srtpPolicy = SrtpPolicy(
                        cipher,
                        secrets.initKeyLen / 8,
                        authn, authKeyLen,  // auth key length
                        secrets.srtpAuthTagLen / 8,
                        secrets.initSaltLen / 8
                )
                val engine = SrtpContextFactory(
                        false /* receiver */,
                        secrets.keyInitiator,
                        secrets.saltInitiator,
                        srtpPolicy, srtpPolicy)
                srtpInTransformer = SRTPTransformer(engine)
                rtcpTransformer!!.setSrtcpIn(SRTCPTransformer(engine))
                muted = false
            }
        }
        return true
    }

    /**
     * Switch on the security.
     *
     * ZRTP calls this method after it has computed the SAS and check if it is verified or not.
     *
     * @param c The name of the used cipher algorithm and mode, or NULL.
     * @param s The SAS string.
     * @param verified if `verified` is true then SAS was verified by both parties during a previous call.
     * @see gnu.java.zrtp.ZrtpCallback.srtpSecretsOn
     */
    override fun srtpSecretsOn(c: String, s: String?, verified: Boolean) {
        if (userCallback != null) {
            userCallback!!.secureOn(c)
            if (s != null || !verified) userCallback!!.showSAS(s!!, verified)
        }
    }

    /**
     * This method shall clear the ZRTP secrets.
     *
     * @param part Defines for which part (sender or receiver) to switch on security
     */
    override fun srtpSecretsOff(part: EnableSecurity) {
        if (part == EnableSecurity.ForSender) {
            if (srtpOutTransformer != null) {
                srtpOutTransformer!!.close()
                srtpOutTransformer = null
            }
        } else if (part == EnableSecurity.ForReceiver) {
            if (srtpInTransformer != null) {
                srtpInTransformer!!.close()
                srtpInTransformer = null
            }
        }
        if (userCallback != null) userCallback!!.secureOff()
    }

    /**
     * Activate timer.
     *
     * @param time The time in ms for the timer.
     * @return always return 1.
     */
    override fun activateTimer(time: Int): Int {
        if (timeoutProvider != null) timeoutProvider!!.requestTimeout(time.toLong())
        return 1
    }

    /**
     * Cancel the active timer.
     *
     * @return always return 1.
     */
    override fun cancelTimer(): Int {
        if (timeoutProvider != null) timeoutProvider!!.cancelRequest()
        return 1
    }

    /**
     * Timeout handling function. Delegates the handling to the ZRTP engine.
     */
    fun handleTimeout() {
        // processTimeout will wait for ~10 below events before failing
        // sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereTooMuchRetries));
        if (zrtpEngine != null) zrtpEngine!!.processTimeout()
    }

    /**
     * Send information messages to the hosting environment.
     *
     * @param severity This defines the message's severity
     * @param subCode The message code.
     */
    override fun sendInfo(severity: MessageSeverity, subCode: EnumSet<*>) {
        val version_and_hash = zrtpEngine!!.peerHelloHashSep
        if (version_and_hash != null && receivedSignaledZRTPVersion != null && receivedSignaledZRTPHashValue != null) {
            val peerHelloVersion = version_and_hash[0]
            val peerHelloHash = version_and_hash[1]
            if (peerHelloVersion != receivedSignaledZRTPVersion
                    || peerHelloHash != receivedSignaledZRTPHashValue) {
                if (userCallback != null) {
                    userCallback!!.showMessage(MessageSeverity.Severe, EnumSet.of(SevereCodes.SevereSecurityException))
                }
                close()
            }
        }
        if (userCallback != null) userCallback!!.showMessage(severity, subCode)
        if (MessageSeverity.ZrtpError == severity) {
            Timber.w(Exception("ZRTP Error: $subCode"))
        }
    }

    /**
     * Comes a message that zrtp negotiation has failed.
     *
     * @param severity This defines the message's severity
     * @param subCode The message code.
     */
    override fun zrtpNegotiationFailed(severity: MessageSeverity, subCode: EnumSet<*>) {
        if (userCallback != null) userCallback!!.zrtpNegotiationFailed(severity, subCode)
    }

    /**
     * The other part doesn't support zrtp.
     */
    override fun zrtpNotSuppOther() {
        if (userCallback != null) userCallback!!.zrtpNotSuppOther()
    }

    /**
     * Zrtp ask for Enrollment.
     *
     * @param info supplied info.
     */
    override fun zrtpAskEnrollment(info: InfoEnrollment) {
        if (userCallback != null) userCallback!!.zrtpAskEnrollment(info)
    }

    /**
     * @param info information to the user about the result of an enrollment.
     * @see gnu.java.zrtp.ZrtpCallback.zrtpInformEnrollment
     */
    override fun zrtpInformEnrollment(info: InfoEnrollment) {
        if (userCallback != null) userCallback!!.zrtpInformEnrollment(info)
    }

    /**
     * @param sasHash Hash value of Short Authentication String.
     * @see gnu.java.zrtp.ZrtpCallback.signSAS
     */
    override fun signSAS(sasHash: ByteArray) {
        if (userCallback != null) userCallback!!.signSAS(sasHash)
    }

    /**
     * @param sasHash Hash value of Short Authentication String.
     * @return false if signature check fails, true otherwise
     * @see gnu.java.zrtp.ZrtpCallback.checkSASSignature
     */
    override fun checkSASSignature(sasHash: ByteArray): Boolean {
        return (userCallback != null
                && userCallback!!.checkSASSignature(sasHash))
    }

    /**
     * Set the SAS as verified internally if the user confirms it
     */
    fun SASVerified() {
        if (zrtpEngine != null) zrtpEngine!!.SASVerified()
        if (userCallback != null) userCallback!!.setSASVerified(true)
    }

    /**
     * Resets the internal engine SAS verified flag
     */
    fun resetSASVerified() {
        if (zrtpEngine != null) zrtpEngine!!.resetSASVerified()
        if (userCallback != null) userCallback!!.setSASVerified(false)
    }

    /**
     * Method called when the user requests through GUI to switch a secured call to unsecured mode.
     * Just forwards the request to the Zrtp class.
     */
    fun requestGoClear() {
        // if (zrtpEngine != null)
        // zrtpEngine.requestGoClear();
    }

    /**
     * Method called when the user requests through GUI to switch a previously unsecured call back
     * to secure mode. Just forwards the request to the Zrtp class.
     */
    fun requestGoSecure() {
        // if (zrtpEngine != null)
        // zrtpEngine.requestGoSecure();
    }

    /**
     * Sets the auxiliary secret data
     *
     * @param data The auxiliary secret data
     */
    fun setAuxSecret(data: ByteArray?) {
        if (zrtpEngine != null) zrtpEngine!!.setAuxSecret(data)
    }

    /**
     * Sets the client ID
     *
     * @param id The client ID
     */
    fun setClientId(id: String) {
        clientIdString = id
    }

    /**
     * Gets the Hello packet Hash
     *
     * @param index Hello hash of the Hello packet identified by index.
     * Index must be 0 <= index < SUPPORTED_ZRTP_VERSIONS.
     * @return the Hello packet hash
     */
    fun getHelloHash(index: Int): String {
        return if (zrtpEngine != null) zrtpEngine!!.getHelloHash(index) else ""
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
    fun getHelloHashSep(index: Int): Array<String>? {
        return if (zrtpEngine != null) zrtpEngine!!.getHelloHashSep(index) else null
    }

    /**
     * Get the peer's Hello Hash data.
     *
     * Use this method to get the peer's Hello Hash data. The method returns the data as a string.
     *
     * @return a String containing the Hello hash value as hex-digits. Peer Hello hash is available
     * after we received a Hello packet from our peer. If peer's hello hash is not available return null.
     */
    val peerHelloHash: String
        get() = if (zrtpEngine != null) zrtpEngine!!.peerHelloHash else ""
    /**
     * The multistream params (The multistream part needs further development)
     */
    var multiStrParams: ByteArray?
        get() = if (zrtpEngine != null) zrtpEngine!!.multiStrParams else ByteArray(0)
        set(parameters) {
            if (zrtpEngine != null) zrtpEngine!!.multiStrParams = parameters
        }

    /**
     * The isMultiStream flag (The multistream part needs further development)
     */
    val isMultiStream: Boolean
        get() = zrtpEngine != null && zrtpEngine!!.isMultiStream

    /**
     * Used to accept a PBX enrollment request (The PBX part needs further development)
     *
     * @param accepted The boolean value indicating if the request is accepted
     */
    fun acceptEnrollment(accepted: Boolean) {
        if (zrtpEngine != null) zrtpEngine!!.acceptEnrollment(accepted)
    }

    /**
     * Get the committed SAS rendering algorithm for this ZRTP session.
     *
     * @return the committed SAS rendering algorithm
     */
    val sasType: SupportedSASTypes?
        get() = if (zrtpEngine != null) zrtpEngine!!.sasType else null

    /**
     * Get the computed SAS hash for this ZRTP session.
     *
     * @return a reference to the byte array that contains the full SAS hash.
     */
    val sasHash: ByteArray?
        get() = if (zrtpEngine != null) zrtpEngine!!.sasHash else null

    /**
     * Send the SAS relay packet.
     *
     * The method creates and sends a SAS relay packet according to the ZRTP specifications.
     * Usually only a MitM capable user agent (PBX) uses this function.
     *
     * @param sh the full SAS hash value
     * @param render the SAS rendering algorithm
     * @return true if the SASReplay packet has been correctly sent, false otherwise
     */
    fun sendSASRelayPacket(sh: ByteArray?, render: SupportedSASTypes?): Boolean {
        return (zrtpEngine != null
                && zrtpEngine!!.sendSASRelayPacket(sh, render))
    }
    /**
     * Check the state of the enrollment mode.
     *
     * If true then we will set the enrollment flag (E) in the confirm packets and performs the
     * enrollment actions. A MitM (PBX) enrollment service sets this flag.
     *
     * @return status of the enrollmentMode flag.
     */
    /**
     * Set the state of the enrollment mode.
     *
     * If true then we will set the enrollment flag (E) in the confirm packets and perform the
     * enrollment actions. A MitM (PBX) enrollment service must set this mode to true.
     *
     * Can be set to true only if mitmMode is also true.
     *
     * @param enrollmentMode defines the new state of the enrollmentMode flag
     */
    var isEnrollmentMode: Boolean
        get() = (zrtpEngine != null
                && zrtpEngine!!.isEnrollmentMode)
        set(enrollmentMode) {
            if (zrtpEngine != null) zrtpEngine!!.isEnrollmentMode = enrollmentMode
        }

    /**
     * Sets signature data for the Confirm packets
     *
     * @param data the signature data
     * @return true if signature data was successfully set
     */
    fun setSignatureData(data: ByteArray?): Boolean {
        return zrtpEngine != null && zrtpEngine!!.setSignatureData(data)
    }

    /**
     * Gets signature data
     *
     * @return the signature data
     */
    val signatureData: ByteArray
        get() = if (zrtpEngine != null) zrtpEngine!!.signatureData else ByteArray(0)

    /**
     * Gets signature length
     *
     * @return the signature length
     */
    val signatureLength: Int
        get() = if (zrtpEngine != null) zrtpEngine!!.signatureLength else 0

    /**
     * Method called by the Zrtp class as result of a GoClear request from the other peer. An
     * explicit user confirmation is needed before switching to unsecured mode. This is asked
     * through the user callback.
     */
    override fun handleGoClear() {
        userCallback!!.confirmGoClear()
    }

    /**
     * Sets the RTP connector using this ZRTP engine
     *
     * @param connector the connector to set
     */
    fun setConnector(connector: AbstractRTPConnector?) {
        zrtpConnector = connector
    }

    /**
     * Get other party's ZID (ZRTP Identifier) data
     *
     * This functions returns the other party's ZID that was received during ZRTP processing.
     *
     * The ZID data can be retrieved after ZRTP receive the first Hello packet from the other
     * party. The application may call this method for example during SAS processing in showSAS
     * (...) user callback method.
     *
     * @return the ZID data as byte array.
     */
    val peerZid: ByteArray?
        get() = if (zrtpEngine != null) zrtpEngine!!.peerZid else null

    /**
     * Get number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    val numberSupportedVersions: Int
        get() = if (zrtpEngine != null) zrtpEngine!!.numberSupportedVersions else 0

    /**
     * Get negotiated ZRTP protocol version.
     *
     * @return the integer representation of the negotiated ZRTP protocol version.
     */
    val currentProtocolVersion: Int
        get() = if (zrtpEngine != null) zrtpEngine!!.currentProtocolVersion else 0

    /**
     * Set ZRTP version received from the signaling layer.
     *
     * @param version received version
     */
    fun setReceivedSignaledZRTPVersion(version: String?) {
        receivedSignaledZRTPVersion = version
    }

    /**
     * Set ZRTP hash value received from the signaling layer.
     *
     * @param value hash value
     */
    fun setReceivedSignaledZRTPHashValue(value: String?) {
        receivedSignaledZRTPHashValue = value
    }

    companion object {
        /**
         * Each ZRTP packet has a fixed header of 12 bytes.
         */
        protected const val ZRTP_PACKET_HEADER = 12
    }
}