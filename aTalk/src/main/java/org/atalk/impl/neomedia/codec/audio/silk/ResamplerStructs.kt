/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * Classes for IIR/FIR resamplers.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerStructs {
    /**
     * Flag to enable support for input/output sampling rates above 48 kHz. Turn off for embedded
     * devices.
     */
    const val RESAMPLER_SUPPORT_ABOVE_48KHZ = true
    const val SKP_Silk_RESAMPLER_MAX_FIR_ORDER = 16
    const val SKP_Silk_RESAMPLER_MAX_IIR_ORDER = 6
}

class SKP_Silk_resampler_state_struct {
    var sIIR = IntArray(ResamplerStructs.SKP_Silk_RESAMPLER_MAX_IIR_ORDER) /*
																			 * this must be the
																			 * first element of this
																			 * struct
																			 */
    var sFIR = IntArray(ResamplerStructs.SKP_Silk_RESAMPLER_MAX_FIR_ORDER)
    var sDown2 = IntArray(2)
    var resampler_function: String? = null
    var resamplerCB: ResamplerFP? = null
    fun resampler_function(state: Any?, out: ShortArray?, out_offset: Int, `in`: ShortArray, in_offset: Int,
            len: Int) {
        resamplerCB!!.resampler_function(state, out, out_offset, `in`, in_offset, len)
    }

    var up2_function: String? = null
    var up2CB: Up2FP? = null
    fun up2_function(state: IntArray?, out: ShortArray?, out_offset: Int, `in`: ShortArray, in_offset: Int, len: Int) {
        up2CB!!.up2_function(state, out, out_offset, `in`, in_offset, len)
    }

    var batchSize = 0
    var invRatio_Q16 = 0
    var FIR_Fracs = 0
    var input2x = 0
    var Coefs: ShortArray? = null
    var sDownPre = IntArray(2)
    var sUpPost = IntArray(2)
    var down_pre_function: String? = null
    var downPreCB: DownPreFP? = null
    fun down_pre_function(state: IntArray?, out: ShortArray, out_offset: Int, `in`: ShortArray, in_offset: Int,
            len: Int) {
        downPreCB!!.down_pre_function(state, out, out_offset, `in`, in_offset, len)
    }

    var up_post_function: String? = null
    var upPostCB: UpPostFP? = null
    fun up_post_function(state: IntArray?, out: ShortArray?, out_offset: Int, `in`: ShortArray, in_offset: Int,
            len: Int) {
        upPostCB!!.up_post_function(state, out, out_offset, `in`, in_offset, len)
    }

    var batchSizePrePost = 0
    var ratio_Q16 = 0
    var nPreDownsamplers = 0
    var nPostUpsamplers = 0
    var magic_number = 0

    /**
     * set all fields of the instance to zero.
     */
    fun memZero() {
        // {
        // if(this.Coefs != null)
        // {
        // Arrays.fill(this.Coefs, (short)0);
        // }
        // }
        Coefs = null
        Arrays.fill(sDown2, 0)
        Arrays.fill(sDownPre, 0)
        Arrays.fill(sFIR, 0)
        Arrays.fill(sIIR, 0)
        Arrays.fill(sUpPost, 0)
        batchSize = 0
        batchSizePrePost = 0
        down_pre_function = null
        downPreCB = null
        FIR_Fracs = 0
        input2x = 0
        invRatio_Q16 = 0
        magic_number = 0
        nPostUpsamplers = 0
        nPreDownsamplers = 0
        ratio_Q16 = 0
        resampler_function = null
        resamplerCB = null
        up2_function = null
        up2CB = null
        up_post_function = null
        upPostCB = null
    }
}

/** */
interface ResamplerFP {
    fun resampler_function(state: Any?, out: ShortArray?, out_offset: Int, `in`: ShortArray, in_offset: Int,
            len: Int)
}

interface Up2FP {
    fun up2_function(state: IntArray?, out: ShortArray?, out_offset: Int, `in`: ShortArray, in_offset: Int, len: Int)
}

interface DownPreFP {
    fun down_pre_function(state: IntArray?, out: ShortArray, out_offset: Int, `in`: ShortArray, in_offset: Int,
            len: Int)
}

interface UpPostFP {
    fun up_post_function(state: IntArray?, out: ShortArray?, out_offset: Int, `in`: ShortArray, in_offset: Int,
            len: Int)
}