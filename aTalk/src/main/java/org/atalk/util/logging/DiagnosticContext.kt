/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.atalk.util.logging

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * A [DiagnosticContext] implementation backed by a
 * [ConcurrentHashMap].
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
class DiagnosticContext
/**
 * {@inheritDoc}
 */
// @SuppressLint("NewApi") // Required API-26
    : ConcurrentHashMap<String?, Any?>() {
    var clock: Clock? = null
    /**
     * Makes a new time series point with a timestamp. This is recommended for
     * time series where it's important to have the exact timestamp value.
     *
     * @param timeSeriesName the name of the time series
     * @param tsMs the timestamp of the time series point (in millis)
     */
    //    /**
    //     * Creates a diagnostic context using the specified clock for timestamp values.
    //     * @param clock providing access to the current instant, date and time using a time-zone.
    //     */
    //    public DiagnosticContext(Clock clock)
    //    {
    //        super();
    //        this.clock = clock;
    //    }
    /**
     * Makes a new time series point without a timestamp. This is recommended
     * for time series where the exact timestamp value isn't important and can
     * be deduced via other means (i.e. Java logging timestamps).
     *
     * @param timeSeriesName the name of the time series
     */
    @JvmOverloads
    fun makeTimeSeriesPoint(timeSeriesName: String?, tsMs: Long = -1L): TimeSeriesPoint {
        return TimeSeriesPoint(this)
                .addField("series", timeSeriesName)
                .addField("time", tsMs)
    }

    //    /**
    //     * Makes a new time series point with an Instant. This is recommended for
    //     * time series where it's important to have the exact timestamp value,
    //     * when the process is working in Instant values.
    //     *
    //     * @param timeSeriesName the name of the time series
    //     * @param ts the timestamp of the time series point
    //     */
    //    @SuppressLint("NewApi") // Required API-26
    //    public TimeSeriesPoint makeTimeSeriesPoint(String timeSeriesName, Instant ts)
    //    {
    //        String time;
    //        return new TimeSeriesPoint(this)
    //            .addField("series", timeSeriesName)
    //            // .addField("time", TimeUtils.formatTimeAsFullMillis(ts.getEpochSecond(), ts.getNano()));
    //    }
    class TimeSeriesPoint(m: Map<String?, Any?>?) : HashMap<String?, Any?>(m) {
        /**
         * Adds a field to the time series point.
         */
        fun addField(key: String?, value: Any?): TimeSeriesPoint {
            put(key, value)
            return this
        }
    }
}