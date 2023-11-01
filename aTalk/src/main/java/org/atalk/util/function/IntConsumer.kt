/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.atalk.util.function

/**
 * Represents an operation that accepts a single `int`-valued argument and
 * returns no result.  This is the primitive type specialization of
 * [Consumer] for `int`.  Unlike most other functional interfaces,
 * `IntConsumer` is expected to operate via side-effects.
 *
 *
 * This is a [functional interface](package-summary.html)
 * whose functional method is [.accept].
 *
 * @see Consumer
 *
 * @since 1.8
 */
fun interface IntConsumer {
    /**
     * Performs this operation on the given argument.
     *
     * @param value the input argument
     */
    fun accept(value: Int)
    /**
     * Returns a composed `IntConsumer` that performs, in sequence, this
     * operation followed by the `after` operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the `after` operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed `IntConsumer` that performs in sequence this
     * operation followed by the `after` operation
     * @throws NullPointerException if `after` is null
     */
    //    default IntConsumer andThen(IntConsumer after) {
    //        Objects.requireNonNull(after);
    //        return (int t) -> { accept(t); after.accept(t); };
    //    }
}