package net.java.sip.communicator.plugin.desktoputil

import java.awt.Color
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Document
import javax.swing.text.JTextComponent

class SIPCommTextField(text: String?) : JTextField(text), DocumentListener {
    /**
     * The default text.
     */
    private var defaultText: String? = null

    /**
     * Indicates if the default text is currently visible.
     */
    private var isDefaultTextVisible = false
    private val foregroundColor = Color.BLACK
    private val defaultTextColor = Color.GRAY

    init {
        if (text != null && text.length > 0) {
            defaultText = text
            isDefaultTextVisible = true
        }
        JTextComponent.getDocument().addDocumentListener(this)
    }

    override fun getText(): String? {
        // TODO Auto-generated method stub
        return null
    }

    fun setBackground(green: Any?) {
        // TODO Auto-generated method stub
    }

    override fun insertUpdate(paramDocumentEvent: DocumentEvent) {
        // TODO Auto-generated method stub
    }

    override fun removeUpdate(paramDocumentEvent: DocumentEvent) {
        // TODO Auto-generated method stub
    }

    override fun changedUpdate(paramDocumentEvent: DocumentEvent) {
        // TODO Auto-generated method stub
    }

    // TODO Auto-generated method stub
    val document: Document?
        get() =// TODO Auto-generated method stub
            null

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}