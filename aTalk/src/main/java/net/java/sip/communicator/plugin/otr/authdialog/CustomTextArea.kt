package net.java.sip.communicator.plugin.otr.authdialog

import java.awt.Color
import javax.swing.JTextArea

/**
 * A special [JTextArea] for use in the OTR authentication panels.
 * It is meant to be used for fingerprint representation and general
 * information display.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class CustomTextArea : JTextArea() {
    init {
        setBackground(Color(0, 0, 0, 0))
        setOpaque(false)
        setColumns(20)
        setEditable(false)
        setLineWrap(true)
        setWrapStyleWord(true)
    }
}