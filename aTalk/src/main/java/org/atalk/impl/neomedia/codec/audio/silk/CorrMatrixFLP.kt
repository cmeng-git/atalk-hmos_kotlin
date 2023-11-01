/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Correlation matrix computations for LS estimate.
 *
 * @author Dingxin Xu
 */
object CorrMatrixFLP {
    /**
     * Calculates correlation vector X'*t.
     *
     * @param x
     * x vector [L+order-1] used to create X.
     * @param x_offset
     * offset of valid data.
     * @param t
     * Target vector [L].
     * @param t_offset
     * offset of valid data.
     * @param L
     * Length of vecors.
     * @param Order
     * Max lag for correlation.
     * @param Xt
     * X'*t correlation vector [order].
     */
    fun SKP_Silk_corrVector_FLP(x: FloatArray,  /* I x vector [L+order-1] used to create X */
            x_offset: Int, t: FloatArray,  /* I Target vector [L] */
            t_offset: Int, L: Int,  /* I Length of vecors */
            Order: Int,  /* I Max lag for correlation */
            Xt: FloatArray /* O X'*t correlation vector [order] */
    ) {
        var lag: Int
        val ptr1: FloatArray
        var ptr1_offset: Int
        ptr1 = x /* Points to first sample of column 0 of X: X[:,0] */
        ptr1_offset = x_offset + Order - 1
        lag = 0
        while (lag < Order) {

            /* Calculate X[:,lag]'*t */
            Xt[lag] = InnerProductFLP.SKP_Silk_inner_product_FLP(ptr1, ptr1_offset, t,
                    t_offset, L).toFloat()
            ptr1_offset-- /* Next column of X */
            lag++
        }
    }

    /**
     * Calculates correlation matrix X'*X.
     *
     * @param x  x vector [ L+order-1 ] used to create X.
     * @param x_offset offset of valid data.
     * @param L Length of vectors.
     * @param Order Max lag for correlation.
     * @param XX X'*X correlation matrix [order x order].
     * @param XX_offset offset of valid data.
     */
    fun SKP_Silk_corrMatrix_FLP(x: FloatArray,  /* I x vector [ L+order-1 ] used to create X */
            x_offset: Int, L: Int,  /* I Length of vectors */
            Order: Int,  /* I Max lag for correlation */
            XX: FloatArray,  /* O X'*X correlation matrix [order x order] */
            XX_offset: Int) {
        var j: Int
        var lag: Int
        var energy: Double
        var ptr2_offset: Int
        val ptr1: FloatArray = x /* First sample of column 0 of X */
        val ptr1_offset: Int = x_offset + Order - 1
        energy = EnergyFLP.SKP_Silk_energy_FLP(ptr1, ptr1_offset, L) /* X[:,0]'*X[:,0] */

        // matrix_ptr( XX, 0, 0, Order ) = ( SKP_float )energy;
        XX[XX_offset + 0] = energy.toFloat()
        j = 1
        while (j < Order) {

            /* Calculate X[:,j]'*X[:,j] */
            energy += (ptr1[ptr1_offset - j] * ptr1[ptr1_offset - j] - ptr1[ptr1_offset + L - j]
                    * ptr1[ptr1_offset + L - j]).toDouble()
            // matrix_ptr( XX, j, j, Order ) = ( SKP_float )energy;
            XX[XX_offset + j * Order + j] = energy.toFloat()
            j++
        }
        val ptr2: FloatArray = x /* First sample of column 1 of X */
        ptr2_offset = x_offset + Order - 2
        lag = 1
        while (lag < Order) {

            /* Calculate X[:,0]'*X[:,lag] */
            // matrix_ptr( XX, lag, 0, Order ) = ( SKP_float )energy;
            // matrix_ptr( XX, 0, lag, Order ) = ( SKP_float )energy;
            energy = InnerProductFLP.SKP_Silk_inner_product_FLP(ptr1, ptr1_offset, ptr2,
                    ptr2_offset, L)
            /* Calculate X[:,j]'*X[:,j + lag] */
            j = 1
            while (j < Order - lag) {

                // matrix_ptr( XX, lag + j, j, Order ) = ( SKP_float )energy;
                // matrix_ptr( XX, j, lag + j, Order ) = ( SKP_float )energy;
                energy += (ptr1[ptr1_offset - j] * ptr2[ptr2_offset - j] - ptr1[ptr1_offset + L - j]
                        * ptr2[ptr2_offset + L - j]).toDouble()
                XX[XX_offset + (lag + j) * Order + j] = energy.toFloat()
                XX[XX_offset + j * Order + (lag + j)] = energy.toFloat()
                j++
            }
            ptr2_offset-- /* Next column of X */
            lag++
        }
    }
}