/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr.authdialog

import net.java.otr4j.session.InstanceTag
import net.java.sip.communicator.plugin.desktoputil.SIPCommDialog
import net.java.sip.communicator.plugin.desktoputil.TransparentPanel
import net.java.sip.communicator.plugin.otr.OtrActivator
import net.java.sip.communicator.plugin.otr.OtrContactManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.UIManager

/**
 * The dialog that pops up when the remote party send us SMP request. It contains detailed information for the user about the authentication
 * process and allows him to authenticate.
 *
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
// @SuppressWarnings("serial")
class SmpAuthenticateBuddyDialog(contact: OtrContactManager.OtrContact, receiverTag: InstanceTag, question: String?) : SIPCommDialog() {
    private val otrContact: OtrContactManager.OtrContact
    private val question: String
    private val receiverTag: InstanceTag

    init {
        otrContact = contact
        this.receiverTag = receiverTag
        this.question = question!!
        initComponents()
    }

    private fun initComponents() {
        setTitle(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.TITLE"))

        // The main panel that contains all components.
        val mainPanel = TransparentPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
        mainPanel.preferredSize = Dimension(300, 350)

        // Add "authentication from contact" to the main panel.
        val authenticationFrom = CustomTextArea()
        var newFont = Font(UIManager.getDefaults().getFont("TextArea.font").fontName, Font.BOLD, 14)
        authenticationFrom.setFont(newFont)
        val resourceName = if (otrContact.resource != null) "/" + otrContact.resource.resourceName else ""
        val authFromText = String.format(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_FROM", arrayOf(otrContact.contact!!.displayName + resourceName))!!)
        authenticationFrom.setText(authFromText)
        mainPanel.add(authenticationFrom)

        // Add "general info" text to the main panel.
        val generalInfo = CustomTextArea()
        generalInfo.setText(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_INFO"))
        mainPanel.add(generalInfo)

        // Add "authentication-by-secret" info text to the main panel.
        val authBySecretInfo = CustomTextArea()
        newFont = Font(UIManager.getDefaults().getFont("TextArea.font").fontName, Font.ITALIC, 10)
        authBySecretInfo.setText(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTH_BY_SECRET_INFO_RESPOND"))
        authBySecretInfo.setFont(newFont)
        mainPanel.add(authBySecretInfo)

        // Create a panel to add question/answer related components
        val questionAnswerPanel = JPanel(GridBagLayout())
        questionAnswerPanel.setBorder(BorderFactory.createEtchedBorder())
        val c = GridBagConstraints()
        c.gridx = 0
        c.gridy = 0
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(5, 5, 0, 5)
        c.weightx = 0.0

        /* Add question label. */
        val questionLabel = JLabel(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.QUESTION_RESPOND"))
        questionAnswerPanel.add(questionLabel, c)

        // Add the question.
        c.insets = Insets(0, 5, 5, 5)
        c.gridy = 1
        val questionArea = CustomTextArea()
        newFont = Font(UIManager.getDefaults().getFont("TextArea.font").fontName, Font.BOLD, UIManager.getDefaults().getFont("TextArea.font")
                .size)
        questionArea.setFont(newFont)
        questionArea.setText(question)
        questionAnswerPanel.add(questionArea, c)

        // Add answer label.
        c.insets = Insets(5, 5, 5, 5)
        c.gridy = 2
        val answerLabel = JLabel(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.ANSWER"))
        questionAnswerPanel.add(answerLabel, c)

        // Add the answer text field.
        c.gridy = 3
        val answerTextBox = JTextField()
        questionAnswerPanel.add(answerTextBox, c)

        // Add the question/answer panel to the main panel.
        mainPanel.add(questionAnswerPanel)

        // Buttons panel.
        val buttonPanel = TransparentPanel(GridBagLayout())
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5))
        val helpButton = JButton(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.HELP")!!)
        helpButton.addActionListener { OtrActivator.scOtrEngine.launchHelp() }
        c.gridwidth = 1
        c.gridy = 0
        c.gridx = 0
        c.weightx = 0.0
        c.insets = Insets(5, 5, 5, 20)
        buttonPanel.add(helpButton, c)
        val cancelButton = JButton(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.CANCEL"))
        cancelButton.addActionListener {
            OtrActivator.scOtrEngine.abortSmp(otrContact)
            this@SmpAuthenticateBuddyDialog.dispose()
        }
        c.insets = Insets(5, 5, 5, 5)
        c.gridx = 1
        buttonPanel.add(cancelButton, c)
        c.gridx = 2
        val authenticateButton = JButton(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATE_BUDDY"))
        authenticateButton.addActionListener {
            OtrActivator.scOtrEngine.respondSmp(otrContact, receiverTag, question, answerTextBox.text)
            this@SmpAuthenticateBuddyDialog.dispose()
        }
        buttonPanel.add(authenticateButton, c)
        this.contentPane.add(mainPanel, BorderLayout.NORTH)
        this.contentPane.add(buttonPanel, BorderLayout.SOUTH)
        this.pack()
    }
}