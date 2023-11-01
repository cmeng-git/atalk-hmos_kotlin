/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import java.util.logging.Level
import java.util.logging.LogManager

/**
 * Standard logging methods.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class Logger
/**
 * Base constructor
 *
 * @param logger the implementation specific logger delegate that this
 * Logger instance should be created around.
 */ private constructor(
        /**
         * The java.util.Logger that would actually be doing the logging.
         */
        private val loggerDelegate: java.util.logging.Logger) {
    /**
     * Logs an entry in the calling method.
     */
    fun logEntry() {
        if (loggerDelegate.isLoggable(Level.FINEST)) {
            val caller = Throwable().stackTrace[1]
            loggerDelegate.log(Level.FINEST, "[entry] " + caller.methodName)
        }
    }

    /**
     * Logs exiting the calling method
     */
    fun logExit() {
        if (loggerDelegate.isLoggable(Level.FINEST)) {
            val caller = Throwable().stackTrace[1]
            loggerDelegate.log(Level.FINEST, "[exit] " + caller.methodName)
        }
    }

    /**
     * Check if a message with a TRACE level would actually be logged by this logger.
     *
     *
     * @return true if the TRACE level is currently being logged
     */
    val isTraceEnabled: Boolean
        get() = loggerDelegate.isLoggable(Level.FINER)

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
        loggerDelegate.finer(msg?.toString() ?: "null")
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param   t   Throwable associated with log message.
     */
    fun trace(msg: Any?, t: Throwable?) {
        loggerDelegate.log(Level.FINER, msg?.toString() ?: "null", t)
    }

    /**
     * Check if a message with a DEBUG level would actually be logged by this
     * logger.
     *
     *
     * @return true if the DEBUG level is currently being logged
     */
    val isDebugEnabled: Boolean
        get() = loggerDelegate.isLoggable(Level.FINE)

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
        loggerDelegate.fine(msg?.toString() ?: "null")
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg    The message to log
     * @param t  Throwable associated with log message.
     */
    fun debug(msg: Any?, t: Throwable?) {
        loggerDelegate.log(Level.FINE, msg?.toString() ?: "null", t)
    }

    /**
     * Check if a message with an INFO level would actually be logged by this
     * logger.
     *
     * @return true if the INFO level is currently being logged
     */
    val isInfoEnabled: Boolean
        get() = loggerDelegate.isLoggable(Level.INFO)

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
        loggerDelegate.info(msg?.toString() ?: "null")
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun info(msg: Any?, t: Throwable?) {
        loggerDelegate.log(Level.INFO, msg?.toString() ?: "null", t)
    }

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
        loggerDelegate.warning(msg?.toString() ?: "null")
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun warn(msg: Any?, t: Throwable?) {
        loggerDelegate.log(Level.WARNING, msg?.toString() ?: "null", t)
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
        loggerDelegate.severe(msg?.toString() ?: "null")
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun error(msg: Any?, t: Throwable?) {
        loggerDelegate.log(Level.SEVERE, msg?.toString() ?: "null", t)
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
        loggerDelegate.severe(msg?.toString() ?: "null")
    }

    /**
     * Log a message, with associated Throwable information.
     *
     *
     * @param msg   The message to log
     * @param t  Throwable associated with log message.
     */
    fun fatal(msg: Any?, t: Throwable?) {
        loggerDelegate.log(Level.SEVERE, msg?.toString() ?: "null", t)
    }

    /**
     * Set logging level for all handlers to FATAL
     */
    fun setLevelFatal() {
        setLevel(Level.SEVERE)
    }

    /**
     * Set logging level for all handlers to ERROR
     */
    fun setLevelError() {
        setLevel(Level.SEVERE)
    }

    /**
     * Set logging level for all handlers to WARNING
     */
    fun setLevelWarn() {
        setLevel(Level.WARNING)
    }

    /**
     * Set logging level for all handlers to INFO
     */
    fun setLevelInfo() {
        setLevel(Level.INFO)
    }

    /**
     * Set logging level for all handlers to DEBUG
     */
    fun setLevelDebug() {
        setLevel(Level.FINE)
    }

    /**
     * Set logging level for all handlers to TRACE
     */
    fun setLevelTrace() {
        setLevel(Level.FINER)
    }

    /**
     * Set logging level for all handlers to ALL (allow all log messages)
     */
    fun setLevelAll() {
        setLevel(Level.ALL)
    }

    /**
     * Set logging level for all handlers to OFF (allow no log messages)
     */
    fun setLevelOff() {
        setLevel(Level.OFF)
    }

    /**
     * Set logging level for all handlers to `level`
     *
     * @param level the level to set for all logger handlers
     */
    private fun setLevel(level: Level) {
        val handlers = loggerDelegate.handlers
        for (handler in handlers) handler.level = level
        loggerDelegate.level = level
    }

    /**
     * Reinitialize the logging properties and reread the logging configuration.
     *
     *
     * The same rules are used for locating the configuration properties
     * as are used at startup. So if the properties containing the log dir
     * locations have changed, we would read the new configuration.
     */
    fun reset() {
        try {
            FileHandler.Companion.pattern = null
            LogManager.getLogManager().reset()
            LogManager.getLogManager().readConfiguration()
        } catch (e: Exception) {
            loggerDelegate.log(Level.INFO, "Failed to reinit logger.", e)
        }
    }

    companion object {
        /**
         * Find or create a logger for the specified class.  If a logger has
         * already been created for that class it is returned.  Otherwise
         * a new logger is created.
         *
         *
         * If a new logger is created its log level will be configured
         * based on the logging configuration and it will be configured
         * to also send logging output to its parent's handlers.
         *
         *
         * @param clazz The creating class.
         *
         *
         * @return a suitable Logger
         * @throws NullPointerException if the name is null.
         */
        @Throws(NullPointerException::class)
        fun getLogger(clazz: Class<*>): Logger {
            return getLogger(clazz.name)
        }

        /**
         * Find or create a logger for a named subsystem.  If a logger has
         * already been created with the given name it is returned.  Otherwise
         * a new logger is created.
         *
         *
         * If a new logger is created its log level will be configured
         * based on the logging configuration and it will be configured
         * to also send logging output to its parent's handlers.
         *
         *
         * @param name A name for the logger. This should be a dot-separated name
         * and should normally be based on the class name of the creator, such as
         * "net.java.sip.communicator.MyFunnyClass"
         *
         *
         * @return a suitable Logger
         * @throws NullPointerException if the name is null.
         */
        @Throws(NullPointerException::class)
        fun getLogger(name: String?): Logger {
            return Logger(java.util.logging.Logger.getLogger(name))
        }
    }
}