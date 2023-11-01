/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.FileTransferProgressEvent
import net.java.sip.communicator.service.protocol.event.FileTransferProgressListener
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.jivesoftware.smackx.jingle.element.JingleReason
import timber.log.Timber
import java.util.*

/**
 * An abstract implementation of the `FileTransfer` interface providing implementation of
 * status and progress events related methods and leaving all protocol specific methods abstract. A
 * protocol specific implementation could extend this class and implement only `cancel()` and
 * `getTransferredBytes()`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
abstract class AbstractFileTransfer : FileTransfer {
    /**
     * A list of listeners registered for file transfer status events.
     */
    private val statusListeners = Vector<FileTransferStatusListener>()

    /**
     * A list of listeners registered for file transfer progress status events.
     */
    private val progressListeners = Vector<FileTransferProgressListener>()

    /*
     * current file transfer Status for keeping track if there is changes;
     * Default to WAITING for contact to accept on start up.
     */
    private var mStatus = FileTransferStatusChangeEvent.WAITING

    /*
     * current progress of byte transferred for keeping track if there is changes
     */
    private var mProgress = 0L

    /**
     * Cancels this file transfer. When this method is called transfer should be interrupted.
     */
    abstract override fun cancel()

    /**
     * Returns the number of bytes already transferred through this file transfer.
     * Note Some file transfer progress is handled via event trigger.
     *
     * @return the number of bytes already transferred through this file transfer
     */
    override fun getTransferredBytes(): Long {
        return -1
    }

    /**
     * Adds the given `FileTransferProgressListener` to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    override fun addProgressListener(listener: FileTransferProgressListener) {
        synchronized(progressListeners) {
            if (!progressListeners.contains(listener)) {
                progressListeners.add(listener)
            }
        }
    }

    /**
     * Adds the given `FileTransferStatusListener` to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    override fun addStatusListener(listener: FileTransferStatusListener) {
        synchronized(statusListeners) {
            if (!statusListeners.contains(listener)) {
                statusListeners.add(listener)
            }
        }
    }

    /**
     * Removes the given `FileTransferProgressListener`.
     *
     * @param listener the listener to remove
     */
    override fun removeProgressListener(listener: FileTransferProgressListener) {
        synchronized(progressListeners) { progressListeners.remove(listener) }
    }

    /**
     * Removes the given `FileTransferStatusListener`.
     *
     * @param listener the listener to remove
     */
    override fun removeStatusListener(listener: FileTransferStatusListener) {
        synchronized(statusListeners) { statusListeners.remove(listener) }
    }

    /**
     * Returns the current status of the transfer. This information could be used from the user
     * interface to show a progress bar indicating the file transfer status.
     *
     * @return the current status of the transfer
     * @see FileTransferStatusChangeEvent
     * @see net.java.sip.communicator.service.filehistory.FileRecord
     */
    override fun getStatus(): Int {
        return mStatus
    }

    /**
     * Notifies all status listeners that a new `FileTransferStatusChangeEvent` has occurred.
     *
     * @param reason the jingle terminate reason
     */
    fun fireStatusChangeEvent(reason: JingleReason) {
        var reasonText = if (reason.text != null) reason.text else reason.asEnum().toString()
        when (reason.asEnum()) {
            JingleReason.Reason.decline -> fireStatusChangeEvent(FileTransferStatusChangeEvent.DECLINED, reasonText)
            JingleReason.Reason.cancel -> fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, reasonText)
            JingleReason.Reason.success -> fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, reasonText)
            else -> {
                reasonText = aTalkApp.getResString(R.string.service_gui_FILE_SEND_CLIENT_ERROR, reasonText)
                fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, reasonText)
            }
        }
    }

    /**
     * Notifies all status listeners that a new `FileTransferStatusChangeEvent` has occurred.
     *
     * @param newStatus the new status
     * @param reason the reason of the status change
     */
    fun fireStatusChangeEvent(newStatus: Int, reason: String?) {
        // Just ignore if status is the same
        if (mStatus == newStatus) return
        var listeners: Collection<FileTransferStatusListener>
        synchronized(statusListeners) { listeners = ArrayList(statusListeners) }
        Timber.d("Dispatching FileTransfer status change: %s => %s to %d listeners.",
                mStatus, newStatus, listeners.size)

        // Updates the mStatus only after statusEvent is created.
        val statusEvent = FileTransferStatusChangeEvent(this, mStatus, newStatus, reason!!)
        mStatus = newStatus
        for (statusListener in listeners) {
            statusListener.statusChanged(statusEvent)
        }
    }

    /**
     * Notifies all status listeners that a new `FileTransferProgressEvent` occurred.
     *
     * @param timestamp the date on which the event occurred
     * @param progress the bytes representing the progress of the transfer
     */
    fun fireProgressChangeEvent(timestamp: Long, progress: Long) {
        // ignore if there is no change since the last progress check
        if (mProgress == progress) return
        mProgress = progress
        var listeners: Collection<FileTransferProgressListener>
        synchronized(progressListeners) { listeners = ArrayList(progressListeners) }
        val progressEvent = FileTransferProgressEvent(this, timestamp, progress)
        for (statusListener in listeners) {
            statusListener.progressChanged(progressEvent)
        }
    }
}