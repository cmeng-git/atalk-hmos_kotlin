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

import java.util.function.Supplier
import java.util.logging.Handler
import java.util.logging.Level

interface Logger {
    /**
     * Create a 'child' logger which derives from this one.  The child logger
     * will share the same log level setting as this one and its
     * [LogContext] (given here) will, in addition to the values
     * it contains, have the parent logger's context merged into it (the child's
     * context values take priority in case of a conflict)
     *
     * @return the created logger
     */
    fun createChildLogger(name: String, context: Map<String, String>): Logger

    /**
     * Create a 'child' logger which derives from this one.  The child logger
     * will share the same log level setting as this one and it will inherit
     * this logger's [LogContext]
     *
     * @return the created logger
     */
    fun createChildLogger(name: String?): Logger

    /**
     * See [java.util.logging.Logger.setUseParentHandlers]
     */
    fun setUseParentHandlers(useParentHandlers: Boolean)

    /**
     * See [java.util.logging.Logger.addHandler]
     */
    @Throws(SecurityException::class)
    fun addHandler(handler: Handler)

    /**
     * See [java.util.logging.Logger.removeHandler]
     */
    @Throws(SecurityException::class)
    fun removeHandler(handler: Handler)

    /**
     * Check if a message with a TRACE level would actually be logged by this
     * logger.
     *
     *
     *
     * @return true if the TRACE level is currently being logged
     */
    val isTraceEnabled: Boolean

    /**
     * Log a TRACE message.
     *
     *
     * If the logger is currently enabled for the TRACE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     *
     * @param msg The message to log
     */
    fun trace(msg: Any)

    /**
     * Log a TRACE message.  Only invokes the given supplier
     * if the TRACE level is currently loggable.
     *
     * @param msgSupplier a [Supplier] which will return the
     * log mesage when invoked
     */
    fun trace(msgSupplier: Supplier<String>)

    /**
     * Check if a message with a DEBUG level would actually be logged by this
     * logger.
     *
     *
     *
     * @return true if the DEBUG level is currently being logged
     */
    val isDebugEnabled: Boolean

    /**
     * Log a DEBUG message.
     *
     *
     * If the logger is currently enabled for the DEBUG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     *
     * @param msg The message to log
     */
    fun debug(msg: Any)

    /**
     * Log a DEBUG message.  Only invokes the given supplier
     * if the DEBUG level is currently loggable.
     *
     * @param msgSupplier a [Supplier] which will return the log mesage when invoked
     */
    fun debug(msgSupplier: Supplier<String>)

    /**
     * Check if a message with an INFO level would actually be logged by this logger.
     *
     * @return true if the INFO level is currently being logged
     */
    val isInfoEnabled: Boolean

    /**
     * Log an INFO message.
     *
     *
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     *
     * @param msg The message to log
     */
    fun info(msg: Any)

    /**
     * Log an INFO message.  Only invokes the given supplier
     * if the INFO level is currently loggable.
     *
     * @param msgSupplier a [Supplier] which will return the log mesage when invoked
     */
    fun info(msgSupplier: Supplier<String>)

    /**
     * Check if a message with a WARN level would actually be logged by this logger.
     *
     * @return true if the WARN level is currently being logged
     */
    val isWarnEnabled: Boolean

    /**
     * Log a WARN message.
     *
     *
     * If the logger is currently enabled for the WARN message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     *
     * @param msg The message to log
     */
    fun warn(msg: Any)

    /**
     * Log a WARN message.  Only invokes the given supplier
     * if the WARN level is currently loggable.
     *
     * @param msgSupplier a [Supplier] which will return the log mesage when invoked
     */
    fun warn(msgSupplier: Supplier<String>)

    /**
     * Log a message, with associated Throwable information.
     *
     *
     *
     * @param msg The message to log
     * @param t Throwable associated with log message.
     */
    fun warn(msg: Any, t: Throwable)

    /**
     * Log a ERROR message.
     *
     *
     * If the logger is currently enabled for the ERROR message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     *
     *
     *
     * @param msg The message to log
     */
    fun error(msg: Any)

    /**
     * Log an ERROR message.  Only invokes the given supplier if the ERROR level is currently loggable.
     *
     * @param msgSupplier a [Supplier] which will return the log mesage when invoked
     */
    fun error(msgSupplier: Supplier<String>)

    /**
     * Log a message, with associated Throwable information.
     *
     *
     *
     * @param msg The message to log
     * @param t Throwable associated with log message.
     */
    fun error(msg: Any, t: Throwable)

    /**
     * Set logging level for all handlers to ERROR
     */
    fun setLevelError()

    /**
     * Set logging level for all handlers to WARNING
     */
    fun setLevelWarn()

    /**
     * Set logging level for all handlers to INFO
     */
    fun setLevelInfo()

    /**
     * Set logging level for all handlers to DEBUG
     */
    fun setLevelDebug()

    /**
     * Set logging level for all handlers to TRACE
     */
    fun setLevelTrace()

    /**
     * Set logging level for all handlers to ALL (allow all log messages)
     */
    fun setLevelAll()

    /**
     * Set logging level for all handlers to OFF (allow no log messages)
     */
    fun setLevelOff()
    /**
     * @return the [Level] configured for this [java.util.logging.Logger].
     */
    /**
     * Set logging level for all handlers to <tt>level</tt>
     *
     * @param level the level to set for all logger handlers
     */
    var level: Level?

    /**
     * Add additional log context to this logger
     *
     * @param addedContext a map of key, value pairs of the key names and values to add
     */
    fun addContext(addedContext: Map<String, String>)

    /**
     * Add additional log context to this logger
     *
     * @param key the context key
     * @param value the context value
     */
    fun addContext(key: String, value: String)
}