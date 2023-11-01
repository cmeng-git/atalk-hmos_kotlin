/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.crypto.otr

import android.content.Intent
import android.os.Bundle
import android.view.View
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.plugin.otr.OtrContactManager
import net.java.sip.communicator.plugin.otr.ScOtrEngineImpl
import net.java.sip.communicator.plugin.otr.ScOtrKeyManager
import net.java.sip.communicator.plugin.otr.ScSessionID
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.IMessage
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.util.ViewUtil.setCompoundChecked
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.CryptoHelper
import java.security.PublicKey
import java.util.*

/**
 * OTR buddy authenticate dialog. Takes OTR session's UUID as an extra.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class OtrAuthenticateDialog : OSGiActivity() {
    /**
     * The `Contact` that belongs to OTR session handled by this instance.
     */
    private var otrContact: OtrContactManager.OtrContact? = null
    private var remoteFingerprint: String? = null
    private val keyManager = OtrActivator.scOtrKeyManager

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.otr_authenticate_dialog)
        setTitle(R.string.plugin_otr_authbuddydialog_TITLE)
        val guid = intent.getSerializableExtra(EXTRA_SESSION_UUID) as UUID
        val sessionID = ScOtrEngineImpl.getScSessionForGuid(guid)
        otrContact = ScOtrEngineImpl.getOtrContact(sessionID!!.sessionID)
        val contact = otrContact!!.contact

        // Local fingerprint.
        val account = contact!!.protocolProvider.accountID.displayName
        val localFingerprint = keyManager.getLocalFingerprint(contact.protocolProvider.accountID)
        val content = findViewById<View>(android.R.id.content)
        setTextViewValue(content, R.id.localFingerprintLbl,
                getString(R.string.plugin_otr_authbuddydialog_LOCAL_FINGERPRINT, account,
                        CryptoHelper.prettifyFingerprint(localFingerprint)))

        // Remote fingerprint.
        val user = contact.displayName
        val pubKey = OtrActivator.scOtrEngine.getRemotePublicKey(otrContact)
        remoteFingerprint = keyManager.getFingerprintFromPublicKey(pubKey)
        setTextViewValue(content, R.id.remoteFingerprintLbl,
                getString(R.string.plugin_otr_authbuddydialog_REMOTE_FINGERPRINT, user,
                        CryptoHelper.prettifyFingerprint(remoteFingerprint)))
        // Action
        setTextViewValue(content, R.id.actionTextView,
                getString(R.string.plugin_otr_authbuddydialog_VERIFY_ACTION, user))

        // Verify button
        setCompoundChecked(getContentView(), R.id.verifyButton,
                keyManager.isVerified(contact, remoteFingerprint))
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v ok button's `View`.
     */
    fun onOkClicked(v: View?) {
        if (ViewUtil.isCompoundChecked(getContentView(), R.id.verifyButton)) {
            keyManager.verify(otrContact, remoteFingerprint)
            val contact = otrContact!!.contact
            val resourceName = if (otrContact!!.resource != null) "/" + otrContact!!.resource!!.resourceName else ""
            val sender = contact!!.displayName
            val message = getString(R.string.plugin_otr_activator_sessionstared, sender + resourceName)
            OtrActivator.uiService.getChat(contact)!!.addMessage(sender, Date(), ChatMessage.MESSAGE_SYSTEM,
                    IMessage.ENCODE_HTML, message)
        } else {
            keyManager.unverify(otrContact, remoteFingerprint)
        }
        finish()
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the cancel button's `View`
     */
    fun onCancelClicked(v: View?) {
        finish()
    }

    companion object {
        /**
         * Key name for OTR session's UUID.
         */
        private const val EXTRA_SESSION_UUID = "uuid"

        /**
         * Creates parametrized `Intent` of buddy authenticate dialog.
         *
         * @param uuid the UUID of OTR session.
         * @return buddy authenticate dialog parametrized with given OTR session's UUID.
         */
        fun createIntent(uuid: UUID?): Intent {
            val intent = Intent(aTalkApp.globalContext, OtrAuthenticateDialog::class.java)
            intent.putExtra(EXTRA_SESSION_UUID, uuid)

            // Started not from Activity
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }
}