/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Insertion sort (fast for already almost sorted arrays): Best case: O(n) for an already sorted
 * array Worst case: O(n^2) for an inversely sorted array Shell short:
 * http://en.wikipedia.org/wiki/Shell_sort
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object Sort {
    /**
     *
     * @param a
     * Unsorted / Sorted vector
     * @param index
     * Index vector for the sorted elements
     * @param L
     * Vector length
     * @param K
     * Number of correctly sorted positions
     */
    fun SKP_Silk_insertion_sort_increasing(a: IntArray,  /* I/O: Unsorted / Sorted vector */
            index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int,  /* I: Vector length */
            K: Int /* I: Number of correctly sorted positions */
    ) {
        var value: Int
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
            value = a[i]
            j = i - 1
            while (j >= 0 && value < a[j]) {
                a[j + 1] = a[j] /* Shift value */
                index[j + 1] = index[j] /* Shift index */
                j--
            }
            a[j + 1] = value /* Write value */
            index[j + 1] = i /* Write index */
            i++
        }

        /* If less than L values are asked for, check the remaining values, */
        /* but only spend CPU to ensure that the K first values are correct */
        i = K
        while (i < L) {
            value = a[i]
            if (value < a[K - 1]) {
                j = K - 2
                while (j >= 0 && value < a[j]) {
                    a[j + 1] = a[j] /* Shift value */
                    index[j + 1] = index[j] /* Shift index */
                    j--
                }
                a[j + 1] = value /* Write value */
                index[j + 1] = i /* Write index */
            }
            i++
        }
    }

    /**
     *
     * @param a
     * Unsorted / Sorted vector
     * @param index
     * Index vector for the sorted elements
     * @param L
     * Vector length
     * @param K
     * Number of correctly sorted positions
     */
    fun SKP_Silk_insertion_sort_decreasing(a: IntArray,  /* I/O: Unsorted / Sorted vector */
            index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int,  /* I: Vector length */
            K: Int /* I: Number of correctly sorted positions */
    ) {
        var value: Int
        var j: Int
        assert(K > 0)
        assert(L > 0)
        assert(L >= K)

        /* Write start indices in index vector */
        var i = 0
        while (i < K) {
            index[i] = i
            i++
        }

        /* Sort vector elements by value, decreasing order */
        i = 1
        while (i < K) {
            value = a[i]
            j = i - 1
            while (j >= 0 && value > a[j]) {
                a[j + 1] = a[j] /* Shift value */
                index[j + 1] = index[j] /* Shift index */
                j--
            }
            a[j + 1] = value /* Write value */
            index[j + 1] = i /* Write index */
            i++
        }

        /* If less than L values are asked for, check the remaining values, */
        /* but only spend CPU to ensure that the K first values are correct */
        i = K
        while (i < L) {
            value = a[i]
            if (value > a[K - 1]) {
                j = K - 2
                while (j >= 0 && value > a[j]) {
                    a[j + 1] = a[j] /* Shift value */
                    index[j + 1] = index[j] /* Shift index */
                    j--
                }
                a[j + 1] = value /* Write value */
                index[j + 1] = i /* Write index */
            }
            i++
        }
    }

    /**
     *
     * @param a
     * Unsorted / Sorted vector
     * @param index
     * Index vector for the sorted elements
     * @param L
     * Vector length
     * @param K
     * Number of correctly sorted positions
     */
    fun SKP_Silk_insertion_sort_decreasing_int16(a: ShortArray,  /* I/O: Unsorted / Sorted vector */
            index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int,  /* I: Vector length */
            K: Int /* I: Number of correctly sorted positions */
    ) {
        var i: Int
        var j: Int
        var value: Int
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
            value = a[i].toInt()
            j = i - 1
            while (j >= 0 && value > a[j]) {
                a[j + 1] = a[j] /* Shift value */
                index[j + 1] = index[j] /* Shift index */
                j--
            }
            a[j + 1] = value.toShort() /* Write value */
            index[j + 1] = i /* Write index */
            i++
        }

        /* If less than L values are asked for, check the remaining values, */
        /* but only spend CPU to ensure that the K first values are correct */
        i = K
        while (i < L) {
            value = a[i].toInt()
            if (value > a[K - 1]) {
                j = K - 2
                while (j >= 0 && value > a[j]) {
                    a[j + 1] = a[j] /* Shift value */
                    index[j + 1] = index[j] /* Shift index */
                    j--
                }
                a[j + 1] = value.toShort() /* Write value */
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
    fun SKP_Silk_insertion_sort_increasing_all_values(a: IntArray,  /*
																		 * I/O: Unsorted / Sorted
																		 * vector
																		 */
            a_offset: Int, L: Int /* I: Vector length */
    ) {
        var value: Int
        var i: Int
        var j: Int

        /* Safety checks */
        Typedef.SKP_assert(L > 0)

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

    /**
     *
     * @param a
     * Unsorted / Sorted vector
     * @param index
     * Index vector for the sorted elements
     * @param L
     * Vector length
     * @param K
     * Number of correctly sorted positions
     */
    fun SKP_Silk_shell_insertion_sort_increasing(a: IntArray,  /* I/O: Unsorted / Sorted vector */
            index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int,  /* I: Vector length */
            K: Int /* I: Number of correctly sorted positions */
    ) {
        var value: Int
        var inc_Q16_tmp: Int
        var i: Int
        var j: Int
        var inc: Int
        var idx: Int

        /* Safety checks */
        Typedef.SKP_assert(K > 0)
        Typedef.SKP_assert(L > 0)
        Typedef.SKP_assert(L >= K)

        /* Calculate initial step size */
        inc_Q16_tmp = L shl 15
        // inc = SKP_RSHIFT( inc_Q16_tmp, 16 );
        inc = inc_Q16_tmp shr 16

        /* Write start indices in index vector */
        i = 0
        while (i < K) {
            index[i] = i
            i++
        }

        /* Shell sort first values */
        while (inc > 0) {
            i = inc
            while (i < K) {
                value = a[i]
                idx = index[i]
                j = i - inc
                while (j >= 0 && value < a[j]) {
                    a[j + inc] = a[j] /* Shift value */
                    index[j + inc] = index[j] /* Shift index */
                    j -= inc
                }
                a[j + inc] = value /* Write value */
                index[j + inc] = idx /* Write index */
                i++
            }
            // inc_Q16_tmp = SKP_SMULWB( inc_Q16_tmp, 29789 ); // 29789_Q16 = 2.2^(-1)_Q0
            inc_Q16_tmp = Macros.SKP_SMULWB(inc_Q16_tmp, 29789) // 29789_Q16 = 2.2^(-1)_Q0

            // inc = SKP_RSHIFT_ROUND( inc_Q16_tmp, 16 );
            inc = SigProcFIX.SKP_RSHIFT_ROUND(inc_Q16_tmp, 16)
        }

        /* If less than L values are asked for, check the remaining values, */
        /* but only spend CPU to ensure that the K first values are correct */
        /* Insertion sort remaining values */
        i = K
        while (i < L) {
            value = a[i]
            if (value < a[K - 1]) {
                j = K - 2
                while (j >= 0 && value < a[j]) {
                    a[j + 1] = a[j] /* Shift value */
                    index[j + 1] = index[j] /* Shift index */
                    j--
                }
                a[j + 1] = value /* Write value */
                index[j + 1] = i /* Write index */
            }
            i++
        }
    }

    /**
     *
     * @param a
     * Unsorted / Sorted vector.
     * @param index
     * Index vector for the sorted elements.
     * @param L
     * Vector length.
     */
    fun SKP_Silk_shell_sort_increasing_all_values(a: IntArray,  /* I/O: Unsorted / Sorted vector */
            index: IntArray,  /* O: Index vector for the sorted elements */
            L: Int /* I: Vector length */
    ) {
        var value: Int
        var inc_Q16_tmp: Int
        var i: Int
        var j: Int
        var inc: Int
        var idx: Int

        /* Safety checks */
        Typedef.SKP_assert(L > 0)

        /* Calculate initial step size */
        // inc_Q16_tmp = SKP_LSHIFT( (int)L, 15 );
        inc_Q16_tmp = L shl 15
        // inc = SKP_RSHIFT( inc_Q16_tmp, 16 );
        inc = inc_Q16_tmp shr 16

        /* Write start indices in index vector */
        i = 0
        while (i < L) {
            index[i] = i
            i++
        }

        /* Sort vector elements by value, increasing order */
        while (inc > 0) {
            i = inc
            while (i < L) {
                value = a[i]
                idx = index[i]
                j = i - inc
                while (j >= 0 && value < a[j]) {
                    a[j + inc] = a[j] /* Shift value */
                    index[j + inc] = index[j] /* Shift index */
                    j -= inc
                }
                a[j + inc] = value /* Write value */
                index[j + inc] = idx /* Write index */
                i++
            }
            // inc_Q16_tmp = SKP_SMULWB( inc_Q16_tmp, 29789 ); // 29789_Q16 = 2.2^(-1)_Q0
            inc_Q16_tmp = Macros.SKP_SMULWB(inc_Q16_tmp, 29789) // 29789_Q16 = 2.2^(-1)_Q0

            // inc = SKP_RSHIFT_ROUND( inc_Q16_tmp, 16 );
            inc = SigProcFIX.SKP_RSHIFT_ROUND(inc_Q16_tmp, 16)
        }
    }
}