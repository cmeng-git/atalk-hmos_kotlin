/*
 * Copyright @ 2018 - present 8x8, Inc.
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

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Helper class which contains functions to create pre-configured executors
 *
 * @author Yura Yaroshevich
 * @author Eng Chong Meng
 */
object ExecutorFactory {
    /**
     * Create [ScheduledExecutorService] with single executor thread
     * @param threadNamePrefix - name prefix for threads created by pool
     * @param threadKeepAliveTime - keep alive time before idle thread is freed
     * @param timeUnit - time unit of <tt>threadKeepAliveTime</tt>
     * @return pre-configured [ScheduledExecutorService]
     */
    @JvmStatic
    fun createSingleThreadScheduledExecutor(
            threadNamePrefix: String,
            threadKeepAliveTime: Int,
            timeUnit: TimeUnit): ScheduledExecutorService {
        return createScheduledExecutor(
                1,
                threadNamePrefix,
                threadKeepAliveTime,
                timeUnit)
    }

    /**
     * Create [ScheduledExecutorService] with number of threads up to
     * number of CPU cores on machine
     * @param threadNamePrefix - name prefix for threads created by pool
     * @param threadKeepAliveTime - keep alive time before idle thread is freed
     * @param timeUnit - time unit of <tt>threadKeepAliveTime</tt>
     * @return pre-configured [ScheduledExecutorService]
     */
    fun createCPUBoundScheduledExecutor(
            threadNamePrefix: String,
            threadKeepAliveTime: Int,
            timeUnit: TimeUnit): ScheduledExecutorService {
        return createScheduledExecutor(
                Runtime.getRuntime().availableProcessors(),
                threadNamePrefix,
                threadKeepAliveTime,
                timeUnit)
    }

    /**
     * Creates pre-configured [ScheduledExecutorService] instance with
     * defaults suitable for ice4j
     * @param threadNamePrefix - name prefix for threads created by pool
     * @param poolSize - max number of threads to keep in pool.
     * @param threadKeepAliveTime - keep alive time before idle thread is freed
     * @param timeUnit - time unit of <tt>threadKeepAliveTime</tt>
     * @return pre-configured [ScheduledExecutorService]
     */
    private fun createScheduledExecutor(
            poolSize: Int,
            threadNamePrefix: String,
            threadKeepAliveTime: Int,
            timeUnit: TimeUnit): ScheduledExecutorService {
        val threadFactory = CustomizableThreadFactory(threadNamePrefix, true)

        // Motivation:
        // The desired behaviour from pool is that it creates limited number
        // of threads based on current load, as well as releasing threads when
        // there is no work to execute.
        // Based on these requirements the following default configuration is
        // chosen.
        // <tt>corePoolSize</tt> for {@link ScheduledThreadPoolExecutor} is
        // behaved both as <tt>corePoolSize</tt> and <tt>maxPoolSize</tt>, so
        // it is actually fixed-size pool.
        // Even so spec says that corePoolSize is number of
        // threads to keep in pool they are actually created on demand, so if
        // there is no load, then threads are not created. But until pool has
        // less than <tt>corePoolSize</tt> threads, pool will create new thread,
        // to execute scheduled task, even though already created threads are
        // idle and have no tasks to execute.
        // <tt>keepAliveTime</tt> is configurable and specifies timeout before
        // idle core thread deleted from pool
        // <tt>removeOnCancelPolicy</tt> is set to true, to immediately remove
        // queued task from pool queue, because some task might be scheduled
        // with very big delay causing having reference to creator from pool
        //
        // Having <tt>corePoolSize</tt> is set to 0 with unlimited pool size
        // <tt>maximumPoolSize</tt> is observed to create only 1 thread in pool
        // no matter how many task are queued and become eligible to execute.
        val executor = ScheduledThreadPoolExecutor(poolSize, threadFactory)
        executor.setKeepAliveTime(threadKeepAliveTime.toLong(), timeUnit)
        executor.allowCoreThreadTimeOut(true)
        executor.removeOnCancelPolicy = true
        return executor
    }

    /**
     * Creates a [ExecutorService] with limited number of threads which
     * are released after idle timeout.
     *
     * @param threadsLimit - numbers of threads in pool
     * @param threadNamePrefix - name prefix for threads created by pool
     * @return pre-configured [ExecutorService]
     */
    @JvmStatic
    fun createFixedThreadPool(
            threadsLimit: Int,
            threadNamePrefix: String?): ExecutorService {
        val threadFactory = CustomizableThreadFactory(threadNamePrefix, true)
        val executor = ThreadPoolExecutor(
                threadsLimit, threadsLimit, 60L, TimeUnit.SECONDS,
                LinkedBlockingDeque(), threadFactory)
        executor.allowCoreThreadTimeOut(true)
        return executor
    }

    /**
     * Creates an [ExecutorService] with an unlimited number of threads
     * which are released after idle timeout.
     *
     * @param threadNamePrefix - name prefix for threads created by pool
     * @return pre-configured [ExecutorService]
     */
    @JvmStatic
    fun createCachedThreadPool(
            threadNamePrefix: String?): ExecutorService {
        val threadFactory = CustomizableThreadFactory(threadNamePrefix, true)
        return Executors.newCachedThreadPool(threadFactory)
    }
}