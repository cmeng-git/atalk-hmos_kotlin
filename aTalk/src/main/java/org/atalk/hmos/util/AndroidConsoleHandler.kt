/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.util

import android.util.Log
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord

/**
 * Android console handler that outputs to `android.util.Log`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidConsoleHandler : Handler() {
    /**
     * Property indicates whether logger should translate logging levels to logcat levels.
     */
    private var useAndroidLevels = true

    init {
        // TODO: failed to set formatter through the properties
        formatter = AndroidLogFormatter()
        useAndroidLevels = isUseAndroidLevels
    }

    override fun close() {}
    override fun flush() {}
    override fun publish(record: LogRecord) {
        try {
            if (isLoggable(record)) {
                val msg = formatter.format(record)
                if (!useAndroidLevels) {
                    Log.w(TAG, msg)
                } else {
                    val level = record.level
                    if (level === Level.INFO) {
                        Log.i(TAG, msg)
                    } else if (level === Level.SEVERE) {
                        Log.e(TAG, msg)
                    } else if (level === Level.FINE || level === Level.FINER) {
                        Log.d(TAG, msg)
                    } else if (level === Level.FINEST) {
                        Log.v(TAG, msg)
                    } else {
                        Log.w(TAG, msg)
                    }
                }
            }
        } catch (e: Exception) {
            // What a Terrible Failure :)
            Log.wtf(TAG, "Error publishing log output", e)
        }
    }

    companion object {
        /**
         * Tag used for output(can be used to filter logcat).
         */
        private val TAG = aTalkApp.getResString(R.string.APPLICATION_NAME)
        val isUseAndroidLevels: Boolean
            get() {
                val property = LogManager.getLogManager()
                        .getProperty(AndroidConsoleHandler::class.java.name + ".useAndroidLevels")
                return property == null || property == "true"
            }
    }
}