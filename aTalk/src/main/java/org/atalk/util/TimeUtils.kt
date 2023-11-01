/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util

/**
 * Provides utility methods for converting between different time formats.
 *
 * Two of the methods are taken from the Apache Commons Net package, and are
 * copied here to avoid pulling in the whole package as a dependency.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
object TimeUtils {
    /**
     * Taken from org.apache.commons.net.ntp.TimeStamp.
     * baseline NTP time if bit-0=0 is 7-Feb-2036 @ 06:28:16 UTC
     */
    internal const val msb0baseTime = 2085978496000L

    /**
     * Taken from org.apache.commons.net.ntp.TimeStamp.
     * baseline NTP time if bit-0=1 is 1-Jan-1900 @ 01:00:00 UTC
     */
    internal const val msb1baseTime = -2208988800000L

    /**
     * Taken from from org.apache.commons.net.ntp.TimeStamp#toNtpTime(long)
     * cmeng; 20180924 - the ntp format return is incompatible for use in RTT calculation
     * @see .toNtpTime
     * @param t Java time
     * @return NTP timestamp representation of Java time value.
     */
    fun toNtpTime_error(t: Long): Long {
        val useBase1 = t < msb0baseTime // time < Feb-2036
        val baseTime: Long
        baseTime = if (useBase1) {
            t - msb1baseTime // dates <= Feb-2036
        } else {
            // if base0 needed for dates >= Feb-2036
            t - msb0baseTime
        }
        var seconds = baseTime / 1000
        val fraction = baseTime % 1000 * 0x100000000L / 1000
        if (useBase1) {
            seconds = seconds or 0x80000000L // set high-order bit if msb1baseTime 1900 used
        }
        return seconds shl 32 or fraction
    }

    // This will returns the correct NTP time in RTT calculation (cmeng)
    fun toNtpTime(baseTime: Long): Long {
        val seconds = baseTime / 1000
        val fraction = baseTime % 1000 * 0x100000000L / 1000
        return seconds shl 32 or fraction
    }

    fun toNtpShort(t: Long): Long {
        val secs = t / 1000L
        val ntptimestamplsw = t % 1000 * 0x100000000L / 1000
        return secs shl 16 or (ntptimestamplsw ushr 16) and 0x0ffffffffL
    }

    /**
     * Taken from from org.apache.commons.net.ntp.TimeStamp#toNtpTime(long)
     * Convert 64-bit NTP timestamp to Java standard time.
     *
     * Note that java time (milliseconds) by definition has less precision
     * then NTP time (picoseconds) so converting NTP timestamp to java time and back
     * to NTP timestamp loses precision. For example, Tue, Dec 17 2002 09:07:24.810 EST
     * is represented by a single Java-based time value of f22cd1fc8a, but its
     * NTP equivalent are all values ranging from c1a9ae1c.cf5c28f5 to c1a9ae1c.cf9db22c.
     *
     * @param ntpTimeValue the input time
     * @return the number of milliseconds since January 1, 1970, 00:00:00 GMT
     * represented by this NTP timestamp value.
     */
    fun getTime(ntpTimeValue: Long): Long {
        val seconds = ntpTimeValue ushr 32 and 0xffffffffL // high-order 32-bits
        var fraction = ntpTimeValue and 0xffffffffL // low-order 32-bits

        // Use round-off on fractional part to preserve going to lower precision
        fraction = Math.round(1000.0 * fraction / 0x100000000L)

        /*
         * If the most significant bit (MSB) on the seconds field is set we use
         * a different time base. The following text is a quote from RFC-2030
         * (SNTP v4):
         *
         *  If bit 0 is set, the UTC time is in the range 1968-2036 and UTC time
         *  is reckoned from 0h 0m 0s UTC on 1 January 1900. If bit 0 is not
         * set, the time is in the range 2036-2104 and UTC time is reckoned
         * from 6h 28m 16s UTC on 7 February 2036.
         */
        val msb = seconds and 0x80000000L
        return if (msb == 0L) {
            // use base: 7-Feb-2036 @ 06:28:16 UTC
            msb0baseTime + seconds * 1000 + fraction
        } else {
            // use base: 1-Jan-1900 @ 01:00:00 UTC
            msb1baseTime + seconds * 1000 + fraction
        }
    }

    /**
     * Converts the given timestamp in NTP Timestamp Format into NTP Short
     * Format (see []//tools.ietf.org/html/rfc5905.section-6"">&quot;https://tools.ietf.org/html/rfc5905#section-6&quot;).
     *
     * @param ntpTime the timestamp to convert.
     * @return the NTP Short Format timestamp, represented as a long.
     */
    fun toNtpShortFormat(ntpTime: Long): Long {
        return ntpTime and 0x0000FFFFFFFF0000L ushr 16
    }

    /**
     * Converts a timestamp in NTP Short Format (Q16.16, see
     * []//tools.ietf.org/html/rfc5905.section-6"">&quot;https://tools.ietf.org/html/rfc5905#section-6&quot;) into milliseconds.
     *
     * @param ntpShortTime the timestamp in NTP Short Format to convert.
     * @return the number of milliseconds.
     */
    fun ntpShortToMs(ntpShortTime: Long): Long {
        return ntpShortTime * 1000L ushr 16
    }

    /**
     * Constructs a `long` representation of a timestamp in NTP Timestamp
     * Format (see []//tools.ietf.org/html/rfc5905.section-6"">&quot;https://tools.ietf.org/html/rfc5905#section-6&quot;).
     *
     * @param msw The most significant word (32bits) represented as a long.
     * @param lsw The least significant word (32bits) represented as a long.
     * @return the NTP timestamp constructed from `msw` and `lsw`.
     */
    fun constructNtp(msw: Long, lsw: Long): Long {
        return msw shl 32 or (lsw and 0xFFFFFFFFL)
    }

    /**
     * Gets the most significant word (32bits) from an NTP Timestamp represented as a long.
     *
     * @param ntpTime the timestamp in NTP Timestamp Format.
     * @return the MSW of `ntpTime`.
     */
    fun getMsw(ntpTime: Long): Long {
        return ntpTime ushr 32 and 0xFFFFFFFFL
    }

    /**
     * Gets the least significant word (32bits) from an NTP Timestamp represented as a long.
     *
     * @param ntpTime the timestamp in NTP Timestamp Format.
     * @return the LSW of `ntpTime`.
     */
    fun getLsw(ntpTime: Long): Long {
        return ntpTime and 0xFFFFFFFFL
    } // Required android-O (API-26) - cannot be used for aTalk (API-21 min)
    //    /**
    //     * Format string for formatTimeAsFullMillis to print milliseconds-per-second
    //     */
    //    @TargetApi(Build.VERSION_CODES.O)
    //    // DecimalFormat is NOT thread safe!
    //    private static final ThreadLocal<DecimalFormat> trailingMilliFormat
    //            = ThreadLocal.withInitial(() -> new DecimalFormat("000"));
    //
    //    /**
    //     * Format string for formatTimeAsFullMillis to print nanoseconds-per-millisecond
    //     */
    //    @TargetApi(Build.VERSION_CODES.O)
    //    private static final ThreadLocal<DecimalFormat> nanosPerMilliFormat
    //            = ThreadLocal.withInitial(() -> {
    //        DecimalFormatSymbols dfs = new DecimalFormatSymbols();
    //        dfs.setDecimalSeparator('.');
    //        return new DecimalFormat(".######", dfs);
    //    });
    //
    //    /**
    //     * Formats a time -- represented by (long seconds, int nanos) -- as
    //     * a String of floating-point milliseconds, in full precision.
    //     *
    //     * This is designed to format the java.time.Duration and java.time.Interval
    //     * classes, without being dependent on them.
    //     *
    //     * This should return a correct result for every valid (secs, nanos) pair.
    //     */
    //    @TargetApi(Build.VERSION_CODES.O)
    //    public static String formatTimeAsFullMillis(long secs, int nanos)
    //    {
    //        assert (nanos >= 0 && nanos < 1_000_000_000);
    //
    //        StringBuilder builder = new StringBuilder();
    //
    //        if (secs < 0 && nanos != 0) {
    //            secs = -secs - 1;
    //            nanos = 1_000_000_000 - nanos;
    //            builder.append('-');
    //        }
    //
    //        int millis = nanos / 1_000_000;
    //        int nanosPerMilli = nanos % 1_000_000;
    //
    //        if (secs != 0) {
    //            builder.append(secs);
    //            builder.append(trailingMilliFormat.get().format(millis));
    //        }
    //        else {
    //            builder.append(millis);
    //        }
    //        if (nanosPerMilli != 0) {
    //            builder.append(nanosPerMilliFormat.get().format(nanosPerMilli / 1e6));
    //        }
    //
    //        return builder.toString();
    //    }
}