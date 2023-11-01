/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

object SolveLSFLP {
    /**
     * Function to solve linear equation Ax = b, when A is a MxM symmetric square matrix - using LDL
     * factorisation.
     *
     * @param A
     * Symmetric square matrix, out: reg.
     * @param A_offset
     * offset of valid data.
     * @param M
     * Size of matrix
     * @param b
     * Pointer to b vector
     * @param x
     * Pointer to x solution vector
     * @param x_offset
     * offset of valid data.
     */
    fun SKP_Silk_solve_LDL_FLP(A: FloatArray,  /* I/O Symmetric square matrix, out: reg. */
            A_offset: Int, M: Int,  /* I Size of matrix */
            b: FloatArray,  /* I Pointer to b vector */
            x: FloatArray?,  /* O Pointer to x solution vector */
            x_offset: Int) {
        var i: Int
        // float L[][] = new float[MAX_MATRIX_SIZE][MAX_MATRIX_SIZE];
        // TODO:change L from two dimension to one dimension.
        val L_tmp = FloatArray(Define.MAX_MATRIX_SIZE * Define.MAX_MATRIX_SIZE)
        val T = FloatArray(Define.MAX_MATRIX_SIZE)
        val Dinv = FloatArray(Define.MAX_MATRIX_SIZE) // inverse diagonal elements of D
        assert(M <= Define.MAX_MATRIX_SIZE)
        /***************************************************
         * Factorize A by LDL such that A = L*D*(L^T), where L is lower triangular with ones on
         * diagonal
         */
        // SKP_Silk_LDL_FLP( A, M, &L[ 0 ][ 0 ], Dinv );
        SKP_Silk_LDL_FLP(A, A_offset, M, L_tmp, Dinv)
        /****************************************************
         * substitute D*(L^T) = T. ie: L*D*(L^T)*x = b => L*T = b <=> T = inv(L)*b
         */
        // SKP_Silk_SolveWithLowerTriangularWdiagOnes_FLP( &L[ 0 ][ 0 ], M, b, T );
        SKP_Silk_SolveWithLowerTriangularWdiagOnes_FLP(L_tmp, M, b, T)
        /****************************************************
         * D*(L^T)*x = T <=> (L^T)*x = inv(D)*T, because D is diagonal just multiply with 1/d_i
         */
        i = 0
        while (i < M) {
            T[i] = T[i] * Dinv[i]
            i++
        }
        /****************************************************
         * x = inv(L') * inv(D) * T
         */
        // SKP_Silk_SolveWithUpperTriangularFromLowerWdiagOnes_FLP( &L[ 0 ][ 0 ], M, T, x );
        SKP_Silk_SolveWithUpperTriangularFromLowerWdiagOnes_FLP(L_tmp, M, T, x, x_offset)
    }

    /**
     * Function to solve linear equation (A^T)x = b, when A is a MxM lower triangular, with ones on
     * the diagonal. (ie then A^T is upper triangular)
     *
     * @param L
     * Pointer to Lower Triangular Matrix
     * @param M
     * Dim of Matrix equation
     * @param b
     * b Vector
     * @param x
     * x Vector
     * @param x_offset
     * offset of valid data.
     */
    fun SKP_Silk_SolveWithUpperTriangularFromLowerWdiagOnes_FLP(L: FloatArray,  /*
																						 * (I)
																						 * Pointer
																						 * to Lower
																						 * Triangular
																						 * Matrix
																						 */
            M: Int,  /* (I) Dim of Matrix equation */
            b: FloatArray,  /* (I) b Vector */
            x: FloatArray?,  /* (O) x Vector */
            x_offset: Int) {
        // SKP_int i, j;
        // SKP_float temp;
        // const SKP_float *ptr1;
        var i: Int
        var j: Int
        var temp: Float
        // TODO:ignore const
        var ptr1: FloatArray
        var ptr1_offset: Int
        i = M - 1
        while (i >= 0) {

            // ptr1 = matrix_adr( L, 0, i, M );
            ptr1 = L
            ptr1_offset = i
            temp = 0f
            j = M - 1
            while (j > i) {
                temp += ptr1[ptr1_offset + j * M] * x!![x_offset + j]
                j--
            }
            temp = b[i] - temp
            x!![x_offset + i] = temp
            i--
        }
    }

    /**
     * Function to solve linear equation Ax = b, when A is a MxM lower triangular matrix, with ones
     * on the diagonal.
     *
     * @param L
     * Pointer to Lower Triangular Matrix
     * @param M
     * Pointer to Lower Triangular Matrix
     * @param b
     * b Vector
     * @param x
     * x Vector
     */
    fun SKP_Silk_SolveWithLowerTriangularWdiagOnes_FLP(L: FloatArray,  /*
																				 * (I) Pointer to
																				 * Lower Triangular
																				 * Matrix
																				 */
            M: Int,  /* (I) Pointer to Lower Triangular Matrix */
            b: FloatArray,  /* (I) b Vector */
            x: FloatArray /* (O) x Vector */
    ) {
        // SKP_int i, j;
        // SKP_float temp;
        // const SKP_float *ptr1;
        var i: Int
        var j: Int
        var temp: Float
        // TODO:ignore const
        var ptr1: FloatArray
        var ptr1_offset: Int
        i = 0
        while (i < M) {

            // ptr1 = matrix_adr( L, i, 0, M );
            ptr1 = L
            ptr1_offset = i * M
            temp = 0f
            j = 0
            while (j < i) {
                temp += ptr1[ptr1_offset + j] * x[j]
                j++
            }
            temp = b[i] - temp
            x[i] = temp
            i++
        }
    }

    /**
     * LDL Factorisation. Finds the upper triangular matrix L and the diagonal Matrix D (only the
     * diagonal elements returned in a vector)such that the symmetric matric A is given by A =
     * L*D*L'.
     *
     * @param A
     * Pointer to Symetric Square Matrix
     * @param A_offset
     * offset of valid data.
     * @param M
     * Size of Matrix
     * @param L
     * Pointer to Square Upper triangular Matrix
     * @param Dinv
     * Pointer to vector holding the inverse diagonal elements of D
     */
    fun SKP_Silk_LDL_FLP(A: FloatArray,  /* (I/O) Pointer to Symetric Square Matrix */
            A_offset: Int, M: Int,  /* (I) Size of Matrix */
            L: FloatArray,  /* (I/O) Pointer to Square Upper triangular Matrix */
            Dinv: FloatArray /* (I/O) Pointer to vector holding the inverse diagonal elements of D */
    ) {
        /*
		 * SKP_int i, j, k, loop_count, err = 1; SKP_float *ptr1, *ptr2; double temp,
		 * diag_min_value; SKP_float v[ MAX_MATRIX_SIZE ], D[ MAX_MATRIX_SIZE ]; // temp arrays
		 */
        var i: Int
        var j: Int
        var k: Int
        var loop_count: Int
        var err = 1
        var ptr1: FloatArray
        var ptr2: FloatArray
        var ptr1_offset: Int
        var ptr2_offset: Int
        var temp: Double
        val diag_min_value: Double
        val v = FloatArray(Define.MAX_MATRIX_SIZE)
        val D = FloatArray(Define.MAX_MATRIX_SIZE) // temp arrays
        assert(M <= Define.MAX_MATRIX_SIZE)
        diag_min_value = (DefineFLP.FIND_LTP_COND_FAC * 0.5f
                * (A[A_offset + 0] + A[A_offset + M * M - 1])).toDouble()
        loop_count = 0
        while (loop_count < M && err == 1) {
            err = 0
            j = 0
            while (j < M) {

                // ptr1 = matrix_adr( L, j, 0, M );
                // temp = matrix_ptr( A, j, j, M ); // element in row j column j
                ptr1 = L
                ptr1_offset = j * M + 0
                temp = A[A_offset + j * M + j].toDouble()
                i = 0
                while (i < j) {
                    v[i] = ptr1[ptr1_offset + i] * D[i]
                    temp -= (ptr1[ptr1_offset + i] * v[i]).toDouble()
                    i++
                }
                if (temp < diag_min_value) {
                    /* Badly conditioned matrix: add white noise and run again */
                    temp = (loop_count + 1) * diag_min_value - temp
                    i = 0
                    while (i < M) {

                        // matrix_ptr( A, i, i, M ) += ( SKP_float )temp;
                        A[A_offset + i * M + i] += temp.toFloat()
                        i++
                    }
                    err = 1
                    break
                }
                D[j] = temp.toFloat()
                Dinv[j] = (1.0f / temp).toFloat()
                // matrix_ptr( L, j, j, M ) = 1.0f;
                L[j * M + j] = 1.0f

                // ptr1 = matrix_adr( A, j, 0, M );
                // ptr2 = matrix_adr( L, j + 1, 0, M);
                ptr1 = A
                ptr1_offset = A_offset + j * M
                ptr2 = L
                ptr2_offset = (j + 1) * M
                i = j + 1
                while (i < M) {
                    temp = 0.0
                    k = 0
                    while (k < j) {
                        temp += (ptr2[ptr2_offset + k] * v[k]).toDouble()
                        k++
                    }
                    // matrix_ptr( L, i, j, M ) = ( SKP_float )( ( ptr1[ i ] - temp ) * Dinv[ j ] );
                    L[i * M + j] = ((ptr1[ptr1_offset + i] - temp) * Dinv[j]).toFloat()
                    ptr2_offset += M // go to next column
                    i++
                }
                j++
            }
            loop_count++
        }
        assert(err == 0)
    }
}