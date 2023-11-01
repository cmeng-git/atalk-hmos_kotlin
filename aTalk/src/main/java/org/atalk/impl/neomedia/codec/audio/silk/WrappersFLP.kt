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
object WrappersFLP {
    /* Wrappers. Calls flp / fix code */
    var ar2_q13_file_offset: Long = 0
    var x_16_file_offset: Long = 0
    var frame_cnt = 0

    /* Convert AR filter coefficients to NLSF parameters */
    fun SKP_Silk_A2NLSF_FLP(pNLSF: FloatArray,  /* O NLSF vector [ LPC_order ] */
            pAR: FloatArray,  /* I LPC coefficients [ LPC_order ] */
            LPC_order: Int /* I LPC order */
    ) {
        var i: Int
        val NLSF_fix = IntArray(Define.MAX_LPC_ORDER)
        val a_fix_Q16 = IntArray(Define.MAX_LPC_ORDER)
        i = 0
        while (i < LPC_order) {
            a_fix_Q16[i] = SigProcFLP.SKP_float2int((pAR[i] * 65536.0f).toDouble())
            i++
        }
        A2NLSF.SKP_Silk_A2NLSF(NLSF_fix, a_fix_Q16, LPC_order)
        i = 0
        while (i < LPC_order) {
            pNLSF[i] = NLSF_fix[i] * (1.0f / 32768.0f)
            i++
        }
    }

    /* Convert LSF parameters to AR prediction filter coefficients */
    fun SKP_Silk_NLSF2A_stable_FLP(pAR: FloatArray?,  /* O LPC coefficients [ LPC_order ] */
            pNLSF: FloatArray,  /* I NLSF vector [ LPC_order ] */
            LPC_order: Int /* I LPC order */
    ) {
        var i: Int
        val NLSF_fix = IntArray(Define.MAX_LPC_ORDER)
        val a_fix_Q12 = ShortArray(Define.MAX_LPC_ORDER)
        i = 0
        while (i < LPC_order) {
            NLSF_fix[i] = SigProcFLP.SKP_float2int((pNLSF[i] * 32768.0f).toDouble())
            i++
        }
        NLSF2AStable.SKP_Silk_NLSF2A_stable(a_fix_Q12, NLSF_fix, LPC_order)
        i = 0
        while (i < LPC_order) {
            pAR!![i] = a_fix_Q12[i] / 4096.0f
            i++
        }
    }

    /* LSF stabilizer, for a single input data vector */
    fun SKP_Silk_NLSF_stabilize_FLP(pNLSF: FloatArray,  /*
															 * I/O (Un)stable NLSF vector [
															 * LPC_order ]
															 */
            pNDelta_min: FloatArray?,  /* I Normalized delta min vector[LPC_order+1] */
            LPC_order: Int /* I LPC order */
    ) {
        var i: Int
        val NLSF_Q15 = IntArray(Define.MAX_LPC_ORDER)
        val ndelta_min_Q15 = IntArray(Define.MAX_LPC_ORDER + 1)
        i = 0
        while (i < LPC_order) {
            NLSF_Q15[i] = SigProcFLP.SKP_float2int((pNLSF[i] * 32768.0f).toDouble())
            ndelta_min_Q15[i] = SigProcFLP.SKP_float2int((pNDelta_min!![i] * 32768.0f).toDouble())
            i++
        }
        ndelta_min_Q15[LPC_order] = SigProcFLP.SKP_float2int((pNDelta_min!![LPC_order] * 32768.0f).toDouble())

        /* NLSF stabilizer, for a single input data vector */
        NLSFStabilize.SKP_Silk_NLSF_stabilize(NLSF_Q15, 0, ndelta_min_Q15, LPC_order)
        i = 0
        while (i < LPC_order) {
            pNLSF[i] = NLSF_Q15[i] * (1.0f / 32768.0f)
            i++
        }
    }

    /* Interpolation function with fixed point rounding */
    fun SKP_Silk_interpolate_wrapper_FLP(xi: FloatArray,  /* O Interpolated vector */
            x0: FloatArray?,  /* I First vector */
            x1: FloatArray,  /* I Second vector */
            ifact: Float,  /* I Interp. factor, weight on second vector */
            d: Int /* I Number of parameters */
    ) {
        val x0_int = IntArray(Define.MAX_LPC_ORDER)
        val x1_int = IntArray(Define.MAX_LPC_ORDER)
        val xi_int = IntArray(Define.MAX_LPC_ORDER)
        val ifact_Q2 = (ifact * 4.0f).toInt()
        var i: Int

        /* Convert input from flp to fix */
        i = 0
        while (i < d) {
            x0_int[i] = SigProcFLP.SKP_float2int((x0!![i] * 32768.0f).toDouble())
            x1_int[i] = SigProcFLP.SKP_float2int((x1[i] * 32768.0f).toDouble())
            i++
        }

        /* Interpolate two vectors */
        Interpolate.SKP_Silk_interpolate(xi_int, x0_int, x1_int, ifact_Q2, d)

        /* Convert output from fix to flp */
        i = 0
        while (i < d) {
            xi[i] = xi_int[i] * (1.0f / 32768.0f)
            i++
        }
    }

    /* Floating-point Silk VAD wrapper */
    fun SKP_Silk_VAD_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /* I/O Encoder state FLP */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            pIn: ShortArray?,  /* I Input signal */
            pIn_offset: Int): Int {
        var i: Int
        val ret: Int
        val SA_Q8 = IntArray(1)
        val SNR_dB_Q7 = IntArray(1)
        val Tilt_Q15 = IntArray(1)
        val Quality_Bands_Q15 = IntArray(Define.VAD_N_BANDS)
        ret = VAD.SKP_Silk_VAD_GetSA_Q8(psEnc!!.sCmn.sVAD, SA_Q8, SNR_dB_Q7, Quality_Bands_Q15,
                Tilt_Q15, pIn, pIn_offset, psEnc.sCmn.frame_length)
        psEnc.speech_activity = SA_Q8[0] / 256.0f
        i = 0
        while (i < Define.VAD_N_BANDS) {
            psEncCtrl.input_quality_bands[i] = Quality_Bands_Q15[i] / 32768.0f
            i++
        }
        psEncCtrl.input_tilt = Tilt_Q15[0] / 32768.0f
        return ret
    }

    /* Floating-point Silk NSQ wrapper */
    fun SKP_Silk_NSQ_wrapper_FLP(psEnc: SKP_Silk_encoder_state_FLP?,  /* I/O Encoder state FLP */
            psEncCtrl: SKP_Silk_encoder_control_FLP,  /* I/O Encoder control FLP */
            x: FloatArray?,  /* I Prefiltered input signal */
            x_offset: Int, q: ByteArray?,  /* O Quantized pulse signal */
            q_offset: Int, useLBRR: Int /* I LBRR flag */
    ) {
        var i: Int
        var j: Int
        var tmp_float: Float
        val x_16 = ShortArray(Define.MAX_FRAME_LENGTH)
        /* Prediction and coding parameters */
        val Gains_Q16 = IntArray(Define.NB_SUBFR)
        val PredCoef_Q12 = Array(2) { ShortArray(Define.MAX_LPC_ORDER) }
        val LTPCoef_Q14 = ShortArray(Define.LTP_ORDER * Define.NB_SUBFR)
        val LTP_scale_Q14: Int

        /* Noise shaping parameters */
        /* Testing */
        val AR2_Q13 = ShortArray(Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX)
        val LF_shp_Q14 = IntArray(Define.NB_SUBFR) /* Packs two int16 coefficients per int32 value */
        val Tilt_Q14 = IntArray(Define.NB_SUBFR)
        val HarmShapeGain_Q14 = IntArray(Define.NB_SUBFR)

        /* Convert control struct to fix control struct */
        /* Noise shape parameters */
        i = 0
        while (i < Define.NB_SUBFR * Define.SHAPE_LPC_ORDER_MAX) {
            AR2_Q13[i] = SigProcFIX.SKP_SAT16(SigProcFLP.SKP_float2int((psEncCtrl.AR2[i] * 8192.0f).toDouble())).toShort()
            i++
        }

        /* TEST*********************************************************************** */
        /*
		 * test of the AR2_Q13
		 */
        // short[] ar2_q13 = new short[ NB_SUBFR * SHAPE_LPC_ORDER_MAX ];
        // String ar2_q13_filename = "D:/gsoc/ar2_q13";
        //
        // /*
        // * Option 1:
        // */
        // DataInputStream ar2_q13_datain = null;
        // try
        // {
        // ar2_q13_datain = new DataInputStream(
        // new FileInputStream(
        // new File(ar2_q13_filename)));
        //
        // for( i = 0; i < NB_SUBFR * SHAPE_LPC_ORDER_MAX; i++ )
        // {
        // // AR2_Q13[ i ] = (short)SigProcFIX.SKP_SAT16( SigProcFLP.SKP_float2int( psEncCtrl.AR2[ i
        // ] * 8192.0f ) );
        // try
        // {
        // ar2_q13[i] = ar2_q13_datain.readShort();
        // AR2_Q13[i] = (short) (((ar2_q13[i] << 8) & 0xFF00) | ((ar2_q13[i] >>> 8) & 0x00FF));
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // try
        // {
        // ar2_q13_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // catch (FileNotFoundException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        /*
		 * Option 2;
		 */
        // RandomAccessFile ar2_q13_datain_rand = null;
        // try
        // {
        // ar2_q13_datain_rand = new RandomAccessFile(new File(ar2_q13_filename), "r");
        // try
        // {
        // ar2_q13_datain_rand.seek(ar2_q13_file_offset);
        // for( i = 0; i < NB_SUBFR * SHAPE_LPC_ORDER_MAX; i++ )
        // {
        // // AR2_Q13[ i ] = (short)SigProcFIX.SKP_SAT16( SigProcFLP.SKP_float2int( psEncCtrl.AR2[ i
        // ] * 8192.0f ) );
        // try
        // {
        // ar2_q13[i] = ar2_q13_datain_rand.readShort();
        // AR2_Q13[i] = (short) (((ar2_q13[i] << 8) & 0xFF00) | ((ar2_q13[i] >>> 8) & 0x00FF));
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // ar2_q13_file_offset += i;
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        //
        // try
        // {
        // ar2_q13_datain_rand.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // catch (FileNotFoundException e1)
        // {
        // // TODO Auto-generated catch block
        // e1.printStackTrace();
        // }
        /**
         * Option 3:
         */
        // ar2_q13_filename += frame_cnt;
        // DataInputStream ar2_q13_datain = null;
        // try
        // {
        // ar2_q13_datain = new DataInputStream(
        // new FileInputStream(
        // new File(ar2_q13_filename)));
        //
        // for( i = 0; i < NB_SUBFR * SHAPE_LPC_ORDER_MAX; i++ )
        // {
        // // AR2_Q13[ i ] = (short)SigProcFIX.SKP_SAT16( SigProcFLP.SKP_float2int( psEncCtrl.AR2[ i
        // ] * 8192.0f ) );
        // try
        // {
        // ar2_q13[i] = ar2_q13_datain.readShort();
        // AR2_Q13[i] = (short) (((ar2_q13[i] << 8) & 0xFF00) | ((ar2_q13[i] >>> 8) & 0x00FF));
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // try
        // {
        // ar2_q13_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // catch (FileNotFoundException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        /* TEST End********************************************************************** */
        i = 0
        while (i < Define.NB_SUBFR) {
            LF_shp_Q14[i] = (SigProcFLP.SKP_float2int((psEncCtrl.LF_AR_shp[i] * 16384.0f).toDouble()) shl 16
                    or (0x0000FFFF and SigProcFLP.SKP_float2int((psEncCtrl.LF_MA_shp[i] * 16384.0f).toDouble())))
            Tilt_Q14[i] = SigProcFLP.SKP_float2int((psEncCtrl.Tilt[i] * 16384.0f).toDouble())
            HarmShapeGain_Q14[i] = SigProcFLP.SKP_float2int((psEncCtrl.HarmShapeGain[i] * 16384.0f).toDouble())
            i++
        }
        val Lambda_Q10: Int = SigProcFLP.SKP_float2int((psEncCtrl.Lambda * 1024.0f).toDouble())

        /* prediction and coding parameters */
        i = 0
        while (i < Define.NB_SUBFR * Define.LTP_ORDER) {
            LTPCoef_Q14[i] = SigProcFLP.SKP_float2int((psEncCtrl.LTPCoef[i] * 16384.0f).toDouble()).toShort()
            i++
        }
        j = 0
        while (j < Define.NB_SUBFR shr 1) {
            i = 0
            while (i < Define.MAX_LPC_ORDER) {
                PredCoef_Q12[j][i] = SigProcFLP.SKP_float2int((psEncCtrl.PredCoef[j]!![i] * 4096.0f).toDouble()).toShort()
                i++
            }
            j++
        }
        i = 0
        while (i < Define.NB_SUBFR) {
            tmp_float = SigProcFIX.SKP_LIMIT(psEncCtrl.Gains[i] * 65536.0f, 2147483000.0f,
                    -2147483000.0f)
            Gains_Q16[i] = SigProcFLP.SKP_float2int(tmp_float.toDouble())
            if (psEncCtrl.Gains[i] > 0.0f) {
                assert(tmp_float >= 0.0f)
                assert(Gains_Q16[i] >= 0)
            }
            i++
        }
        LTP_scale_Q14 = if (psEncCtrl.sCmn.sigtype == Define.SIG_TYPE_VOICED) {
            TablesOther.SKP_Silk_LTPScales_table_Q14[psEncCtrl.sCmn.LTP_scaleIndex].toInt()
        } else {
            0
        }

        /* Convert input to fix */
        SigProcFLP.SKP_float2short_array(x_16, 0, x, x_offset, psEnc!!.sCmn.frame_length)

        /* TEST*********************************************************************** */
        /**
         * test of x_16
         */
        // short x_16_test[] = new short[ MAX_FRAME_LENGTH ];
        // String x_16_filename = "D:/gsoc/x_16";
        // /*
        // * Option 1:
        // */
        // DataInputStream x_16_datain = null;
        // try
        // {
        // x_16_datain = new DataInputStream(
        // new FileInputStream(
        // new File(x_16_filename)));
        // for(int k = 0; k < psEnc.sCmn.frame_length; k++)
        // {
        // try
        // {
        // x_16_test[k] = x_16_datain.readShort();
        // x_16[k] = (short) (((x_16_test[k]<<8)&0xFF00)|((x_16_test[k]>>>8)&0x00FF));
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // try
        // {
        // x_16_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // catch (FileNotFoundException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }

        /*
		 * Option 2:
		 */
        // RandomAccessFile x_16_datain_rand = null;
        // int k;
        // try
        // {
        // x_16_datain_rand = new RandomAccessFile(new File(x_16_filename), "r");
        // try
        // {
        // x_16_datain_rand.seek(x_16_file_offset);
        // for(k = 0; k < psEnc.sCmn.frame_length; k++)
        // {
        // try
        // {
        // x_16_test[k] = x_16_datain_rand.readShort();
        // x_16[k] = (short) (((x_16_test[k]<<8)&0xFF00)|((x_16_test[k]>>>8)&0x00FF));
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // x_16_file_offset += k;
        // }
        // catch (IOException e1)
        // {
        // // TODO Auto-generated catch block
        // e1.printStackTrace();
        // }
        // try
        // {
        // x_16_datain_rand.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // catch (FileNotFoundException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        /**
         * Optino 3:
         */
        // x_16_filename += frame_cnt;
        // DataInputStream x_16_datain = null;
        // try
        // {
        // x_16_datain = new DataInputStream(
        // new FileInputStream(
        // new File(x_16_filename)));
        // for(int k = 0; k < psEnc.sCmn.frame_length; k++)
        // {
        // try
        // {
        // x_16_test[k] = x_16_datain.readShort();
        // x_16[k] = (short) (((x_16_test[k]<<8)&0xFF00)|((x_16_test[k]>>>8)&0x00FF));
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // try
        // {
        // x_16_datain.close();
        // }
        // catch (IOException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }
        // catch (FileNotFoundException e)
        // {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // frame_cnt++;
        /* TEST END*********************************************************************** */

        /* Call NSQ */
        val PredCoef_Q12_dim1_tmp = ShortArray(PredCoef_Q12.size * PredCoef_Q12[0].size)
        var PredCoef_Q12_offset = 0
        for (PredCoef_Q12_i in PredCoef_Q12.indices) {
            System.arraycopy(PredCoef_Q12[PredCoef_Q12_i], 0, PredCoef_Q12_dim1_tmp,
                    PredCoef_Q12_offset, PredCoef_Q12[PredCoef_Q12_i].size)
            PredCoef_Q12_offset += PredCoef_Q12[PredCoef_Q12_i].size
        }
        if (useLBRR != 0) {
            // psEnc.NoiseShapingQuantizer( psEnc.sCmn, psEncCtrl.sCmn, psEnc.sNSQ_LBRR,
            // x_16, q, psEncCtrl.sCmn.NLSFInterpCoef_Q2, PredCoef_Q12[ 0 ], LTPCoef_Q14, AR2_Q13,
            // HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14 );\
            psEnc.NoiseShapingQuantizer(psEnc.sCmn, psEncCtrl.sCmn, psEnc.sNSQ_LBRR, x_16, q,
                    psEncCtrl.sCmn.NLSFInterpCoef_Q2, PredCoef_Q12_dim1_tmp, LTPCoef_Q14, AR2_Q13,
                    HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14)
            // psEnc.NoiseShapingQuantizer( &psEnc->sCmn, &psEncCtrl->sCmn, &psEnc->sNSQ_LBRR,
            // x_16, q, psEncCtrl->sCmn.NLSFInterpCoef_Q2, PredCoef_Q12[ 0 ], LTPCoef_Q14, AR2_Q13,
            // HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14 );
        } else {
            // psEnc.NoiseShapingQuantizer( psEnc.sCmn, psEncCtrl.sCmn, psEnc.sNSQ,
            // x_16, q, psEncCtrl.sCmn.NLSFInterpCoef_Q2, PredCoef_Q12[ 0 ], LTPCoef_Q14, AR2_Q13,
            // HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14 );
            psEnc.NoiseShapingQuantizer(psEnc.sCmn, psEncCtrl.sCmn, psEnc.sNSQ, x_16, q,
                    psEncCtrl.sCmn.NLSFInterpCoef_Q2, PredCoef_Q12_dim1_tmp, LTPCoef_Q14, AR2_Q13,
                    HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14)
            // psEnc.NoiseShapingQuantizer( &psEnc->sCmn, &psEncCtrl->sCmn, &psEnc->sNSQ,
            // x_16, q, psEncCtrl->sCmn.NLSFInterpCoef_Q2, PredCoef_Q12[ 0 ], LTPCoef_Q14, AR2_Q13,
            // HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, Lambda_Q10, LTP_scale_Q14 );
        }
    }
}