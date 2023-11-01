/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.zrtp

import gnu.java.zrtp.ZrtpCodes.*
import gnu.java.zrtp.ZrtpUserCallback
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.event.SrtpListener
import org.atalk.util.MediaType
import timber.log.Timber
import java.util.*

/**
 * The user callback class for ZRTP4J.
 *
 * This class constructs and sends events to the ZRTP GUI implementation. The
 * `showMessage()` function implements a specific check to start
 * associated ZRTP multi-stream sessions.
 *
 * Coordinate this callback class with the associated GUI implementation class
 *
 * @author Emanuel Onica
 * @author Werner Dittmann
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 * @see net.java.sip.communicator.impl.gui.main.call.ZrtpSecurityPanel
`` */
class SecurityEventManager(
        /**
         * The zrtp control that this manager is associated with.
         */
        private val zrtpControl: ZrtpControlImpl) : ZrtpUserCallback() {
    /**
     * The event manager that belongs to the ZRTP master session.
     */
    private var masterEventManager: SecurityEventManager? = null

    /**
     * A callback to the instance that created us.
     */
    private var securityListener: SrtpListener?

    /**
     * The type of this session.
     */
    private var sessionType: MediaType? = null

    /**
     * SAS string.
     */
    private var sas: String? = null
    /**
     * Gets the cipher information for the current media stream.
     *
     * @return the cipher information string.
     */
    /**
     * Cipher.
     */
    var cipherString: String? = null
        private set

    /**
     * Indicates if the SAS has already been verified in a previous session.
     */
    private var isSasVerified = false

    /**
     * The class constructor.
     *
     * zrtpControl that this manager is to be associated with.
     */
    init {
        securityListener = zrtpControl.srtpListener
    }

    /**
     * Set the type of this session.
     *
     * @param sessionType the `MediaType` of this session
     */
    fun setSessionType(sessionType: MediaType?) {
        this.sessionType = sessionType
    }

    /**
     * Sets the event manager that belongs to the ZRTP master session.
     *
     * @param master the event manager that belongs to the ZRTP master session.
     */
    fun setMasterEventManager(master: SecurityEventManager?) {
        masterEventManager = master
    }
    /*
     * The following methods implement the ZrtpUserCallback interface
     */
    /**
     * Reports the security algorithm that the ZRTP protocol negotiated.
     *
     * @param cipher the cipher
     */
    override fun secureOn(cipher: String) {
        Timber.d("%s: cipher enabled: %s", sessionTypeToString(sessionType), cipher)
        cipherString = cipher
    }

    /**
     * ZRTP computes the SAS string after nearly all the negotiation and computations are done internally.
     *
     * @param sas The string containing the SAS.
     * @param isVerified is sas verified.
     */
    override fun showSAS(sas: String, isVerified: Boolean) {
        Timber.d("%s: SAS is: %s", sessionTypeToString(sessionType), sas)
        this.sas = sas
        isSasVerified = isVerified
    }

    /**
     * Sets current SAS verification status.
     *
     * @param isVerified flag indicating whether SAS has been verified.
     */
    fun setSASVerified(isVerified: Boolean) {
        isSasVerified = securityString != null && isVerified
    }

    /**
     * Show some information to user. ZRTP calls this method to display some information to the user.
     * Along with the message ZRTP provides a severity indicator that defines: Info, Warning, Error, and Alert.
     *
     * @param sev severity of the message.
     * @param subCode the message code.
     */
    override fun showMessage(sev: MessageSeverity, subCode: EnumSet<*>) {
        val ii: Iterator<*> = subCode.iterator()
        val msgCode = ii.next()!!
        var message: String? = null
        var i18nMessage: String? = null
        var severity = 0
        var sendEvent = true

        when (msgCode) {
            is InfoCodes -> {
                // Use the following fields if INFORMATION type messages shall be shown to the user via
                // SecurityMessageEvent, i.e. if sendEvent is set to true
                // severity = CallPeerSecurityMessageEvent.INFORMATION;

                // Don't spam user with info messages, only internal processing or logging.
                sendEvent = false

                // If the ZRTP Master session (DH mode) signals "security on" then start multi-stream sessions.
                // Signal SAS to GUI only if this is a DH mode session. Multi-stream session don't have own SAS data
                if (msgCode == InfoCodes.InfoSecureStateOn) {
                    securityListener!!.securityTurnedOn(sessionType, cipherString, zrtpControl)
                }
            }

            is WarningCodes -> {
                // Warning codes usually do not affect encryption or security.
                // Only in few cases inform the user and ask to verify SAS.
                severity = SrtpListener.WARNING
                when (msgCode) {
                    WarningCodes.WarningNoRSMatch -> {
                        message = "No retained shared secret available."
                        i18nMessage = WARNING_NO_RS_MATCH
                    }
                    WarningCodes.WarningNoExpectedRSMatch -> {
                        message = "An expected retained shared secret is missing."
                        i18nMessage = WARNING_NO_EXPECTED_RS_MATCH
                    }
                    WarningCodes.WarningCRCmismatch -> {
                        message = "Internal ZRTP packet checksum mismatch."
                        i18nMessage = aTalkApp.getResString(R.string.impl_media_security_CHECKSUM_MISMATCH)
                    }
                    else -> {
                        // Other warnings are internal only, no user action required
                        sendEvent = false
                    }
                }
            }

            is SevereCodes -> {
                severity = SrtpListener.SEVERE
                when (msgCode) {
                    SevereCodes.SevereCannotSend -> {
                        message = "Failed to send data. Internet data connection or peer is down."
                        i18nMessage = aTalkApp.getResString(R.string.impl_media_security_DATA_SEND_FAILED, msgCode.toString())
                    }
                    SevereCodes.SevereTooMuchRetries -> {
                        message = "Too much retries during ZRTP negotiation."
                        i18nMessage = aTalkApp.getResString(R.string.impl_media_security_RETRY_RATE_EXCEEDED, msgCode.toString())
                    }
                    SevereCodes.SevereProtocolError -> {
                        message = "Internal protocol error occurred."
                        i18nMessage = aTalkApp.getResString(R.string.impl_media_security_INTERNAL_PROTOCOL_ERROR, msgCode.toString())
                    }
                    else -> {
                        message = "General error has occurred."
                        i18nMessage = aTalkApp.getResString(R.string.impl_media_security_ZRTP_GENERIC_MSG, msgCode.toString())
                    }
                }
            }

            is ZrtpErrorCodes -> {
                severity = SrtpListener.ERROR
                message = "Indicates compatibility problems like for example: unsupported protocol version," +
                        " unsupported hash type, cypher type, SAS scheme, etc."
                i18nMessage = aTalkApp.getResString(R.string.impl_media_security_ZRTP_GENERIC_MSG, msgCode.toString())
            }

            else -> {
                // Other warnings are internal only, no user action required
                sendEvent = false
            }
        }

        if (sendEvent) securityListener!!.securityMessageReceived(message, i18nMessage, severity)
        Timber.d("%s: ZRTP message (%s): code: %s; message: %s; sendEvent: %s",
                sessionTypeToString(sessionType), sev, msgCode, message, sendEvent)
    }

    /**
     * Negotiation has failed.
     *
     * @param severity of the message.
     * @param subCode the message code.
     */
    override fun zrtpNegotiationFailed(severity: MessageSeverity, subCode: EnumSet<*>) {
        val ii: Iterator<*> = subCode.iterator()
        val msgCode = ii.next()!!
        aTalkApp.showToastMessage(R.string.impl_media_security_ZRTP_HANDSHAKE_TIMEOUT, msgCode)
        Timber.w(Exception(), "%s: ZRTP key negotiation failed: %s", sessionTypeToString(sessionType), msgCode)
    }

    /**
     * Inform user interface that security is not active any more.
     */
    override fun secureOff() {
        Timber.d("%s: Security off", sessionTypeToString(sessionType))
        securityListener!!.securityTurnedOff(sessionType)
    }

    /**
     * The other part does not support zrtp.
     */
    override fun zrtpNotSuppOther() {
        Timber.d("%s: Other party does not support ZRTP key negotiation protocol, no secure calls possible.",
                sessionTypeToString(sessionType))
        securityListener!!.securityTimeout(sessionType)
    }

    /**
     * Inform the user that ZRTP received "go clear" message from its peer.
     */
    override fun confirmGoClear() {
        Timber.d("%s: GoClear confirmation requested.", sessionTypeToString(sessionType))
    }

    /**
     * Converts the `sessionType` into a `String`.
     *
     * @param sessionType the `MediaType` to be converted into a `String` for the purposes of this
     * `SecurityEventManager`
     * @return a `String` representation of `sessionType`.
     */
    private fun sessionTypeToString(sessionType: MediaType?): String {
        return when (sessionType) {
            MediaType.AUDIO -> "AUDIO_SESSION"
            MediaType.VIDEO -> "VIDEO_SESSION"
            else -> throw IllegalArgumentException("sessionType")
        }
    }

    /**
     * Sets a new receiver of the security callback events.
     *
     * @param securityListener An object that receives the security events.
     */
    fun setSrtpListener(securityListener: SrtpListener) {
        this.securityListener = securityListener
    }

    /**
     * Gets the SAS for the current media stream.
     *
     * @return the four character ZRTP SAS.
     */
    val securityString: String?
        get() = if (masterEventManager != null && masterEventManager != this) {
            masterEventManager!!.sas
        } else sas

    /**
     * Gets the status of the SAS verification.
     *
     * @return true when the SAS has been verified.
     */
    val isSecurityVerified: Boolean
        get() = if (masterEventManager != null && masterEventManager != this) {
            masterEventManager!!.isSasVerified
        } else isSasVerified

    /**
     * Indicates that we started the process of securing the the connection.
     */
    fun securityNegotiationStarted() {
        // make sure we don't throw any exception
        try {
            securityListener!!.securityNegotiationStarted(sessionType, zrtpControl)
        } catch (t: Throwable) {
            Timber.e("Error processing security started.")
        }
    }

    companion object {
        /**
         * A warning `String` that we display to the user.
         */
        val WARNING_NO_RS_MATCH = aTalkApp.getResString(R.string.impl_media_security_WARNING_NO_RS_MATCH)

        /**
         * A warning `String` that we display to the user.
         */
        val WARNING_NO_EXPECTED_RS_MATCH = aTalkApp.getResString(R.string.impl_media_security_WARNING_NO_EXPECTED_RS_MATCH)

        /**
         * Gets the localized message and replace the param. If the param is null we ignore it.
         *
         * @param key the key for the localized message.
         * @param param the param to replace.
         * @return the i18n message.
         */
        private fun getI18NString(key: String, param: String?): String? {
            val resources = LibJitsi.resourceManagementService
            return if (resources == null) {
                null
            } else {
                val params = param?.let { arrayOf(it) }
                resources.getI18NString(key,  params)
            }
        }
    }
}