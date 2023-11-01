/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * The `StringUtils` class is used through this ui implementation for
 * some special operations with strings.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Adam Netocny
 * @author Eng Chong Meng
 */
object GuiUtils {
    private val c1 = Calendar.getInstance()
    private val c2 = Calendar.getInstance()

    /**
     * Number of milliseconds in a second.
     */
    private const val MILLIS_PER_SECOND: Long = 1000

    /**
     * Number of milliseconds in a standard minute.
     */
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND

    /**
     * Number of milliseconds in a standard hour.
     */
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE

    /**
     * Number of milliseconds in a standard day.
     */
    private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

    // These mappings map a character (key) to a specific digit that should
    // replace it for normalization purposes. Non-European digits that may be
    // used in phone numbers are mapped to a European equivalent.
    private var DIGIT_MAPPINGS: Map<Char, Char>? = null

    /**
     * Characters and their replacement in created folder names
     */
    private val ESCAPE_SEQUENCES = arrayOf(arrayOf("&", "&_amp"), arrayOf("/", "&_sl"), arrayOf("\\\\", "&_bs"), arrayOf(":", "&_co"), arrayOf("\\*", "&_as"), arrayOf("\\?", "&_qm"), arrayOf("\"", "&_pa"), arrayOf("<", "&_lt"), arrayOf(">", "&_gt"), arrayOf("\\|", "&_pp"))

    init {
        val digitMap = HashMap<Char, Char>(50)
        digitMap['0'] = '0'
        digitMap['\uFF10'] = '0' // Fullwidth digit 0
        digitMap['\u0660'] = '0' // Arabic-indic digit 0
        digitMap['1'] = '1'
        digitMap['\uFF11'] = '1' // Fullwidth digit 1
        digitMap['\u0661'] = '1' // Arabic-indic digit 1
        digitMap['2'] = '2'
        digitMap['\uFF12'] = '2' // Fullwidth digit 2
        digitMap['\u0662'] = '2' // Arabic-indic digit 2
        digitMap['3'] = '3'
        digitMap['\uFF13'] = '3' // Fullwidth digit 3
        digitMap['\u0663'] = '3' // Arabic-indic digit 3
        digitMap['4'] = '4'
        digitMap['\uFF14'] = '4' // Fullwidth digit 4
        digitMap['\u0664'] = '4' // Arabic-indic digit 4
        digitMap['5'] = '5'
        digitMap['\uFF15'] = '5' // Fullwidth digit 5
        digitMap['\u0665'] = '5' // Arabic-indic digit 5
        digitMap['6'] = '6'
        digitMap['\uFF16'] = '6' // Fullwidth digit 6
        digitMap['\u0666'] = '6' // Arabic-indic digit 6
        digitMap['7'] = '7'
        digitMap['\uFF17'] = '7' // Fullwidth digit 7
        digitMap['\u0667'] = '7' // Arabic-indic digit 7
        digitMap['8'] = '8'
        digitMap['\uFF18'] = '8' // Fullwidth digit 8
        digitMap['\u0668'] = '8' // Arabic-indic digit 8
        digitMap['9'] = '9'
        digitMap['\uFF19'] = '9' // Fullwidth digit 9
        digitMap['\u0669'] = '9' // Arabic-indic digit 9
        DIGIT_MAPPINGS = Collections.unmodifiableMap(digitMap)
    }

    /**
     * Replaces some chars that are special in a regular expression.
     *
     * @param text The initial text.
     * @return the formatted text
     */
    fun replaceSpecialRegExpChars(text: String): String {
        return text.replace("([.()^&$*|])".toRegex(), "\\\\$1")
    }

    /**
     * Counts occurrences of the `needle` character in the given `text`.
     *
     * @param text the text in which we search
     * @param needle the character we're looking for
     * @return the count of occurrences of the `needle` chat in the given `text`
     */
    fun countOccurrences(text: String, needle: Char): Int {
        var count = 0
        for (c in text.toCharArray()) {
            if (c == needle) ++count
        }
        return count
    }

    /**
     * Compares the two dates. The comparison is based only on the day, month
     * and year values. Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is after
     * the second one.
     *
     * @param date1 the first date to compare
     * @param date2 the second date to compare with
     * @return Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is after
     * the second one
     */
    fun compareDates(date1: Date, date2: Date?): Int {
        return date1.compareTo(date2)
    }

    /**
     * Compares the two dates. The comparison is based only on the day, month
     * and year values. Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is after
     * the second one.
     *
     * @param date1 the first date to compare
     * @param date2 the second date to compare with
     * @return Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is after
     * the second one
     */
    fun compareDates(date1: Long, date2: Long): Int {
        return if (date1 < date2) -1 else if (date1 == date2) 0 else 1
    }

    /**
     * Compares the two dates. The comparison is based only on the day, month
     * and year values. Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is
     * after the second one.
     *
     * @param date1 the first date to compare
     * @param date2 the second date to compare with
     * @return Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is
     * after the second one
     */
    fun compareDatesOnly(date1: Long, date2: Long): Int {
        c1.timeInMillis = date1
        c2.timeInMillis = date2
        val day1 = c1[Calendar.DAY_OF_MONTH]
        val month1 = c1[Calendar.MONTH]
        val year1 = c1[Calendar.YEAR]
        val day2 = c2[Calendar.DAY_OF_MONTH]
        val month2 = c2[Calendar.MONTH]
        val year2 = c2[Calendar.YEAR]
        return if (year1 < year2) {
            -1
        } else if (year1 == year2) {
            if (month1 < month2) -1 else if (month1 == month2) {
                if (day1 < day2) -1 else if (day1 == day2) 0 else 1
            } else 1
        } else {
            1
        }
    }

    /**
     * Compares the two dates. The comparison is based only on the day, month
     * and year values. Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is
     * after the second one.
     *
     * @param date1 the first date to compare
     * @param date2 the second date to compare with
     * @return Returns 0 if the two dates are equals, a value < 0 if
     * the first date is before the second one and > 0 if the first date is
     * after the second one
     */
    fun compareDatesOnly(date1: Date, date2: Date): Int {
        return compareDatesOnly(date1.time, date2.time)
    }

    /**
     * Formats the given date. The result format is the following:
     * For example: Tue 29 Oct 2019 04:37:55 FMT+08:00
     *
     * @param date_ the date to format
     * @return the formatted date string
     */
    fun formatDateTime(date_: Date?): String {
        var date = date_
        if (date == null) date = Date()
        val pattern = "E, dd MMM yyyy HH:mm:ss z"
        val simpleDateFormat = SimpleDateFormat(pattern, Locale.US)
        return simpleDateFormat.format(date)

        /*
         * [Month] [Day], [Year] [Hour]:[Minute]:[Second].
         * Dec 24, 2000 12:25:30.
         */
        // long dateTime = date.getTime()
        // StringBuffer strBuf = new StringBuffer();
        // formatDate(dateTime, strBuf);
        // formatTime(dateTime);
        // return strBuf.append(" ").append(formatTime(dateTime)).toString();
    }

    fun formatDateTimeShort(date: Date): String {
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        return df.format(date)
    }

    /**
     * Formats the given date. The result format is the following:
     * Month Day, Year. For example: Dec 24, 2000.
     *
     * @param date the date to format
     * @return the formatted date string
     */
    fun formatDate(date: Date): String {
        return formatDate(date.time)
    }

    /**
     * Formats the given date. The result format is the following:
     * Month Day, Year. For example: Dec 24, 2000.
     *
     * @param date the date to format
     * @return the formatted date string
     */
    private fun formatDate(date: Long): String {
        val strBuf = StringBuffer()
        formatDate(date, strBuf)
        return strBuf.toString()
    }

    /**
     * Formats the given date as: Month DD, YYYY and appends it to the given
     * `dateStrBuf` string buffer.
     *
     * @param date the date to format
     * @param dateStrBuf the `StringBuffer`, where to append the
     * formatted date
     */
    private fun formatDate(date: Long, dateStrBuf: StringBuffer) {
        c1.timeInMillis = date
        dateStrBuf.append(processMonth(c1[Calendar.MONTH]))
        dateStrBuf.append(' ')
        formatTime(c1[Calendar.DAY_OF_MONTH], dateStrBuf)
        dateStrBuf.append(", ")
        formatTime(c1[Calendar.YEAR], dateStrBuf)
    }

    /**
     * Formats the given date as: Month DD, YYYY and appends it to the given `dateStrBuf` string buffer.
     *
     * @param date the date to format
     * @param dateStrBuf the `StringBuffer`, where to append the formatted date
     */
    fun formatDate(date: Date, dateStrBuf: StringBuffer) {
        c1.time = date
        dateStrBuf.append(processMonth(c1[Calendar.MONTH]))
        dateStrBuf.append(' ')
        formatTime(c1[Calendar.DAY_OF_MONTH], dateStrBuf)
        dateStrBuf.append(", ")
        formatTime(c1[Calendar.YEAR], dateStrBuf)
    }

    /**
     * Formats the time for the given date. The result format is the following:
     * Hour Minute:Second. For example: 12:25:30.
     *
     * @param date the date to format
     * @return the formatted hour string
     */
    fun formatTime(date: Date): String {
        return formatTime(date.time)
    }

    /**
     * Formats the time for the given date. The result format is the following:
     * Hour Minute:Second. For example: 12:25:30.
     *
     * @param time the date to format
     * @return the formatted hour string
     */
    fun formatTime(time: Long): String {
        c1.timeInMillis = time
        val timeStrBuf = StringBuffer()
        formatTime(c1[Calendar.HOUR_OF_DAY], timeStrBuf)
        timeStrBuf.append(':')
        formatTime(c1[Calendar.MINUTE], timeStrBuf)
        timeStrBuf.append(':')
        formatTime(c1[Calendar.SECOND], timeStrBuf)
        return timeStrBuf.toString()
    }

    /**
     * Formats the time period duration for the given start date and end date.
     * The result format is the following:
     * Hour Minute:Second. For example: 12:25:30.
     *
     * @param startDate the start date
     * @param endDate the end date
     * @return the formatted hour string
     */
    fun formatTime(startDate: Date, endDate: Date): String {
        return formatTime(startDate.time, endDate.time)
    }

    /**
     * Formats the time period duration for the given start date and end date.
     * The result format is the following:
     * Hour Minute:Second. For example: 12:25:30.
     *
     * @param start the start date in milliseconds
     * @param end the end date in milliseconds
     * @return the formatted hour string
     */
    fun formatTime(start: Long, end: Long): String {
        val duration = end - start
        val milPerSec = 1000L
        val milPerMin = milPerSec * 60
        val milPerHour = milPerMin * 60
        val hours = duration / milPerHour
        val minutes = (duration - hours * milPerHour) / milPerMin
        val seconds = (duration - hours * milPerHour - minutes * milPerMin) / milPerSec
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Gets the display/human-readable string representation of the month with
     * the specified zero-based month number.
     *
     * @param month the zero-based month number
     * @return the corresponding month abbreviation
     */
    private fun processMonth(month: Int): String {
        val monthStringKey = when (month) {
            0 -> "service.gui.JANUARY"
            1 -> "service.gui.FEBRUARY"
            2 -> "service.gui.MARCH"
            3 -> "service.gui.APRIL"
            4 -> "service.gui.MAY"
            5 -> "service.gui.JUNE"
            6 -> "service.gui.JULY"
            7 -> "service.gui.AUGUST"
            8 -> "service.gui.SEPTEMBER"
            9 -> "service.gui.OCTOBER"
            10 -> "service.gui.NOVEMBER"
            11 -> "service.gui.DECEMBER"
            else -> return ""
        }
        return UtilActivator.resources.getI18NString(monthStringKey)!!
    }

    /**
     * Adds a 0 in the beginning of one digit numbers.
     *
     * @param time The time parameter could be hours, minutes or seconds.
     * @param timeStrBuf the `StringBuffer` to which the formatted
     * minutes string is to be appended
     */
    private fun formatTime(time: Int, timeStrBuf: StringBuffer) {
        val timeString = time.toString()
        if (timeString.length < 2) timeStrBuf.append('0')
        timeStrBuf.append(timeString)
    }

    /**
     * Formats the given long to X hour, Y min, Z sec.
     *
     * @param millis the time in milliseconds to format
     * @return the formatted seconds
     */
    fun formatSeconds(millis: Long): String {
        val values = LongArray(4)
        values[0] = millis / MILLIS_PER_DAY
        values[1] = millis / MILLIS_PER_HOUR % 24
        values[2] = millis / MILLIS_PER_MINUTE % 60
        values[3] = millis / MILLIS_PER_SECOND % 60
        val fields = arrayOf(" d ", " h ", " min ", " sec")
        val buf = StringBuffer(64)
        var valueOutput = false
        for (i in 0..3) {
            val value = values[i]
            if (value == 0L) {
                if (valueOutput) buf.append('0').append(fields[i])
            } else {
                valueOutput = true
                buf.append(value).append(fields[i])
            }
        }
        return buf.toString().trim { it <= ' ' }
    }

    /**
     * Replaces the characters that we must escape used for the created
     * filename.
     *
     * @param string the `String` which is to have its characters escaped
     * @return a `String` derived from the specified `id` by
     * escaping characters
     */
    fun escapeFileNameSpecialCharacters(string: String): String {
        var resultId = string
        for (j in ESCAPE_SEQUENCES.indices) {
            resultId = resultId.replace(ESCAPE_SEQUENCES[j][0].toRegex(), ESCAPE_SEQUENCES[j][1])
        }
        return resultId
    }

    /**
     * Escapes special HTML characters such as &lt;, &gt;, &amp; and &quot; in the specified message.
     *
     * @param message the message to be processed
     * @return the processed message with escaped special HTML characters
     */
    fun escapeHTMLChars(message: String): String {
        return message
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;")
    }
}