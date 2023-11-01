/*
 * Copyright @ 2018 Atlassian Pty Ltd
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

import org.json.JSONObject

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class TimeSeriesLogger
/**
 * Ctor.
 */
(
        /**
         * The Java logger that's going to output the time series points.
         */
        private val logger: Logger) {
    /**
     * Check if a message with a TRACE level would actually be logged by this logger.
     *
     * @return true if the TRACE level is currently being logged, otherwise false.
     */
    val isTraceEnabled: Boolean
        get() = logger.isTraceEnabled

    /**
     * Check if a message with a WARNING level would actually be logged by this logger.
     *
     * @return true if the WARNING level is currently being logged, otherwise false.
     */
    val isWarnEnabled: Boolean
        get() = logger.isWarnEnabled

    /**
     * Check if a message with a INFO level would actually be logged by this logger.
     *
     * @return true if the INFO level is currently being logged, otherwise false.
     */
    val isInfoEnabled: Boolean
        get() = logger.isInfoEnabled

    /**
     * Traces a [DiagnosticContext.TimeSeriesPoint].
     *
     * @param point the point to trace.
     */
    fun trace(point: Map<String?, Any?>?) {
        if (point != null && !point.isEmpty()) {
            logger.trace(JSONObject(point).toString())
        }
    }

    /**
     * Logs a [DiagnosticContext.TimeSeriesPoint] in WARNING level.
     *
     * @param point the point to log.
     */
    fun warn(point: Map<String?, Any?>?) {
        if (point != null && !point.isEmpty()) {
            logger.warn(JSONObject(point).toString())
        }
    }

    /**
     * Logs a [DiagnosticContext.TimeSeriesPoint] in INFO level.
     *
     * @param point the point to log.
     */
    fun info(point: Map<String?, Any?>?) {
        if (point != null && !point.isEmpty()) {
            logger.info(JSONObject(point).toString())
        }
    }

    companion object {
        /**
         * Create a logger for the specified class.
         *
         * @param clazz The class for which to create a logger.
         *
         * @return a suitable Logger
         * @throws NullPointerException if the class is null.
         */
        @Throws(NullPointerException::class)
        fun getTimeSeriesLogger(clazz: Class<*>): TimeSeriesLogger {
            val name = "timeseries." + clazz.name
            val logger = Logger.getLogger(name)
            return TimeSeriesLogger(logger)
        }
    }
}