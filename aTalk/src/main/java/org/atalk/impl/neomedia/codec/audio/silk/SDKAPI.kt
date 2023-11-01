/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 *
 * @author Jing Dai
 */
object SDKAPI {
    const val SILK_MAX_FRAMES_PER_PACKET = 5
}

/**
 * Struct for TOC (Table of Contents).
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
class SKP_Silk_TOC_struct {
    var framesInPacket /* Number of 20 ms frames in packet */ = 0
    var fs_kHz /* Sampling frequency in packet */ = 0
    var inbandLBRR /* Does packet contain LBRR information */ = 0
    var corrupt /* Packet is corrupt */ = 0
    var vadFlags = IntArray(SDKAPI.SILK_MAX_FRAMES_PER_PACKET) /* VAD flag for each frame in packet */
    var sigtypeFlags = IntArray(SDKAPI.SILK_MAX_FRAMES_PER_PACKET) /*
																	 * Signal type for each frame in
																	 * packet
																	 */
}