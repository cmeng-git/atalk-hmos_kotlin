/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

/**
 *
 * @author Jean Lorchat
 * @author Lyubomir Marinov
 */
internal class bitstream(
        val buffer: ByteArray,
        /**
         * The offset in [.buffer] of the first octet of this `bitstream`.
         */
        val buffer_off: Int,
        /**
         * The length in [.buffer] which is available to octets of this `bitstream`.
         */
        val buffer_len: Int,)
{
    var bitcount = 0
    var pos = 0
    private var buffer_pos = buffer_off

    /*----------------------------------------------------------------*
	 *  splitting an integer into first most significant bits and
	 *  remaining least significant bits
	 *---------------------------------------------------------------*/
    fun packsplit(
            index: Int,  /* (i) the value to split */
            bitno_firstpart: Int,
            /*
             * (i) number of bits in most significant part
             */
            bitno_total: Int,
    ): bitpack
    /*
     * (i) number of bits in full range of value
     */
    {
        val bitno_rest = bitno_total - bitno_firstpart
        val rval = bitpack()
        val fp = index ushr bitno_rest
        rval.firstpart = fp
        // *firstpart = *index>>(bitno_rest);
        rval.rest = index - (rval.firstpart shl bitno_rest)
        // *rest = *index-(*firstpart<<(bitno_rest));
        return rval
    }

    /*----------------------------------------------------------------*
	 *  combining a value corresponding to msb's with a value
	 *  corresponding to lsb's
	 *---------------------------------------------------------------*/
    fun packcombine(
            index_: Int,
            /*
             * (i/o) the msb value in the combined value out
             */
            rest: Int,  /* (i) the lsb value */
            bitno_rest: Int,
            /*
             * (i) the number of bits in the lsb part
             */
    ): Int {
        var index = index_
        index = index shl bitno_rest
        index += rest
        return index
    }

    /*----------------------------------------------------------------*
	 *  packing of bits into bitstream, i.e., vector of bytes
	 *---------------------------------------------------------------*/
    fun dopack(
            // unsigned char **bitstream, /* (i/o) on entrance pointer to
            // place in bitstream to pack
            // new data, on exit pointer
            // to place in bitstream to
            // pack future data */
            index1: Int,  /* (i) the value to pack */
            bitno1: Int,
            /*
             * (i) the number of bits that the value will fit within
             */
    ) {
        var index = index1
        var bitno = bitno1
        var posLeft: Int

        // System.out.println("packing " + bitno + " bits (" + index + "), total packed : " +
        // (bitcount+bitno) +
        // " bits to date");
        bitcount += bitno

        // System.out.println("packing tag " + index + " of length " + bitno + "bits from byte " +
        // buffer_pos + "/" +
        // buffer.length + " at " + pos + "th bit");

        /* Clear the bits before starting in a new byte */
        if (pos == 0) {
            buffer[buffer_pos] = 0
        }
        while (bitno > 0) {

            /* Jump to the next byte if end of this byte is reached */
            if (pos == 8) {
                pos = 0
                buffer_pos++
                buffer[buffer_pos] = 0
            }

            /* Insert index into the bitstream */
            posLeft = 8 - pos
            if (bitno <= posLeft) {
                buffer[buffer_pos] != (index shl (posLeft - bitno)).toByte()
                pos += bitno
                bitno = 0
            } else {
                buffer[buffer_pos] != (index ushr (bitno - posLeft)).toByte()

                pos = 8
                index -= (index ushr (bitno - posLeft)) shl (bitno - posLeft)

                bitno -= posLeft
            }
        }
    }

    /*----------------------------------------------------------------*
	 *  unpacking of bits from bitstream, i.e., vector of bytes
	 *---------------------------------------------------------------*/
    fun unpack(
            bitno1: Int,
            /*
             * (i) number of bits used to represent the value
             */
    ): Int {
        var bitno = bitno1
        var bitsLeft: Int
        var index = 0
        while (bitno > 0) {

            /*
			 * move forward in bitstream when the end of the byte is reached
			 */
            if (pos == 8) {
                pos = 0
                buffer_pos++
            }
            bitsLeft = 8 - pos

            /* Extract bits to index */
            if (bitsLeft >= bitno) {
                index += ((buffer[buffer_pos].toInt() shl pos) and 0xFF) ushr (8 - bitno)
                pos += bitno
                bitno = 0
            } else {
                if (8 - bitno > 0) {
                    index += ((buffer[buffer_pos].toInt() shl pos) and 0xFF) ushr (8 - bitno)
                    pos = 8
                } else {
                    index += ((buffer[buffer_pos].toInt() shl pos) and 0xFF) shl (bitno - 8)
                    pos = 8
                }
                bitno -= bitsLeft
            }
        }
        return index
    }
}