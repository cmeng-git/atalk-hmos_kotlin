/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object GainQuant {
    const val OFFSET = Define.MIN_QGAIN_DB * 128 / 6 + 16 * 128
    const val SCALE_Q16 = 65536 * (Define.N_LEVELS_QGAIN - 1) / ((Define.MAX_QGAIN_DB - Define.MIN_QGAIN_DB) * 128 / 6)
    const val INV_SCALE_Q16 = 65536 * ((Define.MAX_QGAIN_DB - Define.MIN_QGAIN_DB) * 128 / 6) / (Define.N_LEVELS_QGAIN - 1)

    /**
     * Gain scalar quantization with hysteresis, uniform on log scale.
     *
     * @param ind
     * gain indices
     * @param gain_Q16
     * gains (quantized out)
     * @param prev_ind
     * last index in previous frame
     * @param conditional
     * first gain is delta coded if 1
     */
    fun SKP_Silk_gains_quant(ind: IntArray?,  /* O gain indices */
            gain_Q16: IntArray,  /* I/O gains (quantized out) */
            prev_ind: IntArray,  /* I/O last index in previous frame */
            conditional: Int /* I first gain is delta coded if 1 */
    ) {
        var k = 0
        while (k < Define.NB_SUBFR) {

            /* Add half of previous quantization error, convert to log scale, scale, floor() */
            ind!![k] = Macros.SKP_SMULWB(SCALE_Q16, Lin2log.SKP_Silk_lin2log(gain_Q16[k]) - OFFSET)

            /* Round towards previous quantized gain (hysteresis) */
            if (ind[k] < prev_ind[0]) {
                ind[k]++
            }

            /* Compute delta indices and limit */
            if (k == 0 && conditional == 0) {
                /* Full index */
                ind[k] = SigProcFIX.SKP_LIMIT_int(ind[k], 0, Define.N_LEVELS_QGAIN - 1)
                ind[k] = Math.max(ind[k], prev_ind[0] + Define.MIN_DELTA_GAIN_QUANT)
                prev_ind[0] = ind[k]
            } else {
                /* Delta index */
                ind[k] = SigProcFIX.SKP_LIMIT_int(ind[k] - prev_ind[0], Define.MIN_DELTA_GAIN_QUANT,
                        Define.MAX_DELTA_GAIN_QUANT)
                /* Accumulate deltas */
                prev_ind[0] += ind[k]
                /* Shift to make non-negative */
                ind[k] -= Define.MIN_DELTA_GAIN_QUANT
            }

            /* Convert to linear scale and scale */
            gain_Q16[k] = Log2lin.SKP_Silk_log2lin(Math.min(Macros.SKP_SMULWB(INV_SCALE_Q16, prev_ind[0])
                    + OFFSET, 3967)) /*
								 * 3967 = 31 in Q7
								 */
            k++
        }
    }

    /**
     * Gains scalar dequantization, uniform on log scale.
     *
     * @param gain_Q16
     * quantized gains.
     * @param ind
     * gain indices.
     * @param prev_ind
     * last index in previous frame.
     * @param conditional
     * first gain is delta coded if 1.
     */
    fun SKP_Silk_gains_dequant(gain_Q16: IntArray?,  /* O quantized gains */
            ind: IntArray?,  /* I gain indices */
            prev_ind: IntArray,  /* I/O last index in previous frame */
            conditional: Int /* I first gain is delta coded if 1 */
    ) {
        var k: Int
        k = 0
        while (k < Define.NB_SUBFR) {
            if (k == 0 && conditional == 0) {
                prev_ind[0] = ind!![k]
            } else {
                /* Delta index */
                prev_ind[0] += ind!![k] + Define.MIN_DELTA_GAIN_QUANT
            }

            /* Convert to linear scale and scale */
            gain_Q16!![k] = Log2lin.SKP_Silk_log2lin(Math.min(Macros.SKP_SMULWB(INV_SCALE_Q16, prev_ind[0])
                    + OFFSET, 3967)) /*
								 * 3967 = 31 in Q7
								 */
            k++
        }
    }
}