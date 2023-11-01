/*
 * Copyright @ 2015 - Present 8x8, Inc
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

import java.util.logging.Level
import java.util.logging.LogManager

/**
 * Implements a [Logger] backed by a [java.util.logging.Logger].
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class LoggerImpl
/**
 * Base constructor
 *
 * @param logger the implementation specific logger delegate that this
 * Logger instance should be created around.
 */
(
        /**
         * The java.util.Logger that would actually be doing the logging.
         */
        private val loggerDelegate: java.util.logging.Logger) : Logger() {
    // Level.INFO.intValue(), but DOESN'T initialize the Level object.
    // So, if it hasn't been explicitly set, assume INFO.
    /**
     * Set logging level for all handlers to `level`
     *
     * @param level the level to set for all logger handlers
     */// OpenJDK's Logger implementation initializes its effective level value with
    /**
     * {@inheritDoc}
     */
    override var level: Level?
        get() {
            // OpenJDK's Logger implementation initializes its effective level value with
            // Level.INFO.intValue(), but DOESN'T initialize the Level object.
            // So, if it hasn't been explicitly set, assume INFO.
            val level = loggerDelegate.level
            return level ?: Level.INFO
        }
        set(level) {
            val handlers = loggerDelegate.handlers
            for (handler in handlers) handler.level = level
            loggerDelegate.level = level
        }

    /**
     * {@inheritDoc}
     */
    public override fun isLoggable(level: Level?): Boolean {
        return loggerDelegate.isLoggable(level)
    }

    /**
     * {@inheritDoc}
     */
    override fun log(level: Level?, msg: Any?) {
        loggerDelegate.log(level, msg?.toString() ?: "null")
    }

    /**
     * {@inheritDoc}
     */
    override fun log(level: Level?, msg: Any?, thrown: Throwable?) {
        loggerDelegate.log(level, msg?.toString() ?: "null", thrown)
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     *
     *
     * The same rules are used for locating the configuration properties
     * as are used at startup. So if the properties containing the log dir
     * locations have changed, we would read the new configuration.
     */
    override fun reset() {
        try {
            FileHandler.pattern = null
            LogManager.getLogManager().reset()
            LogManager.getLogManager().readConfiguration()
        } catch (e: Exception) {
            loggerDelegate.log(Level.INFO, "Failed to re-init logger.", e)
        }
    }
}