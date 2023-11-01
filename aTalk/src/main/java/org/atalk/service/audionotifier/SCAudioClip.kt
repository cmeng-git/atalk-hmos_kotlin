/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.audionotifier

import java.util.concurrent.Callable

/**
 * Represents an audio clip which could be played (optionally, in a loop) and stopped..
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface SCAudioClip {
    /**
     * Starts playing this audio once only. The method behaves as if [.play]
     * was invoked with a negative `loopInterval` and/or `null` `loopCondition`.
     */
    fun play()

    /**
     * Starts playing this audio. Optionally, the playback is looped.
     *
     * @param loopInterval the interval of time in milliseconds between consecutive plays of this audio. If
     * negative, this audio is played once only and `loopCondition` is ignored.
     * @param loopCondition a `Callable` which is called at the beginning of each iteration of looped
     * playback of this audio except the first one to determine whether to continue the loop.
     * If `loopInterval` is negative or `loopCondition` is `null`,
     * this audio is played once only.
     */
    fun play(loopInterval: Int, loopCondition: Callable<Boolean>?)

    /**
     * Stops playing this audio.
     */
    fun stop()

    /**
     * Determines whether this audio is started i.e. a `play` method was invoked and no
     * subsequent `stop` has been invoked yet.
     *
     * @return `true` if this audio is started; otherwise, `false`
     */
    val isStarted: Boolean
}