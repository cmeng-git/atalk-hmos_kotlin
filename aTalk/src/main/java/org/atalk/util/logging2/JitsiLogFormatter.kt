/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util.logging2

import java.io.PrintWriter
import java.io.StringWriter
import java.text.DecimalFormat
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogManager
import java.util.logging.LogRecord

class JitsiLogFormatter : Formatter() {
    /**
     * The default constructor for <tt>JitsiLogFormatter</tt> which loads
     * program name property from logging.properties file, if it exists
     */
    init {
        loadConfigProperties()
    }

    /**
     * Format the given LogRecord.
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    @Synchronized
    override fun format(record: LogRecord): String {
        val sb = StringBuffer()
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
        sb.append("[").append(record.threadID).append("] ")
        if (record is ContextLogRecord) {
            val context = record.context
            if (context!!.isNotEmpty()) {
                sb.append(context).append(" ")
            }
        }

        //caller method
        val lineNumber = inferCaller(record)
        sb.append(record.sourceClassName)
        if (record.sourceMethodName != null) {
            sb.append(".")
            sb.append(record.sourceMethodName)

            //include the line number if we have it.
            if (lineNumber != -1) {
                sb.append("#").append(lineNumber)
            }
        }
        sb.append(": ")
        sb.append(record.message)
        sb.append(lineSeparator)
        if (record.thrown != null) {
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                record.thrown.printStackTrace(pw)
                pw.close()
                sb.append(sw.toString())
            } catch (ignore: RuntimeException) {
            }
        }
        return sb.toString()
    }

    /**
     * Try to extract the name of the class and method that called the current
     * log statement.
     *
     * @param record the logrecord where class and method name should be stored.
     *
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
            if (cname == "org.jitsi.utils.logging2.LoggerImpl" || cname == "org.jitsi.utils.logging.LoggerImpl") {
                break
            }
            ix++
        }
        // Now search for the first frame
        // before the SIP Communicator Logger class.
        while (ix < stack.size) {
            val frame = stack[ix]
            lineNumber = stack[ix].lineNumber
            val cname = frame.className
            val shortName = cname.substring(cname.lastIndexOf(".") + 1)
            if (!cname.contains("org.jitsi.utils.logging")) {
                // We've found the relevant frame.
                record.sourceClassName = shortName
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
         * Two digit <tt>DecimalFormat</tt> instance, used to format datetime
         */
        private val twoDigFmt = DecimalFormat("00")

        /**
         * Three digit <tt>DecimalFormat</tt> instance, used to format datetime
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
            val cname = JitsiLogFormatter::class.java.name
            timestampDisabled = manager.getProperty(cname + DISABLE_TIMESTAMP_PROPERTY).toBoolean()
        }

        /**
         * Load the programname property to be used in logs to identify Jitsi-based
         * application which produced the logs
         */
        private fun loadProgramNameProperty() {
            val manager = LogManager.getLogManager()
            val cname = JitsiLogFormatter::class.java.name
            programName = manager.getProperty(cname + PROGRAM_NAME_PROPERTY)
        }
    }
}