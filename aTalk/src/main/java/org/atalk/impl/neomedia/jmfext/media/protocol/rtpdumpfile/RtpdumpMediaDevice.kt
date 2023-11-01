/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.rtpdumpfile

import org.atalk.impl.neomedia.device.AudioMediaDeviceImpl
import org.atalk.impl.neomedia.device.AudioMediaDeviceSession
import org.atalk.impl.neomedia.device.MediaDeviceImpl
import org.atalk.impl.neomedia.device.MediaDeviceSession
import org.atalk.impl.neomedia.jmfext.media.protocol.rtpdumpfile.RtpdumpStream
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import javax.media.CaptureDeviceInfo
import javax.media.Format
import javax.media.MediaLocator
import javax.media.Processor
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat

/**
 * This class contains the method `createRtpdumpMediaDevice` that can create
 * `MediaDevice`s that will read the rtpdump file given. This static method is here for
 * convenience.
 *
 * @author Thomas Kuntz
 */
object RtpdumpMediaDevice {
    /**
     * Create a new video `MediaDevice` instance which will read the rtpdump file located at
     * `filePath`, and which will have the encoding format `encodingConstant`.
     *
     * @param filePath the location of the rtpdump file
     * @param rtpEncodingConstant the format this `MediaDevice` will have. You can find the
     * list of possible format in the class `Constants` of libjitsi (ex : Constants.VP8_RTP).
     * @param format the `MediaFormat` of the data contained in the payload of the recorded rtp
     * packet in the rtpdump file.
     * @return a `MediaDevice` that will read the rtpdump file given.
     */
    fun createRtpdumpVideoMediaDevice(filePath: String,
            rtpEncodingConstant: String?, format: MediaFormat): MediaDevice {
        /*
         * NOTE: The RtpdumpStream instance needs to know the RTP clock rate, to correctly
         * interpret the RTP timestamps. We use the frameRate field of VideoFormat, to piggyback
         * the RTP  clock rate. See RtpdumpStream#RtpdumpStream().
         * TODO: Avoid this hack...
         */
        return MediaDeviceImpl(CaptureDeviceInfo("Video rtpdump file",
                MediaLocator("rtpdumpfile:$filePath"), arrayOf<Format>(VideoFormat(
                rtpEncodingConstant,  /* Encoding */
                null,  /* Dimension */
                Format.NOT_SPECIFIED,  /* maxDataLength */
                Format.byteArray, format.clockRate.toFloat()) /* frameRate */
        )),
                MediaType.VIDEO)
    }

    /**
     * Create a new audio `MediaDevice` instance which will read the rtpdump file located at
     * `filePath`, and which will have the encoding format `format`.
     *
     *
     * Note: for proper function, `format` has to implement correctly the
     * `computeDuration(long)` method, because FMJ insists on using this to compute its own RTP timestamps.
     *
     *
     * Note: The RtpdumpStream instance needs to know the RTP clock rate to correctly interpret the
     * RTP timestamps. We use the sampleRate field of AudioFormat, or the frameRate field of
     * VideoFormat, to piggyback the RTP clock rate. See
     * [RtpdumpStream.RtpdumpStream] TODO: Avoid this hack...
     *
     * @param filePath the location of the rtpdump file
     * @param format the `AudioFormat` of the data contained in the payload of the recorded rtp
     * packet in the rtpdump file.
     * @return a `MediaDevice` that will read the rtpdump file given.
     */
    fun createRtpdumpAudioMediaDevice(filePath: String, format: AudioFormat): MediaDevice {
        return MyAudioMediaDeviceImpl(CaptureDeviceInfo("Audio rtpdump file",
                MediaLocator("rtpdumpfile:$filePath"), arrayOf<Format>(format)))
    }

    /**
     * An implementation of `AudioMediaDevice`.
     */
    private class MyAudioMediaDeviceImpl
    /**
     * Initializes a new `MyAudioMediaDeviceImpl`.
     *
     * @param captureDeviceInfo
     */(captureDeviceInfo: CaptureDeviceInfo) : AudioMediaDeviceImpl(captureDeviceInfo) {
        /**
         * {@inheritDoc}
         *
         *
         * Makes sure that the `MediaDeviceSession` created by this `AudioMediaDevice`
         * does not try to register an `AudioLevelEffect`, because this causes media to be
         * re-encoded (as `AudioLevelEffect` only works with raw audio formats).
         */
        override fun createSession(): MediaDeviceSession {
            return object : AudioMediaDeviceSession(this@MyAudioMediaDeviceImpl) {
                override fun registerLocalUserAudioLevelEffect(processor: Processor) {}
            }
        }
    }
}