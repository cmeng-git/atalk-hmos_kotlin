/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.util.concurrent

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory

/**
 * Implements utility functions to facilitate work with `Executor`s and `ExecutorService`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object ExecutorUtils {
    /**
     * Creates a thread pool that creates new threads as needed, but will reuse previously
     * constructed threads when they are available. Optionally, the new threads are created as
     * daemon threads and their names are based on a specific (prefix) string.
     *
     * @param daemon `true` to create the new threads as daemon threads
     * or `false` to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or `null` to leave them with their default names
     * @return the newly created thread pool
     */
    fun newCachedThreadPool(daemon: Boolean, baseName: String?): ExecutorService {
        return Executors.newCachedThreadPool(newThreadFactory(daemon, baseName))
    }

    /**
     * A thread factory creating threads, which are created as daemon threads(optionally)
     * and their names are based on a specific (prefix) string.
     *
     * @param daemon `true` to create the new threads as daemon threads
     * or `false` to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or `null` to leave them with their default names
     * @return the newly created thread factory
     */
    private fun newThreadFactory(daemon: Boolean, baseName: String?): ThreadFactory {
        return object : ThreadFactory {
            /**
             * The default `ThreadFactory` implementation which is augmented by this
             * instance to create daemon `Thread`s.
             */
            private val defaultThreadFactory = Executors.defaultThreadFactory()
            override fun newThread(r: Runnable): Thread {
                val t = defaultThreadFactory.newThread(r)
                if (t != null) {
                    t.isDaemon = daemon

                    /*
                     * Additionally, make it known through the name of the Thread that it is
                     * associated with the specified class for debugging/informational purposes.
                     */
                    if (baseName != null && baseName.length != 0) {
                        t.name = baseName + "-" + t.name
                    }
                }
                return t
            }
        }
    }

    /**
     * Creates a scheduled thread pool, Optionally, the new threads are created
     * as daemon threads and their names are based on a specific (prefix) string.
     *
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle.
     * @param daemon `true` to create the new threads as daemon threads
     * or `false` to create the new threads as user threads
     * @param baseName the base/prefix to use for the names of the new threads
     * or `null` to leave them with their default names
     * @return the newly created thread pool
     */
    fun newScheduledThreadPool(corePoolSize: Int, daemon: Boolean, baseName: String?): ScheduledExecutorService {
        return Executors.newScheduledThreadPool(corePoolSize, newThreadFactory(daemon, baseName))
    }
}