/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

object Tables {
    const val PITCH_EST_MAX_LAG_MS = 18 /* 18 ms -> 56 Hz */
    const val PITCH_EST_MIN_LAG_MS = 2 /* 2 ms -> 500 Hz */

    /**
     * Copies the specified range of the specified array into a new array. The initial index of the
     * range (`from` ) must lie between zero and `original.length`, inclusive. The
     * value at `original[from]` is placed into the initial element of the copy (unless
     * `from == original.length` or `from == to`). Values from subsequent elements in
     * the original array are placed into subsequent elements in the copy. The final index of the
     * range (`to`), which must be greater than or equal to `from`, may be greater
     * than `original.length`, in which case `0` is placed in all elements of the copy
     * whose index is greater than or equal to `original.length - from`. The length of the
     * returned array will be `to - from`.
     *
     * @param original
     * the array from which a range is to be copied
     * @param from
     * the initial index of the range to be copied, inclusive
     * @param to
     * the final index of the range to be copied, exclusive. (This index may lie outside the
     * array.)
     * @return a new array containing the specified range from the original array, truncated or
     * padded with zeros to obtain the required length
     * @throws ArrayIndexOutOfBoundsException
     * if `from < 0` or `from > original.length()`
     * @throws IllegalArgumentException
     * if `from > to`
     * @throws NullPointerException
     * if `original` is `null`
     */
    fun copyOfRange(original: IntArray, from: Int, to: Int): IntArray {
        if (from < 0 || from > original.size) throw ArrayIndexOutOfBoundsException(from)
        require(from <= to) { "to" }
        val length = to - from
        val copy = IntArray(length)
        var c = 0
        var o = from
        while (c < length) {
            copy[c] = if (o < original.size) original[o] else 0
            c++
            o++
        }
        return copy
    }
}