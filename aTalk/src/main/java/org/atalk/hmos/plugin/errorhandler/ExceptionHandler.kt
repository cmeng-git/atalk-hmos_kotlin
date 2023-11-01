/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.plugin.errorhandler

import android.content.*
import android.os.Process
import org.atalk.hmos.aTalkApp
import org.atalk.service.fileaccess.FileCategory
import timber.log.Timber
import java.io.File

/**
 * The `ExceptionHandler` is used to catch unhandled exceptions which occur on the UI
 * `Thread`. Those exceptions normally cause current `Activity` to freeze and the
 * process usually must be killed after the Application Not Responding dialog is displayed. This
 * handler kills Jitsi process at the moment when the exception occurs, so that user don't have
 * to wait for ANR dialog. It also marks in `SharedPreferences` that such crash has
 * occurred. Next time the Jitsi is started it will ask the user if he wants to send the logs.<br></br>
 *
 *
 * Usually system restarts Jitsi and it's service automatically after the process was killed.
 * That's because the service was still bound to some `Activities` at the moment when the
 * exception occurred.<br></br>
 *
 *
 * The handler is bound to the `Thread` in every `OSGiActivity`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ExceptionHandler private constructor(t: Thread) : Thread.UncaughtExceptionHandler {
    /**
     * Parent exception handler(system default).
     */
    private val parent: Thread.UncaughtExceptionHandler

    /**
     * Creates new instance of `ExceptionHandler` bound to given `Thread`.
     *
     * @param t
     * the `Thread` which will be handled.
     */
    init {
        parent = t.uncaughtExceptionHandler
        t.uncaughtExceptionHandler = this
    }

    /**
     * Marks the crash in `SharedPreferences` and kills the process.
     * Storage: /data/data/org.atalk.hmos/files/log/atalk-crash-logcat.txt
     *
     *
     * {@inheritDoc}
     */
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        markCrashedEvent()
        parent.uncaughtException(thread, ex)
        Timber.e(ex, "uncaughtException occurred, killing the process...")

        // Save logcat for more information.
        val logcatFile: File
        val logcatFN = File("log", "atalk-crash-logcat.txt").toString()
        try {
            logcatFile = ExceptionHandlerActivator.fileAccessService?.getPrivatePersistentFile(logcatFN, FileCategory.LOG)!!
            Runtime.getRuntime().exec("logcat -v time -f " + logcatFile.absolutePath)
        } catch (e: Exception) {
            Timber.e("Couldn't save crash logcat file.")
        }
        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    companion object {
        /**
         * Checks and attaches the `ExceptionHandler` if it hasn't been bound already.
         */
        @JvmStatic
        fun checkAndAttachExceptionHandler() {
            val current = Thread.currentThread()
            if (current.uncaughtExceptionHandler is ExceptionHandler) {
                return
            }
            // Creates and binds new handler instance
            ExceptionHandler(current)
        }

        /**
         * Returns `SharedPreferences` used to mark the crash event.
         *
         * @return `SharedPreferences` used to mark the crash event.
         */
        private val storage: SharedPreferences
            private get() = aTalkApp.globalContext.getSharedPreferences("crash", Context.MODE_PRIVATE)

        /**
         * Marks that the crash has occurred in `SharedPreferences`.
         */
        private fun markCrashedEvent() {
            storage.edit().putBoolean("crash", true).apply()
        }

        /**
         * Returns `true` if Jitsi crash was detected.
         *
         * @return `true` if Jitsi crash was detected.
         */
        @JvmStatic
        fun hasCrashed(): Boolean {
            return storage.getBoolean("crash", false)
        }

        /**
         * Clears the "crashed" flag.
         */
        @JvmStatic
        fun resetCrashedStatus() {
            storage.edit().putBoolean("crash", false).apply()
        }
    }
}