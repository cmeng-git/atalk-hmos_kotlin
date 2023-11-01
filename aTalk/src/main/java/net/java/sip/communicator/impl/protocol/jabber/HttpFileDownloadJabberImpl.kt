/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.protocol.jabber

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import net.java.sip.communicator.service.protocol.AbstractFileTransfer
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.FileTransfer
import net.java.sip.communicator.service.protocol.IMessage
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.persistance.FileBackend
import org.atalk.persistance.FileBackend.getaTalkStore
import org.atalk.persistance.FilePathHelper.getFilePath
import org.jivesoftware.smackx.omemo_media_sharing.AesgcmUrl
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import javax.crypto.CipherOutputStream

/**
 * The Jabber protocol HttpFileDownloadJabberImpl extension of the `AbstractFileTransfer`.
 *
 * @author Eng Chong Meng
 */
open class HttpFileDownloadJabberImpl(private val mSender: Contact, id: String?, dnLinkDescription: String) : AbstractFileTransfer() {
    /* DownloadManager Broadcast Receiver Handler */
    private var downloadReceiver: DownloadReceiver? = null
    private val downloadManager = aTalkApp.downloadManager

    /* previousDownloads <DownloadJobId, Download Link> */
    private val previousDownloads = Hashtable<Long, String>()
    private val msgUuid: String

    /*
     * The advertised downloadable file info:
     * mFile: server url link last segment: File
     * mFileName: mFile filename
     * dnLink: server url link for download
     * mFileSize: the query size of the dnLink file
     */
    private val mFile: File?
    private val mFileName: String?
    private val dnLink: String

    // https download uri link; extracted from dnLink if it is AesgcmUrl
    private val mUri: Uri
    private var mFileSize = 0L

    /**
     * The transfer file full path for saving the received file.
     */
    private var mXferFile: File? = null

    /*
     * Transfer file encryption type, default to ENCRYPTION_NONE.
     */
    private var mEncryption = 0

    /**
     * Unregister the HttpDownload transfer downloadReceiver.
     */
    override fun cancel() {
        doCleanup()
    }

    /**
     * The direction is incoming.
     *
     * @return IN
     */
    override fun getDirection(): Int {
        return FileTransfer.IN
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    override fun getContact(): Contact {
        return mSender
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    override fun getID(): String {
        return msgUuid
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    override fun getLocalFile(): File? {
        return mFile
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    fun getFileName(): String? {
        return mFileName
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    fun getFileSize(): Long {
        return mFileSize
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    fun getDnLink(): String {
        return dnLink
    }

    /**
     * Returns the encryption of the file corresponding to this request.
     *
     * @return the encryption of the file corresponding to this request
     */
    fun getEncryptionType(): Int {
        return mEncryption
    }
    // ********************************************************************************************//
    // Routines supporting HTTP File Download
    /**
     * Method fired when the chat message is clicked. {@inheritDoc}
     * Trigger from @see ChatFragment#
     *
     * checkFileSize check acceptable file Size limit before download if true
     */
    fun initHttpFileDownload() {
        if (previousDownloads.contains(dnLink)) return

        // queryFileSize will also trigger onReceived; just ignore
        if (downloadReceiver == null) {
            downloadReceiver = DownloadReceiver()
            aTalkApp.globalContext.registerReceiver(downloadReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        if (mFileSize == -1L) {
            mFileSize = queryFileSize()
        }
        // Timber.d("Download receiver registered %s: file size: %s", downloadReceiver, mFileSize);
    }

    /**
     * Query the http uploaded file size for auto download.
     */
    private fun queryFileSize(): Long {
        mFileSize = -1
        val request = DownloadManager.Request(mUri)
        val id = downloadManager.enqueue(request)
        val query = DownloadManager.Query()
        query.setFilterById(id)

        // allow loop for 3 seconds for slow server. Server can return size == 0 ?
        var wait = 3
        while (wait-- > 0 && mFileSize <= 0) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                Timber.w("Download Manager query file size exception: %s", e.message)
                return -1
            }
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                mFileSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).toLong()
            }
            cursor.close()
        }

        // Timber.d("Download Manager file size query id: %s %s (%s)", id, mFileSize, wait);
        return mFileSize
    }

    /**
     * Schedules media file download.
     */
    fun download(xferFile: File?) {
        mXferFile = xferFile
        val request = DownloadManager.Request(mUri)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        try {
            val tmpFile = File(getaTalkStore(FileBackend.TMP, true), mFileName!!)
            request.setDestinationUri(Uri.fromFile(tmpFile))
            val jobId = downloadManager.enqueue(request)
            if (jobId > 0) {
                previousDownloads[jobId] = dnLink
                startProgressChecker()
                // Timber.d("Download Manager HttpFileDownload Size: %s %s", mFileSize, previousDownloads.toString());
                fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, null)

                // Send a fake progressChangeEvent to show progressBar
                fireProgressChangeEvent(System.currentTimeMillis(), 100)
            }
        } catch (e: SecurityException) {
            aTalkApp.showToastMessage(e.message)
        } catch (e: Exception) {
            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
        }
    }

    /**
     * Queries the `DownloadManager` for the status of download job identified by given `id`.
     *
     * id download identifier which status will be returned.
     *
     * @return download status of the job identified by given id. If given job is not found
     * [DownloadManager.STATUS_FAILED] will be returned.
     */
    private fun checkDownloadStatus(id: Long): Int {
        val query = DownloadManager.Query()
        query.setFilterById(id)
        downloadManager.query(query).use { cursor ->
            return if (!cursor.moveToFirst()) DownloadManager.STATUS_FAILED else {
                // update fileSize if last queryFileSize failed within the given timeout
                if (mFileSize <= 0) {
                    mFileSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).toLong()
                }
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }
    }

    private inner class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Fetching the download id received with the broadcast and
            // if the received broadcast is for our enqueued download by matching download id
            val lastDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val lastJobStatus = checkDownloadStatus(lastDownloadId)
            // Timber.d("Download receiver %s (%s): %s", lastDownloadId, previousDownloads, lastJobStatus);

            // Just ignore all unrelated download JobId.
            if (previousDownloads.containsKey(lastDownloadId)) {
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    val dnLink = previousDownloads[lastDownloadId]
                    val fileUri = downloadManager.getUriForDownloadedFile(lastDownloadId)
                    val inFile = File(getFilePath(context, fileUri)!!)

                    // update fileSize for progress bar update, in case it is still not updated by download Manager
                    mFileSize = inFile.length()
                    if (inFile.exists()) {
                        // OMEMO media file sharing - need to decrypt file content
                        if (dnLink != null && dnLink.matches(Regex("^aesgcm:.*"))) {
                            try {
                                val aesgcmUrl = AesgcmUrl(dnLink)
                                val decryptCipher = aesgcmUrl.decryptionCipher
                                val fis = FileInputStream(inFile)
                                val outputStream = FileOutputStream(mXferFile)
                                val cipherOutputStream = CipherOutputStream(outputStream, decryptCipher)
                                var count: Int
                                val buffer = ByteArray(4096)
                                while (fis.read(buffer).also { count = it } != -1) {
                                    cipherOutputStream.write(buffer, 0, count)
                                }
                                fis.close()
                                outputStream.flush()
                                cipherOutputStream.close()
                                // inFile.delete();
                                fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, null)
                            } catch (e: Exception) {
                                fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED,
                                        "Failed to decrypt OMEMO media file: $inFile")
                            }
                        } else {
                            // Plain media file sharing; rename will move the infile to outfile dir.
                            if (inFile.renameTo(mXferFile!!)) {
                                fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, null)
                            }
                        }

                        // Timber.d("Downloaded fileSize: %s (%s)", outFile.length(), fileSize);
                        previousDownloads.remove(lastDownloadId)
                        // Remove lastDownloadId from downloadManager record and delete the tmp file
                        downloadManager.remove(lastDownloadId)
                    }
                } else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, dnLink)
                }
                doCleanup()
            }
        }
    }

    /**
     * Get the jobId for the given dnLink
     *
     * dnLink previously download link
     *
     * @return jobId for the dnLink if available else -1
     */
    private fun getJobId(dnLink: String): Long {
        for ((key, value) in previousDownloads) {
            if (value == dnLink) {
                return key
            }
        }
        return -1
    }

    /**
     * Perform cleanup at end of http file transfer process: passed, failed or cancel.
     */
    private fun doCleanup() {
        stopProgressChecker()
        val jobId = getJobId(dnLink)
        if (jobId != -1L) {
            previousDownloads.remove(jobId)
            downloadManager.remove(jobId)
        }

        // Unregister the HttpDownload transfer downloadReceiver.
        // Receiver not registered exception - may occur if window is refreshed while download is in progress?
        if (downloadReceiver != null) {
            try {
                aTalkApp.globalContext.unregisterReceiver(downloadReceiver)
            } catch (ie: IllegalArgumentException) {
                Timber.w("Unregister download receiver exception: %s", ie.message)
            }
            downloadReceiver = null
        }
        // Timber.d("Download Manager for JobId: %s; File: %s (status: %s)", jobId, dnLink, status);
    }

    private var isProgressCheckerRunning = false
    private val handler = Handler()
    private var previousProgress = 0L
    private var waitTime = 0

    /**
     * Checks http download progress.
     */
    private fun checkProgress() {
        val lastDownloadId = getJobId(dnLink)
        val lastJobStatus = checkDownloadStatus(lastDownloadId)
        // Timber.d("Downloading file last jobId: %s; lastJobStatus: %s; dnProgress: %s (%s)",
        //       lastDownloadId, lastJobStatus, previousProgress, waitTime);

        // Terminate downloading task if failed or idleTime timeout
        if (lastJobStatus == DownloadManager.STATUS_FAILED || waitTime < 0) {
            val tmpFile = File(getaTalkStore(FileBackend.TMP, true), mFileName!!)
            Timber.d("Downloaded fileSize (failed): %s (%s)", tmpFile.length(), mFileSize)
            fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, null)
            doCleanup()
            return
        }
        val query = DownloadManager.Query()
        query.setFilterByStatus((DownloadManager.STATUS_FAILED or DownloadManager.STATUS_SUCCESSFUL).inv())
        val cursor = downloadManager.query(query)
        if (!cursor.moveToFirst()) {
            waitTime--
        } else {
            do {
                val progress = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                if (progress <= previousProgress) waitTime-- else {
                    waitTime = MAX_IDLE_TIME
                    previousProgress = progress
                    fireProgressChangeEvent(System.currentTimeMillis(), progress)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    /**
     * Starts watching download progress.
     *
     * This method is safe to call multiple times. Starting an already running progress checker is a no-op.
     */
    private fun startProgressChecker() {
        if (!isProgressCheckerRunning) {
            isProgressCheckerRunning = true
            waitTime = MAX_IDLE_TIME
            previousProgress = -1
            progressChecker.run()
        }
    }

    /**
     * Stops watching download progress.
     */
    private fun stopProgressChecker() {
        isProgressCheckerRunning = false
        handler.removeCallbacks(progressChecker)
    }

    /**
     * Checks download progress and updates status, then re-schedules itself.
     */
    private val progressChecker = object : Runnable {
        override fun run() {
            if (isProgressCheckerRunning) {
                checkProgress()
                handler.postDelayed(this, PROGRESS_DELAY.toLong())
            }
        }
    }

    /**
     * Creates an `IncomingFileTransferJabberImpl`.
     *
     * sender the sender of the file
     * id the message Uuid uniquely identify  record in DB
     * dnLinkDescription the download link may contains other options e.g. file.length()
     */
    init {

        // Create a new msg Uuid if none provided
        msgUuid = id ?: (System.currentTimeMillis().toString() + hashCode())
        val dnLinkInfos = dnLinkDescription.split("\\s+|,|\\t|\\n".toRegex())
        dnLink = dnLinkInfos[0]
        val url: String
        if (dnLink.matches(Regex("^aesgcm:.*"))) {
            val aesgcmUrl = AesgcmUrl(dnLink)
            url = aesgcmUrl.downloadUrl.toString()
            mEncryption = IMessage.ENCRYPTION_OMEMO
        } else {
            url = dnLink
            mEncryption = IMessage.ENCRYPTION_NONE
        }
        mUri = Uri.parse(url)
        mFileName = mUri.lastPathSegment
        mFile = if (mFileName != null) File(mFileName) else null
        mFileSize = if (dnLinkInfos.size > 1 && "fileSize".matches(Regex(dnLinkInfos[1]))) {
            dnLinkInfos[1].split("[:=]".toRegex())[1].toLong()
        } else -1
    }

    companion object {
        //=========================================================
        /*
     * Monitoring file download progress at PROGRESS_DELAY ms interval
     */
        private const val PROGRESS_DELAY = 500

        // Maximum download idle time, 60s = MAX_IDLE_TIME * PROGRESS_DELAY mS before giving up and force to stop
        private const val MAX_IDLE_TIME = 120
    }
}