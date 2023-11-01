/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * The `MediaUseCase` enumeration contains a list of use-cases for media related. Typically
 * it can be used to differentiate a video call (video comes from webcam) and desktop session (video
 * comes from desktop).
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
enum class MediaUseCase
/**
 * Constructor.
 *
 * @param mediaUseCase
 * type of `MediaUseCase` we'd like to create
 */
(
        /**
         * Name of this `MediaUseCase`.
         */
        private val mediaUseCase: String) {

    /**
     * Represents any usecase.
     */
    ANY("any"),

    /**
     * Represents a standard call (voice/video).
     */
    CALL("call"),

    /**
     * Represents a desktop streaming/sharing session.
     */
    DESKTOP("desktop");

    /**
     * Returns the name of this `MediaUseCase`.
     *
     * @return the name of this `MediaUseCase`.
     */
    override fun toString(): String {
        return mediaUseCase
    }

    companion object {
        /**
         * Returns a `MediaUseCase` value corresponding to the specified `mediaUseCase`.
         *
         * @param mediaUseCase
         * the name that we'd like to parse.
         * @return a `MediaUseCase` value corresponding to the specified `mediaUseCase`.
         *
         * @throws IllegalArgumentException
         * in case `mediaUseCase` is not a valid or currently supported media usecase.
         */
        @Throws(IllegalArgumentException::class)
        fun parseString(mediaUseCase: String): MediaUseCase {
            if (CALL.toString() == mediaUseCase) return CALL
            if (ANY.toString() == mediaUseCase) return ANY
            if (DESKTOP.toString() == mediaUseCase) return DESKTOP
            throw IllegalArgumentException(mediaUseCase
                    + " is not a currently supported MediaUseCase")
        }
    }
}