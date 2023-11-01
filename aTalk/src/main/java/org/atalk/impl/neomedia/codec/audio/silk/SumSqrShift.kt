/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * compute number of bits to right shift the sum of squares of a vector of int16s to make it fit in
 * an int32
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object SumSqrShift {
    /**
     * Compute number of bits to right shift the sum of squares of a vector of int16s to make it fit
     * in an int32.
     *
     * @param energy
     * Energy of x, after shifting to the right.
     * @param shift
     * Number of bits right shift applied to energy.
     * @param x
     * Input vector.
     * @param x_offset
     * offset of valid data.
     * @param len
     * Length of input vector.
     */
    fun SKP_Silk_sum_sqr_shift(energy: IntArray,  /* O Energy of x, after shifting to the right */
            shift: IntArray,  /* O Number of bits right shift applied to energy */
            x: ShortArray,  /* I Input vector */
            x_offset: Int, len: Int /* I Length of input vector */
    ) {
        var len = len
        var i: Int
        var shft: Int
        var in32: Int
        var nrg_tmp: Int
        var nrg: Int
        if (len % 2 != 0) {
            /* Input is not 4-byte aligned */
            nrg = Macros.SKP_SMULBB(x[x_offset].toInt(), x[x_offset].toInt())
            i = 1
        } else {
            nrg = 0
            i = 0
        }
        shft = 0
        len--
        while (i < len) {
            /* Load two values at once */
            in32 = x[x_offset + i].toInt() and 0xFFFF shl 16 or (x[x_offset + i + 1].toInt() and 0xFFFF)
            nrg = SigProcFIX.SKP_SMLABB_ovflw(nrg, in32, in32)
            nrg = SigProcFIX.SKP_SMLATT_ovflw(nrg, in32, in32)
            i += 2
            if (nrg < 0) {
                /* Scale down */
                nrg = nrg ushr 2
                shft = 2
                break
            }
        }
        while (i < len) {

            /* Load two values at once */
            in32 = x[x_offset + i].toInt() and 0xFFFF shl 16 or (x[x_offset + i + 1].toInt() and 0xFFFF)
            nrg_tmp = Macros.SKP_SMULBB(in32, in32)
            nrg_tmp = SigProcFIX.SKP_SMLATT_ovflw(nrg_tmp, in32, in32)
            nrg += (nrg_tmp ushr shft)
            if (nrg < 0) {
                /* Scale down */
                nrg = nrg ushr 2
                shft += 2
            }
            i += 2
        }
        if (i == len) {
            /* One sample left to process */
            nrg_tmp = Macros.SKP_SMULBB(x[x_offset + i].toInt(), x[x_offset + i].toInt())
            nrg += (nrg_tmp ushr shft)
        }

        /* Make sure to have at least one extra leading zero (two leading zeros in total) */
        if (nrg and -0x40000000 != 0) {
            nrg = nrg ushr 2
            shft += 2
        }

        /* Output arguments */
        shift[0] = shft
        energy[0] = nrg
    }
}