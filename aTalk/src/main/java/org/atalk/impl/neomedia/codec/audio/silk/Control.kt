/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * Silk codec encoder/decoder control class.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class Control

/**
 * Class for controlling encoder operation
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_SILK_SDK_EncControlStruct {
    /**
     * (Input) Input signal sampling rate in Hertz; 8000/12000/16000/24000.
     */
    var API_sampleRate = 0

    /**
     * (Input) Maximum internal sampling rate in Hertz; 8000/12000/16000/24000.
     */
    var maxInternalSampleRate = 0

    /**
     * (Input) Number of samples per packet; must be equivalent of 20, 40, 60, 80 or 100 ms.
     */
    var packetSize = 0

    /**
     * (Input) Bitrate during active speech in bits/second; internally limited.
     */
    var bitRate = 0

    /**
     * (Inpupt) Uplink packet loss in percent (0-100).
     */
    var packetLossPercentage = 0

    /**
     * (Input) Complexity mode; 0 is lowest; 1 is medium and 2 is highest complexity.
     */
    var complexity = 0

    /**
     * (Input) Flag to enable in-band Forward Error Correction (FEC); 0/1
     */
    var useInBandFEC = 0

    /**
     * (Input) Flag to enable discontinuous transmission (DTX); 0/1
     */
    var useDTX = 0
}

/**
 * Class for controlling decoder operation and reading decoder status.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_SILK_SDK_DecControlStruct {
    /**
     * (Input) Output signal sampling rate in Hertz; 8000/12000/16000/24000.
     */
    var API_sampleRate = 0

    /**
     * (Output) Number of samples per frame.
     */
    var frameSize = 0

    /**
     * (Output) Frames per packet 1, 2, 3, 4, 5.
     */
    var framesPerPacket = 0

    /**
     * (Output) Flag to indicate that the decoder has remaining payloads internally.
     */
    var moreInternalDecoderFrames = 0

    /**
     * (Output) Distance between main payload and redundant payload in packets.
     */
    var inBandFECOffset = 0
}