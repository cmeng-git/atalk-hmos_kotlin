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

import java.util.function.Function
import java.util.function.Supplier
import java.util.logging.Handler
import java.util.logging.Level

/**
 * Implements [Logger] by delegating to a [java.util.logging.Logger].
 */
class LoggerImpl @JvmOverloads constructor(name: String?, minLogLevel: Level = Level.ALL, logContext: LogContext? = LogContext()) : Logger {
    private val loggerDelegate: java.util.logging.Logger?

    /**
     * The 'minimum' level a log statement must be to be logged by this Logger. For example, if this
     * is set to [Level.WARNING], then only log statements at the warning level or above
     * will actually be logged.
     */
    private val minLogLevel: Level
    private val logContext: LogContext?

    constructor(name: String?, logContext: LogContext?) : this(name, Level.ALL, logContext)

    init {
        loggerDelegate = loggerFactory.apply(name!!)
        this.minLogLevel = minLogLevel
        this.logContext = logContext
    }

    /**
     * Create a new logger with the given name.  The resulting logger's [LogContext]
     * will be the result of merging the given [LogContext] with this logger's
     * [LogContext].
     *
     * @param name
     * @param context
     * @return
     */
    override fun createChildLogger(name: String, context: Map<String, String>): Logger {
        return LoggerImpl(name, minLogLevel, logContext!!.createSubContext(context))
    }

    override fun createChildLogger(name: String?): Logger {
        // Note that we still need to create a subcontext here for the log
        // context, otherwise if other values are added later they'll affect
        // the parent's log context as well.
        return LoggerImpl(name, minLogLevel, logContext!!.createSubContext(emptyMap()))
    }

    override fun setUseParentHandlers(useParentHandlers: Boolean) {
        loggerDelegate!!.useParentHandlers = false
    }

    @Throws(SecurityException::class)
    override fun addHandler(handler: Handler) {
        loggerDelegate!!.addHandler(handler)
    }

    @Throws(SecurityException::class)
    override fun removeHandler(handler: Handler) {
        loggerDelegate!!.removeHandler(handler)
    }

    private fun isLoggable(level: Level): Boolean {
        return level.intValue() >= minLogLevel.intValue() && loggerDelegate!!.isLoggable(level)
    }

    private fun log(level: Level, msg: Any, thrown: Throwable) {
        if (!isLoggable(level)) {
            return
        }
        val lr = ContextLogRecord(level, msg.toString(), logContext!!.formattedContext)
        lr.thrown = thrown
        lr.loggerName = loggerDelegate!!.name
        loggerDelegate.log(lr)
    }

    private fun log(level: Level, msg: Any) {
        if (!isLoggable(level)) {
            return
        }
        val lr = ContextLogRecord(level, msg.toString(), logContext!!.formattedContext)
        lr.loggerName = loggerDelegate!!.name
        loggerDelegate.log(lr)
    }

    private fun log(level: Level, msgSupplier: Supplier<String>) {
        if (!isLoggable(level)) {
            return
        }
        val lr = ContextLogRecord(level, msgSupplier.get(), logContext!!.formattedContext)
        lr.loggerName = loggerDelegate!!.name
        loggerDelegate.log(lr)
    }

    // OpenJDK's Logger implementation initializes its effective level value
    // with Level.INFO.intValue(), but DOESN'T initialize the Level object.
    // So, if it hasn't been explicitly set, assume INFO.
    override var level: Level?
        get() {
            // OpenJDK's Logger implementation initializes its effective level value
            // with Level.INFO.intValue(), but DOESN'T initialize the Level object.
            // So, if it hasn't been explicitly set, assume INFO.
            val level = loggerDelegate!!.level
            return level ?: Level.INFO
        }
        set(level) {
            val handlers = loggerDelegate!!.handlers
            for (handler in handlers) handler.level = level
            loggerDelegate.level = level
        }

    override fun setLevelAll() {
        level = Level.ALL
    }

    override fun setLevelDebug() {
        level = Level.FINE
    }

    override fun setLevelError() {
        level = Level.SEVERE
    }

    override fun setLevelInfo() {
        level = Level.INFO
    }

    override fun setLevelOff() {
        level = Level.OFF
    }

    override fun setLevelTrace() {
        level = Level.FINER
    }

    override fun setLevelWarn() {
        level = Level.WARNING
    }

    override val isTraceEnabled: Boolean
        get() = isLoggable(Level.FINER)

    override fun trace(msg: Any) {
        log(Level.FINER, msg)
    }

    override fun trace(msgSupplier: Supplier<String>) {
        log(Level.FINER, msgSupplier)
    }

    override val isDebugEnabled: Boolean
        get() = isLoggable(Level.FINE)

    override fun debug(msg: Any) {
        log(Level.FINE, msg)
    }

    override fun debug(msgSupplier: Supplier<String>) {
        log(Level.FINE, msgSupplier)
    }

    override val isInfoEnabled: Boolean
        get() = isLoggable(Level.INFO)

    override fun info(msg: Any) {
        log(Level.INFO, msg)
    }

    override fun info(msgSupplier: Supplier<String>) {
        log(Level.INFO, msgSupplier)
    }

    override val isWarnEnabled: Boolean
        get() = isLoggable(Level.WARNING)

    override fun warn(msg: Any) {
        log(Level.WARNING, msg)
    }

    override fun warn(msgSupplier: Supplier<String>) {
        log(Level.WARNING, msgSupplier)
    }

    override fun warn(msg: Any, t: Throwable) {
        log(Level.WARNING, msg, t)
    }

    override fun error(msg: Any) {
        log(Level.SEVERE, msg)
    }

    override fun error(msgSupplier: Supplier<String>) {
        log(Level.SEVERE, msgSupplier)
    }

    override fun error(msg: Any, t: Throwable) {
        log(Level.SEVERE, msg, t)
    }

    override fun addContext(addedContext: Map<String, String>) {
        logContext!!.addContext(addedContext)
    }

    override fun addContext(key: String, value: String) {
        logContext!!.addContext(key, value)
    }

    companion object {
        var loggerFactory = Function { name: String -> java.util.logging.Logger.getLogger(name) }
    }
}