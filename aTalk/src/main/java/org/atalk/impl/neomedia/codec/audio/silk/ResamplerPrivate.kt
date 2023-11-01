/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk

/**
 * class for IIR/FIR resamplers.
 *
 * @author Jing Dai
 * @author Dingxin Xu
 */
object ResamplerPrivate {
    /**
     * Number of input samples to process in the inner loop.
     */
    const val RESAMPLER_MAX_BATCH_SIZE_IN = 480
}