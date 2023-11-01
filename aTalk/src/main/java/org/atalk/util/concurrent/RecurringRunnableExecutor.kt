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
package org.atalk.util.concurrent

import timber.log.Timber
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Implements a single-threaded [Executor] of [RecurringRunnable]s
 * i.e. asynchronous tasks which determine by themselves the intervals
 * (the lengths of which may vary) at which they are to be invoked.
 *
 *
 * webrtc/modules/utility/interface/process_thread.h
 * webrtc/modules/utility/source/process_thread_impl.cc
 * webrtc/modules/utility/source/process_thread_impl.h
 *
 * @author Lyubomir Marinov
 * @author George Politis
 * @author Eng Chong Meng
 */
class RecurringRunnableExecutor
/**
 * Initializes a new [RecurringRunnableExecutor] instance.
 */ @JvmOverloads constructor(
        /**
         * A `String` which will be added to the name of [.thread]. Meant to facilitate debugging.
         */
        private val name: String =  /* name */"") : Executor {
    /**
     * The `RecurringRunnable`s registered with this instance which are
     * to be invoked in [.thread].
     */
    private val recurringRunnables = LinkedList<RecurringRunnable>()

    /**
     * The (background) `Thread` which invokes [RecurringRunnable.run] on
     * [.recurringRunnables] (in accord with their respective
     * [RecurringRunnable.timeUntilNextRun]).
     */
    private var thread: Thread? = null

    /**
     * Whether this [RecurringRunnableExecutor] is closed. When it is closed, it should stop its thread(s).
     */
    private var closed = false
    /**
     * Initializes a new [RecurringRunnableExecutor] instance.
     *
     * @param name a string to be added to the name of the thread which this instance will start.
     */
    /**
     * De-registers a `RecurringRunnable` from this `Executor` so
     * that its [RecurringRunnable.run] is no longer invoked (by this instance).
     *
     * @param recurringRunnable the `RecurringRunnable` to de-register from this instance
     * @return `true` if the list of `RecurringRunnable`s of this
     * instance changed because of the method call; otherwise, `false`
     */
    fun deRegisterRecurringRunnable(recurringRunnable: RecurringRunnable?): Boolean {
        return if (recurringRunnable == null) {
            false
        } else {
            synchronized(recurringRunnables) {
                val removed = recurringRunnables.remove(recurringRunnable)
                if (removed) startOrNotifyThread()
                return removed
            }
        }
    }

    /**
     * {@inheritDoc}
     * Accepts for execution [RecurringRunnable]s only.
     */
    override fun execute(command: Runnable) {
        Objects.requireNonNull(command, "command")
        if (command !is RecurringRunnable) {
            throw RejectedExecutionException("The class " + command.javaClass.name
                    + " of command does not implement " + RecurringRunnable::class.java.name)
        }
        registerRecurringRunnable(command)
    }

    /**
     * Executes an iteration of the loop implemented by [.runInThread].
     * Invokes [RecurringRunnable.run] on all [.recurringRunnables] which are at or
     * after the time at which they want the method in question called.
     * TODO(brian): worth investigating if we can invoke the ready runnables
     * outside the scope of the [RecurringRunnableExecutor.recurringRunnables]
     * lock so we can get rid of a possible deadlock scenario when invoking
     * a runnable which needs to signal to the executor that it has work ready
     *
     * @return `true` to continue with the next iteration of the loop
     * implemented by [.runInThread] or `false` to break (out of) the loop
     */
    private fun run(): Boolean {
        if (closed || Thread.currentThread() != thread) {
            return false
        }
        // Wait for the recurringRunnable that should be called next, but don't block thread longer than 100 ms.
        var minTimeToNext = 100L
        synchronized(recurringRunnables) {
            if (recurringRunnables.isEmpty()) {
                return false
            }
            for (recurringRunnable in recurringRunnables) {
                val timeToNext = recurringRunnable.timeUntilNextRun
                if (minTimeToNext > timeToNext) minTimeToNext = timeToNext
            }
        }
        if (minTimeToNext > 0L) {
            synchronized(recurringRunnables) {
                if (recurringRunnables.isEmpty()) {
                    return false
                }
                try {
                    (recurringRunnables as Object).wait(minTimeToNext)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                return true
            }
        }
        synchronized(recurringRunnables) {
            for (recurringRunnable in recurringRunnables) {
                val timeToNext = recurringRunnable.timeUntilNextRun
                if (timeToNext < 1L) {
                    try {
                        recurringRunnable.run()
                    } catch (t: Throwable) {
                        when (t) {
                            is InterruptedException -> {
                                Thread.currentThread().interrupt()
                            }
                            is ThreadDeath -> {
                                throw t
                            }
                            else -> {
                                Timber.e(t, "The invocation of the method %s.run() threw an exception.",
                                        recurringRunnable.javaClass.name)
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    /**
     * Registers a `RecurringRunnable` with this `Executor` so
     * that its [RecurringRunnable.run] is invoked (by this instance).
     *
     * @param recurringRunnable the `RecurringRunnable` to register with this instance
     * @return `true` if the list of `RecurringRunnable`s of this
     * instance changed because of the method call; otherwise, `false`
     */
    fun registerRecurringRunnable(recurringRunnable: RecurringRunnable): Boolean {
        Objects.requireNonNull(recurringRunnable, "recurringRunnable")
        synchronized(recurringRunnables) {
            if (closed) {
                return false
            }

            // Only allow recurringRunnable to be registered once.
            return if (recurringRunnables.contains(recurringRunnable)) {
                false
            } else {
                recurringRunnables.add(0, recurringRunnable)

                // Wake the thread calling run() to update the waiting time. The waiting time for the just
                // registered recurringRunnable may be shorter than all other registered recurringRunnable.
                startOrNotifyThread()
                true
            }
        }
    }

    /**
     * Runs in [.thread].
     */
    private fun runInThread() {
        try {
            while (run()) {
            }
        } finally {
            synchronized(recurringRunnables) {
                if (!closed && Thread.currentThread() == thread) {
                    thread = null
                    // If the (current) thread dies unexpectedly, make sure a new thread will replace it if necessary.
                    startOrNotifyThread()
                }
            }
        }
    }

    /**
     * Starts or notifies [.thread] depending on and in accord with the state of this instance.
     */
    fun startOrNotifyThread() {
        synchronized(recurringRunnables) {
            if (!closed && thread == null) {
                if (!recurringRunnables.isEmpty()) {
                    val thread = object : Thread() {
                        override fun run() {
                            runInThread()
                        }
                    }
                    thread.isDaemon = true
                    thread.name = RecurringRunnableExecutor::class.java.name + ".thread-" + name
                    var started = false
                    this.thread = thread
                    started = try {
                        thread.start()
                        true
                    } finally {
                        if (!started && thread == this.thread) this.thread = null
                    }
                }
            } else {
                (recurringRunnables as Object).notifyAll()
            }
        }
    }

    /**
     * Closes this [RecurringRunnableExecutor], signalling its thread to stop and
     * de-registering all registered runnable.
     */
    fun close() {
        synchronized(recurringRunnables) {
            closed = true
            thread = null
            (recurringRunnables as Object).notifyAll()
        }
    }
}