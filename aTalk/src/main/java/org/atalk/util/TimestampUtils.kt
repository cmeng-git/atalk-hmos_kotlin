/*
 * Copyright @ 2017 - present 8x8 Inc
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
 * Helper class to perform various timestamp manipulations and comparisons
 * @author Ethan Lin
 * @author Eng Chong Meng
 */
object TimestampUtils {
    // RTP timestamp is 32 bits, this represents the maximum value of an RTP timestamp
    const val MAX_TIMESTAMP_VALUE = Long.MAX_VALUE and 0xFFFFFFFFL

    // (Roughly) half of the MAX_TIMESTAMP_VALUE.  Used when we're trying to compare
    // two timestamps to determine which came 'first'.
    const val ROLLOVER_DELTA_VALUE = 0x80000000L

    /**
     * Calculate the subtraction result of two long input as unsigned 32bit int.
     *
     * @param t1 the first timestamp
     * @param t2 the second timestamp
     * @return
     */
    fun subtractAsUnsignedInt32(t1: Long, t2: Long): Long {
        return t1 - t2 and 0xFFFFFFFFL
    }

    /**
     * Returns true if t1 is newer than t2,
     * taking into account rollover.  This is done by effectively
     * checking if the distance of going from 't2' to
     * 't1' (strictly incrementing) is shorter or if going from
     * 't1' to 't2' (i.e. rolling over) is shorter.
     * webrtc/modules/include/module_common_types.h
     *
     * @param t1
     * @param t2
     * @return true if t1 is newer
     */
    fun isNewerTimestamp(t1: Long, t2: Long): Boolean {
        if (t1 == t2) {
            return false
        }
        // Distinguish between elements that are exactly ROLLOVER_DELTA_VALUE apart.
        // If t1 > t2 and |t1-t2| == ROLLOVER_DELTA_VALUE:
        // isNewerTimestamp(t1,t2) = true,
        // isNewerTimestamp(t2,t1) = false
        // rather than having:
        // isNewerTimestamp(t1,t2) = isNewerTimestamp(t2,t1) = false.
        return if (subtractAsUnsignedInt32(t1, t2) == ROLLOVER_DELTA_VALUE) {
            // The two timestamps are exactly ROLLOVER_DELTA_VALUE apart, so
            // we can't guess which is newer.  To break the tie, assume
            // the larger timestamp is newer.
            t1 > t2
        } else subtractAsUnsignedInt32(t1, t2) < ROLLOVER_DELTA_VALUE
    }

    /**
     * webrtc/modules/include/module_common_types.h
     *
     * @param timestamp1
     * @param timestamp2
     * @return
     */
    fun latestTimestamp(timestamp1: Long, timestamp2: Long): Long {
        return if (isNewerTimestamp(timestamp1, timestamp2)) timestamp1 else timestamp2
    }
}