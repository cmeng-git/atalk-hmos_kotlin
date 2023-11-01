/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * NLSF stabilizer: - Moves NLSFs further apart if they are too close - Moves NLSFs away from
 * borders if they are too close - High effort to achieve a modification with minimum Euclidean
 * distance to input vector - Output are sorted NLSF coefficients
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object NLSFStabilize {
    /**
     * Constant Definitions.
     */
    const val MAX_LOOPS = 20

    /**
     * NLSF stabilizer, for a single input data vector.
     *
     * @param NLSF_Q15
     * Unstable/stabilized normalized LSF vector in Q15 [L].
     * @param NLSF_Q15_offset
     * offset of valid data.
     * @param NDeltaMin_Q15
     * Normalized delta min vector in Q15, NDeltaMin_Q15[L] must be >= 1 [L+1].
     * @param L
     * Number of NLSF parameters in the input vector.
     */
    fun SKP_Silk_NLSF_stabilize(NLSF_Q15: IntArray,  /*
														 * I/O: Unstable/stabilized normalized LSF
														 * vector in Q15 [L]
														 */
            NLSF_Q15_offset: Int, NDeltaMin_Q15: IntArray?,  /*
												 * I: Normalized delta min vector in Q15,
												 * NDeltaMin_Q15[L] must be >= 1 [L+1]
												 */
            L: Int /* I: Number of NLSF parameters in the input vector */
    ) {
        var center_freq_Q15: Int
        var diff_Q15: Int
        var min_center_Q15: Int
        var max_center_Q15: Int
        var min_diff_Q15: Int
        var loops: Int
        var i: Int
        var I = 0
        var k: Int

        /* This is necessary to ensure an output within range of a SKP_int16 */
        Typedef.SKP_assert(NDeltaMin_Q15!![L] >= 1)
        loops = 0
        while (loops < MAX_LOOPS) {
            /* Find smallest distance */
            /* First element */
            min_diff_Q15 = NLSF_Q15[NLSF_Q15_offset + 0] - NDeltaMin_Q15[0]
            I = 0
            /* Middle elements */
            i = 1
            while (i <= L - 1) {
                diff_Q15 = (NLSF_Q15[NLSF_Q15_offset + i]
                        - (NLSF_Q15[NLSF_Q15_offset + i - 1] + NDeltaMin_Q15[i]))
                if (diff_Q15 < min_diff_Q15) {
                    min_diff_Q15 = diff_Q15
                    I = i
                }
                i++
            }
            /* Last element */
            diff_Q15 = (1 shl 15) - (NLSF_Q15[NLSF_Q15_offset + L - 1] + NDeltaMin_Q15[L])
            if (diff_Q15 < min_diff_Q15) {
                min_diff_Q15 = diff_Q15
                I = L
            }
            /** */
            /* Now check if the smallest distance non-negative */
            /** */
            if (min_diff_Q15 >= 0) {
                return
            }
            if (I == 0) {
                /* Move away from lower limit */
                NLSF_Q15[NLSF_Q15_offset + 0] = NDeltaMin_Q15[0]
            } else if (I == L) {
                /* Move away from higher limit */
                NLSF_Q15[NLSF_Q15_offset + L - 1] = (1 shl 15) - NDeltaMin_Q15[L]
            } else {
                /* Find the lower extreme for the location of the current center frequency */
                min_center_Q15 = 0
                k = 0
                while (k < I) {
                    min_center_Q15 += NDeltaMin_Q15[k]
                    k++
                }
                min_center_Q15 += NDeltaMin_Q15[I] shr 1

                /* Find the upper extreme for the location of the current center frequency */
                max_center_Q15 = 1 shl 15
                k = L
                while (k > I) {
                    max_center_Q15 -= NDeltaMin_Q15[k]
                    k--
                }
                max_center_Q15 -= NDeltaMin_Q15[I] - (NDeltaMin_Q15[I] shr 1)

                /* Move apart, sorted by value, keeping the same center frequency */
                center_freq_Q15 = SigProcFIX.SKP_LIMIT_32(
                        SigProcFIX.SKP_RSHIFT_ROUND(NLSF_Q15[NLSF_Q15_offset + I - 1]
                                + NLSF_Q15[NLSF_Q15_offset + I], 1), min_center_Q15, max_center_Q15)
                NLSF_Q15[NLSF_Q15_offset + I - 1] = center_freq_Q15 - (NDeltaMin_Q15[I] shr 1)
                NLSF_Q15[NLSF_Q15_offset + I] = (NLSF_Q15[NLSF_Q15_offset + I - 1]
                        + NDeltaMin_Q15[I])
            }
            loops++
        }

        /* Safe and simple fall back method, which is less ideal than the above */
        if (loops == MAX_LOOPS) {
            /* Insertion sort (fast for already almost sorted arrays): */
            /* Best case: O(n) for an already sorted array */
            /* Worst case: O(n^2) for an inversely sorted array */
            Sort.SKP_Silk_insertion_sort_increasing_all_values(NLSF_Q15, NLSF_Q15_offset + 0, L)

            /* First NLSF should be no less than NDeltaMin[0] */
            NLSF_Q15[NLSF_Q15_offset + 0] = SigProcFIX.SKP_max_int(NLSF_Q15[NLSF_Q15_offset + 0],
                    NDeltaMin_Q15[0])

            /* Keep delta_min distance between the NLSFs */
            i = 1
            while (i < L) {
                NLSF_Q15[NLSF_Q15_offset + i] = SigProcFIX.SKP_max_int(
                        NLSF_Q15[NLSF_Q15_offset + i], NLSF_Q15[NLSF_Q15_offset + i - 1]
                        + NDeltaMin_Q15[i])
                i++
            }

            /* Last NLSF should be no higher than 1 - NDeltaMin[L] */
            NLSF_Q15[NLSF_Q15_offset + L - 1] = SigProcFIX.SKP_min_int(NLSF_Q15[NLSF_Q15_offset + L
                    - 1], (1 shl 15) - NDeltaMin_Q15[L])

            /* Keep NDeltaMin distance between the NLSFs */
            i = L - 2
            while (i >= 0) {
                NLSF_Q15[NLSF_Q15_offset + i] = SigProcFIX.SKP_min_int(
                        NLSF_Q15[NLSF_Q15_offset + i], NLSF_Q15[NLSF_Q15_offset + i + 1]
                        - NDeltaMin_Q15[i + 1])
                i--
            }
        }
    }

    /**
     * NLSF stabilizer, over multiple input column data vectors.
     *
     * @param NLSF_Q15 nstable/stabilized normalized LSF vectors in Q15 [LxN].
     * @param NDeltaMin_Q15 Normalized delta min vector in Q15, NDeltaMin_Q15[L] must be >= 1 [L+1].
     * @param N Number of input vectors to be stabilized.
     * @param L NLSF vector dimension.
     */
    fun SKP_Silk_NLSF_stabilize_multi(NLSF_Q15: IntArray,  /*
															 * I/O: Unstable/stabilized normalized
															 * LSF vectors in Q15 [LxN]
															 */
            NDeltaMin_Q15: IntArray?,  /*
							 * I: Normalized delta min vector in Q15, NDeltaMin_Q15[L] must be >= 1
							 * [L+1]
							 */
            N: Int,  /* I: Number of input vectors to be stabilized */
            L: Int /* I: NLSF vector dimension */
    ) {
        /* loop over input data */
        var n: Int = 0
        while (n < N) {
            SKP_Silk_NLSF_stabilize(NLSF_Q15, n * L, NDeltaMin_Q15, L)
            n++
        }
    }
}