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

import java.util.*
import java.util.logging.Level

/**
 * Standard logging methods.
 *
 * @author Emil Ivov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class Logger {
    /**
     * Logs an entry in the calling method.
     */
    fun logEntry() {
        if (isLoggable(Level.FINEST)) {
            val caller = Throwable().stackTrace[1]
            log(Level.FINEST, "[entry] " + caller.methodName)
        }
    }

    /**
     * Logs exiting the calling method
     */
    fun logExit() {
        if (isLoggable(Level.FINEST)) {
            val caller = Throwable().stackTrace[1]
            log(Level.FINEST, "[exit] " + caller.methodName)
        }
    }

    /**
     * Check if a message with a TRACE level would actually be logged by this
     * logger.
     *
     *
     * @return true if the TRACE level is currently being logged
     */
    val isTraceEnabled: Boolean
        get() = isLoggable(Level.FINER)

    /**
     * Log a TRACE message.
     *
     *
     * If the logger is currently enabled for the TRACE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     * @param msg The message to log
     */
    fun trace(msg: Any?) {
        log(Level.FINER, msg)
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param   t   Throwable associated with log message.
     */
    fun trace(msg: Any?, t: Throwable?) {
        log(Level.FINER, msg, t)
    }

    /**
     * Check if a message with a DEBUG level would actually be logged by this
     * logger.
     *
     *
     * @return true if the DEBUG level is currently being logged
     */
    val isDebugEnabled: Boolean
        get() = isLoggable(Level.FINE)

    /**
     * Log a DEBUG message.
     *
     *
     * If the logger is currently enabled for the DEBUG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     * @param msg The message to log
     */
    fun debug(msg: Any?) {
        log(Level.FINE, msg)
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg    The message to log
     * @param t  Throwable associated with log message.
     */
    fun debug(msg: Any?, t: Throwable?) {
        log(Level.FINE, msg, t)
    }

    /**
     * Check if a message with an INFO level would actually be logged by this
     * logger.
     *
     * @return true if the INFO level is currently being logged
     */
    val isInfoEnabled: Boolean
        get() = isLoggable(Level.INFO)

    /**
     * Log a INFO message.
     *
     *
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     * @param msg The message to log
     */
    fun info(msg: Any?) {
        log(Level.INFO, msg)
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun info(msg: Any?, t: Throwable?) {
        log(Level.INFO, msg, t)
    }

    /**
     * Check if a message with a WARN level would actually be logged by this
     * logger.
     *
     *
     * @return true if the WARN level is currently being logged
     */
    val isWarnEnabled: Boolean
        get() = isLoggable(Level.WARNING)

    /**
     * Log a WARN message.
     *
     *
     * If the logger is currently enabled for the WARN message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     * @param msg The message to log
     */
    fun warn(msg: Any?) {
        log(Level.WARNING, msg)
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun warn(msg: Any?, t: Throwable?) {
        log(Level.WARNING, msg, t)
    }

    /**
     * Log a ERROR message.
     *
     *
     * If the logger is currently enabled for the ERROR message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     * @param msg The message to log
     */
    fun error(msg: Any?) {
        log(Level.SEVERE, msg)
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun error(msg: Any?, t: Throwable?) {
        log(Level.SEVERE, msg, t)
    }

    /**
     * Log a FATAL message.
     *
     *
     * If the logger is currently enabled for the FATAL message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     * @param msg The message to log
     */
    fun fatal(msg: Any?) {
        error(msg)
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun fatal(msg: Any?, t: Throwable?) {
        error(msg, t)
    }

    /**
     * Set logging level for all handlers to FATAL
     */
    fun setLevelFatal() {
        level = Level.SEVERE
    }

    /**
     * Set logging level for all handlers to ERROR
     */
    fun setLevelError() {
        level = Level.SEVERE
    }

    /**
     * Set logging level for all handlers to WARNING
     */
    fun setLevelWarn() {
        level = Level.WARNING
    }

    /**
     * Set logging level for all handlers to INFO
     */
    fun setLevelInfo() {
        level = Level.INFO
    }

    /**
     * Set logging level for all handlers to DEBUG
     */
    fun setLevelDebug() {
        level = Level.FINE
    }

    /**
     * Set logging level for all handlers to TRACE
     */
    fun setLevelTrace() {
        level = Level.FINER
    }

    /**
     * Set logging level for all handlers to ALL (allow all log messages)
     */
    fun setLevelAll() {
        level = Level.ALL
    }

    /**
     * Set logging level for all handlers to OFF (allow no log messages)
     */
    fun setLevelOff() {
        level = Level.OFF
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     *
     *
     * The same rules are used for locating the configuration properties
     * as are used at startup. So if the properties containing the log dir
     * locations have changed, we would read the new configuration.
     */
    open fun reset() {}
    /**
     * @return the [Level] configured for this [Logger].
     */
    /**
     * Set logging level for all handlers to `level`
     *
     * @param level the level to set for all logger handlers
     */
    abstract var level: Level?

    /**
     * Checks whether messages with a particular level should be logged
     * according to the log level configured for this [Logger].
     * @param level the log level.
     */
    abstract fun isLoggable(level: Level?): Boolean

    /**
     * Logs a message at a given level, if that level is loggable according to
     * the log level configured by this instance.
     * @param level the level at which to log the message.
     * @param msg the message to log.
     */
    abstract fun log(level: Level?, msg: Any?)

    /**
     * Logs a message at a given level, if that level is loggable according to
     * the log level configured by this instance.
     * @param level the level at which to log the message.
     * @param msg the message to log.
     * @param thrown a [Throwable] associated with log message.
     */
    abstract fun log(level: Level?, msg: Any?, thrown: Throwable?)

    /**
     * Logs a given message with and given category at a given level, if that
     * level is loggable according to the log level configured by this instance.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * @param level the level at which to log the message.
     * @param category the category.
     * @param msg the message to log.
     */
    fun log(level: Level?, category: Category, msg: String) {
        Objects.requireNonNull(category, "category")
        log(level, category.prepend + msg)
    }

    /**
     * Logs a given message with and given category at a given level, if that
     * level is loggable according to the log level configured by this instance.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     * @param level the level at which to log the message.
     * @param category the category.
     * @param msg the message to log.
     * @param thrown a [Throwable] associated with log message.
     */
    fun log(
            level: Level?, category: Category,
            msg: String, thrown: Throwable?,
    ) {
        Objects.requireNonNull(category, "category")
        log(level, category.prepend + msg, thrown)
    }

    /**
     * Log a message with debug level.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     *
     *
     * @param msg The message to log
     * @param category the category.
     */
    fun debug(category: Category, msg: String) {
        log(Level.FINE, category, msg)
    }

    /**
     * Log a message with debug level, with associated Throwable information.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     *
     *
     * @param msg The message to log
     * @param category the category.
     * @param t  Throwable associated with log message.
     */
    fun debug(category: Category, msg: String, t: Throwable?) {
        log(Level.FINE, category, msg, t)
    }

    /**
     * Log a message with error level.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     *
     *
     * @param msg The message to log
     * @param category the category.
     */
    fun error(category: Category, msg: String) {
        log(Level.SEVERE, category, msg)
    }

    /**
     * Log a message with error level, with associated Throwable information.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     *
     *
     * @param msg The message to log
     * @param category the category.
     * @param t Throwable associated with log message.
     */
    fun error(category: Category, msg: String, t: Throwable?) {
        log(Level.SEVERE, category, msg, t)
    }

    /**
     * Log a message with info level, with associated Throwable information.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     *
     *
     * @param category the category.
     * @param msg The message to log
     * @param t Throwable associated with log message.
     */
    fun info(category: Category, msg: String, t: Throwable?) {
        log(Level.INFO, category, msg, t)
    }

    /**
     * Log a message with info level.
     * An identifier of the category in the form of CAT=name will will simply
     * be prepended to the message.
     *
     *
     * @param category the category.
     * @param msg The message to log
     */
    fun info(category: Category, msg: String) {
        log(Level.INFO, category, msg)
    }

    /**
     * An enumeration of different categories for log messages.
     */
    /**
     * The short string which identifies the category and is added to
     * messages logged with this category.
     */
    enum class Category(val descriptor: String) {
        /**
         * A category for log messages containing statistics.
         */
        STATISTICS("stat"),

        /**
         * A category for messages which needn't be stored.
         */
        VOLATILE("vol");

        /**
         * The string to prepend to messages with this category.
         */
        val prepend = "CAT=$name "

        /**
         * {@inheritDoc}
         * @return
         */
        override fun toString(): String {
            return descriptor
        }
    }

    companion object {
        /**
         * Create a logger for the specified class.
         *
         *
         * @param clazz The class for which to create a logger.
         *
         *
         * @return a suitable Logger
         * @throws NullPointerException if the class is null.
         */
        @JvmStatic
        @Throws(NullPointerException::class)
        fun getLogger(clazz: Class<*>): Logger {
            return getLogger(clazz.name)
        }

        /**
         * Create a logger for the specified name.
         *
         *
         * @return a suitable Logger
         * @throws NullPointerException if the name is null.
         */
        @JvmStatic
        @Throws(NullPointerException::class)
        fun getLogger(name: String): Logger {
            return LoggerImpl(java.util.logging.Logger.getLogger(name))
        }

        /**
         * Creates a new [Logger] instance which performs logging through
         * `loggingDelegate` and uses `levelDelegate` to configure its
         * level.
         * @param loggingDelegate the [Logger] used for logging.
         * @param levelDelegate the [Logger] used for configuring the log
         * level.
         */
        fun getLogger(loggingDelegate: Logger, levelDelegate: Logger?): Logger {
            return InstanceLogger(loggingDelegate, levelDelegate)
        }
    }
}