/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util

import java.io.File
import java.util.*

/**
 * Defines the different permit extension file.
 *
 * @author Alexandre Maillard
 * @author Dmitri Melnikov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
object SoundFileUtils {
    /**
     * Different extension of a sound file
     */
    const val wav = "wav"
    const val mid = "midi"
    const val mp2 = "mp2"
    const val mp3 = "mp3"
    const val mod = "mod"
    const val ram = "ram"
    const val wma = "wma"
    const val ogg = "ogg"
    const val gsm = "gsm"
    const val aif = "aiff"
    const val au = "au"

    /**
     * The file extension and the format of call recording to be used by default.
     */
    const val DEFAULT_CALL_RECORDING_FORMAT = mp3

    /**
     * Checks whether this file is a sound file.
     *
     * @param f `File` to check
     * @return `true` if it's a sound file, `false` otherwise
     */
    fun isSoundFile(f: File): Boolean {
        val ext = getMimeExtension(f)
        return if (ext != null) {
            ext == wma || ext == wav || ext == ram || ext == ogg || ext == mp3 || ext == mp2 || ext == mod || ext == mid || ext == gsm || ext == au
        } else false
    }

    /**
     * Checks whether this file is a sound file.
     *
     * @param f `File` to check
     * @param soundFormats The sound formats to restrict the file name
     * extension. If soundFormats is null, then every sound format defined by
     * SoundFileUtils is correct.
     * @return `true` if it's a sound file conforming to the format given
     * in parameters (if soundFormats is null, then every sound format defined
     * by SoundFileUtils is correct), `false` otherwise.
     */
    fun isSoundFile(f: File, soundFormats: Array<String>?): Boolean {
        // If there is no specific filters, then compare the file to all sound extension available.
        if (soundFormats == null) {
            return isSoundFile(f)
        } else {
            val ext = getMimeExtension(f)

            // If the file has an extension
            if (ext != null) {
                return Arrays.binarySearch(soundFormats, ext, java.lang.String.CASE_INSENSITIVE_ORDER) > -1
            }
        }
        return false
    }

    /**
     * Gets the file extension.
     * TODO: There are at least 2 other methods like this scattered around
     * the SC code, we should move them all to util package.
     *
     * @param f which wants the extension
     * @return Return the extension as a String
     */
    fun getMimeExtension(f: File): String? {
        val s = f.name
        val i = s.lastIndexOf('.')
        var ext: String? = null
        if (i > 0 && i < s.length - 1) ext = s.substring(i + 1).lowercase(Locale.getDefault())
        return ext
    }
}