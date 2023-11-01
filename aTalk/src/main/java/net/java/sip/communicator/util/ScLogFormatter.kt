/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Boolean
import java.text.DecimalFormat
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogManager
import java.util.logging.LogRecord
import kotlin.Exception
import kotlin.Int
import kotlin.String
import kotlin.Throwable

/**
 * Print a brief summary of the LogRecord in a human readable. The summary will
 * typically be on a single line (unless it's too long :) ... what I meant to
 * say is that we don't add any line breaks).
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ScLogFormatter : Formatter() {
    /**
     * The default constructor for `ScLogFormatter` which loads
     * program name property from logging.properties file, if it exists
     */
    init {
        loadConfigProperties()
    }

    /**
     * Format the given LogRecord.
     *
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    @Synchronized
    override fun format(record: LogRecord): String {
        val sb = StringBuilder()
        if (programName != null) {
            // Program name
            sb.append(programName)
            sb.append(' ')
        }
        if (!timestampDisabled) {
            //current time
            val cal = Calendar.getInstance()
            val year = cal[Calendar.YEAR]
            val month = cal[Calendar.MONTH] + 1
            val day = cal[Calendar.DAY_OF_MONTH]
            val hour = cal[Calendar.HOUR_OF_DAY]
            val minutes = cal[Calendar.MINUTE]
            val seconds = cal[Calendar.SECOND]
            val millis = cal[Calendar.MILLISECOND]
            sb.append(year).append('-')
            sb.append(twoDigFmt.format(month.toLong())).append('-')
            sb.append(twoDigFmt.format(day.toLong())).append(' ')
            sb.append(twoDigFmt.format(hour.toLong())).append(':')
            sb.append(twoDigFmt.format(minutes.toLong())).append(':')
            sb.append(twoDigFmt.format(seconds.toLong())).append('.')
            sb.append(threeDigFmt.format(millis.toLong())).append(' ')
        }

        //log level
        sb.append(record.level.localizedName)
        sb.append(": ")

        // Thread ID
        sb.append("[" + record.threadID + "] ")

        //caller method
        val lineNumber = inferCaller(record)
        var loggerName = record.loggerName
        if (loggerName == null) loggerName = record.sourceClassName
        if (loggerName!!.startsWith("net.java.sip.communicator.")) {
            sb.append(loggerName.substring("net.java.sip.communicator.".length))
        } else sb.append(record.loggerName)
        if (record.sourceMethodName != null) {
            sb.append(".")
            sb.append(record.sourceMethodName)

            //include the line number if we have it.
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
     * @param record the logrecord where class and method name should be stored.
     * @return the line number that the call was made from in the caller.
     */
    private fun inferCaller(record: LogRecord): Int {
        // Get the stack trace.
        val stack = Throwable().stackTrace

        //the line number that the caller made the call from
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

    /**
     * Loads all config properties.
     */
    private fun loadConfigProperties() {
        loadProgramNameProperty()
        loadTimestampDisabledProperty()
    }

    companion object {
        /**
         * Program name logging property name
         */
        private const val PROGRAM_NAME_PROPERTY = ".programname"

        /**
         * Disable timestamp logging property name.
         */
        private const val DISABLE_TIMESTAMP_PROPERTY = ".disableTimestamp"

        /**
         * Line separator used by current platform
         */
        private val lineSeparator = System.getProperty("line.separator")

        /**
         * Two digit `DecimalFormat` instance, used to format datetime
         */
        private val twoDigFmt = DecimalFormat("00")

        /**
         * Three digit `DecimalFormat` instance, used to format datetime
         */
        private val threeDigFmt = DecimalFormat("000")

        /**
         * The application name used to generate this log
         */
        private var programName: String? = null

        /**
         * Whether logger will add date to the logs, enabled by default.
         */
        private var timestampDisabled = false

        /**
         * Checks and loads timestamp disabled property if any.
         */
        private fun loadTimestampDisabledProperty() {
            val manager = LogManager.getLogManager()
            val cname = ScLogFormatter::class.java.name
            timestampDisabled = Boolean.parseBoolean(manager.getProperty(cname + DISABLE_TIMESTAMP_PROPERTY))
        }

        /**
         * Load the programname property to be used in logs to identify Jitsi-based
         * application which produced the logs
         */
        private fun loadProgramNameProperty() {
            val manager = LogManager.getLogManager()
            val cname = ScLogFormatter::class.java.name
            programName = manager.getProperty(cname + PROGRAM_NAME_PROPERTY)
        }
    }
}