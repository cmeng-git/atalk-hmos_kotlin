/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

/**
 * Implements an `AudioSystem` without any devices which allows the user to select to use no
 * audio capture, notification and playback.
 *
 * @author Lyubomir Marinov
 */
class NoneAudioSystem : AudioSystem(LOCATOR_PROTOCOL) {
    @Throws(Exception::class)
    override fun doInitialize() {
    }

    override fun toString(): String {
        return "None"
    }

    companion object {
        const val LOCATOR_PROTOCOL = "none"
    }
}