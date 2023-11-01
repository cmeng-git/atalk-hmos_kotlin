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
object HPVariableCutoffFLP {
    /**
     * TODO: TEST
     */
    var frame_cnt = 0

    /**
     * High-pass filter with cutoff frequency adaptation based on pitch lag statistics.
     *
     * @param psEnc
     * Encoder state FLP
     * @param psEncCtrl
     * Encoder control FLP
     * @param out
     * High-pass filtered output signal
     * @param out_offset
     * offset of valid data.
     * @param in
     * Input signal
     * @param in_offset
     * offset of valid data.
     */
    fun SKP_Silk_HP_variable_cutoff_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /*
																				 * I/O Encoder state
																				 * FLP
																				 */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            out: ShortArray,  /* O High-pass filtered output signal */
            out_offset: Int, `in`: ShortArray?,  /* I Input signal */
            in_offset: Int) {
        val pitch_freq_Hz: Float
        var pitch_freq_log: Float
        val quality: Float
        var delta_freq: Float
        val smth_coef: Float
        val Fc: Float
        val r: Float
        val B_Q28 = IntArray(3)
        val A_Q28 = IntArray(2)
        /** */
        /* Estimate low end of pitch frequency range */
        /** */
        if (psEnc!!.sCmn.prev_sigtype == Define.SIG_TYPE_VOICED) {

            /* Difference, in log domain */
            pitch_freq_Hz = 1e3f * psEnc.sCmn.fs_kHz / psEnc.sCmn.prevLag
            pitch_freq_log = MainFLP.SKP_Silk_log2(pitch_freq_Hz.toDouble())

            /* Adjustment based on quality */
            quality = psEncCtrl.input_quality_bands[0]
            pitch_freq_log -= (quality * quality
                    * (pitch_freq_log - MainFLP.SKP_Silk_log2(DefineFLP.VARIABLE_HP_MIN_FREQ.toDouble())))
            pitch_freq_log += 0.5f * (0.6f - quality)
            delta_freq = pitch_freq_log - psEnc.variable_HP_smth1
            if (delta_freq < 0.0) {
                /*
				 * Less smoothing for decreasing pitch frequency, to track something close to the
				 * minimum
				 */
                delta_freq *= 3.0f
            }

            /* Limit delta, to reduce impact of outliers */
            delta_freq = SigProcFLP.SKP_LIMIT_float(delta_freq,
                    -DefineFLP.VARIABLE_HP_MAX_DELTA_FREQ, DefineFLP.VARIABLE_HP_MAX_DELTA_FREQ)

            /* Update smoother */
            smth_coef = DefineFLP.VARIABLE_HP_SMTH_COEF1 * psEnc.speech_activity
            psEnc.variable_HP_smth1 += smth_coef * delta_freq
        }

        /* Second smoother */
        psEnc.variable_HP_smth2 += (DefineFLP.VARIABLE_HP_SMTH_COEF2
                * (psEnc.variable_HP_smth1 - psEnc.variable_HP_smth2))

        /* Convert from log scale to Hertz */
        psEncCtrl.pitch_freq_low_Hz = Math.pow(2.0, psEnc.variable_HP_smth2.toDouble()).toFloat()

        /* Limit frequency range */
        psEncCtrl.pitch_freq_low_Hz = SigProcFLP.SKP_LIMIT_float(psEncCtrl.pitch_freq_low_Hz,
                DefineFLP.VARIABLE_HP_MIN_FREQ, DefineFLP.VARIABLE_HP_MAX_FREQ)
        /** */
        /* Compute filter coefficients */
        /** */
        /* Compute cut-off frequency, in radians */
        Fc = (0.45f * 2.0f * 3.14159265359 * psEncCtrl.pitch_freq_low_Hz / (1e3f * psEnc.sCmn.fs_kHz)).toFloat()

        /* 2nd order ARMA coefficients */
        r = 1.0f - 0.92f * Fc

        /* b = r * [1; -2; 1]; */
        /* a = [1; -2 * r * (1 - 0.5 * Fc^2); r^2]; */
        B_Q28[0] = SigProcFLP.SKP_float2int(((1 shl 28) * r).toDouble())
        B_Q28[1] = SigProcFLP.SKP_float2int(((1 shl 28) * -2.0f * r).toDouble())
        B_Q28[2] = B_Q28[0]
        A_Q28[0] = SigProcFLP.SKP_float2int(((1 shl 28) * -2.0f * r * (1.0f - 0.5f * Fc * Fc)).toDouble())
        A_Q28[1] = SigProcFLP.SKP_float2int(((1 shl 28) * r * r).toDouble())
        /** */
        /* High-pass filter */
        /** */

        // /*TEST****************************************************************************/
        // /**
        // * test for B_Q28
        // */
        // int[] b_q28 = B_Q28;
        // String b_q28_filename = "D:/gsoc/b_q28/b_q28";
        // b_q28_filename += frame_cnt;
        // DataInputStream b_q28_datain = null;
        // try
        // {
        // b_q28_datain = new DataInputStream(
        // new FileInputStream(
        // new File(b_q28_filename)));
        // byte[] buffer = new byte[4];
        // for(int ii = 0; ii < b_q28.length; ii++ )
        // {
        // try
        // {
        //
        // int res = b_q28_datain.read(buffer);
        // if(res != buffer.length)
        // {
        // throw new IOException("Unexpected End of Stream");
        // }
        // b_q28[ii] = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
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
        // if(b_q28_datain != null)
        // {
        // try
        // {
        // b_q28_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // }
        // // frame_cnt++;
        // /*TEST END****************************************************************************/
        //
        // /*TEST****************************************************************************/
        // /**
        // * test for psEnc.x_buf
        // */
        // int[] a_q28 = A_Q28;
        // String a_q28_filename = "D:/gsoc/a_q28/a_q28";
        // a_q28_filename += frame_cnt;
        // DataInputStream a_q28_datain = null;
        // try
        // {
        // a_q28_datain = new DataInputStream(
        // new FileInputStream(
        // new File(a_q28_filename)));
        // byte[] buffer = new byte[4];
        // for(int ii = 0; ii < a_q28.length; ii++ )
        // {
        // try
        // {
        //
        // int res = a_q28_datain.read(buffer);
        // if(res != buffer.length)
        // {
        // throw new IOException("Unexpected End of Stream");
        // }
        // a_q28[ii] = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
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
        // if(a_q28_datain != null)
        // {
        // try
        // {
        // a_q28_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // }
        // // frame_cnt++;
        // /*TEST END****************************************************************************/
        //
        // /*TEST****************************************************************************/
        // /**
        // * test for psEnc.sCmn.In_HP_State
        // */
        // int[] in_hp_state = psEnc.sCmn.In_HP_State;
        // String in_hp_state_filename = "D:/gsoc/in_hp_state/in_hp_state";
        // in_hp_state_filename += frame_cnt;
        // DataInputStream in_hp_state_datain = null;
        // try
        // {
        // in_hp_state_datain = new DataInputStream(
        // new FileInputStream(
        // new File(in_hp_state_filename)));
        // byte[] buffer = new byte[4];
        // for(int ii = 0; ii < in_hp_state.length; ii++ )
        // {
        // try
        // {
        //
        // int res = in_hp_state_datain.read(buffer);
        // if(res != buffer.length)
        // {
        // throw new IOException("Unexpected End of Stream");
        // }
        // in_hp_state[ii] = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
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
        // if(in_hp_state_datain != null)
        // {
        // try
        // {
        // in_hp_state_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // }
        // // frame_cnt++;
        // /*TEST END****************************************************************************/

        // /*TEST****************************************************************************/
        // /**
        // * test for in
        // */
        // short[] in_test = in;
        // String in_test_filename = "D:/gsoc/in_test/in_test";
        // in_test_filename += frame_cnt;
        // DataInputStream in_test_datain = null;
        // try
        // {
        // in_test_datain = new DataInputStream(
        // new FileInputStream(
        // new File(in_test_filename)));
        // byte[] buffer = new byte[2];
        // for(int ii = 0; ii < in_test.length; ii++ )
        // {
        // try
        // {
        //
        // int res = in_test_datain.read(buffer);
        // if(res != buffer.length)
        // {
        // throw new IOException("Unexpected End of Stream");
        // }
        // in_test[ii] = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getShort();
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
        // if(in_test_datain != null)
        // {
        // try
        // {
        // in_test_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // }
        // frame_cnt++;
        // /*TEST END****************************************************************************/
        BiquadAlt.SKP_Silk_biquad_alt(`in`, in_offset, B_Q28, A_Q28, psEnc.sCmn.In_HP_State, out,
                out_offset, psEnc.sCmn.frame_length)
    }
}