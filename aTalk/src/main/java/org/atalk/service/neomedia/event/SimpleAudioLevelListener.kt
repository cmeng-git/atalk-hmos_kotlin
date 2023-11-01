/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

/**
 * A very simple listener that delivers `int` values every time the audio level of an audio
 * source changes.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface SimpleAudioLevelListener {
    /**
     * Indicates a new audio level for the source that this listener was registered with.
     *
     * @param level
     * the new/current level of the audio source that this listener is registered with.
     */
    fun audioLevelChanged(level: Int)

    companion object {
        /**
         * The maximum level that can be reported for a participant in a conference. Level values should
         * be distributed between `MAX_LEVEL` and [.MIN_LEVEL] in a way that would appear
         * uniform to users.
         *
         *
         * **Note**: The value of `127` is specifically chosen as the value of
         * `MAX_LEVEL` because (1) we transport the levels within RTP and it gives us a signed
         * `byte` for it, and (2) the range of
         * `[0, 127]` is pretty good to directly express the sound pressure
         * level decibels as heard by humans in Earth's atmosphere.
         *
         */
        const val MAX_LEVEL = 127

        /**
         * The maximum (zero) level that can be reported for a participant in a conference. Level values
         * should be distributed among [.MAX_LEVEL] and `MIN_LEVEL` in a way that would
         * appear uniform to users.
         *
         *
         * **Note**: The value of `0` is specifically chosen as the value of
         * `MIN_LEVEL` because (1) we transport the levels within RTP and it gives us a signed
         * `byte` for it, and (2) the range of
         * `[0, 127]` is pretty good to directly express the sound pressure
         * level decibels as heard by humans in Earth's atmosphere.
         *
         */
        const val MIN_LEVEL = 0
    }
}