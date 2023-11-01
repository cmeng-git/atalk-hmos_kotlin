/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g722

/**
 * Provides the interface to the native G722 Encode/Decode library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object G722 {
    init {
        System.loadLibrary("jng722")
    }

    external fun g722_decoder_close(decoder: Long)
    external fun g722_decoder_open(): Long
    external fun g722_decoder_process(
            decoder: Long,
            input: ByteArray?, inputOffset: Int,
            output: ByteArray?, outputOffset: Int, outputLength: Int)

    external fun g722_encoder_close(encoder: Long)
    external fun g722_encoder_open(): Long
    external fun g722_encoder_process(
            encoder: Long,
            input: ByteArray?, inputOffset: Int,
            output: ByteArray?, outputOffset: Int, outputLength: Int)
}