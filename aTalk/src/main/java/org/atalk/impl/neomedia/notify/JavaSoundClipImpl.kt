/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.notify

import org.atalk.service.audionotifier.AbstractSCAudioClip
import org.atalk.service.audionotifier.AudioNotifierService
import org.atalk.service.audionotifier.SCAudioClip
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Constructor
import java.net.URL
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

/**
 * Implementation of SCAudioClip.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */

/**
 * Initializes a new `JavaSoundClipImpl` instance which is to play audio stored at a
 * specific `URL` using `java.applet.AudioClip`.
 *
 * @param uri the `URL` at which the audio is stored and which the new instance is to load
 * @param audioNotifier the `AudioNotifierService` which is initializing the new instance and whose
 * `mute` property/state is to be monitored by the new instance
 * @throws IOException if a `java.applet.AudioClip` could not be initialized or the audio at the
 * specified `url` could not be read
 */
class JavaSoundClipImpl(uri: String?, audioNotifier: AudioNotifierService?) : AbstractSCAudioClip(uri!!, audioNotifier!!) {
    private val audioClip: SCAudioClip?

    init {
        audioClip = createAppletAudioClip(URL(uri).openStream())
    }

    /**
     * {@inheritDoc}
     *
     *
     * Stops the `java.applet.AudioClip` wrapped by this instance.
     */
    override fun internalStop() {
        try {
            audioClip?.stop()
        } finally {
            super.internalStop()
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * Plays the `java.applet.AudioClip` wrapped by this instance.
     */
    override fun runOnceInPlayThread(): Boolean {
        return if (audioClip == null) false
        else {
            audioClip.play()
            true
        }
    }

    companion object {
        private var acConstructor: Constructor<SCAudioClip>? = null

        @Throws(ClassNotFoundException::class, NoSuchMethodException::class, SecurityException::class)
        private fun createAcConstructor(): Constructor<SCAudioClip> {
            val class1: Class<*> = try {
                Class.forName("com.sun.media.sound.JavaSoundAudioClip", true,
                        ClassLoader.getSystemClassLoader())
            } catch (cnfex: ClassNotFoundException) {
                Class.forName("sun.audio.SunAudioClip", true, null)
            }

            return class1.getConstructor(InputStream::class.java) as Constructor<SCAudioClip>
        }

        /**
         * Creates an AppletAudioClip.
         *
         * @param inputStream the audio input stream
         * @throws IOException
         */
        @Throws(IOException::class)
        private fun createAppletAudioClip(inputStream: InputStream): SCAudioClip {
            if (acConstructor == null) {
                try {
                    acConstructor = AccessController.doPrivileged(
                            PrivilegedExceptionAction {
                                createAcConstructor()
                            }
                    )
                } catch (paex: PrivilegedActionException) {
                    throw IOException("Failed to get AudioClip constructor: "
                            + paex.exception)
                }
            }

            return try {
                acConstructor!!.newInstance(inputStream)
            } catch (ex: Exception) {
                throw IOException("Failed to construct the AudioClip: $ex")
            }
        }
    }
}