/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.speex

/**
 * Provides the interface to the native Speex library.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
object Speex {
    const val SPEEX_GET_FRAME_SIZE = 3
    const val SPEEX_MODEID_NB = 0
    const val SPEEX_MODEID_WB = 1
    const val SPEEX_MODEID_UWB = 2
    const val SPEEX_RESAMPLER_QUALITY_VOIP = 3
    const val SPEEX_SET_ENH = 0
    const val SPEEX_SET_QUALITY = 4
    const val SPEEX_SET_SAMPLING_RATE = 24

    init {
        System.loadLibrary("jnspeex")
    }

    fun assertSpeexIsFunctional() {
        speex_lib_get_mode(SPEEX_MODEID_NB)
    }

    external fun speex_bits_destroy(bits: Long)
    external fun speex_bits_init(): Long
    external fun speex_bits_nbytes(bits: Long): Int
    external fun speex_bits_read_from(bits: Long, bytes: ByteArray?, bytesOffset: Int, len: Int)
    external fun speex_bits_remaining(bits: Long): Int
    external fun speex_bits_reset(bits: Long)
    external fun speex_bits_write(bits: Long, bytes: ByteArray?, bytesOffset: Int, max_len: Int): Int
    external fun speex_decode_int(state: Long, bits: Long, out: ByteArray?, byteOffset: Int): Int
    external fun speex_decoder_ctl(state: Long, request: Int): Int
    external fun speex_decoder_ctl(state: Long, request: Int, value: Int): Int
    external fun speex_decoder_destroy(state: Long)
    external fun speex_decoder_init(mode: Long): Long
    external fun speex_encode_int(state: Long, `in`: ByteArray?, inOffset: Int, bits: Long): Int
    external fun speex_encoder_ctl(state: Long, request: Int): Int
    external fun speex_encoder_ctl(state: Long, request: Int, value: Int): Int
    external fun speex_encoder_destroy(state: Long)
    external fun speex_encoder_init(mode: Long): Long
    external fun speex_lib_get_mode(mode: Int): Long
    external fun speex_resampler_destroy(state: Long)
    external fun speex_resampler_init(nb_channels: Int, in_rate: Int, out_rate: Int, quality: Int, err: Long): Long
    external fun speex_resampler_process_interleaved_int(state: Long, `in`: ByteArray?, inOffset: Int, in_len: Int, out: ByteArray?, outOffset: Int, out_len: Int): Int
    external fun speex_resampler_set_rate(state: Long, in_rate: Int, out_rate: Int): Int
}