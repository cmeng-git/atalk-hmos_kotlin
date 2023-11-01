/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

import kotlin.math.min

/**
 * Encoder API.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object EncAPI {
    /***
     * TODO: TEST
     */
    var frame_cnt = 0

    /**
     * Read control structure from encoder.
     *
     * @param encState
     * State Vecotr.
     * @param encStatus
     * Control Structure.
     * @return
     */
    fun SKP_Silk_SDK_QueryEncoder(encState: Any,  /* I: State Vector */
            encStatus: SKP_SILK_SDK_EncControlStruct /* O: Control Structure */
    ): Int {
        val psEnc: SKP_Silk_encoder_state_FLP
        val ret = 0
        psEnc = encState as SKP_Silk_encoder_state_FLP
        encStatus.API_sampleRate = psEnc.sCmn.API_fs_Hz
        encStatus.maxInternalSampleRate = Macros.SKP_SMULBB(psEnc.sCmn.maxInternal_fs_kHz, 1000)
        encStatus.packetSize = psEnc.sCmn.API_fs_Hz * psEnc.sCmn.PacketSize_ms / 1000 /*
																						 * convert
																						 * samples
																						 * -> ms
																						 */
        encStatus.bitRate = psEnc.sCmn.TargetRate_bps
        encStatus.packetLossPercentage = psEnc.sCmn.PacketLoss_perc
        encStatus.complexity = psEnc.sCmn.Complexity
        encStatus.useInBandFEC = psEnc.sCmn.useInBandFEC
        encStatus.useDTX = psEnc.sCmn.useDTX
        return ret
    }

    /**
     * Init or Reset encoder.
     *
     * @param encState
     * @param encStatus
     * @return
     */
    fun SKP_Silk_SDK_InitEncoder(encState: Any,  /* I/O: State */
            encStatus: SKP_SILK_SDK_EncControlStruct /* O: Control structure */
    ): Int {
        val psEnc: SKP_Silk_encoder_state_FLP
        var ret = 0
        psEnc = encState as SKP_Silk_encoder_state_FLP

        /* Reset Encoder */
        if (InitEncoderFLP.SKP_Silk_init_encoder_FLP(psEnc).let { ret += it; ret } != 0) {
            assert(false)
        }

        /* Read control structure */
        if (SKP_Silk_SDK_QueryEncoder(encState, encStatus).let { ret += it; ret } != 0) {
            assert(false)
        }
        return ret
    }

    /**
     * Encode frame with Silk.
     *
     * @param encState
     * State
     * @param encControl
     * Control structure
     * @param samplesIn
     * Speech sample input vector
     * @param samplesIn_offset
     * offset of valid data.
     * @param nSamplesIn
     * Number of samples in input vector
     * @param outData
     * Encoded output vector
     * @param outData_offset
     * offset of valid data.
     * @param nBytesOut
     * Number of bytes in outData (input: Max bytes)
     * @return
     */
    fun SKP_Silk_SDK_Encode(encState: Any?,  /* I/O: State */
            encControl: SKP_SILK_SDK_EncControlStruct?,  /* I: Control structure */
            samplesIn: ShortArray,  /* I: Speech sample input vector */
            samplesIn_offset: Int, nSamplesIn: Int,  /* I: Number of samples in input vector */
            outData: ByteArray?,  /* O: Encoded output vector */
            outData_offset: Int, nBytesOut: ShortArray /* I/O: Number of bytes in outData (input: Max bytes) */
    ): Int {
        var samplesIn_offset = samplesIn_offset
        var nSamplesIn = nSamplesIn
        val PacketSize_ms: Int
        var ret = 0
        var nSamplesToBuffer: Int
        val input_ms: Int
        var nSamplesFromInput = 0
        var MaxBytesOut: Short
        val psEnc = encState as SKP_Silk_encoder_state_FLP?
        assert(encControl != null)

        /* Check sampling frequency first, to avoid divide by zero later */
        if ((encControl!!.API_sampleRate != 8000 && encControl.API_sampleRate != 12000
                        && encControl.API_sampleRate != 16000 && encControl.API_sampleRate != 24000
                        && encControl.API_sampleRate != 32000 && encControl.API_sampleRate != 44100
                        && encControl.API_sampleRate != 48000
                        || encControl.maxInternalSampleRate != 8000
                        && encControl.maxInternalSampleRate != 12000
                        && encControl.maxInternalSampleRate != 16000 && encControl.maxInternalSampleRate != 24000)) {
            ret = Errors.SKP_SILK_ENC_FS_NOT_SUPPORTED
            assert(false)
            return ret
        }

        /* Set encoder parameters from control structure */
        val API_fs_Hz = encControl.API_sampleRate
        val max_internal_fs_kHz = encControl.maxInternalSampleRate / 1000 /* convert Hz -> kHz */
        PacketSize_ms = 1000 * encControl.packetSize / API_fs_Hz
        val TargetRate_bps = encControl.bitRate
        val PacketLoss_perc = encControl.packetLossPercentage
        val UseInBandFEC = encControl.useInBandFEC
        val Complexity = encControl.complexity
        val UseDTX = encControl.useDTX
        /* Save values in state */
        psEnc!!.sCmn.API_fs_Hz = API_fs_Hz
        psEnc.sCmn.maxInternal_fs_kHz = max_internal_fs_kHz
        psEnc.sCmn.useInBandFEC = UseInBandFEC

        /* Only accept input lengths that are a multiplum of 10 ms */
        input_ms = 1000 * nSamplesIn / API_fs_Hz
        if (input_ms % 10 != 0 || nSamplesIn < 0) {
            ret = Errors.SKP_SILK_ENC_INPUT_INVALID_NO_OF_SAMPLES
            assert(false)
            return ret
        }

        /* Make sure no more than one packet can be produced */
        if (nSamplesIn > psEnc.sCmn.PacketSize_ms * API_fs_Hz / 1000) {
            ret = Errors.SKP_SILK_ENC_INPUT_INVALID_NO_OF_SAMPLES
            assert(false)
            return ret
        }
        if (ControlCodecFLP.SKP_Silk_control_encoder_FLP(psEnc, API_fs_Hz,
                        max_internal_fs_kHz, PacketSize_ms, TargetRate_bps, PacketLoss_perc, UseInBandFEC,
                        UseDTX, input_ms, Complexity).also { ret = it } != 0) {
            assert(false)
            return ret
        }
        /* Detect energy above 8 kHz */
        if (min(API_fs_Hz, 1000 * max_internal_fs_kHz) == 24000 && psEnc.sCmn.sSWBdetect.SWB_detected == 0 && psEnc.sCmn.sSWBdetect.WB_detected == 0) {
            DetectSWBInput.SKP_Silk_detect_SWB_input(psEnc.sCmn.sSWBdetect, samplesIn,
                    samplesIn_offset, nSamplesIn)
        }

        /* Input buffering/resampling and encoding */
        MaxBytesOut = 0 /* return 0 output bytes if no encoder called */
        while (true) {
            nSamplesToBuffer = psEnc.sCmn.frame_length - psEnc.sCmn.inputBufIx
            if (API_fs_Hz == Macros.SKP_SMULBB(1000, psEnc.sCmn.fs_kHz)) {
                nSamplesToBuffer = Math.min(nSamplesToBuffer, nSamplesIn)
                nSamplesFromInput = nSamplesToBuffer
                /* Copy to buffer */
                System.arraycopy(samplesIn, samplesIn_offset, psEnc.sCmn.inputBuf,
                        psEnc.sCmn.inputBufIx, nSamplesFromInput)
            } else {
                nSamplesToBuffer = Math.min(nSamplesToBuffer, nSamplesIn * psEnc.sCmn.fs_kHz * 1000
                        / API_fs_Hz)
                nSamplesFromInput = nSamplesToBuffer * API_fs_Hz / (psEnc.sCmn.fs_kHz * 1000)
                /* Resample and write to buffer */
                ret += Resampler.SKP_Silk_resampler(psEnc.sCmn.resampler_state,
                        psEnc.sCmn.inputBuf, psEnc.sCmn.inputBufIx, samplesIn, samplesIn_offset,
                        nSamplesFromInput)

                // /*TEST****************************************************************************/
                // /**
                // * test for inputbuf
                // */
                // short[] inputbuf = psEnc.sCmn.inputBuf;
                // String inputbuf_filename = "D:/gsoc/inputbuf-res/inputbuf-res";
                // inputbuf_filename += frame_cnt;
                // DataInputStream inputbuf_datain = null;
                // try
                // {
                // inputbuf_datain = new DataInputStream(
                // new FileInputStream(
                // new File(inputbuf_filename)));
                // byte[] buffer = new byte[2];
                // for(int ii = 0; ii < inputbuf.length; ii++ )
                // {
                // try
                // {
                //
                // int res = inputbuf_datain.read(buffer);
                // if(res != buffer.length)
                // {
                // throw new IOException("Unexpected End of Stream");
                // }
                // inputbuf[ii] = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getShort();
                // }
                // catch (IOException e)
                // {
                // // TODO Auto-generated catch block
                // e.printStackTrace();
                // }
                // }
                // }
                // catch (FileNotFoundException e)
                // {
                // // TODO Auto-generated catch block
                // e.printStackTrace();
                // }
                // finally
                // {
                // if(inputbuf_datain != null)
                // {
                // try
                // {
                // inputbuf_datain.close();
                // }
                // catch (IOException e)
                // {
                // // TODO Auto-generated catch block
                // e.printStackTrace();
                // }
                // }
                // }
                // frame_cnt++;
                // /*TEST
                // END****************************************************************************/
            }
            samplesIn_offset += nSamplesFromInput
            nSamplesIn -= nSamplesFromInput
            psEnc.sCmn.inputBufIx += nSamplesToBuffer

            /* Silk encoder */
            if (psEnc.sCmn.inputBufIx >= psEnc.sCmn.frame_length) {
                /* Enough data in input buffer, so encode */
                if (MaxBytesOut.toInt() == 0) {
                    /* No payload obtained so far */
                    MaxBytesOut = nBytesOut[0]
                    val MaxBytesOut_ptr = ShortArray(1)
                    MaxBytesOut_ptr[0] = MaxBytesOut
                    // if( ( ret = Silk_encode_frame_FLP.SKP_Silk_encode_frame_FLP( psEnc, outData,
                    // outData_offset,
                    // MaxBytesOut_ptr, psEnc.sCmn.inputBuf, psEnc.sCmn.inputBufIx ) ) != 0 )
                    if (EncodeFrameFLP.SKP_Silk_encode_frame_FLP(psEnc, outData,
                                    outData_offset, MaxBytesOut_ptr, psEnc.sCmn.inputBuf, 0).also { ret = it } != 0) {
                        assert(false)
                    }
                    MaxBytesOut = MaxBytesOut_ptr[0]
                } else {
                    /* outData already contains a payload */
                    // if( ( ret = Silk_encode_frame_FLP.SKP_Silk_encode_frame_FLP( psEnc, outData,
                    // outData_offset,
                    // nBytesOut, psEnc.sCmn.inputBuf, psEnc.sCmn.inputBufIx) ) != 0 )
                    if (EncodeFrameFLP.SKP_Silk_encode_frame_FLP(psEnc, outData,
                                    outData_offset, nBytesOut, psEnc.sCmn.inputBuf, 0).also { ret = it } != 0) {
                        assert(false)
                    }
                    assert(nBytesOut[0].toInt() == 0)
                }
                psEnc.sCmn.inputBufIx = 0
            } else {
                break
            }
        }
        nBytesOut[0] = MaxBytesOut
        if (psEnc.sCmn.useDTX != 0 && psEnc.sCmn.inDTX != 0) {
            /* DTX simulation */
            nBytesOut[0] = 0
        }
        return ret
    }
}