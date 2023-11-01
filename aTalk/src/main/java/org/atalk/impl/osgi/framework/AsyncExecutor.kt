/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework

import net.java.sip.communicator.util.Logger
import okhttp3.internal.notifyAll
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AsyncExecutor<T : Runnable?> @JvmOverloads constructor(keepAliveTime: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS) {
    private var keepAliveTime: Long
    private val queue: MutableList<CommandFuture<T>> = LinkedList()
    private var shutdown = false
    private var shutdownNow = false
    private var thread: Thread? = null

    init {
        require(keepAliveTime >= 0) { "keepAliveTime" }
        this.keepAliveTime = unit.toMillis(keepAliveTime)
    }

    @Synchronized
    private operator fun contains(command: T): Boolean {
        for (commandFuture in queue) if (commandFuture.command === command) return true
        return false
    }

    fun execute(command: T) {
        submit(command)
    }

    private fun runInThread() {
        var idleTime = -1L

        while (true) {
            var commandFuture: CommandFuture<T>? = null
            var interrupted = false

            synchronized(this) {
                if (shutdownNow) return
                else if (queue.isEmpty()) {
                    /*
                     * Technically, we may keep this Thread alive much longer
                     * than keepAliveTime since idleTime because we always try
                     * to wait for at least keepAliveTime in a single wait. But
                     * we are OK with it as long as this AsyncExecutor does not
                     * keep its Thread forever in the presence of an actual
                     * non-infinite keepAliveTime.
                     */
                    if (idleTime == -1L) {
                        idleTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - idleTime > keepAliveTime) {
                        return
                    }

                    try {
                        (this as Object).wait(keepAliveTime)
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                    if (interrupted) Thread.currentThread().interrupt()
                } else {
                    idleTime = -1
                    commandFuture = queue.removeAt(0)
                }
            }

            if (commandFuture != null) {
                var exception: Throwable? = null

                val command = commandFuture!!.command
                try {
                    command!!.run()
                } catch (t: Throwable) {
                    exception = t
                    if (t is ThreadDeath) throw t else uncaughtException(command, t)
                } finally {
                    commandFuture!!.setDone(exception ?: java.lang.Boolean.TRUE)
                }
            }
        }
    }

    fun setKeepAliveTime(keepAliveTime: Long, unit: TimeUnit) {
        require(keepAliveTime >= 0) { "keepAliveTime" }
        synchronized(this) {
            this.keepAliveTime = unit.toMillis(keepAliveTime)
            notifyAll()
        }
    }

    @Synchronized
    fun shutdown() {
        shutdown = true
        notifyAll()
    }

    fun shutdownNow(): List<T> {
        var awaiting: MutableList<CommandFuture<T>>
        synchronized(this) {
            shutdown = true
            shutdownNow = true
            notifyAll()
            var interrupted = false
            while (thread != null) {
                try {
                    (this as Object).wait(keepAliveTime)
                } catch (ie: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
            awaiting = ArrayList(queue.size)
            awaiting.addAll(queue)
        }
        val awaitingCommands = ArrayList<T>(awaiting.size)
        for (commandFuture in awaiting) {
            awaitingCommands.add(commandFuture.command)
            commandFuture.setDone(java.lang.Boolean.FALSE)
        }
        return awaitingCommands
    }

    @Synchronized
    fun submit(command: T?): Future<*> {
        if (command == null) throw NullPointerException("command")
        if (shutdown) throw RejectedExecutionException("shutdown")
        if (contains(command)) throw RejectedExecutionException("contains")
        val future = CommandFuture<T>(command)
        queue.add(future)
        startThreadOrNotifyAll()
        return future
    }

    @Synchronized
    private fun startThreadOrNotifyAll() {
        if (thread == null && !shutdown && !shutdownNow
                && !queue.isEmpty()) {
            thread = object : Thread(javaClass.name) {
                override fun run() {
                    try {
                        runInThread()
                    } finally {
                        synchronized(this@AsyncExecutor) {
                            if (currentThread() == thread) {
                                thread = null
                                startThreadOrNotifyAll()
                            }
                        }
                    }
                }
            }
            thread!!.setDaemon(true)
            thread!!.start()
        } else notifyAll()
    }

    protected fun uncaughtException(command: T, exception: Throwable?) {
        Logger.getLogger(AsyncExecutor::class.java).error("Error executing command $command", exception)
    }

    private class CommandFuture<T : Runnable?>(val command: T) : Future<Any?> {
        private var done: Boolean? = null
        private var exception: Throwable? = null
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            // TODO Auto-generated method stub
            return false
        }

        @Throws(ExecutionException::class, InterruptedException::class)
        override fun get(): Any? {
            return try {
                get(0, TimeUnit.MILLISECONDS)
            } catch (te: TimeoutException) {
                /*
                 * Since the timeout is infinite, a TimeoutException is not expected.
                 */
                throw RuntimeException(te)
            }
        }

        @Synchronized
        @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
        override fun get(timeout: Long, unit: TimeUnit): Any? {
            var timeout = timeout
            timeout = unit.toMillis(timeout)
            var timeoutException = false
            while (true) {
                if (done != null) {
                    if (done!!) break else throw CancellationException()
                } else if (exception != null) throw ExecutionException(exception) else if (timeoutException) throw TimeoutException() else {
                    (this as Object).wait(timeout)
                    timeoutException = timeout != 0L
                }
            }
            return null
        }

        @Synchronized
        override fun isCancelled(): Boolean {
            return done != null && !done!!
        }

        @Synchronized
        override fun isDone(): Boolean {
            return done != null || exception != null
        }

        @Synchronized
        fun setDone(done: Any?) {
            if (done is Boolean) this.done = done else if (done is Throwable) exception = done else throw IllegalArgumentException("done")
            notifyAll()
        }
    }
}