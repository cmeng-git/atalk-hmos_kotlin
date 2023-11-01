/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

/**
 * @author Jean Lorchat
 */
internal object ilbc_common {
    /*----------------------------------------------------------------*
	 *  check for stability of lsf coefficients
	 *---------------------------------------------------------------*/
    fun LSF_check( /*
								 * (o) 1 for stable lsf vectors and 0 for nonstable ones
								 */
            lsf: FloatArray,  /* (i) a table of lsf vectors */
            dim: Int,  /* (i) the dimension of each lsf vector */
            NoAn: Int): Int
    /*
     * (i) the number of lsf vectors in the table
     */
    {
        var k: Int
        var n: Int
        var m: Int
        val Nit = 2
        var change = 0
        var pos: Int
        val eps = 0.039.toFloat() /* 50 Hz */
        val eps2 = 0.0195.toFloat()
        val maxlsf = 3.14.toFloat() /* 4000 Hz */
        val minlsf = 0.01.toFloat() /* 0 Hz */

        /* LSF separation check */
        n = 0
        while (n < Nit) {
            /* Run through a couple of times */
            m = 0
            while (m < NoAn) {
                /* Number of analyses per frame */
                k = 0
                while (k < dim - 1) {
                    pos = m * dim + k
                    if (lsf[pos + 1] - lsf[pos] < eps) {
                        if (lsf[pos + 1] < lsf[pos]) {
                            lsf[pos + 1] = lsf[pos] + eps2
                            lsf[pos] = lsf[pos + 1] - eps2
                        } else {
                            lsf[pos] -= eps2
                            lsf[pos + 1] += eps2
                        }
                        change = 1
                    }
                    if (lsf[pos] < minlsf) {
                        lsf[pos] = minlsf
                        change = 1
                    }
                    if (lsf[pos] > maxlsf) {
                        lsf[pos] = maxlsf
                        change = 1
                    }
                    k++
                }
                m++
            }
            n++
        }
        return change
    }

    /*----------------------------------------------------------------*
	 *  decoding of the start state
	 *---------------------------------------------------------------*/
    fun StateConstructW(idxForMax: Int,  /*
													 * (i) 6-bit index for the quantization of max
													 * amplitude
													 */
            idxVec: IntArray,  /* (i) vector of quantization indexes */
            syntDenum: FloatArray,  /* (i) synthesis filter denumerator */
            syntDenum_idx: Int, out: FloatArray,  /* (o) the decoded state vector */
            out_idx: Int, len: Int /* (i) length of a state vector */
    ) {
        var maxVal: Float
        val tmpbuf = FloatArray(ilbc_constants.LPC_FILTERORDER + 2 * ilbc_constants.STATE_LEN)
        // , *tmp,
        val tmp: Int
        val numerator = FloatArray(ilbc_constants.LPC_FILTERORDER + 1)
        val foutbuf = FloatArray(ilbc_constants.LPC_FILTERORDER + 2 * ilbc_constants.STATE_LEN)
        // , *fout;
        val fout: Int
        var k: Int
        var tmpi: Int

        /* decoding of the maximum value */
        maxVal = ilbc_constants.state_frgqTbl[idxForMax]
        // System.out.println("idxForMax : " + idxForMax + "maxVal : " + maxVal);
        maxVal = Math.pow(10.0, maxVal.toDouble()).toFloat() / 4.5f
        // System.out.println("maxVal : " + maxVal);

        /* initialization of buffers and coefficients */
        for (li in 0 until ilbc_constants.LPC_FILTERORDER) {
            tmpbuf[li] = 0.0f
            foutbuf[li] = 0.0f
        }
        // memset(tmpbuf, 0, LPC_FILTERORDER*sizeof(float));
        // memset(foutbuf, 0, LPC_FILTERORDER*sizeof(float));
        k = 0
        while (k < ilbc_constants.LPC_FILTERORDER) {
            numerator[k] = syntDenum[syntDenum_idx + ilbc_constants.LPC_FILTERORDER - k]
            k++
        }
        numerator[ilbc_constants.LPC_FILTERORDER] = syntDenum[syntDenum_idx]
        // tmp = &tmpbuf[LPC_FILTERORDER];
        tmp = ilbc_constants.LPC_FILTERORDER
        // fout = &foutbuf[LPC_FILTERORDER];
        fout = ilbc_constants.LPC_FILTERORDER

        /* decoding of the sample values */

        // for (int li = 0; li < idxVec.length; li++)
        // System.out.println("idxVec["+li+"] = " + idxVec[li]);
        k = 0
        while (k < len) {
            tmpi = len - 1 - k
            /* maxVal = 1/scal */
            tmpbuf[tmp + k] = maxVal * ilbc_constants.state_sq3Tbl[idxVec[tmpi]]
            k++
        }

        /* circular convolution with all-pass filter */
        for (li in 0 until len) tmpbuf[tmp + len + li] = 0.0f
        // memset(tmp+len, 0, len*sizeof(float));
        ZeroPoleFilter(tmpbuf, tmp, numerator, syntDenum, syntDenum_idx, 2 * len,
                ilbc_constants.LPC_FILTERORDER, foutbuf, fout)
        k = 0
        while (k < len) {
            out[out_idx + k] = foutbuf[fout + len - 1 - k] + foutbuf[fout + 2 * len - 1 - k]
            k++
        }
    }

    /*----------------------------------------------------------------*
	 *  all-pole filter
	 *---------------------------------------------------------------*/
    fun AllPoleFilter(InOut: FloatArray,  /*
													 * (i/o) on entrance InOut[-orderCoef] to
													 * InOut[-1] contain the state of the filter
													 * (delayed samples). InOut[0] to
													 * InOut[lengthInOut-1] contain the filter
													 * input, on en exit InOut[-orderCoef] to
													 * InOut[-1] is unchanged and InOut[0] to
													 * InOut[lengthInOut-1] contain filtered samples
													 */
            InOut_idx: Int, Coef: FloatArray,  /*
									 * (i) filter coefficients, Coef[0] is assumed to be 1.0f
									 */
            Coef_idx: Int, lengthInOut: Int,  /* (i) number of input/output samples */
            orderCoef: Int) /* (i) number of filter coefficients */
    {
        var n: Int
        var k: Int
        n = 0
        while (n < lengthInOut) {
            k = 1
            while (k <= orderCoef) {
                InOut[n + InOut_idx] -= Coef[Coef_idx + k] * InOut[n - k + InOut_idx]
                k++
            }
            n++
        }
    }

    /*----------------------------------------------------------------*
	 *  all-zero filter
	 *---------------------------------------------------------------*/
    fun AllZeroFilter(In: FloatArray,  /*
												 * (i) In[0] to In[lengthInOut-1] contain filter
												 * input samples
												 */
            In_idx: Int, Coef: FloatArray,  /*
								 * (i) filter coefficients (Coef[0] is assumed to be 1.0f)
								 */
            lengthInOut: Int,  /* (i) number of input/output samples */
            orderCoef: Int,  /* (i) number of filter coefficients */
            Out: FloatArray,  /*
					 * (i/o) on entrance Out[-orderCoef] to Out[-1] contain the filter state, on
					 * exit Out[0] to Out[lengthInOut-1] contain filtered samples
					 */
            Out_idx: Int) {
        var In_idx = In_idx
        var Out_idx = Out_idx
        var n: Int
        var k: Int
        n = 0
        while (n < lengthInOut) {
            Out[Out_idx] = Coef[0] * In[In_idx]
            k = 1
            while (k <= orderCoef) {
                Out[Out_idx] += Coef[k] * In[In_idx - k]
                k++
            }
            Out_idx++
            In_idx++
            n++
        }
    }

    /*----------------------------------------------------------------*
	 *  pole-zero filter
	 *---------------------------------------------------------------*/
    fun ZeroPoleFilter(In: FloatArray,  /*
												 * (i) In[0] to In[lengthInOut-1] contain filter
												 * input samples In[-orderCoef] to In[-1] contain
												 * state of all-zero section
												 */
            In_idx: Int, ZeroCoef: FloatArray,  /*
									 * (i) filter coefficients for all-zero section (ZeroCoef[0] is
									 * assumed to be 1.0f)
									 */
            PoleCoef: FloatArray,  /*
						 * (i) filter coefficients for all-pole section (ZeroCoef[0] is assumed to
						 * be 1.0f)
						 */
            PoleCoef_idx: Int, lengthInOut: Int,  /* (i) number of input/output samples */
            orderCoef: Int,  /* (i) number of filter coefficients */
            Out: FloatArray,  /*
					 * (i/o) on entrance Out[-orderCoef] to Out[-1] contain state of all-pole
					 * section. On exit Out[0] to Out[lengthInOut-1] contain filtered samples
					 */
            Out_idx: Int) {
        AllZeroFilter(In, In_idx, ZeroCoef, lengthInOut, orderCoef, Out, Out_idx)
        AllPoleFilter(Out, Out_idx, PoleCoef, PoleCoef_idx, lengthInOut, orderCoef)
    }

    /*----------------------------------------------------------------*
	 *  conversion from lsf coefficients to lpc coefficients
	 *---------------------------------------------------------------*/
    fun lsf2a(a_coef: FloatArray, freq: FloatArray) {
        var i: Int
        var j: Int
        val hlp: Float
        val p = FloatArray(ilbc_constants.LPC_HALFORDER)
        val q = FloatArray(ilbc_constants.LPC_HALFORDER)
        val a = FloatArray(ilbc_constants.LPC_HALFORDER + 1)
        val a1 = FloatArray(ilbc_constants.LPC_HALFORDER)
        val a2 = FloatArray(ilbc_constants.LPC_HALFORDER)
        val b = FloatArray(ilbc_constants.LPC_HALFORDER + 1)
        val b1 = FloatArray(ilbc_constants.LPC_HALFORDER)
        val b2 = FloatArray(ilbc_constants.LPC_HALFORDER)

        // System.out.println("debut de lsf2a");
        i = 0
        while (i < ilbc_constants.LPC_FILTERORDER) {
            freq[i] = freq[i] * ilbc_constants.PI2
            i++
        }

        /*
		 * Check input for ill-conditioned cases. This part is not found in the TIA standard. It
		 * involves the following 2 IF blocks. If "freq" is judged ill-conditioned, then we first
		 * modify freq[0] and freq[LPC_HALFORDER-1] (normally LPC_HALFORDER = 10 for LPC
		 * applications), then we adjust the other "freq" values slightly
		 */
        if (freq[0] <= 0.0f || freq[ilbc_constants.LPC_FILTERORDER - 1] >= 0.5) {
            if (freq[0] <= 0.0f) {
                freq[0] = 0.022.toFloat()
            }
            if (freq[ilbc_constants.LPC_FILTERORDER - 1] >= 0.5) {
                freq[ilbc_constants.LPC_FILTERORDER - 1] = 0.499.toFloat()
            }
            hlp = ((freq[ilbc_constants.LPC_FILTERORDER - 1] - freq[0])
                    / (ilbc_constants.LPC_FILTERORDER - 1))
            i = 1
            while (i < ilbc_constants.LPC_FILTERORDER) {
                freq[i] = freq[i - 1] + hlp
                i++
            }
        }
        for (li in 0 until ilbc_constants.LPC_HALFORDER) {
            a1[li] = 0.0f
            a2[li] = 0.0f
            b1[li] = 0.0f
            b2[li] = 0.0f
        }
        // memset(a1, 0, LPC_HALFORDER*sizeof(float));
        // memset(a2, 0, LPC_HALFORDER*sizeof(float));
        // memset(b1, 0, LPC_HALFORDER*sizeof(float));
        // memset(b2, 0, LPC_HALFORDER*sizeof(float));
        for (li in 0 until ilbc_constants.LPC_HALFORDER + 1) {
            a[li] = 0.0f
            b[li] = 0.0f
        }
        // memset(a, 0, (LPC_HALFORDER+1)*sizeof(float));
        // memset(b, 0, (LPC_HALFORDER+1)*sizeof(float));

        /*
		 * p[i] and q[i] compute cos(2*pi*omega_{2j}) and cos(2*pi*omega_{2j-1} in eqs. 4.2.2.2-1
		 * and 4.2.2.2-2. Note that for this code p[i] specifies the coefficients used in .Q_A(z)
		 * while q[i] specifies the coefficients used in .P_A(z)
		 */
        i = 0
        while (i < ilbc_constants.LPC_HALFORDER) {
            p[i] = Math.cos((ilbc_constants.TWO_PI * freq[2 * i]).toDouble()).toFloat()
            q[i] = Math.cos((ilbc_constants.TWO_PI * freq[2 * i + 1]).toDouble()).toFloat()
            i++
        }
        a[0] = 0.25f
        b[0] = 0.25f
        i = 0
        while (i < ilbc_constants.LPC_HALFORDER) {
            a[i + 1] = a[i] - 2 * p[i] * a1[i] + a2[i]
            b[i + 1] = b[i] - 2 * q[i] * b1[i] + b2[i]
            a2[i] = a1[i]
            a1[i] = a[i]
            b2[i] = b1[i]
            b1[i] = b[i]
            i++
        }
        j = 0
        while (j < ilbc_constants.LPC_FILTERORDER) {
            if (j == 0) {
                a[0] = 0.25f
                b[0] = -0.25f
            } else {
                b[0] = 0.0f
                a[0] = b[0]
            }
            i = 0
            while (i < ilbc_constants.LPC_HALFORDER) {
                a[i + 1] = a[i] - 2 * p[i] * a1[i] + a2[i]
                b[i + 1] = b[i] - 2 * q[i] * b1[i] + b2[i]
                a2[i] = a1[i]
                a1[i] = a[i]
                b2[i] = b1[i]
                b1[i] = b[i]
                i++
            }
            a_coef[j + 1] = 2 * (a[ilbc_constants.LPC_HALFORDER] + b[ilbc_constants.LPC_HALFORDER])
            j++
        }
        a_coef[0] = 1.0f
    }

    /*----------------------------------------------------------------*
	 *  Construct decoded vector from codebook and gains.
	 *---------------------------------------------------------------*/
    /*----------------------------------------------------------------*
	 *  interpolation between vectors
	 *---------------------------------------------------------------*/
    fun interpolate(out: FloatArray,  /* (o) the interpolated vector */
            in1: FloatArray,  /*
					 * (i) the first vector for the interpolation
					 */
            in2: FloatArray,  /*
					 * (i) the second vector for the interpolation
					 */
            in2_idx: Int, coef: Float,  /* (i) interpolation weights */
            length: Int) /* (i) length of all vectors */ {
        val invcoef = 1.0f - coef
        var i: Int = 0
        while (i < length) {
            out[i] = coef * in1[i] + invcoef * in2[i + in2_idx]
            i++
        }
    }

    /*----------------------------------------------------------------*
	 *  lpc bandwidth expansion
	 *---------------------------------------------------------------*/
    fun bwexpand(out: FloatArray,  /*
											 * (o) the bandwidth expanded lpc coefficients
											 */
            out_idx: Int, `in`: FloatArray,  /*
								 * (i) the lpc coefficients before bandwidth expansion
								 */
            coef: Float,  /* (i) the bandwidth expansion factor */
            length: Int) /* (i) the length of lpc coefficient vectors */ {
        var chirp: Float = coef
        out[out_idx] = `in`[0]
        var i: Int = 1
        while (i < length) {
            out[i + out_idx] = chirp * `in`[i]
            chirp *= coef
            i++
        }
    }

    fun getCBvec(cbvec: FloatArray,  /* (o) Constructed codebook vector */
            mem: FloatArray,  /* (i) Codebook buffer */
            mem_idx: Int, index: Int,  /* (i) Codebook index */
            lMem: Int,  /* (i) Length of codebook buffer */
            cbveclen: Int) /* (i) Codebook vector length */ {
        var j: Int
        val k: Int
        var n: Int
        val memInd: Int
        val sFilt: Int
        val tmpbuf = FloatArray(ilbc_constants.CB_MEML)
        var base_size: Int
        val ilow: Int
        val ihigh: Int
        var alfa: Float
        val alfa1: Float

        /* Determine size of codebook sections */
        base_size = lMem - cbveclen + 1
        if (cbveclen == ilbc_constants.SUBL) {
            base_size += cbveclen / 2
        }

        /* No filter -> First codebook section */
        if (index < lMem - cbveclen + 1) {

            /* first non-interpolated vectors */
            k = index + cbveclen
            /* get vector */
            System.arraycopy(mem, mem_idx + lMem - k, cbvec, 0, cbveclen)
            // memcpy(cbvec, mem+lMem-k, cbveclen*sizeof(float));
        } else if (index < base_size) {
            k = 2 * (index - (lMem - cbveclen + 1)) + cbveclen
            ihigh = k / 2
            ilow = ihigh - 5

            /* Copy first noninterpolated part */
            System.arraycopy(mem, mem_idx + lMem - k / 2, cbvec, 0, ilow)
            // memcpy(cbvec, mem+lMem-k/2, ilow*sizeof(float));

            /* interpolation */
            alfa1 = 0.2.toFloat()
            alfa = 0.0f
            j = ilow
            while (j < ihigh) {
                cbvec[j] = (1.0f - alfa) * mem[mem_idx + lMem - k / 2 + j] + alfa * mem[mem_idx + lMem - k + j]
                alfa += alfa1
                j++
            }

            /* Copy second noninterpolated part */
            System.arraycopy(mem, mem_idx + lMem - k + ihigh, cbvec, ihigh, cbveclen - ihigh)
            // memcpy(cbvec+ihigh, mem+lMem-k+ihigh,
            // (cbveclen-ihigh)*sizeof(float));
        } else {

            /* first non-interpolated vectors */
            if (index - base_size < lMem - cbveclen + 1) {
                val tempbuff2 = FloatArray(ilbc_constants.CB_MEML + ilbc_constants.CB_FILTERLEN
                        + 1)
                // float *pos;
                // float *pp, *pp1;
                var pos: Int
                var pp: Int
                var pp1: Int
                for (li in 0 until ilbc_constants.CB_HALFFILTERLEN) tempbuff2[li] = 0.0f
                // memset(tempbuff2, 0,
                // CB_HALFFILTERLEN*sizeof(float));
                System.arraycopy(mem, mem_idx, tempbuff2, ilbc_constants.CB_HALFFILTERLEN, lMem)
                // memcpy(&tempbuff2[CB_HALFFILTERLEN], mem,
                // lMem*sizeof(float));
                for (li in 0 until ilbc_constants.CB_HALFFILTERLEN + 1) tempbuff2[lMem + ilbc_constants.CB_HALFFILTERLEN + li] = 0.0f
                // memset(&tempbuff2[lMem+CB_HALFFILTERLEN], 0,
                // (CB_HALFFILTERLEN+1)*sizeof(float));
                k = index - base_size + cbveclen
                sFilt = lMem - k
                memInd = sFilt + 1 - ilbc_constants.CB_HALFFILTERLEN

                /* do filtering */
                // pos=cbvec;
                pos = 0
                for (li in 0 until cbveclen) cbvec[li] = 0f
                // memset(pos, 0, cbveclen*sizeof(float));
                n = 0
                while (n < cbveclen) {
                    pp = memInd + n + ilbc_constants.CB_HALFFILTERLEN
                    // pp=&tempbuff2[memInd+n+CB_HALFFILTERLEN];
                    pp1 = ilbc_constants.CB_FILTERLEN - 1
                    // pp1=&cbfiltersTbl[CB_FILTERLEN-1];
                    j = 0
                    while (j < ilbc_constants.CB_FILTERLEN) {

                        // (*pos)+=(*pp++)*(*pp1--);
                        cbvec[pos] += tempbuff2[pp] * ilbc_constants.cbfiltersTbl[pp1]
                        pp++
                        pp1--
                        j++
                    }
                    pos++
                    n++
                }
            } else {
                val tempbuff2 = FloatArray(ilbc_constants.CB_MEML + ilbc_constants.CB_FILTERLEN
                        + 1)

                // float *pos;
                // float *pp, *pp1;
                var pos: Int
                var pp: Int
                var pp1: Int
                var i: Int
                for (li in 0 until ilbc_constants.CB_HALFFILTERLEN) tempbuff2[li] = 0.0f
                // memset(tempbuff2, 0,
                // CB_HALFFILTERLEN*sizeof(float));
                System.arraycopy(mem, mem_idx, tempbuff2, ilbc_constants.CB_HALFFILTERLEN, lMem)
                // memcpy(&tempbuff2[CB_HALFFILTERLEN], mem,
                // lMem*sizeof(float));
                for (li in 0 until ilbc_constants.CB_HALFFILTERLEN) tempbuff2[lMem + ilbc_constants.CB_HALFFILTERLEN + li] = 0.0f
                // memset(&tempbuff2[lMem+CB_HALFFILTERLEN], 0,
                // (CB_HALFFILTERLEN+1)*sizeof(float));
                k = 2 * (index - base_size - (lMem - cbveclen + 1)) + cbveclen
                sFilt = lMem - k
                memInd = sFilt + 1 - ilbc_constants.CB_HALFFILTERLEN

                /* do filtering */
                // pos=&tmpbuf[sFilt];
                pos = sFilt
                // memset(pos, 0, k*sizeof(float));
                for (li in 0 until k) tmpbuf[pos + li] = 0.0f
                i = 0
                while (i < k) {
                    pp = memInd + i + ilbc_constants.CB_HALFFILTERLEN
                    // pp=&tempbuff2[memInd+i+CB_HALFFILTERLEN];
                    pp1 = ilbc_constants.CB_FILTERLEN - 1
                    // pp1=&cbfiltersTbl[CB_FILTERLEN-1];
                    j = 0
                    while (j < ilbc_constants.CB_FILTERLEN) {

                        // (*pos)+=(*pp++)*(*pp1--);
                        tmpbuf[pos] += tempbuff2[pp] * ilbc_constants.cbfiltersTbl[pp1]
                        pp++
                        pp1--
                        j++
                    }
                    pos++
                    i++
                }
                ihigh = k / 2
                ilow = ihigh - 5

                /* Copy first noninterpolated part */
                System.arraycopy(tmpbuf, lMem - k / 2, cbvec, 0, ilow)
                // memcpy(cbvec, tmpbuf+lMem-k/2,
                // ilow*sizeof(float));

                /* interpolation */
                alfa1 = 0.2.toFloat()
                alfa = 0.0f
                j = ilow
                while (j < ihigh) {
                    cbvec[j] = (1.0f - alfa) * tmpbuf[lMem - k / 2 + j] + alfa * tmpbuf[lMem - k + j]
                    alfa += alfa1
                    j++
                }

                /* Copy second noninterpolated part */
                System.arraycopy(tmpbuf, lMem - k + ihigh, cbvec, ihigh, cbveclen - ihigh)
                // memcpy(cbvec+ihigh, tmpbuf+lMem-k+ihigh,
                // (cbveclen-ihigh)*sizeof(float));
            }
        }
    }

    fun gainquant( /* (o) quantized gain value */
            `in`: Float,  /* (i) gain value */
            maxIn: Float,  /* (i) maximum of gain value */
            cblen: Int,  /* (i) number of quantization indices */
            index: IntArray,  /* (o) quantization index */
            index_idx: Int): Float {
        var i: Int
        var tindex: Int
        var minmeasure: Float
        var measure: Float
        val cb: FloatArray?
        var scale: Float

        /* ensure a lower bound on the scaling factor */
        scale = maxIn
        if (scale < 0.1) {
            scale = 0.1.toFloat()
        }

        /* select the quantization table */cb = if (cblen == 8) {
            ilbc_constants.gain_sq3Tbl
        } else if (cblen == 16) {
            ilbc_constants.gain_sq4Tbl
        } else {
            ilbc_constants.gain_sq5Tbl
        }

        /* select the best index in the quantization table */
        minmeasure = 10000000.0f
        tindex = 0
        i = 0
        while (i < cblen) {
            measure = (`in` - scale * cb[i]) * (`in` - scale * cb[i])
            if (measure < minmeasure) {
                tindex = i
                minmeasure = measure
            }
            i++
        }
        index[index_idx] = tindex

        /* return the quantized value */
        return scale * cb[tindex]
    }

    /*----------------------------------------------------------------*
	 *  decoder for quantized gains in the gain-shape coding of
	 *  residual
	 *---------------------------------------------------------------*/
    fun gaindequant( /* (o) quantized gain value */
            index: Int,  /* (i) quantization index */
            maxIn: Float,  /* (i) maximum of unquantized gain */
            cblen: Int): Float /* (i) number of quantization indices */ {
        var scale: Float

        /* obtain correct scale factor */
        scale = Math.abs(maxIn)
        if (scale < 0.1) {
            scale = 0.1.toFloat()
        }

        /* select the quantization table and return the decoded value */
        if (cblen == 8) {
            return scale * ilbc_constants.gain_sq3Tbl[index]
        } else if (cblen == 16) {
            return scale * ilbc_constants.gain_sq4Tbl[index]
        } else if (cblen == 32) {
            return scale * ilbc_constants.gain_sq5Tbl[index]
        }
        return 0.0f
    }

    fun iCBConstruct(decvector: FloatArray,  /* (o) Decoded vector */
            decvector_idx: Int, index: IntArray,  /* (i) Codebook indices */
            index_idx: Int, gain_index: IntArray,  /* (i) Gain quantization indices */
            gain_index_idx: Int, mem: FloatArray,  /* (i) Buffer for codevector construction */
            mem_idx: Int, lMem: Int,  /* (i) Length of buffer */
            veclen: Int,  /* (i) Length of vector */
            nStages: Int /* (i) Number of codebook stages */
    ) {
        var j: Int
        var k: Int
        val gain = FloatArray(ilbc_constants.CB_NSTAGES)
        val cbvec = FloatArray(ilbc_constants.SUBL)

        /* gain de-quantization */
        gain[0] = gaindequant(gain_index[gain_index_idx + 0], 1.0f, 32)
        if (nStages > 1) {
            gain[1] = gaindequant(gain_index[gain_index_idx + 1], Math.abs(gain[0]), 16)
        }
        if (nStages > 2) {
            gain[2] = gaindequant(gain_index[gain_index_idx + 2], Math.abs(gain[1]), 8)
        }

        /*
		 * codebook vector construction and construction of total vector
		 */
        getCBvec(cbvec, mem, mem_idx, index[index_idx + 0], lMem, veclen)
        j = 0
        while (j < veclen) {
            decvector[decvector_idx + j] = gain[0] * cbvec[j]
            j++
        }
        if (nStages > 1) {
            k = 1
            while (k < nStages) {
                getCBvec(cbvec, mem, mem_idx, index[index_idx + k], lMem, veclen)
                j = 0
                while (j < veclen) {
                    decvector[decvector_idx + j] += gain[k] * cbvec[j]
                    j++
                }
                k++
            }
        }
    }
}