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
object CodeSigns {
    /* shifting avoids if-statement */ // #define SKP_enc_map(a) ( SKP_RSHIFT( (a), 15 ) + 1 )
    fun SKP_enc_map(a: Int): Int {
        return (a shr 15) + 1
    }

    // #define SKP_dec_map(a) ( SKP_LSHIFT( (a), 1 ) - 1 )
    fun SKP_dec_map(a: Int): Int {
        return (a shl 1) - 1
    }

    /**
     * Encodes signs of excitation.
     *
     * @param sRC
     * Range coder state.
     * @param q
     * Pulse signal.
     * @param length
     * Length of input.
     * @param sigtype
     * Signal type.
     * @param QuantOffsetType
     * QuantOffsetType.
     * @param RateLevelIndex
     * Rate level index.
     */
    fun SKP_Silk_encode_signs(sRC: SKP_Silk_range_coder_state?,  /* I/O Range coder state */
            q: ByteArray?,  /* I Pulse signal */
            length: Int,  /* I Length of input */
            sigtype: Int,  /* I Signal type */
            QuantOffsetType: Int,  /* I Quantization offset type */
            RateLevelIndex: Int /* I Rate level index */
    ) {
        var i: Int
        var inData: Int
        val cdf = IntArray(3)
        i = Macros.SKP_SMULBB(Define.N_RATE_LEVELS - 1, (sigtype shl 1) + QuantOffsetType) + RateLevelIndex
        cdf[0] = 0
        cdf[1] = TablesSign.SKP_Silk_sign_CDF[i]
        cdf[2] = 65535
        i = 0
        while (i < length) {
            if (q!![i].toInt() != 0) {
                // inData = SKP_enc_map( q[ i ] ); /* - = 0, + = 1 */
                inData = (q[i].toInt() shr 15) + 1 /* - = 0, + = 1 */
                RangeCoder.SKP_Silk_range_encoder(sRC, inData, cdf, 0)
            }
            i++
        }
    }

    /**
     * Decodes signs of excitation.
     *
     * @param sRC
     * Range coder state.
     * @param q
     * pulse signal.
     * @param length
     * length of output.
     * @param sigtype
     * Signal type.
     * @param QuantOffsetType
     * Quantization offset type.
     * @param RateLevelIndex
     * Rate Level Index.
     */
    fun SKP_Silk_decode_signs(sRC: SKP_Silk_range_coder_state?,  /* I/O Range coder state */
            q: IntArray,  /* I/O pulse signal */
            length: Int,  /* I length of output */
            sigtype: Int,  /* I Signal type */
            QuantOffsetType: Int,  /* I Quantization offset type */
            RateLevelIndex: Int /* I Rate Level Index */
    ) {
        var i: Int
        var data: Int
        val data_ptr = IntArray(1)
        val cdf = IntArray(3)
        i = Macros.SKP_SMULBB(Define.N_RATE_LEVELS - 1, (sigtype shl 1) + QuantOffsetType) + RateLevelIndex
        cdf[0] = 0
        cdf[1] = TablesSign.SKP_Silk_sign_CDF[i]
        cdf[2] = 65535
        i = 0
        while (i < length) {
            if (q[i] > 0) {
                RangeCoder.SKP_Silk_range_decoder(data_ptr, 0, sRC, cdf, 0, 1)
                data = data_ptr[0]
                /* attach sign */
                /* implementation with shift, subtraction, multiplication */
                // q[ i ] *= SKP_dec_map( data );
                q[i] *= (data shl 1) - 1
            }
            i++
        }
    }
}