/*
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
package org.atalk.impl.neomedia.codec.audio.amrwb

import org.atalk.impl.neomedia.codec.AbstractCodec2
import org.atalk.service.neomedia.codec.Constants
import javax.media.Buffer
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements an RTP packetizer for Adaptive Multi-Rate Wideband (AMR-WB).
 *
 * @author Lyubomir Marinov
 */
class Packetizer : AbstractCodec2("AMR-WB RTP Packetizer",
        AudioFormat::class.java,
        SUPPORTED_OUTPUT_FORMATS) {
    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    init {
        inputFormats = SUPPORTED_INPUT_FORMATS
    }

    /**
     * {@inheritDoc}
     */
    override fun doClose() {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Throws(ResourceUnavailableException::class)
    override fun doOpen() {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    override fun doProcess(inBuf: Buffer, outBuf: Buffer): Int {
        // TODO Auto-generated method stub
        return 0
    }

    companion object {
        /**
         * The list of <tt>Format</tt>s of audio data supported as input by <tt>Packetizer</tt> instances.
         */
        val SUPPORTED_INPUT_FORMATS = arrayOf(AudioFormat(Constants.AMR_WB))

        /**
         * The list of <tt>Format</tt>s of audio data supported as output by <tt>Packetizer</tt> instances.
         */
        val SUPPORTED_OUTPUT_FORMATS = arrayOf(AudioFormat(Constants.AMR_WB_RTP))
    }
}