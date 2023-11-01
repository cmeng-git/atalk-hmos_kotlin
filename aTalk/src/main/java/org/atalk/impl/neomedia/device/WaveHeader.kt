/*
 * Copyright (C) 2011 Jacquet Wong
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.impl.neomedia.device

import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Wave header
 */
class WaveHeader {
    var isValid = false
        private set
    private var chunkId: String? = null

    // unsigned 4-bit, little endian
    private var chunkSize = 0L

    var format: String? = null
        private set

    private var subChunk1Id: String? = null

    // unsigned 4-bit, little endian
    private var subChunk1Size = 0L

    // unsigned 2-bit, little endian
    var audioFormat = 0
        private set

    // unsigned 2-bit, little endian
    var channels = 0
        private set

    // unsigned 4-bit, little endian
    var sampleRate = 0L
        private set

    // unsigned 4-bit, little endian
    private var byteRate = 0L

    // unsigned 2-bit, little endian
    private var blockAlign = 0

    // unsigned 2-bit, little endian
    var bitsPerSample  = 0
        private set

    private var subChunk2Id: String? = null

    // unsigned 4-bit, little endian
    private var subChunk2Size = 0L

    constructor(filename: String?) {
        try {
            val inputStream = FileInputStream(filename)
            isValid = loadHeader(inputStream)
            inputStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    constructor(inputStream: InputStream) {
        isValid = loadHeader(inputStream)
    }

    /*
	 * WAV File Specification https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
	 */
    private fun loadHeader(inputStream: InputStream): Boolean {
        val headerBuffer = ByteArray(44) // wav header is 44 bytes
        try {
            inputStream.read(headerBuffer)

            // read header
            var pointer = 0
            chunkId = String(byteArrayOf(headerBuffer[pointer++], headerBuffer[pointer++],
                    headerBuffer[pointer++], headerBuffer[pointer++]))
            // little endian
            chunkSize = (headerBuffer[pointer++].toInt() and 0xff).toLong() or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 8
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 16
                    ) or (headerBuffer[pointer++].toInt() and (0xff shl 24)).toLong()
            format = String(byteArrayOf(headerBuffer[pointer++], headerBuffer[pointer++],
                    headerBuffer[pointer++], headerBuffer[pointer++]))
            subChunk1Id = String(byteArrayOf(headerBuffer[pointer++], headerBuffer[pointer++],
                    headerBuffer[pointer++], headerBuffer[pointer++]))
            subChunk1Size = (headerBuffer[pointer++].toInt() and 0xff).toLong() or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 8
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 16
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 24)
            audioFormat = (headerBuffer[pointer++].toInt() and 0xff or (headerBuffer[pointer++].toInt() and 0xff shl 8))
            channels = (headerBuffer[pointer++].toInt() and 0xff or (headerBuffer[pointer++].toInt() and 0xff shl 8))
            sampleRate = (headerBuffer[pointer++].toInt() and 0xff).toLong() or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 8
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 16
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 24)
            byteRate = (headerBuffer[pointer++].toInt() and 0xff).toLong() or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 8
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 16
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 24)
            blockAlign = (headerBuffer[pointer++].toInt() and 0xff or (headerBuffer[pointer++].toInt() and 0xff shl 8))
            bitsPerSample = (headerBuffer[pointer++].toInt() and 0xff or (headerBuffer[pointer++].toInt() and 0xff shl 8))
            subChunk2Id = String(byteArrayOf(headerBuffer[pointer++], headerBuffer[pointer++],
                    headerBuffer[pointer++], headerBuffer[pointer++]))
            subChunk2Size = (headerBuffer[pointer++].toInt() and 0xff).toLong() or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 8
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 16
                    ) or ((headerBuffer[pointer++].toInt() and 0xff).toLong() shl 24)
            // end read header

            // the inputStream should be closed outside this method
            // dis.close();
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        // check the format is support
        return chunkId!!.uppercase(Locale.getDefault()) == RIFF_HEADER
                && format!!.uppercase(Locale.getDefault()) == WAVE_HEADER && audioFormat == 1
    }

    fun length(): Float {
        return subChunk2Size.toFloat() / byteRate
    }

    fun timestamp(): String {
        val totalSeconds = length()
        val second = totalSeconds % 60
        val minute = totalSeconds.toInt() / 60 % 60
        val hour = (totalSeconds / 3600).toInt()
        val sb = StringBuffer()
        if (hour > 0) {
            sb.append("$hour:")
        }
        if (minute > 0) {
            sb.append("$minute:")
        }
        sb.append(second)
        return sb.toString()
    }

    override fun toString(): String {
        val sb = StringBuffer()
        sb.append("chunkId: $chunkId")
        sb.append("\n")
        sb.append("chunkSize: $chunkSize")
        sb.append("\n")
        sb.append("format: $format")
        sb.append("\n")
        sb.append("subChunk1Id: $subChunk1Id")
        sb.append("\n")
        sb.append("subChunk1Size: $subChunk1Size")
        sb.append("\n")
        sb.append("audioFormat: $audioFormat")
        sb.append("\n")
        sb.append("channels: $channels")
        sb.append("\n")
        sb.append("sampleRate: $sampleRate")
        sb.append("\n")
        sb.append("byteRate: $byteRate")
        sb.append("\n")
        sb.append("blockAlign: $blockAlign")
        sb.append("\n")
        sb.append("bitsPerSample: $bitsPerSample")
        sb.append("\n")
        sb.append("subChunk2Id: $subChunk2Id")
        sb.append("\n")
        sb.append("subChunk2Size: $subChunk2Size")
        sb.append("\n")
        sb.append("length: " + timestamp())
        return sb.toString()
    }

    companion object {
        const val RIFF_HEADER = "RIFF"
        const val WAVE_HEADER = "WAVE"
        const val FMT_HEADER = "fmt "
        const val DATA_HEADER = "data"
    }
}