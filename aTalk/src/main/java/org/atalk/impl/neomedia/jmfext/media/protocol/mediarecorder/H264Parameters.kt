/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.mediarecorder

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.atalk.hmos.aTalkApp
import timber.log.Timber
import java.io.IOException
import java.io.RandomAccessFile
import java.io.Serializable
import javax.media.format.VideoFormat

/**
 * The class finds SPS and PPS parameters in mp4 video file. It is also responsible for caching them
 * in `SharedPreferences`, so that the parameters are read only once for the device.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class H264Parameters : Serializable {
    /**
     * Returns the picture parameter set.
     *
     * @return the picture parameter set.
     */
    /**
     * The picture parameter set
     */
    var pps: ByteArray? = null
        private set
    /**
     * Returns the sequence parameter set.
     *
     * @return the sequence parameter set.
     */
    /**
     * The sequence parameter set
     */
    var sps: ByteArray? = null
        private set

    /**
     * Parses the mp4 file and extracts sps and pps parameters.
     *
     * @param path the path to sample video file.
     * @throws java.io.IOException if we failed to parse and extract values.
     */
    constructor(path: String?) {
        RandomAccessFile(path, "r").use { sampleFile -> parse("", sampleFile) }
    }

    /**
     * Constructor for storage purposes.
     */
    private constructor() {}

    /**
     * Parses mp4 file in order to fins sps and pps parameters.
     *
     * @param path current boxes path(start with empty and we are looking for
     * ".moov.trak.mdia.minf.stbl.stsd" where avcC resides.
     * @param file random access sample video file
     * @throws IOException if we failed to parse video sample.
     */
    @Throws(IOException::class)
    private fun parse(path: String, file: RandomAccessFile) {
        // Looking for box stsd
        if (path == ".moov.trak.mdia.minf.stbl.stsd") {
            parseStsdBox(file)
            return
        }
        Timber.d("H264 Path: %s", path)
        val buffer = ByteArray(4)
        while (true) {
            // Read box size
            var size = readUnsignedInt32(file)
            file.read(buffer)
            // Read box name
            val name = String(buffer)
            Timber.d("Atom: %s; size: %d", name, size)
            if (name == "moov" || name == "trak" || name == "mdia" || name == "minf" || name == "stbl" || name == "stsd") {
                parse("$path.$name", file)
                return
            } else {
                if (size == 1L) {
                    size = readUnsignedInt64(file) /* largesize */ - 8
                }
                if (size == 0L) {
                    throw IOException("Invalid box size == 0")
                } else discard(file, size - (4 /* size */ + 4 /* type */))
            }
        }
    }

    /**
     * Parses the stsd box in order to fins avcC part and extract parameters.
     *
     * @param file the random access file with pointer set to stsd box position.
     * @throws IOException if we failed to extract sps and pps parameters.
     */
    @Throws(IOException::class)
    private fun parseStsdBox(file: RandomAccessFile) {
        val buffer = ByteArray(8)
        while (true) {
            var a: Int
            do {
                a = file.read()
                if (a == -1) {
                    throw IOException("End of stream")
                }
            } while (a != 'a'.code)
            file.read(buffer, 0, 3)
            if (buffer[0] == 'v'.code.toByte() && buffer[1] == 'c'.code.toByte() && buffer[2] == 'C'.code.toByte()) break
        }
        /*
         * The avcC box's structure as defined in ISO-IEC 14496-15, part 5.2.4.1.1
         *
		 *  aligned(8) class AVCDecoderConfigurationRecord {
		 *		unsigned int(8) configurationVersion = 1;
		 *		unsigned int(8) AVCProfileIndication;
		 *		unsigned int(8) profile_compatibility;
		 *		unsigned int(8) AVCLevelIndication;
		 *		bit(6) reserved = ‘111111’b;
		 *		unsigned int(2) lengthSizeMinusOne;
		 *		bit(3) reserved = ‘111’b;
		 *		unsigned int(5) numOfSequenceParameterSets;
		 *		for (i=0; i< numOfSequenceParameterSets; i++) {
		 *			unsigned int(16) sequenceParameterSetLength ;
		 *			bit(8*sequenceParameterSetLength) sequenceParameterSetNALUnit;
		 *		}
		 *		unsigned int(8) numOfPictureParameterSets;
		 *		for (i=0; i< numOfPictureParameterSets; i++) {
		 *			unsigned int(16) pictureParameterSetLength;
		 *			bit(8*pictureParameterSetLength) pictureParameterSetNALUnit;
		 *		}
		 *	}
		 *
		 */
        // Assume numOfSequenceParameterSets = 1, numOfPictureParameterSets = 1
        // Read the SPS parameter
        discard(file, 7)
        val spsLength = file.read()
        sps = ByteArray(spsLength)
        file.read(sps)
        // Read the PPS parameter
        discard(file, 2)
        val ppsLength = file.read()
        pps = ByteArray(ppsLength)
        file.read(pps)
    }

    /**
     * Advances random access file pointer by `count` bytes.
     *
     * @param file the random access file to be used.
     * @param count number of bytes to discard.
     * @throws IOException if end of stream or other error occurs.
     */
    @Throws(IOException::class)
    private fun discard(file: RandomAccessFile, count: Long) {
        file.seek(file.filePointer + count)
    }

    /**
     * Reads the unsigned int with size of `byteCount`.
     *
     * @param file the file to be used for reading.
     * @param byteCount the size of int to be read.
     * @return the unsigned int of `byteCount` length read from given `file`.
     * @throws IOException if EOF or other IO error occurs.
     */
    @Throws(IOException::class)
    private fun readUnsignedInt(file: RandomAccessFile, byteCount: Int): Long {
        var value = 0L
        for (i in byteCount - 1 downTo 0) {
            val b = file.read()
            value = if (-1 == b) throw IOException("End of stream") else {
                if (i == 7 && b and 0x80 != 0) throw IOException("Integer overflow")
                value or (b.toLong() and 0xFFL shl 8) * i
            }
        }
        return value
    }

    /**
     * Reads 32 bit unsigned int.
     *
     * @param file the file to be used.
     * @return the 32 bit unsigned int read from given `file`
     * @throws IOException if EOF or other error occurs.
     */
    @Throws(IOException::class)
    private fun readUnsignedInt32(file: RandomAccessFile): Long {
        return readUnsignedInt(file, 4)
    }

    /**
     * Reads 64 bit unsigned int.
     *
     * @param file the file to be used.
     * @return the 64 bit unsigned int read from given `file`
     * @throws IOException if EOF or other error occurs.
     */
    @Throws(IOException::class)
    private fun readUnsignedInt64(file: RandomAccessFile): Long {
        return readUnsignedInt(file, 8)
    }

    /**
     * Logs parameters stored by this instance.
     */
    fun logParameters() {
        var msg = "PPS: "
        for (b in pps!!) {
            msg += String.format("%02X", b) + ","
        }
        Timber.i("%s", msg)
        msg = "SPS: "
        for (b in sps!!) {
            msg += String.format("%02X", b) + ","
        }
        Timber.i("%s", msg)
    }

    companion object {
        /**
         * ID used for shared preferences name and key that stores the string value.
         */
        private const val STORE_ID = "org.atalk.h264parameters.value"

        /**
         * Name of shared preference key that stores video size string.
         */
        private const val VIDEO_SIZE_STORE_ID = "org.atalk.h264parameters.video_size"

        /**
         * Returns previously stored `H264Parameters` instance or `null` if nothing was
         * stored or if the video size of given format doesn't match the stored one.
         *
         * @param formatUsed format for which the H264 parameters will be retrieved.
         * @return previously stored `H264Parameters` instance or `null` if nothing was
         * stored or format video size doesn't match.
         */
        fun getStoredParameters(formatUsed: VideoFormat?): H264Parameters? {
            val config = aTalkApp.globalContext.getSharedPreferences(
                    STORE_ID, Context.MODE_PRIVATE)

            // Checks if the video size matches
            val storedSizeStr = config.getString(VIDEO_SIZE_STORE_ID, null)
            if (formatUsed!!.size.toString() != storedSizeStr) return null
            val storedValue = config.getString(STORE_ID, null)
            if (storedValue == null || storedValue.isEmpty()) return null
            val spsAndPps = storedValue.split(",")
            if (spsAndPps.size != 2) {
                Timber.e("Invalid store parameters string: %s", storedValue)
                return null
            }
            val params = H264Parameters()
            params.sps = Base64.decode(spsAndPps[0], Base64.DEFAULT)
            params.pps = Base64.decode(spsAndPps[1], Base64.DEFAULT)
            return params
        }

        /**
         * Stores given `H264Parameters` instance using `SharedPreferences`.
         *
         * @param params the `H264Parameters` instance to be stored.
         */
        fun storeParameters(params: H264Parameters, formatUsed: VideoFormat?) {
            val config = aTalkApp.globalContext.getSharedPreferences(STORE_ID, Context.MODE_PRIVATE)
            if (params.sps == null || params.pps == null) {
                return
            }
            val spsStr = Base64.encodeToString(params.sps, Base64.DEFAULT)
            val ppsStr = Base64.encodeToString(params.pps, Base64.DEFAULT)
            val storeString = "$spsStr,$ppsStr"
            config.edit().putString(STORE_ID, storeString).putString(VIDEO_SIZE_STORE_ID, formatUsed!!.size.toString()).apply()
        }
    }
}