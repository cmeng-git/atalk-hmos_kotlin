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
class TablesOtherFLP {
    var SKP_Silk_Quantization_Offsets = arrayOf(floatArrayOf(Define.OFFSET_VL_Q10 / 1024.0f, Define.OFFSET_VH_Q10 / 1024.0f), floatArrayOf(Define.OFFSET_UVL_Q10 / 1024.0f, Define.OFFSET_UVH_Q10 / 1024.0f))

    companion object {
        var SKP_Silk_HarmShapeFIR_FLP = floatArrayOf(16384.0f / 65536.0f, 32767.0f / 65536.0f,
                16384.0f / 65536.0f)
    }
}