/*
 *  Java OTR library
 *  Copyright (C) 2008-2009  Ian Goldberg, Muhaimeen Ashraf, Andrew Chung,
 *                           Can Tang
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of version 2.1 of the GNU Lesser General
 *  Public License as published by the Free Software Foundation.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
/* Ported to otr4j by devrandom */
package net.java.otr4j.crypto

import net.java.otr4j.io.OtrInputStream
import net.java.otr4j.io.OtrOutputStream
import net.java.otr4j.io.SerializationUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

object SM {
    const val EXPECT1 = 0
    const val EXPECT2 = 1
    const val EXPECT3 = 2
    const val EXPECT4 = 3
    const val EXPECT5 = 4
    const val PROG_OK = 0
    const val PROG_CHEATED = -2
    const val PROG_FAILED = -1
    const val PROG_SUCCEEDED = 1
    const val MSG1_LEN = 6
    const val MSG2_LEN = 11
    const val MSG3_LEN = 8
    const val MSG4_LEN = 3
    val MODULUS_S = BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
            + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
            + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
            + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
            + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
            + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
            + "670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF", 16)
    val MODULUS_MINUS_2 = BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
            + "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
            + "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
            + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
            + "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
            + "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
            + "670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFD", 16)
    val ORDER_S = BigInteger("7FFFFFFFFFFFFFFFE487ED5110B4611A62633145C06E0E68"
            + "948127044533E63A0105DF531D89CD9128A5043CC71A026E"
            + "F7CA8CD9E69D218D98158536F92F8A1BA7F09AB6B6A8E122"
            + "F242DABB312F3F637A262174D31BF6B585FFAE5B7A035BF6"
            + "F71C35FDAD44CFD2D74F9208BE258FF324943328F6722D9E"
            + "E1003E5C50B1DF82CC6D241B0E2AE9CD348B1FD47E9267AF"
            + "C1B2AE91EE51D6CB0E3179AB1042A95DCF6A9483B84B4B36"
            + "B3861AA7255E4C0278BA36046511B993FFFFFFFFFFFFFFFF", 16)
    val GENERATOR_S = Util.hexStringToBytes("02")
    const val MOD_LEN_BITS = 1536
    const val MOD_LEN_BYTES = 192

    /**
     * Generate a random exponent
     *
     * @return the generated random exponent.
     */
    private fun randomExponent(): BigInteger {
        val sr = SecureRandom()
        val sb = ByteArray(MOD_LEN_BYTES)
        sr.nextBytes(sb)
        return BigInteger(1, sb)
    }

    /**
     * Hash one or two BigIntegers. To hash only one BigInteger, b may be set to NULL.
     *
     * @param version the prefix to use
     * @param a The 1st BigInteger to hash.
     * @param b The 2nd BigInteger to hash.
     * @return the BigInteger for the resulting hash value.
     * @throws net.java.otr4j.crypto.SM.SMException when the SHA-256 algorithm
     * is missing or when the biginteger can't be serialized.
     */
    @Throws(SMException::class)
    fun hash(version: Int, a: BigInteger?, b: BigInteger?): BigInteger {
        return try {
            val sha256 = MessageDigest.getInstance("SHA-256")
            sha256.update(version.toByte())
            sha256.update(SerializationUtils.writeMpi(a))
            if (b != null) sha256.update(SerializationUtils.writeMpi(b))
            BigInteger(1, sha256.digest())
        } catch (ex: NoSuchAlgorithmException) {
            throw SMException("cannot find SHA-256", ex)
        } catch (ex: IOException) {
            throw SMException("cannot serialize bigint", ex)
        }
    }

    @Throws(SMException::class)
    fun serialize(ints: Array<BigInteger?>): ByteArray {
        return try {
            val out = ByteArrayOutputStream()
            val oos = OtrOutputStream(out)
            oos.writeInt(ints.size)
            for (i in ints) {
                oos.writeBigInt(i)
            }
            val b = out.toByteArray()
            oos.close()
            b
        } catch (ex: IOException) {
            throw SMException("cannot serialize bigints")
        }
    }

    @Throws(SMException::class)
    fun unserialize(bytes: ByteArray): Array<BigInteger?> {
        return try {
            val `in` = ByteArrayInputStream(bytes)
            val ois = OtrInputStream(`in`)
            val len = ois.readInt()
            if (len > 100) throw SMException("Too many ints")
            val ints = arrayOfNulls<BigInteger>(len)
            for (i in 0 until len) {
                ints[i] = ois.readBigInt()
            }
            ois.close()
            ints
        } catch (ex: IOException) {
            throw SMException("cannot unserialize bigints", ex)
        }
    }

    /**
     * Check that an BigInteger is in the right range to be a (non-unit) group element.
     *
     * @param g the BigInteger to check.
     * @return true if the BigInteger is in the right range, false otherwise.
     */
    fun checkGroupElem(g: BigInteger?): Boolean {
        return g!! < BigInteger.valueOf(2L) || g > MODULUS_MINUS_2
    }

    /**
     * Check that an BigInteger is in the right range to be a (non-zero) exponent.
     *
     * @param x The BigInteger to check.
     * @return true if the BigInteger is in the right range, false otherwise.
     */
    fun checkExpon(x: BigInteger?): Boolean {
        return x!! < BigInteger.ONE || x >= ORDER_S
    }

    /**
     * Proof of knowledge of a discrete logarithm.
     *
     * @param g the group generator
     * @param x the secret information
     * @param version the prefix to use for the hashing function
     * @return c and d.
     * @throws SMException when c and d could not be calculated
     */
    @Throws(SMException::class)
    fun proofKnowLog(g: BigInteger, x: BigInteger?, version: Int): Array<BigInteger?> {
        val r = randomExponent()
        var temp = g.modPow(r, MODULUS_S)
        val c = hash(version, temp, null)
        temp = x!!.multiply(c).mod(ORDER_S)
        val d = r.subtract(temp).mod(ORDER_S)
        val ret = arrayOfNulls<BigInteger>(2)
        ret[0] = c
        ret[1] = d
        return ret
    }

    /**
     * Verify a proof of knowledge of a discrete logarithm. Checks that c = h(g^d x^c)
     *
     * @param c c from remote party
     * @param d d from remote party
     * @param g the group generator
     * @param x our secret information
     * @param version the prefix to use
     * @return -1, 0 or 1 as our locally calculated value of c is numerically less than, equal to,
     * or greater than `c`.
     * @throws SMException when something goes wrong
     */
    @Throws(SMException::class)
    fun checkKnowLog(c: BigInteger, d: BigInteger, g: BigInteger, x: BigInteger?, version: Int): Int {
        val gd = g.modPow(d, MODULUS_S)
        val xc = x!!.modPow(c, MODULUS_S)
        val gdxc = gd.multiply(xc).mod(MODULUS_S)
        val hgdxc = hash(version, gdxc, null)
        return hgdxc.compareTo(c)
    }

    /**
     * Proof of knowledge of coordinates with first components being equal
     *
     * @param state MVN_PASS_JAVADOC_INSPECTION
     * @param r MVN_PASS_JAVADOC_INSPECTION
     * @param version MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun proofEqualCoords(state: SMState?, r: BigInteger, version: Int): Array<BigInteger?> {
        val r1 = randomExponent()
        val r2 = randomExponent()

        /* Compute the value of c, as c = h(g3^r1, g1^r1 g2^r2) */
        var temp1 = state!!.g1.modPow(r1, MODULUS_S)
        var temp2 = state.g2!!.modPow(r2, MODULUS_S)
        temp2 = temp1.multiply(temp2).mod(MODULUS_S)
        temp1 = state.g3!!.modPow(r1, MODULUS_S)
        val c = hash(version, temp1, temp2)

        /* Compute the d values, as d1 = r1 - r c, d2 = r2 - secret c */
        temp1 = r.multiply(c).mod(ORDER_S)
        val d1 = r1.subtract(temp1).mod(ORDER_S)
        temp1 = state.secret!!.multiply(c).mod(ORDER_S)
        val d2 = r2.subtract(temp1).mod(ORDER_S)
        val ret = arrayOfNulls<BigInteger>(3)
        ret[0] = c
        ret[1] = d1
        ret[2] = d2
        return ret
    }

    /**
     * Verify a proof of knowledge of coordinates with first components being equal
     *
     * @param c MVN_PASS_JAVADOC_INSPECTION
     * @param d1 MVN_PASS_JAVADOC_INSPECTION
     * @param d2 MVN_PASS_JAVADOC_INSPECTION
     * @param p MVN_PASS_JAVADOC_INSPECTION
     * @param q MVN_PASS_JAVADOC_INSPECTION
     * @param state MVN_PASS_JAVADOC_INSPECTION
     * @param version MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun checkEqualCoords(c: BigInteger, d1: BigInteger, d2: BigInteger, p: BigInteger?,
            q: BigInteger?, state: SMState?, version: Int): Int {
        /* To verify, we test that hash(g3^d1 * p^c, g1^d1 * g2^d2 * q^c) = c
         * If indeed c = hash(g3^r1, g1^r1 g2^r2), d1 = r1 - r*c,
         * d2 = r2 - secret*c.  And if indeed p = g3^r, q = g1^r * g2^secret
         * Then we should have that:
         *   hash(g3^d1 * p^c, g1^d1 * g2^d2 * q^c)
         * = hash(g3^(r1 - r*c + r*c), g1^(r1 - r*c + q*c) *
         *      g2^(r2 - secret*c + secret*c))
         * = hash(g3^r1, g1^r1 g2^r2)
         * = c
         */
        var temp2 = state!!.g3!!.modPow(d1, MODULUS_S)
        var temp3 = p!!.modPow(c, MODULUS_S)
        val temp1 = temp2.multiply(temp3).mod(MODULUS_S)
        temp2 = state.g1.modPow(d1, MODULUS_S)
        temp3 = state.g2!!.modPow(d2, MODULUS_S)
        temp2 = temp2.multiply(temp3).mod(MODULUS_S)
        temp3 = q!!.modPow(c, MODULUS_S)
        temp2 = temp3.multiply(temp2).mod(MODULUS_S)
        val cprime = hash(version, temp1, temp2)
        return c.compareTo(cprime)
    }

    /**
     * Proof of knowledge of logs with exponents being equal
     *
     * @param state MVN_PASS_JAVADOC_INSPECTION
     * @param version MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun proofEqualLogs(state: SMState?, version: Int): Array<BigInteger?> {
        val r = randomExponent()

        /* Compute the value of c, as c = h(g1^r, (Qa/Qb)^r) */
        var temp1 = state!!.g1.modPow(r, MODULUS_S)
        val temp2 = state.qab!!.modPow(r, MODULUS_S)
        val c = hash(version, temp1, temp2)

        /* Compute the d values, as d = r - x3 c */
        temp1 = state.x3!!.multiply(c).mod(ORDER_S)
        val d = r.subtract(temp1).mod(ORDER_S)
        val ret = arrayOfNulls<BigInteger>(2)
        ret[0] = c
        ret[1] = d
        return ret
    }

    /**
     * Verify a proof of knowledge of logs with exponents being equal
     *
     * @param c MVN_PASS_JAVADOC_INSPECTION
     * @param d MVN_PASS_JAVADOC_INSPECTION
     * @param r MVN_PASS_JAVADOC_INSPECTION
     * @param state MVN_PASS_JAVADOC_INSPECTION
     * @param version MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun checkEqualLogs(c: BigInteger, d: BigInteger, r: BigInteger?, state: SMState?, version: Int): Int {
        /* Here, we recall the exponents used to create g3.
         * If we have previously seen g3o = g1^x where x is unknown
         * during the DH exchange to produce g3, then we may proceed with:
         *
         * To verify, we test that hash(g1^d * g3o^c, qab^d * r^c) = c
         * If indeed c = hash(g1^r1, qab^r1), d = r1- x * c
         * And if indeed r = qab^x
         * Then we should have that:
         *   hash(g1^d * g3o^c, qab^d r^c)
         * = hash(g1^(r1 - x*c + x*c), qab^(r1 - x*c + x*c))
         * = hash(g1^r1, qab^r1)
         * = c
         */
        var temp2 = state!!.g1.modPow(d, MODULUS_S)
        var temp3 = state.g3o!!.modPow(c, MODULUS_S)
        val temp1 = temp2.multiply(temp3).mod(MODULUS_S)
        temp3 = state.qab!!.modPow(d, MODULUS_S)
        temp2 = r!!.modPow(c, MODULUS_S)
        temp2 = temp3.multiply(temp2).mod(MODULUS_S)
        val cprime = hash(version, temp1, temp2)
        return c.compareTo(cprime)
    }

    /**
     * Create first message in SMP exchange.  Input is Alice's secret value which this protocol
     * aims to compare to Bob's. The return value is a serialized BigInteger array whose elements
     * correspond to the following:
     *
     * [0] = g2a, Alice's half of DH exchange to determine g2
     * [1] = c2, [2] = d2, Alice's ZK proof of knowledge of g2a exponent
     * [3] = g3a, Alice's half of DH exchange to determine g3
     * [4] = c3, [5] = d3, Alice's ZK proof of knowledge of g3a exponent
     *
     * @param astate MVN_PASS_JAVADOC_INSPECTION
     * @param secret MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun step1(astate: SMState?, secret: ByteArray?): ByteArray {
        /* Initialize the sm state or update the secret */
        // Util.checkBytes("secret", secret);
        val secretMpi = BigInteger(1, secret)
        astate!!.secret = secretMpi
        astate.receivedQuestion = 0
        astate.x2 = randomExponent()
        astate.x3 = randomExponent()
        val msg1 = arrayOfNulls<BigInteger>(6)
        msg1[0] = astate.g1.modPow(astate.x2!!, MODULUS_S)
        var res = proofKnowLog(astate.g1, astate.x2, 1)
        msg1[1] = res[0]
        msg1[2] = res[1]
        msg1[3] = astate.g1.modPow(astate.x3!!, MODULUS_S)
        res = proofKnowLog(astate.g1, astate.x3, 2)
        msg1[4] = res[0]
        msg1[5] = res[1]
        val ret = serialize(msg1)
        astate.smProgState = PROG_OK
        return ret
    }

    /**
     * Receive the first message in SMP exchange, which was generated by step1. Input is saved
     * until the user inputs their secret information. No output.
     *
     * @param bstate MVN_PASS_JAVADOC_INSPECTION
     * @param input MVN_PASS_JAVADOC_INSPECTION
     * @param receivedQuestion MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun step2a(bstate: SMState?, input: ByteArray, receivedQuestion: Int) {

        /* Initialize the sm state if needed */
        bstate!!.receivedQuestion = receivedQuestion
        bstate.smProgState = PROG_CHEATED

        /* Read from input to find the mpis */
        val msg1 = unserialize(input)
        if (checkGroupElem(msg1[0]) || checkExpon(msg1[2])
                || checkGroupElem(msg1[3])
                || checkExpon(msg1[5])) {
            throw SMException("Invalid parameter")
        }

        /* Store Alice's g3a value for later in the protocol */
        bstate.g3o = msg1[3]

        /* Verify Alice's proofs */
        if (checkKnowLog(msg1[1]!!, msg1[2]!!, bstate.g1, msg1[0], 1) != 0 || checkKnowLog(msg1[4]!!, msg1[5]!!, bstate.g1, msg1[3], 2) != 0) {
            throw SMException("Proof checking failed")
        }

        /* Create Bob's half of the generators g2 and g3 */
        bstate.x2 = randomExponent()
        bstate.x3 = randomExponent()

        /* Combine the two halves from Bob and Alice and determine g2 and g3 */
        bstate.g2 = msg1[0]!!.modPow(bstate.x2!!, MODULUS_S)
        // Util.checkBytes("g2b", bstate.g2.getValue());
        bstate.g3 = msg1[3]!!.modPow(bstate.x3!!, MODULUS_S)
        // Util.checkBytes("g3b", bstate.g3.getValue());
        bstate.smProgState = PROG_OK
    }

    /**
     * Create second message in SMP exchange.  Input is Bob's secret value. Information from
     * earlier steps in the exchange is taken from Bob's state.  Output is a serialized mpi array
     * whose elements correspond to the following:
     *
     * [0] = g2b, Bob's half of DH exchange to determine g2
     * [1] = c2, [2] = d2, Bob's ZK proof of knowledge of g2b exponent
     * [3] = g3b, Bob's half of DH exchange to determine g3
     * [4] = c3, [5] = d3, Bob's ZK proof of knowledge of g3b exponent
     * [6] = pb, [7] = qb, Bob's halves of the (Pa/Pb) and (Qa/Qb) values
     * [8] = cp, [9] = d5, [10] = d6, Bob's ZK proof that pb, qb formed correctly
     *
     * @param bstate MVN_PASS_JAVADOC_INSPECTION
     * @param secret MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun step2b(bstate: SMState?, secret: ByteArray?): ByteArray {
        /* Convert the given secret to the proper form and store it */
        // Util.checkBytes("secret", secret);
        val secretMpi = BigInteger(1, secret)
        bstate!!.secret = secretMpi
        val msg2 = arrayOfNulls<BigInteger>(11)
        msg2[0] = bstate.g1.modPow(bstate.x2!!, MODULUS_S)
        var res = proofKnowLog(bstate.g1, bstate.x2, 3)
        msg2[1] = res[0]
        msg2[2] = res[1]
        msg2[3] = bstate.g1.modPow(bstate.x3!!, MODULUS_S)
        res = proofKnowLog(bstate.g1, bstate.x3, 4)
        msg2[4] = res[0]
        msg2[5] = res[1]

        /* Calculate P and Q values for Bob */
        val r = randomExponent()
        // BigInteger r = new BigInteger(SM.GENERATOR_S);
        bstate.p = bstate.g3!!.modPow(r, MODULUS_S)
        // Util.checkBytes("Pb", bstate.p.getValue());
        msg2[6] = bstate.p
        val qb1 = bstate.g1.modPow(r, MODULUS_S)
        // Util.checkBytes("Qb1", qb1.getValue());
        val qb2 = bstate.g2!!.modPow(bstate.secret!!, MODULUS_S)
        // Util.checkBytes("Qb2", qb2.getValue());
        // Util.checkBytes("g2", bstate.g2.getValue());
        // Util.checkBytes("secret", bstate.secret.getValue());
        bstate.q = qb1.multiply(qb2).mod(MODULUS_S)
        // Util.checkBytes("Qb", bstate.q.getValue());
        msg2[7] = bstate.q
        res = proofEqualCoords(bstate, r, 5)
        msg2[8] = res[0]
        msg2[9] = res[1]
        msg2[10] = res[2]

        /* Convert to serialized form */
        return serialize(msg2)
    }

    /**
     * Create third message in SMP exchange.  Input is a message generated by otrl_sm_step2b.
     * Output is a serialized mpi array whose elements correspond to the following:
     *
     * [0] = pa, [1] = qa, Alice's halves of the (Pa/Pb) and (Qa/Qb) values
     * [2] = cp, [3] = d5, [4] = d6, Alice's ZK proof that pa, qa formed correctly
     * [5] = ra, calculated as (Qa/Qb)^x3 where x3 is the exponent used in g3a
     * [6] = cr, [7] = d7, Alice's ZK proof that ra is formed correctly
     *
     * @param astate MVN_PASS_JAVADOC_INSPECTION
     * @param input MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun step3(astate: SMState?, input: ByteArray): ByteArray {
        /* Read from input to find the mpis */
        astate!!.smProgState = PROG_CHEATED
        val msg2 = unserialize(input)
        if (checkGroupElem(msg2[0]) || checkGroupElem(msg2[3])
                || checkGroupElem(msg2[6]) || checkGroupElem(msg2[7])
                || checkExpon(msg2[2]) || checkExpon(msg2[5])
                || checkExpon(msg2[9]) || checkExpon(msg2[10])) {
            throw SMException("Invalid Parameter")
        }
        val msg3 = arrayOfNulls<BigInteger>(8)

        /* Store Bob's g3a value for later in the protocol */
        astate.g3o = msg2[3]

        /* Verify Bob's knowledge of discreet log proofs */
        if (checkKnowLog(msg2[1]!!, msg2[2]!!, astate.g1, msg2[0], 3) != 0
                || checkKnowLog(msg2[4]!!, msg2[5]!!, astate.g1, msg2[3], 4) != 0) {
            throw SMException("Proof checking failed")
        }

        /* Combine the two halves from Bob and Alice and determine g2 and g3 */
        astate.g2 = msg2[0]!!.modPow(astate.x2!!, MODULUS_S)
        // Util.checkBytes("g2a", astate.g2.getValue());
        astate.g3 = msg2[3]!!.modPow(astate.x3!!, MODULUS_S)
        // Util.checkBytes("g3a", astate.g3.getValue());

        /* Verify Bob's coordinate equality proof */
        if (checkEqualCoords(msg2[8]!!, msg2[9]!!, msg2[10]!!, msg2[6], msg2[7], astate, 5) != 0) throw SMException("Invalid Parameter")

        /* Calculate P and Q values for Alice */
        val r = randomExponent()
        // BigInteger r = new BigInteger(SM.GENERATOR_S);
        astate.p = astate.g3!!.modPow(r, MODULUS_S)
        // Util.checkBytes("Pa", astate.p.getValue());
        msg3[0] = astate.p
        val qa1 = astate.g1.modPow(r, MODULUS_S)
        // Util.checkBytes("Qa1", qa1.getValue());
        val qa2 = astate.g2!!.modPow(astate.secret!!, MODULUS_S)
        // Util.checkBytes("Qa2", qa2.getValue());
        // Util.checkBytes("g2", astate.g2.getValue());
        // Util.checkBytes("secret", astate.secret.getValue());
        astate.q = qa1.multiply(qa2).mod(MODULUS_S)
        msg3[1] = astate.q
        // Util.checkBytes("Qa", astate.q.getValue());
        var res = proofEqualCoords(astate, r, 6)
        msg3[2] = res[0]
        msg3[3] = res[1]
        msg3[4] = res[2]

        /* Calculate Ra and proof */
        var inv = msg2[6]!!.modInverse(MODULUS_S)
        astate.pab = astate.p!!.multiply(inv).mod(MODULUS_S)
        inv = msg2[7]!!.modInverse(MODULUS_S)
        astate.qab = astate.q!!.multiply(inv).mod(MODULUS_S)
        msg3[5] = astate.qab!!.modPow(astate.x3!!, MODULUS_S)
        res = proofEqualLogs(astate, 7)
        msg3[6] = res[0]
        msg3[7] = res[1]
        val output = serialize(msg3)
        astate.smProgState = PROG_OK
        return output
    }

    /**
     * Create final message in SMP exchange.  Input is a message generated by otrl_sm_step3.
     * Output is a serialized mpi array whose elements correspond to the following:
     *
     * [0] = rb, calculated as (Qa/Qb)^x3 where x3 is the exponent used in g3b
     * [1] = cr, [2] = d7, Bob's ZK proof that rb is formed correctly
     *
     * This method also checks if Alice and Bob's secrets were the same. If so, it returns
     * NO_ERROR.  If the secrets differ, an INV_VALUE error is returned instead.
     *
     * @param bstate MVN_PASS_JAVADOC_INSPECTION
     * @param input MVN_PASS_JAVADOC_INSPECTION
     * @return MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun step4(bstate: SMState?, input: ByteArray): ByteArray {
        /* Read from input to find the mpis */
        val msg3 = unserialize(input)
        bstate!!.smProgState = PROG_CHEATED
        val msg4 = arrayOfNulls<BigInteger>(3)
        if (checkGroupElem(msg3[0]) || checkGroupElem(msg3[1])
                || checkGroupElem(msg3[5]) || checkExpon(msg3[3])
                || checkExpon(msg3[4]) || checkExpon(msg3[7])) {
            throw SMException("Invalid Parameter")
        }

        /* Verify Alice's coordinate equality proof */
        if (checkEqualCoords(msg3[2]!!, msg3[3]!!, msg3[4]!!, msg3[0], msg3[1], bstate, 6) != 0) throw SMException("Invalid Parameter")

        /* Find Pa/Pb and Qa/Qb */
        var inv = bstate.p!!.modInverse(MODULUS_S)
        bstate.pab = msg3[0]!!.multiply(inv).mod(MODULUS_S)
        inv = bstate.q!!.modInverse(MODULUS_S)
        bstate.qab = msg3[1]!!.multiply(inv).mod(MODULUS_S)

        /* Verify Alice's log equality proof */
        if (checkEqualLogs(msg3[6]!!, msg3[7]!!, msg3[5], bstate, 7) != 0) {
            throw SMException("Proof checking failed")
        }

        /* Calculate Rb and proof */
        msg4[0] = bstate.qab!!.modPow(bstate.x3!!, MODULUS_S)
        val res = proofEqualLogs(bstate, 8)
        msg4[1] = res[0]
        msg4[2] = res[1]
        val output = serialize(msg4)

        /* Calculate Rab and verify that secrets match */
        val rab = msg3[5]!!.modPow(bstate.x3!!, MODULUS_S)
        // Util.checkBytes("rab", rab.getValue());
        // Util.checkBytes("pab", bstate.pab.getValue());
        val comp = rab.compareTo(bstate.pab!!)
        bstate.smProgState = if (comp != 0) PROG_FAILED else PROG_SUCCEEDED
        return output
    }

    /**
     * Receives the final SMP message, which was generated in otrl_sm_step. This method checks if
     * Alice and Bob's secrets were the same. If so, it returns NO_ERROR. If the secrets differ,
     * an INV_VALUE error is returned instead.
     *
     * @param astate MVN_PASS_JAVADOC_INSPECTION
     * @param input MVN_PASS_JAVADOC_INSPECTION
     * @throws SMException MVN_PASS_JAVADOC_INSPECTION
     */
    @Throws(SMException::class)
    fun step5(astate: SMState?, input: ByteArray) {
        /* Read from input to find the mpis */
        val msg4 = unserialize(input)
        astate!!.smProgState = PROG_CHEATED
        if (checkGroupElem(msg4[0]) || checkExpon(msg4[2])) {
            throw SMException("Invalid Parameter")
        }

        /* Verify Bob's log equality proof */
        if (checkEqualLogs(msg4[1]!!, msg4[2]!!, msg4[0], astate, 8) != 0) throw SMException("Invalid Parameter")

        /* Calculate Rab and verify that secrets match */
        val rab = msg4[0]!!.modPow(astate.x3!!, MODULUS_S)
        // Util.checkBytes("rab", rab.getValue());
        // Util.checkBytes("pab", astate.pab.getValue());
        val comp = rab.compareTo(astate.pab!!)
        //		if (comp != 0) {
//			System.out.println("checking failed");
//		}
        astate.smProgState = if (comp != 0) PROG_FAILED else PROG_SUCCEEDED
    }

    // ***************************************************
    // Session stuff - perhaps factor out
    @Throws(SMException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val res = MODULUS_MINUS_2.subtract(MODULUS_S).mod(MODULUS_S)
        val ss = Util.bytesToHexString(res.toByteArray())
        println(ss)
        val secret1 = "abcdef".toByteArray()
        val a = SMState()
        val b = SMState()
        val msg1 = step1(a, secret1)
        step2a(b, msg1, 123)
        val msg2 = step2b(b, secret1)
        val msg3 = step3(a, msg2)
        val msg4 = step4(b, msg3)
        step5(a, msg4)
    }

    class SMState {
        var secret: BigInteger? = null
        var x2: BigInteger? = null
        var x3: BigInteger? = null
        var g1: BigInteger
        var g2: BigInteger? = null
        var g3: BigInteger? = null
        var g3o: BigInteger? = null
        var p: BigInteger? = null
        var q: BigInteger? = null
        var pab: BigInteger? = null
        var qab: BigInteger? = null
        var nextExpected = 0
        var receivedQuestion = 0
        var smProgState: Int
        var approved: Boolean
        var asked: Boolean

        /**
         * Ctor.
         */
        init {
            g1 = BigInteger(1, GENERATOR_S)
            smProgState = PROG_OK
            approved = false
            asked = false
        }
    }

    class SMException : Exception {
        constructor() : super() {}
        constructor(cause: Throwable?) : super(cause) {}
        constructor(message: String?) : super(message) {}
        constructor(message: String?, cause: Throwable?) : super(message, cause) {}

        companion object {
            private const val serialVersionUID = 1L
        }
    }
}