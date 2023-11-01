/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr

import net.java.otr4j.OtrEngineHost
import net.java.otr4j.OtrEngineListener
import net.java.otr4j.OtrException
import net.java.otr4j.OtrPolicy
import net.java.otr4j.OtrPolicyImpl
import net.java.otr4j.OtrSessionManager
import net.java.otr4j.OtrSessionManagerImpl
import net.java.otr4j.crypto.OtrCryptoEngineImpl
import net.java.otr4j.crypto.OtrCryptoException
import net.java.otr4j.session.FragmenterInstructions
import net.java.otr4j.session.InstanceTag
import net.java.otr4j.session.Session
import net.java.otr4j.session.SessionID
import net.java.otr4j.session.SessionStatus
import net.java.sip.communicator.plugin.otr.OtrContactManager.*
import net.java.sip.communicator.plugin.otr.authdialog.SmpAuthenticateBuddyDialog
import net.java.sip.communicator.plugin.otr.authdialog.SmpProgressDialog
import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService
import net.java.sip.communicator.service.gui.ChatLinkClickedListener
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessagingTransport
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatMessage
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact as OtrContact1

/**
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Marin Dzhigarov
 * @author Danny van Heumen
 * @author Eng Chong Meng
 */
class ScOtrEngineImpl : ScOtrEngine, ChatLinkClickedListener, ServiceListener {
    private val configurator = OtrConfigurator()
    private val injectedMessageUIDs = Vector<String>()
    private val listeners = Vector<ScOtrEngineListener>()
    private val otrEngineHost = ScOtrEngineHost()
    private val otrEngine: OtrSessionManager

    /**
     * Manages the scheduling of TimerTasks that are used to set Contact's ScSessionStatus
     * (to TIMED_OUT) after a period of time.
     */
    private val scheduler = ScSessionStatusScheduler()

    /**
     * This mapping is used for taking care of keeping SessionStatus and ScSessionStatus in sync
     * for every Session object.
     */
    private val scSessionStatusMap = ConcurrentHashMap<SessionID?, ScSessionStatus?>()

    init {
        otrEngine = OtrSessionManagerImpl(otrEngineHost)

        // Clears the map after previous instance
        // This is required because of OSGi restarts in the same VM on Android
        contactsMap.clear()
        scSessionStatusMap.clear()
        otrEngine.addOtrEngineListener(object : OtrEngineListener {
            override fun sessionStatusChanged(sessionID: SessionID?) {
                val otrContact = getOtrContact(sessionID) ?: return
                val resourceName = if (otrContact.resource != null) "/" + otrContact.resource.resourceName else ""
                val contact = otrContact.contact
                val sender = contact!!.address

                // Cancel any scheduled tasks that will change the ScSessionStatus for this Contact
                scheduler.cancel(otrContact)
                var message = ""
                val scSessionStatus: ScSessionStatus
                val session = otrEngine.getSession(sessionID)
                when (session!!.sessionStatus) {
                    SessionStatus.ENCRYPTED -> {
                        scSessionStatus = ScSessionStatus.ENCRYPTED
                        scSessionStatusMap[sessionID] = scSessionStatus
                        val remotePubKey = session.remotePublicKey
                        var remoteFingerprint: String? = null
                        try {
                            remoteFingerprint = OtrCryptoEngineImpl().getFingerprint(remotePubKey)
                        } catch (e: OtrCryptoException) {
                            Timber.d("Could not get the fingerprint from the public key for: %s", contact)
                        }
                        val allFingerprintsOfContact = OtrActivator.scOtrKeyManager.getAllRemoteFingerprints(contact)
                        if (allFingerprintsOfContact != null) {
                            if (!allFingerprintsOfContact.contains(remoteFingerprint)) {
                                OtrActivator.scOtrKeyManager.saveFingerprint(contact, remoteFingerprint)
                            }
                        }
                        if (!OtrActivator.scOtrKeyManager.isVerified(contact, remoteFingerprint)) {
                            OtrActivator.scOtrKeyManager.unverify(otrContact, remoteFingerprint)
                            var sessionGuid: UUID? = null
                            for (scSessionID in contactsMap.keys) {
                                if (scSessionID.sessionID!! == sessionID) {
                                    sessionGuid = scSessionID.uuid
                                    break
                                }
                            }
                            OtrActivator.uiService.getChat(contact)!!.addChatLinkClickedListener(this@ScOtrEngineImpl)
                            val unverifiedSessionWarning = aTalkApp.getResString(
                                    R.string.plugin_otr_activator_unverifiedsessionwarning, sender + resourceName,
                                    this.javaClass.name, "AUTHENTIFICATION", sessionGuid.toString())
                            OtrActivator.uiService.getChat(contact)!!.addMessage(sender, Date(),
                                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, unverifiedSessionWarning)
                        }

                        // show info whether history is on or off
                        val otrAndHistoryMessage = if (!OtrActivator.messageHistoryService!!.isHistoryLoggingEnabled
                                || !isHistoryLoggingEnabled(contact)) {
                            aTalkApp.getResString(R.string.plugin_otr_activator_historyoff,
                                    aTalkApp.getResString(R.string.APPLICATION_NAME),
                                    this.javaClass.name, "showHistoryPopupMenu")
                        } else {
                            aTalkApp.getResString(R.string.plugin_otr_activator_historyon,
                                    aTalkApp.getResString(R.string.APPLICATION_NAME),
                                    this.javaClass.name, "showHistoryPopupMenu")
                        }
                        OtrActivator.uiService.getChat(contact)!!.addMessage(sender, Date(),
                                ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, otrAndHistoryMessage)

                        // show info on OTR session status
                        message = aTalkApp.getResString(R.string.plugin_otr_activator_multipleinstancesdetected, sender)
                        if (contact.isSupportResources && contact.getResources() != null && contact.getResources()!!.size > 1) {
                            OtrActivator.uiService.getChat(contact)!!.addMessage(sender, Date(),
                                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message)
                        }
                        message = aTalkApp.getResString(if (OtrActivator.scOtrKeyManager.isVerified(contact, remoteFingerprint)) R.string.plugin_otr_activator_sessionstared else R.string.plugin_otr_activator_unverifiedsessionstared, sender + resourceName)
                    }
                    SessionStatus.FINISHED -> {
                        scSessionStatus = ScSessionStatus.FINISHED
                        scSessionStatusMap[sessionID] = scSessionStatus
                        message = aTalkApp.getResString(R.string.plugin_otr_activator_sessionfinished,
                                sender + resourceName)
                    }
                    SessionStatus.PLAINTEXT -> {
                        scSessionStatus = ScSessionStatus.PLAINTEXT
                        scSessionStatusMap[sessionID] = scSessionStatus
                        message = aTalkApp.getResString(R.string.plugin_otr_activator_sessionlost, sender + resourceName)
                    }
                }
                OtrActivator.uiService.getChat(contact)!!.addMessage(sender, Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message)
                for (l in getListeners()) l.sessionStatusChanged(otrContact)
            }

            override fun multipleInstancesDetected(sessionID: SessionID?) {
                val otrContact = getOtrContact(sessionID) ?: return
                for (l in getListeners()) l.multipleInstancesDetected(otrContact)
            }

            override fun outgoingSessionChanged(sessionID: SessionID?) {
                val otrContact = getOtrContact(sessionID) ?: return
                for (l in getListeners()) l.outgoingSessionChanged(otrContact)
            }
        })
    }

    /**
     * Checks whether history is enabled for the metaContact containing the `contact`.
     *
     * @param contact the contact to check.
     * @return whether chat logging is enabled while chatting with `contact`.
     */
    private fun isHistoryLoggingEnabled(contact: Contact): Boolean {
        val metaContact = OtrActivator.contactListService!!.findMetaContactByContact(contact)
        return if (metaContact != null) OtrActivator.messageHistoryService!!.isHistoryLoggingEnabled(metaContact.getMetaUID()) else true
    }

    override fun addListener(listener: ScOtrEngineListener) {
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
    }

    override fun chatLinkClicked(url: URI) {
        val action = url.path
        if (action == "/AUTHENTIFICATION") {
            val guid = UUID.fromString(url.query)
                    ?: throw RuntimeException("No UUID found in OTR authenticate URL")

            // Looks for registered action handler
            val actionHandler = ServiceUtils.getService(OtrActivator.bundleContext, OtrActionHandler::class.java)
            if (actionHandler != null) {
                actionHandler.onAuthenticateLinkClicked(guid)
            } else {
                Timber.e("No OtrActionHandler registered")
            }
        }
    }

    override fun endSession(contact: OtrContact1) {
        val sessionID = getSessionID(contact)
        try {
            setSessionStatus(contact, ScSessionStatus.PLAINTEXT)
            otrEngine.getSession(sessionID)!!.endSession()
        } catch (e: OtrException) {
            showError(sessionID, e.message!!)
        }
    }

    override fun getContactPolicy(contact: Contact): OtrPolicy {
        val pps = contact.protocolProvider
        val sessionID = SessionID(pps.accountID.accountUniqueID, contact.address, pps.protocolName)
        val policy = configurator.getPropertyInt(sessionID.toString() + CONTACT_POLICY, -1)
        return if (policy < 0) globalPolicy!! else OtrPolicyImpl(policy)
    }

    /*
         * SEND_WHITESPACE_TAG bit will be lowered until we stabilize the OTR.
         */
    override var globalPolicy: OtrPolicy?
        get() {
            /*
         * SEND_WHITESPACE_TAG bit will be lowered until we stabilize the OTR.
         */
            val defaultScOtrPolicy = OtrPolicy.OTRL_POLICY_DEFAULT and OtrPolicy.SEND_WHITESPACE_TAG.inv()
            return OtrPolicyImpl(configurator.getPropertyInt(GLOBAL_POLICY, defaultScOtrPolicy))
        }
        set(policy) {
            if (policy == null) configurator.removeProperty(GLOBAL_POLICY) else configurator.setProperty(GLOBAL_POLICY, policy.policy)
            for (l in getListeners()) l.globalPolicyChanged()
        }

    /**
     * Gets a copy of the list of `ScOtrEngineListener`s registered with this instance
     * which may safely be iterated without the risk of a `ConcurrentModificationException`.
     *
     * @return a copy of the list of `ScOtrEngineListener`s registered with this instance
     * which may safely be iterated without the risk of a `ConcurrentModificationException`
    `` */
    private fun getListeners(): Array<ScOtrEngineListener> {
        synchronized(listeners) { return listeners.toTypedArray() }
    }

    private fun setSessionStatus(otrContact: OtrContact1, status: ScSessionStatus) {
        scSessionStatusMap[getSessionID(otrContact)] = status
        scheduler.cancel(otrContact)
        for (l in getListeners()) l.sessionStatusChanged(otrContact)
    }

    override fun getSessionStatus(contact: OtrContact1?): ScSessionStatus? {
        val sessionID = getSessionID(contact)
        val sessionStatus = otrEngine.getSession(sessionID)!!.sessionStatus
        val scSessionStatus: ScSessionStatus?
        if (!scSessionStatusMap.containsKey(sessionID)) {
            scSessionStatus = when (sessionStatus) {
                SessionStatus.PLAINTEXT -> ScSessionStatus.PLAINTEXT
                SessionStatus.ENCRYPTED -> ScSessionStatus.ENCRYPTED
                SessionStatus.FINISHED -> ScSessionStatus.FINISHED
            }
            scSessionStatusMap[sessionID] = scSessionStatus
        }
        return scSessionStatusMap[sessionID]
    }

    override fun isMessageUIDInjected(messageUID: String): Boolean {
        return injectedMessageUIDs.contains(messageUID)
    }

    override fun launchHelp() {
        val ref = OtrActivator.bundleContext.getServiceReference(BrowserLauncherService::class.java.name)
                ?: return
        val service = OtrActivator.bundleContext.getService<Any>(ref as ServiceReference<Any>) as BrowserLauncherService
        service.openURL(aTalkApp.getResString(R.string.plugin_otr_authbuddydialog_HELP_URI))
    }

    override fun refreshSession(contact: OtrContact1?) {
        val sessionID = getSessionID(contact)
        try {
            otrEngine.getSession(sessionID)!!.refreshSession()
        } catch (e: OtrException) {
            Timber.e(e, "Error refreshing session")
            showError(sessionID, e.message!!)
        }
    }

    override fun removeListener(listener: ScOtrEngineListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    /**
     * Cleans the contactsMap when `ProtocolProviderService` gets unregistered.
     */
    override fun serviceChanged(ev: ServiceEvent) {
        val provider = OtrActivator.bundleContext.getService(ev.serviceReference) ?: return

        if (ev.type == ServiceEvent.UNREGISTERING) {
            Timber.d("Unregister a PPS, cleaning OTR's ScSessionID (Contact map); and Contact (SpmProgressDialog map).")
            synchronized(contactsMap) {
                val i = contactsMap.values.iterator()
                while (i.hasNext()) {
                    val otrContact = i.next()
                    if (provider == otrContact!!.contact!!.protocolProvider) {
                        scSessionStatusMap.remove(getSessionID(otrContact))
                        i.remove()
                    }
                }
            }
            val i = progressDialogMap.keys.iterator()
            while (i.hasNext()) {
                if (provider == i.next().contact!!.protocolProvider) i.remove()
            }
            scheduler.serviceChanged(ev)
        }
    }

    override fun setContactPolicy(contact: Contact, policy: OtrPolicy?) {
        val pps = contact.protocolProvider
        val sessionID = SessionID(pps.accountID.accountUniqueID,
                contact.address, pps.protocolName)
        val propertyID = sessionID.toString() + CONTACT_POLICY
        if (policy == null) configurator.removeProperty(propertyID) else configurator.setProperty(propertyID, policy.policy)
        for (l in getListeners()) l.contactPolicyChanged(contact)
    }

    fun showError(sessionID: SessionID?, err: String) {
        val otrContact = getOtrContact(sessionID) ?: return
        val contact = otrContact.contact
        OtrActivator.uiService.getChat(contact!!)!!.addMessage(contact.address, Date(),
                ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, err)
    }

    override fun startSession(contact: OtrContact1) {
        val sessionID = getSessionID(contact)
        val scSessionStatus = ScSessionStatus.LOADING
        scSessionStatusMap[sessionID] = scSessionStatus
        for (l in getListeners()) {
            l.sessionStatusChanged(contact)
        }
        scheduler.scheduleScSessionStatusChange(contact, ScSessionStatus.TIMED_OUT)
        try {
            otrEngine.getSession(sessionID)!!.startSession()
        } catch (e: OtrException) {
            Timber.e(e, "Error starting session")
            showError(sessionID, e.message!!)
        }
    }

    override fun transformReceiving(contact: OtrContact1?, content: String?): String? {
        val sessionID = getSessionID(contact)
        return try {
            otrEngine.getSession(sessionID)!!.transformReceiving(content)
        } catch (e: OtrException) {
            Timber.e(e, "Error receiving the message")
            showError(sessionID, e.message!!)
            null
        }
    }

    override fun transformSending(contact: OtrContact1?, content: String?): Array<String>? {
        val sessionID = getSessionID(contact)
        return try {
            otrEngine.getSession(sessionID)!!.transformSending(content!!)
        } catch (e: OtrException) {
            Timber.e(e, "Error transforming the message")
            showError(sessionID, e.message!!)
            null
        }
    }

    private fun getSession(otrContact: OtrContact1?): Session? {
        val sessionID = getSessionID(otrContact)
        return otrEngine.getSession(sessionID)
    }

    override fun initSmp(contact: OtrContact1, question: String?, secret: String?) {
        val session = getSession(contact)
        try {
            session!!.initSmp(question, secret!!)
            var progressDialog = progressDialogMap[contact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact.contact!!)
                progressDialogMap[contact] = progressDialog
            }
            progressDialog.init()
            progressDialog.setVisible(true)
        } catch (e: OtrException) {
            Timber.e(e, "Error initializing SMP session with contact %s", contact.contact!!.displayName)
            showError(session!!.sessionID, e.message!!)
        }
    }

override fun respondSmp(contact: OtrContact1, receiverTag: InstanceTag?, question: String?, secret: String?) {
        val session = getSession(contact)
        try {
            session!!.respondSmp(receiverTag!!, question, secret!!)
            var progressDialog = progressDialogMap[contact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact.contact!!)
                progressDialogMap[contact] = progressDialog
            }
            progressDialog.incrementProgress()
            progressDialog.setVisible(true)
        } catch (e: OtrException) {
            Timber.e(e, "Error occurred when sending SMP response to contact %s", contact.contact!!.displayName)
            showError(session!!.sessionID, e.message!!)
        }
    }

    override fun abortSmp(contact: OtrContact1) {
        val session = getSession(contact)
        try {
            session!!.abortSmp()
            var progressDialog = progressDialogMap[contact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact.contact!!)
                progressDialogMap[contact] = progressDialog
            }
            progressDialog.dispose()
        } catch (e: OtrException) {
            Timber.e(e, "Error aborting SMP session with contact %s", contact.contact!!.displayName)
            showError(session!!.sessionID, e.message!!)
        }
    }

    override fun getRemotePublicKey(otrContact: OtrContact1?): PublicKey? {
        if (otrContact == null) return null
        val session = getSession(otrContact)
        return session!!.remotePublicKey
    }

    override fun getSessionInstances(contact: OtrContact1?): List<Session> {
        return if (contact == null) emptyList() else getSession(contact)!!.instances
    }

    override fun setOutgoingSession(contact: OtrContact1?, tag: InstanceTag?): Boolean {
        if (contact == null) return false
        val session = getSession(contact)
        scSessionStatusMap.remove(session!!.sessionID)
        return session.setOutgoingInstance(tag!!)
    }

    override fun getOutgoingSession(contact: OtrContact1?): Session? {
        if (contact == null) return null
        val sessionID = getSessionID(contact)
        return otrEngine.getSession(sessionID)!!.outgoingInstance
    }

    private inner class ScOtrEngineHost : OtrEngineHost {
        override fun getLocalKeyPair(sessionID: SessionID?): KeyPair? {
            val accountID = OtrActivator.getAccountIDByUID(sessionID!!.accountID)
            val keyPair = OtrActivator.scOtrKeyManager.loadKeyPair(accountID)
            if (keyPair == null) OtrActivator.scOtrKeyManager.generateKeyPair(accountID)
            return OtrActivator.scOtrKeyManager.loadKeyPair(accountID)
        }

        override fun getSessionPolicy(sessionID: SessionID?): OtrPolicy {
            return getContactPolicy(getOtrContact(sessionID)!!.contact!!)
        }

        override fun injectMessage(sessionID: SessionID?, msg: String?) {
            val otrContact = getOtrContact(sessionID)
            val contact = otrContact!!.contact
            val resource = otrContact.resource

            // Following may return null even resource name is the same
//			ContactResource resource = null;
//			if (contact.supportResources()) {
//				Collection<ContactResource> resources = contact.getResources();
//				if (resources != null) {
//					for (ContactResource r : resources) {
//						if (r.equals(otrContact.resource)) {
//							resource = r;
//							break;
//						}
//					}
//				}
//			}

            val imOpSet = contact!!.protocolProvider.getOperationSet(OperationSetBasicInstantMessaging::class.java)

            // This is a dirty way of detecting whether the injected message contains HTML markup.
            // If this is the case then we should create the message with the appropriate content
            // type so that the remote party can properly display the HTML. When otr4j injects
            // QueryMessages it calls OtrEngineHost.getFallbackMessage() which is currently the
            // only host method that uses HTML so we can simply check if the injected message
            // contains the string that getFallbackMessage() returns.
            val otrHtmlFallbackMessage = "<a href=\"https://en.wikipedia.org/wiki/Off-the-Record_Messaging\">"
            val mimeType = if (msg!!.contains(otrHtmlFallbackMessage)) IMessage.ENCODE_HTML else IMessage.ENCODE_PLAIN
            val message = imOpSet!!.createMessage(msg, mimeType, null)
            injectedMessageUIDs.add(message.getMessageUID())
            imOpSet.sendInstantMessage(contact, resource, message)
        }

        override fun showError(sessionID: SessionID?, error: String?) {
            this@ScOtrEngineImpl.showError(sessionID, error!!)
        }

        override fun showAlert(sessionID: SessionID?, error: String?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact!!
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                    Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN, error!!)
        }

        @Throws(OtrException::class)
        override fun unreadableMessageReceived(sessionID: SessionID?) {
            val otrContact = getOtrContact(sessionID)
            val resourceName = if (otrContact!!.resource != null) "/" + otrContact.resource!!.resourceName else ""
            val contact = otrContact.contact
            val error = aTalkApp.getResString(R.string.plugin_otr_activator_unreadablemsgreceived,
                    contact!!.displayName + resourceName)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address, Date(),
                    ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error)
        }

        @Throws(OtrException::class)
        override fun unencryptedMessageReceived(sessionID: SessionID?, msg: String?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact!!
            val warn = aTalkApp.getResString(R.string.plugin_otr_activator_unencryptedmsgreceived)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address, Date(),
                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN, warn)
        }

        @Throws(OtrException::class)
        override fun smpError(sessionID: SessionID?, tlvType: Int, cheated: Boolean) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact
            Timber.d("SMP error occurred. Contact: %s. TLV type: %s. Cheated: %s",
                    contact!!.displayName, tlvType, cheated)
            val error = aTalkApp.getResString(R.string.plugin_otr_activator_smperror)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                    Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error)
            var progressDialog = progressDialogMap[otrContact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact)
                progressDialogMap[otrContact] = progressDialog
            }
            progressDialog.setProgressFail()
            progressDialog.setVisible(true)
        }

        @Throws(OtrException::class)
        override fun smpAborted(sessionID: SessionID?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact
            val session = otrEngine.getSession(sessionID)
            if (session!!.isSmpInProgress) {
                val warn = aTalkApp.getResString(R.string.plugin_otr_activator_smpaborted, contact!!.displayName)
                OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                        Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN, warn)
                var progressDialog = progressDialogMap[otrContact]
                if (progressDialog == null) {
                    progressDialog = SmpProgressDialog(contact)
                    progressDialogMap[otrContact] = progressDialog
                }
                progressDialog.setProgressFail()
                progressDialog.setVisible(true)
            }
        }

        @Throws(OtrException::class)
        override fun finishedSessionMessage(sessionID: SessionID?, msgText: String?) {
            val otrContact = getOtrContact(sessionID) ?: return

//			String resourceName = otrContact.resource != null ? "/" + otrContact.resource.getResourceName() : "";
            val contact = otrContact.contact
            val error = aTalkApp.getResString(R.string.plugin_otr_activator_sessionfinishederror,
                    contact!!.displayName)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                    Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error)
        }

        @Throws(OtrException::class)
        override fun requireEncryptedMessage(sessionID: SessionID?, msgText: String?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact!!
            val error = aTalkApp.getResString(R.string.plugin_otr_activator_requireencryption, msgText)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                    Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error)
        }

        override fun getLocalFingerprintRaw(sessionID: SessionID?): ByteArray? {
            val accountID = OtrActivator.getAccountIDByUID(sessionID!!.accountID)
            return OtrActivator.scOtrKeyManager.getLocalFingerprintRaw(accountID)
        }

        override fun askForSecret(sessionID: SessionID, receiverTag: InstanceTag, question: String?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact
            val dialog = SmpAuthenticateBuddyDialog(otrContact, receiverTag, question)
            dialog.setVisible(true)
            var progressDialog = progressDialogMap[otrContact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact!!)
                progressDialogMap[otrContact] = progressDialog
            }
            progressDialog.init()
            progressDialog.setVisible(true)
        }

        override fun verify(sessionID: SessionID?, fingerprint: String?, approved: Boolean) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact
            OtrActivator.scOtrKeyManager.verify(otrContact, fingerprint)
            var progressDialog = progressDialogMap[otrContact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact!!)
                progressDialogMap[otrContact] = progressDialog
            }
            progressDialog.setProgressSuccess()
            progressDialog.setVisible(true)
        }

        override fun unverify(sessionID: SessionID?, fingerprint: String?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val contact = otrContact.contact
            OtrActivator.scOtrKeyManager.unverify(otrContact, fingerprint)
            var progressDialog: SmpProgressDialog? = progressDialogMap[otrContact]
            if (progressDialog == null) {
                progressDialog = SmpProgressDialog(contact!!)
                progressDialogMap[otrContact] = progressDialog
            }
            progressDialog.setProgressFail()
            progressDialog.setVisible(true)
        }

        override fun getReplyForUnreadableMessage(sessionID: SessionID?): String {
            val accountID = OtrActivator.getAccountIDByUID(sessionID!!.accountID)
            return aTalkApp.getResString(R.string.plugin_otr_activator_unreadablemsgreply,
                    accountID!!.displayName, accountID.displayName)
        }

        override fun getFallbackMessage(sessionID: SessionID?): String {
            val accountID = OtrActivator.getAccountIDByUID(sessionID!!.accountID)
            return aTalkApp.getResString(R.string.plugin_otr_activator_fallbackmessage,
                    accountID!!.displayName)
        }

        override fun multipleInstancesDetected(sessionID: SessionID?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val resourceName = if (otrContact.resource != null) "/" + otrContact.resource.resourceName else ""
            val contact = otrContact.contact
            val message = aTalkApp.getResString(R.string.plugin_otr_activator_multipleinstancesdetected,
                    contact!!.displayName + resourceName)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                    Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message)
        }

        override fun messageFromAnotherInstanceReceived(sessionID: SessionID?) {
            val otrContact = getOtrContact(sessionID) ?: return
            val resourceName = if (otrContact.resource != null) "/" + otrContact.resource.resourceName else ""
            val contact = otrContact.contact
            val message = aTalkApp.getResString(R.string.plugin_otr_activator_msgfromanotherinstance,
                    contact!!.displayName + resourceName)
            OtrActivator.uiService.getChat(contact)!!.addMessage(contact.address,
                    Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message)
        }

        /**
         * Provide fragmenter instructions according to the Instant Messaging transport channel of
         * the contact's protocol.
         */
        override fun getFragmenterInstructions(sessionID: SessionID?): FragmenterInstructions? {
            val otrContact = getOtrContact(sessionID)
            val transport = otrContact!!.contact!!.protocolProvider.getOperationSet(
                    OperationSetBasicInstantMessagingTransport::class.java)
            if (transport == null) {
                // There is no operation set for querying transport parameters.
                // Assuming transport capabilities are unlimited.
                Timber.d("No implementation of BasicInstantMessagingTransport available. Assuming OTR defaults for OTR fragmentation instructions.")
                return null
            }
            var messageSize = transport.getMaxMessageSize(otrContact.contact)
            if (messageSize == OperationSetBasicInstantMessagingTransport.UNLIMITED) {
                messageSize = FragmenterInstructions.UNLIMITED
            }
            var numberOfMessages = transport.getMaxNumberOfMessages(otrContact.contact)
            if (numberOfMessages == OperationSetBasicInstantMessagingTransport.UNLIMITED) {
                numberOfMessages = FragmenterInstructions.UNLIMITED
            }
            Timber.d("OTR fragmentation instructions for sending a message to %s (%s). Max messages no: %s, Max message size: %s",
                    otrContact.contact!!.displayName, otrContact.contact.address, numberOfMessages, messageSize)
            return FragmenterInstructions(numberOfMessages, messageSize)
        }
    }

    /**
     * Manages the scheduling of TimerTasks that are used to set Contact's ScSessionStatus after a
     * period of time.
     *
     * @author Marin Dzhigarov
     */
    private inner class ScSessionStatusScheduler {
        private val timer = Timer()
        private val tasks: MutableMap<OtrContact1, TimerTask> = ConcurrentHashMap<OtrContact1, TimerTask>()
        fun scheduleScSessionStatusChange(otrContact: OtrContact1, status: ScSessionStatus) {
            cancel(otrContact)
            val task = object : TimerTask() {
                override fun run() {
                    setSessionStatus(otrContact, status)
                }
            }
            timer.schedule(task, SESSION_TIMEOUT.toLong())
            tasks[otrContact] = task
        }

        fun cancel(otrContact: OtrContact1) {
            val task = tasks[otrContact]
            task?.cancel()
            tasks.remove(otrContact)
        }

        fun serviceChanged(ev: ServiceEvent) {
            val service = OtrActivator.bundleContext.getService(ev.serviceReference) as? ProtocolProviderService
                    ?: return
            if (ev.type == ServiceEvent.UNREGISTERING) {
                val i: MutableIterator<OtrContact1> = tasks.keys.iterator()
                while (i.hasNext()) {
                    val otrContact: OtrContact1 = i.next()
                    if (service == otrContact.contact!!.protocolProvider) {
                        cancel(otrContact)
                        i.remove()
                    }
                }
            }
        }
    }

    companion object {
        private const val CONTACT_POLICY = ".contact_policy"
        private const val GLOBAL_POLICY = "GLOBAL_POLICY"

        /**
         * The max timeout period elapsed prior to establishing a TIMED_OUT session.
         */
        private val SESSION_TIMEOUT = OtrActivator.configService.getInt("otr.SESSION_STATUS_TIMEOUT", 30000)
        private val contactsMap = Hashtable<ScSessionID, OtrContact1?>()
        private val progressDialogMap = ConcurrentHashMap<OtrContact1, SmpProgressDialog?>()

        fun getOtrContact(sessionID: SessionID?): OtrContact1? {
            return contactsMap[ScSessionID(sessionID)]
        }

        /**
         * Returns the `ScSessionID` for given `UUID`.
         *
         * @param guid the `UUID` identifying `ScSessionID`.
         * @return the `ScSessionID` for given `UUID` or `null` if no matching session found.
         */
        fun getScSessionForGuid(guid: UUID): ScSessionID? {
            for (scSessionID in contactsMap.keys) {
                if (scSessionID.uuid == guid) {
                    return scSessionID
                }
            }
            return null
        }

        fun getSessionID(otrContact: OtrContact1?): SessionID {
            val pps = otrContact!!.contact!!.protocolProvider
            val resourceName = if (otrContact.resource != null) "/" + otrContact.resource.resourceName else ""
            val sessionID = SessionID(pps.accountID.accountUniqueID,
                    otrContact.contact!!.address + resourceName, pps.protocolName)
            synchronized(contactsMap) {
                if (contactsMap.containsKey(ScSessionID(sessionID))) return sessionID
                val scSessionID = ScSessionID(sessionID)
                contactsMap.put(scSessionID, otrContact)
            }
            return sessionID
        }
    }
}