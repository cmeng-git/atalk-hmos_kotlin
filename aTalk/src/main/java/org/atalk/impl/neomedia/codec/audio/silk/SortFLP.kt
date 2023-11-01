/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Insertion sort (fast for already almost sorted arrays): Best case: O(n) for an already sorted
 * array Worst case: O(n^2) for an inversely sorted array
 *
 * To be implemented: Shell short: https://en.wikipedia.org/wiki/Shell_sort
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object SortFLP {
    /**
     *
     * @param a
     * Unsorted / Sorted vector
     * @param a_offset
     * @param index
     * Index vector for the sorted elements
     * @param L
     * Vector length
     * @param K
     * Number of correctly sorted positions
     */
    fun SKP_Silk_insertion_sort_increasing_FLP(a: FloatArray,  /* I/O: Unsorted / Sorted vector */
            a_offset: Int, index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int,  /* I: Vector length */
            K: Int /* I: Number of correctly sorted positions */
    ) {
        var value: Float
        var i: Int
        var j: Int
        assert(K > 0)
        assert(L > 0)
        assert(L >= K)

        /* Write start indices in index vector */
        i = 0
        while (i < K) {
            index[i] = i
            i++
        }

        /* Sort vector elements by value, increasing order */
        i = 1
        while (i < K) {
            value = a[a_offset + i]
            j = i - 1
            while (j >= 0 && value < a[a_offset + j]) {
                a[a_offset + j + 1] = a[a_offset + j] /* Shift value */
                index[j + 1] = index[j] /* Shift index */
                j--
            }
            a[a_offset + j + 1] = value /* Write value */
            index[j + 1] = i /* Write index */
            i++
        }

        /* If less than L values are asked check the remaining values, */
        /* but only spend CPU to ensure that the K first values are correct */
        i = K
        while (i < L) {
            value = a[a_offset + i]
            if (value < a[a_offset + K - 1]) {
                j = K - 2
                while (j >= 0 && value < a[a_offset + j]) {
                    a[a_offset + j + 1] = a[a_offset + j] /* Shift value */
                    index[j + 1] = index[j] /* Shift index */
                    j--
                }
                a[a_offset + j + 1] = value /* Write value */
                index[j + 1] = i /* Write index */
            }
            i++
        }
    }

    /**
     *
     * @param a
     * Unsorted / Sorted vector.
     * @param a_offset
     * offset of valid data.
     * @param index
     * Index vector for the sorted elements.
     * @param L
     * Vector length.
     * @param K
     * Number of correctly sorted positions.
     */
    fun SKP_Silk_insertion_sort_decreasing_FLP(a: FloatArray,  /* I/O: Unsorted / Sorted vector */
            a_offset: Int, index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int,  /* I: Vector length */
            K: Int /* I: Number of correctly sorted positions */
    ) {
        var value: Float
        var i: Int
        var j: Int
        assert(K > 0)
        assert(L > 0)
        assert(L >= K)

        /* Write start indices in index vector */
        i = 0
        while (i < K) {
            index[i] = i
            i++
        }

        /* Sort vector elements by value, decreasing order */
        i = 1
        while (i < K) {
            value = a[a_offset + i]
            j = i - 1
            while (j >= 0 && value > a[a_offset + j]) {
                a[a_offset + j + 1] = a[a_offset + j] /* Shift value */
                index[j + 1] = index[j] /* Shift index */
                j--
            }
            a[a_offset + j + 1] = value /* Write value */
            index[j + 1] = i /* Write index */
            i++
        }

        /* If less than L values are asked check the remaining values, */
        /* but only spend CPU to ensure that the K first values are correct */
        i = K
        while (i < L) {
            value = a[a_offset + i]
            if (value > a[a_offset + K - 1]) {
                j = K - 2
                while (j >= 0 && value > a[a_offset + j]) {
                    a[a_offset + j + 1] = a[a_offset + j] /* Shift value */
                    index[j + 1] = index[j] /* Shift index */
                    j--
                }
                a[a_offset + j + 1] = value /* Write value */
                index[j + 1] = i /* Write index */
            }
            i++
        }
    }

    /**
     *
     * @param a
     * Unsorted / Sorted vector
     * @param a_offset
     * offset of valid data.
     * @param L
     * Vector length
     */
    fun SKP_Silk_insertion_sort_increasing_all_values_FLP(a: FloatArray,  /*
																			 * I/O: Unsorted /
																			 * Sorted vector
																			 */
            a_offset: Int, L: Int /* I: Vector length */
    ) {
        var value: Float
        var i: Int
        var j: Int
        assert(L > 0)

        /* Sort vector elements by value, increasing order */
        i = 1
        while (i < L) {
            value = a[a_offset + i]
            j = i - 1
            while (j >= 0 && value < a[a_offset + j]) {
                a[a_offset + j + 1] = a[a_offset + j] /* Shift value */
                j--
            }
            a[a_offset + j + 1] = value /* Write value */
            i++
        }
    }
}