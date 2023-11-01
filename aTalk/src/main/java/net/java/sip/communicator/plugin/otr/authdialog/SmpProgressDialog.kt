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
import net.java.sip.communicator.service.protocol.Contact
import java.awt.*
import javax.swing.*
import javax.swing.plaf.basic.BasicProgressBarUI

/**
 * The dialog that pops up when SMP negotiation starts.
 * It contains a progress bar that indicates the status of the SMP
 * authentication process.
 *
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
class SmpProgressDialog(contact: Contact) : SIPCommDialog() {
    private val progressBar = JProgressBar(0, 100)
    private val successColor = Color(86, 140, 2)
    private val failColor = Color(204, 0, 0)
    private val iconLabel = JLabel()

    /**
     * Instantiates SmpProgressDialog.
     *
     * @param contact The contact that this dialog is associated with.
     */
    init {
        setTitle(OtrActivator.resourceService!!.getI18NString("plugin.otr.smpprogressdialog.TITLE"))
        val mainPanel = TransparentPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
        mainPanel.preferredSize = Dimension(300, 70)
        val authFromText = String.format(OtrActivator.resourceService!!.getI18NString("plugin.otr.authbuddydialog.AUTHENTICATION_FROM", arrayOf(contact.displayName))!!)
        val labelsPanel = TransparentPanel()
        labelsPanel.layout = BoxLayout(labelsPanel, BoxLayout.X_AXIS)
        labelsPanel.add(iconLabel)
        labelsPanel.add(Box.createRigidArea(Dimension(5, 0)))
        labelsPanel.add(JLabel(authFromText))
        mainPanel.add(labelsPanel)
        mainPanel.add(progressBar)
        init()
        this.getContentPane().add(mainPanel)
        this.pack()
    }

    /**
     * Initializes the progress bar and sets it's progression to 1/3.
     */
    fun init() {
        progressBar.setUI(object : BasicProgressBarUI() {
            private var r = Rectangle()
            override fun paintIndeterminate(g: Graphics, c: JComponent) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                r = getBox(r)
                g.setColor(progressBar.foreground)
                g.fillOval(r.x, r.y, r.width, r.height)
            }
        })
        progressBar.setValue(33)
        progressBar.foreground = successColor
        progressBar.setStringPainted(false)
        iconLabel.icon = OtrActivator.resourceService!!.getImage(
                "plugin.otr.ENCRYPTED_UNVERIFIED_ICON_22x22")
    }

    /**
     * Sets the progress bar to 2/3 of completion.
     */
    fun incrementProgress() {
        progressBar.setValue(66)
    }

    /**
     * Sets the progress bar to green.
     */
    fun setProgressSuccess() {
        progressBar.setValue(100)
        progressBar.foreground = successColor
        progressBar.setStringPainted(true)
        progressBar.setString(OtrActivator.resourceService!!.getI18NString("plugin.otr.smpprogressdialog.AUTHENTICATION_SUCCESS"))
        iconLabel.icon = OtrActivator.resourceService!!.getImage("plugin.otr.ENCRYPTED_ICON_22x22")
    }

    /**
     * Sets the progress bar to red.
     */
    fun setProgressFail() {
        progressBar.setValue(100)
        progressBar.foreground = failColor
        progressBar.setStringPainted(true)
        progressBar.setString(OtrActivator.resourceService!!.getI18NString("plugin.otr.smpprogressdialog.AUTHENTICATION_FAIL"))
    }
}