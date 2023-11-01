/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

/**
 * @author Jean Lorchat
 */
internal class ilbc_ulp(  /* codec settings for encoder instance */
        var mode: Int) {
    var blockl = 0
    var nsub = 0
    var nasub = 0
    var lpc_n = 0
    var no_of_bytes = 0
    var no_of_words = 0
    var state_short_len = 0
    lateinit var lsf_bits: Array<IntArray?>
    lateinit var start_bits: IntArray
    lateinit var startfirst_bits: IntArray
    lateinit var scale_bits: IntArray
    lateinit var state_bits: IntArray
    lateinit var extra_cb_index: Array<IntArray?>
    lateinit var extra_cb_gain: Array<IntArray?>
    lateinit var cb_index: Array<Array<IntArray?>>
    lateinit var cb_gain: Array<Array<IntArray?>>

    init {
        if (mode != 20 && mode != 30) {
            println("Unknown mode $mode")
        } else {
            lsf_bits = Array(6) { IntArray(ilbc_constants.ULP_CLASSES + 2) }
            start_bits = IntArray(ilbc_constants.ULP_CLASSES + 2)
            startfirst_bits = IntArray(ilbc_constants.ULP_CLASSES + 2)
            scale_bits = IntArray(ilbc_constants.ULP_CLASSES + 2)
            state_bits = IntArray(ilbc_constants.ULP_CLASSES + 2)
            extra_cb_index = Array(ilbc_constants.CB_NSTAGES) { IntArray(ilbc_constants.ULP_CLASSES + 2) }
            extra_cb_gain = Array(ilbc_constants.CB_NSTAGES) { IntArray(ilbc_constants.ULP_CLASSES + 2) }
            cb_index = Array(ilbc_constants.NSUB_MAX) { Array(ilbc_constants.CB_NSTAGES) { IntArray(ilbc_constants.ULP_CLASSES + 2) } }
            cb_gain = Array(ilbc_constants.NSUB_MAX) { Array(ilbc_constants.CB_NSTAGES) { IntArray(ilbc_constants.ULP_CLASSES + 2) } }
            if (mode == 20) {
                blockl = ilbc_constants.BLOCKL_20MS
                nsub = ilbc_constants.NSUB_20MS
                nasub = ilbc_constants.NASUB_20MS
                lpc_n = ilbc_constants.LPC_N_20MS
                no_of_bytes = ilbc_constants.NO_OF_BYTES_20MS
                no_of_words = ilbc_constants.NO_OF_WORDS_20MS
                state_short_len = ilbc_constants.STATE_SHORT_LEN_20MS
                /* ULP init */
                // iLBCenc_inst->ULP_inst=&ULP_20msTbl;
                System.arraycopy(ilbc_constants.lsf_bits_20ms, 0, lsf_bits, 0, 6)
                System.arraycopy(ilbc_constants.start_bits_20ms, 0, start_bits, 0,
                        ilbc_constants.start_bits_20ms.size)
                System.arraycopy(ilbc_constants.startfirst_bits_20ms, 0, startfirst_bits, 0,
                        ilbc_constants.startfirst_bits_20ms.size)
                System.arraycopy(ilbc_constants.scale_bits_20ms, 0, scale_bits, 0,
                        ilbc_constants.scale_bits_20ms.size)
                System.arraycopy(ilbc_constants.state_bits_20ms, 0, state_bits, 0,
                        ilbc_constants.state_bits_20ms.size)
                System.arraycopy(ilbc_constants.extra_cb_index_20ms, 0, extra_cb_index, 0,
                        ilbc_constants.CB_NSTAGES)
                System.arraycopy(ilbc_constants.extra_cb_gain_20ms, 0, extra_cb_gain, 0,
                        ilbc_constants.CB_NSTAGES)
                System
                        .arraycopy(ilbc_constants.cb_index_20ms, 0, cb_index, 0, ilbc_constants.NSUB_20MS)
                System.arraycopy(ilbc_constants.cb_gain_20ms, 0, cb_gain, 0, ilbc_constants.NSUB_20MS)
            } else if (mode == 30) {
                blockl = ilbc_constants.BLOCKL_30MS
                nsub = ilbc_constants.NSUB_30MS
                nasub = ilbc_constants.NASUB_30MS
                lpc_n = ilbc_constants.LPC_N_30MS
                no_of_bytes = ilbc_constants.NO_OF_BYTES_30MS
                no_of_words = ilbc_constants.NO_OF_WORDS_30MS
                state_short_len = ilbc_constants.STATE_SHORT_LEN_30MS
                /* ULP init */
                // ULP_inst=&ULP_30msTbl;
                System.arraycopy(ilbc_constants.lsf_bits_30ms, 0, lsf_bits, 0, 6)
                System.arraycopy(ilbc_constants.start_bits_30ms, 0, start_bits, 0,
                        ilbc_constants.start_bits_30ms.size)
                System.arraycopy(ilbc_constants.startfirst_bits_30ms, 0, startfirst_bits, 0,
                        ilbc_constants.startfirst_bits_30ms.size)
                System.arraycopy(ilbc_constants.scale_bits_30ms, 0, scale_bits, 0,
                        ilbc_constants.scale_bits_30ms.size)
                System.arraycopy(ilbc_constants.state_bits_30ms, 0, state_bits, 0,
                        ilbc_constants.state_bits_30ms.size)
                System.arraycopy(ilbc_constants.extra_cb_index_30ms, 0, extra_cb_index, 0,
                        ilbc_constants.CB_NSTAGES)
                System.arraycopy(ilbc_constants.extra_cb_gain_30ms, 0, extra_cb_gain, 0,
                        ilbc_constants.CB_NSTAGES)
                // System.out.println("nsubmax vaut: " + NSUB_MAX + " vs " + NSUB_30MS +
                // ", alors que la taille de la table est: " + cb_index_30ms.length + " vs " +
                // cb_index.length);
                System
                        .arraycopy(ilbc_constants.cb_index_30ms, 0, cb_index, 0, ilbc_constants.NSUB_30MS)
                System.arraycopy(ilbc_constants.cb_gain_30ms, 0, cb_gain, 0, ilbc_constants.NSUB_30MS)
            }

            // for (int i = 0; i < NSUB_MAX; i++) {
            // for (int j = 0; j < CB_NSTAGES; j++) {
            // for (int k = 0; k < ULP_CLASSES+2; k++) {
            // System.out.print(" " + cb_gain[i][j][k]);
            // }
            // System.out.print(" | ");
            // }
            // System.out.println("");
            // }
        }
    }
}