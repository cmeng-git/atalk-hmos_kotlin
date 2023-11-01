/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr.authdialog

import net.java.sip.communicator.plugin.desktoputil.SIPCommTextField
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.plugin.otr.OtrContactManager
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.util.swing.TransparentPanel
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.security.PublicKey
import java.util.*
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * @author George Politis
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class FingerprintAuthenticationPanel internal constructor(contact: OtrContactManager.OtrContact) : TransparentPanel(), DocumentListener {
    /**
     * The Contact that we are authenticating.
     */
    private val otrContact: OtrContactManager.OtrContact
    private var txtRemoteFingerprintComparison: SIPCommTextField? = null

    /**
     * Our fingerprint.
     */
    private var txtLocalFingerprint: JTextArea? = null

    /**
     * The purported fingerprint of the remote party.
     */
    private var txtRemoteFingerprint: JTextArea? = null

    /**
     * The "I have" / "I have not" combo box.
     */
    var cbAction: JComboBox? = null
        private set
    private val actionIHave = ActionComboBoxItem(ActionComboBoxItemIndex.I_HAVE)
    private val actionIHaveNot = ActionComboBoxItem(ActionComboBoxItemIndex.I_HAVE_NOT)
    private var txtAction: JTextArea? = null

    /**
     * Creates an instance FingerprintAuthenticationPanel
     */
    init {
        otrContact = contact
        initComponents()
        loadContact()
    }

    /**
     * Initializes the [FingerprintAuthenticationPanel] components.
     */
    private fun initComponents() {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(350, 300)
        val generalInformation = CustomTextArea()
        generalInformation.setText(
                OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_FINGERPRINT"))
        add(generalInformation)
        add(Box.createVerticalStrut(10))
        txtLocalFingerprint = CustomTextArea()
        add(txtLocalFingerprint)
        add(Box.createVerticalStrut(10))
        txtRemoteFingerprint = CustomTextArea()
        add(txtRemoteFingerprint)
        add(Box.createVerticalStrut(10))

        // Action Panel (the panel that holds the I have/I have not dropdown)
        val pnlAction = JPanel(GridBagLayout())
        pnlAction.setBorder(BorderFactory.createEtchedBorder())
        add(pnlAction)
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(5, 5, 5, 5)
        c.weightx = 0.0
        cbAction = JComboBox()
        cbAction!!.addItem(actionIHave)
        cbAction!!.addItem(actionIHaveNot)
        val pubKey = OtrActivator.scOtrEngine.getRemotePublicKey(otrContact)
        val remoteFingerprint = OtrActivator.scOtrKeyManager.getFingerprintFromPublicKey(pubKey)
        cbAction!!.selectedItem = if (OtrActivator.scOtrKeyManager.isVerified(otrContact.contact, remoteFingerprint)) actionIHave else actionIHaveNot
        pnlAction.add(cbAction, c)
        txtAction = CustomTextArea()
        c.weightx = 1.0
        pnlAction.add(txtAction, c)
        val resourceName = if (otrContact.resource != null) "/" + otrContact.resource.resourceName else ""
        txtRemoteFingerprintComparison = SIPCommTextField(
                aTalkApp.getResString(R.string.plugin_otr_authbuddydialog_FINGERPRINT_CHECK,
                        otrContact.contact!!.displayName + resourceName))
        txtRemoteFingerprintComparison!!.document!!.addDocumentListener(this)
        c.gridwidth = 2
        c.gridy = 1
        pnlAction.add(txtRemoteFingerprintComparison, c)
        c.gridwidth = 1
        c.gridy = 0
    }

    /**
     * Sets up the [OtrBuddyAuthenticationDialog] components so that they
     * reflect the [OtrBuddyAuthenticationDialog.otrContact]
     */
    private fun loadContact() {
        // Local fingerprint.
        val account = otrContact.contact!!.protocolProvider.accountID.displayName
        val localFingerprint = OtrActivator.scOtrKeyManager.getLocalFingerprint(otrContact.contact.protocolProvider.accountID)
        txtLocalFingerprint!!.setText(OtrActivator.resourceService!!.getI18NString(
                "plugin.otr.authbuddydialog.LOCAL_FINGERPRINT", arrayOf(account!!, localFingerprint!!)))

        // Remote fingerprint.
        val user = otrContact.contact.displayName
        val pubKey = OtrActivator.scOtrEngine.getRemotePublicKey(otrContact)
        val remoteFingerprint = OtrActivator.scOtrKeyManager.getFingerprintFromPublicKey(pubKey)
        txtRemoteFingerprint!!.setText(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.REMOTE_FINGERPRINT", arrayOf(user, remoteFingerprint!!)))

        // Action
        txtAction!!.setText(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.VERIFY_ACTION", arrayOf(user)))
    }

    override fun removeUpdate(e: DocumentEvent) {
        compareFingerprints()
    }

    override fun insertUpdate(e: DocumentEvent) {
        compareFingerprints()
    }

    override fun changedUpdate(e: DocumentEvent) {
        compareFingerprints()
    }

    private fun compareFingerprints() {
        val pubKey = OtrActivator.scOtrEngine.getRemotePublicKey(otrContact)
        val remoteFingerprint = OtrActivator.scOtrKeyManager.getFingerprintFromPublicKey(pubKey)
        if (txtRemoteFingerprintComparison!!.text == null || txtRemoteFingerprintComparison!!.text!!.isEmpty()) {
            txtRemoteFingerprintComparison!!.setBackground(Color.white)
            return
        }
        if (txtRemoteFingerprintComparison!!.text!!.lowercase(Locale.ROOT).contains(remoteFingerprint!!.lowercase(Locale.getDefault()))) {
            txtRemoteFingerprintComparison!!.setBackground(Color.green)
            cbAction!!.selectedItem = actionIHave
        } else {
            txtRemoteFingerprintComparison!!.setBackground(Color(243, 72, 48))
            cbAction!!.selectedItem = actionIHaveNot
        }
    }

    /**
     * A simple enumeration that is meant to be used with
     * [ActionComboBoxItem] to distinguish them (like an ID).
     *
     * @author George Politis
     */
    internal enum class ActionComboBoxItemIndex {
        I_HAVE, I_HAVE_NOT
    }

    /**
     * A special [JComboBox] that is hosted in OtrBuddyAuthenticationDialog.cbAction.
     *
     * @author George Politis
     */
    internal inner class ActionComboBoxItem(var action: ActionComboBoxItemIndex) {
        private var text: String? = null

        init {
            text = when (action) {
                ActionComboBoxItemIndex.I_HAVE -> OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.I_HAVE")
                ActionComboBoxItemIndex.I_HAVE_NOT -> OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.I_HAVE_NOT")
            }
        }

        override fun toString(): String {
            return text!!
        }
    }
}