/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.TextUtils
import net.java.sip.communicator.impl.certificate.CertificateServiceImpl
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager
import net.java.sip.communicator.service.certificate.CertificateConfigEntry
import net.java.sip.communicator.service.certificate.CertificateService
import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils.isSendChatStateNotifications
import net.java.sip.communicator.util.ConfigurationUtils.isSendMessageDeliveryReceipt
import net.java.sip.communicator.util.NetworkUtils.getInetAddress
import net.java.sip.communicator.util.NetworkUtils.getSRVRecords
import org.apache.commons.lang3.StringUtils
import org.atalk.crypto.omemo.AndroidOmemoService
import org.atalk.hmos.BuildConfig
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.account.settings.BoshProxyDialog
import org.atalk.hmos.gui.call.JingleMessageSessionImpl
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.dialogs.DialogActivity.DialogListener
import org.atalk.hmos.gui.login.LoginSynchronizationPoint
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.util.LocaleHelper
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.util.OSUtils
import org.jivesoftware.smack.*
import org.jivesoftware.smack.ConnectionConfiguration.DnssecMode
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode
import org.jivesoftware.smack.SmackException.*
import org.jivesoftware.smack.XMPPException.StreamErrorException
import org.jivesoftware.smack.XMPPException.XMPPErrorException
import org.jivesoftware.smack.bosh.BOSHConfiguration
import org.jivesoftware.smack.bosh.XMPPBOSHConnection
import org.jivesoftware.smack.packet.*
import org.jivesoftware.smack.provider.ExtensionElementProvider
import org.jivesoftware.smack.provider.ProviderManager
import org.jivesoftware.smack.proxy.ProxyInfo
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.roster.rosterstore.DirectoryRosterStore
import org.jivesoftware.smack.sasl.SASLErrorException
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jivesoftware.smack.util.SslContextFactory
import org.jivesoftware.smack.util.TLSUtils
import org.jivesoftware.smack.util.dns.minidns.MiniDnsDane
import org.jivesoftware.smackx.DefaultExtensionElementProvider
import org.jivesoftware.smackx.avatar.AvatarManager
import org.jivesoftware.smackx.avatar.useravatar.UserAvatarManager
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarData
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata
import org.jivesoftware.smackx.avatar.useravatar.provider.AvatarDataProvider
import org.jivesoftware.smackx.avatar.useravatar.provider.AvatarMetadataProvider
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate
import org.jivesoftware.smackx.avatar.vcardavatar.provider.VCardTempXUpdateProvider
import org.jivesoftware.smackx.bob.element.BoBIQ
import org.jivesoftware.smackx.bytestreams.ibb.InBandBytestreamManager
import org.jivesoftware.smackx.bytestreams.ibb.packet.DataPacketExtension
import org.jivesoftware.smackx.bytestreams.socks5.Socks5BytestreamManager
import org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream
import org.jivesoftware.smackx.caps.EntityCapsManager
import org.jivesoftware.smackx.caps.packet.CapsExtension
import org.jivesoftware.smackx.caps.provider.CapsExtensionProvider
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension
import org.jivesoftware.smackx.captcha.provider.CaptchaProvider
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension
import org.jivesoftware.smackx.coin.CoinIQ
import org.jivesoftware.smackx.coin.CoinIQProvider
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ
import org.jivesoftware.smackx.colibri.ColibriIQProvider
import org.jivesoftware.smackx.confdesc.ConferenceDescriptionExtension
import org.jivesoftware.smackx.confdesc.ConferenceDescriptionExtensionProvider
import org.jivesoftware.smackx.delay.packet.DelayInformation
import org.jivesoftware.smackx.delay.provider.DelayInformationProvider
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager
import org.jivesoftware.smackx.disco.packet.DiscoverInfo
import org.jivesoftware.smackx.disco.packet.DiscoverItems
import org.jivesoftware.smackx.externalservicediscovery.ExternalServiceDiscoveryManager
import org.jivesoftware.smackx.externalservicediscovery.ExternalServiceDiscoveryProvider
import org.jivesoftware.smackx.externalservicediscovery.ExternalServices
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator
import org.jivesoftware.smackx.httpauthorizationrequest.HttpAuthorizationRequestListener
import org.jivesoftware.smackx.httpauthorizationrequest.HttpAuthorizationRequestManager
import org.jivesoftware.smackx.httpauthorizationrequest.element.ConfirmExtension
import org.jivesoftware.smackx.httpauthorizationrequest.provider.ConfirmExtProvider
import org.jivesoftware.smackx.httpauthorizationrequest.provider.ConfirmIQProvider
import org.jivesoftware.smackx.inputevt.InputEvtIQ
import org.jivesoftware.smackx.inputevt.InputEvtIQProvider
import org.jivesoftware.smackx.iqlast.LastActivityManager
import org.jivesoftware.smackx.iqregisterx.packet.Registration
import org.jivesoftware.smackx.iqregisterx.provider.RegistrationProvider
import org.jivesoftware.smackx.iqregisterx.provider.RegistrationStreamFeatureProvider
import org.jivesoftware.smackx.iqversion.VersionManager
import org.jivesoftware.smackx.jibri.JibriIq
import org.jivesoftware.smackx.jibri.JibriIqProvider
import org.jivesoftware.smackx.jingle.element.Jingle
import org.jivesoftware.smackx.jingle.provider.JingleProvider
import org.jivesoftware.smackx.jingle_filetransfer.JingleFileTransferManager
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFileTransferImpl
import org.jivesoftware.smackx.jingle_rtp.element.*
import org.jivesoftware.smackx.jingleinfo.JingleInfoQueryIQ
import org.jivesoftware.smackx.jingleinfo.JingleInfoQueryIQProvider
import org.jivesoftware.smackx.jinglemessage.JingleMessageManager
import org.jivesoftware.smackx.jinglemessage.element.JingleMessage
import org.jivesoftware.smackx.jinglenodes.SmackServiceNode
import org.jivesoftware.smackx.jinglenodes.TrackerEntry
import org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ
import org.jivesoftware.smackx.jitsimeet.*
import org.jivesoftware.smackx.mam.MamManager
import org.jivesoftware.smackx.mam.element.MamPrefsIQ
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension
import org.jivesoftware.smackx.muc.packet.MUCInitialPresence
import org.jivesoftware.smackx.nick.packet.Nick
import org.jivesoftware.smackx.nick.provider.NickProvider
import org.jivesoftware.smackx.ping.PingFailedListener
import org.jivesoftware.smackx.ping.PingManager
import org.jivesoftware.smackx.receipts.DeliveryReceipt
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager.AutoReceiptMode
import org.jivesoftware.smackx.si.packet.StreamInitiation
import org.jivesoftware.smackx.thumbnail.Thumbnail
import org.jivesoftware.smackx.thumbnail.ThumbnailStreamInitiationProvider
import org.jivesoftware.smackx.vcardtemp.packet.VCard
import org.jivesoftware.smackx.xhtmlim.XHTMLManager
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension
import org.jxmpp.jid.DomainBareJid
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.EntityFullJid
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.jid.parts.Resourcepart
import org.jxmpp.stringprep.XmppStringprepException
import org.jxmpp.util.XmppStringUtils
import org.minidns.dnsname.DnsName
import org.minidns.dnssec.DnssecValidationFailedException
import org.xmlpull.v1.XmlPullParserException
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.*
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.SocketFactory
import javax.net.ssl.*

/**
 * An implementation of the protocol provider service over the Jabber protocol
 *
 * @author Damian Minkov
 * @author Symphorien Wanko
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 * @author MilanKral
 */
class ProtocolProviderServiceJabberImpl : AbstractProtocolProviderService(), PingFailedListener, HttpAuthorizationRequestListener {
    /**
     * Used to connect to a XMPP server.
     */
    override var connection: AbstractXMPPConnection? = null

    /**
     * The InetSocketAddress of the XMPP server.
     */
    private var mInetSocketAddress: InetSocketAddress? = null

    /**
     * Indicates whether or not the provider is initialized and ready for use.
     */
    private var isInitialized = false

    /**
     * False to disable resetting the smack reply timer on completion;
     * let AndroidOmemoService do it as the task is async
     */
    private var resetSmackTimer = true

    /**
     * We use this to lock access to initialization.
     */
    private val initializationLock = Any()

    /**
     * The identifier of the account that this provider represents.
     */
    override lateinit var accountID: JabberAccountID

    /**
     * Used when we need to re-register or someone needs to obtain credentials.
     */
    var authority: SecurityAuthority? = null
        private set

    /**
     * The resource we will use when connecting during this run.
     */
    private var mResource: Resourcepart? = null

    private val isDesktopSharingEnable = false

    /**
     * Persistent Storage for Roster Versioning support.
     */
    var rosterStoreDirectory: File? = null
        private set
    private var mRoster: Roster? = null

    /**
     * A set of features supported by our Jabber implementation. In general, we add the new feature(s)
     * when we add new operation sets.
     * (see xep-0030: [Discovering Information About a Jabber Entity](https://www.xmpp.org/extensions/xep-0030.html#info)).
     * Example : to tell the world that we support jingle, we simply have to do :
     * supportedFeatures.add("https://www.xmpp.org/extensions/xep-0166.html#ns"); Beware there is no
     * canonical mapping between op set and jabber features (op set is a SC "concept"). This means
     * that one op set in SC can correspond to many jabber features. It is also possible that there
     * is no jabber feature corresponding to a SC op set or again, we can currently support some
     * features which do not have a specific op set in SC (the mandatory feature :
     * xmlns=="http://jabber.org/protocol/disco#info" is one example). We can find features corresponding to
     * op set in the xep(s) related to implemented functionality.
     */
    private val supportedFeatures = ArrayList<String>()
    /**
     * Returns the currently valid [ScServiceDiscoveryManager].
     *
     * @return the currently valid [ScServiceDiscoveryManager].
     */
    /**
     * The `ServiceDiscoveryManager` is responsible for advertising
     * `supportedFeatures` when asked by a remote client. It can also be used to query
     * remote clients for supported features.
     */
    var discoveryManager: ScServiceDiscoveryManager? = null
        private set
    private var reconnectionManager: ReconnectionManager? = null
    private var androidOmemoService: AndroidOmemoService? = null
    private var httpAuthorizationRequestManager: HttpAuthorizationRequestManager? = null

    /**
     * The `OperationSetContactCapabilities` of this `ProtocolProviderService` which
     * is the service-public counterpart of [.discoveryManager].
     */
    private var opsetContactCapabilities: OperationSetContactCapabilitiesJabberImpl? = null
    /**
     * Returns the current instance of `JabberStatusEnum`.
     *
     * @return the current instance of `JabberStatusEnum`.
     */
    /**
     * The statuses.
     */
    var jabberStatusEnum: JabberStatusEnum? = null
        private set

    /**
     * The service we use to interact with user.
     */
    private var guiVerification: CertificateService? = null

    /**
     * Used with tls connecting when certificates are not trusted and we ask the user to confirm.
     * When some timeout expires connect method returns, and we use abortConnecting to
     * abort further execution cause after user chooses we make further processing from there.
     */
    private var abortConnecting = false

    /**
     * Flag indicating are we currently executing connectAndLogin method.
     */
    private var inConnectAndLogin = false

    /**
     * Flag indicates that the last getJitsiVideobridge returns NoResponseException
     *
     * @see .getJitsiVideobridge
     */
    private var isLastVbNoResponse = false

    /**
     * Instant of OperationSetPersistentPresent
     */
    private var opSetPP: OperationSetPersistentPresence? = null

    /**
     * Object used to synchronize the flag inConnectAndLogin.
     */
    private val connectAndLoginLock = Any()

    /**
     * If an event occurs during login we fire it at the end of the login process (at the end of
     * connectAndLogin method).
     */
    private var eventDuringLogin: RegistrationStateChangeEvent? = null

    /**
     * Listens for XMPP connection state or errors.
     */
    private var xmppConnectionListener: XMPPConnectionListener? = null

    /**
     * The details of the proxy we are using to connect to the server (if any)
     */
    private var proxy: ProxyInfo? = null

    /**
     * State for connect and login state.
     */
    private enum class ConnectState {
        /**
         * Abort any further connecting.
         */
        ABORT_CONNECTING,

        /**
         * Continue trying with next address.
         */
        CONTINUE_TRYING,

        /**
         * Stop trying we succeeded or just have a final state for the whole connecting procedure.
         */
        STOP_TRYING
    }

    /**
     * Synchronization object to monitor jingle nodes auto discovery.
     */
    private val jingleNodesSyncRoot = Any()

    /**
     * Stores user credentials for local use if user hasn't stored its password.
     */
    var userCredentials: UserCredentials? = null
        private set

    /**
     * Flag to indicate the network type connection before the ConnectionClosedOnError occur
     * Note: Switching between WiFi and Mobile network will also causes ConnectionClosedOnError to occur
     */
    private var isLastConnectionMobile = false

    /**
     * Flag to indicate the connected mobile network has ConnectionClosedOnError due to:
     * 1. Disconnection occur while it is in connected with Mobile network
     * 2. Occur within 500mS of the ping action
     * i.e. to discard ConnectionClosedOnError due to other factors e.g. network fading etc
     */
    private var isMobilePingClosedOnError = false

    /**
     * Set to success if protocol provider has successfully connected to the server.
     */
    private var xmppConnected: LoginSynchronizationPoint<XMPPException>? = null

    /**
     * Set to success if account has registered on server via inBand Registration.
     */
    var accountIBRegistered: LoginSynchronizationPoint<XMPPException>? = null

    /**
     * Set to success if account has authenticated with the server.
     */
    private var accountAuthenticated: LoginSynchronizationPoint<XMPPException>? = null

    /**
     * An `OperationSet` that allows access to connection information used by the protocol provider.
     */
    private inner class OperationSetConnectionInfoJabberImpl : OperationSetConnectionInfo {
        /**
         * @return The XMPP server hostAddress.
         */
        override fun getServerAddress(): InetSocketAddress? {
            return mInetSocketAddress
        }
    }

    /**
     * Returns the state of the account login state of this protocol provider
     * Note: RegistrationState is not inBand Registration
     *
     * @return the `RegistrationState` ot the provider is currently in.
     */
    override val registrationState: RegistrationState
        get() {
            when (connection) {
                null -> {
                    return if (inConnectAndLogin) RegistrationState.REGISTERING
                    else RegistrationState.UNREGISTERED
                }
                else -> {
                    when {
                        connection!!.isAuthenticated -> {
                            return RegistrationState.REGISTERED
                        }
                        else -> {
                            if (connection!!.isConnected || (connection is XMPPTCPConnection
                                            && (connection as XMPPTCPConnection).isDisconnectedButSmResumptionPossible)) {
                                return RegistrationState.REGISTERING
                            }
                        }
                    }
                }
            }
            return RegistrationState.UNREGISTERED
        }

    /**
     * Return the certificate verification service impl.
     *
     * @return the CertificateVerification service.
     */
    private val certificateVerificationService: CertificateService?
        get() {
            if (guiVerification == null) {
                val guiVerifyReference = JabberActivator.bundleContext.getServiceReference(
                    CertificateService::class.java.name)
                if (guiVerifyReference != null) {
                    guiVerification = JabberActivator.bundleContext.getService(guiVerifyReference) as CertificateService
                }
            }
            return guiVerification
        }

    /**
     * Starts the registration process. Connection details such as registration server, user
     * name/number are provided through the configuration service through implementation specific properties.
     *
     * @param authority the security authority that will be used for resolving any security challenges that
     * may be returned during the registration or at any moment while we're registered.
     *
     * @throws OperationFailedException with the corresponding code it the registration fails for some reason
     * (e.g. a networking error or an implementation problem).
     */
    @Throws(OperationFailedException::class)
    override fun register(authority: SecurityAuthority?) {
        requireNotNull(authority) {
            ("The register method needs a valid non-null"
                    + " authority impl in order to be able to retrieve passwords.")
        }
        this.authority = authority
        try {
            // reset states
            abortConnecting = false

            // indicate we have started connectAndLogin process
            synchronized(connectAndLoginLock) {
                inConnectAndLogin = true
            }
            val loginReason = "User Authentication Required!"
            initializeConnectAndLogin(authority, SecurityAuthority.AUTHENTICATION_REQUIRED, loginReason, false)
        } catch (ex: XMPPException) {
            Timber.e("Error registering: %s", ex.message)
            eventDuringLogin = null
            fireRegistrationStateChanged(ex)
        } catch (ex: SmackException) {
            Timber.e("Error registering: %s", ex.message)
            eventDuringLogin = null
            fireRegistrationStateChanged(ex)
        } finally {
            synchronized(connectAndLoginLock) {
                // If an error has occurred during login, only fire it here in order to avoid a
                // deadlock which occurs in reconnect plugin. The deadlock is because we fired an
                // event during login process and have locked initializationLock; and we cannot
                // unregister from reconnect, because unregister method also needs this lock.
                if (eventDuringLogin != null) {
                    val newState = eventDuringLogin!!.getNewState()
                    if (newState == RegistrationState.CONNECTION_FAILED
                            || newState == RegistrationState.UNREGISTERED) {
                        disconnectAndCleanConnection()
                    }
                    fireRegistrationStateChanged(eventDuringLogin!!.getOldState(), newState,
                        eventDuringLogin!!.getReasonCode(), eventDuringLogin!!.getReason())
                    eventDuringLogin = null
                }
                inConnectAndLogin = false
            }
        }
    }

    /**
     * Connect and login again to the server.
     *
     * @param authReasonCode indicates the reason of the re-authentication.
     */
    private fun reRegister(authReasonCode: Int, loginReason: String?) {
        try {
            Timber.d("SMACK: Trying to re-register account!")

            // set to indicate the account has not registered during the registration process
            unregisterInternal(false)
            // reset states
            abortConnecting = false

            // indicate we started connectAndLogin process
            synchronized(connectAndLoginLock) { inConnectAndLogin = true }
            initializeConnectAndLogin(authority, authReasonCode, loginReason, true)
        } catch (ex: OperationFailedException) {
            Timber.e("Error reRegistering: %s", ex.message)
            eventDuringLogin = null
            disconnectAndCleanConnection()
            fireRegistrationStateChanged(registrationState, RegistrationState.CONNECTION_FAILED,
                RegistrationStateChangeEvent.REASON_INTERNAL_ERROR, null)
        } catch (ex: XMPPException) {
            Timber.e("Error ReRegistering: %s", ex.message)
            eventDuringLogin = null
            fireRegistrationStateChanged(ex)
        } catch (ex: SmackException) {
            Timber.e("Error ReRegistering: %s", ex.message)
            eventDuringLogin = null
            fireRegistrationStateChanged(ex)
        } finally {
            synchronized(connectAndLoginLock) {

                // If an error has occurred during login, only fire it here in order to avoid a
                // deadlock which occurs in reconnect plugin. The deadlock is because we fired an
                // event during login process and have locked initializationLock; and we cannot
                // unregister from reconnect, because unregister method also needs this lock.
                if (eventDuringLogin != null) {
                    val newState = eventDuringLogin!!.getNewState()
                    if (newState == RegistrationState.CONNECTION_FAILED
                            || newState == RegistrationState.UNREGISTERED) disconnectAndCleanConnection()
                    fireRegistrationStateChanged(eventDuringLogin!!.getOldState(), newState,
                        eventDuringLogin!!.getReasonCode(), eventDuringLogin!!.getReason())
                    eventDuringLogin = null
                }
                inConnectAndLogin = false
            }
        }
    }

    /**
     * Indicates if the XMPP transport channel is using a TLS secured socket.
     *
     * @return True when TLS is used, false otherwise.
     */
    override val isSignalingTransportSecure: Boolean
        get() = connection != null && connection!!.isSecureConnection

    /**
     * Returns the "transport" protocol of this instance used to carry the control channel for the
     * current protocol service.
     *
     * @return The "transport" protocol of this instance: TCP, TLS or UNKNOWN.
     */

    override val transportProtocol: TransportProtocol
        get() {
            // Without a connection, there is no transport available.
            return if (connection != null && connection!!.isConnected) {
                // Transport using a secure connection.
                if (connection!!.isSecureConnection) {
                    TransportProtocol.TLS
                }
                else TransportProtocol.TCP
                // Transport using a unsecured connection.
            }
            else TransportProtocol.UNKNOWN
        }

    /**
     * Connect and login to the server
     *
     * @param authority SecurityAuthority
     * @param reasonCode the authentication reason code. Indicates the reason of this authentication.
     *
     * @throws XMPPException if we cannot connect to the server - network problem
     * @throws OperationFailedException if login parameters as server port are not correct
     */
    @Throws(XMPPException::class, SmackException::class, OperationFailedException::class)
    private fun initializeConnectAndLogin(
            authority: SecurityAuthority?, reasonCode: Int,
            loginReason: String?, isShowAlways: Boolean,
    ) {
        synchronized(initializationLock) {

            // if a thread is waiting for initializationLock and enters, lets check whether one
            // has already tried login and have succeeded. Avoid duplicate connections"
            if (isRegistered) return

            val loginStrategy = createLoginStrategy()
            userCredentials = loginStrategy.prepareLogin(authority!!, reasonCode, loginReason, isShowAlways)
            if (!loginStrategy.loginPreparationSuccessful()
                    || (userCredentials != null && userCredentials!!.isUserCancel)) return

            loadResource()
            loadProxy()

            /*
             * with a google account (either gmail or google apps related), the userID MUST be the
             * @see EntityBareJid i.e. user@serviceName
             */
            val userID = if (accountID.protocolDisplayName == "Google Talk") {
                accountID.mUserID
            }
            else {
                XmppStringUtils.parseLocalpart(accountID.mUserID)
            }
            try {
                connectAndLogin(userID, loginStrategy)
            } catch (ex: XMPPException) {
                // server disconnect us after such an error, do cleanup or connection denied.
                disconnectAndCleanConnection()
                throw ex // rethrow the original exception
            } catch (ex: SmackException) {
                disconnectAndCleanConnection()
                throw ex
            } finally {
                // Reset to Smack default on login process completion
                if (connection != null && resetSmackTimer) connection!!.replyTimeout = SMACK_REPLY_TIMEOUT_DEFAULT
                resetSmackTimer = true
            }
        }
    }

    /**
     * Creates the JabberLoginStrategy to use for the current account.
     */
    private fun createLoginStrategy(): JabberLoginStrategy {
        val ccBuilder = if (accountID.isBOSHEnable()) {
            BOSHConfiguration.builder()
        }
        else {
            XMPPTCPConnectionConfiguration.builder()
        }

        if (accountID.isAnonymousAuthUsed()) {
            return AnonymousLoginStrategy(accountID.authorizationName, ccBuilder)
        }

        val clientCertId = accountID.tlsClientCertificate
        return if (clientCertId != null && clientCertId != CertificateConfigEntry.CERT_NONE.toString()) {
            LoginByClientCertificateStrategy(accountID, ccBuilder)
        }
        else {
            LoginByPasswordStrategy(this, accountID, ccBuilder)
        }
    }

    /**
     * Initializes the Jabber Resource identifier: default or auto generated.
     */
    private fun loadResource() {
        if (mResource != null) return
        var sResource = accountID.getAccountPropertyString(ProtocolProviderFactory.RESOURCE, DEFAULT_RESOURCE)
        val autoGenRes = accountID.getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, true)
        if (autoGenRes) {
            val random = SecureRandom()
            sResource += "-" + BigInteger(32, random).toString(32)
        }
        try {
            mResource = Resourcepart.from(sResource)
        } catch (e: XmppStringprepException) {
            e.printStackTrace()
        }
    }

    /**
     * Sets the proxy information as per account proxy setting
     *
     * @throws OperationFailedException for incorrect proxy parameters being specified
     */
    @Throws(OperationFailedException::class)
    private fun loadProxy() {
        if (accountID.isUseProxy) {
            var proxyType = accountID.getAccountPropertyString(
                ProtocolProviderFactory.PROXY_TYPE, accountID.proxyType)
            if (accountID.isBOSHEnable()) {
                if (!accountID.isBoshHttpProxyEnabled()) {
                    proxy = null
                    return
                }
                else proxyType = BoshProxyDialog.HTTP
            }
            val proxyAddress = accountID.getAccountPropertyString(
                ProtocolProviderFactory.PROXY_ADDRESS, accountID.proxyAddress)
            val proxyPortStr = accountID.getAccountPropertyString(
                ProtocolProviderFactory.PROXY_PORT, accountID.proxyPort)
            val proxyPort = try {
                proxyPortStr!!.toInt()
            } catch (ex: NumberFormatException) {
                throw OperationFailedException("Wrong proxy port, " + proxyPortStr
                        + " does not represent an integer", OperationFailedException.INVALID_ACCOUNT_PROPERTIES, ex)
            }
            val proxyUsername = accountID.getAccountPropertyString(
                ProtocolProviderFactory.PROXY_USERNAME, accountID.proxyUserName)
            val proxyPassword = accountID.getAccountPropertyString(
                ProtocolProviderFactory.PROXY_PASSWORD, accountID.proxyPassword)
            if (proxyAddress == null || proxyAddress.isEmpty()) {
                throw OperationFailedException("Missing Proxy Address",
                    OperationFailedException.INVALID_ACCOUNT_PROPERTIES)
            }
            proxy = try {
                ProxyInfo(ProxyInfo.ProxyType.valueOf(proxyType!!),
                    proxyAddress, proxyPort, proxyUsername, proxyPassword)
            } catch (e: IllegalStateException) {
                Timber.e(e, "Invalid Proxy Type not support by smack")
                null
            }
        }
        else {
            proxy = null
        }
    }

    /**
     * Connects xmpp connection and login. Returning the state whether is it final - Abort due to
     * certificate cancel or keep trying cause only current address has failed or stop trying cause we succeeded.
     *
     * @param userName the username to use
     * @param loginStrategy the login strategy to use
     *
     * @return return the state how to continue the connect process.
     * @throws XMPPException & SmackException if we cannot connect for some reason
     */
    @Throws(XMPPException::class, SmackException::class)
    private fun connectAndLogin(userName: String?, loginStrategy: JabberLoginStrategy): ConnectState {
        val config = loginStrategy.connectionConfigurationBuilder

        // Set XmppDomain to serviceName - default for no server-overridden and Bosh connection.
        val serviceName = accountID.xmppDomain
        config.setXmppDomain(serviceName)
        config.setResource(mResource)
        config.setProxyInfo(proxy)
        config.setCompressionEnabled(false)
        config.setLanguage(LocaleHelper.xmlLocale)

        /*=== Configure connection for BOSH or TCP ===*/
        val isBosh = accountID.isBOSHEnable()
        if (isBosh) {
            val boshURL = accountID.getBoshUrl()
            val boshConfigurationBuilder = config as BOSHConfiguration.Builder?
            try {
                val boshURI = URI(boshURL)
                val useHttps = boshURI.scheme == "https"
                var port = boshURI.port
                if (port == -1) {
                    port = if (useHttps) 443 else 80
                }
                var file = boshURI.path
                // use rawQuery as getQuery() decodes the string
                val query = boshURI.rawQuery
                if (!TextUtils.isEmpty(query)) {
                    file += "?$query"
                }
                boshConfigurationBuilder!!
                        .setUseHttps(useHttps)
                        .setFile(file)
                        .setHost(boshURI.host)
                        .setPort(port)
            } catch (e: URISyntaxException) {
                Timber.e("Fail setting bosh URL in XMPPBOSHConnection configuration: %s", e.message)
                val stanzaError = StanzaError.getBuilder(StanzaError.Condition.unexpected_request).build()
                throw XMPPErrorException(null, stanzaError)
            }
        }
        else {
            /*
             * The defined settings for setHostAddress and setHost are handled by XMPPTCPConnection
             * #populateHostAddresses()-obsoleted, mechanism for the various service mode.
             */
            val isServerOverridden = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, false)

            // cmeng - value not defined currently for CUSTOM_XMPP_DOMAIN login
            val customXMPPDomain = accountID.getAccountPropertyString(ProtocolProviderFactory.CUSTOM_XMPP_DOMAIN)
            if (customXMPPDomain != null) {
                mInetSocketAddress = InetSocketAddress(customXMPPDomain, DEFAULT_PORT)
                Timber.i("Connect using custom XMPP domain: %s", mInetSocketAddress)
                config.setHostAddress(mInetSocketAddress!!.address)
                config.setPort(DEFAULT_PORT)
            }
            else if (isServerOverridden) {
                val host = accountID.serverAddress
                val port = accountID.getAccountPropertyInt(ProtocolProviderFactory.SERVER_PORT, DEFAULT_PORT)
                mInetSocketAddress = InetSocketAddress(host, port)
                Timber.i("Connect using server override: %s", mInetSocketAddress)

                // For host given as ip address, then no DNSSEC authentication support
                if (host!![0].digitToIntOrNull(16) != -1) {
                    config.setHostAddress(mInetSocketAddress!!.address)
                }
                // setHostAddress will take priority over setHost in smack populateHostAddresses() implementation - obsoleted
                config.setHost(host)
                config.setPort(port)
            }
            else {
                mInetSocketAddress = InetSocketAddress(accountID.service, DEFAULT_PORT)
                Timber.i("Connect using service SRV Resource Record: %s", mInetSocketAddress)
                config.setHost(null as DnsName?)
                config.setHostAddress(null)
            }
        }

        // if we have OperationSetPersistentPresence to take care of <presence/> sending, then
        // disable smack from sending the initial presence upon user authentication
        opSetPP = getOperationSet(OperationSetPersistentPresence::class.java)
        if (opSetPP != null) config.setSendPresence(false)
        if (connection != null && connection!!.isConnected) {
            Timber.w("Attempt on connection that is not null and isConnected %s", accountID.accountJid)
            disconnectAndCleanConnection()
        }
        config.setSocketFactory(SocketFactory.getDefault())
        var supportedProtocols = arrayOf("TLSv1", "TLSv1.1", "TLSv1.2")
        try {
            supportedProtocols = (SSLSocketFactory.getDefault().createSocket() as SSLSocket).supportedProtocols
        } catch (e: IOException) {
            Timber.d("Use default supported Protocols: %s", supportedProtocols.contentToString())
        }
        Arrays.sort(supportedProtocols)

        // Determine enabled TLS protocol versions using getMinimumTLSversion
        val enabledTLSProtocols = ArrayList<String>()
        for (prot in supportedProtocols.indices.reversed()) {
            val pVersion = supportedProtocols[prot]
            enabledTLSProtocols.add(pVersion)
            if (pVersion == accountID.getMinimumTLSversion()) {
                break
            }
        }
        val enabledTLSProtocolsArray = arrayOfNulls<String>(enabledTLSProtocols.size)
        enabledTLSProtocols.toArray(enabledTLSProtocolsArray)
        config.setEnabledSSLProtocols(enabledTLSProtocolsArray)

        // Cannot use a custom SSL context with DNSSEC enabled
        val dnssecMode = accountID.dnssMode
        if (DNSSEC_DISABLE == dnssecMode) {
            config.setDnssecMode(DnssecMode.disabled)

            /*
             * BOSH connection does not support TLS;
             * XEP-206 Note: The client SHOULD ignore any Transport Layer Security (TLS) feature since
             * BOSH channel encryption SHOULD be negotiated at the HTTP layer.
             */
            val tlsRequired: Boolean
            if (isBosh) {
                tlsRequired = false
                config.setSecurityMode(SecurityMode.disabled)
            }
            else {
                /*
                 * user have the possibility to disable TLS but in this case, it will not be able to
                 * connect to a server which requires TLS;
                 */
                tlsRequired = loginStrategy.isTlsRequired
                config.setSecurityMode(if (tlsRequired) SecurityMode.required else SecurityMode.ifpossible)
            }
            val cvs = certificateVerificationService
            if (cvs != null) {
                try {
                    val sslTrustManager = getTrustManager(cvs, serviceName)
                    val sslContext = loginStrategy.createSslContext(cvs, sslTrustManager)
                    val sslContextFactory = SslContextFactory { sslContext }
                    config.setSslContextFactory(sslContextFactory)
                    config.setCustomX509TrustManager(sslTrustManager)
                    config.setAuthzid(accountID.bareJid!!.asEntityBareJidIfPossible())
                } catch (e: GeneralSecurityException) {
                    Timber.e(e, "Error creating custom trust manager")
                    throw ATalkXmppException("Security-Exception: Creating custom TrustManager", e)
                }
            }
            else if (tlsRequired) {
                // StanzaError stanzaError = StanzaError.getBuilder(Condition.service_unavailable).build();
                // throw new XMPPErrorException(null, stanzaError);
                throw ATalkXmppException(
                    "Security-Exception: Certificate verification service is unavailable and TLS is required")
            }
        }
        else {
            if (DNSSEC_ONLY == dnssecMode) {
                config.setDnssecMode(DnssecMode.needsDnssec)
            }
            else if (DNSSEC_AND_DANE == dnssecMode) {
                // override user SecurityMode setting for DNSSEC & DANE option
                config.setDnssecMode(DnssecMode.needsDnssecAndDane)
                config.setSecurityMode(SecurityMode.required)
            }
        }

        // String userJid = userName + "@" + serviceName;
        // String password = userCredentials.getPasswordAsString();
        // config.setUsernameAndPassword(userJid, password);
        connection = try {
            if (isBosh) {
                XMPPBOSHConnection(config.build() as BOSHConfiguration)
            }
            else {
                XMPPTCPConnection(config.build() as XMPPTCPConnectionConfiguration)
            }
        } catch (ex: IllegalStateException) {
            // Cannot use a custom SSL context with DNSSEC enabled
            val errMsg = "${ex.message}\nPlease change DNSSEC security option accordingly."
            val stanzaError = StanzaError.from(StanzaError.Condition.not_allowed, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
        }

        /* Start monitoring the status before connection-login. Only register listener once */
        if (xmppConnectionListener == null) {
            xmppConnectionListener = XMPPConnectionListener()
            connection!!.addConnectionListener(xmppConnectionListener)
        }

        // Allow longer timeout during login for slow client device; clear to default in caller
        connection!!.replyTimeout = SMACK_REPLY_EXTENDED_TIMEOUT_30

        // Init the connection SynchronizedPoints
        xmppConnected = LoginSynchronizationPoint(this, "connection connected")
        Timber.i("Starting XMPP Connection...: %s", mInetSocketAddress)
        try {
            connection!!.connect()
        } catch (ex: StreamErrorException) {
            var errMsg = ex.message
            if (StringUtils.isEmpty(errMsg)) errMsg = ex.streamError.descriptiveText
            Timber.e("Encounter problem during XMPPConnection: %s", errMsg)
            val stanzaError = StanzaError.from(StanzaError.Condition.policy_violation, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
            // } catch (DnssecValidationFailedException | IllegalArgumentException ex) {
        } catch (ex: DnssecValidationFailedException) {
            val errMsg = ex.message
            val stanzaError = StanzaError.from(StanzaError.Condition.not_authorized, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
        } catch (ex: SecurityRequiredByServerException) {
            // "SSL/TLS required by server but disabled in client"
            val errMsg = ex.message
            val stanzaError = StanzaError.from(StanzaError.Condition.not_allowed, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
        } catch (ex: SecurityRequiredByClientException) {
            // "SSL/TLS required by client but not supported by server"
            val errMsg = ex.message
            val stanzaError = StanzaError.from(StanzaError.Condition.service_unavailable, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
        } catch (ex: Exception) {
            // XMPPException | SmackException | IOException | InterruptedException | NullPointerException
            // if (ex.cause is SSLHandshakeException) {
            //    Timber.e(ex.cause);
            // }
            val errMsg = aTalkApp.getResString(R.string.service_gui_XMPP_EXCEPTION, ex.message)
            Timber.e("%s", errMsg)
            val stanzaError = StanzaError.from(StanzaError.Condition.remote_server_timeout, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
        }

        try {
            /*
             * Wait for connectionListener to report connection status. Exception handled in the above try/catch
             */
            xmppConnected!!.checkIfSuccessOrWaitOrThrow()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // Check if user has canceled the Trusted Certificate confirmation request
        if (abortConnecting) {
            abortConnecting = false
            disconnectAndCleanConnection()
            return ConnectState.ABORT_CONNECTING
        }

        if (!connection!!.isConnected) {
            Timber.e("XMPPConnection establishment has failed!")

            // mConnection is not connected, lets set the mConnection state as failed;
            disconnectAndCleanConnection()
            eventDuringLogin = null
            fireRegistrationStateChanged(registrationState,
                RegistrationState.CONNECTION_FAILED, RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND, null)
            return ConnectState.ABORT_CONNECTING
        }

        // cmeng - leave the registering state broadcast when xmpp is connected - may be better to do it here
        fireRegistrationStateChanged(RegistrationState.UNREGISTERED, RegistrationState.REGISTERING,
            RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null)

        // Init the user authentication SynchronizedPoints
        accountAuthenticated = LoginSynchronizationPoint(this, "account authenticated")
        var success = false
        try {
            success = loginStrategy.login(connection!!, userName!!, mResource!!)
        } catch (ex: StreamErrorException) {
            val errMsg = ex.streamError.descriptiveText
            Timber.e("Encounter problem during XMPPConnection: %s", errMsg)
            val stanzaError = StanzaError.from(StanzaError.Condition.policy_violation, errMsg).build()
            throw XMPPErrorException(null, stanzaError)
        } catch (el: Exception) {
            var errMsg = el.message
            /*
             * If account is not registered on server, send IB registration request to server if user
             * enable the option. Otherwise throw back to user and ask for InBand registration confirmation.
             */
            if (StringUtils.isNotEmpty(errMsg) && errMsg!!.contains("not-authorized")) {
                if (accountID.isIbRegistration) {
                    try {
                        // Server sends stream disconnect on "not-authorized". So perform manual connect again before
                        // server closes the stream. Some Server does otherwise, so check before making connection.
                        if (!connection!!.isConnected) {
                            connection!!.connect()
                        }
                        // stop pps connectionListener from disturbing IBR registration process
                        connection!!.removeConnectionListener(xmppConnectionListener)
                        xmppConnectionListener = null

                        accountIBRegistered = LoginSynchronizationPoint(this, "account ib registered")
                        loginStrategy.registerAccount(this, accountID)
                        eventDuringLogin = null
                        return ConnectState.STOP_TRYING
                    } catch (ex: StreamErrorException) {
                        errMsg = ex.streamError.descriptiveText
                        Timber.e("Encounter problem during XMPPConnection: %s", errMsg)
                        val stanzaError = StanzaError.from(StanzaError.Condition.policy_violation, errMsg).build()
                        throw XMPPErrorException(null, stanzaError)
                    } catch (err: Exception) {
                        // SmackException | XMPPException | InterruptedException | IOException | NullPointerExceptio
                        disconnectAndCleanConnection()
                        eventDuringLogin = null
                        fireRegistrationStateChanged(registrationState, RegistrationState.CONNECTION_FAILED,
                            RegistrationStateChangeEvent.REASON_IB_REGISTRATION_FAILED,
                            loginStrategy.javaClass.name + " requests abort")

                        errMsg = err.message
                        if (StringUtils.isNotEmpty(errMsg) && !errMsg!!.contains("registration-required")) {
                            errMsg = aTalkApp.getResString(R.string.service_gui_REGISTRATION_REQUIRED, errMsg)
                            Timber.e("%s", errMsg)
                            val stanzaError = StanzaError.from(StanzaError.Condition.forbidden, errMsg).build()
                            throw XMPPErrorException(null, stanzaError)
                        }
                        else {
                            Timber.e("%s", errMsg)
                            val stanzaError = StanzaError.from(StanzaError.Condition.registration_required,
                                errMsg).build()
                            throw XMPPErrorException(null, stanzaError)
                        }
                    }
                }
                else {
                    if (el is SASLErrorException) {
                        errMsg += ": " + el.saslFailure.descriptiveText
                    }
                    errMsg = aTalkApp.getResString(R.string.service_gui_NOT_AUTHORIZED_HINT, errMsg)
                    val stanzaError = StanzaError.from(StanzaError.Condition.not_authorized, errMsg).build()
                    throw XMPPErrorException(null, stanzaError)
                }
            }
        }

        // cmeng - sometimes exception and crash after this point during apk debug launch. android JIT problem
        try {
            // wait for connectionListener to report status. Exceptions are handled in try/catch
            accountAuthenticated!!.checkIfSuccessOrWait()
        } catch (e: InterruptedException) {
            Timber.w("Xmpp Connection authentication exception: %s", e.message)
        }
        if (!success) {
            disconnectAndCleanConnection()
            eventDuringLogin = null
            fireRegistrationStateChanged(registrationState,
                RegistrationState.CONNECTION_FAILED, RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED,
                loginStrategy.javaClass.name + " requests abort")
            return ConnectState.ABORT_CONNECTING
        }
        return if (connection!!.isAuthenticated) {
            ConnectState.STOP_TRYING
        }
        else {
            disconnectAndCleanConnection()
            eventDuringLogin = null
            fireRegistrationStateChanged(registrationState, RegistrationState.UNREGISTERED,
                RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null)
            ConnectState.CONTINUE_TRYING
        }
    }

    /**
     * Listener for jabber connection events
     */
    private inner class XMPPConnectionListener : ConnectionListener {
        /**
         * Notification that the connection was closed normally.
         */
        override fun connectionClosed() {
            val errMsg = "Stream closed!"
            val stanzaError = StanzaError.from(StanzaError.Condition.remote_server_timeout, errMsg).build()
            val xmppException = XMPPErrorException(null, stanzaError)
            xmppConnected!!.reportFailure(xmppException)
            if (reconnectionManager != null) reconnectionManager!!.disableAutomaticReconnection()

            // if we are in the middle of connecting process do not fire events, will do it later
            // when the method connectAndLogin finishes its work
            synchronized(connectAndLoginLock) {
                if (inConnectAndLogin) {
                    eventDuringLogin = RegistrationStateChangeEvent(
                        this@ProtocolProviderServiceJabberImpl, registrationState,
                        RegistrationState.CONNECTION_FAILED,
                        RegistrationStateChangeEvent.REASON_USER_REQUEST, errMsg)
                    return
                }
            }
            // Fire that connection has closed. User is responsible to log in again as the stream
            // closed can be authentication, ssl security etc that an auto retrial is of little use
            fireRegistrationStateChanged(registrationState,
                RegistrationState.CONNECTION_FAILED,
                RegistrationStateChangeEvent.REASON_USER_REQUEST, errMsg)
        }

        /**
         * Notification that the connection was closed due to an exception. When abruptly
         * disconnected, the ReconnectionManager will try to reconnecting to the server.
         * Note: Must reported as RegistrationState.RECONNECTING to allow resume as all
         * initial setup must be kept.
         *
         * @param exception contains information on the error.
         */
        override fun connectionClosedOnError(exception: Exception) {
            var errMsg = exception.message
            var regEvent = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED
            var seCondition = StanzaError.Condition.remote_server_not_found
            Timber.e("### Connection closed on error (StreamErrorException: %s) during XMPPConnection: %s",
                exception is StreamErrorException, errMsg)
            if (exception is SSLException) {
                regEvent = RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND
            }
            else if (exception is StreamErrorException) {
                val err = exception.streamError
                val condition = err.condition
                errMsg = err.descriptiveText
                if (condition == StreamError.Condition.conflict) {
                    seCondition = StanzaError.Condition.conflict
                    regEvent = RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN
                    if (errMsg.contains("removed")) {
                        regEvent = RegistrationStateChangeEvent.REASON_NON_EXISTING_USER_ID
                    }
                }
                else if (condition == StreamError.Condition.policy_violation) {
                    seCondition = StanzaError.Condition.policy_violation
                    regEvent = RegistrationStateChangeEvent.REASON_POLICY_VIOLATION
                }
            }
            else if (exception is XmlPullParserException) {
                seCondition = StanzaError.Condition.unexpected_request
                regEvent = RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED
            }

            // Timber.e(exception, "Smack connection Closed OnError: %s", errMsg);
            val stanzaError = StanzaError.from(seCondition, errMsg).build()
            val xmppException = XMPPErrorException(null, stanzaError)
            xmppConnected!!.reportFailure(xmppException)

            // if we are in the middle of connecting process do not fire events, will do it
            // later when the method connectAndLogin finishes its work
            synchronized(connectAndLoginLock) {
                if (inConnectAndLogin) {
                    eventDuringLogin = RegistrationStateChangeEvent(
                        this@ProtocolProviderServiceJabberImpl, registrationState,
                        RegistrationState.CONNECTION_FAILED, regEvent, errMsg!!)
                    return
                }
            }

            // if ((seCondition == Condition.conflict) || (seCondition == Condition.policy_violation)) {
            if (seCondition == StanzaError.Condition.conflict) {
                // launch re-login prompt with reason for disconnect "replace with new connection"
                fireRegistrationStateChanged(exception)
            }
            else  // Reconnecting state - keep all contacts' status
                fireRegistrationStateChanged(registrationState, RegistrationState.RECONNECTING, regEvent, errMsg!!)
        }

        /**
         * Notification that the connection has been successfully connected to the remote endpoint (e.g. the XMPP server).
         *
         * Note that the connection is likely not yet authenticated and therefore only limited operations
         * like registering an account may be possible.
         *
         * @param connection the XMPPConnection which successfully connected to its endpoint.
         */
        override fun connected(connection: XMPPConnection) {
            /*
             * re-init mConnection in case this is a new re-connection; FFR:
             * java.lang.IllegalArgumentException:
             * at org.jivesoftware.smack.util.Objects.requireNonNull (Objects.java:42)
             *  at org.jivesoftware.smack.Manager.<init> (Manager.java:33)
             *  at org.jivesoftware.smackx.iqversion.VersionManager.<init> (VersionManager.java:84)
             *  at org.jivesoftware.smackx.iqversion.VersionManager.getInstanceFor (VersionManager.java:106)
             *  at net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl.initServicesAndFeatures (ProtocolProviderServiceJabberImpl.java:1734)
             */
            this@ProtocolProviderServiceJabberImpl.connection = connection as AbstractXMPPConnection
            xmppConnected!!.reportSuccess()
            if (connection is XMPPTCPConnection) setTrafficClass()

            // must initialize caps entities upon success connection to ensure it is ready for the very first <iq/> send
            initServicesAndFeatures()

            /*  Start up External Service Discovery Manager XEP-0215 */
            ExternalServiceDiscoveryManager.getInstanceFor(connection)

            // check and set auto tune ping interval if necessary
            tunePingInterval()

            /*
             * Broadcast to all others after connection is connected but before actual account registration start.
             * This is required by others to init their states and get ready when the user is authenticated
             */
            // fireRegistrationStateChanged(RegistrationState.UNREGISTERED, RegistrationState.REGISTERING,
            //        RegistrationStateChangeEvent.REASON_USER_REQUEST, "TCP Connection Successful");
        }

        /**
         * Notification that the connection has been authenticated.
         *
         * @param connection the XMPPConnection which successfully authenticated.
         * @param resumed true if a previous XMPP session's stream was resumed.
         */
        override fun authenticated(connection: XMPPConnection, resumed: Boolean) {
            accountAuthenticated!!.reportSuccess()

            // Get the Roster instance for this authenticated user
            mRoster = Roster.getInstanceFor(this@ProtocolProviderServiceJabberImpl.connection)

            /*
             * XEP-0237:Roster Versioning - init RosterStore for each authenticated account to
             * support persistent storage
             */
            initRosterStore()

            /* Always set roster subscription mode to manual so Roster passes the control back to aTalk for processing */
            mRoster!!.subscriptionMode = Roster.SubscriptionMode.manual

            /* Set Roster subscription mode per global defined option for this accounts - Roaster will handle accept-all*/
            //    if (ConfigurationUtils.isPresenceSubscribeAuto())
            //        mRoster.setSubscriptionMode(Roster.SubscriptionMode.accept_all);
            //    else
            //        mRoster.setSubscriptionMode(Roster.SubscriptionMode.manual);
            isResumed = resumed
            val msg = "Smack: User Authenticated with isResumed state: $resumed"
            if (this@ProtocolProviderServiceJabberImpl.connection is XMPPTCPConnection) (this@ProtocolProviderServiceJabberImpl.connection as XMPPTCPConnection).setUseStreamManagementResumption(
                true)

            /*
             * Enable ReconnectionManager with ReconnectionPolicy.RANDOM_INCREASING_DELAY
             * - attempt to reconnect when server disconnect unexpectedly
             * Only enable on authentication. Otherwise <not-authorized/> will also trigger reconnection.
             */
            reconnectionManager = ReconnectionManager.getInstanceFor(this@ProtocolProviderServiceJabberImpl.connection)
            reconnectionManager!!.enableAutomaticReconnection()
            if (accountID.isIbRegistration) accountID.isIbRegistration = false

            // Init HTTPAuthorizationRequestManager on authenticated
            httpAuthorizationRequestManager = HttpAuthorizationRequestManager.getInstanceFor(
                this@ProtocolProviderServiceJabberImpl.connection)
            httpAuthorizationRequestManager!!.addIncomingListener(this@ProtocolProviderServiceJabberImpl)

            /*
             * Must initialize omemoManager on every new connected connection, to ensure both pps and omemoManager is referred
             * to same instance of xmppConnection.  Perform only after connection is connected to ensure the user is defined
             */
            // androidOmemoService = new AndroidOmemoService(ProtocolProviderServiceJabberImpl.this);

            /*
             * Must only initialize omemoDevice after user authenticated
             * Leave the smack reply timer reset to androidOmemoService as it is running async
             */
            resetSmackTimer = false
            androidOmemoService!!.initOmemoDevice()

            /*  Start up Jingle File Transfer */
            JingleFileTransferManager.getInstanceFor(this@ProtocolProviderServiceJabberImpl.connection)

            // Start up both instances for incoming JingleMessage events handlers
            JingleMessageManager.getInstanceFor(connection)
            JingleMessageSessionImpl.getInstanceFor(connection)

            // Fire registration state has changed
            if (resumed) {
                fireRegistrationStateChanged(RegistrationState.REGISTERING, RegistrationState.REGISTERED,
                    RegistrationStateChangeEvent.REASON_RESUMED, msg, false)
            }
            else {
                eventDuringLogin = null
                fireRegistrationStateChanged(RegistrationState.REGISTERING, RegistrationState.REGISTERED,
                    RegistrationStateChangeEvent.REASON_USER_REQUEST, msg, true)

                // Seems like must only execute after <Presence/> has been send and roster is handled.
                // Otherwise own <presence/> status is not received, and status icon stays grey.
                // <a href="https://xmpp.org/extensions/xep-0441.html">XEP-0441: Message Archive Management Preferences 0.2.0 (2020-08-25)</a>
                val mMHS = MessageHistoryActivator.messageHistoryService
                enableMam(connection, mMHS.isHistoryLoggingEnabled)
            }
        }
    }

    /**
     * Called when the server ping fails.
     */
    override fun pingFailed() {
        // Timber.w("Ping failed! isLastConnectionMobile: %s; isConnectedMobile: %s", isLastConnectionMobile,
        //        isConnectedMobile());
        isMobilePingClosedOnError = isLastConnectionMobile && isConnectedMobile
    }

    /*
     * Perform auto tuning of the ping interval if ping and optimization are both enabled and
     * connectionClosedOnError is due to ping activity
     */
    private fun tunePingInterval() {
        // Remember the new connection network type, use in next network ClosedOnError for reference
        isLastConnectionMobile = isConnectedMobile

        // Perform ping auto-tune only if a mobile network connection error, ping and auto tune options are all enabled
        if (!isMobilePingClosedOnError || !accountID.isKeepAliveEnable || !accountID.isPingAutoTuneEnable)
            return

        isMobilePingClosedOnError = false
        var pingInterval = accountID.pingInterval!!.toInt()
        /* adjust ping Interval in step according to its current value */
        if (pingInterval > 240) pingInterval -= 30 else if (pingInterval > 120) pingInterval -= 10

        // kept lowest limit at 120S ping interval
        if (pingInterval >= 120) {
            accountID.storeAccountProperty(ProtocolProviderFactory.PING_INTERVAL, pingInterval)
            Timber.w("Auto tuning ping interval to: %s", pingInterval)
        }
        else {
            Timber.e("Connection closed on error with ping interval of 120 second!!!")
        }
    }

    // Check if there is any connectivity to a mobile network
    private val isConnectedMobile: Boolean
        get() {
            val context = aTalkApp.globalContext
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            if (network != null) {
                val nc = cm.getNetworkCapabilities(network)
                return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
            return false
        }

    /**
     * Gets the TrustManager that should be used for the specified service
     *
     * @param serviceName the service name
     * @param cvs The CertificateVerificationService to retrieve the trust manager
     *
     * @return the trust manager
     */
    @Throws(GeneralSecurityException::class)
    private fun getTrustManager(cvs: CertificateService, serviceName: DomainBareJid): X509TrustManager {
        return HostTrustManager(
            cvs.getTrustManager(listOf(serviceName.toString(),
                CertificateServiceImpl.CERT_XMPP_CLIENT_SUBFIX + serviceName)))
    }

    /**
     * Use to disconnect current connection if exists and do the necessary clean up.
     */
    private fun disconnectAndCleanConnection() {
        if (connection == null) return

        /*
         * Must stop any reconnection timer if any; disconnect does not kill the timer. It continuous
         * to count down and starts another reconnection which disrupts any new established connection.
         */
        if (reconnectionManager != null) reconnectionManager!!.abortPossiblyRunningReconnection()

        // Remove the listener that is added at connection setup
        if (xmppConnectionListener != null) {
            connection!!.removeConnectionListener(xmppConnectionListener)
            xmppConnectionListener = null
        }
        mRoster = null
        // account must have authenticated to close stream;
        // else stream:error <not-authorized xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>
        if (connection!!.isAuthenticated) {
            try {
                val presenceBuilder = connection!!.stanzaFactory.buildPresenceStanza().ofType(Presence.Type.unavailable)
                if (opSetPP != null && StringUtils.isNotEmpty(opSetPP!!.getCurrentStatusMessage())) {
                    presenceBuilder.status = opSetPP!!.getCurrentStatusMessage()
                }
                connection!!.disconnect(presenceBuilder.build())
            } catch (ex: Exception) {
                Timber.w("Exception while disconnect and clean connection!!!")
            }
        }
        // set it null as it also holds a reference to the old connection; it will be created again on new connection setup
        connection = null
        if (httpAuthorizationRequestManager != null) {
            httpAuthorizationRequestManager!!.removeIncomingListener(this)
        }
        try {
            /*
             * The discoveryManager is exposed as service-public by the OperationSetContactCapabilities of this
             * ProtocolProviderService. No longer expose it because it's going away.
             */
            if (opsetContactCapabilities != null) opsetContactCapabilities!!.setDiscoveryManager(null)
        } finally {
            if (discoveryManager != null) {
                discoveryManager!!.stop()
                discoveryManager = null
            }
        }
    }

    /**
     * Ends the registration of this protocol provider with the service.
     */
    override fun unregister() {
        unregisterInternal(true)
    }

    /**
     * Ends the registration of this protocol provider with the service.
     *
     * @param userRequest is the unregister by user request.
     */
    override fun unregister(userRequest: Boolean) {
        unregisterInternal(true, userRequest)
    }

    /**
     * Unregister and fire the event if requested
     *
     * @param fireEvent boolean
     */
    @JvmOverloads
    fun unregisterInternal(fireEvent: Boolean, userRequest: Boolean = false) {
        synchronized(initializationLock) {
            if (fireEvent) {
                eventDuringLogin = null
                fireRegistrationStateChanged(registrationState, RegistrationState.UNREGISTERING,
                    RegistrationStateChangeEvent.REASON_NOT_SPECIFIED, null, userRequest)
            }
            disconnectAndCleanConnection()
            if (fireEvent) {
                eventDuringLogin = null
                fireRegistrationStateChanged(RegistrationState.UNREGISTERING, RegistrationState.UNREGISTERED,
                    RegistrationStateChangeEvent.REASON_USER_REQUEST, null, userRequest)
            }
        }
    }

    /**
     * Returns the short name of the protocol that the implementation of this provider is based
     * upon (like SIP, Jabber, ICQ/AIM, or others for example).
     *
     * @return a String containing the short name of the protocol this service is taking care of.
     */
    override val protocolName = ProtocolNames.JABBER

    /**
     * The icon corresponding to the jabber protocol.
     */
    override lateinit var protocolIcon: ProtocolIconJabberImpl
// override var protocolIcon: ProtocolIcon


    /**
     * Setup the rosterStoreDirectory store for each mAccountID during login process i.e.
     * tag the rosterStore with userID, to support server rosterVersioning if available
     * Note: roster.isRosterVersioningSupported() is not used as its actual status is only know
     * after account is authenticated (too late?).
     */
    fun initRosterStore() {
        val userID = accountID.mUserID
        rosterStoreDirectory = File(aTalkApp.globalContext.filesDir, "/rosterStore_$userID")
        if (!rosterStoreDirectory!!.exists()) {
            if (!rosterStoreDirectory!!.mkdir()) Timber.e("Roster Store directory creation error: %s",
                rosterStoreDirectory!!.absolutePath)
        }
        if (rosterStoreDirectory!!.exists()) {
            var rosterStore = DirectoryRosterStore.open(rosterStoreDirectory)
            if (rosterStore == null) {
                rosterStore = DirectoryRosterStore.init(rosterStoreDirectory)
            }
            mRoster!!.setRosterStore(rosterStore)
        }
    }

    /**
     * Setup all the Smack Service Discovery and other features that can only be performed during
     * actual account registration stage (mConnection). For initial setup see:
     * [.initSmackDefaultSettings] and [.initialize]
     *
     * Note: For convenience, many of the OperationSets when supported will handle state and events changes on its own.
     */
    private fun initServicesAndFeatures() {
        /*  XEP-0092: Software Version initialization */
        VersionManager.getInstanceFor(connection)

        /* XEP-0199: XMPP Ping: Each account may set his own ping interval */
        val mPingManager = PingManager.getInstanceFor(connection)
        if (accountID.isKeepAliveEnable) {
            val pingInterval = accountID.pingInterval!!.toInt()
            mPingManager.pingInterval = pingInterval
            mPingManager.registerPingFailedListener(this)
        }
        else {
            // Disable pingManager
            mPingManager.pingInterval = 0
        }

        /* XEP-0184: Message Delivery Receipts - global option */
        val deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection)
        // Always enable the ReceiptReceivedListener and receipt request (independent of contact capability)
        val mhs = MessageHistoryActivator.messageHistoryService
        deliveryReceiptManager.addReceiptReceivedListener(mhs)
        deliveryReceiptManager.autoAddDeliveryReceiptRequests()
        if (isSendMessageDeliveryReceipt()) {
            deliveryReceiptManager.autoReceiptMode = AutoReceiptMode.ifIsSubscribed
        }
        else {
            deliveryReceiptManager.autoReceiptMode = AutoReceiptMode.disabled
        }

        // Enable Last Activity - XEP-0012
        val lastActivityManager = LastActivityManager.getInstanceFor(connection)
        lastActivityManager.enable()

        /*  Start up VCardAvatarManager / UserAvatarManager for mAccount auto-update */
        VCardAvatarManager.getInstanceFor(connection)
        UserAvatarManager.getInstanceFor(connection)

        /*
         * Must initialize omemoManager on every new connected connection, to ensure both pps and
         * omemoManager is referred to same instance of xmppConnection.  Perform only after connection
         * is connected to ensure the user is defined
         *
         * Move to authenticated stage?
         * Perform here to ensure only one androidOmemoService is created; otherwise onResume may create multiple instances
         */
        androidOmemoService = AndroidOmemoService(this)

        /*
         * add SupportedFeatures only prior to registerServiceDiscoveryManager. Otherwise found
         * some race condition with some optional features not properly initialized
         */
        addSupportedCapsFeatures()

        /*
         * XEP-0030:Service Discovery: Leave it to the last step so all features are included in
         * caps ver calculation
         */
        registerServiceDiscoveryManager()
    }

    /**
     * Defined all the entity capabilities for the EntityCapsManager to advertise in
     * disco#info query from other entities. Some features support are user selectable
     *
     * Note: Do not need to mention if there are already included in Smack Library and have been activated.
     */
    private fun addSupportedCapsFeatures() {
        supportedFeatures.clear()
        val isCallingDisabled = JabberActivator.getConfigurationService()!!
                .getBoolean(IS_CALLING_DISABLED, MainMenuActivity.disableMediaServiceOnFault)
        var isCallingDisabledForAccount = true
        var isVideoCallingDisabledForAccount = true

        if (accountID != null) {
            isCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, MainMenuActivity.disableMediaServiceOnFault)
            isVideoCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.IS_VIDEO_CALLING_DISABLED_FOR_ACCOUNT,
                MainMenuActivity.disableMediaServiceOnFault)
        }

        if (!MainMenuActivity.disableMediaServiceOnFault && !isCallingDisabled && !isCallingDisabledForAccount) {
            /*
             * Adds Jingle related features to the supported features.
             */
            // XEP-0166: Jingle
            supportedFeatures.add(URN_XMPP_JINGLE)
            // XEP-0167: Jingle RTP Sessions
            supportedFeatures.add(URN_XMPP_JINGLE_RTP)
            // XEP-0177: Jingle Raw UDP Transport Method
            supportedFeatures.add(URN_XMPP_JINGLE_RAW_UDP_0)
            supportedFeatures.add(JingleMessage.NAMESPACE)

            /*
             * Reflect the preference of the user with respect to the use of ICE.
             */
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_ICE, true)) {
                // XEP-0176: Jingle ICE-UDP Transport Method
                supportedFeatures.add(URN_XMPP_JINGLE_ICE_UDP_1)
            }

            // XEP-0167: Jingle RTP Sessions
            supportedFeatures.add(URN_XMPP_JINGLE_RTP_AUDIO)
            // XEP-0262: Use of ZRTP in Jingle RTP Sessions
            supportedFeatures.add(URN_XMPP_JINGLE_RTP_ZRTP)
            if (!isVideoCallingDisabledForAccount) {
                // XEP-0180: Jingle Video via RTP
                supportedFeatures.add(URN_XMPP_JINGLE_RTP_VIDEO)
                if (isDesktopSharingEnable) {
                    // Adds extension to support remote control as a sharing server (sharer).
                    supportedFeatures.add(InputEvtIQ.NAMESPACE_SERVER)

                    // Adds extension to support remote control as a sharing client (sharer).
                    supportedFeatures.add(InputEvtIQ.NAMESPACE_CLIENT)
                }
            }

            /*
             * Reflect the preference of the user with respect to the use of Jingle Nodes.
             */
            if (accountID.getAccountPropertyBoolean(
                        ProtocolProviderFactoryJabberImpl.IS_USE_JINGLE_NODES, true)) {
                // XEP-0278: Jingle Relay Nodes
                supportedFeatures.add(URN_XMPP_JINGLE_NODES)
            }

            // XEP-0251: Jingle Session Transfer
            supportedFeatures.add(URN_XMPP_JINGLE_TRANSFER_0)
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
                // XEP-0320: Use of DTLS-SRTP in Jingle Sessions
                supportedFeatures.add(URN_XMPP_JINGLE_DTLS_SRTP)
            }
        }

        // ===============================================
        // TODO: add the feature if any, corresponding to persistent presence, if someone knows
        // supportedFeatures.add(_PRESENCE_);

        // XEP-0065: SOCKS5 Bytestream
        supportedFeatures.add(Bytestream.NAMESPACE)
        supportedFeatures.add(JingleFileTransferImpl.NAMESPACE)

        // Do not advertise if user disable message delivery receipts option
        if (isSendMessageDeliveryReceipt()) {
            // XEP-0184: Message Delivery Receipts
            supportedFeatures.add(DeliveryReceipt.NAMESPACE)
        }
        else {
            supportedFeatures.remove(DeliveryReceipt.NAMESPACE)
        }

        // Do not advertise if user disable chat stat notifications option
        if (isSendChatStateNotifications()) {
            // XEP-0085: Chat State Notifications
            supportedFeatures.add(ChatStateExtension.NAMESPACE)
        }

        // XEP-0298: Delivering Conference Information to Jingle Participants (Coin)
        supportedFeatures.add(URN_XMPP_JINGLE_COIN)

        // this feature is mandatory to be compliant with Service Discovery - added by default
        // supportedFeatures.add("http://jabber.org/protocol/disco#info");

        // XEP-0047: In-Band Bytestreams
        supportedFeatures.add(DataPacketExtension.NAMESPACE)

        // XEP-0294: Jingle RTP Header Extensions Negotiation
        supportedFeatures.add(RtpHeader.NAMESPACE)

        // XEP-0308: Last Message Correction
        supportedFeatures.add(MessageCorrectExtension.NAMESPACE)

        /* This is the "main" feature to advertise when a client support muc. We have to
         * add some features for specific functionality we support in muc.
         * see https://www.xmpp.org/extensions/xep-0045.html
         * The http://jabber.org/protocol/muc feature is already included in smack.
         */
        // XEP-0045: Multi-User Chat
        supportedFeatures.add(MUCInitialPresence.NAMESPACE + "#rooms")
        supportedFeatures.add(MUCInitialPresence.NAMESPACE + "#traffic")

        // XEP-0054: vcard-temp
        supportedFeatures.add(VCard.NAMESPACE)

        // XEP-0095: Stream Initiation
        supportedFeatures.add(StreamInitiation.NAMESPACE)

        // XEP-0096: SI File Transfer
        supportedFeatures.add(FileTransferNegotiator.SI_PROFILE_FILE_TRANSFER_NAMESPACE)

        // XEP-0231: Bits of Binary
        supportedFeatures.add(BoBIQ.NAMESPACE)

        // XEP-0264: File Transfer Thumbnails
        supportedFeatures.add(Thumbnail.NAMESPACE)

        // XEP-0084: User Avatar
        supportedFeatures.add(AvatarMetadata.NAMESPACE_NOTIFY)
        supportedFeatures.add(AvatarData.NAMESPACE)

        // XEP-0384: OMEMO Encryption - obsoleted?
        // supportedFeatures.add(OmemoConstants.PEP_NODE_DEVICE_LIST_NOTIFY);

        // XEP-0092: Software Version
        supportedFeatures.add(URN_XMPP_IQ_VERSION)

        // Enable the XEP-0071: XHTML-IM feature for the account
        supportedFeatures.add(XHTMLExtension.NAMESPACE)
        // XHTMLManager.setServiceEnabled(mConnection, true);
    }

    /**
     * Registers the ServiceDiscoveryManager wrapper
     *
     * we setup all supported features before packets are actually being sent during feature
     * registration. So we'd better do it here so that our first presence update would
     * contain a caps with the right features.
     */
    private fun registerServiceDiscoveryManager() {
        // Add features aTalk supports in addition to smack.
        val featuresToRemove = arrayOf("http://jabber.org/protocol/commands")
        val featuresToAdd = supportedFeatures.toTypedArray()
        // boolean cacheNonCaps = true;
        discoveryManager = ScServiceDiscoveryManager(this, connection, featuresToRemove,
            featuresToAdd, true)

        /*
         * Expose the discoveryManager as service-public through the
         * OperationSetContactCapabilities of this ProtocolProviderService.
         */
        if (opsetContactCapabilities != null) opsetContactCapabilities!!.setDiscoveryManager(discoveryManager)
    }

    /**
     * Inorder to take effect on xmppConnection setup and the very first corresponding stanza being
     * sent; all smack default settings must be initialized prior to connection & account login.
     * Note: The getInstanceFor(xmppConnection) action during account login will auto-include
     * the smack Service Discovery feature. So it is no necessary to add the feature again in
     * method [.initialize]
     */
    private fun initSmackDefaultSettings() {
        val omemoReplyTimeout = 10000 // increase smack default timeout to 10 seconds

        /*
         * 	init Avatar to support persistent storage for both XEP-0153 and XEP-0084
         */
        initAvatarStore()

        /*
         * XEP-0153: vCard-Based Avatars - We will handle download of VCard on our own when there is an avatar update
         */
        VCardAvatarManager.setAutoDownload(false)

        /*
         * XEP-0084: User Avatars - Enable auto download when there is an avatar update
         */
        UserAvatarManager.setAutoDownload(true)

        /*
         * The CapsExtension node value to advertise in <presence/>.
         */
        val entityNode = if (OSUtils.IS_ANDROID) "https://android.atalk.org" else "https://atalk.org"
        EntityCapsManager.setDefaultEntityNode(entityNode)

        /* setup EntityCapsManager persistent store for XEP-0115: Entity Capabilities */
        ScServiceDiscoveryManager.initEntityPersistentStore()

        /*
         * The CapsExtension reply to be included in the caps <Identity/>
         */
        val category = "client"
        val appName = aTalkApp.getResString(R.string.APPLICATION_NAME)
        val type = "android"
        val identity = DiscoverInfo.Identity(category, appName, type)
        ServiceDiscoveryManager.setDefaultIdentity(identity)

        /*
         * XEP-0092: Software Version
         * Initialize jabber:iq:version support feature
         */
        val versionName = BuildConfig.VERSION_NAME
        val os = if (OSUtils.IS_ANDROID) "android" else "pc"
        VersionManager.setDefaultVersion(appName, versionName, os)

        /*
         * XEP-0199: XMPP Ping
         * Set the default ping interval in seconds used by PingManager. Can be omitted if you do
         * not wish to change the Smack Default Setting. The Smack default is 30 minutes.
         */
        PingManager.setDefaultPingInterval(defaultPingInterval)

        // cmeng - to take care of slow device S3=7s (N3=4.5S) and heavy loaded server.
        SmackConfiguration.setDefaultReplyTimeout(omemoReplyTimeout)

        // Enable smack debug message printing
        SmackConfiguration.DEBUG = true

        // Disable certain ReflectionDebuggerFactory.DEFAULT_DEBUGGERS loading for Android (that are only for windows)
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smackx.debugger.EnhancedDebugger")
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smack.debugger.LiteDebugger")

        // Disables unused class, throwing some errors on login (disco-info)
        SmackConfiguration.addDisabledSmackClass("org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager")

        // Just ignore UnparseableStanza to avoid connection abort
        SmackConfiguration.setDefaultParsingExceptionCallback { packetData: UnparseableStanza ->
            val ex = packetData.parsingException
            val errMsg = packetData.content.toString() + ": " + ex.message
            // StanzaErrorException xmppException = null;
            // if (errMsg.contains("403")) {
            //     StanzaError stanzaError = StanzaError.from(Condition.forbidden, errMsg).build();
            //     xmppException = new StanzaErrorException(null, stanzaError);
            // }
            // else if (errMsg.contains("503")) {
            //     StanzaError stanzaError = StanzaError.from(Condition.service_unavailable, errMsg).build();
            //     xmppException = new StanzaErrorException(null, stanzaError);
            // }
            //
            // if (xmppException != null)
            //     throw xmppException;
            // else
            Timber.w(ex, "UnparseableStanza Error: %s", errMsg)
        }
        XMPPTCPConnection.setUseStreamManagementDefault(true)

        // setup Dane provider
        MiniDnsDane.setup()

        // Enable XEP-0054 User Avatar mode to disable avatar photo update via <presence/><photo/>
        AvatarManager.setUserAvatar(true)

        // uncomment if XMPP Server cannot support supports SCRAMSHA1Mechanism
        // SASLAuthentication.unregisterSASLMechanism(SCRAMSHA1Mechanism.class.getName());
    }

    /**
     * Initialized the service implementation, and puts it in a state where it could inter-operate
     * with other services. It is strongly recommended that properties in this Map be mapped to
     * property names as specified by `AccountProperties`.
     *
     * @param screenName the account id/uin/screenName of the account that we're about to create
     * @param accountID the identifier of the account that this protocol provider represents.
     *
     * @see net.java.sip.communicator.service.protocol.AccountID
     */
    fun initialize(screenName: EntityBareJid?, accountID: JabberAccountID) {
        synchronized(initializationLock) {
            this.accountID = accountID

            // Initialize all the smack default setting
            initSmackDefaultSettings()

            /*
             * Tell Smack what are the additional IQProviders that aTalk can support
             */
            // register our coin provider
            ProviderManager.addIQProvider(CoinIQ.ELEMENT, CoinIQ.NAMESPACE, CoinIQProvider())

            // Jitsi Videobridge IQProvider and PacketExtensionProvider
            ProviderManager.addIQProvider(ColibriConferenceIQ.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                ColibriIQProvider())
            ProviderManager.addIQProvider(JibriIq.ELEMENT, JibriIq.NAMESPACE, JibriIqProvider())

            // register our input event provider
            ProviderManager.addIQProvider(InputEvtIQ.ELEMENT, InputEvtIQ.NAMESPACE, InputEvtIQProvider())

            // register our jingle provider
            ProviderManager.addIQProvider(Jingle.ELEMENT, Jingle.NAMESPACE, JingleProvider())

            // register our JingleInfo provider
            ProviderManager.addIQProvider(JingleInfoQueryIQ.ELEMENT, JingleInfoQueryIQ.NAMESPACE,
                JingleInfoQueryIQProvider())

            // replace the default StreamInitiationProvider with our
            // custom provider that handles the XEP-0264 <File/> element
            ProviderManager.addIQProvider(StreamInitiation.ELEMENT, StreamInitiation.NAMESPACE,
                ThumbnailStreamInitiationProvider())
            ProviderManager.addIQProvider(Registration.ELEMENT, Registration.NAMESPACE, RegistrationProvider())
            ProviderManager.addIQProvider(ConfirmExtension.ELEMENT, ConfirmExtension.NAMESPACE, ConfirmIQProvider())

            // XEP-0215: External Service Discovery to process IQ
            ProviderManager.addIQProvider(ExternalServices.ELEMENT, ExternalServices.NAMESPACE,
                ExternalServiceDiscoveryProvider())
            ProviderManager.addExtensionProvider(
                ConferenceDescriptionExtension.ELEMENT, ConferenceDescriptionExtension.NAMESPACE,
                ConferenceDescriptionExtensionProvider())
            ProviderManager.addExtensionProvider(Nick.QNAME.localPart, Nick.NAMESPACE, NickProvider())
            ProviderManager.addExtensionProvider(AvatarUrl.ELEMENT, AvatarUrl.NAMESPACE, AvatarUrl.Provider())
            ProviderManager.addExtensionProvider(StatsId.ELEMENT, StatsId.NAMESPACE, StatsId.Provider())
            ProviderManager.addExtensionProvider(IdentityExtension.ELEMENT, IdentityExtension.NAMESPACE,
                IdentityExtension.Provider())
            ProviderManager.addExtensionProvider(AvatarIdExtension.ELEMENT, AvatarIdExtension.NAMESPACE,
                DefaultExtensionElementProvider(AvatarIdExtension::class.java))
            ProviderManager.addExtensionProvider(JsonMessageExtension.ELEMENT, JsonMessageExtension.NAMESPACE,
                DefaultExtensionElementProvider(JsonMessageExtension::class.java))
            ProviderManager.addExtensionProvider(TranslationLanguageExtension.ELEMENT,
                TranslationLanguageExtension.NAMESPACE,
                DefaultExtensionElementProvider(TranslationLanguageExtension::class.java))
            ProviderManager.addExtensionProvider(
                TranscriptionLanguageExtension.ELEMENT, TranscriptionLanguageExtension.NAMESPACE,
                DefaultExtensionElementProvider(TranscriptionLanguageExtension::class.java))
            ProviderManager.addExtensionProvider(
                TranscriptionStatusExtension.ELEMENT, TranscriptionStatusExtension.NAMESPACE,
                DefaultExtensionElementProvider(TranscriptionStatusExtension::class.java))
            ProviderManager.addExtensionProvider(
                TranscriptionRequestExtension.ELEMENT, TranscriptionRequestExtension.NAMESPACE,
                DefaultExtensionElementProvider(TranscriptionRequestExtension::class.java))
            ProviderManager.addExtensionProvider(ConfirmExtension.ELEMENT, ConfirmExtension.NAMESPACE,
                ConfirmExtProvider())

            /*
             * Tell Smack what are the additional StreamFeatureProvider and ExtensionProviders that aTalk can support
             */
            ProviderManager.addStreamFeatureProvider(Registration.Feature.ELEMENT, Registration.Feature.NAMESPACE,
                RegistrationStreamFeatureProvider() as ExtensionElementProvider<ExtensionElement>)
            ProviderManager.addExtensionProvider(CapsExtension.ELEMENT, CapsExtension.NAMESPACE,
                CapsExtensionProvider())

            // XEP-0084: User Avatar (metadata) + notify
            ProviderManager.addExtensionProvider(AvatarMetadata.ELEMENT, AvatarMetadata.NAMESPACE,
                AvatarMetadataProvider())

            // XEP-0084: User Avatar (data)
            ProviderManager.addExtensionProvider(AvatarData.ELEMENT, AvatarData.NAMESPACE, AvatarDataProvider())

            // XEP-0153: vCard-Based Avatars
            ProviderManager.addExtensionProvider(VCardTempXUpdate.ELEMENT, VCardTempXUpdate.NAMESPACE,
                VCardTempXUpdateProvider())

            // XEP-0158: CAPTCHA Forms
            ProviderManager.addExtensionProvider(CaptchaExtension.ELEMENT, CaptchaExtension.NAMESPACE,
                CaptchaProvider())

            // in case of modified account, we clear list of supported features and all state
            // change listeners, otherwise we can have two OperationSet for same feature and it
            // can causes problem (i.e. two OperationSetBasicTelephony can launch two ICE
            // negotiations (with different ufrag/pwd) and peer will failed call. And by the way
            // user will see two dialog for answering/refusing the call
            clearRegistrationStateChangeListener()
            clearSupportedOperationSet()
            var protocolIconPath = accountID.getAccountPropertyString(ProtocolProviderFactory.PROTOCOL_ICON_PATH)
            if (protocolIconPath == null) protocolIconPath = "resources/images/protocol/jabber"
            protocolIcon = ProtocolIconJabberImpl(protocolIconPath)
            jabberStatusEnum = JabberStatusEnum.getJabberStatusEnum(protocolIconPath)

            /*
             * Here are all the OperationSets that aTalk supported; to be queried by the
             * application and take appropriate actions
             */
            // String keepAliveStrValue = mAccountID.getAccountPropertyString(ProtocolProviderFactory.KEEP_ALIVE_METHOD);

            // initialize the presence OperationSet
            val infoRetriever = InfoRetriever(this, screenName)
            val persistentPresence = OperationSetPersistentPresenceJabberImpl(this, infoRetriever)
            addSupportedOperationSet(OperationSetPersistentPresence::class.java, persistentPresence)

            // register it once again for those that simply need presence
            addSupportedOperationSet(OperationSetPresence::class.java, persistentPresence)
            if (accountID.getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_READ_ONLY_GROUPS) != null) {
                addSupportedOperationSet(OperationSetPersistentPresencePermissions::class.java,
                    OperationSetPersistentPresencePermissionsJabberImpl(this))
            }

            // initialize the IM operation set
            val basicInstantMessaging = OperationSetBasicInstantMessagingJabberImpl(this)
            addSupportedOperationSet(OperationSetBasicInstantMessaging::class.java, basicInstantMessaging)
            addSupportedOperationSet(OperationSetMessageCorrection::class.java, basicInstantMessaging)

            // The XHTMLExtension.NAMESPACE: http://jabber.org/protocol/xhtml-im feature is included already in smack.
            addSupportedOperationSet(OperationSetExtendedAuthorizations::class.java,
                OperationSetExtendedAuthorizationsJabberImpl(this, persistentPresence))

            // initialize the chat state notifications operation set
            addSupportedOperationSet(OperationSetChatStateNotifications::class.java,
                OperationSetChatStateNotificationsJabberImpl(this))

            // Initialize the multi-user chat operation set
            addSupportedOperationSet(OperationSetMultiUserChat::class.java,
                OperationSetMultiUserChatJabberImpl(this))
            addSupportedOperationSet(OperationSetJitsiMeetTools::class.java,
                OperationSetJitsiMeetToolsJabberImpl(this))
            addSupportedOperationSet(OperationSetServerStoredContactInfo::class.java,
                OperationSetServerStoredContactInfoJabberImpl(infoRetriever))
            val accountInfo = OperationSetServerStoredAccountInfoJabberImpl(this, infoRetriever, screenName!!)
            addSupportedOperationSet(OperationSetServerStoredAccountInfo::class.java, accountInfo)

            // Initialize avatar operation set
            addSupportedOperationSet(OperationSetAvatar::class.java,
                OperationSetAvatarJabberImpl(this, accountInfo))

            // initialize the file transfer operation set
            addSupportedOperationSet(OperationSetFileTransfer::class.java,
                OperationSetFileTransferJabberImpl(this))
            addSupportedOperationSet(OperationSetInstantMessageTransform::class.java,
                OperationSetInstantMessageTransformImpl())

            // initialize the thumbNailed file factory operation set
            addSupportedOperationSet(OperationSetThumbnailedFileFactory::class.java,
                OperationSetThumbnailedFileFactoryImpl())

            // initialize the telephony operation set
            val isCallingDisabled = JabberActivator.getConfigurationService()!!
                    .getBoolean(IS_CALLING_DISABLED, MainMenuActivity.disableMediaServiceOnFault)
            val isCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
                ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, MainMenuActivity.disableMediaServiceOnFault)

            // Check if calling is enabled.
            if (!MainMenuActivity.disableMediaServiceOnFault && !isCallingDisabled && !isCallingDisabledForAccount) {
                val basicTelephony = OperationSetBasicTelephonyJabberImpl(this)
                addSupportedOperationSet(OperationSetAdvancedTelephony::class.java, basicTelephony)
                addSupportedOperationSet(OperationSetBasicTelephony::class.java, basicTelephony)
                addSupportedOperationSet(OperationSetSecureZrtpTelephony::class.java, basicTelephony)
                addSupportedOperationSet(OperationSetSecureSDesTelephony::class.java, basicTelephony)

                // initialize audio telephony OperationSet
                addSupportedOperationSet(OperationSetTelephonyConferencing::class.java,
                    OperationSetTelephonyConferencingJabberImpl(this))
                addSupportedOperationSet(OperationSetBasicAutoAnswer::class.java,
                    OperationSetAutoAnswerJabberImpl(this))
                addSupportedOperationSet(OperationSetResourceAwareTelephony::class.java,
                    OperationSetResAwareTelephonyJabberImpl(basicTelephony))
                val isVideoCallingDisabledForAccount = accountID.getAccountPropertyBoolean(
                    ProtocolProviderFactory.IS_VIDEO_CALLING_DISABLED_FOR_ACCOUNT,
                    MainMenuActivity.disableMediaServiceOnFault)
                if (!isVideoCallingDisabledForAccount) {
                    // initialize video telephony OperationSet
                    addSupportedOperationSet(OperationSetVideoTelephony::class.java,
                        OperationSetVideoTelephonyJabberImpl(basicTelephony))
                }

                // Only init video bridge if enabled
                val isVideobridgeDisabled = JabberActivator.getConfigurationService()!!
                        .getBoolean(OperationSetVideoBridge.IS_VIDEO_BRIDGE_DISABLED, false)
                if (!isVideobridgeDisabled) {
                    // init video bridge
                    addSupportedOperationSet(OperationSetVideoBridge::class.java, OperationSetVideoBridgeImpl(this))
                }

                // init DTMF
                val operationSetDTMF = OperationSetDTMFJabberImpl(this)
                addSupportedOperationSet(OperationSetDTMF::class.java, operationSetDTMF)
                addSupportedOperationSet(OperationSetIncomingDTMF::class.java, OperationSetIncomingDTMFJabberImpl())
            }

            // OperationSetContactCapabilities
            opsetContactCapabilities = OperationSetContactCapabilitiesJabberImpl(this)
            if (discoveryManager != null) opsetContactCapabilities!!.setDiscoveryManager(discoveryManager)
            addSupportedOperationSet(OperationSetContactCapabilities::class.java, opsetContactCapabilities!!)
            val opsetChangePassword = OperationSetChangePasswordJabberImpl(this)
            addSupportedOperationSet(OperationSetChangePassword::class.java, opsetChangePassword)
            val opsetCusaxCusaxUtils = OperationSetCusaxUtilsJabberImpl()
            addSupportedOperationSet(OperationSetCusaxUtils::class.java, opsetCusaxCusaxUtils)
            val isUserSearchEnabled = accountID.getAccountPropertyBoolean(IS_USER_SEARCH_ENABLED_PROPERTY, false)
            if (isUserSearchEnabled) {
                addSupportedOperationSet(OperationSetUserSearch::class.java, OperationSetUserSearchJabberImpl(this))
            }
            val opsetTLS = OperationSetTLSJabberImpl(this)
            addSupportedOperationSet(OperationSetTLS::class.java, opsetTLS)
            val opsetConnectionInfo = OperationSetConnectionInfoJabberImpl()
            addSupportedOperationSet(OperationSetConnectionInfo::class.java, opsetConnectionInfo)
            isInitialized = true
        }
    }

    /**
     * Makes the service implementation close all open sockets and release any resources that it
     * might have taken and prepare for shutdown/garbage collection.
     */
    override fun shutdown() {
        synchronized(initializationLock) {
            Timber.log(TimberLog.FINER, "Killing the Jabber Protocol Provider.")

            // kill all active calls
            val telephony = getOperationSet(
                OperationSetBasicTelephony::class.java) as OperationSetBasicTelephonyJabberImpl?
            telephony?.shutdown()
            disconnectAndCleanConnection()
            isInitialized = false
        }
    }

    /**
     * Validates the node part of a JID and returns an error message if applicable and a
     * suggested correction.
     *
     * @param contactId the contact identifier to validate
     * @param result Must be supplied as an empty a list. Implementors add items:
     *
     *  1. is the error message if applicable
     *  1. a suggested correction. Index 1 is optional and can only be present if there was a validation failure.
     *
     *
     * @return true if the contact id is valid, false otherwise
     */
    override fun validateContactAddress(contactId: String?, result: MutableList<String?>?): Boolean {
        var iContactId = contactId
        requireNotNull(result) { "result must be an empty list" }
        result.clear()
        val b = try {
            iContactId = iContactId!!.trim { it <= ' ' }
            // no suggestion for an empty id
            if (iContactId.isEmpty()) {
                result.add(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS, iContactId))
                return false
            }
            var user = iContactId
            var remainder = ""
            val at = iContactId.indexOf('@')
            if (at > -1) {
                user = iContactId.substring(0, at)
                remainder = iContactId.substring(at)
            }

            // <conforming-char> ::= #x21 | [#x23-#x25] | [#x28-#x2E] |
            // [#x30-#x39] | #x3B | #x3D | #x3F |
            // [#x41-#x7E] | [#x80-#xD7FF] |
            // [#xE000-#xFFFD] | [#x10000-#x10FFFF]
            var valid = true
            val suggestion = StringBuilder()
            for (c in user.toCharArray()) {
                if ((((c.code == 0x21 || c.code in 0x23..0x25 || c.code in 0x28..0x2e || c.code in 0x30..0x39 || c.code == 0x3b || c.code == 0x3d || c.code == 0x3f || c.code in 0x41..0x7e || c.code >= 0x80) && c.code <= 0xd7ff || c.code >= 0xe000) && c.code > 0xfffd)) {
                    valid = false
                }
                else {
                    suggestion.append(c)
                }
            }
            if (!valid) {
                result.add(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS, iContactId))
                result.add(suggestion.toString() + remainder)
                return false
            }
            return true
        } catch (ex: Exception) {
            result.add(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS, iContactId))
        }
        return false
    }

    /**
     * Determines whether a specific `XMPPException` signals that attempted login has failed.
     *
     * Calling method will trigger a re-login dialog if the return `failureMode` is not
     * `SecurityAuthority.REASON_UNKNOWN` etc
     *
     * Add additional exMsg message if necessary to achieve this effect.
     *
     * @param ex the `Exception` which is to be determined whether it signals
     * that attempted authentication has failed
     *
     * @return if the specified `ex` signals that attempted authentication is
     * known' otherwise `SecurityAuthority.REASON_UNKNOWN` is returned.
     * @see SecurityAuthority.REASON_UNKNOWN
     */
    private fun checkLoginFailMode(ex: Exception): Int {
        var failureMode = SecurityAuthority.REASON_UNKNOWN
        val exMsg = ex.message!!.lowercase()

        /*
         * As there are no defined type or reason specified for XMPPException, we try to
         * determine the reason according to the received exception messages that are relevant
         * and found in smack 4.2.0
         */
        if (exMsg.contains("saslerror") && exMsg.contains("external")) {
            failureMode = SecurityAuthority.SASL_ERROR_EXTERNAL
        }
        else if (exMsg.contains("saslerror") && (exMsg.contains("invalid")
                        || exMsg.contains("error") || exMsg.contains("failed"))) {
            failureMode = SecurityAuthority.INVALID_AUTHORIZATION
        }
        else if (exMsg.contains("forbidden")) {
            failureMode = SecurityAuthority.AUTHENTICATION_FORBIDDEN
        }
        else if (exMsg.contains("not-authorized")) {
            failureMode = SecurityAuthority.NOT_AUTHORIZED
        }
        else if (exMsg.contains("unable to determine password")) {
            failureMode = SecurityAuthority.WRONG_PASSWORD
        }
        else if (exMsg.contains("service-unavailable")
                || exMsg.contains("tls is required")) {
            failureMode = SecurityAuthority.AUTHENTICATION_FAILED
        }
        else if (exMsg.contains("remote-server-timeout")
                || exMsg.contains("no response received within reply timeout")
                || exMsg.contains("connection failed")) {
            failureMode = SecurityAuthority.CONNECTION_FAILED
        }
        else if (exMsg.contains("remote-server-not-found")
                || exMsg.contains("internal-server-error")) {
            failureMode = SecurityAuthority.NO_SERVER_FOUND
        }
        else if (exMsg.contains("conflict")) {
            failureMode = SecurityAuthority.CONFLICT
        }
        else if (exMsg.contains("policy-violation")) {
            failureMode = SecurityAuthority.POLICY_VIOLATION
        }
        else if (exMsg.contains("not-allowed")) {
            failureMode = SecurityAuthority.DNSSEC_NOT_ALLOWED
        }
        else if (exMsg.contains("security-exception")) {
            failureMode = SecurityAuthority.SECURITY_EXCEPTION
        }
        return failureMode
    }

    /**
     * Tries to determine the appropriate message and status to fire, according to the exception.
     *
     * @param ex the [XMPPException] or [SmackException]  that caused the state change.
     */
    private fun fireRegistrationStateChanged(ex: Exception) {
        var regState = RegistrationState.UNREGISTERED
        var reasonCode = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED
        val failMode = checkLoginFailMode(ex)
        when (failMode) {
            SecurityAuthority.INVALID_AUTHORIZATION -> {
                regState = RegistrationState.AUTHENTICATION_FAILED
                reasonCode = RegistrationStateChangeEvent.REASON_AUTHENTICATION_FAILED
            }
            SecurityAuthority.AUTHENTICATION_FORBIDDEN -> {
                regState = RegistrationState.CONNECTION_FAILED
                reasonCode = RegistrationStateChangeEvent.REASON_IB_REGISTRATION_FAILED
            }
            SecurityAuthority.NO_SERVER_FOUND -> {
                regState = RegistrationState.CONNECTION_FAILED
                reasonCode = RegistrationStateChangeEvent.REASON_SERVER_NOT_FOUND
            }
            SecurityAuthority.CONNECTION_FAILED -> {
                regState = RegistrationState.CONNECTION_FAILED
                reasonCode = RegistrationStateChangeEvent.REASON_NOT_SPECIFIED
            }
            SecurityAuthority.AUTHENTICATION_FAILED -> {
                regState = RegistrationState.AUTHENTICATION_FAILED
                reasonCode = RegistrationStateChangeEvent.REASON_TLS_REQUIRED
            }
            SecurityAuthority.CONFLICT -> {
                regState = RegistrationState.CONNECTION_FAILED
                reasonCode = RegistrationStateChangeEvent.REASON_MULTIPLE_LOGIN
            }
        }

        // we fired these for some reason that we have gone offline; lets clean
        // the current connection state for any future connections
        if (regState === RegistrationState.UNREGISTERED
                || regState === RegistrationState.CONNECTION_FAILED) {
            disconnectAndCleanConnection()
        }
        var reason = ex.message
        fireRegistrationStateChanged(registrationState, regState, reasonCode, reason!!)

        // Show error and abort further attempt for unknown or specified exceptions; others proceed to retry
        if (failMode == SecurityAuthority.REASON_UNKNOWN || failMode == SecurityAuthority.SASL_ERROR_EXTERNAL || failMode == SecurityAuthority.SECURITY_EXCEPTION || failMode == SecurityAuthority.POLICY_VIOLATION) {
            if (TextUtils.isEmpty(reason) && ex.cause != null) reason = ex.cause!!.message
            DialogActivity.showDialog(aTalkApp.globalContext,
                aTalkApp.getResString(R.string.service_gui_ERROR), reason)
        }
        else {
            // Try re-register and ask user for new credentials giving detail reason description.
            if (ex is XMPPErrorException) {
                reason = ex.stanzaError.descriptiveText
            }
            reRegister(failMode, reason)
        }
    }

    /**
     * Determines if the given list of `features` is supported by the specified jabber id.
     *
     * @param jid the jabber id for which to check;
     * Jid must be FullJid unless it is for service e.g. proxy.atalk.org, conference.atalk.org
     * @param features the list of features to check for
     *
     * @return `true` if the list of features is supported; otherwise, `false`
     */
    fun isFeatureListSupported(jid: Jid, vararg features: String?): Boolean {
        try {
            if (discoveryManager == null) return false
            val featureInfo = discoveryManager!!.discoverInfoNonBlocking(jid) ?: return false

            // If one is not supported we return false and don't check the others.
            for (feature in features) {
                if (!featureInfo.containsFeature(feature)) {
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            Timber.d(e, "Failed to retrieve discovery info.")
        }
        return false
    }

    /**
     * Determines if the given list of `features` is supported by the specified jabber id.
     *
     * @param jid the jabber id that we'd like to get information about
     * @param feature the feature to check for
     *
     * @return `true` if the list of features is supported, otherwise returns `false`
     */
    fun isFeatureSupported(jid: Jid, feature: String?): Boolean {
        return isFeatureListSupported(jid, feature)
    }

    /**
     * Returns the full jabber id (jid) corresponding to the given contact. If the provider is not
     * connected then just returns the given jid (BareJid).
     *
     * @param contact the contact, for which we're looking for a full jid
     *
     * @return the jid of the specified contact or bareJid if the provider is not yet connected;
     */
    fun getFullJidIfPossible(contact: Contact): Jid {
        return getFullJidIfPossible(contact.contactJid!!)
    }

    /**
     * Returns the full jabber id (jid) for the given jid if possible. If the provider is not
     * connected then just returns the given jid (BareJid).
     *
     * @param jid_ the contact jid (i.e. usually without resource) whose full jid we are looking for.
     *
     * @return the jid of the specified contact or bareJid if the provider is not yet connected;
     */
    fun getFullJidIfPossible(jid_: Jid): Jid {
        // when we are not connected there is no full jid
        var jid = jid_
        if (connection != null && connection!!.isConnected) {
            if (mRoster != null) jid = mRoster!!.getPresence(jid.asBareJid()).from
        }
        return jid
    }

    /**
     * The trust manager which asks the client whether to trust a particular certificate,
     * when it is not android root's CA trusted.
     * Note: X509ExtendedTrustManager required API-24
     */
    private inner class HostTrustManager
    /**
     * Creates the custom trust manager.
     *
     * @param tm the default trust manager.
     */
        (
            /**
             * The default trust manager.
             */
            private val tm: X509TrustManager?,
    ) : X509TrustManager // X509ExtendedTrustManager
    {
        /**
         * Not used.
         *
         * @return nothing.
         */
        override fun getAcceptedIssuers(): Array<X509Certificate?> {
            return arrayOfNulls(0)
        }

        /**
         * Not used.
         *
         * @param chain the cert chain.
         * @param authType authentication type like: RSA.
         *
         * @throws CertificateException never
         * @throws UnsupportedOperationException always
         */
        @Throws(CertificateException::class, UnsupportedOperationException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            throw UnsupportedOperationException()
        }

        // All the below 4 Overrides are for X509ExtendedTrustManager
        // @Override
        @Throws(CertificateException::class)
        fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?, socket: Socket?) {
            throw UnsupportedOperationException()
        }

        // @Override
        @Throws(CertificateException::class)
        fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            checkServerTrusted(chain, authType)
        }

        // @Override
        @Throws(CertificateException::class)
        fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?, engine: SSLEngine?) {
            throw UnsupportedOperationException()
        }

        // @Override
        @Throws(CertificateException::class)
        fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            checkServerTrusted(chain, authType)
        }

        /**
         * Check whether a certificate is trusted, if not ask user whether he trusts it.
         *
         * @param chain the certificate chain.
         * @param authType authentication type like: RSA.
         *
         * @throws CertificateException not trusted.
         */
        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            abortConnecting = true
            // Timber.e(new Exception("TSL Certificate Invalid"));
            try {
                tm!!.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                // notify in a separate thread to avoid a deadlock when a reg state listener
                // accesses a synchronized XMPPConnection method (like getRoster)
                Thread {
                    fireRegistrationStateChanged(registrationState,
                        RegistrationState.UNREGISTERED, RegistrationStateChangeEvent.REASON_USER_REQUEST,
                        "Not trusted certificate")
                }.start()
                throw e
            }
            if (abortConnecting) {
                // connect hasn't finished we will continue normally
                abortConnecting = false
            }
            else {
                // in this situation connect method has finished and it was disconnected so we
                // wont to connect. register.connect in new thread so we can release the current
                // connecting thread, otherwise this blocks jabber
                Thread { reRegister(SecurityAuthority.CONNECTION_FAILED, null) }.start()
            }
        }
    }// mResource can be null if user is not registered, so use default

    /**
     * Return the EntityFullJid associate with this protocol provider.
     *
     * Build our own EntityJid if not connected. May not be full compliant - For explanation
     *
     * @return the Jabber EntityFullJid
     * @see AbstractXMPPConnection .user
     */
    val ourJID: EntityFullJid
        get() {
            val fullJid = if (connection != null) {
                connection!!.user
            }
            else {
                // mResource can be null if user is not registered, so use default
                loadResource()
                JidCreate.entityFullFrom(accountID.bareJid!!.asEntityBareJidIfPossible(), mResource)
            }
            return fullJid
        }

    /**
     * Returns the `InetAddress` that is most likely to be to be used as a next hop when
     * contacting our XMPP server. This is an utility method that is used whenever we have to
     * choose one of our local addresses (e.g. when trying to pick a best candidate for raw udp).
     * It is based on the assumption that, in absence of any more specific details, chances are
     * that we will be accessing remote destinations via the same interface that we are using to
     * access our jabber server.
     *
     * @return the `InetAddress` that is most likely to be to be used as a next hop when contacting our server.
     * @throws IllegalArgumentException if we don't have a valid server.
     */
    @get:Throws(IllegalArgumentException::class)
    val nextHop: InetAddress
        get() {
            val nextHop: InetAddress
            val nextHopStr = if (proxy != null) {
                proxy!!.proxyAddress
            }
            else {
                connection!!.host
            }
            nextHop = try {
                getInetAddress(nextHopStr)
            } catch (ex: UnknownHostException) {
                throw IllegalArgumentException("seems we don't have a valid next hop.", ex)
            }
            Timber.d("Returning address %s as next hop.", nextHop)
            return nextHop
        }

    /**
     * Start auto-discovery of JingleNodes tracker/relays.
     */
    fun startJingleNodesDiscovery() {
        // Jingle Nodes Service Initialization;
        val accID = accountID as JabberAccountIDImpl?

        // v2.2.2  mConnection == null on FFR ???. Call only on RegistrationState.REGISTERED state?
        val service = SmackServiceNode(connection, 60000)

        // make sure SmackServiceNode will clean up when connection is closed
        connection!!.addConnectionListener(service)
        for (desc in accID!!.getJingleNodes()) {
            var entry: TrackerEntry? = null
            try {
                entry = TrackerEntry(
                    if (desc.isRelaySupported()) TrackerEntry.Type.relay else TrackerEntry.Type.tracker,
                    TrackerEntry.Policy._public,
                    JidCreate.from(desc.getJID()), JingleChannelIQ.UDP)
            } catch (e: XmppStringprepException) {
                e.printStackTrace()
            }
            service.addTrackerEntry(entry)
        }
        Thread(JingleNodesServiceDiscovery(service, connection!!, accID, jingleNodesSyncRoot)).start()
        jingleNodesServiceNode = service
    }

    /**
     * Get the Jingle Nodes service. Note that this method will block until Jingle Nodes auto
     * discovery (if enabled) finished.
     *
     * @return Jingle Nodes service
     */
    /**
     * Jingle Nodes service.
     */
    var jingleNodesServiceNode: SmackServiceNode? = null
        get() {
            synchronized(jingleNodesSyncRoot) { return field }
        }

    /**
     * Returns true if our account is a Gmail or a Google Apps ones.
     *
     * @return true if our account is a Gmail or a Google Apps ones.
     */
    val isGmailOrGoogleAppsAccount: Boolean
        get() {
            var domain = accountID.service
            if (accountID.isServerOverridden) {
                domain = accountID.getAccountPropertyString(ProtocolProviderFactory.SERVER_ADDRESS, domain)!!
            }
            return isGmailOrGoogleAppsAccount(domain)
        }

    /**
     * Gets the entity PRE_KEY_ID of the first Jitsi Videobridge associated with [.mConnection] i.e.
     * provided by the `serviceName` of `mConnection`.
     * Abort checking if last check returned with NoResponseException. Await 45s wait time
     *
     * @return the entity PRE_KEY_ID of the first Jitsi Videobridge associated with `mConnection`
     */
    val jitsiVideobridge: Jid?
        get() {
            if (connection != null && connection!!.isConnected) {
                val discoveryManager = discoveryManager
                val serviceName = connection!!.xmppServiceDomain
                var discoverItems: DiscoverItems? = null
                try {
                    discoverItems = discoveryManager!!.discoverItems(serviceName)
                } catch (ex: NoResponseException) {
                    Timber.d(ex, "Failed to discover the items associated with Jabber entity: %s", serviceName)
                } catch (ex: NotConnectedException) {
                    Timber.d(ex, "Failed to discover the items associated with Jabber entity: %s", serviceName)
                } catch (ex: XMPPException) {
                    Timber.d(ex, "Failed to discover the items associated with Jabber entity: %s", serviceName)
                } catch (ex: InterruptedException) {
                    Timber.d(ex, "Failed to discover the items associated with Jabber entity: %s", serviceName)
                }
                if (discoverItems !== null && !isLastVbNoResponse) {
                    val discoverItemIter = discoverItems.items

                    for (discoverItem in discoverItemIter) {
                        val entityID = discoverItem.entityID
                        var discoverInfo: DiscoverInfo? = null

                        try {
                            discoverInfo = discoveryManager!!.discoverInfo(entityID)
                        } catch (ex: NoResponseException) {
                            Timber.w(ex, "Failed to discover information about Jabber entity: %s", entityID)
                            isLastVbNoResponse = true
                        } catch (ex: NotConnectedException) {
                            Timber.w(ex, "Failed to discover information about Jabber entity: %s", entityID)
                        } catch (ex: XMPPException) {
                            Timber.w(ex, "Failed to discover information about Jabber entity: %s", entityID)
                        } catch (ex: InterruptedException) {
                            Timber.w(ex, "Failed to discover information about Jabber entity: %s", entityID)
                        }

                        if ((discoverInfo !== null) && discoverInfo.containsFeature(ColibriConferenceIQ.NAMESPACE)) {
                            return entityID
                        }
                    }
                }
            }
            return null
        }

    /**
     * ============== HTTP Authorization Request received ===============.
     *
     * Handler for the incoming HttpAuthorizationRequest received. Generate a HeadsUp notification to alert user
     * if the request is via IQ and the device is in locked state; user must exit device locked state to handle
     * the next incoming request; the current request dialog launch may have been ignored by android if aTalk
     * was not in Foreground state.
     *
     * @param from the server from which the request is sent
     * @param confirmExt auth request confirm element
     * @param instruction the instruction send from the server
     */
    override fun onHttpAuthorizationRequest(from: DomainBareJid, confirmExt: ConfirmExtension, instruction: String) {
        var iInstruction = instruction
        val authId = confirmExt.id
        if (StringUtils.isEmpty(iInstruction)) {
            iInstruction = aTalkApp.getResString(R.string.service_gui_HTTP_REQUEST_INSTRUCTION,
                confirmExt.method, confirmExt.url, authId, accountID.accountJid)

            // Show an headsUp notification for incoming IQ auth request when device is in locked state to alert user
            if (aTalkApp.isDeviceLocked) {
                NotificationManager.fireChatNotification(from, NotificationManager.INCOMING_MESSAGE,
                    aTalkApp.getResString(R.string.service_gui_HTTP_REQUEST_TITLE), iInstruction, null)
            }
        }
        val listenerId = DialogActivity.showConfirmDialog(aTalkApp.globalContext,
            aTalkApp.getResString(R.string.service_gui_HTTP_REQUEST_TITLE), iInstruction,
            aTalkApp.getResString(R.string.service_gui_ACCEPT),
            object : DialogListener {
                override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                    val id = dialog.listenerID
                    httpAuthorizationRequestManager!!.acceptId(mAuthIds[id])
                    mAuthIds.remove(id)
                    return true
                }

                override fun onDialogCancelled(dialog: DialogActivity) {
                    val id = dialog.listenerID
                    httpAuthorizationRequestManager!!.rejectId(mAuthIds[id])
                    mAuthIds.remove(id)
                }
            }
        )
        mAuthIds[listenerId] = authId
    }
// ==================================================
    /**
     * Sets the traffic class for the XMPP signalling socket.
     */
    private fun setTrafficClass() {
        val s = socket
        if (s != null) {
            val configService = JabberActivator.getConfigurationService()
            val dscp = configService!!.getString(XMPP_DSCP_PROPERTY)
            if (dscp != null) {
                try {
                    val dscpInt = dscp.toInt() shl 2
                    if (dscpInt > 0) s.trafficClass = dscpInt
                } catch (e: Exception) {
                    Timber.i(e, "Failed to set trafficClass")
                }
            }
        }
    }

    /**
     * Return no null if the SSL socket (if TLS used).
     * Retrieve the XMPP connection secureSocket use in protocolProvider (by reflection)
     *
     * @return The SSL socket or null if not used
     */
    val sslSocket: SSLSocket?
        get() {
            var secureSocket: SSLSocket? = null
            if (connection != null && connection!!.isConnected) {
                try {
                    val socket = XMPPTCPConnection::class.java.getDeclaredField("secureSocket")
                    socket.isAccessible = true
                    secureSocket = socket[connection] as SSLSocket
                } catch (e: Exception) {
                    Timber.w("Access to XMPPTCPConnection.secureSocket not found!")
                }
            }
            return secureSocket
        }

    /**
     * Retrieve the XMPP connection socket used by the protocolProvider (by reflection)
     *
     * @return the socket which is used for this connection.
     * XMPPTCPConnection.socket
     */
    val socket: Socket?
        get() {
            var socket: Socket? = null
            if (connection != null && connection!!.isConnected) {
                try {
                    val rField = XMPPTCPConnection::class.java.getDeclaredField("socket")
                    rField.isAccessible = true
                    socket = rField[connection] as Socket
                } catch (e: Exception) {
                    Timber.w("Access to XMPPTCPConnection.socket not found!")
                }
            }
            return socket
        }

    companion object {
        /**
         * Jingle's Discovery Info common URN.
         */
        const val URN_XMPP_JINGLE = Jingle.NAMESPACE

        /**
         * Jingle's Discovery Info URN for RTP support.
         */
        const val URN_XMPP_JINGLE_RTP = RtpDescription.NAMESPACE

        /**
         * Jingle's Discovery Info URN for RTP support with audio.
         */
        const val URN_XMPP_JINGLE_RTP_AUDIO = "urn:xmpp:jingle:apps:rtp:audio"

        /**
         * Jingle's Discovery Info URN for RTP support with video.
         */
        const val URN_XMPP_JINGLE_RTP_VIDEO = "urn:xmpp:jingle:apps:rtp:video"

        /**
         * Jingle's Discovery Info URN for ZRTP support with RTP.
         */
        const val URN_XMPP_JINGLE_RTP_ZRTP = ZrtpHash.NAMESPACE

        /**
         * Jingle's Discovery Info URN for ICE_UDP transport support.
         */
        const val URN_XMPP_JINGLE_RAW_UDP_0 = RawUdpTransport.NAMESPACE

        /**
         * Jingle's Discovery Info URN for ICE_UDP transport support.
         */
        const val URN_XMPP_JINGLE_ICE_UDP_1 = IceUdpTransport.NAMESPACE

        /**
         * Jingle's Discovery Info URN for Jingle Nodes support.
         */
        const val URN_XMPP_JINGLE_NODES = "http://jabber.org/protocol/jinglenodes"

        /**
         * Jingle's Discovery Info URN for "XEP-0251: Jingle Session Transfer" support.
         */
        const val URN_XMPP_JINGLE_TRANSFER_0 = SdpTransfer.NAMESPACE

        /**
         * Jingle's Discovery Info URN for
         * XEP-0298: Delivering Conference Information to Jingle Participants (Coin)
         */
        const val URN_XMPP_JINGLE_COIN = "urn:xmpp:coin"

        /**
         * Jingle's Discovery Info URN for &quot;XEP-0320: Use of DTLS-SRTP in Jingle Sessions&quot;.
         * "urn:xmpp:jingle:apps:dtls:0"
         */
        const val URN_XMPP_JINGLE_DTLS_SRTP = SrtpFingerprint.NAMESPACE

        /**
         * Discovery Info URN for classic RFC3264-style Offer/Answer negotiation with no support for
         * Trickle ICE and low tolerance to transport/payload separation. Defined in XEP-0176
         */
        const val URN_IETF_RFC_3264 = "urn:ietf:rfc:3264"

        /**
         * [XEP-0092: Software Version](https://xmpp.org/extensions/xep-0092.html).
         */
        // Used in JVB
        const val URN_XMPP_IQ_VERSION = "jabber:iq:version"

        /**
         * URN for XEP-0077 inband registration
         */
        const val URN_REGISTER = "jabber:iq:register"

        /*
     * Determines the requested DNSSEC security mode.
     * <b>Note that Smack's support for DNSSEC/DANE is experimental!</b>
     *
     * The default '{@link #disabled}' means that neither DNSSEC nor DANE verification will be performed. When
     * '{@link #needsDnssec}' is used, then the connection will not be established if the resource records used
     * to connect to the XMPP service are not authenticated by DNSSEC. Additionally, if '{@link #needsDnssecAndDane}'
     * is used, then the XMPP service's TLS certificate is verified using DANE.
     */
        // Do not perform any DNSSEC authentication or DANE verification.
        private const val DNSSEC_DISABLE = "disabled"

        // Require all DNS information to be authenticated by DNSSEC.
        private const val DNSSEC_ONLY = "needsDnssec"

        // Require all DNS information to be authenticated by DNSSEC and require the XMPP service's TLS certificate
        // to be verified using DANE.
        private const val DNSSEC_AND_DANE = "needsDnssecAndDane"
        /*
     * The name of the property under which the user may specify if the desktop streaming or sharing should be disabled.
     */
        // private static final String IS_DESKTOP_STREAMING_DISABLED = "protocol.jabber.DESKTOP_STREAMING_DISABLED";
        /**
         * The name of the property under which the user may specify if audio/video calls should be disabled.
         */
        private const val IS_CALLING_DISABLED = "protocol.jabber.CALLING_DISABLED"

        /**
         * Smack packet maximum reply timeout - Smack will immediately return on a reply or until a timeout
         * before issues exception. Need this to take care for some servers' response on some packages
         * e.g. disco#info (30 seconds). Also on some slow client e.g. Samsung SII takes up to 30
         * Sec to response to sasl authentication challenge on first login
         */
        const val SMACK_REPLY_EXTENDED_TIMEOUT_30 = 30000L // 30 seconds

        // vCard save takes about 29 seconds on Note 8
        const val SMACK_REPLY_EXTENDED_TIMEOUT_40 = 40000L // 40 seconds

        // Some server takes ~8sec to response due to disco#info request (default timer = 5seconds)
        // File transfer e.g. IBB across server can take more than 5 seconds
        const val SMACK_REPLY_EXTENDED_TIMEOUT_10 = 10000L // 10 seconds
        const val SMACK_REPLY_OMEMO_INIT_TIMEOUT = 15000L // 15 seconds

        /**
         * aTalk Smack packet reply default timeout - use Smack default instead of 10s (starting v2.1.8).
         * Too many FFR on ANR at smack.StanzaCollector.nextResult (StanzaCollector.java:206) when server is not responding.
         * - change the xmppConnect replyTimeout to smack default of 5 seconds under normal operation.
         */
        val SMACK_REPLY_TIMEOUT_DEFAULT = SmackConfiguration.getDefaultReplyTimeout().toLong()
        const val DEFAULT_PORT = 5222

        /**
         * XMPP signaling DSCP configuration property name.
         */
        private const val XMPP_DSCP_PROPERTY = "protocol.jabber.XMPP_DSCP"

        /**
         * Indicates if user search is disabled.
         */
        private const val IS_USER_SEARCH_ENABLED_PROPERTY = "USER_SEARCH_ENABLED"
        private const val DEFAULT_RESOURCE = "atalk"

        /**
         * Map reference of Dialog listenerId to authRequest Id
         */
        private val mAuthIds = HashMap<Long, String>()

        /**
         * XEP-0199: XMPP Ping
         * The default ping interval in seconds used by PingManager. The Smack default is 30 minutes.
         * See [.initSmackDefaultSettings]
         */
        var defaultPingInterval = 240 // 4 minutes
        var defaultMinimumTLSversion = TLSUtils.PROTO_TLSV1_2

        // load xmpp manager classes
        init {
            if (OSUtils.IS_ANDROID) loadJabberServiceClasses()
        }

        /**
         * Enable or disable MAM service according per the give enable setting.
         *
         * @param connection XMPPConnection to act upon
         * @param enable set MAM service per the given value
         */
        fun enableMam(connection: XMPPConnection?, enable: Boolean) {
            val mamManager = MamManager.getInstanceFor(connection, null)
            try {
                if (mamManager.isSupported) {
                    if (enable) {
                        mamManager.enableMamForAllMessages()
                    }
                    else {
                        mamManager.setDefaultBehavior(MamPrefsIQ.DefaultBehavior.never)
                    }
                }
            } catch (e: NoResponseException) {
                Timber.e("Enable Mam For All Messages: %s", e.message)
            } catch (e: XMPPErrorException) {
                Timber.e("Enable Mam For All Messages: %s", e.message)
            } catch (e: NotConnectedException) {
                Timber.e("Enable Mam For All Messages: %s", e.message)
            } catch (e: NotLoggedInException) {
                Timber.e("Enable Mam For All Messages: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.e("Enable Mam For All Messages: %s", e.message)
            }
        }

        /**
         * Setup a common avatarStoreDirectory store for all accounts during Smack initialization
         * state to support server avatar info persistent storage
         */
        fun initAvatarStore() {
            /* Persistent Storage directory for Avatar. */
            val avatarStoreDirectory = File(aTalkApp.globalContext.filesDir, "/avatarStore")

            // Store in memory cache by default, and in persistent store if not null
            VCardAvatarManager.setPersistentCache(avatarStoreDirectory)
            UserAvatarManager.setPersistentCache(avatarStoreDirectory)
        }

        /**
         * Logs a specific message and associated `Throwable` cause as an error using the
         * current `Logger` and then throws a new `OperationFailedException` with the
         * message, a specific error code and the cause.
         *
         * @param message the message to be logged and then wrapped in a new `OperationFailedException`
         * @param errorCode the error code to be assigned to the new `OperationFailedException`
         * @param cause the `Throwable` that has caused the necessity to log an error and have a new
         * `OperationFailedException` thrown
         *
         * @throws OperationFailedException the exception that we wanted this method to throw.
         */
        @Throws(OperationFailedException::class)
        fun throwOperationFailedException(message: String?, errorCode: Int, cause: Throwable?) {
            Timber.e(cause, "%s", message)
            if (cause == null) throw OperationFailedException(message, errorCode)
            else throw OperationFailedException(message, errorCode, cause)
        }

        /**
         * Returns true if our account is a Gmail or a Google Apps ones.
         *
         * @param domain domain to check
         *
         * @return true if our account is a Gmail or a Google Apps ones.
         */
        fun isGmailOrGoogleAppsAccount(domain: String?): Boolean {
            try {
                val srvRecords = getSRVRecords("xmpp-client", "tcp", domain!!)
                        ?: return false
                for (srv in srvRecords) {
                    if (srv.target.toString().contains("google.com")) {
                        return true
                    }
                }
            } catch (e: IOException) {
                Timber.e("Failed when checking for google account: %s", e.message)
            }
            return false
        }

        /**
         * Load jabber service class, their static context will register what is needed. Used in
         * android as when using the other jars these services are loaded from the jar manifest.
         */
        private fun loadJabberServiceClasses() {
            try {
                // pre-configure smack in android just to load class to init their static blocks
                Timber.d("Smack version: %s", Smack.getVersion())
                Class.forName(ServiceDiscoveryManager::class.java.name)
                Class.forName(DelayInformation::class.java.name)
                Class.forName(DelayInformationProvider::class.java.name)
                Class.forName(Socks5BytestreamManager::class.java.name)
                Class.forName(XHTMLManager::class.java.name)
                Class.forName(InBandBytestreamManager::class.java.name)
            } catch (e: ClassNotFoundException) {
                Timber.e("Error loading classes in smack: %s", e.message)
            }
        }
    }
}