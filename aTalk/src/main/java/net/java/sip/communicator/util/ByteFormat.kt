/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.text.DecimalFormat
import java.text.FieldPosition
import java.text.Format
import java.text.ParsePosition

/**
 * Acknowledgment: This file was originally provided by the Ignite Realtime community, and was part
 * of the Spark project (distributed under the terms of the LGPL).
 *
 *
 * A formatter for formatting byte sizes. For example, formatting 12345 bytes results in "12.1 K"
 * and 1234567 results in "1.18 MB".
 *
 * @author Bill Lynch
 * @author Eng Chong Meng
 */
class ByteFormat : Format() {
    /**
     * Format the given object (must be a Long).
     *
     * @param obj assumed to be the number of bytes as a Long.
     * @param buf the StringBuffer to append to.
     * @param pos field position.
     * @return A formatted string representing the given bytes in more human-readable form.
     */
    override fun format(obj: Any, buf: StringBuffer, pos: FieldPosition): StringBuffer {
        if (obj is Long) {
            val numBytes = obj
            if (numBytes < 1024) {
                val formatter = DecimalFormat("#,##0")
                buf.append(formatter.format(numBytes.toDouble())).append(" bytes")
            } else if (numBytes < 1024 * 1024) {
                val formatter = DecimalFormat("#,##0.0")
                buf.append(formatter.format(numBytes / 1024.0)).append(" KB")
            } else if (numBytes < 1024 * 1024 * 1024) {
                val formatter = DecimalFormat("#,##0.0")
                buf.append(formatter.format(numBytes / (1024.0 * 1024.0))).append(" MB")
            } else {
                val formatter = DecimalFormat("#,##0.0")
                buf.append(formatter.format(numBytes / (1024.0 * 1024.0 * 1024.0))).append(" GB")
            }
        }
        return buf
    }

    /**
     * In this implementation, returns null always.
     *
     * @param source Source string to parse.
     * @param pos Position to parse from.
     * @return returns null in this implementation.
     */
    override fun parseObject(source: String, pos: ParsePosition): Any? {
        return null
    }

    companion object {
        private const val serialVersionUID: Long = 0

        /**
         * Formats a long which represent a number of bytes to human readable form.
         *
         * @param bytes the value to format
         * @return formatted string
         */
        fun format(bytes: Long): String? {
            var check = 1L

            // sizes
            val sufixes = arrayOf("", " bytes", " KB", " MB", " GB")
            for (i in 1..4) {
                val tempCheck = check * 1024
                if (bytes < tempCheck || i == 4) {
                    return DecimalFormat(if (check == 1L) "#,##0" else "#,##0.0").format(bytes.toDouble() / check) + sufixes[i]
                }
                check = tempCheck
            }
            // we are not suppose to come to here
            return null
        }
    }
}