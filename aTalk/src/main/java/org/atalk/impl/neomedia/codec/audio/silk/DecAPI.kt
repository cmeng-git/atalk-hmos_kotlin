/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import java.util.*

/**
 * The Decoder API.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object DecAPI {
    /**
     * Reset the decoder state.
     *
     * @param decState
     * the decoder state
     * @return ret
     */
    fun SKP_Silk_SDK_InitDecoder(decState: Any /* I/O: State */
    ): Int {
        var ret = 0
        val struc: SKP_Silk_decoder_state
        struc = decState as SKP_Silk_decoder_state
        ret = CreateInitDestroy.SKP_Silk_init_decoder(struc)
        return ret
    }

    /**
     * Decode a frame.
     *
     * @param decState
     * the decoder state.
     * @param decControl
     * the decoder control.
     * @param lostFlag
     * the lost flag. 0: no loss; 1: loss.
     * @param inData
     * encoding input vector.
     * @param inData_offset
     * the actual data offset in the input vector.
     * @param nBytesIn
     * number of input bytes.
     * @param samplesOut
     * decoded output speech vector.
     * @param samplesOut_offset
     * the actual data offset in the output vector.
     * @param nSamplesOut
     * number of samples.
     * @return the returned value carries the error message. 0 indicates OK; other indicates error.
     */
    fun SKP_Silk_SDK_Decode(decState: Any?,  /* I/O: State */
            decControl: SKP_SILK_SDK_DecControlStruct?,  /* I/O: Control structure */
            lostFlag: Int,  /* I: 0: no loss, 1 loss */
            inData: ByteArray,  /* I: Encoded input vector */
            inData_offset: Int, nBytesIn: Int,  /* I: Number of input Bytes */
            samplesOut: ShortArray,  /* O: Decoded output speech vector */
            samplesOut_offset: Int, nSamplesOut: ShortArray /* I/O: Number of samples (vector/decoded) */
    ): Int {
        var lostFlag = lostFlag
        var ret = 0
        val used_bytes: Int
        val prev_fs_kHz: Int
        val psDec: SKP_Silk_decoder_state?
        psDec = decState as SKP_Silk_decoder_state?
        /** */
        /* Test if first frame in payload */
        /** */
        if (psDec!!.moreInternalDecoderFrames == 0) {
            /* First Frame in Payload */
            psDec.nFramesDecoded = 0 /* Used to count frames in packet */
        }
        if (psDec.moreInternalDecoderFrames == 0 && /* First frame in packet */
                lostFlag == 0 && /* Not packet loss */
                nBytesIn > Define.MAX_ARITHM_BYTES) { /* Too long payload */
            /* Avoid trying to decode a too large packet */
            lostFlag = 1
            ret = Errors.SKP_SILK_DEC_PAYLOAD_TOO_LARGE
        }

        /* Save previous sample frequency */
        prev_fs_kHz = psDec.fs_kHz

        /* Call decoder for one frame */
        val used_bytes_ptr = IntArray(1)
        ret += DecodeFrame.SKP_Silk_decode_frame(psDec, samplesOut, samplesOut_offset, nSamplesOut,
                inData, inData_offset, nBytesIn, lostFlag, used_bytes_ptr)
        used_bytes = used_bytes_ptr[0]
        if (used_bytes != 0) /* Only Call if not a packet loss */
        {
            if (psDec.nBytesLeft > 0 && psDec.FrameTermination == Define.SKP_SILK_MORE_FRAMES && psDec.nFramesDecoded < 5) {
                /* We have more frames in the Payload */
                psDec.moreInternalDecoderFrames = 1
            } else {
                /* Last frame in Payload */
                psDec.moreInternalDecoderFrames = 0
                psDec.nFramesInPacket = psDec.nFramesDecoded

                /* Track inband FEC usage */
                if (psDec.vadFlag == Define.VOICE_ACTIVITY) {
                    if (psDec.FrameTermination == Define.SKP_SILK_LAST_FRAME) {
                        psDec.no_FEC_counter++
                        if (psDec.no_FEC_counter > Define.NO_LBRR_THRES) {
                            psDec.inband_FEC_offset = 0
                        }
                    } else if (psDec.FrameTermination == Define.SKP_SILK_LBRR_VER1) {
                        psDec.inband_FEC_offset = 1 /* FEC info with 1 packet delay */
                        psDec.no_FEC_counter = 0
                    } else if (psDec.FrameTermination == Define.SKP_SILK_LBRR_VER2) {
                        psDec.inband_FEC_offset = 2 /* FEC info with 2 packets delay */
                        psDec.no_FEC_counter = 0
                    }
                }
            }
        }
        if (Define.MAX_API_FS_KHZ * 1000 < decControl!!.API_sampleRate || 8000 > decControl.API_sampleRate) {
            ret = Errors.SKP_SILK_DEC_INVALID_SAMPLING_FREQUENCY
            return ret
        }

        /* Resample if needed */
        if (psDec.fs_kHz * 1000 != decControl.API_sampleRate) {
            val samplesOut_tmp = ShortArray(Define.MAX_API_FS_KHZ * Define.FRAME_LENGTH_MS)
            Typedef.SKP_assert(psDec.fs_kHz <= Define.MAX_API_FS_KHZ)

            /* Copy to a tmp buffer as the resampling writes to samplesOut */
            System.arraycopy(samplesOut, samplesOut_offset + 0, samplesOut_tmp, 0, nSamplesOut[0].toInt())
            /* (Re-)initialize resampler state when switching internal sampling frequency */
            if (prev_fs_kHz != psDec.fs_kHz
                    || psDec.prev_API_sampleRate != decControl.API_sampleRate) {
                ret = Resampler.SKP_Silk_resampler_init(psDec.resampler_state, psDec.fs_kHz * 1000,
                        decControl.API_sampleRate)
            }

            /* Resample the output to API_sampleRate */
            ret += Resampler.SKP_Silk_resampler(psDec.resampler_state, samplesOut,
                    samplesOut_offset, samplesOut_tmp, 0, nSamplesOut[0].toInt())

            /* Update the number of output samples */
            nSamplesOut[0] = (nSamplesOut[0] * decControl.API_sampleRate / (psDec.fs_kHz * 1000)).toShort()
        }
        psDec.prev_API_sampleRate = decControl.API_sampleRate

        /* Copy all parameters that are needed out of internal structure to the control stucture */
        decControl.frameSize = psDec.frame_length
        decControl.framesPerPacket = psDec.nFramesInPacket
        decControl.inBandFECOffset = psDec.inband_FEC_offset
        decControl.moreInternalDecoderFrames = psDec.moreInternalDecoderFrames
        return ret
    }

    /**
     * Find LBRR information in a packet.
     *
     * @param inData
     * encoded input vector.
     * @param inData_offset
     * offset of the valid data.
     * @param nBytesIn
     * number of input bytes.
     * @param lost_offset
     * offset from lost packet.
     * @param LBRRData
     * LBRR payload.
     * @param LBRRData_offset
     * offset of the valid data.
     * @param nLBRRBytes
     * number of LBRR bytes.
     */
    fun SKP_Silk_SDK_search_for_LBRR(inData: ByteArray,  /* I: Encoded input vector */
            inData_offset: Int, nBytesIn: Short,  /* I: Number of input Bytes */
            lost_offset: Int,  /* I: Offset from lost packet */
            LBRRData: ByteArray?,  /* O: LBRR payload */
            LBRRData_offset: Int, nLBRRBytes: ShortArray /* O: Number of LBRR Bytes */
    ) {
        var LBRRData = LBRRData
        val sDec = SKP_Silk_decoder_state() // Local decoder state to avoid
        // interfering with
        // running decoder */
        val sDecCtrl = SKP_Silk_decoder_control()
        val TempQ = IntArray(Define.MAX_FRAME_LENGTH)
        if (lost_offset < 1 || lost_offset > Define.MAX_LBRR_DELAY) {
            /* No useful FEC in this packet */
            nLBRRBytes[0] = 0
            return
        }
        sDec.nFramesDecoded = 0
        sDec.fs_kHz = 0 /* Force update parameters LPC_order etc */
        Arrays.fill(sDec.prevNLSF_Q15, 0, Define.MAX_LPC_ORDER, 0)
        for (i in 0 until Define.MAX_LPC_ORDER) sDec.prevNLSF_Q15[i] = 0
        RangeCoder.SKP_Silk_range_dec_init(sDec.sRC, inData, inData_offset, nBytesIn.toInt())
        while (true) {
            DecodeParameters.SKP_Silk_decode_parameters(sDec, sDecCtrl, TempQ, 0)
            if (sDec.sRC.error != 0) {
                /* Corrupt stream */
                nLBRRBytes[0] = 0
                return
            }
            // TODO:note the semicolon;
            // };
            if (sDec.FrameTermination - 1 and lost_offset != 0 && sDec.FrameTermination > 0 && sDec.nBytesLeft >= 0) {
                /* The wanted FEC is present in the packet */
                nLBRRBytes[0] = sDec.nBytesLeft.toShort()
                System.arraycopy(inData, inData_offset + nBytesIn - sDec.nBytesLeft, LBRRData,
                        LBRRData_offset + 0, sDec.nBytesLeft)
                break
            }
            if (sDec.nBytesLeft > 0 && sDec.FrameTermination == Define.SKP_SILK_MORE_FRAMES) {
                sDec.nFramesDecoded++
            } else {
                LBRRData = null
                nLBRRBytes[0] = 0
                break
            }
        }
    }

    /**
     * Getting type of content for a packet.
     *
     * @param inData
     * encoded input vector.
     * @param nBytesIn
     * number of input bytes.
     * @param Silk_TOC
     * type of content.
     */
    fun SKP_Silk_SDK_get_TOC(inData: ByteArray,  /* I: Encoded input vector */
            nBytesIn: Short,  /* I: Number of input bytes */
            Silk_TOC: SKP_Silk_TOC_struct /* O: Type of content */
    ) {
        val sDec = SKP_Silk_decoder_state()
        val sDecCtrl = SKP_Silk_decoder_control()
        val TempQ = IntArray(Define.MAX_FRAME_LENGTH)
        sDec.nFramesDecoded = 0
        sDec.fs_kHz = 0 /* Force update parameters LPC_order etc */
        RangeCoder.SKP_Silk_range_dec_init(sDec.sRC, inData, 0, nBytesIn.toInt())
        Silk_TOC.corrupt = 0
        while (true) {
            DecodeParameters.SKP_Silk_decode_parameters(sDec, sDecCtrl, TempQ, 0)
            Silk_TOC.vadFlags[sDec.nFramesDecoded] = sDec.vadFlag
            Silk_TOC.sigtypeFlags[sDec.nFramesDecoded] = sDecCtrl.sigtype
            if (sDec.sRC.error != 0) {
                /* Corrupt stream */
                Silk_TOC.corrupt = 1
                break
            }
            // TODO:note the semicolon;
            // };
            if (sDec.nBytesLeft > 0 && sDec.FrameTermination == Define.SKP_SILK_MORE_FRAMES) {
                sDec.nFramesDecoded++
            } else {
                break
            }
        }
        if (Silk_TOC.corrupt != 0 || sDec.FrameTermination == Define.SKP_SILK_MORE_FRAMES || sDec.nFramesInPacket > SDKAPI.SILK_MAX_FRAMES_PER_PACKET) {
            /* Corrupt packet */
            run {
                Silk_TOC.corrupt = 0
                Silk_TOC.framesInPacket = 0
                Silk_TOC.fs_kHz = 0
                Silk_TOC.inbandLBRR = 0
                for (i in Silk_TOC.vadFlags.indices) Silk_TOC.vadFlags[i] = 0
                for (i in Silk_TOC.sigtypeFlags.indices) Silk_TOC.sigtypeFlags[i] = 0
            }
            Silk_TOC.corrupt = 1
        } else {
            Silk_TOC.framesInPacket = sDec.nFramesDecoded + 1
            Silk_TOC.fs_kHz = sDec.fs_kHz
            if (sDec.FrameTermination == Define.SKP_SILK_LAST_FRAME) {
                Silk_TOC.inbandLBRR = sDec.FrameTermination
            } else {
                Silk_TOC.inbandLBRR = sDec.FrameTermination - 1
            }
        }
    }

    /**
     * Get the version number.
     *
     * @return the string specifying the version number.
     */
    fun SKP_Silk_SDK_get_version(): String {
        return "1.0.6"
    }
}