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
package org.atalk.util.function

/**
 * Represents a function that accepts one argument and produces a result. This
 * is a poor man's backport of the `Function` interface found in Java 1.8.
 *
 *
 * This is a [functional interface](package-summary.html)
 * whose functional method is [.apply].
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 *
 * @author George Politis
 * @author Eng Chong Meng
 */

// @Deprecated
// cmeng: needed by aTalk with API-19
abstract class AbstractFunction<T, R> {
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     */
    abstract fun apply(t: T): R

    /**
     * Returns a composed function that first applies the `before`
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of input to the `before` function, and to the composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the `before`
     * function and then applies this function
     * @throws NullPointerException if before is null
     * @see .andThen
    </V> */
    fun <V> compose(before: AbstractFunction<in V, out T>?): AbstractFunction<V, R> {
        if (before == null) {
            throw NullPointerException()
        }
        return object : AbstractFunction<V, R>() {
            override fun apply(v: V): R {
                return this@AbstractFunction.apply(before.apply(v))
            }
        }
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the `after` function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the `after` function, and of the composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the `after` function
     * @throws NullPointerException if after is null
     * @see .compose
    </V> */
    fun <V> andThen(after: AbstractFunction<in R, out V>?): AbstractFunction<T, V> {
        if (after == null) {
            throw NullPointerException()
        }
        return object : AbstractFunction<T, V>() {
            override fun apply(t: T): V {
                return after.apply(this@AbstractFunction.apply(t))
            }
        }
    } //    /**
    //     * Returns a function that always returns its input argument.
    //     *
    //     * @param <T> the type of the input and output objects to the function
    //     * @return a function that always returns its input argument
    //     */
    //    static <T> Function<T, T> identity() {
    //        return t -> t;
    //    }
}