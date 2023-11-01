/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

import org.atalk.util.ArrayIOUtils.writeShort
import kotlin.math.sqrt

/**
 * Implements an iLBC decoder.
 *
 * @author Jean Lorchat
 * @author Lyubomir Marinov
 */
internal class ilbc_decoder(init_mode: Int, init_enhancer: Int) {
    var consPLICount: Int
    var prevPLI: Int
    var prevLag: Int
    var last_lag: Int
    var prev_enh_pl: Int
    var per: Float
    var prevResidual: FloatArray
    var seed: Long
    var prevLpc: FloatArray
    var ULP_inst: ilbc_ulp? = null
    var syntMem: FloatArray
    var lsfdeqold: FloatArray
    var old_syntdenum: FloatArray
    var hpomem: FloatArray
    var use_enhancer: Int
    var enh_buf: FloatArray
    var enh_period: FloatArray

    // La plupart des variables globales sont dans ilbc_constants.etc...
    fun syntFilter(Out: FloatArray,  /* (i/o) Signal to be filtered */
            Out_idx: Int, a: FloatArray,  /* (i) LP parameters */
            a_idx: Int, len: Int,  /* (i) Length of signal */
            mem: FloatArray) /* (i/o) Filter state */
    {
        var i: Int
        var j: Int
        // float *po, *pi, *pa, *pm;
        var po: Int
        var pi: Int
        var pa: Int
        var pm: Int

        // System.out.println("out size : " + Out.length);
        // System.out.println("out idx : " + Out_idx);
        // System.out.println("a size : " + a.length);
        // System.out.println("a idx : " + a_idx);
        // System.out.println("len : " + len);
        // System.out.println("mem size : " + mem.length);
        po = Out_idx

        /* Filter first part using memory from past */
        i = 0
        while (i < ilbc_constants.LPC_FILTERORDER) {

            // pi=&Out[i-1];
            // pa=&a[1];
            // pm=&mem[LPC_FILTERORDER-1];
            pi = Out_idx + i - 1
            pa = a_idx + 1
            pm = ilbc_constants.LPC_FILTERORDER - 1
            j = 1
            while (j <= i) {

                // *po-=(*pa++)*(*pi--);
                // System.out.println("1 Soustraction (" + i + "," + j + ") a " + Out[po] + " de " +
                // a[pa] + " * " +
                // Out[pi]);
                // System.out.println("index " + (po - Out_idx) + " <> " + (pi - Out_idx));
                Out[po] -= a[pa] * Out[pi]
                // System.out.println("Pour un resultat de " + Out[po]);
                pa++
                pi--
                j++
            }
            j = i + 1
            while (j < ilbc_constants.LPC_FILTERORDER + 1) {

                // *po-=(*pa++)*(*pm--);
                // System.out.println("2 Soustraction a " + Out[po] + " de " + a[pa] + " * " +
                // mem[pm]);
                Out[po] -= a[pa] * mem[pm]
                // System.out.println("Pour un resultat de " + Out[po]);
                pa++
                pm--
                j++
            }
            po++
            i++
        }

        /*
		 * Filter last part where the state is entirely in the output vector
		 */
        i = ilbc_constants.LPC_FILTERORDER
        while (i < len) {

            // pi=&Out[i-1];
            pi = Out_idx + i - 1
            // pa=&a[1];
            pa = a_idx + 1
            j = 1
            while (j < ilbc_constants.LPC_FILTERORDER + 1) {

                // *po-=(*pa++)*(*pi--);
                // System.out.println("3 Soustraction a " + Out[po] + " de " + a[pa] + " * " +
                // Out[pi]);
                Out[po] -= a[pa] * Out[pi]
                // System.out.println("Pour un resultat de " + Out[po]);
                pa++
                pi--
                j++
            }
            po++
            i++
        }

        /* Update state vector */
        System.arraycopy(Out, Out_idx + len - ilbc_constants.LPC_FILTERORDER, mem, 0,
                ilbc_constants.LPC_FILTERORDER)
        // memcpy(mem, &Out[len-LPC_FILTERORDER],
        // LPC_FILTERORDER*sizeof(float));
    }

    /*---------------------------------------------------------------*
	 *  interpolation of lsf coefficients for the decoder
	 *--------------------------------------------------------------*/
    fun LSFinterpolate2a_dec(a: FloatArray,  /* (o) lpc coefficients for a sub-frame */
            lsf1: FloatArray,  /* (i) first lsf coefficient vector */
            lsf2: FloatArray,  /* (i) second lsf coefficient vector */
            lsf2_idx: Int, coef: Float,  /* (i) interpolation weight */
            length: Int /* (i) length of lsf vectors */
    ) {
        val lsftmp = FloatArray(ilbc_constants.LPC_FILTERORDER)
        ilbc_common.interpolate(lsftmp, lsf1, lsf2, lsf2_idx, coef, length)
        ilbc_common.lsf2a(a, lsftmp)
    }

    /*---------------------------------------------------------------*
	 *  obtain dequantized lsf coefficients from quantization index
	 *--------------------------------------------------------------*/
    fun SimplelsfDEQ(lsfdeq: FloatArray,  /* (o) dequantized lsf coefficients */
            index: IntArray,  /* (i) quantization index */
            lpc_n: Int /* (i) number of LPCs */
    ) {
        var i: Int
        var j: Int
        var pos: Int
        var cb_pos: Int

        /* decode first LSF */
        pos = 0
        cb_pos = 0
        i = 0
        while (i < ilbc_constants.LSF_NSPLIT) {
            j = 0
            while (j < ilbc_constants.dim_lsfCbTbl[i]) {
                lsfdeq[pos + j] = ilbc_constants.lsfCbTbl[cb_pos
                        + (index[i].toLong() * ilbc_constants.dim_lsfCbTbl[i] + j).toInt()]
                j++
            }
            pos += ilbc_constants.dim_lsfCbTbl[i]
            cb_pos += ilbc_constants.size_lsfCbTbl[i] * ilbc_constants.dim_lsfCbTbl[i]
            i++
        }
        if (lpc_n > 1) {

            /* decode last LSF */
            pos = 0
            cb_pos = 0
            i = 0
            while (i < ilbc_constants.LSF_NSPLIT) {
                j = 0
                while (j < ilbc_constants.dim_lsfCbTbl[i]) {
                    lsfdeq[ilbc_constants.LPC_FILTERORDER + pos + j] = ilbc_constants.lsfCbTbl[cb_pos
                            + (index[ilbc_constants.LSF_NSPLIT + i].toLong() * ilbc_constants.dim_lsfCbTbl[i]).toInt() + j]
                    j++
                }
                pos += ilbc_constants.dim_lsfCbTbl[i]
                cb_pos += ilbc_constants.size_lsfCbTbl[i] * ilbc_constants.dim_lsfCbTbl[i]
                i++
            }
        }
    }

    /*----------------------------------------------------------------*
	 *  obtain synthesis and weighting filters form lsf coefficients
	 *---------------------------------------------------------------*/
    fun DecoderInterpolateLSF(syntdenum: FloatArray?,  /* (o) synthesis filter coefficients */
            weightdenum: FloatArray,  /*
							 * (o) weighting denumerator coefficients
							 */
            lsfdeq: FloatArray,  /* (i) dequantized lsf coefficients */
            length: Int) /* (i) length of lsf coefficient vector */
    {
        var i: Int
        var pos: Int
        val lp_length: Int
        val lp = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        val lsfdeq2: Int
        lsfdeq2 = length
        // lsfdeq2 = lsfdeq + length;
        lp_length = length + 1
        if (ULP_inst!!.mode == 30) {
            /* sub-frame 1: Interpolation between old and first */
            LSFinterpolate2a_dec(lp, lsfdeqold, lsfdeq, 0,
                    ilbc_constants.lsf_weightTbl_30ms[0], length)
            System.arraycopy(lp, 0, syntdenum, 0, lp_length)
            // memcpy(syntdenum,lp,lp_length*sizeof(float));
            ilbc_common.bwexpand(weightdenum, 0, lp, ilbc_constants.LPC_CHIRP_WEIGHTDENUM,
                    lp_length)

            /*
			 * sub-frames 2 to 6: interpolation between first and last LSF
			 */
            pos = lp_length
            i = 1
            while (i < 6) {
                LSFinterpolate2a_dec(lp, lsfdeq, lsfdeq, lsfdeq2,
                        ilbc_constants.lsf_weightTbl_30ms[i], length)
                System.arraycopy(lp, 0, syntdenum, pos, lp_length)
                // memcpy(syntdenum + pos,lp,lp_length*sizeof(float));
                ilbc_common.bwexpand(weightdenum, pos, lp, ilbc_constants.LPC_CHIRP_WEIGHTDENUM,
                        lp_length)
                pos += lp_length
                i++
            }
        } else {
            pos = 0
            i = 0
            while (i < ULP_inst!!.nsub) {
                LSFinterpolate2a_dec(lp, lsfdeqold, lsfdeq, 0,
                        ilbc_constants.lsf_weightTbl_20ms[i], length)
                System.arraycopy(lp, 0, syntdenum, pos, lp_length)
                // memcpy(syntdenum+pos,lp,lp_length*sizeof(float));
                ilbc_common.bwexpand(weightdenum, pos, lp, ilbc_constants.LPC_CHIRP_WEIGHTDENUM,
                        lp_length)
                pos += lp_length
                i++
            }
        }

        /* update memory */
        if (ULP_inst!!.mode == 30) {
            System.arraycopy(lsfdeq, lsfdeq2, lsfdeqold, 0, length)
            // memcpy(iLBCdec_inst->lsfdeqold, lsfdeq2, length*sizeof(float));
        } else {
            System.arraycopy(lsfdeq, 0, lsfdeqold, 0, length)
            // memcpy(iLBCdec_inst->lsfdeqold, lsfdeq, length*sizeof(float));
        }
    }

    fun index_conv_dec(index: IntArray) /* (i/o) Codebook indexes */ {
        var k: Int
        k = 1
        while (k < ilbc_constants.CB_NSTAGES) {
            if (index[k] >= 44 && index[k] < 108) {
                index[k] += 64
            } else if (index[k] >= 108 && index[k] < 128) {
                index[k] += 128
            } else {
                /* ERROR */
            }
            k++
        }
    }

    fun hpOutput(In: FloatArray,  /* (i) vector to filter */
            len: Int,  /* (i) length of vector to filter */
            Out: FloatArray,  /* (o) the resulting filtered vector */
            mem: FloatArray) /* (i/o) the filter state */ {
        var i: Int
        // float *pi, *po;
        var pi: Int
        var po: Int

        /* all-zero section */

        // pi = &In[0];
        // po = &Out[0];
        pi = 0
        po = 0
        i = 0
        while (i < len) {
            Out[po] = ilbc_constants.hpo_zero_coefsTbl[0] * In[pi]
            Out[po] += ilbc_constants.hpo_zero_coefsTbl[1] * mem[0]
            Out[po] += ilbc_constants.hpo_zero_coefsTbl[2] * mem[1]
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
            Out[po] -= ilbc_constants.hpo_pole_coefsTbl[1] * mem[2]
            Out[po] -= ilbc_constants.hpo_pole_coefsTbl[2] * mem[3]
            mem[3] = mem[2]
            mem[2] = Out[po]
            po++
            i++
        }
    }

    /*----------------------------------------------------------------*
	 * downsample (LP filter and decimation)
	 *---------------------------------------------------------------*/
    fun DownSample(In: FloatArray,  /* (i) input samples */
            in_idx: Int, Coef: FloatArray?,  /* (i) filter coefficients */
            lengthIn: Int,  /* (i) number of input samples */
            state: FloatArray,  /* (i) filter state */
            Out: FloatArray) /* (o) downsampled output */ {
        var o: Float
        // float *Out_ptr = Out;
        var out_ptr = 0
        // float *Coef_ptr, *In_ptr;
        var coef_ptr = 0
        var in_ptr = in_idx
        // float *state_ptr;
        var state_ptr = 0
        var j: Int
        var stop: Int

        /* LP filter and decimate at the same time */
        var i = ilbc_constants.DELAY_DS
        while (i < lengthIn) {
            coef_ptr = 0
            in_ptr = in_idx + i
            state_ptr = ilbc_constants.FILTERORDER_DS - 2
            o = 0.0f

            // stop = (i < ilbc_constants.FILTERORDER_DS) ? i + 1 : ilbc_constants.FILTERORDER_DS;
            stop = if (i < ilbc_constants.FILTERORDER_DS) {
                i + 1
            } else {
                ilbc_constants.FILTERORDER_DS
            }
            j = 0
            while (j < stop) {
                o += Coef!![coef_ptr] * In[in_ptr]
                coef_ptr++
                in_ptr--
                j++
            }
            j = i + 1
            while (j < ilbc_constants.FILTERORDER_DS) {
                o += Coef!![coef_ptr] * state[state_ptr]
                coef_ptr++
                state_ptr--
                j++
            }
            Out[out_ptr] = o
            out_ptr++
            i += ilbc_constants.FACTOR_DS
        }

        /* Get the last part (use zeros as input for the future) */
        i = lengthIn + ilbc_constants.FACTOR_DS
        while (i < lengthIn + ilbc_constants.DELAY_DS) {
            o = 0.0f
            if (i < lengthIn) {
                coef_ptr = 0
                in_ptr = in_idx + i
                j = 0
                while (j < ilbc_constants.FILTERORDER_DS) {
                    o += Coef!![coef_ptr] * Out[out_ptr]
                    coef_ptr++
                    out_ptr--
                    j++
                }
            } else {
                coef_ptr = i - lengthIn
                in_ptr = in_idx + lengthIn - 1
                j = 0
                while (j < ilbc_constants.FILTERORDER_DS - (i - lengthIn)) {
                    o += Coef!![coef_ptr] * In[in_ptr]
                    coef_ptr++
                    in_ptr--
                    j++
                }
            }
            Out[out_ptr] = o
            out_ptr++
            i += ilbc_constants.FACTOR_DS
        }
    }

    /*----------------------------------------------------------------*
	 * Find index in array such that the array element with said
	 * index is the element of said array closest to "value"
	 * according to the squared-error criterion
	 *---------------------------------------------------------------*/
    fun NearestNeighbor( // int index[], /* (o) index of array element closest
            // to value */
            array: FloatArray?,  /* (i) data array */
            value: Float,  /* (i) value */
            arlength: Int): Int /* (i) dimension of data array */ {
        var i: Int
        var bestcrit: Float
        var crit: Float
        var index: Int
        crit = array!![0] - value
        bestcrit = crit * crit
        index = 0
        i = 1
        while (i < arlength) {
            crit = array[i] - value
            crit *= crit
            if (crit < bestcrit) {
                bestcrit = crit
                index = i
            }
            i++
        }
        return index
    }

    /*----------------------------------------------------------------*
	 * compute cross correlation between sequences
	 *---------------------------------------------------------------*/
    fun mycorr1(corr: FloatArray,  /* (o) correlation of seq1 and seq2 */
            corr_idx: Int, seq1: FloatArray,  /* (i) first sequence */
            seq1_idx: Int, dim1: Int,  /* (i) dimension first seq1 */
            seq2: FloatArray?,  /* (i) second sequence */
            seq2_idx: Int, dim2: Int) /* (i) dimension seq2 */ {
        var j: Int

        // System.out.println("longueur 1 : " + seq1.length);
        // System.out.println("distance 1 : " + seq1_idx);
        // System.out.println("longueur 2 : " + seq2.length);
        // System.out.println("distance 2 : " + seq2_idx);

        // System.out.println("dimensions : " + dim1 + " et " + dim2);

        // BUG in ILBC ???
        var i = 0
        while (i <= dim1 - dim2) {
            if (corr_idx + i < corr.size) corr[corr_idx + i] = 0.0f
            j = 0
            while (j < dim2) {
                corr[corr_idx + i] += seq1[seq1_idx + i + j] * seq2!![seq2_idx + j]
                j++
            }
            i++
        }
    }

    /*----------------------------------------------------------------*
	 * upsample finite array assuming zeros outside bounds
	 *---------------------------------------------------------------*/
    fun enh_upsample(useq1: FloatArray,  /* (o) upsampled output sequence */
            seq1: FloatArray,  /* (i) unupsampled sequence */
            dim1: Int,  /* (i) dimension seq1 */
            hfl: Int) /* (i) polyphase filter length=2*hfl+1 */ {
        // float *pu,*ps;
        var hfl = hfl
        var pu: Int
        var ps: Int
        var i: Int
        var j: Int
        var k: Int
        var q: Int
        var filterlength: Int
        val hfl2: Int
        val polyp = IntArray(ilbc_constants.ENH_UPS0)
        /*
         * pointers to polyphase columns
         */
        // const float *pp;
        var pp: Int

        /* define pointers for filter */
        filterlength = 2 * hfl + 1
        if (filterlength > dim1) {
            hfl2 = dim1 / 2
            j = 0
            while (j < ilbc_constants.ENH_UPS0) {
                polyp[j] = j * filterlength + hfl - hfl2
                j++
            }
            hfl = hfl2
            filterlength = 2 * hfl + 1
        } else {
            j = 0
            while (j < ilbc_constants.ENH_UPS0) {
                polyp[j] = j * filterlength
                j++
            }
        }

        /* filtering: filter overhangs left side of sequence */
        // pu=useq1;
        pu = 0
        i = hfl
        while (i < filterlength) {
            j = 0
            while (j < ilbc_constants.ENH_UPS0) {

                // *pu=0.0f;
                useq1[pu] = 0.0f
                // pp = polyp[j];
                pp = polyp[j]
                // ps = seq1+i;
                ps = i
                k = 0
                while (k <= i) {
                    useq1[pu] += seq1[ps] * ilbc_constants.polyphaserTbl[pp]
                    ps--
                    pp++
                    k++
                }
                pu++
                j++
            }
            i++
        }

        /* filtering: simple convolution=inner products */
        i = filterlength
        while (i < dim1) {
            j = 0
            while (j < ilbc_constants.ENH_UPS0) {

                // *pu=0.0f;
                useq1[pu] = 0.0f
                // pp = polyp[j];
                pp = polyp[j]
                // ps = seq1+i;
                ps = i
                k = 0
                while (k < filterlength) {

                    // *pu += *ps-- * *pp++;
                    useq1[pu] += seq1[ps] * ilbc_constants.polyphaserTbl[pp]
                    ps--
                    pp++
                    k++
                }
                pu++
                j++
            }
            i++
        }

        /* filtering: filter overhangs right side of sequence */
        q = 1
        while (q <= hfl) {
            j = 0
            while (j < ilbc_constants.ENH_UPS0) {

                // *pu=0.0f;
                useq1[pu] = 0.0f
                // pp = polyp[j]+q;
                pp = polyp[j] + q
                // ps = seq1+dim1-1;
                ps = dim1 - 1
                k = 0
                while (k < filterlength - q) {
                    useq1[pu] += seq1[ps] * ilbc_constants.polyphaserTbl[pp]
                    ps--
                    pp++
                    k++
                }
                pu++
                j++
            }
            q++
        }
    }

    /**
     * @param period
     * Currently not used
     */
    /*----------------------------------------------------------------*
	 * find segment starting near idata+estSegPos that has highest
	 * correlation with idata+centerStartPos through
	 * idata+centerStartPos+ENH_BLOCKL-1 segment is found at a
	 * resolution of ENH_UPSO times the original of the original
	 * sampling rate
	 *---------------------------------------------------------------*/
    fun refiner(seg: FloatArray,  /* (o) segment array */
            seg_idx: Int, idata: FloatArray,  /* (i) original data buffer */
            idatal: Int,  /* (i) dimension of idata */
            centerStartPos: Int,  /* (i) beginning center segment */
            estSegPos: Float,  /* (i) estimated beginning other segment */
            period: Float): Float /* (i) estimated pitch period */ {
        val estSegPosRounded: Int
        var searchSegStartPos: Int
        var searchSegEndPos: Int
        val corrdim: Int
        var tloc: Int
        var tloc2: Int
        var i: Int
        val st: Int
        val en: Int
        val fraction: Int
        val vect = FloatArray(ilbc_constants.ENH_VECTL)
        val corrVec = FloatArray(ilbc_constants.ENH_CORRDIM)
        var maxv: Float
        val corrVecUps = FloatArray(ilbc_constants.ENH_CORRDIM * ilbc_constants.ENH_UPS0)
        var updStartPos = 0.0f

        /* defining array bounds */
        estSegPosRounded = (estSegPos - 0.5).toInt()
        searchSegStartPos = estSegPosRounded - ilbc_constants.ENH_SLOP
        if (searchSegStartPos < 0) {
            searchSegStartPos = 0
        }
        searchSegEndPos = estSegPosRounded + ilbc_constants.ENH_SLOP
        if (searchSegEndPos + ilbc_constants.ENH_BLOCKL >= idatal) {
            searchSegEndPos = idatal - ilbc_constants.ENH_BLOCKL - 1
        }
        corrdim = searchSegEndPos - searchSegStartPos + 1

        /*
		 * compute upsampled correlation (corr33) and find location of max
		 */
        // System.out.println("appel 1");
        mycorr1(corrVec, 0, idata, searchSegStartPos, corrdim + ilbc_constants.ENH_BLOCKL - 1,
                idata, centerStartPos, ilbc_constants.ENH_BLOCKL)
        enh_upsample(corrVecUps, corrVec, corrdim, ilbc_constants.ENH_FL0)
        tloc = 0
        maxv = corrVecUps[0]
        i = 1
        while (i < ilbc_constants.ENH_UPS0 * corrdim) {
            if (corrVecUps[i] > maxv) {
                tloc = i
                maxv = corrVecUps[i]
            }
            i++
        }

        /*
		 * make vector can be upsampled without ever running outside bounds
		 */
        updStartPos = searchSegStartPos + tloc.toFloat() / ilbc_constants.ENH_UPS0.toFloat() + 1.0f
        tloc2 = tloc / ilbc_constants.ENH_UPS0
        if (tloc > tloc2 * ilbc_constants.ENH_UPS0) {
            tloc2++
        }
        st = searchSegStartPos + tloc2 - ilbc_constants.ENH_FL0
        if (st < 0) {
            for (li in 0 until -st) vect[li] = 0.0f
            // memset(vect,0,-st*sizeof(float));
            System.arraycopy(idata, 0, vect, -st, ilbc_constants.ENH_VECTL + st)
            // memcpy(&vect[-st],idata, (ilbc_constants.ENH_VECTL+st)*sizeof(float));
        } else {
            en = st + ilbc_constants.ENH_VECTL
            if (en > idatal) {
                System.arraycopy(idata, st, vect, 0, ilbc_constants.ENH_VECTL - (en - idatal))
                // memcpy(vect, &idata[st],
                // (ilbc_constants.ENH_VECTL-(en-idatal))*sizeof(float));
                for (li in 0 until en - idatal) vect[ilbc_constants.ENH_VECTL - (en - idatal) + li] = 0.0f
                // memset(&vect[ilbc_constants.ENH_VECTL-(en-idatal)], 0,
                // (en-idatal)*sizeof(float));
            } else {
                System.arraycopy(idata, st, vect, 0, ilbc_constants.ENH_VECTL)
                // memcpy(vect, &idata[st], ilbc_constants.ENH_VECTL*sizeof(float));
            }
        }
        fraction = tloc2 * ilbc_constants.ENH_UPS0 - tloc

        /* compute the segment (this is actually a convolution) */

        // System.out.println("appel 2");
        // System.out.println("longueur 1 : " + vect.length);
        // System.out.println("distance 1 : " + 0);
        // System.out.println("longueur 2 : " + ilbc_constants.polyphaserTbl.length);
        // System.out.println("distance 2 : " + (2*ilbc_constants.ENH_FL0+1)*fraction);
        // System.out.println("dimension 1 : " + ilbc_constants.ENH_VECTL);
        // System.out.println("dimension 2 : " + (2 * ilbc_constants.ENH_FL0+1));
        // System.out.println("correlations de dimension " + seg.length);
        mycorr1(seg, seg_idx, vect, 0, ilbc_constants.ENH_VECTL, ilbc_constants.polyphaserTbl,
                (2 * ilbc_constants.ENH_FL0 + 1) * fraction, 2 * ilbc_constants.ENH_FL0 + 1)
        return updStartPos
    }

    /*----------------------------------------------------------------*
	 * find the smoothed output data
	 *---------------------------------------------------------------*/
    fun smath(odata: FloatArray,  /* (o) smoothed output */
            odata_idx: Int, sseq: FloatArray,  /* (i) said second sequence of waveforms */
            hl: Int,  /* (i) 2*hl+1 is sseq dimension */
            alpha0: Float) /* (i) max smoothing energy fraction */ {
        var i: Int
        var k: Int
        var w00: Float
        var w10: Float
        var w11: Float
        val A: Float
        var B: Float
        val C: Float
        var err: Float
        var errs: Float
        val surround = FloatArray(ilbc_constants.BLOCKL_MAX) /*
																 * shape contributed by other than
																 * current
																 */
        val wt = FloatArray(2 * ilbc_constants.ENH_HL + 1) /*
																 * waveform weighting to get
																 * surround shape
																 */
        val denom: Float
        var psseq: Int

        /*
		 * create shape of contribution from all waveforms except the current one
		 */
        i = 1
        while (i <= 2 * hl + 1) {
            wt[i - 1] = 0.5.toFloat() * (1 - Math.cos((2 * ilbc_constants.PI * i / (2 * hl + 2)).toDouble()).toFloat())
            i++
        }
        wt[hl] = 0.0f /* for clarity, not used */
        i = 0
        while (i < ilbc_constants.ENH_BLOCKL) {
            surround[i] = sseq[i] * wt[0]
            i++
        }
        k = 1
        while (k < hl) {
            psseq = k * ilbc_constants.ENH_BLOCKL
            i = 0
            while (i < ilbc_constants.ENH_BLOCKL) {
                surround[i] += sseq[psseq + i] * wt[k]
                i++
            }
            k++
        }
        k = hl + 1
        while (k <= 2 * hl) {
            psseq = k * ilbc_constants.ENH_BLOCKL
            i = 0
            while (i < ilbc_constants.ENH_BLOCKL) {
                surround[i] += sseq[psseq + i] * wt[k]
                i++
            }
            k++
        }

        /* compute some inner products */
        w11 = 0.0f
        w10 = w11
        w00 = w10
        psseq = hl * ilbc_constants.ENH_BLOCKL /* current block */
        i = 0
        while (i < ilbc_constants.ENH_BLOCKL) {
            w00 += sseq[psseq + i] * sseq[psseq + i]
            w11 += surround[i] * surround[i]
            w10 += surround[i] * sseq[psseq + i]
            i++
        }
        if (Math.abs(w11) < 1.0f) {
            w11 = 1.0f
        }
        C = Math.sqrt((w00 / w11).toDouble()).toFloat()

        /* first try enhancement without power-constraint */
        errs = 0.0f
        psseq = hl * ilbc_constants.ENH_BLOCKL
        i = 0
        while (i < ilbc_constants.ENH_BLOCKL) {
            odata[odata_idx + i] = C * surround[i]
            err = sseq[psseq + i] - odata[odata_idx + i]
            errs += err * err
            i++
        }

        /* if constraint violated by first try, add constraint */
        if (errs > alpha0 * w00) {
            if (w00 < 1) {
                w00 = 1f
            }
            denom = (w11 * w00 - w10 * w10) / (w00 * w00)
            if (denom > 0.0001f) {
                /*
                 * eliminates numerical problems for if smooth
                 */
                A = Math.sqrt(((alpha0 - alpha0 * alpha0 / 4) / denom).toDouble()).toFloat()
                B = -alpha0 / 2 - A * w10 / w00
                B = B + 1
            } else { /*
					 * essentially no difference between cycles; smoothing not needed
					 */
                A = 0.0f
                B = 1.0f
            }

            /* create smoothed sequence */
            psseq = hl * ilbc_constants.ENH_BLOCKL
            i = 0
            while (i < ilbc_constants.ENH_BLOCKL) {
                odata[odata_idx + i] = A * surround[i] + B * sseq[psseq + i]
                i++
            }
        }
    }

    /*----------------------------------------------------------------*
	 * get the pitch-synchronous sample sequence
	 *---------------------------------------------------------------*/
    fun getsseq(sseq: FloatArray,  /* (o) the pitch-synchronous sequence */
            idata: FloatArray,  /* (i) original data */
            idatal: Int,  /* (i) dimension of data */
            centerStartPos: Int,  /* (i) where current block starts */
            period: FloatArray,  /* (i) rough-pitch-period array */
            plocs: FloatArray?,  /*
						 * (i) where periods of period array are taken
						 */
            periodl: Int,  /* (i) dimension period array */
            hl: Int) /* (i) 2*hl+1 is the number of sequences */ {
        var i: Int
        val centerEndPos: Int
        var q: Int
        val blockStartPos = FloatArray(2 * ilbc_constants.ENH_HL + 1)
        val lagBlock = IntArray(2 * ilbc_constants.ENH_HL + 1)
        val plocs2 = FloatArray(ilbc_constants.ENH_PLOCSL)
        // float *psseq;
        var psseq: Int
        centerEndPos = centerStartPos + ilbc_constants.ENH_BLOCKL - 1

        /* present */
        lagBlock[hl] = NearestNeighbor(plocs, 0.5.toFloat() * (centerStartPos + centerEndPos),
                periodl)
        blockStartPos[hl] = centerStartPos.toFloat()
        psseq = ilbc_constants.ENH_BLOCKL * hl
        // psseq=sseq+ENH_BLOCKL*hl;
        System.arraycopy(idata, centerStartPos, sseq, psseq, ilbc_constants.ENH_BLOCKL)
        // memcpy(psseq, idata+centerStartPos, ENH_BLOCKL*sizeof(float));

        /* past */
        q = hl - 1
        while (q >= 0) {
            blockStartPos[q] = blockStartPos[q + 1] - period[lagBlock[q + 1]]
            lagBlock[q] = NearestNeighbor(plocs, blockStartPos[q] + ilbc_constants.ENH_BLOCKL_HALF
                    - period[lagBlock[q + 1]], periodl)
            if (blockStartPos[q] - ilbc_constants.ENH_OVERHANG >= 0) {
                blockStartPos[q] = refiner(sseq, q * ilbc_constants.ENH_BLOCKL, idata, idatal,
                        centerStartPos, blockStartPos[q], period[lagBlock[q + 1]])
            } else {
                psseq = q * ilbc_constants.ENH_BLOCKL
                // psseq=sseq+q*ENH_BLOCKL;
                for (li in 0 until ilbc_constants.ENH_BLOCKL) sseq[psseq + li] = 0.0f
                // memset(psseq, 0, ENH_BLOCKL*sizeof(float));
            }
            q--
        }

        /* future */
        i = 0
        while (i < periodl) {
            plocs2[i] = plocs!![i] - period[i]
            i++
        }
        q = hl + 1
        while (q <= 2 * hl) {
            lagBlock[q] = NearestNeighbor(plocs2, blockStartPos[q - 1]
                    + ilbc_constants.ENH_BLOCKL_HALF, periodl)
            blockStartPos[q] = blockStartPos[q - 1] + period[lagBlock[q]]
            if (blockStartPos[q] + ilbc_constants.ENH_BLOCKL + ilbc_constants.ENH_OVERHANG < idatal) {
                blockStartPos[q] = refiner(sseq, q * ilbc_constants.ENH_BLOCKL, idata, idatal,
                        centerStartPos, blockStartPos[q], period[lagBlock[q]])
            } else {
                psseq = q * ilbc_constants.ENH_BLOCKL
                // psseq=sseq+q*ENH_BLOCKL;
                for (li in 0 until ilbc_constants.ENH_BLOCKL) sseq[psseq + li] = 0.0f
                // memset(psseq, 0, ENH_BLOCKL*sizeof(float));
            }
            q++
        }
    }

    /*----------------------------------------------------------------*
	 * perform enhancement on idata+centerStartPos through
	 * idata+centerStartPos+ENH_BLOCKL-1
	 *---------------------------------------------------------------*/
    fun enhancer(odata: FloatArray,  /* (o) smoothed block, dimension blockl */
            odata_idx: Int, idata: FloatArray,  /* (i) data buffer used for enhancing */
            idatal: Int,  /* (i) dimension idata */
            centerStartPos: Int,  /*
							 * (i) first sample current block within idata
							 */
            alpha0: Float,  /*
					 * (i) max correction-energy-fraction (in [0,1])
					 */
            period: FloatArray,  /* (i) pitch period array */
            plocs: FloatArray?,  /*
						 * (i) locations where period array values valid
						 */
            periodl: Int /* (i) dimension of period and plocs */
    ) {
        val sseq = FloatArray((2 * ilbc_constants.ENH_HL + 1) * ilbc_constants.ENH_BLOCKL)

        /* get said second sequence of segments */
        getsseq(sseq, idata, idatal, centerStartPos, period, plocs, periodl, ilbc_constants.ENH_HL)

        /* compute the smoothed output from said second sequence */
        smath(odata, odata_idx, sseq, ilbc_constants.ENH_HL, alpha0)
    }

    /*----------------------------------------------------------------*
	 * cross correlation
	 *---------------------------------------------------------------*/
    fun xCorrCoef(target: FloatArray,  /* (i) first array */
            t_idx: Int, regressor: FloatArray,  /* (i) second array */
            r_idx: Int, subl: Int): Float /* (i) dimension arrays */ {
        var ftmp1 = 0.0f
        var ftmp2 = 0.0f
        var i: Int = 0
        while (i < subl) {
            ftmp1 += target[t_idx + i] * regressor[r_idx + i]
            ftmp2 += regressor[r_idx + i] * regressor[r_idx + i]
            i++
        }
        return if (ftmp1 > 0.0f) {
            ftmp1 * ftmp1 / ftmp2
        } else {
            0.0f
        }
    }

    /*----------------------------------------------------------------*
	 * interface for enhancer
	 *---------------------------------------------------------------*/
    fun enhancerInterface(out: FloatArray,  /* (o) enhanced signal */
            `in`: FloatArray): Int /* (i) unenhanced signal */ {
        // float *enh_buf, *enh_period; (definis en global pour la classe)
        var iblock: Int
        var isample: Int
        var lag = 0
        var ilag: Int
        var i: Int
        var ioffset: Int
        var cc: Float
        var maxcc: Float
        var ftmp1: Float
        var ftmp2: Float
        // float *inPtr, *enh_bufPtr1, *enh_bufPtr2;
        var inPtr: Int
        var enh_bufPtr1: Int
        var enh_bufPtr2: Int
        val plc_pred = FloatArray(ilbc_constants.ENH_BLOCKL)
        val lpState = FloatArray(6)
        val downsampled = FloatArray((ilbc_constants.ENH_NBLOCKS * ilbc_constants.ENH_BLOCKL + 120) / 2)
        val inLen = ilbc_constants.ENH_NBLOCKS * ilbc_constants.ENH_BLOCKL + 120
        val start: Int
        val inlag: Int

        // enh_buf=iLBCdec_inst->enh_buf;
        // enh_period=iLBCdec_inst->enh_period;
        System.arraycopy(enh_buf, ULP_inst!!.blockl, enh_buf, 0, ilbc_constants.ENH_BUFL
                - ULP_inst!!.blockl)
        // memmove(enh_buf, &enh_buf[iLBCdec_inst->blockl],
        // (ENH_BUFL-iLBCdec_inst->blockl)*sizeof(float));
        System.arraycopy(`in`, 0, enh_buf, ilbc_constants.ENH_BUFL - ULP_inst!!.blockl,
                ULP_inst!!.blockl)
        // memcpy(&enh_buf[ENH_BUFL-this.ULP_inst.blockl], in,
        // this.ULP_inst.blockl*sizeof(float));
        val plc_blockl: Int = if (ULP_inst!!.mode == 30) ilbc_constants.ENH_BLOCKL else 40

        /* when 20 ms frame, move processing one block */
        ioffset = 0
        if (ULP_inst!!.mode == 20) ioffset = 1
        i = 3 - ioffset
        System.arraycopy(enh_period, i, enh_period, 0, ilbc_constants.ENH_NBLOCKS_TOT - i)
        // memmove(enh_period, &enh_period[i],
        // (ENH_NBLOCKS_TOT-i)*sizeof(float));

        /*
		 * Set state information to the 6 samples right before the samples to be downsampled.
		 */
        System.arraycopy(enh_buf, (ilbc_constants.ENH_NBLOCKS_EXTRA + ioffset)
                * ilbc_constants.ENH_BLOCKL - 126, lpState, 0, 6)
        // memcpy(lpState,
        // enh_buf+(ENH_NBLOCKS_EXTRA+ioffset)*ENH_BLOCKL-126,
        // 6*sizeof(float));

        /* Down sample a factor 2 to save computations */
        DownSample(enh_buf, (ilbc_constants.ENH_NBLOCKS_EXTRA + ioffset)
                * ilbc_constants.ENH_BLOCKL - 120, ilbc_constants.lpFilt_coefsTbl, inLen - ioffset
                * ilbc_constants.ENH_BLOCKL, lpState, downsampled)

        /* Estimate the pitch in the down sampled domain. */
        iblock = 0
        while (iblock < ilbc_constants.ENH_NBLOCKS - ioffset) {
            lag = 10
            maxcc = xCorrCoef(downsampled, 60 + iblock * ilbc_constants.ENH_BLOCKL_HALF,
                    downsampled, 60 + iblock * ilbc_constants.ENH_BLOCKL_HALF - lag,
                    ilbc_constants.ENH_BLOCKL_HALF)
            ilag = 11
            while (ilag < 60) {
                cc = xCorrCoef(downsampled, 60 + iblock * ilbc_constants.ENH_BLOCKL_HALF,
                        downsampled, 60 + iblock * ilbc_constants.ENH_BLOCKL_HALF - ilag,
                        ilbc_constants.ENH_BLOCKL_HALF)
                if (cc > maxcc) {
                    maxcc = cc
                    lag = ilag
                }
                ilag++
            }

            /* Store the estimated lag in the non-downsampled domain */
            enh_period[iblock + ilbc_constants.ENH_NBLOCKS_EXTRA + ioffset] = lag.toFloat() * 2
            iblock++
        }

        /* PLC was performed on the previous packet */
        if (prev_enh_pl == 1) {
            inlag = enh_period[ilbc_constants.ENH_NBLOCKS_EXTRA + ioffset].toInt()
            lag = inlag - 1
            maxcc = xCorrCoef(`in`, 0, `in`, lag, plc_blockl)
            ilag = inlag
            while (ilag <= inlag + 1) {
                cc = xCorrCoef(`in`, 0, `in`, ilag, plc_blockl)
                if (cc > maxcc) {
                    maxcc = cc
                    lag = ilag
                }
                ilag++
            }
            enh_period[ilbc_constants.ENH_NBLOCKS_EXTRA + ioffset - 1] = lag.toFloat()

            /*
			 * compute new concealed residual for the old lookahead, mix the forward PLC with a
			 * backward PLC from the new frame
			 */

            // inPtr=&in[lag-1];
            inPtr = lag - 1

            // enh_bufPtr1=&plc_pred[plc_blockl-1];
            enh_bufPtr1 = plc_blockl - 1
            start = if (lag > plc_blockl) {
                plc_blockl
            } else {
                lag
            }
            isample = start
            while (isample > 0) {

                // *enh_bufPtr1-- = *inPtr--;
                plc_pred[enh_bufPtr1] = `in`[inPtr]
                enh_bufPtr1--
                inPtr--
                isample--
            }

            // enh_bufPtr2=&enh_buf[ENH_BUFL-1-this.ULP_inst.blockl];
            enh_bufPtr2 = ilbc_constants.ENH_BUFL - 1 - ULP_inst!!.blockl
            isample = plc_blockl - 1 - lag
            while (isample >= 0) {

                // *enh_bufPtr1-- = *enh_bufPtr2--;
                plc_pred[enh_bufPtr1] = enh_buf[enh_bufPtr2]
                enh_bufPtr1--
                enh_bufPtr2--
                isample--
            }

            /* limit energy change */
            ftmp2 = 0.0f
            ftmp1 = 0.0f
            i = 0
            while (i < plc_blockl) {
                ftmp2 += (enh_buf[ilbc_constants.ENH_BUFL - 1 - ULP_inst!!.blockl - i]
                        * enh_buf[ilbc_constants.ENH_BUFL - 1 - ULP_inst!!.blockl - i])
                ftmp1 += plc_pred[i] * plc_pred[i]
                i++
            }
            ftmp1 = Math.sqrt((ftmp1 / plc_blockl).toDouble()).toFloat()
            ftmp2 = Math.sqrt((ftmp2 / plc_blockl).toDouble()).toFloat()
            if (ftmp1 > 2.0f * ftmp2 && ftmp1 > 0.0) {
                i = 0
                while (i < plc_blockl - 10) {
                    plc_pred[i] *= 2.0f * ftmp2 / ftmp1
                    i++
                }
                i = plc_blockl - 10
                while (i < plc_blockl) {
                    plc_pred[i] *= (i - plc_blockl + 10) * (1.0f - 2.0.toFloat() * ftmp2 / ftmp1) / 10 + 2.0f * ftmp2 / ftmp1
                    i++
                }
            }
            enh_bufPtr1 = ilbc_constants.ENH_BUFL - 1 - ULP_inst!!.blockl
            // enh_bufPtr1=&enh_buf[ilbc_constants.ENH_BUFL-1-this.ULP_inst.blockl];
            i = 0
            while (i < plc_blockl) {
                ftmp1 = (i + 1).toFloat() / (plc_blockl + 1).toFloat()
                enh_buf[enh_bufPtr1] *= ftmp1
                // *enh_bufPtr1 *= ftmp1;
                enh_buf[enh_bufPtr1] += (1.0f - ftmp1) * plc_pred[plc_blockl - 1 - i]
                // *enh_bufPtr1 += ((float)1.0f-ftmp1)*
                // plc_pred[plc_blockl-1-i];
                enh_bufPtr1--
                i++
            }
        }
        if (ULP_inst!!.mode == 20) {
            /* Enhancer with 40 samples delay */
            iblock = 0
            while (iblock < 2) {
                enhancer(out, iblock * ilbc_constants.ENH_BLOCKL, enh_buf, ilbc_constants.ENH_BUFL,
                        (5 + iblock) * ilbc_constants.ENH_BLOCKL + 40, ilbc_constants.ENH_ALPHA0,
                        enh_period, ilbc_constants.enh_plocsTbl, ilbc_constants.ENH_NBLOCKS_TOT)
                iblock++
            }
        } else if (ULP_inst!!.mode == 30) {
            /* Enhancer with 80 samples delay */
            iblock = 0
            while (iblock < 3) {
                enhancer(out, iblock * ilbc_constants.ENH_BLOCKL, enh_buf, ilbc_constants.ENH_BUFL,
                        (4 + iblock) * ilbc_constants.ENH_BLOCKL, ilbc_constants.ENH_ALPHA0,
                        enh_period, ilbc_constants.enh_plocsTbl, ilbc_constants.ENH_NBLOCKS_TOT)
                iblock++
            }
        }
        return lag * 2
    }

    /*----------------------------------------------------------------*
	 *  Packet loss concealment routine. Conceals a residual signal
	 *  and LP parameters. If no packet loss, update state.
	 *---------------------------------------------------------------*/
    /*----------------------------------------------------------------*
	 *  Compute cross correlation and pitch gain for pitch prediction
	 *  of last subframe at given lag.
	 *---------------------------------------------------------------*/
    fun compCorr(cc: FloatArray,  /* (o) cross correlation coefficient */
            gc: FloatArray,  /* (o) gain */
            pm: FloatArray, buffer: FloatArray,  /* (i) signal buffer */
            lag: Int,  /* (i) pitch lag */
            bLen: Int,  /* (i) length of buffer */
            sRange: Int) /* (i) correlation search length */ {
        var sRange = sRange
        var i: Int
        var ftmp1: Float
        var ftmp2: Float
        var ftmp3: Float

        /* Guard against getting outside buffer */
        if (bLen - sRange - lag < 0) {
            sRange = bLen - lag
        }
        ftmp1 = 0.0f
        ftmp2 = 0.0f
        ftmp3 = 0.0f
        i = 0
        while (i < sRange) {
            ftmp1 += buffer[bLen - sRange + i] * buffer[bLen - sRange + i - lag]
            ftmp2 += buffer[bLen - sRange + i - lag] * buffer[bLen - sRange + i - lag]
            ftmp3 += buffer[bLen - sRange + i] * buffer[bLen - sRange + i]
            i++
        }
        if (ftmp2 > 0.0f) {
            cc[0] = ftmp1 * ftmp1 / ftmp2
            gc[0] = Math.abs(ftmp1 / ftmp2)
            pm[0] = Math.abs(ftmp1) / (Math.sqrt(ftmp2.toDouble()).toFloat() * Math.sqrt(ftmp3.toDouble()).toFloat())
        } else {
            cc[0] = 0.0f
            gc[0] = 0.0f
            pm[0] = 0.0f
        }
    }

    fun doThePLC(PLCresidual: FloatArray,  /* (o) concealed residual */
            PLClpc: FloatArray?,  /* (o) concealed LP parameters */
            PLI: Int,  /*
				 * (i) packet loss indicator 0 - no PL, 1 = PL
				 */
            decresidual: FloatArray?,  /* (i) decoded residual */
            lpc: FloatArray?,  /* (i) decoded LPC (only used for no PL) */
            lpc_idx: Int, inlag: Int) /* (i) pitch lag */ {
        var lag = 20
        var randlag = 0
        var gain = 0.0f
        var maxcc = 0.0f
        var use_gain = 0.0f
        var gain_comp = 0.0f
        var maxcc_comp = 0.0f
        var per = 0.0f
        var max_per = 0.0f
        var i: Int
        var pick: Int
        var use_lag: Int
        val ftmp: Float
        val randvec: FloatArray
        val pitchfact: Float
        var energy: Float
        val a_gain: FloatArray
        val a_comp: FloatArray
        val a_per: FloatArray
        randvec = FloatArray(ilbc_constants.BLOCKL_MAX)
        a_gain = FloatArray(1)
        a_comp = FloatArray(1)
        a_per = FloatArray(1)

        /* Packet Loss */
        if (PLI == 1) {
            consPLICount += 1

            /*
			 * if previous frame not lost, determine pitch pred. gain
			 */
            if (prevPLI != 1) {

                /*
				 * Search around the previous lag to find the best pitch period
				 */
                lag = inlag - 3
                a_comp[0] = maxcc
                a_gain[0] = gain
                a_per[0] = max_per
                compCorr(a_comp, a_gain, a_per, prevResidual, lag, ULP_inst!!.blockl, 60)
                maxcc = a_comp[0]
                gain = a_gain[0]
                max_per = a_per[0]
                i = inlag - 2
                while (i <= inlag + 3) {
                    a_comp[0] = maxcc_comp
                    a_gain[0] = gain_comp
                    a_per[0] = per
                    compCorr(a_comp, a_gain, a_per, prevResidual, i, ULP_inst!!.blockl, 60)
                    maxcc_comp = a_comp[0]
                    gain_comp = a_gain[0]
                    per = a_per[0]
                    if (maxcc_comp > maxcc) {
                        maxcc = maxcc_comp
                        gain = gain_comp
                        lag = i
                        max_per = per
                    }
                    i++
                }
            } else {
                lag = prevLag
                max_per = this.per
            }

            /* downscaling */
            use_gain = 1.0f
            if (consPLICount * ULP_inst!!.blockl > 320) use_gain = 0.9f else if (consPLICount * ULP_inst!!.blockl > 2 * 320) use_gain = 0.7f else if (consPLICount * ULP_inst!!.blockl > 3 * 320) use_gain = 0.5f else if (consPLICount * ULP_inst!!.blockl > 4 * 320) use_gain = 0.0f

            /* mix noise and pitch repeatition */
            ftmp = Math.sqrt(max_per.toDouble()).toFloat()
            pitchfact = if (ftmp > 0.7f) 1.0f else if (ftmp > 0.4.toFloat()) (ftmp - 0.4.toFloat()) / (0.7.toFloat() - 0.4.toFloat()) else 0.0f

            /* avoid repetition of same pitch cycle */
            use_lag = lag
            if (lag < 80) {
                use_lag = 2 * lag
            }

            /* compute concealed residual */
            energy = 0.0f
            i = 0
            while (i < ULP_inst!!.blockl) {


                /* noise component */
                seed = seed * 69069 + 1 and (-0x80000000 - 1).toLong()
                randlag = 50 + (seed % 70).toInt()
                pick = i - randlag
                if (pick < 0) {
                    randvec[i] = prevResidual[ULP_inst!!.blockl + pick]
                } else {
                    randvec[i] = randvec[pick]
                }

                /* pitch repeatition component */
                pick = i - use_lag
                if (pick < 0) {
                    PLCresidual[i] = prevResidual[ULP_inst!!.blockl + pick]
                } else {
                    PLCresidual[i] = PLCresidual[pick]
                }

                /* mix random and periodicity component */
                if (i < 80) PLCresidual[i] = (use_gain
                        * (pitchfact * PLCresidual[i] + (1.0f - pitchfact) * randvec[i])) else if (i < 160) PLCresidual[i] = (0.95.toFloat() * use_gain
                        * (pitchfact * PLCresidual[i] + (1.0f - pitchfact) * randvec[i])) else PLCresidual[i] = (0.9.toFloat() * use_gain
                        * (pitchfact * PLCresidual[i] + (1.0f - pitchfact) * randvec[i]))
                energy += PLCresidual[i] * PLCresidual[i]
                i++
            }

            /* less than 30 dB, use only noise */
            if (sqrt((energy / ULP_inst!!.blockl).toDouble()).toFloat() < 30.0f) {
                gain = 0.0f
                i = 0
                while (i < ULP_inst!!.blockl) {
                    PLCresidual[i] = randvec[i]
                    i++
                }
            }

            /* use old LPC */

            // memcpy(PLClpc,this.prevLpc, (LPC_FILTERORDER+1)*sizeof(float));
            System.arraycopy(prevLpc, 0, PLClpc, 0, ilbc_constants.LPC_FILTERORDER + 1)
        } else {
            // memcpy(PLCresidual, decresidual,this.ULP_inst.blockl*sizeof(float));
            System.arraycopy(decresidual, 0, PLCresidual, 0, ULP_inst!!.blockl)
            // memcpy(PLClpc, lpc, (LPC_FILTERORDER+1)*sizeof(float));
            System.arraycopy(lpc, lpc_idx, PLClpc, 0, ilbc_constants.LPC_FILTERORDER + 1)
            consPLICount = 0
        }

        /* update state */
        if (PLI != 0) {
            prevLag = lag
            this.per = max_per
        }
        prevPLI = PLI
        // memcpy(this.prevLpc, PLClpc, (LPC_FILTERORDER+1)*sizeof(float));
        System.arraycopy(PLClpc, 0, prevLpc, 0, ilbc_constants.LPC_FILTERORDER + 1)
        // memcpy(this.prevResidual, PLCresidual, this.ULP_inst.blockl*sizeof(float));
        System.arraycopy(PLCresidual, 0, prevResidual, 0, ULP_inst!!.blockl)
    }

    // public int decode(short decoded_data[], short encoded_data[], int mode)
    // {
    // return this.ULP_inst.blockl;
    // }
    fun decode( /* (o) Number of decoded samples */
            decoded: ByteArray?, decodedOffset: Int,  /* (o) Decoded signal block */
            encoded: ByteArray, encodedOffset: Int,  /* (i) Encoded bytes */
            mode: Short): Short /* (i) 0=PL, 1=Normal */ {
        var decodedOffset = decodedOffset
        val decblock = FloatArray(ilbc_constants.BLOCKL_MAX)
        var dtmp: Float
        // char en_data[] = new char [this.ULP_inst.no_of_bytes];
        val en_data = bitstream(encoded, encodedOffset, ULP_inst!!.no_of_bytes)

        /* check if mode is valid */
        if (mode < 0 || mode > 1) {
            println("\nERROR - Wrong mode - 0, 1 allowed\n")
        }

        /* do actual decoding of block */
        iLBC_decode(decblock, en_data, mode.toInt())

        /* convert to short */
        var k: Int = 0
        while (k < ULP_inst!!.blockl) {
            dtmp = decblock[k]
            // System.out.println("on a eu : " + dtmp);
            if (dtmp < ilbc_constants.MIN_SAMPLE) dtmp = ilbc_constants.MIN_SAMPLE.toFloat() else if (dtmp > ilbc_constants.MAX_SAMPLE) dtmp = ilbc_constants.MAX_SAMPLE.toFloat()
            writeShort(dtmp.toInt().toShort(), decoded!!, decodedOffset)
            k++
            decodedOffset += 2
        }
        return ULP_inst!!.blockl.toShort()
    }

    /*----------------------------------------------------------------*
	 *  frame residual decoder function (subrutine to iLBC_decode)
	 *---------------------------------------------------------------*/
    fun Decode(decresidual: FloatArray,  /* (o) decoded residual frame */
            start: Int,  /*
					 * (i) location of start state
					 */
            idxForMax: Int,  /*
						 * (i) codebook index for the maximum value
						 */
            idxVec: IntArray,  /*
					 * (i) codebook indexes for the samples in the start state
					 */
            syntdenum: FloatArray,  /*
							 * (i) the decoded synthesis filter coefficients
							 */
            cb_index: IntArray,  /*
						 * (i) the indexes for the adaptive codebook
						 */
            gain_index: IntArray,  /*
						 * (i) the indexes for the corresponding gains
						 */
            extra_cb_index: IntArray,  /*
							 * (i) the indexes for the adaptive codebook part of start state
							 */
            extra_gain_index: IntArray,  /*
								 * (i) the indexes for the corresponding gains
								 */
            state_first: Int) /*
						 * (i) 1 if non adaptive part of start state comes first 0 if that part
						 * comes last
						 */ {
        val reverseDecresidual = FloatArray(ilbc_constants.BLOCKL_MAX)
        val mem = FloatArray(ilbc_constants.CB_MEML)
        var k: Int
        var meml_gotten: Int
        var i: Int
        val start_pos: Int
        var subframe: Int
        val diff: Int = ilbc_constants.STATE_LEN - ULP_inst!!.state_short_len
        start_pos = if (state_first == 1) {
            (start - 1) * ilbc_constants.SUBL
        } else {
            (start - 1) * ilbc_constants.SUBL + diff
        }

        /* decode scalar part of start state */
        ilbc_common.StateConstructW(idxForMax, idxVec, syntdenum, (start - 1)
                * (ilbc_constants.LPC_FILTERORDER + 1), decresidual, start_pos,
                ULP_inst!!.state_short_len)
        if (state_first != 0) { /* put adaptive part in the end */

            /* setup memory */
            for (li in 0 until ilbc_constants.CB_MEML - ULP_inst!!.state_short_len) mem[li] = 0.0f
            // memset(mem, 0,
            // (CB_MEML-this.ULP_inst.state_short_len)*sizeof(float));
            System.arraycopy(decresidual, start_pos, mem, ilbc_constants.CB_MEML
                    - ULP_inst!!.state_short_len, ULP_inst!!.state_short_len)
            // memcpy(mem+CB_MEML-this.ULP_inst.state_short_len,
            // decresidual+start_pos,
            // this.ULP_inst.state_short_len*sizeof(float));

            /* construct decoded vector */
            ilbc_common.iCBConstruct(decresidual, start_pos + ULP_inst!!.state_short_len,
                    extra_cb_index, 0, extra_gain_index, 0, mem, ilbc_constants.CB_MEML
                    - ilbc_constants.stMemLTbl, ilbc_constants.stMemLTbl, diff,
                    ilbc_constants.CB_NSTAGES)
        } else { /* put adaptive part in the beginning */

            /* create reversed vectors for prediction */
            k = 0
            while (k < diff) {
                reverseDecresidual[k] = decresidual[(start + 1) * ilbc_constants.SUBL - 1
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
            // memset(mem, 0, (CB_MEML-k)*sizeof(float));

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
        var subcount: Int = 0

        /* forward prediction of sub-frames */
        val Nfor: Int = ULP_inst!!.nsub - start - 1
        if (Nfor > 0) {

            /* setup memory */
            for (li in 0 until ilbc_constants.CB_MEML - ilbc_constants.STATE_LEN) mem[li] = 0.0f
            // memset(mem, 0, (CB_MEML-STATE_LEN)*sizeof(float));
            System.arraycopy(decresidual, (start - 1) * ilbc_constants.SUBL, mem,
                    ilbc_constants.CB_MEML - ilbc_constants.STATE_LEN, ilbc_constants.STATE_LEN)
            // memcpy(mem+CB_MEML-STATE_LEN, decresidual+(start-1)*SUBL,
            // STATE_LEN*sizeof(float));

            /* loop over sub-frames to encode */
            subframe = 0
            while (subframe < Nfor) {


                /* construct decoded vector */
                ilbc_common.iCBConstruct(decresidual, (start + 1 + subframe) * ilbc_constants.SUBL,
                        cb_index, subcount * ilbc_constants.CB_NSTAGES, gain_index, subcount
                        * ilbc_constants.CB_NSTAGES, mem, ilbc_constants.CB_MEML
                        - ilbc_constants.memLfTbl[subcount], ilbc_constants.memLfTbl[subcount],
                        ilbc_constants.SUBL, ilbc_constants.CB_NSTAGES)

                /* update memory */
                System.arraycopy(mem, ilbc_constants.SUBL, mem, 0, ilbc_constants.CB_MEML
                        - ilbc_constants.SUBL)
                // memcpy(mem, mem+SUBL, (CB_MEML-SUBL)*sizeof(float));
                System.arraycopy(decresidual, (start + 1 + subframe) * ilbc_constants.SUBL, mem,
                        ilbc_constants.CB_MEML - ilbc_constants.SUBL, ilbc_constants.SUBL)
                // memcpy(mem+CB_MEML-SUBL,
                // &decresidual[(start+1+subframe)*SUBL],
                // SUBL*sizeof(float));
                subcount++
                subframe++
            }
        }

        /* backward prediction of sub-frames */
        val Nback: Int = start - 1
        if (Nback > 0) {

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

            /* loop over subframes to decode */
            subframe = 0
            while (subframe < Nback) {


                /* construct decoded vector */
                ilbc_common.iCBConstruct(reverseDecresidual, subframe * ilbc_constants.SUBL,
                        cb_index, subcount * ilbc_constants.CB_NSTAGES, gain_index, subcount
                        * ilbc_constants.CB_NSTAGES, mem, ilbc_constants.CB_MEML
                        - ilbc_constants.memLfTbl[subcount], ilbc_constants.memLfTbl[subcount],
                        ilbc_constants.SUBL, ilbc_constants.CB_NSTAGES)

                /* update memory */
                System.arraycopy(mem, ilbc_constants.SUBL, mem, 0, ilbc_constants.CB_MEML
                        - ilbc_constants.SUBL)
                // memcpy(mem, mem+SUBL, (CB_MEML-SUBL)*sizeof(float));
                System.arraycopy(reverseDecresidual, subframe * ilbc_constants.SUBL, mem,
                        ilbc_constants.CB_MEML - ilbc_constants.SUBL, ilbc_constants.SUBL)
                // memcpy(mem+CB_MEML-SUBL,
                // &reverseDecresidual[subframe*SUBL],
                // SUBL*sizeof(float));
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
    }

    /*----------------------------------------------------------------*
	 *  main decoder function
	 *---------------------------------------------------------------*/
    fun iLBC_decode(decblock: FloatArray,  /* (o) decoded signal block */
            bytes: bitstream,  /* (i) encoded signal bits */
            mode: Int) /*
				 * (i) 0: bad packet, PLC, 1: normal
				 */ {
        var mode = mode
        val data = FloatArray(ilbc_constants.BLOCKL_MAX)
        val lsfdeq = FloatArray(ilbc_constants.LPC_FILTERORDER * ilbc_constants.LPC_N_MAX)
        val PLCresidual = FloatArray(ilbc_constants.BLOCKL_MAX)
        val PLClpc = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        val zeros = FloatArray(ilbc_constants.BLOCKL_MAX)
        val one = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        var k: Int
        var i: Int
        var start: Int
        var idxForMax: Int
        /* pos, */
        var lastpart: Int
        var ulp: Int
        var lag: Int
        var ilag: Int
        var cc: Float
        var maxcc: Float
        val idxVec = IntArray(ilbc_constants.STATE_LEN)
        // int check;
        val gain_index = IntArray(ilbc_constants.NASUB_MAX * ilbc_constants.CB_NSTAGES)
        val extra_gain_index = IntArray(ilbc_constants.CB_NSTAGES)
        val cb_index = IntArray(ilbc_constants.CB_NSTAGES * ilbc_constants.NASUB_MAX)
        val extra_cb_index = IntArray(ilbc_constants.CB_NSTAGES)
        val lsf_i = IntArray(ilbc_constants.LSF_NSPLIT * ilbc_constants.LPC_N_MAX)
        var state_first: Int
        val last_bit: Int
        // unsigned char *pbytes;
        val weightdenum = FloatArray((ilbc_constants.LPC_FILTERORDER + 1)
                * ilbc_constants.NSUB_MAX)
        val order_plus_one: Int
        val syntdenum = FloatArray(ilbc_constants.NSUB_MAX
                * (ilbc_constants.LPC_FILTERORDER + 1))
        val decresidual = FloatArray(ilbc_constants.BLOCKL_MAX)
        if (mode > 0) { /* the data are good */

            /* decode data */

            // pbytes=bytes;
            // pos=0;

            /* Set everything to zero before decoding */
            k = 0
            while (k < ilbc_constants.LSF_NSPLIT * ilbc_constants.LPC_N_MAX) {
                lsf_i[k] = 0
                k++
            }
            start = 0
            state_first = 0
            idxForMax = 0
            k = 0
            while (k < ULP_inst!!.state_short_len) {
                idxVec[k] = 0
                k++
            }
            k = 0
            while (k < ilbc_constants.CB_NSTAGES) {
                extra_cb_index[k] = 0
                k++
            }
            k = 0
            while (k < ilbc_constants.CB_NSTAGES) {
                extra_gain_index[k] = 0
                k++
            }
            i = 0
            while (i < ULP_inst!!.nasub) {
                k = 0
                while (k < ilbc_constants.CB_NSTAGES) {
                    cb_index[i * ilbc_constants.CB_NSTAGES + k] = 0
                    k++
                }
                i++
            }
            i = 0
            while (i < ULP_inst!!.nasub) {
                k = 0
                while (k < ilbc_constants.CB_NSTAGES) {
                    gain_index[i * ilbc_constants.CB_NSTAGES + k] = 0
                    k++
                }
                i++
            }

            /* loop over ULP classes */
            ulp = 0
            while (ulp < 3) {


                /* LSF */
                k = 0
                while (k < ilbc_constants.LSF_NSPLIT * ULP_inst!!.lpc_n) {
                    lastpart = bytes.unpack(ULP_inst!!.lsf_bits[k]!![ulp])
                    // unpack( &pbytes, &lastpart,
                    // this.ULP_inst.lsf_bits[k][ulp], &pos);
                    lsf_i[k] = bytes
                            .packcombine(lsf_i[k], lastpart, ULP_inst!!.lsf_bits[k]!![ulp])
                    k++
                }

                /* Start block info */
                lastpart = bytes.unpack(ULP_inst!!.start_bits[ulp])
                // unpack( &pbytes, &lastpart,
                // this.ULP_inst.start_bits[ulp], &pos);
                start = bytes.packcombine(start, lastpart, ULP_inst!!.start_bits[ulp])
                // System.out.println("start = " + start);
                // packcombine(&start, lastpart,
                // this.ULP_inst.start_bits[ulp]);
                lastpart = bytes.unpack(ULP_inst!!.startfirst_bits[ulp])
                // unpack( &pbytes, &lastpart,
                // this.ULP_inst.startfirst_bits[ulp], &pos);
                state_first = bytes.packcombine(state_first, lastpart,
                        ULP_inst!!.startfirst_bits[ulp])
                // System.out.println("state_first = " + state_first);
                // packcombine(&state_first, lastpart,
                // this.ULP_inst.startfirst_bits[ulp]);
                lastpart = bytes.unpack(ULP_inst!!.scale_bits[ulp])
                // unpack( &pbytes, &lastpart,
                // this.ULP_inst.scale_bits[ulp], &pos);
                idxForMax = bytes.packcombine(idxForMax, lastpart, ULP_inst!!.scale_bits[ulp])
                // System.out.println("idxForMax = " + idxForMax);
                // packcombine(&idxForMax, lastpart,
                // this.ULP_inst.scale_bits[ulp]);
                k = 0
                while (k < ULP_inst!!.state_short_len) {
                    lastpart = bytes.unpack(ULP_inst!!.state_bits[ulp])
                    // unpack( &pbytes, &lastpart,
                    // this.ULP_inst.state_bits[ulp], &pos);
                    idxVec[k] = bytes.packcombine(idxVec[k], lastpart,
                            ULP_inst!!.state_bits[ulp])
                    k++
                }

                /* 23/22 (20ms/30ms) sample block */
                k = 0
                while (k < ilbc_constants.CB_NSTAGES) {
                    lastpart = bytes.unpack(ULP_inst!!.extra_cb_index[k]!![ulp])
                    // unpack( &pbytes, &lastpart,
                    // this.ULP_inst.extra_cb_index[k][ulp],
                    // &pos);
                    extra_cb_index[k] = bytes.packcombine(extra_cb_index[k], lastpart,
                            ULP_inst!!.extra_cb_index[k]!![ulp])
                    k++
                }
                k = 0
                while (k < ilbc_constants.CB_NSTAGES) {
                    lastpart = bytes.unpack(ULP_inst!!.extra_cb_gain[k]!![ulp])
                    // unpack( &pbytes, &lastpart,
                    // this.ULP_inst.extra_cb_gain[k][ulp],
                    // &pos);
                    extra_gain_index[k] = bytes.packcombine(extra_gain_index[k], lastpart,
                            ULP_inst!!.extra_cb_gain[k]!![ulp])
                    k++
                }

                /* The two/four (20ms/30ms) 40 sample sub-blocks */
                i = 0
                while (i < ULP_inst!!.nasub) {
                    k = 0
                    while (k < ilbc_constants.CB_NSTAGES) {
                        lastpart = bytes.unpack(ULP_inst!!.cb_index[i][k]!![ulp])
                        // unpack( &pbytes, &lastpart,
                        // this.ULP_inst.cb_index[i][k][ulp],
                        // &pos);
                        cb_index[i * ilbc_constants.CB_NSTAGES + k] = bytes.packcombine(cb_index[i
                                * ilbc_constants.CB_NSTAGES + k], lastpart,
                                ULP_inst!!.cb_index[i][k]!![ulp])
                        k++
                    }
                    i++
                }
                i = 0
                while (i < ULP_inst!!.nasub) {
                    k = 0
                    while (k < ilbc_constants.CB_NSTAGES) {
                        lastpart = bytes.unpack(ULP_inst!!.cb_gain[i][k]!![ulp])
                        gain_index[i * ilbc_constants.CB_NSTAGES + k] = bytes.packcombine(
                                gain_index[i * ilbc_constants.CB_NSTAGES + k], lastpart,
                                ULP_inst!!.cb_gain[i][k]!![ulp])
                        k++
                    }
                    i++
                }
                ulp++
            }
            /*
			 * Extract last bit. If it is 1 this indicates an empty/lost frame
			 */
            last_bit = bytes.unpack(1)
            // System.out.println("last_bit = " + last_bit);

            /* Check for bit errors or empty/lost frames */
            if (start < 1) mode = 0
            if (ULP_inst!!.mode == 20 && start > 3) mode = 0
            if (ULP_inst!!.mode == 30 && start > 5) mode = 0
            if (last_bit == 1) mode = 0
            if (mode == 1) { /*
							 * No bit errors was detected, continue decoding
							 */

                /* adjust index */
                index_conv_dec(cb_index)

                // for (int li = 0; li < cb_index.length; li++)
                // System.out.println("cb_index["+li+"] = " + cb_index[li]);

                /* decode the lsf */
                SimplelsfDEQ(lsfdeq, lsf_i, ULP_inst!!.lpc_n)
                // for (int li = 0; li < lsfdeq.length; li++)
                // System.out.println("lsfdeq["+li+"] = " + lsfdeq[li]);
                ilbc_common.LSF_check(lsfdeq, ilbc_constants.LPC_FILTERORDER, ULP_inst!!.lpc_n)
                // check=ilbc_common.LSF_check(lsfdeq, ilbc_constants.LPC_FILTERORDER,
                // this.ULP_inst.lpc_n);
                // System.out.println("check returns " + check);
                DecoderInterpolateLSF(syntdenum, weightdenum, lsfdeq,
                        ilbc_constants.LPC_FILTERORDER)
                // for (int li = 0; li < syntdenum.length; li++)
                // System.out.println("syntdenum[" + li + "] = " + syntdenum[li]);
                // for (int li = 0; li < weightdenum.length; li++)
                // System.out.println("weightdenum[" + li + "] = " + weightdenum[li]);
                Decode(decresidual, start, idxForMax, idxVec, syntdenum, cb_index, gain_index,
                        extra_cb_index, extra_gain_index, state_first)

                // for (int li = 0; li < decresidual.length; li++)
                // System.out.println("decresidual[" + li + "] = " + decresidual[li]);

                /* preparing the plc for a future loss! */
                doThePLC(PLCresidual, PLClpc, 0, decresidual, syntdenum,
                        (ilbc_constants.LPC_FILTERORDER + 1) * (ULP_inst!!.nsub - 1), last_lag)
                System.arraycopy(PLCresidual, 0, decresidual, 0, ULP_inst!!.blockl)
                // for (int li = 0; li < decresidual.length; li++)
                // System.out.println("decresidual[" + li + "] = " + decresidual[li]);
                // memcpy(decresidual, PLCresidual,
                // this.ULP_inst.blockl*sizeof(float));
            }
        }
        if (mode == 0) {
            /*
			 * the data is bad (either a PLC call was made or a severe bit error was detected)
			 */

            /* packet loss conceal */
            for (li in 0 until ilbc_constants.BLOCKL_MAX) zeros[li] = 0.0f
            // memset(zeros, 0, BLOCKL_MAX*sizeof(float));
            one[0] = 1f
            for (li in 0 until ilbc_constants.LPC_FILTERORDER) one[li + 1] = 0.0f
            // memset(one+1, 0, LPC_FILTERORDER*sizeof(float));
            start = 0
            doThePLC(PLCresidual, PLClpc, 1, zeros, one, 0, last_lag)
            System.arraycopy(PLCresidual, 0, decresidual, 0, ULP_inst!!.blockl)
            // memcpy(decresidual, PLCresidual,
            // this.ULP_inst.blockl*sizeof(float));
            order_plus_one = ilbc_constants.LPC_FILTERORDER + 1
            i = 0
            while (i < ULP_inst!!.nsub) {
                System.arraycopy(PLClpc, 0, syntdenum, i * order_plus_one, order_plus_one)
                i++
            }
        }
        if (use_enhancer == 1) {

            /* post filtering */
            last_lag = enhancerInterface(data, decresidual)

            // System.out.println("last_lag : " + this.last_lag);

            // for (int li = 0; li < data.length; li++)
            // System.out.println("data["+li+"] = " + data[li]);

            // for (li = 0; li <

            /* synthesis filtering */
            if (ULP_inst!!.mode == 20) {
                /* Enhancer has 40 samples delay */
                i = 0
                // System.out.println("run 1");
                syntFilter(data, i * ilbc_constants.SUBL, old_syntdenum, (i
                        + ULP_inst!!.nsub - 1)
                        * (ilbc_constants.LPC_FILTERORDER + 1), ilbc_constants.SUBL, syntMem)
                // System.out.println("runs 2");
                i = 1
                while (i < ULP_inst!!.nsub) {

                    // System.out.println("pass " + i);
                    syntFilter(data, i * ilbc_constants.SUBL, syntdenum, (i - 1)
                            * (ilbc_constants.LPC_FILTERORDER + 1), ilbc_constants.SUBL, syntMem)
                    i++
                }
                // for (int li = 0; li < data.length; li++)
                // System.out.println("psdata["+li+"] = " + data[li]);
            } else if (ULP_inst!!.mode == 30) {
                /* Enhancer has 80 samples delay */
                // System.out.println("runs 3");
                i = 0
                while (i < 2) {
                    syntFilter(data, i * ilbc_constants.SUBL, old_syntdenum, (i
                            + ULP_inst!!.nsub - 2)
                            * (ilbc_constants.LPC_FILTERORDER + 1), ilbc_constants.SUBL, syntMem)
                    i++
                }
                i = 2
                while (i < ULP_inst!!.nsub) {

                    // System.out.println("runs 4");
                    syntFilter(data, i * ilbc_constants.SUBL, syntdenum, (i - 2)
                            * (ilbc_constants.LPC_FILTERORDER + 1), ilbc_constants.SUBL, syntMem)
                    i++
                }
            }
        } else {

            /* Find last lag */
            lag = 20
            maxcc = xCorrCoef(decresidual, ilbc_constants.BLOCKL_MAX - ilbc_constants.ENH_BLOCKL,
                    decresidual, ilbc_constants.BLOCKL_MAX - ilbc_constants.ENH_BLOCKL - lag,
                    ilbc_constants.ENH_BLOCKL)
            ilag = 21
            while (ilag < 120) {
                cc = xCorrCoef(decresidual, ilbc_constants.BLOCKL_MAX - ilbc_constants.ENH_BLOCKL,
                        decresidual, ilbc_constants.BLOCKL_MAX - ilbc_constants.ENH_BLOCKL - ilag,
                        ilbc_constants.ENH_BLOCKL)
                if (cc > maxcc) {
                    maxcc = cc
                    lag = ilag
                }
                ilag++
            }
            last_lag = lag

            /* copy data and run synthesis filter */
            System.arraycopy(decresidual, 0, data, 0, ULP_inst!!.blockl)
            // memcpy(data, decresidual,
            // this.ULP_inst.blockl*sizeof(float));
            // System.out.println("runs 5");
            i = 0
            while (i < ULP_inst!!.nsub) {
                syntFilter(data, i * ilbc_constants.SUBL, syntdenum, i
                        * (ilbc_constants.LPC_FILTERORDER + 1), ilbc_constants.SUBL, syntMem)
                i++
            }
        }

        /*
		 * high pass filtering on output if desired, otherwise copy to out
		 */
        hpOutput(data, ULP_inst!!.blockl, decblock, hpomem)

        /* memcpy(decblock,data,iLBCdec_inst->blockl*sizeof(float)); */
        System.arraycopy(syntdenum, 0, old_syntdenum, 0, ULP_inst!!.nsub
                * (ilbc_constants.LPC_FILTERORDER + 1))
        // memcpy(this.old_syntdenum, syntdenum,
        // this.ULP_inst.nsub*(LPC_FILTERORDER+1)*sizeof(float));
        prev_enh_pl = 0
        if (mode == 0) { /* PLC was used */
            prev_enh_pl = 1
        }
    }

    init {
        ULP_inst = ilbc_ulp(init_mode)
        /* properties to initialize : */
        syntMem = FloatArray(ilbc_constants.LPC_FILTERORDER)
        prevLpc = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        prevResidual = FloatArray(ilbc_constants.NSUB_MAX * ilbc_constants.SUBL)
        old_syntdenum = FloatArray((ilbc_constants.LPC_FILTERORDER + 1) * ilbc_constants.NSUB_MAX)
        hpomem = FloatArray(4)
        enh_buf = FloatArray(ilbc_constants.ENH_BUFL)
        enh_period = FloatArray(ilbc_constants.ENH_NBLOCKS_TOT)
        lsfdeqold = FloatArray(ilbc_constants.LPC_FILTERORDER)
        for (li in syntMem.indices) syntMem[li] = 0.0f
        System
                .arraycopy(ilbc_constants.lsfmeanTbl, 0, lsfdeqold, 0, ilbc_constants.LPC_FILTERORDER)
        // for (int li = 0; li < lsfdeqold.length; li++)
        // lsfdeqold[li] = 0.0f;
        for (li in old_syntdenum.indices) old_syntdenum[li] = 0.0f
        for (li in 0 until ilbc_constants.NSUB_MAX) old_syntdenum[li * (ilbc_constants.LPC_FILTERORDER + 1)] = 1.0f
        last_lag = 20
        prevLag = 120
        per = 0.0f
        consPLICount = 0
        prevPLI = 0
        prevLpc[0] = 1.0f
        for (li in 1 until prevLpc.size) prevLpc[li] = 0.0f
        for (li in prevResidual.indices) prevResidual[li] = 0.0f
        seed = 777
        for (li in hpomem.indices) hpomem[li] = 0.0f
        use_enhancer = init_enhancer
        for (li in enh_buf.indices) enh_buf[li] = 0.0f
        for (li in 0 until ilbc_constants.ENH_NBLOCKS_TOT) enh_period[li] = 40.0f
        prev_enh_pl = 0
    }
}