/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

import org.atalk.util.ArrayIOUtils.readShort
import kotlin.math.abs
import kotlin.math.cos
import kotlin.Int as Int1

/**
 * Implements an iLBC encoder.
 *
 * @author Jean Lorchat
 * @author Lyubomir Marinov
 */
internal class ilbc_encoder(  /* encoding mode, either 20 or 30 ms */
        var mode: Int1
) {
    /* analysis filter state */
    private var anaMem: FloatArray // LPC_FILTERORDER];

    /* old lsf parameters for interpolation */
    private var lsfold: FloatArray // LPC_FILTERORDER];
    private var lsfdeqold: FloatArray // LPC_FILTERORDER];

    /* signal buffer for LP analysis */
    private var lpc_buffer: FloatArray // LPC_LOOKBACK + BLOCKL_MAX];

    /* state of input HP filter */
    private var hpimem: FloatArray // 4];
    var ULP_inst: ilbc_ulp? = null

    /**
     * @param syntDenum
     * Currently not used
     */
    /* encoder methods start here */
    /*----------------------------------------------------------------*
	 *  predictive noise shaping encoding of scaled start state
	 *  (subrutine for StateSearchW)
	 *---------------------------------------------------------------*/
    private fun AbsQuantW(`in`: FloatArray,
            /* (i) vector to encode */
            in_idx: Int1, syntDenum: FloatArray?,
            /* (i) denominator of synthesis filter */
            syntDenum_idx_: Int1, weightDenum: FloatArray,
            /* (i) denominator of weighting filter */
            weightDenum_idx_: Int1, out: IntArray,
            /* (o) vector of quantizer indexes */
            len: Int1,
            /*
			 * (i) length of vector to encode and vector of quantizer indexes
			 */
            state_first: Int1
            /*
             * (i) position of start state in the 80 vec
             */
    ) {
        // float *syntOut;
        var syntDenum_idx = syntDenum_idx_
        var weightDenum_idx = weightDenum_idx_
        val syntOutBuf = FloatArray(ilbc_constants.LPC_FILTERORDER
                + ilbc_constants.STATE_SHORT_LEN_30MS)
        var toQ: Float
        val index = IntArray(1)

        /* initialization of buffer for filtering */
        for (li in 0 until ilbc_constants.LPC_FILTERORDER) {
            syntOutBuf[li] = 0.0f
        }
        // memset(syntOutBuf, 0, LPC_FILTERORDER*sizeof(float));

        /* initialization of pointer for filtering */

        // syntOut = &syntOutBuf[LPC_FILTERORDER];
        val syntOut: Int1 = ilbc_constants.LPC_FILTERORDER

        /* synthesis and weighting filters on input */
        if (state_first != 0) {
            ilbc_common.AllPoleFilter(`in`, in_idx, weightDenum, weightDenum_idx,
                    ilbc_constants.SUBL, ilbc_constants.LPC_FILTERORDER)
        } else {
            ilbc_common.AllPoleFilter(`in`, in_idx, weightDenum, weightDenum_idx,
                    ULP_inst!!.state_short_len - ilbc_constants.SUBL,
                    ilbc_constants.LPC_FILTERORDER)
        }

        /* encoding loop */
        var n: Int1 = 0
        while (n < len) {
            /* time update of filter coefficients */
            if (state_first != 0 && n == ilbc_constants.SUBL) {
                syntDenum_idx += ilbc_constants.LPC_FILTERORDER + 1
                weightDenum_idx += ilbc_constants.LPC_FILTERORDER + 1

                /* synthesis and weighting filters on input */
                ilbc_common.AllPoleFilter(`in`, in_idx + n, weightDenum, weightDenum_idx, len - n,
                        ilbc_constants.LPC_FILTERORDER)
            } else if (state_first == 0 && n == ULP_inst!!.state_short_len - ilbc_constants.SUBL) {
                syntDenum_idx += ilbc_constants.LPC_FILTERORDER + 1
                weightDenum_idx += ilbc_constants.LPC_FILTERORDER + 1

                /* synthesis and weighting filters on input */
                ilbc_common.AllPoleFilter(`in`, in_idx + n, weightDenum, weightDenum_idx, len - n,
                        ilbc_constants.LPC_FILTERORDER)
            }

            /* prediction of synthesized and weighted input */
            syntOutBuf[syntOut + n] = 0.0f
            ilbc_common.AllPoleFilter(syntOutBuf, syntOut + n, weightDenum, weightDenum_idx, 1,
                    ilbc_constants.LPC_FILTERORDER)

            /* quantization */
            toQ = `in`[in_idx + n] - syntOutBuf[syntOut + n]
            sort_sq(index, 0, toQ, ilbc_constants.state_sq3Tbl, 8)
            out[n] = index[0]
            syntOutBuf[syntOut + n] = ilbc_constants.state_sq3Tbl[out[n]]

            /* update of the prediction filter */
            ilbc_common.AllPoleFilter(syntOutBuf, syntOut + n, weightDenum, weightDenum_idx, 1,
                    ilbc_constants.LPC_FILTERORDER)
            n++
        }
    }

    /*----------------------------------------------------------------*
	 *  encoding of start state
	 *---------------------------------------------------------------*/
    private fun StateSearchW(residual: FloatArray?,  /* (i) target residual vector */
            residual_idx: Int1, syntDenum: FloatArray,  /* (i) lpc synthesis filter */
            syntDenum_idx: Int1, weightDenum: FloatArray,  /* (i) weighting filter denuminator */
            weightDenum_idx: Int1, idxForMax: IntArray,
            /*
             * (o) quantizer index for maximum amplitude
             */
            idxVec: IntArray,  /* (o) vector of quantization indexes */
            len: Int1,  /* (i) length of all vectors */
            state_first: Int1
    )
    /*
     * (i) position of start state in the 80 vec
     */
    {
        var maxVal: Float
        val tmpbuf = FloatArray(ilbc_constants.LPC_FILTERORDER + 2
                * ilbc_constants.STATE_SHORT_LEN_30MS)
        // float *tmp,
        val numerator = FloatArray(1 + ilbc_constants.LPC_FILTERORDER)
        val foutbuf = FloatArray(ilbc_constants.LPC_FILTERORDER + 2
                * ilbc_constants.STATE_SHORT_LEN_30MS)
        // , *fout;
        val scal: Float

        /* initialization of buffers and filter coefficients */
        for (li in 0 until ilbc_constants.LPC_FILTERORDER) {
            tmpbuf[li] = 0.0f
            foutbuf[li] = 0.0f
        }

        // memset(tmpbuf, 0, LPC_FILTERORDER*sizeof(float));
        // memset(foutbuf, 0, LPC_FILTERORDER*sizeof(float));
        var k = 0
        while (k < ilbc_constants.LPC_FILTERORDER) {
            numerator[k] = syntDenum[syntDenum_idx + ilbc_constants.LPC_FILTERORDER - k]
            k++
        }
        numerator[ilbc_constants.LPC_FILTERORDER] = syntDenum[syntDenum_idx]
        // tmp = &tmpbuf[LPC_FILTERORDER];
        val tmp = ilbc_constants.LPC_FILTERORDER
        // fout = &foutbuf[LPC_FILTERORDER];
        val fout = ilbc_constants.LPC_FILTERORDER

        /* circular convolution with the all-pass filter */
        System.arraycopy(residual as Any, residual_idx, tmpbuf, tmp, len)
        // memcpy(tmp, residual, len*sizeof(float));
        for (li in 0 until len) tmpbuf[tmp + len + li] = 0.0f
        // memset(tmp+len, 0, len*sizeof(float));
        ilbc_common.ZeroPoleFilter(tmpbuf, tmp, numerator, syntDenum, syntDenum_idx, 2 * len,
                ilbc_constants.LPC_FILTERORDER, foutbuf, fout)
        k = 0
        while (k < len) {
            foutbuf[fout + k] += foutbuf[fout + k + len]
            k++
        }

        /* identification of the maximum amplitude value */
        maxVal = foutbuf[fout + 0]
        k = 1
        while (k < len) {
            if (foutbuf[fout + k] * foutbuf[fout + k] > maxVal * maxVal) {
                maxVal = foutbuf[fout + k]
            }
            k++
        }
        maxVal = abs(maxVal)

        /* encoding of the maximum amplitude value */
        if (maxVal < 10.0f) {
            maxVal = 10.0f
        }
        // log10 is since 1.5
        // maxVal = (float)Math.log10(maxVal);
        maxVal = (Math.log(maxVal.toDouble()) / Math.log(10.0)).toFloat()
        sort_sq(idxForMax, 0, maxVal, ilbc_constants.state_frgqTbl, 64)

        /*
		 * decoding of the maximum amplitude representation value, and corresponding scaling of
		 * start state
		 */
        maxVal = ilbc_constants.state_frgqTbl[idxForMax[0]]
        val qmax = Math.pow(10.0, maxVal.toDouble()).toFloat()
        scal = 4.5f / qmax
        k = 0
        while (k < len) {
            foutbuf[fout + k] *= scal
            k++
        }

        /* predictive noise shaping encoding of scaled start state */
        AbsQuantW(foutbuf, fout, syntDenum, syntDenum_idx, weightDenum, weightDenum_idx, idxVec,
                len, state_first)
    }

    /*----------------------------------------------------------------*
	 *  conversion from lpc coefficients to lsf coefficients
	 *---------------------------------------------------------------*/
    private fun a2lsf(freq: FloatArray,  /* (o) lsf coefficients */
            freq_idx: Int1, a: FloatArray) /* (i) lpc coefficients */ {
        val steps = floatArrayOf(0.00635f, 0.003175f, 0.0015875f, 0.00079375f)
        var step: Float
        var step_idx: Int1
        var lsp_index: Int1
        val p = FloatArray(ilbc_constants.LPC_HALFORDER)
        val q = FloatArray(ilbc_constants.LPC_HALFORDER)
        val p_pre = FloatArray(ilbc_constants.LPC_HALFORDER)
        val q_pre = FloatArray(ilbc_constants.LPC_HALFORDER)
        val old_p = 0
        val old_q = 1
        // float *old;
        val olds = FloatArray(2)
        var old: Int1
        // float *pq_coef;
        var pq_coef: FloatArray
        var omega: Float
        var old_omega: Float
        var hlp: Float
        var hlp1: Float
        var hlp2: Float
        var hlp3: Float
        var hlp4: Float
        var hlp5: Float
        var i: Int1 = 0
        while (i < ilbc_constants.LPC_HALFORDER) {
            p[i] = -1.0f * (a[i + 1] + a[ilbc_constants.LPC_FILTERORDER - i])
            q[i] = a[ilbc_constants.LPC_FILTERORDER - i] - a[i + 1]
            i++
        }
        p_pre[0] = -1.0f - p[0]
        p_pre[1] = -p_pre[0] - p[1]
        p_pre[2] = -p_pre[1] - p[2]
        p_pre[3] = -p_pre[2] - p[3]
        p_pre[4] = -p_pre[3] - p[4]
        p_pre[4] = p_pre[4] / 2
        q_pre[0] = 1.0f - q[0]
        q_pre[1] = q_pre[0] - q[1]
        q_pre[2] = q_pre[1] - q[2]
        q_pre[3] = q_pre[2] - q[3]
        q_pre[4] = q_pre[3] - q[4]
        q_pre[4] = q_pre[4] / 2
        omega = 0.0f
        old_omega = 0.0f
        olds[old_p] = ilbc_constants.DOUBLE_MAX
        olds[old_q] = ilbc_constants.DOUBLE_MAX

        /*
		 * Here we loop through lsp_index to find all the LPC_FILTERORDER roots for omega.
		 */
        lsp_index = 0
        while (lsp_index < ilbc_constants.LPC_FILTERORDER) {


            /*
			 * Depending on lsp_index being even or odd, we alternatively solve the roots for the
			 * two LSP equations.
			 */
            if (lsp_index and 0x1 == 0) {
                pq_coef = p_pre
                old = old_p
            } else {
                pq_coef = q_pre
                old = old_q
            }

            /* Start with low resolution grid */
            step_idx = 0
            step = steps[step_idx]
            while (step_idx < ilbc_constants.LSF_NUMBER_OF_STEPS) {


                /*
				 * cos(10piw) + pq(0)cos(8piw) + pq(1)cos(6piw) + pq(2)cos(4piw) + pq(3)cod(2piw) +
				 * pq(4)
				 */
                hlp = cos((omega * ilbc_constants.TWO_PI).toDouble()).toFloat()
                hlp1 = 2.0f * hlp + pq_coef[0]
                hlp2 = 2.0f * hlp * hlp1 - 1.0.toFloat() + pq_coef[1]
                hlp3 = 2.0f * hlp * hlp2 - hlp1 + pq_coef[2]
                hlp4 = 2.0f * hlp * hlp3 - hlp2 + pq_coef[3]
                hlp5 = hlp * hlp4 - hlp3 + pq_coef[4]
                if (hlp5 * olds[old] <= 0.0f || omega >= 0.5) {
                    if (step_idx == ilbc_constants.LSF_NUMBER_OF_STEPS - 1) {
                        if (abs(hlp5) >= abs(olds[old])) {
                            // System.out.println("acces index " + freq_idx + lsp_index);
                            freq[freq_idx + lsp_index] = omega - step
                        } else {
                            // System.out.println("acces index " + freq_idx + lsp_index);
                            freq[freq_idx + lsp_index] = omega
                        }
                        if (olds[old] >= 0.0f) {
                            olds[old] = -1.0f * ilbc_constants.DOUBLE_MAX
                        } else {
                            olds[old] = ilbc_constants.DOUBLE_MAX
                        }
                        omega = old_omega
                        step_idx = ilbc_constants.LSF_NUMBER_OF_STEPS
                    } else {
                        if (step_idx == 0) {
                            old_omega = omega
                        }
                        step_idx++
                        omega -= steps[step_idx]

                        /* Go back one grid step */
                        step = steps[step_idx]
                    }
                } else {

                    /*
					 * increment omega until they are of different sign, and we know there is at
					 * least one root between omega and old_omega
					 */
                    olds[old] = hlp5
                    omega += step
                }
            }
            lsp_index++
        }
        i = 0
        while (i < ilbc_constants.LPC_FILTERORDER) {

            // System.out.println("acces index " + freq_idx + i);
            freq[freq_idx + i] = freq[freq_idx + i] * ilbc_constants.TWO_PI
            i++
        }
    }

    /*----------------------------------------------------------------*
	 *  lpc analysis (subrutine to LPCencode)
	 *---------------------------------------------------------------*/
    private fun simpleAnalysis(lsf: FloatArray,  /* (o) lsf coefficients */
            data: FloatArray?) /* (i) new data vector */ {
        var isLoop: Int1
        val temp = FloatArray(ilbc_constants.BLOCKL_MAX)
        val lp = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        val lp2 = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        val r = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        isLoop = ilbc_constants.LPC_LOOKBACK + ilbc_constants.BLOCKL_MAX - ULP_inst!!.blockl
        // System.out.println("copie 1");
        // System.out.println("\nInformations de copie : \nbuffer source : " + data.length +
        // " octets\n"+
        // "buffer cible : " + this.lpc_buffer.length + "octets\n" +
        // "  offset : " + is + "octets\n" +
        // "longueur de la copie : " + this.ULP_inst.blockl);
        System.arraycopy(data as Any, 0, lpc_buffer, isLoop, ULP_inst!!.blockl)
        // memcpy(iLBCenc_inst->lpc_buffer+is,data,iLBCenc_inst->blockl*sizeof(float));

        /* No lookahead, last window is asymmetric */
        var k = 0
        while (k < ULP_inst!!.lpc_n) {
            isLoop = ilbc_constants.LPC_LOOKBACK
            if (k < ULP_inst!!.lpc_n - 1) {
                window(temp, ilbc_constants.lpc_winTbl, lpc_buffer, 0,
                        ilbc_constants.BLOCKL_MAX)
            } else {
                window(temp, ilbc_constants.lpc_asymwinTbl, lpc_buffer, isLoop,
                        ilbc_constants.BLOCKL_MAX)
            }
            autocorr(r, temp, ilbc_constants.BLOCKL_MAX, ilbc_constants.LPC_FILTERORDER)
            window(r, r, ilbc_constants.lpc_lagwinTbl, 0, ilbc_constants.LPC_FILTERORDER + 1)
            levdurb(lp, temp, r, ilbc_constants.LPC_FILTERORDER)
            ilbc_common.bwexpand(lp2, 0, lp, ilbc_constants.LPC_CHIRP_SYNTDENUM,
                    ilbc_constants.LPC_FILTERORDER + 1)
            a2lsf(lsf, k * ilbc_constants.LPC_FILTERORDER, lp2)
            k++
        }
        isLoop = ilbc_constants.LPC_LOOKBACK + ilbc_constants.BLOCKL_MAX - ULP_inst!!.blockl
        // System.out.println("copie 2");
        System.arraycopy(lpc_buffer, ilbc_constants.LPC_LOOKBACK + ilbc_constants.BLOCKL_MAX
                - isLoop, lpc_buffer, 0, isLoop)
        // memmove(iLBCenc_inst->lpc_buffer,
        // iLBCenc_inst->lpc_buffer+LPC_LOOKBACK+BLOCKL_MAX-is,
        // is*sizeof(float));
    }

    /*----------------------------------------------------------------*
	 *  lsf interpolator and conversion from lsf to a coefficients
	 *  (subrutine to SimpleInterpolateLSF)
	 *---------------------------------------------------------------*/
    private fun lsfinterpolate2aEnc(a: FloatArray,  /* (o) lpc coefficients */
            lsf1: FloatArray,  /* (i) first set of lsf coefficients */
            lsf2: FloatArray,  /* (i) second set of lsf coefficients */
            lsf2_idx: Int1, coef: Float,  /*
								 * (i) weighting coefficient to use between lsf1 and lsf2
								 */
            length: Long /* (i) length of coefficient vectors */
    ) {
        val lsftmp = FloatArray(ilbc_constants.LPC_FILTERORDER)
        ilbc_common.interpolate(lsftmp, lsf1, lsf2, lsf2_idx, coef, length.toInt())
        ilbc_common.lsf2a(a, lsftmp)
    }

    /*----------------------------------------------------------------*
	 *  lsf interpolator (subrutine to LPCencode)
	 *---------------------------------------------------------------*/
    fun simpleInterpolateLSF(syntdenum: FloatArray,
            /*
             * (o) the synthesis filter denominator resulting
             * from the quantized interpolated lsf
             */
            weightdenum: FloatArray,
            /*
             * (o) the weighting filter denominator resulting from the unquantized
             * interpolated lsf
             */
            lsf: FloatArray,  /* (i) the unquantized lsf coefficients */
            lsfdeq: FloatArray,  /* (i) the dequantized lsf coefficients */
            lsfold: FloatArray,
            /*
             * (i) the unquantized lsf coefficients of the previous signal frame
             */
            lsfdeqold: FloatArray,
            /*
             * (i) the dequantized lsf coefficients of the previous signal frame
             */
            length: Int1
    ) /* (i) should equate LPC_FILTERORDER */
    {
        var i: Int1
        var pos: Int1
        val lp = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        val lsf2: Int1 = length
        val lsfdeq2: Int1 = length
        // lsf2 = lsf + length;
        // lsfdeq2 = lsfdeq + length;
        val lp_length: Int1 = length + 1
        if (ULP_inst!!.mode == 30) {
            /*
			 * sub-frame 1: Interpolation between old and first
			 * 
			 * set of lsf coefficients
			 */
            lsfinterpolate2aEnc(lp, lsfdeqold, lsfdeq, 0, ilbc_constants.lsf_weightTbl_30ms[0],
                    length.toLong())
            System.arraycopy(lp, 0, syntdenum, 0, lp_length)
            // memcpy(syntdenum,lp,lp_length*sizeof(float));
            lsfinterpolate2aEnc(lp, lsfold, lsf, 0, ilbc_constants.lsf_weightTbl_30ms[0], length.toLong())
            ilbc_common.bwexpand(weightdenum, 0, lp, ilbc_constants.LPC_CHIRP_WEIGHTDENUM,
                    lp_length)

            /*
			 * sub-frame 2 to 6: Interpolation between first and second set of lsf coefficients
			 */
            pos = lp_length
            i = 1
            while (i < ULP_inst!!.nsub) {
                lsfinterpolate2aEnc(lp, lsfdeq, lsfdeq, lsfdeq2,
                        ilbc_constants.lsf_weightTbl_30ms[i], length.toLong())
                System.arraycopy(lp, 0, syntdenum, pos, lp_length)
                // memcpy(syntdenum + pos,lp,lp_length*sizeof(float));
                lsfinterpolate2aEnc(lp, lsf, lsf, lsf2, ilbc_constants.lsf_weightTbl_30ms[i],
                        length.toLong())
                ilbc_common.bwexpand(weightdenum, pos, lp, ilbc_constants.LPC_CHIRP_WEIGHTDENUM,
                        lp_length)
                pos += lp_length
                i++
            }
        } else {
            pos = 0
            i = 0
            while (i < ULP_inst!!.nsub) {

                // System.out.println("ici ?");
                lsfinterpolate2aEnc(lp, lsfdeqold, lsfdeq, 0,
                        ilbc_constants.lsf_weightTbl_20ms[i], length.toLong())
                // System.out.println("ici !");
                System.arraycopy(lp, 0, syntdenum, pos, lp_length)
                for (li in 0 until lp_length)  // System.out.println("interpolate syntdenum [" + (li+pos) +"] is worth " +
                // syntdenum[li+pos]);
                // memcpy(syntdenum+pos,lp,lp_length*sizeof(float));
                    lsfinterpolate2aEnc(lp, lsfold, lsf, 0, ilbc_constants.lsf_weightTbl_20ms[i],
                            length.toLong())
                ilbc_common.bwexpand(weightdenum, pos, lp, ilbc_constants.LPC_CHIRP_WEIGHTDENUM,
                        lp_length)
                pos += lp_length
                i++
            }
        }

        /* update memory */
        if (ULP_inst!!.mode == 30) {
            System.arraycopy(lsf, lsf2, lsfold, 0, length)
            // memcpy(lsfold, lsf2, length*sizeof(float));
            System.arraycopy(lsfdeq, lsfdeq2, lsfdeqold, 0, length)
            // memcpy(lsfdeqold, lsfdeq2, length*sizeof(float));
        } else {
            System.arraycopy(lsf, 0, lsfold, 0, length)
            // memcpy(lsfold, lsf, length*sizeof(float));
            System.arraycopy(lsfdeq, 0, lsfdeqold, 0, length)
            // memcpy(lsfdeqold, lsfdeq, length*sizeof(float));
        }
    }

    /*----------------------------------------------------------------*
	 *  lsf quantizer (subrutine to LPCencode)
	 *---------------------------------------------------------------*/
    fun SimplelsfQ(lsfdeq: FloatArray,
            /*
             * (o) dequantized lsf coefficients (dimension FILTERORDER)
             */
            index: IntArray,  /* (o) quantization index */
            lsf: FloatArray,
            /*
        	 * (i) the lsf coefficient vector to be quantized (dimension FILTERORDER )
		     */
            lpc_n: Int1 /* (i) number of lsf sets to quantize */
    ) {
        /* Quantize first LSF with memoryless split VQ */
        SplitVQ(lsfdeq, 0, index, 0, lsf, 0, ilbc_constants.lsfCbTbl, ilbc_constants.LSF_NSPLIT,
                ilbc_constants.dim_lsfCbTbl, ilbc_constants.size_lsfCbTbl)
        if (lpc_n == 2) {
            /* Quantize second LSF with memoryless split VQ */
            SplitVQ(lsfdeq, ilbc_constants.LPC_FILTERORDER, index, ilbc_constants.LSF_NSPLIT, lsf,
                    ilbc_constants.LPC_FILTERORDER, ilbc_constants.lsfCbTbl, ilbc_constants.LSF_NSPLIT,
                    ilbc_constants.dim_lsfCbTbl, ilbc_constants.size_lsfCbTbl)
        }
    }

    /*----------------------------------------------------------------*
	 *  lpc encoder
	 *---------------------------------------------------------------*/
    private fun LPCencode(syntdenum: FloatArray,
            /*
             * (i/o) synthesis filter coefficients before/after encoding
             */
            weightdenum: FloatArray,
            /*
             * (i/o) weighting denumerator coefficients before/after encoding
             */
            lsf_index: IntArray,  /* (o) lsf quantization index */
            data: FloatArray?) /* (i) lsf coefficients to quantize */ {
        val lsf = FloatArray(ilbc_constants.LPC_FILTERORDER * ilbc_constants.LPC_N_MAX)
        val lsfdeq = FloatArray(ilbc_constants.LPC_FILTERORDER * ilbc_constants.LPC_N_MAX)
        simpleAnalysis(lsf, data)
        // for (int li = 0; li < ilbc_constants.LPC_FILTERORDER * ilbc_constants.LPC_N_MAX; li++)
        // System.out.println("postSA n-" + li + " is worth " + lsf[li]);
        // for (int li = 0; li < ilbc_constants.BLOCKL_MAX; li++)
        // System.out.println("data postSA n-" + li + " is worth " + data[li]);
        SimplelsfQ(lsfdeq, lsf_index, lsf, ULP_inst!!.lpc_n)
        // for (int li = 0; li < ilbc_constants.LPC_FILTERORDER * ilbc_constants.LPC_N_MAX; li++)
        // System.out.println("postSlsfQ n-" + li + " is worth " + lsfdeq[li]);
        // for (int li = 0; li < lsf_index.length; li++)
        // System.out.println("index postSlsfQ n-" + li + " is worth " + lsf_index[li]);
        ilbc_common.LSF_check(lsfdeq, ilbc_constants.LPC_FILTERORDER, ULP_inst!!.lpc_n)
        // System.out.println("check gives " + change);
        simpleInterpolateLSF(syntdenum, weightdenum, lsf, lsfdeq, lsfold, lsfdeqold,
                ilbc_constants.LPC_FILTERORDER)
        // for (int li = 0; li < syntdenum.length; li++)
        // System.out.println("syntdenum[" + li +"] is worth " + syntdenum[li]);
    }

    private fun iCBSearch(index: IntArray,  /* (o) Codebook indices */
            index_idx: Int1, gain_index: IntArray,  /* (o) Gain quantization indices */
            gain_index_idx: Int1,
            intarget: FloatArray?,  /* (i) Target vector for encoding */
            intarget_idx: Int1, mem: FloatArray?,  /* (i) Buffer for codebook construction */
            mem_idx: Int1, lMem: Int1,  /* (i) Length of buffer */
            lTarget: Int1,  /* (i) Length of vector */
            nStages: Int1,  /* (i) Number of codebook stages */
            weightDenum: FloatArray,  /* (i) weighting filter coefficients */
            weightDenum_idx: Int1, weightState: FloatArray?,  /* (i) weighting filter state */
            block: Int1
    ) /* (i) the sub-block number */ {
        var j: Int1
        var icount: Int1
        var best_index: Int1
        var range: Int1
        var counter: Int1
        var max_measure: Float
        var gain: Float
        var measure: Float
        var crossDot: Float
        var ftmp: Float
        val gains = FloatArray(ilbc_constants.CB_NSTAGES)
        val target = FloatArray(ilbc_constants.SUBL)
        var base_index: Int1
        var sInd: Int1
        var eInd: Int1
        var base_size: Int1
        var sIndAug: Int1
        var eIndAug: Int1
        val buf = FloatArray(ilbc_constants.CB_MEML + ilbc_constants.SUBL + (2
                * ilbc_constants.LPC_FILTERORDER))
        val invenergy = FloatArray(ilbc_constants.CB_EXPAND * 128)
        val energy = FloatArray(ilbc_constants.CB_EXPAND * 128)
        // float *pp, *ppi=0, *ppo=0, *ppe=0;
        var pp: Int1
        var ppi = 0
        var ppo = 0
        var ppe = 0
        var ppt: FloatArray
        val cbvectors = FloatArray(ilbc_constants.CB_MEML)
        val cvec = FloatArray(ilbc_constants.SUBL)
        val aug_vec = FloatArray(ilbc_constants.SUBL)
        val a = FloatArray(1)
        val b = IntArray(1)
        val c = FloatArray(1)
        for (li in 0 until ilbc_constants.SUBL) cvec[li] = 0.0f
        // memset(cvec,0,SUBL*sizeof(float));

        /* Determine size of codebook sections */
        base_size = lMem - lTarget + 1
        if (lTarget == ilbc_constants.SUBL) {
            base_size = lMem - lTarget + 1 + lTarget / 2
        }

        /* setup buffer for weighting */
        System.arraycopy(weightState, 0, buf, 0, ilbc_constants.LPC_FILTERORDER)
        // memcpy(buf,weightState,sizeof(float)*LPC_FILTERORDER);
        System.arraycopy(mem, mem_idx, buf, ilbc_constants.LPC_FILTERORDER, lMem)
        // memcpy(buf+LPC_FILTERORDER,mem,lMem*sizeof(float));
        System.arraycopy(intarget, intarget_idx, buf, ilbc_constants.LPC_FILTERORDER + lMem, lTarget)
        // memcpy(buf+LPC_FILTERORDER+lMem,intarget,lTarget*sizeof(float));

        // System.out.println("beginning of mem");
        // for (int li = 0; li < lMem; li++)
        // System.out.println("mem[" + li + "] = " + mem[li+mem_idx]);
        // System.out.println("end of mem");

        // System.out.println("plages : [0-" + ilbc_constants.LPC_FILTERORDER +
        // "], puis [" + ilbc_constants.LPC_FILTERORDER + "-" + (ilbc_constants.LPC_FILTERORDER +
        // lMem) +
        // "], puis [" + (ilbc_constants.LPC_FILTERORDER + lMem) +
        // "-" + (ilbc_constants.LPC_FILTERORDER + lMem + lTarget) + "]");

        // System.out.println("beginning of buffer");

        // for (int li = 0; li < buf.length; li++)
        // System.out.println("buffer[" + li + "] = " + buf[li]);

        // System.out.println("end of buffer");
        /* weighting */
        ilbc_common.AllPoleFilter(buf, ilbc_constants.LPC_FILTERORDER, weightDenum,
                weightDenum_idx, lMem + lTarget, ilbc_constants.LPC_FILTERORDER)

        /* Construct the codebook and target needed */
        System.arraycopy(buf, ilbc_constants.LPC_FILTERORDER + lMem, target, 0, lTarget)
        // memcpy(target, buf+LPC_FILTERORDER+lMem, lTarget*sizeof(float));
        var tene = 0.0f
        var i: Int1 = 0
        while (i < lTarget) {
            tene += target[i] * target[i]
            i++
        }

        /*
		 * Prepare search over one more codebook section. This section is created by filtering the
		 * original buffer with a filter.
		 */
        filteredCBvecs(cbvectors, buf, ilbc_constants.LPC_FILTERORDER, lMem)

        /* The Main Loop over stages */
        var stage: Int1 = 0
        while (stage < nStages) {
            range = ilbc_constants.search_rangeTbl[block]!![stage]

            /* initialize search measure */
            max_measure = -10000000.0f
            gain = 0.0f
            best_index = 0

            /*
			 * Compute cross dot product between the target and the CB memory
			 */
            crossDot = 0.0f
            pp = ilbc_constants.LPC_FILTERORDER + lMem - lTarget
            // pp=buf+ilbc_constants.LPC_FILTERORDER+lMem-lTarget;
            j = 0
            while (j < lTarget) {
                crossDot += target[j] * buf[pp]
                pp++
                j++
            }
            if (stage == 0) {

                /*
				 * Calculate energy in the first block of 'lTarget' samples.
				 */
                ppe = 0
                ppi = ilbc_constants.LPC_FILTERORDER + lMem - lTarget - 1
                ppo = ilbc_constants.LPC_FILTERORDER + lMem - 1
                // ppe = energy;
                // ppi = buf+ilbc_constants.LPC_FILTERORDER+lMem-lTarget-1;
                // ppo = buf+ilbc_constants.LPC_FILTERORDER+lMem-1;
                energy[ppe] = 0.0f
                pp = ilbc_constants.LPC_FILTERORDER + lMem - lTarget
                // pp=buf+ilbc_constants.LPC_FILTERORDER+lMem-lTarget;
                j = 0
                while (j < lTarget) {
                    energy[ppe] += buf[pp] * buf[pp]
                    pp++
                    j++
                }
                if (energy[ppe] > 0.0f) {
                    invenergy[0] = 1.0f / (energy[ppe] + ilbc_constants.EPS)
                } else {
                    invenergy[0] = 0.0f
                }
                ppe++
                measure = -10000000.0f
                if (crossDot > 0.0f) {
                    measure = crossDot * crossDot * invenergy[0]
                }
            } else {
                measure = crossDot * crossDot * invenergy[0]
            }

            /* check if measure is better */
            ftmp = crossDot * invenergy[0]
            if (measure > max_measure && Math.abs(ftmp) < ilbc_constants.CB_MAXGAIN) {
                best_index = 0
                max_measure = measure
                gain = ftmp
            }

            /*
			 * loop over the main first codebook section, full search
			 */
            icount = 1
            while (icount < range) {


                /* calculate measure */
                crossDot = 0.0f
                pp = ilbc_constants.LPC_FILTERORDER + lMem - lTarget - icount
                // pp = buf+LPC_FILTERORDER+lMem-lTarget-icount;
                j = 0
                while (j < lTarget) {
                    crossDot += target[j] * buf[pp]
                    pp++
                    j++
                }
                if (stage == 0) {
                    energy[ppe] = energy[icount - 1] + buf[ppi] * buf[ppi] - buf[ppo] * buf[ppo]
                    ppe++
                    ppo--
                    ppi--
                    if (energy[icount] > 0.0f) {
                        invenergy[icount] = 1.0f / (energy[icount] + ilbc_constants.EPS)
                    } else {
                        invenergy[icount] = 0.0f
                    }
                    measure = -10000000.0f
                    if (crossDot > 0.0f) {
                        measure = crossDot * crossDot * invenergy[icount]
                    }
                } else {
                    measure = crossDot * crossDot * invenergy[icount]
                }

                /* check if measure is better */
                ftmp = crossDot * invenergy[icount]
                if (measure > max_measure && Math.abs(ftmp) < ilbc_constants.CB_MAXGAIN) {
                    best_index = icount
                    max_measure = measure
                    gain = ftmp
                }
                icount++
            }

            /*
			 * Loop over augmented part in the first codebook section, full search. The vectors are
			 * interpolated.
			 */
            if (lTarget == ilbc_constants.SUBL) {

                /*
				 * Search for best possible cb vector and compute the CB-vectors' energy.
				 */
                a[0] = max_measure
                b[0] = best_index
                c[0] = gain
                searchAugmentedCB(20, 39, stage, base_size - lTarget / 2, target, buf,
                        ilbc_constants.LPC_FILTERORDER + lMem, a, b, c, energy, invenergy)
                max_measure = a[0]
                best_index = b[0]
                gain = c[0]
            }

            /* set search range for following codebook sections */

            // System.out.println("best index : " + best_index);
            base_index = best_index

            /* unrestricted search */
            if (ilbc_constants.CB_RESRANGE == -1) {
                // System.out.println("on met a 0");
                sInd = 0
                eInd = range - 1
                sIndAug = 20
                eIndAug = 39
            } else {
                /* Initialize search indices */
                sIndAug = 0
                eIndAug = 0
                sInd = base_index - ilbc_constants.CB_RESRANGE / 2
                // System.out.println("on met a " + base_index + " - " +
                // ilbc_constants.CB_RESRANGE/2 + " = " + sInd);
                eInd = sInd + ilbc_constants.CB_RESRANGE
                if (lTarget == ilbc_constants.SUBL) {
                    if (sInd < 0) {
                        sIndAug = 40 + sInd
                        eIndAug = 39
                        // System.out.println("On met encore a 0");
                        sInd = 0
                    } else if (base_index < base_size - 20) {
                        if (eInd > range) {
                            sInd -= eInd - range
                            // System.out.println("on retire " + eInd + " - " + range +
                            // " pour arriver a " + sInd);
                            eInd = range
                        }
                    } else { /* base_index >= (base_size-20) */
                        if (sInd < base_size - 20) {
                            sIndAug = 20
                            sInd = 0
                            // System.out.println("on remet encore a 0");
                            eInd = 0
                            eIndAug = 19 + ilbc_constants.CB_RESRANGE
                            if (eIndAug > 39) {
                                eInd = eIndAug - 39
                                eIndAug = 39
                            }
                        } else {
                            sIndAug = 20 + sInd - (base_size - 20)
                            eIndAug = 39
                            sInd = 0
                            // System.out.println("on remetz4 a zero");
                            eInd = ilbc_constants.CB_RESRANGE - (eIndAug - sIndAug + 1)
                        }
                    }
                } else { /* lTarget = 22 or 23 */
                    if (sInd < 0) {
                        eInd -= sInd
                        sInd = 0
                        // System.out.println("on remet x5 a zero");
                    }
                    if (eInd > range) {
                        sInd -= eInd - range
                        // System.out.println("on retire " + eInd + " - " + range +
                        // " pour arriver a " + sInd);
                        eInd = range
                    }
                }
            }

            /* search of higher codebook section */

            /* index search range */
            counter = sInd
            // System.out.println("on ajoute " + base_size + " pour arriver a " + sInd);
            sInd += base_size
            eInd += base_size
            if (stage == 0) {
                // ppe = energy+base_size;
                ppe = base_size
                energy[ppe] = 0.0f
                pp = lMem - lTarget
                // pp=cbvectors+lMem-lTarget;
                j = 0
                while (j < lTarget) {
                    energy[ppe] += cbvectors[pp] * cbvectors[pp]
                    pp++
                    j++
                }
                ppi = lMem - 1 - lTarget
                ppo = lMem - 1
                // ppi = cbvectors + lMem - 1 - lTarget;
                // ppo = cbvectors + lMem - 1;
                j = 0
                while (j < range - 1) {
                    energy[ppe + 1] = (energy[ppe] + cbvectors[ppi] * cbvectors[ppi]
                            - cbvectors[ppo] * cbvectors[ppo])
                    ppo--
                    ppi--
                    ppe++
                    j++
                }
            }

            /* loop over search range */
            icount = sInd
            while (icount < eInd) {


                /* calculate measure */
                crossDot = 0.0f
                pp = lMem - counter++ - lTarget
                // pp=cbvectors + lMem - (counter++) - lTarget;

                // System.out.println("lMem : " + lMem);
                // System.out.println("counter : " + counter);
                // System.out.println("target : " + lTarget);
                j = 0
                while (j < lTarget) {
                    crossDot += target[j] * cbvectors[pp]
                    pp++
                    j++
                }
                if (energy[icount] > 0.0f) {
                    invenergy[icount] = 1.0f / (energy[icount] + ilbc_constants.EPS)
                } else {
                    invenergy[icount] = 0.0f
                }
                if (stage == 0) {
                    measure = -10000000.0f
                    if (crossDot > 0.0f) {
                        measure = crossDot * crossDot * invenergy[icount]
                    }
                } else {
                    measure = crossDot * crossDot * invenergy[icount]
                }

                /* check if measure is better */
                ftmp = crossDot * invenergy[icount]
                if (measure > max_measure && Math.abs(ftmp) < ilbc_constants.CB_MAXGAIN) {
                    best_index = icount
                    max_measure = measure
                    gain = ftmp
                }
                icount++
            }

            /* Search the augmented CB inside the limited range. */
            if (lTarget == ilbc_constants.SUBL && sIndAug != 0) {
                a[0] = max_measure
                b[0] = best_index
                c[0] = gain
                searchAugmentedCB(sIndAug, eIndAug, stage, 2 * base_size - 20, target, cbvectors,
                        lMem, a, b, c, energy, invenergy)
                max_measure = a[0]
                best_index = b[0]
                gain = c[0]
            }

            /* record best index */
            index[index_idx + stage] = best_index

            /* gain quantization */
            if (stage == 0) {
                if (gain < 0.0f) {
                    gain = 0.0f
                }
                if (gain > ilbc_constants.CB_MAXGAIN) {
                    gain = ilbc_constants.CB_MAXGAIN
                }
                gain = ilbc_common.gainquant(gain, 1.0f, 32, gain_index, gain_index_idx + stage)
            } else {
                gain = if (stage == 1) {
                    ilbc_common.gainquant(gain, Math.abs(gains[stage - 1]), 16, gain_index,
                            gain_index_idx + stage)
                } else {
                    ilbc_common.gainquant(gain, Math.abs(gains[stage - 1]), 8, gain_index,
                            gain_index_idx + stage)
                }
            }

            /*
			 * Extract the best (according to measure) codebook vector
			 */
            if (lTarget == ilbc_constants.STATE_LEN - ULP_inst!!.state_short_len) {
                if (index[index_idx + stage] < base_size) {
                    pp = ilbc_constants.LPC_FILTERORDER + lMem - lTarget - index[index_idx + stage]
                    // pp=buf+ilbc_constants.LPC_FILTERORDER+lMem-lTarget-index[stage];
                    ppt = buf
                } else {
                    pp = lMem - lTarget - index[index_idx + stage] + base_size
                    // pp=cbvectors+lMem-lTarget-index[stage]+base_size;
                    ppt = cbvectors
                }
            } else {
                if (index[index_idx + stage] < base_size) {
                    if (index[index_idx + stage] < base_size - 20) {
                        pp = (ilbc_constants.LPC_FILTERORDER + lMem - lTarget
                                - index[index_idx + stage])
                        // pp=buf+LPC_FILTERORDER+lMem-lTarget-index[stage];
                        ppt = buf
                    } else {
                        createAugmentedVec(index[index_idx + stage] - base_size + 40, buf,
                                ilbc_constants.LPC_FILTERORDER + lMem, aug_vec)
                        // pp=aug_vec;
                        pp = 0
                        ppt = aug_vec
                    }
                } else {
                    var filterno: Int1
                    var position: Int1
                    filterno = index[index_idx + stage] / base_size
                    position = index[index_idx + stage] - filterno * base_size
                    if (position < base_size - 20) {
                        pp = filterno * lMem - lTarget - index[index_idx + stage] + filterno * base_size
                        // pp=cbvectors+filterno*lMem-lTarget-index[stage]+filterno*base_size;
                        ppt = cbvectors
                    } else {
                        createAugmentedVec(index[index_idx + stage] - (filterno + 1) * base_size
                                + 40, cbvectors, filterno * lMem, aug_vec)
                        // pp=aug_vec;
                        pp = 0
                        ppt = aug_vec
                    }
                }
            }

            /*
			 * Subtract the best codebook vector, according to measure, from the target vector
			 */
            j = 0
            while (j < lTarget) {
                cvec[j] += gain * ppt[pp]
                target[j] -= gain * ppt[pp]
                pp++
                j++
            }

            /* record quantized gain */
            gains[stage] = gain
            stage++
        }

        /* Gain adjustment for energy matching */
        var cene: Float = 0.0f
        i = 0
        while (i < lTarget) {
            cene += cvec[i] * cvec[i]
            i++
        }
        j = gain_index[gain_index_idx + 0]
        i = gain_index[gain_index_idx + 0]
        while (i < 32) {
            ftmp = cene * ilbc_constants.gain_sq5Tbl[i] * ilbc_constants.gain_sq5Tbl[i]
            if ((ftmp < (tene * gains[0] * gains[0])) && (ilbc_constants.gain_sq5Tbl[j] < (2.0f * gains[0]))) {
                j = i
            }
            i++
        }
        gain_index[gain_index_idx + 0] = j
    }

    private fun index_conv_enc(index: IntArray) /* (i/o) Codebook indexes */ {
        var k: Int1 = 1
        while (k < ilbc_constants.CB_NSTAGES) {
            if (index[k] in 108..171) {
                index[k] -= 64
            } else if (index[k] >= 236) {
                index[k] -= 128
            } else {
                /* ERROR */
            }
            k++
        }
    }

    private fun hpInput(In: FloatArray,  /* (i) vector to filter */
            len: Int1,  /* (i) length of vector to filter */
            Out: FloatArray,  /* (o) the resulting filtered vector */
            mem: FloatArray) /* (i/o) the filter state */ {
        // float *pi, *po;
        /* all-zero section */
        // pi = &In[0];
        var pi = 0
        // po = &Out[0];
        var po = 0
        var i = 0
        while (i < len) {

            // System.out.println(Out[po] + " + " + ilbc_constants.hpi_zero_coefsTbl[0] + " * " +
            // In[pi] + "((" +
            // ilbc_constants.hpi_zero_coefsTbl[0] * In[pi]);
            Out[po] = ilbc_constants.hpi_zero_coefsTbl[0] * In[pi]
            // System.out.println("then *po=" + Out[po]);
            // System.out.println(Out[po] + " + " + ilbc_constants.hpi_zero_coefsTbl[1] +" * "+
            // mem[0] + "((" +
            // ilbc_constants.hpi_zero_coefsTbl[1] * mem[0]);
            Out[po] += ilbc_constants.hpi_zero_coefsTbl[1] * mem[0]
            // System.out.println("then *po=" + Out[po]);
            // System.out.println(Out[po] + " + " + ilbc_constants.hpi_zero_coefsTbl[2] + " * " +
            // mem[1] + "((" +
            // ilbc_constants.hpi_zero_coefsTbl[2] * mem[1]);
            Out[po] += ilbc_constants.hpi_zero_coefsTbl[2] * mem[1]
            // System.out.println("then *po=" + Out[po]);
            mem[1] = mem[0]
            mem[0] = In[pi]
            po++
            pi++
            i++
        }

        /* all-pole section */

        // po = &Out[0];
        po = 0
        i = 0
        while (i < len) {

            // System.out.println("(part 2-"+i+") *po=" + Out[po]);
            // System.out.println(Out[po] + " - " + ilbc_constants.hpi_pole_coefsTbl[1] + " * " +
            // mem[2] + " ((" +
            // ilbc_constants.hpi_pole_coefsTbl[1] * mem[2]);
            Out[po] -= ilbc_constants.hpi_pole_coefsTbl[1] * mem[2]
            // System.out.println("then *po=" + Out[po]);
            // System.out.println(Out[po] + " - " + ilbc_constants.hpi_pole_coefsTbl[2] + " * " +
            // mem[3] + " ((" +
            // ilbc_constants.hpi_pole_coefsTbl[2] * mem[3]);
            Out[po] -= ilbc_constants.hpi_pole_coefsTbl[2] * mem[3]
            // System.out.println("2then *po=" + Out[po]);
            mem[3] = mem[2]
            mem[2] = Out[po]
            po++
            i++
        }
    }

    /*----------------------------------------------------------------*
	 *  calculation of auto correlation
	 *---------------------------------------------------------------*/
    private fun autocorr(r: FloatArray,  /* (o) autocorrelation vector */
            x: FloatArray,  /* (i) data vector */
            N: Int1,  /* (i) length of data vector */
            order: Int1
    ) /*
					 * largest lag for calculated autocorrelations
					 */ {
        var lag: Int1
        var n: Int1
        var sum: Float
        lag = 0
        while (lag <= order) {
            sum = 0f
            n = 0
            while (n < N - lag) {
                sum += x[n] * x[n + lag]
                n++
            }
            r[lag] = sum
            lag++
        }
    }

    /*----------------------------------------------------------------*
	 *  window multiplication
	 *---------------------------------------------------------------*/
    fun window(z: FloatArray,  /* (o) the windowed data */
            x: FloatArray?,  /* (i) the original data vector */
            y: FloatArray?,  /* (i) the window */
            y_idx: Int1, N: Int1
    ) /* (i) length of all vectors */ {
        var i: Int1
        i = 0
        while (i < N) {
            z[i] = x!![i] * y!![i + y_idx]
            i++
        }
    }

    /*----------------------------------------------------------------*
	 *  levinson-durbin solution for lpc coefficients
	 *---------------------------------------------------------------*/
    private fun levdurb(a: FloatArray,  /*
									 * (o) lpc coefficient vector starting with 1.0f
									 */
            k: FloatArray,  /* (o) reflection coefficients */
            r: FloatArray,  /* (i) autocorrelation vector */
            order: Int1
    ) /* (i) order of lpc filter */ {
        var sum: Float
        var alpha: Float
        var m: Int1
        var m_h: Int1
        var i: Int1
        a[0] = 1.0f
        if (r[0] < ilbc_constants.EPS) { /* if r[0] <= 0, set LPC coeff. to zero */
            i = 0
            while (i < order) {
                k[i] = 0f
                a[i + 1] = 0f
                i++
            }
        } else {
            k[0] = -r[1] / r[0]
            a[1] = k[0]
            alpha = r[0] + r[1] * k[0]
            m = 1
            while (m < order) {
                sum = r[m + 1]
                i = 0
                while (i < m) {
                    sum += a[i + 1] * r[m - i]
                    i++
                }
                k[m] = -sum / alpha
                alpha += k[m] * sum
                m_h = m + 1 shr 1
                i = 0
                while (i < m_h) {
                    sum = a[i + 1] + k[m] * a[m - i]
                    a[m - i] += k[m] * a[i + 1]
                    a[i + 1] = sum
                    i++
                }
                a[m + 1] = k[m]
                m++
            }
        }
    }

    /*----------------------------------------------------------------*
	 *  vector quantization
	 *---------------------------------------------------------------*/
    fun vq(Xq: FloatArray,  /* (o) the quantized vector */
            Xq_idx: Int1, index: IntArray,  /* (o) the quantization index */
            index_idx: Int1, CB: FloatArray?,  /* (i) the vector quantization codebook */
            CB_idx: Int1, X: FloatArray,  /* (i) the vector to quantize */
            X_idx: Int1, n_cb: Int1,  /* (i) the number of vectors in the codebook */
            dim: Int1
    ) /* (i) the dimension of all vectors */ {
        var i: Int1
        var j: Int1
        var pos: Int1
        var minindex: Int1
        var dist: Float
        var tmp: Float
        var mindist: Float
        pos = 0
        mindist = ilbc_constants.DOUBLE_MAX
        minindex = 0
        j = 0
        while (j < n_cb) {
            dist = X[X_idx] - CB!![pos + CB_idx]
            dist *= dist
            i = 1
            while (i < dim) {
                tmp = X[i + X_idx] - CB[pos + i + CB_idx]
                dist += tmp * tmp
                i++
            }
            if (dist < mindist) {
                mindist = dist
                minindex = j
            }
            pos += dim
            j++
        }
        i = 0
        while (i < dim) {
            Xq[i + Xq_idx] = CB!![minindex * dim + i + CB_idx]
            i++
        }
        index[index_idx] = minindex
    }

    /*----------------------------------------------------------------*
	 *  split vector quantization
	 *---------------------------------------------------------------*/
    private fun SplitVQ(qX: FloatArray,  /* (o) the quantized vector */
            qX_idx: Int1, index: IntArray,  /*
								 * (o) a vector of indexes for all vector codebooks in the split
								 */
            index_idx: Int1, X: FloatArray,  /* (i) the vector to quantize */
            X_idx: Int1, CB: FloatArray?,  /* (i) the quantizer codebook */
            nsplit: Int1,  /* the number of vector splits */
            dim: IntArray?,  /* the dimension of X and qX */
            cbsize: IntArray?) /* the number of vectors in the codebook */ {
        var cb_pos: Int1
        var X_pos: Int1
        var i: Int1
        cb_pos = 0
        X_pos = 0
        i = 0
        while (i < nsplit) {
            vq(qX, X_pos + qX_idx, index, i + index_idx, CB, cb_pos, X, X_pos + X_idx, cbsize!![i],
                    dim!![i])
            X_pos += dim[i]
            cb_pos += dim[i] * cbsize[i]
            i++
        }
    }

    /*----------------------------------------------------------------*
	 *  scalar quantization
	 *---------------------------------------------------------------*/
    private fun sort_sq( /* on renvoie xq et on modifie index par effet de bord */ // float *xq, /* (o) the quantized value */
            index: IntArray,  /* (o) the quantization index */
            index_idx: Int1, x: Float,  /* (i) the value to quantize */
            cb: FloatArray?,  /* (i) the quantization codebook */
            cb_size: Int1
    ): Float /* (i) the size of the quantization codebook */ {
        var i: Int1
        val xq: Float
        if (x <= cb!![0]) {
            // *index = 0;
            index[index_idx] = 0
            xq = cb[0]
        } else {
            i = 0
            while (x > cb[i] && i < cb_size - 1) {
                i++
            }
            if (x > (cb[i] + cb[i - 1]) / 2) {
                index[index_idx] = i
                xq = cb[i]
            } else {
                index[index_idx] = i - 1
                xq = cb[i - 1]
            }
        }
        return xq
    }

    /*---------------------------------------------------------------*
	 *  Classification of subframes to localize start state
	 *--------------------------------------------------------------*/
    private fun FrameClassify( /* index to the max-energy sub-frame */
            residual: FloatArray): Int1 /* (i) lpc residual signal */ {
        var max_ssqEn: Float
        val fssqEn = FloatArray(ilbc_constants.NSUB_MAX)
        val bssqEn = FloatArray(ilbc_constants.NSUB_MAX)
        var pp: Int1
        var l: Int1
        var max_ssqEn_n: Int1
        // float [] ssqEn_win[NSUB_MAX-1]={(float)0.8,(float)0.9,
        val ssqEn_win = floatArrayOf(0.8f, 0.9f, 1.0f, 0.9f, 0.8f)
        val sampEn_win = floatArrayOf(1.0f / 6.0f, 2.0f / 6.0f, 3.0f / 6.0f, 4.0f / 6.0f, 5.0f / 6.0f)

        /* init the front and back energies to zero */
        for (li in 0 until ilbc_constants.NSUB_MAX) fssqEn[li] = 0.0f
        // memset(fssqEn, 0, NSUB_MAX*sizeof(float));
        for (li in 0 until ilbc_constants.NSUB_MAX) bssqEn[li] = 0.0f
        // memset(bssqEn, 0, NSUB_MAX*sizeof(float));

        /* Calculate front of first seqence */
        var n: Int1 = 0
        // pp=residual;
        pp = 0
        l = 0
        while (l < 5) {
            fssqEn[n] += sampEn_win[l] * residual[pp] * residual[pp]
            pp++
            l++
        }
        l = 5
        while (l < ilbc_constants.SUBL) {
            fssqEn[n] += residual[pp] * residual[pp]
            pp++
            l++
        }

        /* Calculate front and back of all middle sequences */
        n = 1
        while (n < ULP_inst!!.nsub - 1) {

            // pp=residual+n*SUBL;
            pp = n * ilbc_constants.SUBL
            l = 0
            while (l < 5) {
                fssqEn[n] += sampEn_win[l] * residual[pp] * residual[pp]
                bssqEn[n] += residual[pp] * residual[pp]
                pp++
                l++
            }
            l = 5
            while (l < ilbc_constants.SUBL - 5) {
                fssqEn[n] += residual[pp] * residual[pp]
                bssqEn[n] += residual[pp] * residual[pp]
                pp++
                l++
            }
            l = ilbc_constants.SUBL - 5
            while (l < ilbc_constants.SUBL) {
                fssqEn[n] += residual[pp] * residual[pp]
                bssqEn[n] += (sampEn_win[ilbc_constants.SUBL - l - 1] * residual[pp]
                        * residual[pp])
                pp++
                l++
            }
            n++
        }

        /* Calculate back of last seqence */
        n = ULP_inst!!.nsub - 1
        pp = n * ilbc_constants.SUBL
        l = 0
        while (l < ilbc_constants.SUBL - 5) {
            bssqEn[n] += residual[pp] * residual[pp]
            pp++
            l++
        }
        l = ilbc_constants.SUBL - 5
        while (l < ilbc_constants.SUBL) {
            bssqEn[n] += sampEn_win[ilbc_constants.SUBL - l - 1] * residual[pp] * residual[pp]
            pp++
            l++
        }

        /*
		 * find the index to the weighted 80 sample with most energy
		 */
        l = if (ULP_inst!!.mode == 20) 1 else 0
        max_ssqEn = (fssqEn[0] + bssqEn[1]) * ssqEn_win[l]
        max_ssqEn_n = 1
        n = 2
        while (n < ULP_inst!!.nsub) {
            l++
            if ((fssqEn[n - 1] + bssqEn[n]) * ssqEn_win[l] > max_ssqEn) {
                max_ssqEn = (fssqEn[n - 1] + bssqEn[n]) * ssqEn_win[l]
                max_ssqEn_n = n
            }
            n++
        }
        return max_ssqEn_n
    }

    /* from anaFilter.c, perform LP analysis filtering */
    private fun anaFilter(In: FloatArray, in_idx: Int1, a: FloatArray, a_idx: Int1, len: Int1, Out: FloatArray,
            out_idx: Int1, mem: FloatArray) {
        var i: Int1
        var j: Int1
        var po: Int1
        var pi: Int1
        var pm: Int1
        var pa: Int1
        po = out_idx

        /* Filter first part using memory from past */
        i = 0
        while (i < ilbc_constants.LPC_FILTERORDER) {
            pi = in_idx + i
            pm = ilbc_constants.LPC_FILTERORDER - 1
            pa = a_idx
            Out[po] = 0.0f
            j = 0
            while (j <= i) {
                Out[po] += a[pa] * In[pi]
                pa++
                pi--
                j++
            }
            j = i + 1
            while (j < ilbc_constants.LPC_FILTERORDER + 1) {
                Out[po] += a[pa] * mem[pm]
                pa++
                pm--
                j++
            }
            po++
            i++
        }

        /*
		 * Filter last part where the state is entirely in the input vector
		 */
        i = ilbc_constants.LPC_FILTERORDER
        while (i < len) {
            pi = in_idx + i
            pa = a_idx
            Out[po] = 0.0f
            j = 0
            while (j < ilbc_constants.LPC_FILTERORDER + 1) {
                Out[po] += a[pa] * In[pi]
                pa++
                pi--
                j++
            }
            po++
            i++
        }

        /* Update state vector */
        System.arraycopy(In, in_idx + len - ilbc_constants.LPC_FILTERORDER, mem, 0,
                ilbc_constants.LPC_FILTERORDER)
    }

    /*----------------------------------------------------------------*
	 *  Construct an additional codebook vector by filtering the
	 *  initial codebook buffer. This vector is then used to expand
	 *  the codebook with an additional section.
	 *---------------------------------------------------------------*/
    private fun filteredCBvecs(cbvectors: FloatArray, mem: FloatArray, mem_idx: Int1, lMem: Int1) {
        var i: Int1
        var j: Int1
        var k: Int1
        var pp: Int1
        var pp1: Int1
        val tempbuff2: FloatArray
        var pos: Int1
        tempbuff2 = FloatArray(ilbc_constants.CB_MEML + ilbc_constants.CB_FILTERLEN)
        i = 0
        while (i < ilbc_constants.CB_HALFFILTERLEN) {
            tempbuff2[i] = 0.0f
            i++
        }
        System.arraycopy(mem, mem_idx, tempbuff2, ilbc_constants.CB_HALFFILTERLEN - 1, lMem)
        i = lMem + ilbc_constants.CB_HALFFILTERLEN - 1
        while (i < lMem + ilbc_constants.CB_FILTERLEN) {
            tempbuff2[i] = 0.0f
            i++
        }

        /* Create codebook vector for higher section by filtering */

        /* do filtering */
        pos = 0
        i = 0
        while (i < lMem) {
            cbvectors[i] = 0f
            i++
        }
        k = 0
        while (k < lMem) {

            // pp=&tempbuff2[k];
            pp = k
            // pp1=&cbfiltersTbl[CB_FILTERLEN-1];
            pp1 = ilbc_constants.CB_FILTERLEN - 1
            j = 0
            while (j < ilbc_constants.CB_FILTERLEN) {
                cbvectors[pos] += tempbuff2[pp] * ilbc_constants.cbfiltersTbl[pp1]
                pp++
                pp1--
                j++
            }
            pos++
            k++
        }
    }

    /*----------------------------------------------------------------*
	 *  Search the augmented part of the codebook to find the best
	 *  measure.
	 *----------------------------------------------------------------*/
    private fun searchAugmentedCB(low: Int1,  /* (i) Start index for the search */
            high: Int1,  /* (i) End index for the search */
            stage: Int1,  /* (i) Current stage */
            startIndex: Int1,  /*
						 * (i) Codebook index for the first aug vector
						 */
            target: FloatArray,  /* (i) Target vector for encoding */
            buffer: FloatArray,  /*
						 * (i) Pointer to the end of the buffer for augmented codebook construction
						 */
            buffer_idx: Int1, max_measure: FloatArray,  /* (i/o) Currently maximum measure */
            best_index: IntArray,  /* (o) Currently the best index */
            gain: FloatArray,  /* (o) Currently the best gain */
            energy: FloatArray,  /*
						 * (o) Energy of augmented codebook vectors
						 */
            invenergy: FloatArray /*
						 * (o) Inv energy of augmented codebook vectors
						 */
    ) {
        var icount: Int1
        var ilow: Int1
        var j: Int1
        var tmpIndex: Int1
        var pp: Int1
        var ppo: Int1
        var ppi: Int1
        var ppe: Int1
        var crossDot: Float
        var alfa: Float
        var weighted: Float
        var measure: Float
        var nrjRecursive: Float
        var ftmp: Float

        /*
		 * Compute the energy for the first (low-5) noninterpolated samples
		 */

        // for (pp = 0; pp < buffer.length; pp++)
        // System.out.println("buffer[" + (pp - buffer_idx) + "] = " + buffer[pp]);
        nrjRecursive = 0.0f
        // pp = buffer - low + 1;
        pp = 1 - low + buffer_idx
        j = 0
        while (j < low - 5) {
            nrjRecursive += buffer[pp] * buffer[pp]
            pp++
            j++
        }
        ppe = buffer_idx - low

        // System.out.println("energie recursive " + nrjRecursive);
        icount = low
        while (icount <= high) {


            /*
			 * Index of the codebook vector used for retrieving energy values
			 */
            tmpIndex = startIndex + icount - 20
            ilow = icount - 4

            /* Update the energy recursively to save complexity */
            nrjRecursive = nrjRecursive + buffer[ppe] * buffer[ppe]
            ppe--
            energy[tmpIndex] = nrjRecursive

            /*
			 * Compute cross dot product for the first (low-5) samples
			 */
            crossDot = 0.0f
            pp = buffer_idx - icount
            j = 0
            while (j < ilow) {
                crossDot += target[j] * buffer[pp]
                pp++
                j++
            }

            /* interpolation */
            alfa = 0.2.toFloat()
            ppo = buffer_idx - 4
            ppi = buffer_idx - icount - 4
            j = ilow
            while (j < icount) {
                weighted = (1.0f - alfa) * buffer[ppo] + alfa * buffer[ppi]
                ppo++
                ppi++
                energy[tmpIndex] += weighted * weighted
                crossDot += target[j] * weighted
                alfa += 0.2.toFloat()
                j++
            }

            /*
			 * Compute energy and cross dot product for the remaining samples
			 */
            pp = buffer_idx - icount
            j = icount
            while (j < ilbc_constants.SUBL) {
                energy[tmpIndex] += buffer[pp] * buffer[pp]
                crossDot += target[j] * buffer[pp]
                pp++
                j++
            }
            if (energy[tmpIndex] > 0.0f) {
                invenergy[tmpIndex] = 1.0f / (energy[tmpIndex] + ilbc_constants.EPS)
            } else {
                invenergy[tmpIndex] = 0.0f
            }
            if (stage == 0) {
                measure = -10000000.0f
                if (crossDot > 0.0f) {
                    measure = crossDot * crossDot * invenergy[tmpIndex]
                }
            } else {
                measure = crossDot * crossDot * invenergy[tmpIndex]
            }

            /* check if measure is better */
            ftmp = crossDot * invenergy[tmpIndex]

            // System.out.println("on compare " + measure + " et " + max_measure[0]);
            // System.out.println("ainsi que " + Math.abs(ftmp) + " et " +
            // ilbc_constants.CB_MAXGAIN);
            if (measure > max_measure[0] && Math.abs(ftmp) < ilbc_constants.CB_MAXGAIN) {
                // System.out.println("new best index at " + tmpIndex + ", where icount = " +
                // icount);
                best_index[0] = tmpIndex
                max_measure[0] = measure
                gain[0] = ftmp
            }
            icount++
        }
    }

    /*----------------------------------------------------------------*
	 *  Recreate a specific codebook vector from the augmented part.
	 *
	 *----------------------------------------------------------------*/
    private fun createAugmentedVec(index: Int1, buffer: FloatArray, buffer_idx: Int1, cbVec: FloatArray) {
        var j: Int1
        var weighted: Float
        val ilow: Int1 = index - 5

        /* copy the first noninterpolated part */
        var pp: Int1 = buffer_idx - index
        System.arraycopy(buffer, pp, cbVec, 0, index)
        // memcpy(cbVec,pp,sizeof(float)*index);

        /* interpolation */
        val alfa1: Float = 0.2.toFloat()
        var alfa: Float = 0.0f
        // ppo = buffer-5;
        var ppo: Int1 = buffer_idx - 5
        // ppi = buffer-index-5;
        var ppi: Int1 = buffer_idx - index - 5
        j = ilow
        while (j < index) {

            // weighted = ((float)1.0f-alfa)*(*ppo)+alfa*(*ppi);
            weighted = (1.0f - alfa) * buffer[ppo] + alfa * buffer[ppi]
            ppo++
            ppi++
            cbVec[j] = weighted
            alfa += alfa1
            j++
        }

        /* copy the second noninterpolated part */

        // pp = buffer - index;
        pp = buffer_idx - index
        // memcpy(cbVec+index,pp,sizeof(float)*(SUBL-index));
        System.arraycopy(buffer, pp, cbVec, index, ilbc_constants.SUBL - index)
    }

    init {
        ULP_inst = if (mode == 30 || mode == 20) {
            ilbc_ulp(mode)
        } else {
            throw Error("invalid mode")
        }
        anaMem = FloatArray(ilbc_constants.LPC_FILTERORDER)
        lsfold = FloatArray(ilbc_constants.LPC_FILTERORDER)
        lsfdeqold = FloatArray(ilbc_constants.LPC_FILTERORDER)
        lpc_buffer = FloatArray(ilbc_constants.LPC_LOOKBACK + ilbc_constants.BLOCKL_MAX)
        hpimem = FloatArray(4)
        for (li in anaMem.indices) anaMem[li] = 0.0f
        System.arraycopy(ilbc_constants.lsfmeanTbl, 0, lsfdeqold, 0,
                ilbc_constants.LPC_FILTERORDER)
        // for (int li = 0; li < lsfold.length; li++)
        // lsfold[li] = 0.0f;
        System.arraycopy(ilbc_constants.lsfmeanTbl, 0, lsfold, 0,
                ilbc_constants.LPC_FILTERORDER)
        // for (int li = 0; li < lsfdeqold.length; li++)
        // lsfdeqold[li] = 0.0f;
        for (li in lpc_buffer.indices) lpc_buffer[li] = 0.0f
        for (li in hpimem.indices) hpimem[li] = 0.0f

        // memset((*iLBCenc_inst).anaMem, 0,
        // LPC_FILTERORDER*sizeof(float));
        // memcpy((*iLBCenc_inst).lsfold, lsfmeanTbl,
        // LPC_FILTERORDER*sizeof(float));
        // memcpy((*iLBCenc_inst).lsfdeqold, lsfmeanTbl,
        // LPC_FILTERORDER*sizeof(float));
        // memset((*iLBCenc_inst).lpc_buffer, 0,
        // (LPC_LOOKBACK+BLOCKL_MAX)*sizeof(float));
        // memset((*iLBCenc_inst).hpimem, 0, 4*sizeof(float));

        // return (iLBCenc_inst->no_of_bytes);
    }

    // public int encode(short encoded_data[], short data[])
    // {
    // for (int i = 0; i < encoded_data.length; i ++) {
    // data[i%data.length] = encoded_data[i];
    // }
    // if (mode == 20)
    // return ilbc_constants.BLOCKL_20MS;
    // else
    // return ilbc_constants.BLOCKL_30MS;
    // }
    fun encode(encoded: ByteArray, encodedOffset: Int1, decoded: ByteArray?, decodedOffset: Int1): Int1 {
        var decodedOffset = decodedOffset
        val block = FloatArray(ULP_inst!!.blockl)
        val en_data = bitstream(encoded, encodedOffset, ULP_inst!!.no_of_bytes)
        // char en_data[] = new char [this.ULP_inst.no_of_bytes];

        /* convert signal to float */
        var k: Int1 = 0
        while (k < ULP_inst!!.blockl) {
            block[k] = readShort(decoded!!, decodedOffset).toFloat()
            k++
            decodedOffset += 2
        }

        // for (int li = 0; li < block.length; li++)
        // System.out.println("block " + li + " : " + block[li]);

        /* do the actual encoding */
        iLBC_encode(en_data, block)
        return ULP_inst!!.no_of_bytes
    }

    fun iLBC_encode(bytes: bitstream,  /* (o) encoded data bits iLBC */
            block: FloatArray) /* (o) speech vector to encode */ {
        var start: Int1
        val idxForMax = IntArray(1)
        var n: Int1
        var k: Int1
        var meml_gotten: Int1
        val Nback: Int1
        var i: Int1
        // unsigned char *pbytes;
        val diff: Int1
        val start_pos: Int1
        var state_first: Int1
        var en1: Float
        var en2: Float
        var index: Int1
        var ulp: Int1
        // int [] firstpart = new int[1];
        var firstpart: Int1
        var subcount: Int1
        var subframe: Int1
        val data = FloatArray(ilbc_constants.BLOCKL_MAX)
        val residual = FloatArray(ilbc_constants.BLOCKL_MAX)
        val reverseResidual = FloatArray(ilbc_constants.BLOCKL_MAX)
        val idxVec = IntArray(ilbc_constants.STATE_LEN)
        val reverseDecresidual = FloatArray(ilbc_constants.BLOCKL_MAX)
        val mem = FloatArray(ilbc_constants.CB_MEML)
        val gain_index = IntArray(ilbc_constants.CB_NSTAGES * ilbc_constants.NASUB_MAX)
        val extra_gain_index = IntArray(ilbc_constants.CB_NSTAGES)
        val cb_index = IntArray(ilbc_constants.CB_NSTAGES * ilbc_constants.NASUB_MAX)
        val extra_cb_index = IntArray(ilbc_constants.CB_NSTAGES)
        val lsf_i = IntArray(ilbc_constants.LSF_NSPLIT * ilbc_constants.LPC_N_MAX)
        val weightState = FloatArray(ilbc_constants.LPC_FILTERORDER)
        val syntdenum = FloatArray(ilbc_constants.NSUB_MAX
                * (ilbc_constants.LPC_FILTERORDER + 1))
        val weightdenum = FloatArray(ilbc_constants.NSUB_MAX
                * (ilbc_constants.LPC_FILTERORDER + 1))
        val decresidual = FloatArray(ilbc_constants.BLOCKL_MAX)
        var pack: bitpack?

        /*
		 * high pass filtering of input signal if such is not done prior to calling this function
		 */

        // System.out.println("Data prior to hpinput call");
        // for (int li = 0; li < data.length; li++)
        // System.out.println("index : " + li + " and value " + data[li]);
        // System.out.println("Mem prior to hpinput call");
        // for (int li = 0; li < this.hpimem.length; li++)
        // System.out.println("index : " + li + " and value " + this.hpimem[li]);
        hpInput(block, ULP_inst!!.blockl, data, hpimem)
        // System.out.println("Data after hpinput call");
        // for (int li = 0; li < data.length; li++)
        // System.out.println("index : " + li + " and value " + data[li]);
        // System.out.println("Mem after hpinput call");
        // for (int li = 0; li < this.hpimem.length; li++)
        // System.out.println("index : " + li + " and value " + this.hpimem[li]);

        /* otherwise simply copy */

        /* memcpy(data,block,iLBCenc_inst->blockl*sizeof(float)); */

        /* LPC of hp filtered input data */
        LPCencode(syntdenum, weightdenum, lsf_i, data)

        // for (int li = 0; li < ilbc_constants.NSUB_MAX*(ilbc_constants.LPC_FILTERORDER+1); li++)
        // System.out.println("postLPC n-" + li + " is worth " + syntdenum[li] + ", " +
        // weightdenum[li]);

        /* inverse filter to get residual */
        n = 0
        while (n < ULP_inst!!.nsub) {
            anaFilter(data, n * ilbc_constants.SUBL, syntdenum, n
                    * (ilbc_constants.LPC_FILTERORDER + 1), ilbc_constants.SUBL, residual, n
                    * ilbc_constants.SUBL, anaMem)
            n++
        }

        // for (int li = 0; li < ilbc_constants.BLOCKL_MAX; li++)
        // System.out.println("block residual n-" + li + " is worth " + residual[li]);

        /* find state location */
        start = FrameClassify(residual)

        /*
		 * check if state should be in first or last part of the two subframes
		 */
        diff = ilbc_constants.STATE_LEN - ULP_inst!!.state_short_len
        en1 = 0f
        index = (start - 1) * ilbc_constants.SUBL
        i = 0
        while (i < ULP_inst!!.state_short_len) {
            en1 += residual[index + i] * residual[index + i]
            i++
        }
        en2 = 0f
        index = (start - 1) * ilbc_constants.SUBL + diff
        i = 0
        while (i < ULP_inst!!.state_short_len) {
            en2 += residual[index + i] * residual[index + i]
            i++
        }
        if (en1 > en2) {
            state_first = 1
            start_pos = (start - 1) * ilbc_constants.SUBL
        } else {
            state_first = 0
            start_pos = (start - 1) * ilbc_constants.SUBL + diff
        }

        /* scalar quantization of state */
        StateSearchW(residual, start_pos, syntdenum, (start - 1)
                * (ilbc_constants.LPC_FILTERORDER + 1), weightdenum, (start - 1)
                * (ilbc_constants.LPC_FILTERORDER + 1), idxForMax, idxVec,
                ULP_inst!!.state_short_len, state_first)
        ilbc_common.StateConstructW(idxForMax[0], idxVec, syntdenum, (start - 1)
                * (ilbc_constants.LPC_FILTERORDER + 1), decresidual, start_pos,
                ULP_inst!!.state_short_len)

        /* predictive quantization in state */
        if (state_first != 0) { /* put adaptive part in the end */

            /* setup memory */
            for (li in 0 until ilbc_constants.CB_MEML - ULP_inst!!.state_short_len) mem[li] = 0.0f
            System.arraycopy(decresidual, start_pos, mem, ilbc_constants.CB_MEML
                    - ULP_inst!!.state_short_len, ULP_inst!!.state_short_len)
            // memcpy(mem+ilbc_constants.CB_MEML-this.ULP_inst.state_short_len,
            // decresidual+start_pos,
            // this.ULP_inst.state_short_len*sizeof(float));
            for (li in 0 until ilbc_constants.LPC_FILTERORDER) weightState[li] = 0.0f
            // memset(weightState, 0, ilbc_constants.LPC_FILTERORDER*sizeof(float));

            /* encode sub-frames */
            iCBSearch(extra_cb_index, 0, extra_gain_index, 0, residual, start_pos
                    + ULP_inst!!.state_short_len, mem, ilbc_constants.CB_MEML
                    - ilbc_constants.stMemLTbl, ilbc_constants.stMemLTbl, diff,
                    ilbc_constants.CB_NSTAGES, weightdenum, start
                    * (ilbc_constants.LPC_FILTERORDER + 1), weightState, 0)

            /* construct decoded vector */
            ilbc_common.iCBConstruct(decresidual, start_pos + ULP_inst!!.state_short_len,
                    extra_cb_index, 0, extra_gain_index, 0, mem, ilbc_constants.CB_MEML
                    - ilbc_constants.stMemLTbl, ilbc_constants.stMemLTbl, diff,
                    ilbc_constants.CB_NSTAGES)
        } else { /* put adaptive part in the beginning */

            /* create reversed vectors for prediction */
            k = 0
            while (k < diff) {
                reverseResidual[k] = residual[(start + 1) * ilbc_constants.SUBL - 1
                        - (k + ULP_inst!!.state_short_len)]
                k++
            }

            /* setup memory */
            meml_gotten = ULP_inst!!.state_short_len
            k = 0
            while (k < meml_gotten) {
                mem[ilbc_constants.CB_MEML - 1 - k] = decresidual[start_pos + k]
                k++
            }
            for (li in 0 until ilbc_constants.CB_MEML - k) mem[li] = 0.0f
            // memset(mem, 0, (ilbc_constants.CB_MEML-k)*sizeof(float));
            for (li in 0 until ilbc_constants.LPC_FILTERORDER) weightState[li] = 0.0f
            // memset(weightState, 0, ilbc_constants.LPC_FILTERORDER*sizeof(float));

            /* encode sub-frames */
            iCBSearch(extra_cb_index, 0, extra_gain_index, 0, reverseResidual, 0, mem,
                    ilbc_constants.CB_MEML - ilbc_constants.stMemLTbl, ilbc_constants.stMemLTbl, diff,
                    ilbc_constants.CB_NSTAGES, weightdenum, (start - 1)
                    * (ilbc_constants.LPC_FILTERORDER + 1), weightState, 0)

            /* construct decoded vector */
            ilbc_common.iCBConstruct(reverseDecresidual, 0, extra_cb_index, 0, extra_gain_index, 0,
                    mem, ilbc_constants.CB_MEML - ilbc_constants.stMemLTbl, ilbc_constants.stMemLTbl,
                    diff, ilbc_constants.CB_NSTAGES)

            /* get decoded residual from reversed vector */
            k = 0
            while (k < diff) {
                decresidual[start_pos - 1 - k] = reverseDecresidual[k]
                k++
            }
        }

        /* counter for predicted sub-frames */
        subcount = 0

        /* forward prediction of sub-frames */
        val Nfor: Int1 = ULP_inst!!.nsub - start - 1
        if (Nfor > 0) {

            /* setup memory */
            for (li in 0 until ilbc_constants.CB_MEML - ilbc_constants.STATE_LEN) mem[li] = 0.0f
            // memset(mem, 0, (ilbc_constants.CB_MEML-ilbc_constants.STATE_LEN)*sizeof(float));
            System.arraycopy(decresidual, (start - 1) * ilbc_constants.SUBL, mem,
                    ilbc_constants.CB_MEML - ilbc_constants.STATE_LEN, ilbc_constants.STATE_LEN)
            // memcpy(mem+ilbc_constants.CB_MEML-ilbc_constants.STATE_LEN,
            // decresidual+(start-1)*ilbc_constants.SUBL,
            // ilbc_constants.STATE_LEN*sizeof(float));
            for (li in 0 until ilbc_constants.LPC_FILTERORDER) weightState[li] = 0.0f
            // memset(weightState, 0, ilbc_constants.LPC_FILTERORDER*sizeof(float));

            /* loop over sub-frames to encode */
            subframe = 0
            while (subframe < Nfor) {


                /* encode sub-frame */
                iCBSearch(cb_index, subcount * ilbc_constants.CB_NSTAGES, gain_index, subcount
                        * ilbc_constants.CB_NSTAGES, residual, (start + 1 + subframe)
                        * ilbc_constants.SUBL, mem, ilbc_constants.CB_MEML
                        - ilbc_constants.memLfTbl[subcount], ilbc_constants.memLfTbl[subcount],
                        ilbc_constants.SUBL, ilbc_constants.CB_NSTAGES, weightdenum,
                        (start + 1 + subframe) * (ilbc_constants.LPC_FILTERORDER + 1), weightState,
                        subcount + 1)

                /* construct decoded vector */
                ilbc_common.iCBConstruct(decresidual, (start + 1 + subframe) * ilbc_constants.SUBL,
                        cb_index, subcount * ilbc_constants.CB_NSTAGES, gain_index, subcount
                        * ilbc_constants.CB_NSTAGES, mem, ilbc_constants.CB_MEML
                        - ilbc_constants.memLfTbl[subcount], ilbc_constants.memLfTbl[subcount],
                        ilbc_constants.SUBL, ilbc_constants.CB_NSTAGES)

                /* update memory */
                System.arraycopy(mem, ilbc_constants.SUBL, mem, 0,
                        ilbc_constants.CB_MEML - ilbc_constants.SUBL)
                // memcpy(mem, mem+ilbc_constants.SUBL,
                // (ilbc_constants.CB_MEML-ilbc_constants.SUBL)*sizeof(float));
                System.arraycopy(decresidual, (start + 1 + subframe) * ilbc_constants.SUBL, mem,
                        ilbc_constants.CB_MEML - ilbc_constants.SUBL, ilbc_constants.SUBL)
                // memcpy(mem+ilbc_constants.CB_MEML-ilbc_constants.SUBL,
                // &decresidual[(start+1+subframe)*ilbc_constants.SUBL],
                // ilbc_constants.SUBL*sizeof(float));
                for (li in 0 until ilbc_constants.LPC_FILTERORDER) weightState[li] = 0.0f
                // memset(weightState, 0, ilbc_constants.LPC_FILTERORDER*sizeof(float));
                subcount++
                subframe++
            }
        }

        /* backward prediction of sub-frames */
        Nback = start - 1
        if (Nback > 0) {

            /* create reverse order vectors */
            n = 0
            while (n < Nback) {
                k = 0
                while (k < ilbc_constants.SUBL) {
                    reverseResidual[n * ilbc_constants.SUBL + k] = residual[((start - 1)
                            * ilbc_constants.SUBL) - 1 - n * ilbc_constants.SUBL - k]
                    reverseDecresidual[n * ilbc_constants.SUBL + k] = decresidual[((start - 1)
                            * ilbc_constants.SUBL) - 1 - n * ilbc_constants.SUBL - k]
                    k++
                }
                n++
            }

            /* setup memory */
            meml_gotten = ilbc_constants.SUBL * (ULP_inst!!.nsub + 1 - start)
            if (meml_gotten > ilbc_constants.CB_MEML) {
                meml_gotten = ilbc_constants.CB_MEML
            }
            k = 0
            while (k < meml_gotten) {
                mem[ilbc_constants.CB_MEML - 1 - k] = decresidual[(start - 1) * ilbc_constants.SUBL
                        + k]
                k++
            }
            for (li in 0 until ilbc_constants.CB_MEML - k) mem[li] = 0.0f
            // memset(mem, 0, (ilbc_constants.CB_MEML-k)*sizeof(float));
            for (li in 0 until ilbc_constants.LPC_FILTERORDER) weightState[li] = 0.0f
            // memset(weightState, 0, ilbc_constants.LPC_FILTERORDER*sizeof(float));

            /* loop over sub-frames to encode */
            subframe = 0
            while (subframe < Nback) {


                /* encode sub-frame */
                iCBSearch(cb_index, subcount * ilbc_constants.CB_NSTAGES, gain_index, subcount
                        * ilbc_constants.CB_NSTAGES, reverseResidual, subframe * ilbc_constants.SUBL,
                        mem, ilbc_constants.CB_MEML - ilbc_constants.memLfTbl[subcount],
                        ilbc_constants.memLfTbl[subcount], ilbc_constants.SUBL,
                        ilbc_constants.CB_NSTAGES, weightdenum, (start - 2 - subframe)
                        * (ilbc_constants.LPC_FILTERORDER + 1), weightState, subcount + 1)

                /* construct decoded vector */
                ilbc_common.iCBConstruct(reverseDecresidual, subframe * ilbc_constants.SUBL,
                        cb_index, subcount * ilbc_constants.CB_NSTAGES, gain_index, subcount
                        * ilbc_constants.CB_NSTAGES, mem, ilbc_constants.CB_MEML
                        - ilbc_constants.memLfTbl[subcount], ilbc_constants.memLfTbl[subcount],
                        ilbc_constants.SUBL, ilbc_constants.CB_NSTAGES)

                /* update memory */
                System.arraycopy(mem, ilbc_constants.SUBL, mem, 0,
                        ilbc_constants.CB_MEML - ilbc_constants.SUBL)
                // memcpy(mem, mem+ilbc_constants.SUBL,
                // (ilbc_constants.CB_MEML-ilbc_constants.SUBL)*sizeof(float));
                System.arraycopy(reverseDecresidual, subframe * ilbc_constants.SUBL, mem,
                        ilbc_constants.CB_MEML - ilbc_constants.SUBL, ilbc_constants.SUBL)
                // memcpy(mem+ilbc_constants.CB_MEML-ilbc_constants.SUBL,
                // &reverseDecresidual[subframe*ilbc_constants.SUBL],
                // ilbc_constants.SUBL*sizeof(float));
                for (li in 0 until ilbc_constants.LPC_FILTERORDER) weightState[li] = 0.0f
                // memset(weightState, 0, ilbc_constants.LPC_FILTERORDER*sizeof(float));
                subcount++
                subframe++
            }

            /* get decoded residual from reversed vector */
            i = 0
            while (i < ilbc_constants.SUBL * Nback) {
                decresidual[ilbc_constants.SUBL * Nback - i - 1] = reverseDecresidual[i]
                i++
            }
        }
        /* end encoding part */

        /* adjust index */index_conv_enc(cb_index)

        /* pack bytes */

        // pbytes=bytes;

        /* loop over the 3 ULP classes */
        ulp = 0
        while (ulp < 3) {


            /* LSF */
            // System.out.println("ULP Class " + ulp);
            k = 0
            while (k < ilbc_constants.LSF_NSPLIT * ULP_inst!!.lpc_n) {

                // System.out.println("LSF " + k);
                pack = bytes.packsplit(lsf_i[k], ULP_inst!!.lsf_bits[k]!![ulp],
                        ULP_inst!!.lsf_bits[k]!![ulp] + ULP_inst!!.lsf_bits[k]!![ulp + 1]
                                + ULP_inst!!.lsf_bits[k]!![ulp + 2])
                firstpart = pack.firstpart
                lsf_i[k] = pack.rest
                bytes.dopack(firstpart, ULP_inst!!.lsf_bits[k]!![ulp])
                k++
            }

            /* Start block info */

            // System.out.println("start bits");
            pack = bytes.packsplit(start, ULP_inst!!.start_bits[ulp],
                    ULP_inst!!.start_bits[ulp] + ULP_inst!!.start_bits[ulp + 1]
                            + ULP_inst!!.start_bits[ulp + 2])
            firstpart = pack.firstpart
            start = pack.rest
            bytes.dopack(firstpart, ULP_inst!!.start_bits[ulp])

            // System.out.println("startfirst bits");
            pack = bytes.packsplit(state_first, ULP_inst!!.startfirst_bits[ulp],
                    ULP_inst!!.startfirst_bits[ulp] + ULP_inst!!.startfirst_bits[ulp + 1]
                            + ULP_inst!!.startfirst_bits[ulp + 2])
            firstpart = pack.firstpart
            state_first = pack.rest
            bytes.dopack(firstpart, ULP_inst!!.startfirst_bits[ulp])

            // System.out.println("scale bits");
            pack = bytes.packsplit(idxForMax[0], ULP_inst!!.scale_bits[ulp],
                    ULP_inst!!.scale_bits[ulp] + ULP_inst!!.scale_bits[ulp + 1]
                            + ULP_inst!!.scale_bits[ulp + 2])
            firstpart = pack.firstpart
            idxForMax[0] = pack.rest
            bytes.dopack(firstpart, ULP_inst!!.scale_bits[ulp])

            // System.out.println("state bits");
            k = 0
            while (k < ULP_inst!!.state_short_len) {

                // System.out.println("state short len #" + k);
                pack = bytes.packsplit(idxVec[k], ULP_inst!!.state_bits[ulp],
                        ULP_inst!!.state_bits[ulp] + ULP_inst!!.state_bits[ulp + 1]
                                + ULP_inst!!.state_bits[ulp + 2])
                firstpart = pack.firstpart
                idxVec[k] = pack.rest
                bytes.dopack(firstpart, ULP_inst!!.state_bits[ulp])
                k++
            }

            /* 23/22 (20ms/30ms) sample block */

            // System.out.println("extra_cb_index");
            k = 0
            while (k < ilbc_constants.CB_NSTAGES) {
                pack = bytes.packsplit(extra_cb_index[k], ULP_inst!!.extra_cb_index[k]!![ulp],
                        ULP_inst!!.extra_cb_index[k]!![ulp] + ULP_inst!!.extra_cb_index[k]!![ulp + 1]
                                + ULP_inst!!.extra_cb_index[k]!![ulp + 2])
                firstpart = pack.firstpart
                extra_cb_index[k] = pack.rest
                bytes.dopack(firstpart, ULP_inst!!.extra_cb_index[k]!![ulp])
                k++
            }

            // System.out.println("extra_cb_gain");
            k = 0
            while (k < ilbc_constants.CB_NSTAGES) {
                pack = bytes.packsplit(extra_gain_index[k], ULP_inst!!.extra_cb_gain[k]!![ulp],
                        ULP_inst!!.extra_cb_gain[k]!![ulp] + ULP_inst!!.extra_cb_gain[k]!![ulp + 1]
                                + ULP_inst!!.extra_cb_gain[k]!![ulp + 2])
                firstpart = pack.firstpart
                extra_gain_index[k] = pack.rest
                // this.ULP_inst.extra_cb_gain[k][ulp] = pack.get_rest();
                bytes.dopack(firstpart, ULP_inst!!.extra_cb_gain[k]!![ulp])
                k++
            }

            /* The two/four (20ms/30ms) 40 sample sub-blocks */

            // System.out.println("cb_index");
            i = 0
            while (i < ULP_inst!!.nasub) {
                k = 0
                while (k < ilbc_constants.CB_NSTAGES) {
                    pack = bytes.packsplit(cb_index[i * ilbc_constants.CB_NSTAGES + k],
                            ULP_inst!!.cb_index[i][k]!![ulp], ULP_inst!!.cb_index[i][k]!![ulp]
                            + ULP_inst!!.cb_index[i][k]!![ulp + 1]
                            + ULP_inst!!.cb_index[i][k]!![ulp + 2])
                    firstpart = pack.firstpart
                    cb_index[i * ilbc_constants.CB_NSTAGES + k] = pack.rest
                    bytes.dopack(firstpart, ULP_inst!!.cb_index[i][k]!![ulp])
                    k++
                }
                i++
            }

            // System.out.println("cb_gain");
            i = 0
            while (i < ULP_inst!!.nasub) {
                k = 0
                while (k < ilbc_constants.CB_NSTAGES) {
                    pack = bytes.packsplit(gain_index[i * ilbc_constants.CB_NSTAGES + k],
                            ULP_inst!!.cb_gain[i][k]!![ulp], ULP_inst!!.cb_gain[i][k]!![ulp]
                            + ULP_inst!!.cb_gain[i][k]!![ulp + 1]
                            + ULP_inst!!.cb_gain[i][k]!![ulp + 2])
                    firstpart = pack.firstpart
                    gain_index[i * ilbc_constants.CB_NSTAGES + k] = pack.rest
                    bytes.dopack(firstpart, ULP_inst!!.cb_gain[i][k]!![ulp])
                    k++
                }
                i++
            }
            ulp++
        }

        /*
		 * set the last bit to zero (otherwise the decoder will treat it as a lost frame)
		 */
        // System.out.println("final bit");
        bytes.dopack(0, 1)
    }
}