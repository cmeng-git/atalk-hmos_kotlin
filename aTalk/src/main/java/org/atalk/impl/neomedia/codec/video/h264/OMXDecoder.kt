/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.video.h264

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.Format
import javax.media.ResourceUnavailableException
import javax.media.format.RGBFormat
import javax.media.format.VideoFormat

/**
 * Implements an H.264 decoder using OpenMAX.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class OMXDecoder : AbstractCodec2("H.264 OpenMAX Decoder", VideoFormat::class.java, SUPPORTED_OUTPUT_FORMATS) {
    private var ptr: Long = 0

    /**
     * Initializes a new `OMXDecoder` instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
    }

    override fun doClose() {
        if (ptr != 0L) {
            close(ptr)
            ptr = 0
        }
    }

    /**
     * Opens this `Codec` and acquires the resources that it needs to
     * operate. All required input and/or output formats are assumed to have
     * been set on this `Codec` before `doOpen` is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * `Codec` needs to operate cannot be acquired
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        ptr = open(null)
        if (ptr == 0L) throw ResourceUnavailableException("open")
    }

    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        return BUFFER_PROCESSED_OK
    }

    private external fun close(ptr: Long)
    @Throws(ResourceUnavailableException::class)

    private external fun open(reserved: Any?): Long

    companion object {
        init {
            System.loadLibrary("jnopenmax")
        }

        /**
         * The list of `Format`s of video data supported as input by `OMXDecoder` instances.
         */
        private val SUPPORTED_INPUT_FORMATS = arrayOf<Format>(VideoFormat(Constants.H264))

        /**
         * The list of `Format`s of video data supported as output by `OMXDecoder` instances.
         */
        private val SUPPORTED_OUTPUT_FORMATS = arrayOf<Format>(
                RGBFormat(
                        null,
                        Format.NOT_SPECIFIED,
                        Format.intArray,
                        Format.NOT_SPECIFIED.toFloat(),
                        32,
                        0x000000FF, 0x0000FF00, 0x00FF0000)
        )
    }
}