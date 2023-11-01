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

/**
 * Implements a [RecurringRunnable] which has its
 * [RecurringRunnable.run] invoked at a specific interval/period.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class PeriodicRunnable @JvmOverloads constructor(period: Long, invokeImmediately: Boolean = false) : RecurringRunnable {
    /**
     * Gets the last time in milliseconds at which [.run] was invoked.
     *
     * @return the last time in milliseconds at which [.run] was invoked
     */
    /**
     * The last time in milliseconds at which [.run] was invoked.
     */
    var lastProcessTime = System.currentTimeMillis()
        private set
    /**
     * Gets the interval/period in milliseconds at which [.run] is to be invoked.
     *
     * @return the interval/period in milliseconds at which [.run] is to be invoked
     */
    /**
     * Sets the period in milliseconds at which [.run] is to be invoked.
     * Note that the change may not take effect immediately.
     *
     * @param period the period to set.
     */
    /**
     * The interval/period in milliseconds at which [.run] is to be invoked.
     */
    var period: Long
    /**
     * Initializes a new `PeriodicRunnable` instance which is to have
     * its [.run] invoked at a specific interval/period.
     *
     * @param period the interval/period in milliseconds at which [.run] is to be invoked
     * @param invokeImmediately whether to invoke the runnable immediately or
     * wait for one `period` before the first invocation.
     */
    /**
     * Initializes a new `PeriodicRunnable` instance which is to have
     * its [.run] invoked at a specific interval/period.
     *
     * @param period the interval/period in milliseconds at which
     * [.run] is to be invoked
     */
    init {
        require(period >= 1) { "period $period" }
        this.period = period
        lastProcessTime = if (invokeImmediately) -1 else System.currentTimeMillis()
    }// We haven't run yet.

    /**
     * {@inheritDoc}
     */
    override val timeUntilNextRun: Long
        get() {
            // We haven't run yet.
            if (lastProcessTime < 0) {
                return 0
            }
            val timeSinceLastProcess = Math.max(System.currentTimeMillis() - lastProcessTime, 0)
            return Math.max(period - timeSinceLastProcess, 0)
        }

    /**
     * {@inheritDoc}
     *
     * Updates [._lastProcessTime].
     */
    override fun run() {
        if (lastProcessTime < 0) {
            lastProcessTime = System.currentTimeMillis()
        } else {
            /* This ensures the schedule doesn't slip if one run is scheduled late. */
            lastProcessTime += period
        }
    }
}