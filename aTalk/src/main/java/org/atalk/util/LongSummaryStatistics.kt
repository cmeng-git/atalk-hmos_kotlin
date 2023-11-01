/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.atalk.util

import org.atalk.util.function.IntConsumer
import org.atalk.util.function.LongConsumer

/**
 * A state object for collecting statistics such as count, min, max, sum, and
 * average.
 *
 *
 * This class is designed to work with (though does not require)
 * [streams][java.util.stream]. For example, you can compute
 * summary statistics on a stream of longs with:
 * <pre> `LongSummaryStatistics stats = longStream.collect(LongSummaryStatistics::new,
 * LongSummaryStatistics::accept,
 * LongSummaryStatistics::combine);
`</pre> *
 *
 *
 * `LongSummaryStatistics` can be used as a
 * [java.util.stream.Stream.collect] reduction}
 * target for a [stream][java.util.stream.Stream]. For example:
 *
 * <pre> `LongSummaryStatistics stats = people.stream()
 * .collect(Collectors.summarizingLong(Person::getAge));
`</pre> *
 *
 * This computes, in a single pass, the count of people, as well as the minimum,
 * maximum, sum, and average of their ages.
 *
 * @implNote This implementation is not thread safe. However, it is safe to use
 * [ Collectors.toLongStatistics()][java.util.stream.Collectors.summarizingLong] on a parallel stream, because the parallel
 * implementation of [Stream.collect()][java.util.stream.Stream.collect]
 * provides the necessary partitioning, isolation, and merging of results for
 * safe and efficient parallel execution.
 *
 *
 * This implementation does not check for overflow of the sum.
 * @since 1.8
 */
class LongSummaryStatistics
/**
 * Construct an empty instance with zero count, zero sum,
 * `Long.MAX_VALUE` min, `Long.MIN_VALUE` max and zero
 * average.
 */
    : LongConsumer, IntConsumer {
    /**
     * Returns the count of values recorded.
     *
     * @return the count of values
     */
    var count = 0L
        private set

    /**
     * Returns the sum of values recorded, or zero if no values have been
     * recorded.
     *
     * @return the sum of values, or zero if none
     */
    var sum = 0L
        private set

    /**
     * Returns the minimum value recorded, or `Long.MAX_VALUE` if no
     * values have been recorded.
     *
     * @return the minimum value, or `Long.MAX_VALUE` if none
     */
    var min = Long.MAX_VALUE
        private set

    /**
     * Returns the maximum value recorded, or `Long.MIN_VALUE` if no
     * values have been recorded
     *
     * @return the maximum value, or `Long.MIN_VALUE` if none
     */
    var max = Long.MIN_VALUE
        private set

    /**
     * Records a new `int` value into the summary information.
     *
     * @param value the input value
     */
    override fun accept(value: Int) {
        accept(value.toLong())
    }

    /**
     * Records a new `long` value into the summary information.
     *
     * @param value the input value
     */
    override fun accept(value: Long) {
        ++count
        sum += value
        min = Math.min(min, value)
        max = Math.max(max, value)
    }

    /**
     * Combines the state of another `LongSummaryStatistics` into this
     * one.
     *
     * @param other another `LongSummaryStatistics`
     * @throws NullPointerException if `other` is null
     */
    fun combine(other: LongSummaryStatistics) {
        count += other.count
        sum += other.sum
        min = Math.min(min, other.min)
        max = Math.max(max, other.max)
    }

    /**
     * Returns the arithmetic mean of values recorded, or zero if no values have been
     * recorded.
     *
     * @return The arithmetic mean of values, or zero if none
     */
    val average: Double
        get() = if (count > 0) sum.toDouble() / count else 0.0

    /**
     * {@inheritDoc}
     *
     * Returns a non-empty string representation of this object suitable for
     * debugging. The exact presentation format is unspecified and may vary
     * between implementations and versions.
     */
    override fun toString(): String {
        return String.format("%s{count=%d, sum=%d, min=%d, average=%f, max=%d}",
                this.javaClass.simpleName,
                count,
                sum,
                min,
                average,
                max)
    }
}