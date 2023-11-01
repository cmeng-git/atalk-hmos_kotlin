/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format

/**
 * The interface represents an audio format. Audio formats characterize audio streams and the
 * `AudioMediaFormat` interface gives access to some of its properties such as encoding,
 * clock rate, and number of channels.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface AudioMediaFormat : MediaFormat {
    /**
     * Returns the number of audio channels associated with this `AudioMediaFormat`.
     *
     * @return the number of audio channels associated with this `AudioMediaFormat`.
     */
    val channels: Int
}