/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray

import androidx.core.app.NotificationCompat
import net.java.sip.communicator.service.systray.PopupMessage

/**
 * Popup notification that consists of few merged previous popups.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidMergedPopup internal constructor(rootPopup: AndroidPopup) : AndroidPopup(rootPopup.handler, rootPopup.popupMessage) {
    /**
     * List of merged popups.
     */
    private val mergedPopups = ArrayList<AndroidPopup>()

    /**
     * Creates new instance of `AndroidMergedPopup` with given `AndroidPopup` as root.
     *
     * rootPopup root `AndroidPopup`.
     */
    init {
        id = rootPopup.id
    }

    /**
     * {@inheritDoc}
     */
    public override fun mergePopup(popupMessage: PopupMessage?): AndroidPopup {
        // Timing out notifications are replaced - not valid in android
//        AndroidPopup replace = null;
//        if (mergedPopups.size() > 0) {
//            replace = mergedPopups.get(mergedPopups.size() - 1);
//            if (replace.timeoutHandler != null) {
//                replace.cancelTimeout();
//            }
//        }
//        if (replace != null) {
//            mergedPopups.set(mergedPopups.indexOf(replace), new AndroidPopup(handler, popupMessage));
//        }
//        else {
//            mergedPopups.add(new AndroidPopup(handler, popupMessage));
//        }
        mergedPopups.add(AndroidPopup(handler, popupMessage))
        return this
    }

    /**
     * {@inheritDoc}
     */
    override val message: String
        get() {
            val msg = StringBuilder(super.message!!)
            for (popup in mergedPopups) {
                msg.append("\n").append(popup.message)
            }
            return msg.toString()
        }

    /**
     * {@inheritDoc}
     */
    override fun buildNotification(nId: Int): NotificationCompat.Builder {
        val builder = super.buildNotification(nId)
        // Set number of events
        builder!!.setNumber(mergedPopups.size + 1)
        return builder
    }

    /**
     * {@inheritDoc}
     */
    override fun onBuildInboxStyle(inboxStyle: NotificationCompat.InboxStyle) {
        super.onBuildInboxStyle(inboxStyle)
        for (popup in mergedPopups) {
            inboxStyle.addLine(popup.message)
        }
    }

    fun displaySnoozeAction(): Boolean {
        return mergedPopups.size > 2
    }
}