/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Simple opy.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivateCopy {
    /**
     * Simple copy.
     *
     * @param SS
     * Resampler state (unused).
     * @param out
     * Output signal
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal
     * @param in_offset
     * offset of valid data.
     * @param inLen
     * Number of input samples
     */
    fun SKP_Silk_resampler_private_copy(SS: Any?,  /* I/O: Resampler state (unused) */
            out: ShortArray?,  /* O: Output signal */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal */
            in_offset: Int, inLen: Int /* I: Number of input samples */
    ) {
        for (k in 0 until inLen) out!![out_offset + k] = `in`[in_offset + k]
    }
}