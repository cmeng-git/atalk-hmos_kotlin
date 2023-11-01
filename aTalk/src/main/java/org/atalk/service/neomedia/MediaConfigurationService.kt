/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.util.MediaType
import java.awt.Component

/**
 * An interface that exposes the `Component`s used in media configuration user interfaces.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
interface MediaConfigurationService {
    /**
     * Returns a `Component` for audio configuration
     *
     * @return A `Component` for audio configuration
     */
    fun createAudioConfigPanel(): Component?

    /**
     * Returns a `Component` for video configuration
     *
     * @return A `Component` for video configuration
     */
    fun createVideoConfigPanel(): Component?

    /**
     * Returns a `Component` for encodings configuration (either audio or video)
     *
     * @param mediaType The type of media -- either MediaType.AUDIO or MediaType.VIDEO
     * @param encodingConfiguration The `EncodingConfiguration` instance to use.
     * @return The `Component` for encodings configuration
     */
    fun createEncodingControls(mediaType: MediaType?,
                               encodingConfiguration: EncodingConfiguration?): Component?

    /**
     * Returns the `MediaService` instance
     *
     * @return the `MediaService` instance
     */
    val mediaService: MediaService?
}