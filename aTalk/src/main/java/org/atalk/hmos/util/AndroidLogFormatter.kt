/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.util

import java.io.PrintWriter
import java.io.StringWriter
import java.util.logging.Formatter
import java.util.logging.LogRecord

/**
 * Slightly modified `ScLogFormatter` to Android specifics.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidLogFormatter : Formatter() {
    private val useAndroidLevels = AndroidConsoleHandler.isUseAndroidLevels

    /**
     * Format the given LogRecord.
     *
     * @param record
     * the log record to be formatted.
     * @return a formatted log record
     */
    @Synchronized
    override fun format(record: LogRecord): String {
        val sb = StringBuilder()
        if (!useAndroidLevels) {
            // log level
            sb.append(record.level.localizedName)
            sb.append(": ")
        }

        // Thread ID
        sb.append("[").append(record.threadID).append("] ")

        // caller method
        val lineNumber = inferCaller(record)
        var loggerName = record.loggerName
        if (loggerName == null) loggerName = record.sourceClassName
        if (loggerName!!.startsWith("net.java.sip.communicator.")) {
            sb.append(loggerName.substring("net.java.sip.communicator.".length))
        } else sb.append(record.loggerName)
        if (record.sourceMethodName != null) {
            sb.append(".")
            sb.append(record.sourceMethodName)

            // include the line number if we have it.
            if (lineNumber != -1) sb.append("().").append(Integer.toString(lineNumber)) else sb.append("()")
        }
        sb.append(" ")
        sb.append(record.message)
        sb.append(lineSeparator)
        if (record.thrown != null) {
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                record.thrown.printStackTrace(pw)
                pw.close()
                sb.append(sw.toString())
            } catch (ex: Exception) {
            }
        }
        return sb.toString()
    }

    /**
     * Try to extract the name of the class and method that called the current log statement.
     *
     * @param record
     * the LogRecord where class and method name should be stored.
     *
     * @return the line number that the call was made from in the caller.
     */
    private fun inferCaller(record: LogRecord): Int {
        // Get the stack trace.
        val stack = Throwable().stackTrace

        // the line number that the caller made the call from
        var lineNumber = -1

        // First, search back to a method in the SIP Communicator Logger class.
        var ix = 0
        while (ix < stack.size) {
            val frame = stack[ix]
            val cname = frame.className
            if (cname == "net.java.sip.communicator.util.Logger") {
                break
            }
            ix++
        }
        // Now search for the first frame before the SIP Communicator Logger class.
        while (ix < stack.size) {
            val frame = stack[ix]
            lineNumber = stack[ix].lineNumber
            val cname = frame.className
            if (cname != "net.java.sip.communicator.util.Logger") {
                // We've found the relevant frame.
                record.sourceClassName = cname
                record.sourceMethodName = frame.methodName
                break
            }
            ix++
        }
        return lineNumber
    }

    companion object {
        private val lineSeparator = System.getProperty("line.separator")
    }
}