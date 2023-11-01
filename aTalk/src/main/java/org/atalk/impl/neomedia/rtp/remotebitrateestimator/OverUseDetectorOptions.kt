/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.remotebitrateestimator

/**
 * Bandwidth over-use detector options. These are used to drive experimentation with bandwidth
 * estimation parameters.
 *
 * webrtc/webrtc/common_types.h
 *
 * @author Lyubomir Marinov
 */
internal class OverUseDetectorOptions {
    var initialAvgNoise = 0.0
    val initialE = arrayOf(doubleArrayOf(100.0, 0.0), doubleArrayOf(0.0, 1e-1))
    var initialOffset = 0.0
    val initialProcessNoise = doubleArrayOf(1e-10, 1e-2)
    var initialSlope = 8.0 / 512.0
    var initialThreshold = 25.0
    var initialVarNoise = 50.0
}