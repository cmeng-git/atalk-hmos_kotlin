/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr.authdialog

import net.java.sip.communicator.plugin.desktoputil.TransparentPanel
import net.java.sip.communicator.plugin.otr.OtrActivator
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class SecretQuestionAuthenticationPanel internal constructor() : TransparentPanel() {
    /**
     * The text field where the authentication initiator will type his question.
     */
    val question = JTextField()

    /**
     * The text field where the authentication initiator will type his answer.
     */
    private val answer = JTextField()

    /**
     * Creates an instance SecretQuestionAuthenticationPanel.
     */
    init {
        initComponents()
    }

    /**
     * Initializes the [SecretQuestionAuthenticationPanel] components.
     */
    private fun initComponents() {
        setLayout(BoxLayout(this, BoxLayout.Y_AXIS))
        val generalInformation = CustomTextArea()
        generalInformation.setText(
                OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTH_BY_QUESTION_INFO_INIT"))
        this.add(generalInformation)
        this.add(Box.createVerticalStrut(10))
        val questionAnswerPanel = JPanel(GridBagLayout())
        questionAnswerPanel.setBorder(BorderFactory.createEtchedBorder())
        val c = GridBagConstraints()
        c.gridx = 0
        c.gridy = 0
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(5, 5, 0, 5)
        c.weightx = 1.0
        val questionLabel = JLabel(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.QUESTION_INIT"))
        questionAnswerPanel.add(questionLabel, c)
        c.gridy = 1
        c.insets = Insets(0, 5, 5, 5)
        questionAnswerPanel.add(question, c)
        c.gridy = 2
        c.insets = Insets(5, 5, 0, 5)
        val answerLabel = JLabel(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.ANSWER"))
        questionAnswerPanel.add(answerLabel, c)
        c.gridy = 3
        c.insets = Insets(0, 5, 5, 5)
        questionAnswerPanel.add(answer, c)
        this.add(questionAnswerPanel)
        this.add(Box.Filler(
                Dimension(300, 100),
                Dimension(300, 100),
                Dimension(300, 100)))
    }

    /**
     * Returns the secret answer text.
     *
     * @return The secret answer text.
     */
    val secret: String
        get() = answer.text

    /**
     * Returns the secret question text.
     *
     * @return The secret question text.
     */
    fun getQuestion(): String {
        return question.text
    }
}