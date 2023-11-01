/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import java.awt.Dimension

/**
 * Predefined quality preset used to specify some video settings during an existing call or when
 * starting a new call.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class QualityPreset
/**
 * Initializes a new quality preset with a specific `resolution` and an unspecified `frameRate`.
 *
 * @param resolution the resolution
 */
@JvmOverloads constructor(
        /**
         * The resolution to use.
         */
        val resolution: Dimension?,

        /**
         * The frame rate to use.
         */
        private val fameRate: Float = -1f /* unspecified */) : Comparable<QualityPreset?> {

    /**
     * Compares to presets and its dimensions.
     *
     * @param other object to compare to.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     */
    override fun compareTo(other: QualityPreset?): Int {
        return when {
            resolution == null -> -1
            other?.resolution == null -> 1
            resolution == other.resolution -> 0
            resolution.height < other.resolution.height && resolution.width < other.resolution.width -> -1
            else -> 1
        }
    }

    companion object {
        /**
         * 720p HD
         */
        val HD_QUALITY = QualityPreset(Dimension(1280, 720), 30f)

        /**
         * Low
         */
        val LO_QUALITY = QualityPreset(Dimension(320, 240), 15f)

        /**
         * SD
         */
        val SD_QUALITY = QualityPreset(Dimension(640, 480), 20f)
    }
}