/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr.authdialog

import net.java.sip.communicator.plugin.desktoputil.SIPCommDialog
import net.java.sip.communicator.plugin.desktoputil.TransparentPanel
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.plugin.otr.OtrContactManager
import net.java.sip.communicator.plugin.otr.authdialog.FingerprintAuthenticationPanel.ActionComboBoxItem
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.security.PublicKey
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * @author George Politis
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class OtrBuddyAuthenticationDialog(otrContact: OtrContactManager.OtrContact) : SIPCommDialog(false) {
    private val otrContact: OtrContactManager.OtrContact

    /**
     * The OtrBuddyAuthenticationDialog ctor.
     *
     * otrContact The Contact this OtrBuddyAuthenticationDialog refers to.
     */
    init {
        this.otrContact = otrContact
        initComponents()
    }

    /**
     * Initializes the [OtrBuddyAuthenticationDialog] components.
     */
    private fun initComponents() {
        setTitle(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.TITLE"))
        val mainPanel = TransparentPanel()
        mainPanel.setLayout(BoxLayout(mainPanel, BoxLayout.Y_AXIS))
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
        mainPanel.setPreferredSize(Dimension(350, 400))
        val generalInformation = CustomTextArea()
        val resourceService = OtrActivator.resourceService!!
        generalInformation.setText(resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_INFO"))
        mainPanel.add(generalInformation)
        mainPanel.add(Box.createVerticalStrut(10))

        // Add authentication method label and combo box.
        val am = arrayOf(
                resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_METHOD_QUESTION")!!,
                resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_METHOD_SECRET")!!,
                resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_METHOD_FINGERPRINT")!!)
        val authenticationMethodComboBox = JComboBox(am)
        val authMethodLabel = CustomTextArea()
        authMethodLabel.setText(resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_METHOD"))
        mainPanel.add(authMethodLabel)
        mainPanel.add(authenticationMethodComboBox)
        mainPanel.add(Box.createVerticalStrut(10))

        // Add authentication panels in a card layout so that the user can
        // use the combo box to switch between authentication methods.
        val authenticationPanel = TransparentPanel(CardLayout())
        val fingerprintPanel = FingerprintAuthenticationPanel(otrContact)
        val secretQuestionPanel = SecretQuestionAuthenticationPanel()
        val sharedSecretPanel = SharedSecretAuthenticationPanel()
        authenticationPanel.add(secretQuestionPanel, am[0])
        authenticationPanel.add(sharedSecretPanel, am[1])
        authenticationPanel.add(fingerprintPanel, am[2])
        authenticationMethodComboBox.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val cl = authenticationPanel.layout as CardLayout
                cl.show(authenticationPanel, e.item as String)
            }
        }
        authenticationMethodComboBox.setSelectedIndex(0)
        mainPanel.add(authenticationPanel)
        val c = GridBagConstraints()
        c.insets = Insets(5, 5, 5, 5)
        c.weightx = 1.0
        c.gridwidth = 1

        // Buttons panel.
        val buttonPanel = TransparentPanel(GridBagLayout())
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5))
        val helpButton = JButton(resourceService.getI18NString("plugin.otr.authbuddydialog.HELP"))
        helpButton.addActionListener { OtrActivator.scOtrEngine.launchHelp() }
        buttonPanel.add(helpButton, c)

        // Provide space between help and the other two button, not sure if this
        // is optimal..
        c.weightx = 1.0
        buttonPanel.add(JLabel(), c)
        c.weightx = 0.0
        val cancelButton = JButton(resourceService.getI18NString("plugin.otr.authbuddydialog.CANCEL"))
        cancelButton.addActionListener { dispose() }
        buttonPanel.add(cancelButton, c)
        val authenticateButton = JButton(resourceService.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATE_BUDDY"))
        authenticateButton.addActionListener {
            val authenticationMethod = authenticationMethodComboBox.selectedItem as String
            if (authenticationMethod == am[0]) {
                val secret = secretQuestionPanel.secret
                val question = secretQuestionPanel.question
                OtrActivator.scOtrEngine.initSmp(otrContact, question.toString(), secret)
                dispose()
            } else if (authenticationMethod == am[1]) {
                val secret = sharedSecretPanel.secret
                val question: String? = null
                OtrActivator.scOtrEngine.initSmp(otrContact, question, secret.toString())
                dispose()
            } else if (authenticationMethod == am[2]) {
                val actionItem = fingerprintPanel.cbAction!!.selectedItem as ActionComboBoxItem
                val pubKey = OtrActivator.scOtrEngine.getRemotePublicKey(otrContact)
                val fingerprint = OtrActivator.scOtrKeyManager.getFingerprintFromPublicKey(pubKey)
                when (actionItem.action) {
                    FingerprintAuthenticationPanel.ActionComboBoxItemIndex.I_HAVE -> OtrActivator.scOtrKeyManager.verify(otrContact, fingerprint)
                    FingerprintAuthenticationPanel.ActionComboBoxItemIndex.I_HAVE_NOT -> OtrActivator.scOtrKeyManager.unverify(otrContact, fingerprint)
                }
                dispose()
            }
        }
        buttonPanel.add(authenticateButton, c)
        this.getContentPane().add(mainPanel, BorderLayout.NORTH)
        this.getContentPane().add(buttonPanel, BorderLayout.SOUTH)
        this.pack()
    }
}