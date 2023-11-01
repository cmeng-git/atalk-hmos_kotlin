/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import android.content.Context
import android.net.Uri
import org.atalk.hmos.aTalkApp
import org.atalk.impl.androidresources.AndroidResourceServiceImpl
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.InputStream
import javax.media.format.AudioFormat

/**
 * Utils that obtain audio resource input stream and its format.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
object AudioStreamUtils {
    /**
     * Obtains an audio input stream from the URL provided.
     *
     * @param uri a valid url to a sound resource.
     * @return the input stream to audio data.
     * @throws java.io.IOException if an I/O exception occurs
     */
    @JvmStatic
    fun getAudioInputStream(uri: String): InputStream? {
        var audioStream: InputStream? = null
        try {
            // Context context = ServiceUtils.getService(NeomediaActivator.getBundleContext(), OSGiService.class);
            val context = aTalkApp.globalContext

            // As Android resources don't use file extensions, remove it if there is one.
            val lastPathSeparator = uri.lastIndexOf('/')
            var extensionStartIx = 0
            var resourceUri: String
            if (lastPathSeparator > -1 && uri.lastIndexOf('.').also { extensionStartIx = it } > lastPathSeparator) resourceUri = uri.substring(0, extensionStartIx) else resourceUri = uri

            // Must convert to proper androidResource for content access to aTalk raw/*.wav
            if (uri.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
                resourceUri = "android.resource://" + context.packageName + "/" + resourceUri
            }
            audioStream = context.contentResolver.openInputStream(Uri.parse(resourceUri))
        } catch (t: FileNotFoundException) {
            Timber.e(t, "Error opening file: %s", uri)
        }
        return audioStream
    }

    /**
     * Returns the audio format for the WAV `InputStream`. Or null if format cannot be obtained.
     *
     * @param audioInputStream the input stream.
     * @return the format of the audio stream.
     */
    @JvmStatic
    fun getFormat(audioInputStream: InputStream): AudioFormat {
        val waveHeader = WaveHeader(audioInputStream)
        return AudioFormat(AudioFormat.LINEAR,
                waveHeader.sampleRate.toDouble(), waveHeader.bitsPerSample, waveHeader.channels)
    }
}