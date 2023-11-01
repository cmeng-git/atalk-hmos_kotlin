/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Range coder
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object RangeCoder {
    /**
     * Range encoder for one symbol.
     *
     * @param psRC
     * compressor data structure.
     * @param data
     * uncompressed data.
     * @param prob
     * cumulative density functions.
     * @param prob_offset
     * offset of valid data.
     */
    fun SKP_Silk_range_encoder(psRC: SKP_Silk_range_coder_state?,  /*
																		 * I/O compressor data
																		 * structure
																		 */
            data: Int,  /* I uncompressed data */
            prob: IntArray?,  /* I cumulative density functions */
            prob_offset: Int) {
        val low_Q16: Long
        val high_Q16: Long
        val base_tmp: Long
        val range_Q32: Long

        /* Copy structure data */
        var base_Q32 = psRC!!.base_Q32
        var range_Q16 = psRC.range_Q16
        var bufferIx = psRC.bufferIx
        val buffer = psRC.buffer
        if (psRC.error != 0) {
            return
        }

        /* Update interval */
        low_Q16 = prob!![prob_offset + data].toLong()
        high_Q16 = prob[prob_offset + data + 1].toLong()
        base_tmp = base_Q32 /* save current base, to test for carry */

        // TODO: base_Q32 should be 32-bit
        // base_Q32 += ( range_Q16 * low_Q16 ) & 0xFFFFFFFFL;
        base_Q32 += range_Q16 * low_Q16 and 0xFFFFFFFFL
        base_Q32 = base_Q32 and 0xFFFFFFFFL
        range_Q32 = range_Q16 * (high_Q16 - low_Q16) and 0xFFFFFFFFL

        /* Check for carry */
        if (base_Q32 < base_tmp) {
            /* Propagate carry in buffer */
            var bufferIx_tmp = bufferIx
            while ((++buffer[--bufferIx_tmp]).toInt() == 0);
        }

        /* Check normalization */
        if (range_Q32 and 0xFF000000L != 0L) // if( (range_Q32 & 0xFF000000)!=0 )
        {
            /* No normalization */
            // range_Q16 = ( range_Q32 >> 16 );
            range_Q16 = range_Q32 ushr 16
        } else {
            if (range_Q32 and 0xFFFF0000L != 0L) // if( (range_Q32 & 0xFFFF0000)!=0 )
            {
                /* Normalization of 8 bits shift */
                // range_Q16 = ( range_Q32 >> 8 );
                range_Q16 = range_Q32 ushr 8
            } else {
                /* Normalization of 16 bits shift */
                range_Q16 = range_Q32
                /* Make sure not to write beyond buffer */
                if (bufferIx >= psRC.bufferLength) {
                    psRC.error = Define.RANGE_CODER_WRITE_BEYOND_BUFFER
                    return
                }
                /* Write one byte to buffer */
                buffer[bufferIx++] = (base_Q32 ushr 24).toByte()
                // base_Q32 = base_Q32 << 8;
                base_Q32 = base_Q32 shl 8 and 0xFFFFFFFFL
            }
            /* Make sure not to write beyond buffer */
            if (bufferIx >= psRC.bufferLength) {
                psRC.error = Define.RANGE_CODER_WRITE_BEYOND_BUFFER
                return
            }
            /* Write one byte to buffer */
            buffer[bufferIx++] = (base_Q32 ushr 24).toByte()
            // base_Q32 = base_Q32 << 8;
            base_Q32 = base_Q32 shl 8 and 0xFFFFFFFFL
        }

        /* Copy structure data back */
        psRC.base_Q32 = base_Q32
        psRC.range_Q16 = range_Q16
        psRC.bufferIx = bufferIx
    }

    /**
     * Range encoder for multiple symbols.
     *
     * @param psRC
     * compressor data structure.
     * @param data
     * uncompressed data [nSymbols].
     * @param prob
     * cumulative density functions.
     * @param nSymbols
     * number of data symbols.
     */
    fun SKP_Silk_range_encoder_multi(psRC: SKP_Silk_range_coder_state?,
            /*
             * I/O compressor data structure
             */
            data: IntArray?,  /* I uncompressed data [nSymbols] */
            prob: Array<IntArray>?,  /* I cumulative density functions */
            nSymbols: Int /* I number of data symbols */
    ) {
        var k: Int
        k = 0
        while (k < nSymbols) {
            SKP_Silk_range_encoder(psRC, data!![k], prob!![k], 0)
            k++
        }
    }

    /**
     * Range decoder for one symbol.
     *
     * @param data
     * uncompressed data.
     * @param data_offset
     * offset of valid data.
     * @param psRC
     * compressor data structure.
     * @param prob
     * cumulative density function.
     * @param prob_offset
     * offset of valid data.
     * @param probIx1
     * initial (middle) entry of cdf.
     */
    fun SKP_Silk_range_decoder(data: IntArray,  /* O uncompressed data */
            data_offset: Int, psRC: SKP_Silk_range_coder_state?,  /* I/O compressor data structure */
            prob: IntArray?,  /* I cumulative density function */
            prob_offset: Int, probIx1: Int /* I initial (middle) entry of cdf */
    ) {
        var probIx = probIx1
        var low_Q16: Long
        var high_Q16: Long
        var base_tmp: Long
        var range_Q32: Long

        /* Copy structure data */
        var base_Q32 = psRC!!.base_Q32
        var range_Q16 = psRC.range_Q16
        var bufferIx = psRC.bufferIx
        val buffer = psRC.buffer
        val buffer_offset = 4
        if (psRC.error != 0) {
            /* Set output to zero */
            data[data_offset + 0] = 0
            return
        }
        high_Q16 = prob!![prob_offset + probIx].toLong()

        // TODO base_tmp = SigProcFIX.SKP_MUL_uint( range_Q16, high_Q16 );
        base_tmp = range_Q16 * high_Q16 and 0xFFFFFFFFL
        if (base_tmp > base_Q32) {
            while (true) {
                low_Q16 = prob[--probIx + prob_offset].toLong()
                // base_tmp = SigProcFIX.SKP_MUL_uint( range_Q16, low_Q16 );
                base_tmp = range_Q16 * low_Q16 and 0xFFFFFFFFL
                if (base_tmp <= base_Q32) {
                    break
                }
                high_Q16 = low_Q16
                /* Test for out of range */
                if (high_Q16 == 0L) {
                    psRC.error = Define.RANGE_CODER_CDF_OUT_OF_RANGE
                    /* Set output to zero */
                    data[data_offset + 0] = 0
                    return
                }
            }
        } else {
            while (true) {
                low_Q16 = high_Q16
                high_Q16 = prob[++probIx + prob_offset].toLong()
                // base_tmp = SigProcFIX.SKP_MUL_uint( range_Q16, high_Q16 );
                base_tmp = range_Q16 * high_Q16 and 0xFFFFFFFFL
                if (base_tmp > base_Q32) {
                    probIx--
                    break
                }
                /* Test for out of range */
                if (high_Q16 == 0xFFFFL) {
                    psRC.error = Define.RANGE_CODER_CDF_OUT_OF_RANGE
                    /* Set output to zero */
                    data[data_offset + 0] = 0
                    return
                }
            }
        }
        data[data_offset + 0] = probIx

        // base_Q32 -= SigProcFIX.SKP_MUL_uint( range_Q16, low_Q16 );
        base_Q32 -= range_Q16 * low_Q16 and 0xFFFFFFFFL
        base_Q32 = base_Q32 and 0xFFFFFFFFL
        // range_Q32 = SigProcFIX.SKP_MUL_uint( range_Q16, high_Q16 - low_Q16 );
        range_Q32 = range_Q16 * (high_Q16 - low_Q16) and 0xFFFFFFFFL
        range_Q32 = range_Q32 and 0xFFFFFFFFL

        /* Check normalization */
        // TODO:or if( (range_Q32 & 0xFF000000) != 0 ) {
        if (range_Q32 and 0xFF000000L != 0L) {
            /* No normalization */
            range_Q16 = range_Q32 ushr 16
        } else {
            // TODO:or if( (range_Q32 & 0xFFFF0000) != 0 ) {
            if (range_Q32 and 0xFFFF0000L != 0L) {
                /* Normalization of 8 bits shift */
                range_Q16 = range_Q32 ushr 8
                /* Check for errors */
                if (base_Q32 ushr 24 != 0L) {
                    psRC.error = Define.RANGE_CODER_NORMALIZATION_FAILED
                    /* Set output to zero */
                    data[data_offset + 0] = 0
                    return
                }
            } else {
                /* Normalization of 16 bits shift */
                range_Q16 = range_Q32
                /* Check for errors */
                // TODO: if( ( base_Q32 >> 16 ) != 0 ) {
                if (base_Q32 ushr 16 != 0L) {
                    psRC.error = Define.RANGE_CODER_NORMALIZATION_FAILED
                    /* Set output to zero */
                    data[data_offset + 0] = 0
                    return
                }
                /* Update base */base_Q32 = base_Q32 shl 8
                base_Q32 = base_Q32 and 0xFFFFFFFFL
                /* Make sure not to read beyond buffer */
                if (bufferIx < psRC.bufferLength) {
                    /* Read one byte from buffer */
                    base_Q32 = base_Q32 or (buffer[buffer_offset + bufferIx++].toInt() and 0xFF).toLong()
                }
            }
            /* Update base */base_Q32 = base_Q32 shl 8
            base_Q32 = base_Q32 and 0xFFFFFFFFL
            /* Make sure not to read beyond buffer */
            if (bufferIx < psRC.bufferLength) {
                /* Read one byte from buffer */
                base_Q32 = base_Q32 or (buffer[buffer_offset + bufferIx++].toInt() and 0xFF).toLong()
            }
        }

        /* Check for zero interval length */
        if (range_Q16 == 0L) {
            psRC.error = Define.RANGE_CODER_ZERO_INTERVAL_WIDTH
            /* Set output to zero */
            data[data_offset + 0] = 0
            return
        }

        /* Copy structure data back */
        psRC.base_Q32 = base_Q32
        psRC.range_Q16 = range_Q16
        psRC.bufferIx = bufferIx
    }

    /**
     * Range decoder for multiple symbols.
     *
     * @param data uncompressed data [nSymbols].
     * @param psRC compressor data structure.
     * @param prob cumulative density functions.
     * @param probStartIx initial (middle) entries of cdfs [nSymbols].
     * @param nSymbols number of data symbols.
     */
    fun SKP_Silk_range_decoder_multi(data: IntArray,  /* O uncompressed data [nSymbols] */
            psRC: SKP_Silk_range_coder_state?,  /* I/O compressor data structure */
            prob: Array<IntArray>,  /* I cumulative density functions */
            probStartIx: IntArray?,  /* I initial (middle) entries of cdfs [nSymbols] */
            nSymbols: Int /* I number of data symbols */
    ) {
        var k: Int
        k = 0
        while (k < nSymbols) {
            SKP_Silk_range_decoder(data, k, psRC, prob[k], 0, probStartIx!![k])
            k++
        }
    }

    /**
     * Initialize range encoder.
     *
     * @param psRC
     * compressor data structure.
     */
    fun SKP_Silk_range_enc_init(psRC: SKP_Silk_range_coder_state? /*
																		 * O compressor data
																		 * structure
																		 */
    ) {
        /* Initialize structure */
        psRC!!.bufferLength = Define.MAX_ARITHM_BYTES
        psRC.range_Q16 = 0x0000FFFF
        psRC.bufferIx = 0
        psRC.base_Q32 = 0
        psRC.error = 0
    }

    /**
     * Initialize range decoder.
     *
     * @param psRC
     * compressor data structure.
     * @param buffer
     * buffer for compressed data [bufferLength].
     * @param buffer_offset
     * offset of valid data.
     * @param bufferLength
     * buffer length (in bytes).
     */
    fun SKP_Silk_range_dec_init(psRC: SKP_Silk_range_coder_state?,  /*
																		 * O compressor data
																		 * structure
																		 */
            buffer: ByteArray,  /* I buffer for compressed data [bufferLength] */
            buffer_offset: Int, bufferLength: Int /* I buffer length (in bytes) */
    ) {
        /* check input */
        if (bufferLength > Define.MAX_ARITHM_BYTES) {
            psRC!!.error = Define.RANGE_CODER_DEC_PAYLOAD_TOO_LONG
            return
        }
        /* Initialize structure */
        /* Copy to internal buffer */
        System.arraycopy(buffer, buffer_offset, psRC!!.buffer, 0, bufferLength)
        psRC.bufferLength = bufferLength
        psRC.bufferIx = 0
        psRC.base_Q32 = (buffer[buffer_offset + 0].toInt() and 0xFF shl 24
                or (buffer[buffer_offset + 1].toInt() and 0xFF shl 16)
                or (buffer[buffer_offset + 2].toInt() and 0xFF shl 8) or (buffer[buffer_offset + 3].toInt() and 0xFF)).toLong() and 0xFFFFFFFFL
        psRC.range_Q16 = 0x0000FFFF
        psRC.error = 0
    }

    /**
     * Determine length of bitstream.
     *
     * @param psRC
     * compressed data structure.
     * @param nBytes
     * number of BYTES in stream.
     * @return returns number of BITS in stream.
     */
    fun SKP_Silk_range_coder_get_length( /* O returns number of BITS in stream */
            psRC: SKP_Silk_range_coder_state?,  /* I compressed data structure */
            nBytes: IntArray /* O number of BYTES in stream */
    ): Int {
        val nBits: Int

        /* Number of bits in stream */
        nBits = (psRC!!.bufferIx shl 3) + Macros.SKP_Silk_CLZ32((psRC.range_Q16 - 1).toInt()) - 14
        nBytes[0] = nBits + 7 shr 3
        /* Return number of bits in bitstream */
        return nBits
    }

    /**
     * Write shortest uniquely decodable stream to buffer, and determine its length.
     *
     * @param psRC
     * ompressed data structure.
     */
    fun SKP_Silk_range_enc_wrap_up(psRC: SKP_Silk_range_coder_state? /*
																			 * I/O compressed data
																			 * structure
																			 */
    ) {
        var bufferIx_tmp: Int
        val bits_to_store: Int
        val bits_in_stream: Int
        val nBytes: Int
        val mask: Int
        var base_Q24: Long

        /* Lower limit of interval, shifted 8 bits to the right */base_Q24 = psRC!!.base_Q32 ushr 8
        val nBytes_ptr = IntArray(1)
        bits_in_stream = SKP_Silk_range_coder_get_length(psRC, nBytes_ptr)
        nBytes = nBytes_ptr[0]

        /* Number of additional bits (1..9) required to be stored to stream */
        // TODO: bits_to_store = bits_in_stream - psRC.bufferIx << 3 ;
        bits_to_store = bits_in_stream - (psRC.bufferIx shl 3)

        /* Round up to required resolution */
        // TODO:base_Q24 should be 32-bit long.
        // base_Q24 += 0x00800000 >>> ( bits_to_store - 1 );
        base_Q24 += (0x00800000 ushr bits_to_store - 1).toLong()
        base_Q24 = base_Q24 and 0xFFFFFFFFL
        // base_Q24 &= 0xFFFFFFFF << ( 24 - bits_to_store );
        base_Q24 = base_Q24 and (-0x1 shl 24 - bits_to_store).toLong()

        /* Check for carry */
        if (base_Q24 and 0x01000000L != 0L) {
            /* Propagate carry in buffer */
            bufferIx_tmp = psRC.bufferIx
            while ((++psRC.buffer[--bufferIx_tmp]).toInt() == 0);
        }

        /* Store to stream, making sure not to write beyond buffer */
        if (psRC.bufferIx < psRC.bufferLength) {
            psRC.buffer[psRC.bufferIx++] = (base_Q24 ushr 16).toByte()
            if (bits_to_store > 8) {
                if (psRC.bufferIx < psRC.bufferLength) {
                    psRC.buffer[psRC.bufferIx++] = (base_Q24 ushr 8).toByte()
                }
            }
        }

        /* Fill up any remaining bits in the last byte with 1s */
        if (bits_in_stream and 7 != 0) {
            mask = 0xFF shr (bits_in_stream and 7)
            if (nBytes - 1 < psRC.bufferLength) {
                psRC.buffer[nBytes - 1] = (psRC.buffer[nBytes - 1].toInt() or mask).toByte()
            }
        }
    }

    /**
     * Check that any remaining bits in the last byte are set to 1.
     *
     * @param psRC
     * compressed data structure.
     */
    fun SKP_Silk_range_coder_check_after_decoding(psRC: SKP_Silk_range_coder_state?
            /* I/O compressed data structure */
    ) {
        val bits_in_stream: Int
        val nBytes: Int
        val mask: Int
        val nBytes_ptr = IntArray(1)
        bits_in_stream = SKP_Silk_range_coder_get_length(psRC, nBytes_ptr)
        nBytes = nBytes_ptr[0]

        /* Make sure not to read beyond buffer */
        if (nBytes - 1 >= psRC!!.bufferLength) {
            psRC.error = Define.RANGE_CODER_DECODER_CHECK_FAILED
            return
        }

        /* Test any remaining bits in last byte */
        if (bits_in_stream and 7 != 0) {
            mask = 0xFF shr (bits_in_stream and 7)
            if (psRC.buffer[nBytes - 1].toInt() and mask != mask) {
                psRC.error = Define.RANGE_CODER_DECODER_CHECK_FAILED
                return
            }
        }
    }
}