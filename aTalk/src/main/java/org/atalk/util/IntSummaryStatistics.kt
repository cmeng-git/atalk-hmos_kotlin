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

/**
 * A state object for collecting statistics such as count, min, max, sum, and
 * average.
 *
 *
 * This class is designed to work with (though does not require)
 * [streams][java.util.stream]. For example, you can compute
 * summary statistics on a stream of ints with:
 * <pre> `IntSummaryStatistics stats = intStream.collect(IntSummaryStatistics::new,
 * IntSummaryStatistics::accept,
 * IntSummaryStatistics::combine);
`</pre> *
 *
 *
 * `IntSummaryStatistics` can be used as a
 * [reduction][java.util.stream.Stream.collect]
 * target for a [stream][java.util.stream.Stream]. For example:
 *
 * <pre> `IntSummaryStatistics stats = people.stream()
 * .collect(Collectors.summarizingInt(Person::getDependents));
`</pre> *
 *
 * This computes, in a single pass, the count of people, as well as the minimum,
 * maximum, sum, and average of their number of dependents.
 *
 * @implNote This implementation is not thread safe. However, it is safe to use
 * [ Collectors.toIntStatistics()][java.util.stream.Collectors.summarizingInt] on a parallel stream, because the parallel
 * implementation of [Stream.collect()][java.util.stream.Stream.collect]
 * provides the necessary partitioning, isolation, and merging of results for
 * safe and efficient parallel execution.
 *
 *
 * This implementation does not check for overflow of the sum.
 * @since 1.8
 */
class IntSummaryStatistics
/**
 * Construct an empty instance with zero count, zero sum,
 * `Integer.MAX_VALUE` min, `Integer.MIN_VALUE` max and zero
 * average.
 */
    : IntConsumer {
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
     * Returns the minimum value recorded, or `Integer.MAX_VALUE` if no
     * values have been recorded.
     *
     * @return the minimum value, or `Integer.MAX_VALUE` if none
     */
    var min = Int.MAX_VALUE
        private set

    /**
     * Returns the maximum value recorded, or `Integer.MIN_VALUE` if no
     * values have been recorded.
     *
     * @return the maximum value, or `Integer.MIN_VALUE` if none
     */
    var max = Int.MIN_VALUE
        private set

    /**
     * Records a new value into the summary information
     *
     * @param value the input value
     */
    override fun accept(value: Int) {
        ++count
        sum += value.toLong()
        min = Math.min(min, value)
        max = Math.max(max, value)
    }

    /**
     * Combines the state of another `IntSummaryStatistics` into this one.
     *
     * @param other another `IntSummaryStatistics`
     * @throws NullPointerException if `other` is null
     */
    fun combine(other: IntSummaryStatistics) {
        count += other.count
        sum += other.sum
        min = Math.min(min, other.min)
        max = Math.max(max, other.max)
    }

    /**
     * Returns the arithmetic mean of values recorded, or zero if no values have been
     * recorded.
     *
     * @return the arithmetic mean of values, or zero if none
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