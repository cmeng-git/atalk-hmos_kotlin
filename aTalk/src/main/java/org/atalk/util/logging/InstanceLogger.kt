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

/**
 * Implements a [Logger], which delegates logging to another
 * [Logger], and allows it's level to be lowered independent of the
 * delegate (by another [Logger] instance or by a level configuration).
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class InstanceLogger
/**
 * Initializes an [InstanceLogger] instance with the given delegate
 * for logging and for getting the level.
 */
(
        /**
         * The [Logger] used for logging.
         */
        private val loggingDelegate: Logger,
        /**
         * The [Logger] used for configuring the level. Messages are logged
         * iff:
         * 1. [.levelDelegate] is not set or allows it.
         * 2. [.loggingDelegate] allows it.
         * 3. [.level], is not set or allows it.
         */
        private val levelDelegate: Logger?) : Logger() {
    /**
     * {@inheritDoc}
     */
    /**
     * {@inheritDoc}
     */
    /**
     * The level configured for this instance.
     */
    override var level: Level? = null
        get() = higher(
                higher(if (field != null) field else Level.ALL, loggingDelegate.level),
                if (levelDelegate != null) levelDelegate.level else Level.ALL)

    /**
     * {@inheritDoc}
     */
    public override fun isLoggable(level: Level?): Boolean {
        val loggerLevel = this.level
        return (!(level == null || loggerLevel === Level.OFF)
                && level.intValue() >= loggerLevel!!.intValue())
    }

    /**
     * {@inheritDoc}
     */
    override fun log(level: Level?, msg: Any?) {
        if (isLoggable(level)) {
            loggingDelegate.log(level, msg?.toString() ?: "null")
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun log(level: Level?, msg: Any?, thrown: Throwable?) {
        if (isLoggable(level)) {
            loggingDelegate.log(level, msg?.toString() ?: "null", thrown)
        }
    }

    /**
     * @return the higher of two logging levels.
     * e.g.: higher(Level.FINE, Level.WARNING) -> Level.WARNING
     */
    private fun higher(a: Level?, b: Level?): Level? {
        return if (a!!.intValue() >= b!!.intValue()) a else b
    }
}