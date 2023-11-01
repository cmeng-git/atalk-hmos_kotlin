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
import javax.media.Buffer
import javax.media.ResourceUnavailableException
import javax.media.format.AudioFormat

/**
 * Implements an RTP depacketizer for Adaptive Multi-Rate Wideband (AMR-WB).
 *
 * @author Lyubomir Marinov
 */
class DePacketizer : AbstractCodec2("AMR-WB RTP DePacketizer",
        AudioFormat::class.java,
        Packetizer.SUPPORTED_INPUT_FORMATS) {

    /**
     * Initializes a new <tt>DePacketizer</tt> instance.
     */
    init {
        inputFormats = Packetizer.SUPPORTED_OUTPUT_FORMATS
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
}