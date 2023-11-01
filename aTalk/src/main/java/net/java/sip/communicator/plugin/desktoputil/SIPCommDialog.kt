package net.java.sip.communicator.plugin.desktoputil

import javax.swing.JDialog

open class SIPCommDialog() : JDialog() {
    /**
     * Indicates if the size and location of this dialog are stored after closing.
     */
    private var isSaveSizeAndLocation = true

    init {
        init()
    }

    constructor(isSaveSizeAndLocation: Boolean) : this() {
        this.isSaveSizeAndLocation = isSaveSizeAndLocation
    }

    fun setTitle(paramString: String?) {}
    fun dispose() {}
    private fun init() {}
    override fun setVisible(isVisible: Boolean) {
        super.setVisible(isVisible)
    }

    companion object {
        /*
     * Serial version UID.
     */
        private const val serialVersionUID = 0L
    }
}