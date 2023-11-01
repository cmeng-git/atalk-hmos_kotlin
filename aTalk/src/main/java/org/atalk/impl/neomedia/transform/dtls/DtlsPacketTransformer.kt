/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls

import okhttp3.internal.notifyAll
import org.atalk.hmos.aTalkApp
import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.impl.neomedia.RTCPPacketPredicate
import org.atalk.impl.neomedia.RTPConnectorOutputStream
import org.atalk.impl.neomedia.RTPPacketPredicate
import org.atalk.impl.neomedia.transform.PacketTransformer
import org.atalk.impl.neomedia.transform.SinglePacketTransformer
import org.atalk.impl.neomedia.transform.srtp.SRTCPTransformer
import org.atalk.impl.neomedia.transform.srtp.SRTPTransformer
import org.atalk.impl.neomedia.transform.srtp.SrtpContextFactory
import org.atalk.impl.neomedia.transform.srtp.SrtpPolicy
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.DtlsControl.Setup
import org.atalk.service.neomedia.RawPacket
import org.atalk.util.ConfigUtils
import org.atalk.util.MediaType
import org.bouncycastle.tls.AlertDescription
import org.bouncycastle.tls.AlertLevel
import org.bouncycastle.tls.ContentType
import org.bouncycastle.tls.DTLSClientProtocol
import org.bouncycastle.tls.DTLSProtocol
import org.bouncycastle.tls.DTLSServerProtocol
import org.bouncycastle.tls.DTLSTransport
import org.bouncycastle.tls.DatagramTransport
import org.bouncycastle.tls.ExporterLabel
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.SRTPProtectionProfile
import org.bouncycastle.tls.TlsClientContext
import org.bouncycastle.tls.TlsContext
import org.bouncycastle.tls.TlsFatalAlert
import org.bouncycastle.tls.TlsPeer
import org.bouncycastle.tls.TlsServerContext
import org.bouncycastle.tls.TlsUtils
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import java.util.*

/**
 * Implements [PacketTransformer] for DTLS-SRTP. It's capable of working in
 * pure DTLS mode if appropriate flag was set in `DtlsControlImpl`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class DtlsPacketTransformer(
        /**
         * The `TransformEngine` which has initialized this instance.
         */
        private val transformEngine: DtlsTransformEngine,
        /**
         * The ID of the component which this instance works for/is associated with.
         */
        private val componentID: Int,
) : PacketTransformer, PropertyChangeListener {

    /**
     * The `RTPConnector` which uses this `PacketTransformer`.
     */
    private var mConnector: AbstractRTPConnector? = null

    /**
     * The background `Thread` which initializes [.mDtlsTransport].
     */
    private var connectThread: Thread? = null

    /**
     * The `DatagramTransport` implementation which adapts [.mConnector] and this
     * `PacketTransformer` to the terms of the Bouncy Castle Crypto APIs.
     */
    private var datagramTransport: DatagramTransportImpl? = null

    /**
     * The `DTLSTransport` through which the actual packet transformations are being performed by this instance.
     */
    private var mDtlsTransport: DTLSTransport? = null

    /**
     * The `MediaType` of the stream which this instance works for/is associated with.
     */
    private var mediaType: MediaType? = null

    /**
     * The `Queue` of SRTP `RawPacket`s which were received from the
     * remote while [._srtpTransformer] was unavailable i.e. `null`.
     */
    private val _reverseTransformSrtpQueue = LinkedList<RawPacket>()

    /**
     * Whether rtcp-mux is in use.
     *
     *
     * If enabled, and this is the transformer for RTCP, it will not establish
     * a DTLS session on its own, but rather wait for the RTP transformer to
     * do so, and reuse it to initialize the SRTP transformer.
     */
    private var rtcpmux = false

    /**
     * The `SRTPTransformer` (to be) used by this instance.
     */
    private var _srtpTransformer: SinglePacketTransformer? = null

    /**
     * The last time (in milliseconds since the epoch) that
     * [._srtpTransformer] was set to a non-`null` value.
     */
    private var _srtpTransformerLastChanged: Long = -1

    /**
     * The indicator which determines whether the `TlsPeer` employed by this `PacketTransformer`
     * has raised an `AlertDescription.close_notify` `AlertLevel.warning`
     * i.e. the remote DTLS peer has closed the write side of the connection.
     */
    private var tlsPeerHasRaisedCloseNotifyWarning = false

    /**
     * The `Queue` of SRTP `RawPacket`s which were to be sent to the
     * remote while [._srtpTransformer] was unavailable i.e. `null`.
     */
    private val _transformSrtpQueue = LinkedList<RawPacket?>()

    /**
     * Gets the `TransformEngine` which has initialized this instance.
     *
     * @return the `TransformEngine` which has initialized this instance
     */
    private var mStarted = false

    /**
     * Initializes a new `DtlsPacketTransformer` instance.
     *
     * transformEngine the `TransformEngine` which is initializing the new instance
     * componentID the ID of the component for which the new instance is to work
     */
    init {
        // Track the DTLS properties which control the conditional behaviors of DtlsPacketTransformer.
        properties.addPropertyChangeListener(this)
        propertyChange( /* propertyName */null as String?)
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun close() {
        properties.removePropertyChangeListener(this)

        // SrtpControl.start(MediaType) starts its associated TransformEngine.
        // We will use that mediaType to signal the normal stop then as well
        // i.e. we will call setMediaType(null) first.
        setMediaType(null)
        setConnector(null)
    }

    /**
     * Closes [.datagramTransport] if it is non-`null` and logs and swallows any `IOException`.
     */
    private fun closeDatagramTransport() {
        if (datagramTransport != null) {
            datagramTransport!!.close()
            datagramTransport = null
        }
    }

    /**
     * Determines whether [.runInConnectThread] is
     * to try to establish a DTLS connection.
     *
     * @param i the number of tries remaining after the current one
     * @param datagramTransport object sending and receiving DTLS data
     * @return `true` to try to establish a DTLS connection; otherwise, `false`
     */
    private fun enterRunInConnectThreadLoop(i: Int, datagramTransport: DatagramTransport): Boolean {
        if (i < 0 || i > CONNECT_TRIES) {
            return false
        }
        else {
            val currentThread = Thread.currentThread()
            synchronized(this) {
                if (i > 0 && i < CONNECT_TRIES - 1) {
                    var interrupted = false

                    try {
                        (this as Object).wait(CONNECT_RETRY_INTERVAL)
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                    if (interrupted)
                        currentThread.interrupt()
                }
                return currentThread == connectThread
                        && datagramTransport == this.datagramTransport
            }
        }
    }

    /**
     * Gets the `DtlsControl` implementation associated with this instance.
     *
     * @return the `DtlsControl` implementation associated with this instance
     */
    val dtlsControl: DtlsControlImpl
        get() = transformEngine.dtlsControl

    /**
     * Gets the properties of [DtlsControlImpl] and their values which
     * the associated `DtlsControlImpl` shares with this instance.
     *
     * @return the properties of `DtlsControlImpl` and their values which
     * the associated `DtlsControlImpl` shares with this instance
     */
    val properties: Properties
        get() = transformEngine.properties // Though _srtpTransformer is NOT initialized, there is no

    // point in waiting because there is no one to initialize it.
    // _srtpTransformer is initialized

    /**
     * Gets the `SRTPTransformer` (to be) used by this instance.
     *
     * @return the `SRTPTransformer` (to be) used by this instance
     */
    private val sRTPTransformer: SinglePacketTransformer?
        get() {
            var srtpTransformer = _srtpTransformer

            if (srtpTransformer != null)
                return srtpTransformer

            if (rtcpmux && DtlsTransformEngine.COMPONENT_RTCP == componentID)
                return initializeSRTCPTransformerFromRtp()

            // XXX It is our explicit policy to rely on the SrtpListener to notify the user that
            // the session is not secure. Unfortunately,
            // (1) the SrtpListener is not supported by this DTLS SrtpControl implementation and
            // (2) encrypted packets may arrive soon enough to be let through while _srtpTransformer is still initializing.
            // Consequently, we may wait for _srtpTransformer (a bit) to initialize.
            var yield = true
            do {
                if (_srtpTransformer != null) break // _srtpTransformer is initialized

                synchronized(this) {
                    srtpTransformer = _srtpTransformer
                    if (connectThread == null) {
                        // Though _srtpTransformer is NOT initialized, there is no
                        // point in waiting because there is no one to initialize it.
                        yield = false
                    }
                }

                if (yield) {
                    yield = false
                    Thread.yield()
                }
                else {
                    break
                }
            }
            while (true)

            return srtpTransformer
        }

    /**
     * Handles a specific `IOException` which was thrown during the execution of
     * [.runInConnectThread] while trying to establish a DTLS connection
     *
     * @param ioe the `IOException` to handle
     * @param msg the human-readable message to log about the specified `ioe`
     * @param i the number of tries remaining after the current one
     * @return `true` if the specified `ioe` was successfully handled; `false`, otherwise
     */
    private fun handleRunInConnectThreadException(ioe: IOException, msg: String, i: Int): Boolean {
        /*
         * SrtpControl.start(MediaType) starts its associated TransformEngine. We will use that
         * mediaType to signal the normal stop then as well i.e. we will ignore exception after the
         * procedure to stop this PacketTransformer has begun.
         */
        var msg = msg
        if (mediaType == null) return false

        if (ioe is TlsFatalAlert) {
            val alertDescription = ioe.alertDescription

            if (alertDescription == AlertDescription.unexpected_message) {
                msg += " Received fatal unexpected message."
                if (i == 0 || Thread.currentThread() != connectThread
                        || mConnector == null
                        || mediaType == null) {
                    msg += " Giving up after " + (CONNECT_TRIES - i) + " retries."
                }
                else {
                    msg += " Will retry."
                    Timber.e(ioe, "%s", msg)
                    return true
                }
            }
            else {
                msg += " Received fatal alert: " + ioe.message + "."
            }
        }
        Timber.e(ioe, "%s", msg)
        aTalkApp.showToastMessage(msg)
        return false
    }

    /**
     * Tries to initialize [._srtpTransformer] by using the `DtlsPacketTransformer` for RTP.
     * (The method invocations should be on the `DtlsPacketTransformer` for RTCP as the method name suggests.)
     *
     * @return the (possibly updated) value of [._srtpTransformer].
     */
    private fun initializeSRTCPTransformerFromRtp(): SinglePacketTransformer? {
        val rtpTransformer = transformEngine.rtpTransformer as DtlsPacketTransformer

        // Prevent recursion (that is pretty much impossible to ever happen).
        if (rtpTransformer != this) {
            val srtpTransformer = rtpTransformer.sRTPTransformer
            if (srtpTransformer is SRTPTransformer) {
                synchronized(this) {
                    if (_srtpTransformer == null) {
                        setSrtpTransformer(SRTCPTransformer(srtpTransformer))
                    }
                }
            }
        }
        return _srtpTransformer
    }

    /**
     * Initializes a new `SRTPTransformer` instance with a specific (negotiated)
     * `SRTPProtectionProfile` and the keying material specified by a specific `TlsContext`.
     * Note: Only call via notifyHandshakeComplete(), return value is not use
     *
     * @param srtpProtectionProfile the (negotiated) `SRTPProtectionProfile` to initialize the new instance with
     * @param tlsContext the `TlsContext` which represents the keying material
     * @return a new `SRTPTransformer` instance initialized with `srtpProtectionProfile` and `tlsContext`
     */
    fun initializeSRTPTransformer(srtpProtectionProfile: Int, tlsContext: TlsContext): SinglePacketTransformer? {
        if (isSrtpDisabled)
            return null

        val rtcp = when (componentID) {
            DtlsTransformEngine.COMPONENT_RTCP -> true
            DtlsTransformEngine.COMPONENT_RTP -> false
            else -> throw IllegalStateException("componentID")
        }

        val cipher_key_length: Int
        val cipher_salt_length: Int
        val aead_auth_tag_length: Int
        val cipher: Int
        val auth_function: Int
        val auth_key_length: Int
        var RTCP_auth_tag_length: Int
        var RTP_auth_tag_length: Int

        when (srtpProtectionProfile) {
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80 -> {
                cipher = SrtpPolicy.AESCM_ENCRYPTION
                cipher_key_length = 128 / 8
                cipher_salt_length = 112 / 8
                auth_function = SrtpPolicy.HMACSHA1_AUTHENTICATION
                auth_key_length = 160 / 8
                run {
                    RTCP_auth_tag_length = 80 / 8
                    RTP_auth_tag_length = RTCP_auth_tag_length
                }
            }

            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_32 -> {
                cipher = SrtpPolicy.AESCM_ENCRYPTION
                cipher_key_length = 128 / 8
                cipher_salt_length = 112 / 8
                auth_function = SrtpPolicy.HMACSHA1_AUTHENTICATION
                auth_key_length = 160 / 8
                RTP_auth_tag_length = 32 / 8
                RTCP_auth_tag_length = 80 / 8
            }

            SRTPProtectionProfile.SRTP_NULL_HMAC_SHA1_80 -> {
                cipher = SrtpPolicy.NULL_ENCRYPTION
                cipher_key_length = 0
                cipher_salt_length = 0
                auth_function = SrtpPolicy.HMACSHA1_AUTHENTICATION
                auth_key_length = 160 / 8
                run {
                    RTCP_auth_tag_length = 80 / 8
                    RTP_auth_tag_length = RTCP_auth_tag_length
                }
            }

            SRTPProtectionProfile.SRTP_NULL_HMAC_SHA1_32 -> {
                cipher = SrtpPolicy.NULL_ENCRYPTION
                cipher_key_length = 0
                cipher_salt_length = 0
                auth_function = SrtpPolicy.HMACSHA1_AUTHENTICATION
                auth_key_length = 160 / 8
                RTP_auth_tag_length = 32 / 8
                RTCP_auth_tag_length = 80 / 8
            }

            /*
             * RFC 7714 14.2 - correspond to the use of an AEAD algorithm
             * Note: SRTP protection profiles do not specify an auth_function, auth_key_length, or auth_tag_length,
             * because all of these profiles use AEAD algorithms and thus do not use a separate auth_function,
             * auth_key, or auth_tag. The term aead_auth_tag_length" is used to emphasize that this refers to the
             * authentication tag provided by the AEAD algorithm and that this tag is not located in the
             * authentication tag field provided by SRTP/SRTCP.
             */
            SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM -> {
                Timber.w("Unsupported RFC-7714 SRTP profile: %s", SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM)
                cipher = SrtpPolicy.AESCM_ENCRYPTION
                cipher_key_length = 128 / 8
                cipher_salt_length = 96 / 8
                aead_auth_tag_length = 16 // 16 octets
                auth_function = SrtpPolicy.NULL_ENCRYPTION
                auth_key_length = 0 // NA
                RTP_auth_tag_length = 0 // NA
                RTCP_auth_tag_length = 0 // NA
            }

            SRTPProtectionProfile.SRTP_AEAD_AES_256_GCM -> {
                Timber.w("Unsupported RFC-7714 SRTP profile: %s", SRTPProtectionProfile.SRTP_AEAD_AES_256_GCM)
                cipher = SrtpPolicy.AESCM_ENCRYPTION
                cipher_key_length = 256 / 8
                cipher_salt_length = 96 / 8
                aead_auth_tag_length = 16 // 16 octets
                auth_function = SrtpPolicy.NULL_ENCRYPTION
                auth_key_length = 0 // NA
                RTP_auth_tag_length = 0 // NA
                RTCP_auth_tag_length = 0 // NA
            }

            else ->
                throw IllegalArgumentException("srtpProtectionProfile")
        }

        /*
         * BouncyCastle >=1.59 clears the master secret from its session parameters immediately after connect,
         * making it unavailable. See https://github.com/bcgit/bc-java/issues/203:
         * Either call exportKeyingMaterial during the notifyHandshakeComplete callback OR
         * Available on TlsSession (the session keeps a copy of the master secret after handshake completion)
         */
        val length = 2 * (cipher_key_length + cipher_salt_length)
        var keyingMaterial: ByteArray?
        try {
            // must call via notifyHandshakeComplete() callback, otherwise sp.getMasterSecret() == null
            keyingMaterial = tlsContext.exportKeyingMaterial(ExporterLabel.dtls_srtp, null, length)
        } catch (ex: Exception) {
            keyingMaterial = exportKeyingMaterial(tlsContext, ExporterLabel.dtls_srtp, null, length)
            Timber.w("Export Keying Material without ExtendedMasterSecret for %s: %s",
                if (rtcp) "rtcp" else "rtp", keyingMaterial)
        }

        val client_write_SRTP_master_key = ByteArray(cipher_key_length)
        val server_write_SRTP_master_key = ByteArray(cipher_key_length)
        val client_write_SRTP_master_salt = ByteArray(cipher_salt_length)
        val server_write_SRTP_master_salt = ByteArray(cipher_salt_length)

        val keyingMaterialValues = arrayOf(
            client_write_SRTP_master_key,
            server_write_SRTP_master_key,
            client_write_SRTP_master_salt,
            server_write_SRTP_master_salt
        )

        if (keyingMaterial != null) {
            var i = 0
            var keyingMaterialOffset = 0
            while (i < keyingMaterialValues.size) {
                val keyingMaterialValue = keyingMaterialValues[i]
                System.arraycopy(keyingMaterial, keyingMaterialOffset,
                    keyingMaterialValue, 0, keyingMaterialValue.size)
                keyingMaterialOffset += keyingMaterialValue.size
                i++
            }
        }

        val srtcpPolicy = SrtpPolicy(
            cipher,
            cipher_key_length,
            auth_function,
            auth_key_length,
            RTCP_auth_tag_length,
            cipher_salt_length)

        val srtpPolicy = SrtpPolicy(
            cipher,
            cipher_key_length,
            auth_function,
            auth_key_length,
            RTP_auth_tag_length,
            cipher_salt_length)

        val clientSRTPContextFactory = SrtpContextFactory( /* sender */
            tlsContext is TlsClientContext,
            client_write_SRTP_master_key,
            client_write_SRTP_master_salt,
            srtpPolicy,
            srtcpPolicy)

        val serverSRTPContextFactory = SrtpContextFactory( /* sender */
            tlsContext is TlsServerContext,
            server_write_SRTP_master_key,
            server_write_SRTP_master_salt,
            srtpPolicy,
            srtcpPolicy)

        val forwardSRTPContextFactory: SrtpContextFactory
        val reverseSRTPContextFactory: SrtpContextFactory

        when (tlsContext) {
            is TlsClientContext -> {
                forwardSRTPContextFactory = clientSRTPContextFactory
                reverseSRTPContextFactory = serverSRTPContextFactory
            }

            is TlsServerContext -> {
                forwardSRTPContextFactory = serverSRTPContextFactory
                reverseSRTPContextFactory = clientSRTPContextFactory
            }

            else -> {
                throw IllegalArgumentException("tlsContext")
            }
        }

        val srtpTransformer: SinglePacketTransformer = if (rtcp) {
            SRTCPTransformer(forwardSRTPContextFactory, reverseSRTPContextFactory)
        }
        else {
            SRTPTransformer(forwardSRTPContextFactory, reverseSRTPContextFactory)
        }

        Timber.d("SinglePacketTransformer initialized (%s/%s); profile = %s; tlsPeer: %s", mediaType.toString(),
            componentID, srtpProtectionProfile, tlsContext)
        setSrtpTransformer(srtpTransformer)
        return srtpTransformer
    }

    val secureCommunicationStatus: Boolean
        get() = _srtpTransformer != null

    /**
     * Determines whether this `DtlsPacketTransformer` is to operate in
     * pure DTLS mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return `true` for pure DTLS without SRTP extensions or `false` for DTLS/SRTP
     */
    private val isSrtpDisabled: Boolean
        get() = properties.isSrtpDisabled

    @Synchronized
    private fun maybeStart() {
        if (mediaType != null && mConnector != null && !mStarted) {
            start()
        }
    }

    /**
     * Notifies this instance that the DTLS record layer associated with a specific `TlsPeer` has raised an alert.
     *
     * @param alertLevel [AlertLevel]
     * @param alertDescription [AlertDescription]
     * @param message a human-readable message explaining what caused the alert. May be `null`.
     * @param cause the exception that caused the alert to be raised. May be `null`.
     */
    fun notifyAlertRaised(
            tlsPeer: TlsPeer?, alertLevel: Short, alertDescription: Short,
            message: String?, cause: Throwable?,
    ) {
        if (AlertLevel.warning == alertLevel
                && AlertDescription.close_notify == alertDescription) {
            tlsPeerHasRaisedCloseNotifyWarning = true
        }
        dtlsControl.notifyAlertRaised(tlsPeer, alertLevel, alertDescription, message, cause)
    }

    override fun propertyChange(ev: PropertyChangeEvent) {
        propertyChange(ev.propertyName)
    }

    private fun propertyChange(propertyName: String?) {
        // This DtlsPacketTransformer calls the method with null at construction
        // time to initialize the respective states.
        when {
            propertyName == null -> {
                propertyChange(Properties.RTCPMUX_PNAME)
                propertyChange(Properties.MEDIA_TYPE_PNAME)
                propertyChange(Properties.CONNECTOR_PNAME)
            }

            Properties.CONNECTOR_PNAME == propertyName -> {
                setConnector(properties[propertyName] as AbstractRTPConnector?)
            }

            Properties.MEDIA_TYPE_PNAME == propertyName -> {
                setMediaType(properties[propertyName] as MediaType?)
            }

            Properties.RTCPMUX_PNAME == propertyName -> {
                val newValue = properties[propertyName]
                setRtcpmux(newValue != null && newValue as Boolean)
            }
        }
    }

    /**
     * Queues `RawPacket`s to be supplied to
     * [.transformSrtp] when [._srtpTransformer] becomes available.
     *
     * @param pkts the `RawPacket`s to queue
     * @param transform `true` if `pkts` are to be sent to the
     * remote peer or `false` if `pkts` were received from the remote peer
     */
    private fun queueTransformSrtp(pkts: Array<RawPacket?>?, transform: Boolean) {
        if (pkts != null) {
            val q: Queue<RawPacket?> = if (transform) _transformSrtpQueue else _reverseTransformSrtpQueue
            synchronized(q) {
                for (pkt in pkts) {
                    if (pkt != null) {
                        while (q.size >= TRANSFORM_QUEUE_CAPACITY && q.poll() != null) {
                        }
                        q.add(pkt)
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun reverseTransform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return transform(pkts, false)
    }

    /**
     * Processes a DTLS `RawPacket` received from the remote peer, and
     * reads any available application data into `outPkts`.
     *
     * @param pkt the DTLS `RawPacket` received from the remote peer to process.
     * @param outPkts a list of packets, to which application data read from
     * the DTLS transport should be appended. If `null`, application data will not be read.
     */
    private fun reverseTransformDtls(pkt: RawPacket, outPkts: MutableList<RawPacket?>?) {
        if (rtcpmux && DtlsTransformEngine.COMPONENT_RTCP == componentID) {
            // This should never happen.
            Timber.w("Dropping a DTLS packet, because it was received on the RTCP channel while rtcpmux is in use.")
            return
        }

        // First, make the input packet available for bouncycastle to read.
        synchronized(this) {
            if (datagramTransport == null) {
                Timber.w("Dropping a DTLS packet. This DtlsPacketTransformer has not been started successfully or has been closed.")
            }
            else {
                datagramTransport!!.queueReceive(pkt.buffer, pkt.offset, pkt.length)
            }
        }

        if (outPkts == null) {
            return
        }

        // Next, try to read any available application data from bouncycastle.
        // The DTLS transport hasn't initialized yet if null.
        if (mDtlsTransport != null) {
            // There might be more than one packet queued in datagramTransport,
            // if they were added prior to dtlsTransport being initialized. Read all of them.
            try {
                do {
                    val receiveLimit = mDtlsTransport!!.receiveLimit
                    // FIXME This is at best inefficient, but it is not meant as a long-term solution.
                    // A major refactoring is planned, which will probably make this code obsolete.
                    val buf = ByteArray(receiveLimit)
                    val p = RawPacket(buf, 0, buf.size)
                    val received = mDtlsTransport!!.receive(buf, 0, buf.size, DTLS_TRANSPORT_RECEIVE_WAITMILLIS)
                    if (received <= 0) {
                        // No (more) application data was decoded.
                        break
                    }
                    else {
                        p.length = received
                        outPkts.add(p)
                    }
                }
                while (true)
            } catch (ioe: IOException) {
                // SrtpControl.start(MediaType) starts its associated
                // TransformEngine. We will use that mediaType to signal the
                // normal stop then as well i.e. we will ignore exception after
                // the procedure to stop this PacketTransformer has begun.
                if (mediaType != null && !tlsPeerHasRaisedCloseNotifyWarning) {
                    Timber.e(ioe, "Failed to decode a DTLS record!")
                }
            }
        }
    }

    /**
     * Runs in [.connectThread] to initialize [.mDtlsTransport].
     *
     * @param dtlsProtocol server or client TLS protocol
     * @param tlsPeer TLS peer
     * @param datagramTransport UDP DatagramTransport
     */
    private fun runInConnectThread(dtlsProtocol: DTLSProtocol, tlsPeer: TlsPeer, datagramTransport: DatagramTransport) {
        // DTLS client
        if (dtlsProtocol is DTLSClientProtocol) {
            val tlsClient = tlsPeer as TlsClientImpl

            for (i in CONNECT_TRIES - 1 downTo 0) {
                if (!enterRunInConnectThreadLoop(i, datagramTransport)) break
                try {
                    mDtlsTransport = dtlsProtocol.connect(tlsClient, datagramTransport)
                    break
                } catch (ioe: IOException) {
                    if (handleRunInConnectThreadException(ioe, "Failed to connect DTLS client to server!", i)) {
                        break
                    }
                }
            }
        }
        // DTLS server
        else if (dtlsProtocol is DTLSServerProtocol) {
            val tlsServer = tlsPeer as TlsServerImpl
            for (i in CONNECT_TRIES - 1 downTo 0) {
                if (!enterRunInConnectThreadLoop(i, datagramTransport))
                    break
                try {
                    mDtlsTransport = dtlsProtocol.accept(tlsServer, datagramTransport)
                    break
                } catch (ioe: IOException) {
                    if (handleRunInConnectThreadException(ioe, "Failed to accept DTLS client connection!", i)) {
                        break
                    }
                }
            }
        }
        else {
            // It MUST be either a DTLS client or a DTLS server.
            throw IllegalStateException("dtlsProtocol")
        }
    }

    /**
     * Sends the data contained in a specific byte array as application data through the DTLS
     * connection of this `DtlsPacketTransformer`.
     *
     * @param buf the byte array containing data to send.
     * @param off the offset in `buf` where the data begins.
     * @param len the length of data to send.
     */
    private fun sendApplicationData(buf: ByteArray?, off: Int, len: Int) {
        val dtlsTransport = mDtlsTransport
        var throwable: Throwable? = null

        if (dtlsTransport != null) {
            try {
                dtlsTransport.send(buf, off, len)
            } catch (ioe: IOException) {
                throwable = ioe
            }
        }
        else {
            throwable = NullPointerException("dtlsTransport")
        }
        if (throwable != null) {
            /*
             * SrtpControl.start(MediaType) starts its associated TransformEngine. We will use that
             * mediaType to signal the normal stop then as well i.e. we will ignore exception after
             * the procedure to stop this PacketTransformer has begun.
             */
            if (mediaType != null && !tlsPeerHasRaisedCloseNotifyWarning) {
                Timber.e(throwable, "Failed to send application data over DTLS transport.")
            }
        }
    }

    /**
     * Sets the `RTPConnector` which is to use or uses this `PacketTransformer`.
     *
     * @param connector the `RTPConnector` which is to use or uses this `PacketTransformer`
     */
    @Synchronized
    private fun setConnector(connector: AbstractRTPConnector?) {
        if (mConnector != connector) {
            mConnector = connector

            val datagramTransport = this.datagramTransport
            datagramTransport?.setConnector(connector)

            if (connector != null)
                maybeStart()
        }
    }

    /**
     * Sets the `MediaType` of the stream which this instance is to work for/be associated with.
     *
     * @param mediaType the `MediaType` of the stream which this instance is to work for/be associated with
     */
    @Synchronized
    private fun setMediaType(mediaType: MediaType?) {
        if (this.mediaType != mediaType) {
            val oldValue = this.mediaType
            this.mediaType = mediaType
            if (oldValue != null)
                stop()
            if (this.mediaType != null)
                maybeStart()
        }
    }

    /**
     * Enables/disables rtcp-mux.
     *
     * @param rtcpmux `true` to enable rtcp-mux or `false` to disable it.
     */
    private fun setRtcpmux(rtcpmux: Boolean) {
        this.rtcpmux = rtcpmux
    }

    /**
     * Sets [._srtpTransformer] to a specific value.
     *
     * @param srtpTransformer the `SinglePacketTransformer` to set on `_srtpTransformer`
     */
    @Synchronized
    fun setSrtpTransformer(srtpTransformer: SinglePacketTransformer?) {
        if (_srtpTransformer != srtpTransformer) {
            val oldTransformer = _srtpTransformer
            oldTransformer?.close()

            _srtpTransformer = srtpTransformer
            _srtpTransformerLastChanged = System.currentTimeMillis()

            // For the sake of completeness, we notify whenever we assign to srtpTransformer.
            notifyAll()
        }
    }

    /**
     * Starts this `PacketTransformer`.
     */
    @Synchronized
    private fun start() {
        if (datagramTransport != null) {
            if (connectThread == null && mDtlsTransport == null) {
                Timber.w("%s has been started but has failed to establish the DTLS connection!",
                    javaClass.name)
            }
            return
        }

        if (rtcpmux && DtlsTransformEngine.COMPONENT_RTCP == componentID) {
            /*
             * In the case of rtcp-mux, the RTCP transformer does not create a DTLS session.
             * The SRTP context (srtpTransformer) will be initialized on demand using
             * initializeSRTCPTransformerFromRtp()
             */
            return
        }

        val connector = mConnector
        mStarted = true
        if (connector == null)
            throw NullPointerException("connector")

        val setup = properties.setup
        val dtlsProtocol: DTLSProtocol
        val tlsPeer: TlsPeer

        if (Setup.ACTIVE == setup) {
            dtlsProtocol = DTLSClientProtocol()
            tlsPeer = TlsClientImpl(this)
        }
        else {
            dtlsProtocol = DTLSServerProtocol()
            tlsPeer = TlsServerImpl(this)
        }

        tlsPeerHasRaisedCloseNotifyWarning = false
        val datagramTransport = DatagramTransportImpl(componentID)
        datagramTransport.setConnector(connector)

        val connectThread: Thread = object : Thread() {
            override fun run() {
                try {
                    runInConnectThread(dtlsProtocol, tlsPeer, datagramTransport)
                } finally {
                    if (currentThread() == connectThread) {
                        connectThread = null
                        dtlsControl.secureOnOff(secureCommunicationStatus)
                    }
                }
            }
        }

        connectThread.isDaemon = true
        connectThread.name = DtlsPacketTransformer::class.java.name + ".connectThread"

        this.connectThread = connectThread
        this.datagramTransport = datagramTransport
        var started = false

        try {
            connectThread.start()
            started = true
        } finally {
            if (!started) {
                if (connectThread == this.connectThread)
                    this.connectThread = null
                if (datagramTransport == this.datagramTransport)
                    this.datagramTransport = null
            }
        }
        notifyAll()
    }

    /**
     * Stops this `PacketTransformer`.
     */
    @Synchronized
    private fun stop() {
        mStarted = false
        if (connectThread != null) connectThread = null
        try {
            /*
             * The dtlsTransport and srtpTransformer SHOULD be closed, of course. The datagramTransport MUST be closed.
             */
            if (mDtlsTransport != null) {
                try {
                    mDtlsTransport!!.close()
                } catch (ioe: IOException) {
                    Timber.e(ioe, "Failed to (properly) close %s", mDtlsTransport!!.javaClass)
                }
                mDtlsTransport = null
            }

            if (_srtpTransformer != null) {
                _srtpTransformer!!.close()
                _srtpTransformer = null
            }
        } finally {
            try {
                closeDatagramTransport()
            } finally {
                notifyAll()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun transform(pkts: Array<RawPacket?>): Array<RawPacket?> {
        return transform(pkts,  /* transform */true)
    }

    /**
     * Processes `RawPacket`s to be sent to or received from (depending on `transform`) the remote peer.
     *
     * @param inPkts the `RawPacket`s to be sent to or received from
     * (depending on `transform` the remote peer
     * @param transform `true` of `inPkts` are to be sent to the
     * remote peer or `false` if `inPkts` have been received from the remote peer
     * @return the `RawPacket`s which are the result of the processing
     */
    private fun transform(inPkts: Array<RawPacket?>?, transform: Boolean): Array<RawPacket?> {
        var outPkts = ArrayList<RawPacket?>()

        // DTLS and SRTP packets are distinct, separate (in DTLS-SRTP).
        // Additionally, the UDP transport does not guarantee the packet send order.
        // Consequently, it should be fine to process DTLS packets first.
        outPkts = transformDtls(inPkts, transform, outPkts)
        outPkts = ArrayList(transformNonDtls(inPkts, transform, outPkts))

        return outPkts.toTypedArray()
    }

    /**
     * Processes DTLS `RawPacket`s to be sent to or received from
     * (depending on `transform`) the remote peer. The implementation
     * picks the elements of `inPkts` which look like DTLS records and
     * replaces them with `null`.
     *
     * @param inPkts the `RawPacket`s to be sent to or received from
     * (depending on `transform` the remote peer among which there may (or
     * may not) be DTLS `RawPacket`s
     * @param transform `true` of `inPkts` are to be sent to the
     * remote peer or `false` if `inPkts` have been received from the remote peer
     * @param outPkts the `List` of `RawPacket`s into which the
     * results of the processing of the DTLS `RawPacket`s are to be written
     * @return the `List` of `RawPacket`s which are the result of
     * the processing including the elements of `outPkts`. Practically, `outPkts` itself.
     */
    private fun transformDtls(
            inPkts: Array<RawPacket?>?, transform: Boolean, outPkts: ArrayList<RawPacket?>,
    ): ArrayList<RawPacket?> {
        if (inPkts != null) {
            for (i in inPkts.indices) {
                val inPkt = inPkts[i] ?: continue
                val buf = inPkt.buffer
                val off = inPkt.offset
                val len = inPkt.length
                if (isDtlsRecord(buf, off, len)) {
                    // In the outgoing/transform direction DTLS records pass
                    // through (e.g. DatagramTransportImpl has sent them).
                    if (transform) {
                        outPkts.add(inPkt)
                    }
                    else {
                        reverseTransformDtls(inPkt, outPkts)
                    }

                    // Whatever the outcome, inPkt has been consumed. The following is being done
                    // because there may be a subsequent iteration over inPkts later on.
                    inPkts[i] = null
                }
            }
        }
        return outPkts
    }

    /**
     * Processes non-DTLS `RawPacket`s to be sent to or received from (depending on `transform`) the remote peer.
     * The implementation assumes that all elements of `inPkts` are non-DTLS `RawPacket`s.
     *
     * @param inPkts the `RawPacket`s to be sent to or received from (depending on `transform` the remote peer
     * @param transform `true` of `inPkts` are to be sent to the remote peer or `false` if `inPkts` have been
     * received from the remote peer
     * @param oPkts the `List` of `RawPacket`s into which the results of the processing of `inPkts` are to be written
     * @return the `List` of `RawPacket`s which are the result of
     * the processing including the elements of `outPkts`. Practically, `outPkts` itself.
     */
    private fun transformNonDtls(
            inPkts: Array<RawPacket?>?, transform: Boolean, oPkts: List<RawPacket?>,
    ): List<RawPacket?> {
        /* Pure/non-SRTP DTLS */
        var outPkts = oPkts
        if (isSrtpDisabled) {
            // (1) In the incoming/reverseTransform direction, only DTLS records pass through.
            // (2) In the outgoing/transform direction, the specified inPkts
            // will pass through this PacketTransformer only if they get transformed into DTLS records.
            if (transform) outPkts = transformNonSrtp(inPkts, outPkts)
        }
        else {
            /* SRTP */
            transformSrtp(inPkts, transform, outPkts)
        }
        return outPkts
    }

    /**
     * Processes non-SRTP `RawPacket`s to be sent to the remote peer. The
     * implementation assumes that all elements of `inPkts` are non-SRTP `RawPacket`s.
     *
     * @param inPkts the `RawPacket`s to be sent to the remote peer
     * @param outPkts the `List` of `RawPacket`s into which the results of the processing of `inPkts` are to be written
     * @return the `List` of `RawPacket`s which are the result of the processing including the elements of `outPkts`.
     * Practically, `outPkts` itself. The implementation does not produce its own
     * `RawPacket`s though because it merely wraps `inPkts` into DTLS application data.
     */
    private fun transformNonSrtp(inPkts: Array<RawPacket?>?, outPkts: List<RawPacket?>): List<RawPacket?> {
        if (inPkts != null) {
            for (inPkt in inPkts) {
                if (inPkt == null) continue
                val buf = inPkt.buffer
                val off = inPkt.offset
                val len = inPkt.length
                sendApplicationData(buf, off, len)
            }
        }
        return outPkts
    }

    /**
     * Processes SRTP `RawPacket`s to be sent or received (depending on `transform`) the remote peer.
     * The implementation assumes that all elements of `inPkts` are SRTP `RawPacket`s.
     *
     * @param inPkts the SRTP `RawPacket`s to be sent to or received from (depending on `transform`) the remote peer
     * @param transform `true` of `inPkts` are to be sent to the
     * remote peer or `false` if `inPkts` have been received from the remote peer
     * @param oPkts the `List` of `RawPacket`s into which the results of the processing of `inPkts` are to be written
     * @return the `List` of `RawPacket`s which are the result of
     * the processing including the elements of `outPkts`. Practically, `outPkts` itself.
     */
    private fun transformSrtp(
            inPkts: Array<RawPacket?>?, transform: Boolean, oPkts: List<RawPacket?>,
    ): List<RawPacket?> {
        var outPkts = oPkts
        val srtpTransformer = sRTPTransformer
        if (srtpTransformer == null) {
            // If unencrypted (SRTP) packets are to be dropped, they are dropped by not being processed here.
            if (!DROP_UNENCRYPTED_PKTS) {
                queueTransformSrtp(inPkts, transform)
            }
        }
        else {
            // Process the (SRTP) packets provided to earlier (method)
            // invocations during which _srtpTransformer was unavailable.
            val q = if (transform) _transformSrtpQueue else _reverseTransformSrtpQueue

            // XXX Don't obtain a lock if the queue is empty. If a thread was in
            // the process of adding packets to it, they will be handled in a
            // subsequent call. If the queue is empty, as it usually is, the
            // call to transformSrtp below is unnecessary, so we can avoid the lock.
            if (q.size > 0) {
                synchronized(q) {

                    // WARNING: this is a temporary workaround for an issue we
                    // have observed in which a DtlsPacketTransformer is shared
                    // between multiple MediaStream instances and the packet
                    // queue contains packets belonging to both. We try to
                    // recognize the packets belonging to each MediaStream by
                    // their RTP SSRC or RTP payload type, and pull only these
                    // packets into the output array. We use the input packet
                    // (or rather the first input packet) as a template, because
                    // it comes from the MediaStream which called us.
                    val template = if (inPkts != null && inPkts.isNotEmpty()) inPkts[0] else null
                    outPkts = try {
                        transformSrtp(srtpTransformer, q, transform, outPkts.toMutableList(), template)
                    } finally {
                        // If a RawPacket from q causes an exception, do not attempt to process it next time.
                        clearQueue(q, template)
                    }
                }
            }

            // Process the (SRTP) packets provided to the current (method) invocation.
            if (inPkts != null && inPkts.isNotEmpty()) {
                outPkts = transformSrtp(srtpTransformer, listOf(*inPkts), transform, outPkts.toMutableList(), null)
            }
        }
        return outPkts
    }

    /**
     * Processes SRTP `RawPacket`s to be sent to or received from
     * (depending on `transform`) the remote peer. The implementation
     * assumes that all elements of `inPkts` are SRTP `RawPacket`s.
     *
     * @param srtpTransformer the `SinglePacketTransformer` to perform the actual processing
     * @param inPkts the SRTP `RawPacket`s to be sent to or received from (depending on `transform`) the remote peer
     * @param transform `true` of `inPkts` are to be sent to the
     * remote peer or `false` if `inPkts` have been received from the remote peer
     * @param outPkts the `List` of `RawPacket`s into which the results of the processing of `inPkts` are to be written
     * @param template A template to match input packets. Only input packets matching this template
     * (checked with [.match]) will be processed. A null template matches all packets.
     * @return the `List` of `RawPacket`s which are the result of
     * the processing including the elements of `outPkts`. Practically, `outPkts` itself.
     */
    private fun transformSrtp(
            srtpTransformer: SinglePacketTransformer, inPkts: Collection<RawPacket?>,
            transform: Boolean, outPkts: MutableList<RawPacket?>, template: RawPacket?,
    ): List<RawPacket?> {
        for (inPkt in inPkts) {
            if (inPkt != null && match(template, inPkt)) {
                val outPkt = if (transform) srtpTransformer.transform(inPkt)
                else srtpTransformer.reverseTransform(inPkt)
                if (outPkt != null) outPkts.add(outPkt)
            }
        }
        return outPkts
    }

    /**
     * Removes from `q` all packets matching `template` (checked with [.match]. A null `template` matches all packets.
     *
     * @param q the queue to remove packets from.
     * @param template the template
     */
    private fun clearQueue(q: LinkedList<out RawPacket?>, template: RawPacket?) {
        val srtpTransformerLastChanged = _srtpTransformerLastChanged
        if (srtpTransformerLastChanged >= 0
                && System.currentTimeMillis() - srtpTransformerLastChanged > 3000) {
            // The purpose of these queues is to queue packets while DTLS is in
            // the process of establishing a connection. If some of the packets
            // were not "read" 3 seconds after DTLS finished, they can safely be
            // dropped, and we do so to avoid looping through the queue on every subsequent packet.
            q.clear()
            return
        }
        val it = q.iterator()
        while (it.hasNext()) {
            if (match(template, it.next())) it.remove()
        }
    }

    /**
     * Checks whether `pkt` matches the template `template`. A `null` template matches all packets,
     * while a `null` packet will only be matched by a `null` template. Two non-`null` packets match
     * if they are both RTP or both RTCP and they have the same SSRC or the same RTP Payload Type.
     * The goal is for a template packet from one `MediaStream` to match the packets for that stream,
     * and only these packets.
     *
     * @param template the template.
     * @param pkt the packet.
     * @return `true` if `template` matches `pkt` (i.e. they have the same SSRC or RTP Payload Type).
     */
    private fun match(template: RawPacket?, pkt: RawPacket?): Boolean {
        if (template == null) return true
        if (pkt == null) return false

        return when {
            RTPPacketPredicate.INSTANCE.test(template) -> {
                (template.getSSRC() == pkt.getSSRC()
                        || template.payloadType == pkt.payloadType)
            }
            RTCPPacketPredicate.INSTANCE.test(template) -> {
                template.rtcpSSRC == pkt.rtcpSSRC
            }
            else -> true
        }
    }

    companion object {
        /**
         * The interval in milliseconds between successive tries to await successful connections in [.runInConnectThread].
         *
         * @see .CONNECT_TRIES
         */
        private const val CONNECT_RETRY_INTERVAL = 500L

        /**
         * The maximum number of times that [.runInConnectThread] is to retry the invocations of
         * [DTLSClientProtocol.connect] and [DTLSServerProtocol.accept] in anticipation of a successful connection.
         *
         * @see .CONNECT_RETRY_INTERVAL
         */
        private const val CONNECT_TRIES = 3

        /**
         * The indicator which determines whether unencrypted packets sent or received through
         * `DtlsPacketTransformer` are to be dropped. The default value is `false`.
         *
         * @see .DROP_UNENCRYPTED_PKTS_PNAME
         */
        private val DROP_UNENCRYPTED_PKTS: Boolean

        /**
         * The name of the `ConfigurationService` and/or `System` property which
         * indicates whether unencrypted packets sent or received through
         * `DtlsPacketTransformer` are to be dropped. The default value is `false`.
         */
        private val DROP_UNENCRYPTED_PKTS_PNAME = DtlsPacketTransformer::class.java.name + ".dropUnencryptedPkts"

        /**
         * The length of the header of a DTLS record.
         * +1 content_type
         * +2 version
         * +2 epoch
         * +6 sequence_number
         * +2 length (record layer fragment)
         */
        const val DTLS_RECORD_HEADER_LENGTH = 13

        /**
         * The number of milliseconds a `DtlsPacketTransform` is to wait on its
         * [.mDtlsTransport] in order to receive a packet. -1 not allow in tls.DTLSTransport
         */
        private const val DTLS_TRANSPORT_RECEIVE_WAITMILLIS = 100 // -1

        /**
         * The maximum number of elements of queues such as [._reverseTransformSrtpQueue] and [.transformSrtpQueue].
         * Defined in order to reduce excessive memory use (which may lead to [OutOfMemoryError]s, for example).
         */
        private val TRANSFORM_QUEUE_CAPACITY = RTPConnectorOutputStream.PACKET_QUEUE_CAPACITY

        init {
            val cfg = LibJitsi.configurationService
            DROP_UNENCRYPTED_PKTS = ConfigUtils.getBoolean(cfg, DROP_UNENCRYPTED_PKTS_PNAME, false)
        }

        /**
         * Determines whether a specific array of `byte`s appears to contain a DTLS record.
         *
         * @param buf the array of `byte`s to be analyzed
         * @param off the offset within `buf` at which the analysis is to start
         * @param len the number of bytes within `buf` starting at `off` to be analyzed
         * @return `true` if the specified `buf` appears to contain a DTLS record
         */
        fun isDtlsRecord(buf: ByteArray, off: Int, len: Int): Boolean {
            if (len >= DTLS_RECORD_HEADER_LENGTH) {
                when (TlsUtils.readUint8(buf, off)) {
                    ContentType.alert,
                    ContentType.application_data,
                    ContentType.change_cipher_spec,
                    ContentType.handshake,
                    -> {
                        val major = buf[off + 1].toInt() and 0xff
                        val minor = buf[off + 2].toInt() and 0xff

                        val version = ProtocolVersion.get(major, minor)
                        if (version.isDTLS) {
                            val length = TlsUtils.readUint16(buf, off + 11)
                            if (DTLS_RECORD_HEADER_LENGTH + length <= len) {
                                return true
                            }
                        }
                    }
                    else -> {}
                }
            }
            return false
        }

        /**
         * Copied from AbstractTlsContext#exportKeyingMaterial and modified to work with an externally
         * provided masterSecret value. One without the Extended Master Password
         */
        private fun exportKeyingMaterial(
                tlsContext: TlsContext, asciiLabel: String, context_value: ByteArray?, length: Int,
        ): ByteArray {
            require(!(context_value != null && !TlsUtils.isValidUint16(context_value.size))) {
                "'context_value' must have length less than 2^16 (or be null)"
            }

            val securityParameters = tlsContext.securityParametersConnection
                    ?: throw IllegalStateException("Export of key material unavailable before handshake completion")

            val seed = TlsUtils.calculateExporterSeed(securityParameters, context_value)
            val tlsSecret = securityParameters.masterSecret

            // pass sp instead of context as securityParametersHandshake is clear on AbstractTlsContext#handshakeComplete
            return TlsUtils.PRF(securityParameters, tlsSecret, asciiLabel, seed, length).extract()
        }
    }
}