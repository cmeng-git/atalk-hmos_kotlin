/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import android.widget.Toast
import net.java.sip.communicator.service.systray.AbstractPopupMessageHandler
import net.java.sip.communicator.service.systray.PopupMessage
import org.atalk.hmos.aTalkApp

/**
 * TODO: Toast popup handler stub. It should be registered by displayed Activity as we need to hold the UI thread to
 * show Toasts. Also [ClickableToastController] may be used to catch the clicks.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ToastPopupMessageHandler : AbstractPopupMessageHandler() {
    /**
     * {@inheritDoc}
     */
    override fun showPopupMessage(popupMessage: PopupMessage) {
        val context = aTalkApp.globalContext
        val text = popupMessage.message
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.show()
    }

    /**
     * {@inheritDoc}
     */
    override val preferenceIndex = 1

    override fun toString(): String {
        TODO("Not yet implemented")
    }
}