/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * Interface to collection of resamplers.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
/*
 * Matrix of resampling methods used: Fs_out (kHz) 8 12 16 24 32 44.1 48
 * 
 * 8 C UF U UF UF UF UF 12 AF C UF U UF UF UF 16 D AF C UF U UF UF Fs_in (kHz) 24 AIF D AF C UF UF U
 * 32 UF AF D AF C UF UF 44.1 AMI AMI AMI AMI AMI C UF 48 DAF DAF AF D AF UF C
 * 
 * default method: UF
 * 
 * C -> Copy (no resampling) D -> Allpass-based 2x downsampling U -> Allpass-based 2x upsampling DAF
 * -> Allpass-based 2x downsampling followed by AR2 filter followed by FIR interpolation UF ->
 * Allpass-based 2x upsampling followed by FIR interpolation AMI -> ARMA4 filter followed by FIR
 * interpolation AF -> AR2 filter followed by FIR interpolation
 * 
 * Input signals sampled above 48 kHz are first downsampled to at most 48 kHz. Output signals
 * sampled above 48 kHz are upsampled from at most 48 kHz.
 */
object Resampler {
    /**
     * Greatest common divisor.
     *
     * @param a
     * @param b
     */
    fun gcd(a: Int, b: Int): Int {
        var a = a
        var b = b
        var tmp: Int
        while (b > 0) {
            tmp = a - b * (a / b)
            a = b
            b = tmp
        }
        return a
    }

    /**
     * Initialize/reset the resampler state for a given pair of input/output sampling rates.
     *
     * @param S
     * resampler state.
     * @param Fs_Hz_in
     * Input sampling rate (Hz).
     * @param Fs_Hz_out
     * Output sampling rate (Hz).
     * @return
     */
    fun SKP_Silk_resampler_init(S: SKP_Silk_resampler_state_struct?,  /* I/O: Resampler state */
            Fs_Hz_in: Int,  /* I: Input sampling rate (Hz) */
            Fs_Hz_out: Int /* I: Output sampling rate (Hz) */
    ): Int {
        var Fs_Hz_in = Fs_Hz_in
        var Fs_Hz_out = Fs_Hz_out
        val cycleLen: Int
        val cyclesPerBatch: Int
        var up2 = 0
        var down2 = 0

        /* Clear state */
        // TODO:how to set all fields of S to 0?
        // S = new SKP_Silk_resampler_state_struct();
        S!!.memZero()

        /* Input checking */
        if (ResamplerStructs.RESAMPLER_SUPPORT_ABOVE_48KHZ) {
            if (Fs_Hz_in < 8000 || Fs_Hz_in > 192000 || Fs_Hz_out < 8000 || Fs_Hz_out > 192000) {
                assert(false)
                return -1
            }
        } else {
            if (Fs_Hz_in < 8000 || Fs_Hz_in > 48000 || Fs_Hz_out < 8000 || Fs_Hz_out > 48000) {
                assert(false)
                return -1
            }
        }
        if (ResamplerStructs.RESAMPLER_SUPPORT_ABOVE_48KHZ) {
            /* Determine pre downsampling and post upsampling */
            if (Fs_Hz_in > 96000) {
                S.nPreDownsamplers = 2
                S.down_pre_function = "SKP_Silk_resampler_private_down4"
                S.downPreCB = DownPreImplDown4()
            } else if (Fs_Hz_in > 48000) {
                S.nPreDownsamplers = 1
                S.down_pre_function = "SKP_Silk_resampler_down2"
                S.downPreCB = DownPreImplDown2()
            } else {
                S.nPreDownsamplers = 0
                S.down_pre_function = null
                S.downPreCB = null
            }
            if (Fs_Hz_out > 96000) {
                S.nPostUpsamplers = 2
                S.up_post_function = "SKP_Silk_resampler_private_up4"
                S.upPostCB = UpPostImplUp4()
            } else if (Fs_Hz_out > 48000) {
                S.nPostUpsamplers = 1
                S.up_post_function = "SKP_Silk_resampler_up2"
                S.upPostCB = UpPostImplUp2()
            } else {
                S.nPostUpsamplers = 0
                S.up_post_function = null
                S.upPostCB = null
            }
            if (S.nPreDownsamplers + S.nPostUpsamplers > 0) {
                /* Ratio of output/input samples */
                S.ratio_Q16 = (Fs_Hz_out shl 13) / Fs_Hz_in shl 3
                /* Make sure the ratio is rounded up */
                while (Macros.SKP_SMULWW(S.ratio_Q16, Fs_Hz_in) < Fs_Hz_out) S.ratio_Q16++

                /* Batch size is 10 ms */
                S.batchSizePrePost = Fs_Hz_in / 100

                /* Convert sampling rate to those after pre-downsampling and before post-upsampling */
                Fs_Hz_in = Fs_Hz_in shr S.nPreDownsamplers
                Fs_Hz_out = Fs_Hz_out shr S.nPostUpsamplers
            }
        }

        /* Number of samples processed per batch */
        /* First, try 10 ms frames */
        S.batchSize = Fs_Hz_in / 100
        if (S.batchSize * 100 != Fs_Hz_in || Fs_Hz_in % 100 != 0) {
            /*
			 * No integer number of input or output samples with 10 ms frames, use greatest common
			 * divisor
			 */
            cycleLen = Fs_Hz_in / gcd(Fs_Hz_in, Fs_Hz_out)
            cyclesPerBatch = ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN / cycleLen
            if (cyclesPerBatch == 0) {
                /*
				 * cycleLen too big, let's just use the maximum batch size. Some distortion will
				 * result.
				 */
                S.batchSize = ResamplerPrivate.RESAMPLER_MAX_BATCH_SIZE_IN
                assert(false)
            } else {
                S.batchSize = cyclesPerBatch * cycleLen
            }
        }

        /* Find resampler with the right sampling ratio */
        if (Fs_Hz_out > Fs_Hz_in) {
            /* Upsample */
            if (Fs_Hz_out == Fs_Hz_in * 2) { /* Fs_out : Fs_in = 2 : 1 */
                /* Special case: directly use 2x upsampler */
                S.resampler_function = "SKP_Silk_resampler_private_up2_HQ_wrapper"
                S.resamplerCB = ResamplerImplWrapper()
            } else {
                /* Default resampler */
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
                up2 = 1
                if (Fs_Hz_in > 24000) {
                    /* Low-quality all-pass upsampler */
                    S.up2_function = "SKP_Silk_resampler_up2"
                    S.up2CB = Up2ImplUp2()
                } else {
                    /* High-quality all-pass upsampler */
                    S.up2_function = "SKP_Silk_resampler_private_up2_HQ"
                    S.up2CB = Up2ImplHQ()
                }
            }
        } else if (Fs_Hz_out < Fs_Hz_in) {
            /* Downsample */
            if (Fs_Hz_out * 4 == Fs_Hz_in * 3) { /* Fs_out : Fs_in = 3 : 4 */
                S.FIR_Fracs = 3
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_3_4_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 3 == Fs_Hz_in * 2) { /* Fs_out : Fs_in = 2 : 3 */
                S.FIR_Fracs = 2
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_2_3_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 2 == Fs_Hz_in) { /* Fs_out : Fs_in = 1 : 2 */
                S.FIR_Fracs = 1
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_1_2_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 8 == Fs_Hz_in * 3) { /* Fs_out : Fs_in = 3 : 8 */
                S.FIR_Fracs = 3
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_3_8_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 3 == Fs_Hz_in) { /* Fs_out : Fs_in = 1 : 3 */
                S.FIR_Fracs = 1
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_1_3_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 4 == Fs_Hz_in) { /* Fs_out : Fs_in = 1 : 4 */
                S.FIR_Fracs = 1
                down2 = 1
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_1_2_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 6 == Fs_Hz_in) { /* Fs_out : Fs_in = 1 : 6 */
                S.FIR_Fracs = 1
                down2 = 1
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_1_3_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_down_FIR"
                S.resamplerCB = ResamplerImplDownFIR()
            } else if (Fs_Hz_out * 441 == Fs_Hz_in * 80) { /* Fs_out : Fs_in = 80 : 441 */
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_80_441_ARMA4_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
            } else if (Fs_Hz_out * 441 == Fs_Hz_in * 120) { /* Fs_out : Fs_in = 120 : 441 */
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_120_441_ARMA4_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
            } else if (Fs_Hz_out * 441 == Fs_Hz_in * 160) { /* Fs_out : Fs_in = 160 : 441 */
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_160_441_ARMA4_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
            } else if (Fs_Hz_out * 441 == Fs_Hz_in * 240) { /* Fs_out : Fs_in = 240 : 441 */
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_240_441_ARMA4_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
            } else if (Fs_Hz_out * 441 == Fs_Hz_in * 320) { /* Fs_out : Fs_in = 320 : 441 */
                S.Coefs = ResamplerRom.SKP_Silk_Resampler_320_441_ARMA4_COEFS
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
            } else {
                /* Default resampler */
                S.resampler_function = "SKP_Silk_resampler_private_IIR_FIR"
                S.resamplerCB = ResamplerImplIIRFIR()
                up2 = 1
                if (Fs_Hz_in > 24000) {
                    /* Low-quality all-pass upsampler */
                    S.up2_function = "SKP_Silk_resampler_up2"
                    S.up2CB = Up2ImplUp2()
                } else {
                    /* High-quality all-pass upsampler */
                    S.up2_function = "SKP_Silk_resampler_private_up2_HQ"
                    S.up2CB = Up2ImplHQ()
                }
            }
        } else {
            /* Input and output sampling rates are equal: copy */
            S.resampler_function = "SKP_Silk_resampler_private_copy"
            S.resamplerCB = ResamplerImplCopy()
        }
        S.input2x = up2 or down2

        /* Ratio of input/output samples */
        S.invRatio_Q16 = (Fs_Hz_in shl 14 + up2 - down2) / Fs_Hz_out shl 2
        /* Make sure the ratio is rounded up */
        while (Macros.SKP_SMULWW(S.invRatio_Q16, Fs_Hz_out shl down2) < Fs_Hz_in shl up2) {
            S.invRatio_Q16++
        }
        S.magic_number = 123456789
        return 0
    }

    /**
     * Clear the states of all resampling filters, without resetting sampling rate ratio.
     *
     * @param S
     * Resampler state.
     * @return
     */
    fun SKP_Silk_resampler_clear(S: SKP_Silk_resampler_state_struct /* I/O: Resampler state */
    ): Int {
        Arrays.fill(S.sDown2, 0)
        Arrays.fill(S.sIIR, 0)
        Arrays.fill(S.sFIR, 0)
        if (ResamplerStructs.RESAMPLER_SUPPORT_ABOVE_48KHZ) {
            Arrays.fill(S.sDownPre, 0)
            Arrays.fill(S.sUpPost, 0)
        }
        return 0
    }

    /**
     * Resampler: convert from one sampling rate to another.
     *
     * @param S
     * Resampler state.
     * @param out
     * Output signal.
     * @param out_offset
     * offset of vaild data.
     * @param in
     * Input signal.
     * @param in_offset
     * offset of valid data.
     * @param inLen
     * Number of input samples
     * @return
     */
    fun SKP_Silk_resampler(S: SKP_Silk_resampler_state_struct?,  /* I/O: Resampler state */
            out: ShortArray?,  /* O: Output signal */
            out_offset: Int, `in`: ShortArray,  /* I: Input signal */
            in_offset: Int, inLen: Int /* I: Number of input samples */
    ): Int {
        /* Verify that state was initialized and has not been corrupted */
        var out_offset = out_offset
        var in_offset = in_offset
        var inLen = inLen
        if (S!!.magic_number != 123456789) {
            assert(false)
            return -1
        }
        if (ResamplerStructs.RESAMPLER_SUPPORT_ABOVE_48KHZ) {
            if (S.nPreDownsamplers + S.nPostUpsamplers > 0) {
                /* The input and/or output sampling rate is above 48000 Hz */
                var nSamplesIn: Int
                var nSamplesOut: Int
                val in_buf = ShortArray(480)
                val out_buf = ShortArray(480)
                while (inLen > 0) {
                    /* Number of input and output samples to process */
                    nSamplesIn = SigProcFIX.SKP_min(inLen, S.batchSizePrePost)
                    nSamplesOut = Macros.SKP_SMULWB(S.ratio_Q16, nSamplesIn)
                    Typedef.SKP_assert(nSamplesIn shr S.nPreDownsamplers <= 480)
                    Typedef.SKP_assert(nSamplesOut shr S.nPostUpsamplers <= 480)
                    if (S.nPreDownsamplers > 0) {
                        S.down_pre_function(S.sDownPre, in_buf, 0, `in`, in_offset, nSamplesIn)
                        if (S.nPostUpsamplers > 0) {
                            S.resampler_function(S, out_buf, 0, in_buf, 0,
                                    nSamplesIn shr S.nPreDownsamplers)
                            S.up_post_function(S.sUpPost, out, out_offset, out_buf, 0,
                                    nSamplesOut shr S.nPostUpsamplers)
                        } else {
                            S.resampler_function(S, out, out_offset, in_buf, 0,
                                    nSamplesIn shr S.nPreDownsamplers)
                        }
                    } else {
                        S.resampler_function(S, out_buf, 0, `in`, in_offset,
                                nSamplesIn shr S.nPreDownsamplers)
                        S.up_post_function(S.sUpPost, out, out_offset, out_buf, 0,
                                nSamplesOut shr S.nPostUpsamplers)
                    }
                    in_offset += nSamplesIn
                    out_offset += nSamplesOut
                    inLen -= nSamplesIn
                }
            } else {
                /* Input and output sampling rate are at most 48000 Hz */
                S.resampler_function(S, out, out_offset, `in`, in_offset, inLen)
            }
        } else {
            /* Input and output sampling rate are at most 48000 Hz */
            S.resampler_function(S, out, out_offset, `in`, in_offset, inLen)
        }
        return 0
    }
}

/** */
internal class DownPreImplDown4 : DownPreFP {
    override fun down_pre_function(state: IntArray?, out: ShortArray, outOffset: Int, `in`: ShortArray,
            inOffset: Int, len: Int) {
        ResamplerPrivateDown4.SKP_Silk_resampler_private_down4(state, 0, out, outOffset, `in`,
                inOffset, len)
    }
}

internal class DownPreImplDown2 : DownPreFP {
    override fun down_pre_function(state: IntArray?, out: ShortArray, outOffset: Int, `in`: ShortArray,
            inOffset: Int, len: Int) {
        ResamplerDown2.SKP_Silk_resampler_down2(state, 0, out, outOffset, `in`, inOffset, len)
    }
} /*----------------------------------------------------------------*/

internal class UpPostImplUp4 : UpPostFP {
    override fun up_post_function(state: IntArray?, out: ShortArray?, outOffset: Int, `in`: ShortArray, inOffset: Int,
            len: Int) {
        ResamplerPrivateUp4.SKP_Silk_resampler_private_up4(state, 0, out, outOffset, `in`, inOffset,
                len)
    }
}

internal class UpPostImplUp2 : UpPostFP {
    override fun up_post_function(state: IntArray?, out: ShortArray?, outOffset: Int, `in`: ShortArray, inOffset: Int,
            len: Int) {
        ResamplerUp2.SKP_Silk_resampler_up2(state, 0, out, outOffset, `in`, inOffset, len)
    }
} /*----------------------------------------------------------------*/

internal class ResamplerImplWrapper : ResamplerFP {
    override fun resampler_function(state: Any?, out: ShortArray?, outOffset: Int, `in`: ShortArray,
            inOffset: Int, len: Int) {
        ResamplerPrivateUp2HQ.SKP_Silk_resampler_private_up2_HQ_wrapper(state, out, outOffset, `in`,
                inOffset, len)
    }
}

internal class ResamplerImplIIRFIR : ResamplerFP {
    override fun resampler_function(state: Any?, out: ShortArray?, outOffset: Int, `in`: ShortArray,
            inOffset: Int, len: Int) {
        ResamplerPrivateIIRFIR.SKP_Silk_resampler_private_IIR_FIR(state, out, outOffset, `in`,
                inOffset, len)
    }
}

internal class ResamplerImplDownFIR : ResamplerFP {
    override fun resampler_function(state: Any?, out: ShortArray?, outOffset: Int, `in`: ShortArray,
            inOffset: Int, len: Int) {
        ResamplerPrivateDownFIR.SKP_Silk_resampler_private_down_FIR(state, out, outOffset, `in`,
                inOffset, len)
    }
}

internal class ResamplerImplCopy : ResamplerFP {
    override fun resampler_function(state: Any?, out: ShortArray?, outOffset: Int, `in`: ShortArray,
            inOffset: Int, len: Int) {
        ResamplerPrivateCopy.SKP_Silk_resampler_private_copy(state, out, outOffset, `in`, inOffset,
                len)
    }
} /*----------------------------------------------------------------*/

internal class Up2ImplUp2 : Up2FP {
    override fun up2_function(state: IntArray?, out: ShortArray?, outOffset: Int, `in`: ShortArray, inOffset: Int,
            len: Int) {
        ResamplerUp2.SKP_Silk_resampler_up2(state, 0, out, outOffset, `in`, inOffset, len)
    }
}

internal class Up2ImplHQ : Up2FP {
    override fun up2_function(state: IntArray?, out: ShortArray?, outOffset: Int, `in`: ShortArray, inOffset: Int,
            len: Int) {
        ResamplerPrivateUp2HQ.SKP_Silk_resampler_private_up2_HQ(state, 0, out, outOffset, `in`,
                inOffset, len)
    }
}