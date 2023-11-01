package org.atalk.hmos.plugin.timberlog

import android.util.Log
import timber.log.Timber

/**
 * Debug tree log everything;.
 * Log everything i.e priority == (Log.VERBOSE || Log.DEBUG || Log.INFO || Log.WARN || Log.ERROR)
 * Log priority == TimberLevel.FINE only if enabled
 */
open class DebugTreeExt : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority != TimberLog.FINER || TimberLog.isTraceEnable

        // For testing release version logcat messages in debug mode
        // return (priority == Log.WARN || priority == Log.ERROR || priority == Log.ASSERT
        //        || (priority == Log.INFO && TimberLog.isInfoEnable));
    }

    /**
     * Must override log to print TimberLog.FINE properly by changing priority to Log.DEBUG
     * Log.println(priority, tag, message) would not print priority == TimberLog.FINE
     */
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == TimberLog.FINER || priority == TimberLog.FINEST) {
            println_native(0, priority, tag, message)
        } else {
            super.log(priority, tag, message, t)
        }
    }

    companion object {
        fun println_native(bufID: Int, priority: Int, tag: String?, msgs: String): Int {
            val prefix = priorityChar(priority).toString() + "/" + tag + ": "
            for (msg in msgs.split("\n".toRegex())) {
                println(prefix + msg)
            }
            return 0
        }

        // to replicate prefix visible when using 'adb logcat'
        private fun priorityChar(priority: Int): Char {
            return when (priority) {
                Log.VERBOSE -> 'V'
                Log.DEBUG -> 'D'
                Log.INFO -> 'I'
                Log.WARN -> 'W'
                Log.ERROR -> 'E'
                Log.ASSERT -> 'A'
                TimberLog.FINER -> 'T'
                TimberLog.FINEST -> 'S'
                else -> '?'
            }
        }
    }
}