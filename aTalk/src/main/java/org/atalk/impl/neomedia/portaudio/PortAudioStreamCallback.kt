/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.portaudio

import java.nio.ByteBuffer

/**
 * @author Lyubomir Marinov
 */
interface PortAudioStreamCallback {
    /**
     * Callback.
     *
     * @param input
     * input `ByteBuffer`
     * @param output
     * output `ByteBuffer`
     * @return
     */
    fun callback(input: ByteBuffer?, output: ByteBuffer?): Int

    /**
     * Finished callback.
     */
    fun finishedCallback()

    companion object {
        /**
         * &quot;Abort&quot; result code.
         */
        const val RESULT_ABORT = 2

        /**
         * &quot;Complete&quot; result code.
         */
        const val RESULT_COMPLETE = 1

        /**
         * &quot;Continue&quot; result code.
         */
        const val RESULT_CONTINUE = 0
    }
}