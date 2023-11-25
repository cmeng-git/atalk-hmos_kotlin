/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2022 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.persistance

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.atalk.hmos.aTalkApp
import timber.log.Timber
import java.io.*
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * File Backend utilities
 *
 * @author Eng Chong Meng
 */
object FileBackend {
    private const val FILE_PROVIDER = ".files"
    const val MEDIA_TYPE_IMAGE = 1
    const val MEDIA_TYPE_VIDEO = 2

    /**
     * The default buffer size to use.
     */
    private const val DEFAULT_BUFFER_SIZE = 1024 * 4

    // android-Q accessible path to apk is: /storage/emulated/0/Android/data/org.atalk.hmos/files
    var FP_aTALK = "/aTalk"
    var EXPROT_DB = "EXPORT_DB"
    var MEDIA = "Media"
    var MEDIA_CAMERA = "Media/Camera"
    var MEDIA_DOCUMENT = "Media/Documents"
    var MEDIA_VOICE_RECEIVE = "Media/Voice_Receive"
    var MEDIA_VOICE_SEND = "Media/Voice_Send"
    @JvmField
    var TMP = "tmp"
    fun IsExternalStorageWritable(): Boolean {
        // boolean mExternalStorageAvailable = false;
        val mExternalStorageWriteable: Boolean
        val state = Environment.getExternalStorageState()
        mExternalStorageWriteable = if (Environment.MEDIA_MOUNTED == state) {
            // We can read and write the media
            /* mExternalStorageAvailable = */
            true
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY == state) {
            // We can only read the media
            // mExternalStorageAvailable = true;
            false
        } else {
            // Something else is wrong. It may be one of many other states, but all we need
            // to know is we can neither read nor write
            /* mExternalStorageAvailable = */
            false
        }
        return mExternalStorageWriteable
    }

    /**
     * Copies a file or directory to a new location. If copying a directory, the entire contents
     * of the directory are copied recursively.
     *
     * @param srcPath the full path of the file or directory to be copied
     * @param targetPath the full path of the target directory to which the file or directory should be copied
     * @param subFolder the new name of the file or directory
     * @throws IllegalArgumentException if an invalid source or destination path is provided
     * @throws FileNotFoundException if the source path cannot be found on the file system
     * @throws SecurityException if unable to create the new file or directory specified by destination path
     * @throws IOException if an attempt is made to copy the contents of a directory into itself, or if the
     * source and destination paths are identical, or if a general error occurs
     */
    @Throws(IllegalArgumentException::class, SecurityException::class, IOException::class)
    fun copyRecursive(srcPath: File?, targetPath: File?, subFolder: String?) {
        // ensure source exists
        if (srcPath == null || !srcPath.exists()) {
            throw FileNotFoundException("Source Path not found: $srcPath")
        }

        // ensure target is a directory if exists
        if (targetPath == null || targetPath.exists() && !targetPath.isDirectory) {
            throw FileNotFoundException("Target is null or not a directory: $targetPath")
        }
        // Form full destination path
        var dstPath = targetPath
        if (subFolder != null) dstPath = File(targetPath, subFolder)

        // source is a directory
        if (srcPath.isDirectory) {
            // can't copy directory into itself
            // file:///SDCard/tmp/ --> file:///SDCard/tmp/tmp/ ==> NO!
            // file:///SDCard/tmp/ --> file:///SDCard/tmp/ ==> NO!
            // file:///SDCard/tmp/ --> file:///SDCard/tmp2/ ==> OK
            if (dstPath == srcPath) {
                throw IOException("Cannot copy directory into itself.")
            }

            // create the destination directory if non-exist
            if (!dstPath.exists() && !dstPath.mkdir()) throw IOException("Cannot create destination directory.")

            // recursively copy directory contents
            val files = srcPath.listFiles()
            for (file in files) {
                val fileName = file.name
                copyRecursive(File(srcPath, fileName), dstPath, fileName)
            }
        } else {
            // can't copy file onto itself
            if (dstPath == srcPath) {
                throw IOException("Cannot copy file onto itself.")
            }

            // replace the existing file, but not directory
            if (dstPath.exists()) {
                if (dstPath.isDirectory) {
                    throw IOException("Cannot overwrite existing directory.")
                } else if (!dstPath.delete()) throw IOException("Cannot delete old file. $dstPath")
            }
            if (!dstPath.exists() && !dstPath.createNewFile()) throw IOException("Cannot create file to copy. $dstPath")
            try {
                val inputStream = FileInputStream(srcPath)
                val outputStream = FileOutputStream(dstPath)
                copy(inputStream, outputStream) // org.apache.commons.io
                inputStream.close()
                outputStream.close()
            } catch (e: Exception) { // IOException
                e.printStackTrace()
            }
        }
    }

    /**
     * Deletes the specified file or directory from file system. If the specified path is a
     * directory, the deletion is recursive.
     *
     * @param filePath full path of file or directory to be deleted
     * @throws IOException throws exception if any
     */
    @Throws(IOException::class)
    fun deleteRecursive(filePath: File?) {
        if (filePath != null && filePath.exists()) {
            // If the file is a directory, we will recursively call deleteRecursive on it.
            if (filePath.isDirectory) {
                val files = filePath.listFiles()
                for (file in files) {
                    deleteRecursive(file)
                }
            }
            // Finally, delete the root directory, after all the files in the directory have been deleted.
            if (!filePath.delete()) {
                throw IOException("Could not deleteRecursive: $filePath")
            }
        }
    }

    /**
     * Copy bytes from a large (over 2GB) `InputStream` to an `OutputStream`.
     *
     * This method buffers the input internally, so there is no need to use a `BufferedInputStream`.
     *
     * @param input the `InputStream` to read from
     * @param output the `OutputStream` to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     * @since Commons IO 1.3
     */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = 0L
        var n: Int
        while (-1 != input.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
            count += n.toLong()
        }
        return count
    }

    /**
     * Default aTalk downloadable directory i.e. Download/aTalk
     *
     * @param subFolder subFolder to be created under aTalk downloadable directory, null if root
     * @return aTalk default directory
     */
    @JvmStatic
    fun getaTalkStore(subFolder: String?, createNew: Boolean): File {
        var filePath = FP_aTALK
        if (!TextUtils.isEmpty(subFolder)) filePath += File.separator + subFolder

        // https://developer.android.com/reference/android/os/Environment#getExternalStorageDirectory()
        // File atalkDLDir = aTalkApp.globalContext.getExternalFilesDir(filePath);
        // File atalkDLDir = new File(Environment.getExternalStorageDirectory(), filePath);
        val atalkDLDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + filePath)
        if (createNew && !atalkDLDir.exists() && !atalkDLDir.mkdirs()) {
            Timber.e("Could not create aTalk folder: %s", atalkDLDir)
        }
        return atalkDLDir
    }

    /**
     * Create a new File for saving image or video captured with camera
     */
    fun getOutputMediaFile(type: Int): File? {
        val aTalkMediaDir = getaTalkStore(MEDIA_CAMERA, true)

        // Create a media file name
        var mediaFile: File? = null
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = File(aTalkMediaDir, "IMG_$timeStamp.jpg")
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = File(aTalkMediaDir, "VID_$timeStamp.mp4")
        }
        return mediaFile
    }

    /**
     * Get the correct Uri path according to android API
     *
     * @param context context
     * @param file the specific file path
     * @return the actual Uri
     */
    fun getUriForFile(context: Context, file: File?): Uri {
        try {
            val packageId = context.packageName
            return FileProvider.getUriForFile(context, packageId + FILE_PROVIDER, file!!)
        } catch (e: IllegalArgumentException) {
            throw SecurityException(e)
        }
    }

    /**
     * To make a best guess if a given link string is a file download link address
     *
     * @param link a string to be checked for file link
     * @return true if the string is likely to be a Http File Download link
     */
    fun isHttpFileDnLink(link: String?): Boolean {
        if (link != null) {
            if (link.matches(Regex("(?s)^aesgcm:.*"))) {
                return true
            }

            // Use URLUtil.isValidUrl(link)?
            // boolean isValidLink = URLUtil.isHttpUrl(link) || URLUtil.isHttpsUrl(link);
            if (link.matches(Regex("(?s)^http[s]:.*")) && !link.contains("\\s")) {
                // return false if there is no ext or 2 < ext.length() > 5
                val ext = link.replace("(?s)^.+/[\\w-]+\\.([\\w-]{2,5})$".toRegex(), "$1")
                return if (ext.length > 5) {
                    false
                } else {
                    // web common extensions: asp, cgi, [s]htm[l], js, php, pl
                    // android asp, cgi shtm, shtml, js, php, pl => (mimeType == null)
                    !ext.matches(Regex("s*[achjp][sgthl][pim]*[l]*"))
                }
            }
        }
        return false
    }

    /**
     * To guess the mime type of the given uri using the mimeMap or from path name
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @param mime a reference mime type (from attachment)
     * @return mime type of the given uri
     */
    fun getMimeType(ctx: Context, uri: Uri, mime: String?): String? {
        Timber.d("guessMimeTypeFromUriAndMime %s and mimeType = %s", uri, mime)
        if (mime == null || mime == "application/octet-stream") {
            val guess = getMimeType(ctx, uri)
            return guess ?: mime
        }
        return getMimeType(ctx, uri)
    }

    /**
     * To guess the mime type of the given uri using the mimeMap or from path name
     * Unicode uri string must be urlEncoded for android getFileExtensionFromUrl(),
     * else alwyas return ""
     *
     * Note: android returns *.mp3 file as audio/mpeg. See https://tools.ietf.org/html/rfc3003;
     * and returns as video/mpeg on re-submission with *.mpeg
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return mime type of the given uri
     */
    fun getMimeType(ctx: Context?, uri: Uri): String? {
        var mimeType: String? = null
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            val cr = ctx?.contentResolver
            mimeType = cr?.getType(uri)
        } else {
            val fileExtension: String? = try {
                // Need to encode unicode uri before proceed; revert all "%3A", "%2F" and "+" to ":", "/" and "%20"
                val uriEncoded = URLEncoder.encode(uri.toString(), "UTF-8")
                        .replace("%3A".toRegex(), ":")
                        .replace("%2F".toRegex(), "/")
                        .replace("\\+".toRegex(), "%20")
                MimeTypeMap.getFileExtensionFromUrl(uriEncoded)
            } catch (e: UnsupportedEncodingException) {
                Timber.w("urlEncode exception: %s", e.message)
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            }
            if (fileExtension != null) mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.getDefault()))
        }

        // Make a guess base on filePath
        if (mimeType == null || mimeType == "application/octet-stream") {
            val fileName = uri.path
            if (fileName != null) {
                if (fileName.contains("image")) mimeType = "image/*" else if (fileName.contains("video")) mimeType = "video/*" else if (fileName.contains("audio")) mimeType = "audio/*"

                // Make a last ditch to guess ContentType From FileInputStream
                if (mimeType == null) {
                    try {
                        val `is` = FileInputStream(fileName)
                        val tmp = guessContentTypeFromStream(`is`)
                        if (tmp != null) mimeType = tmp
                    } catch (ignore: IOException) {
                    }
                }
            }
        }
        return mimeType
    }

    /**
     * Check if the file has video or image media content
     *
     * @param file File to be check
     * @return true if the given file has media content
     */
    fun isMediaFile(file: File): Boolean {
        val ctx = aTalkApp.globalContext
        val uri = getUriForFile(ctx, file)
        val mimeType = getMimeType(ctx, uri)

        // mimeType is null if file contains no ext on old android or else "application/octet-stream"
        if (TextUtils.isEmpty(mimeType)) {
            Timber.e("File mimeType is null: %s", file.path)
            return false
        }
        // Android returns 3gp and video/3gp
        return !mimeType!!.contains("3gp") && (mimeType.contains("image") || mimeType.contains("video"))
    }

    /**
     * cmeng: modified from URLConnection class
     *
     * Try to determine the type of input stream based on the characters at the beginning of the input stream.
     * This method  be used by subclasses that override the `getContentType` method.
     *
     * Ideally, this routine would not be needed, but many `http` servers return the incorrect content type;
     * in addition, there are many nonstandard extensions. Direct inspection of the bytes to determine the content
     * type is often more accurate than believing the content type claimed by the `http` server.
     *
     * @param is an input stream that supports marks.
     * @return a guess at the content type, or `null` if none can be determined.
     * @throws IOException if an I/O error occurs while reading the input stream.
     * @see InputStream.mark
     * @see InputStream.markSupported
     * @see java.net.URLConnection.getContentType
     */
    @Throws(IOException::class)
    private fun guessContentTypeFromStream(`is`: InputStream): String? {
        val c1 = `is`.read()
        val c2 = `is`.read()
        val c3 = `is`.read()
        val c4 = `is`.read()
        val c5 = `is`.read()
        val c6 = `is`.read()
        val c7 = `is`.read()
        val c8 = `is`.read()
        val c9 = `is`.read()
        val c10 = `is`.read()
        val c11 = `is`.read()
        val c12 = `is`.read()
        val c13 = `is`.read()
        val c14 = `is`.read()
        val c15 = `is`.read()
        val c16 = `is`.read()
        if (c1 == 'G'.code && c2 == 'I'.code && c3 == 'F'.code && c4 == '8'.code) {
            return "image/gif"
        }
        if (c1 == '#'.code && c2 == 'd'.code && c3 == 'e'.code && c4 == 'f'.code) {
            return "image/x-bitmap"
        }
        if (c1 == '!'.code && c2 == ' '.code && c3 == 'X'.code && c4 == 'P'.code && c5 == 'M'.code && c6 == '2'.code) {
            return "image/x-pixmap"
        }
        if (c1 == 137 && c2 == 80 && c3 == 78 && c4 == 71 && c5 == 13 && c6 == 10 && c7 == 26 && c8 == 10) {
            return "image/png"
        }
        if (c1 == 0xFF && c2 == 0xD8 && c3 == 0xFF) {
            if (c4 == 0xE0 || c4 == 0xEE) {
                return "image/jpeg"
            }

            /*
             * File format used by digital cameras to store images.
             * Exif Format can be read by any application supporting JPEG.
             * Exif Spec can be found at:
             * http://www.pima.net/standards/it10/PIMA15740/Exif_2-1.PDF
             */
            if (c4 == 0xE1 && c7 == 'E'.code && c8 == 'x'.code && c9 == 'i'.code && c10 == 'f'.code && c11 == 0) {
                return "image/jpeg"
            }
        }
        if (c1 == 0x2E && c2 == 0x73 && c3 == 0x6E && c4 == 0x64) {
            return "audio/basic" // .au format, big endian
        }
        if (c1 == 0x64 && c2 == 0x6E && c3 == 0x73 && c4 == 0x2E) {
            return "audio/basic" // .au format, little endian
        }
        if (c1 == 'R'.code && c2 == 'I'.code && c3 == 'F'.code && c4 == 'F'.code) {
            /* I don't know if this is official but evidence
             * suggests that .wav files start with "RIFF" - brown
             */
            return "audio/x-wav"
        }
        if (c1 == 0xCA && c2 == 0xFE && c3 == 0xBA && c4 == 0xBE) {
            return "application/java-vm"
        }
        if (c1 == 0xAC && c2 == 0xED) {
            // next two bytes are version number, currently 0x00 0x05
            return "application/x-java-serialized-object"
        }
        if (c1 == '<'.code) {
            if (c2 == '!'.code || c2 == 'h'.code && (c3 == 't'.code && c4 == 'm'.code && c5 == 'l'.code ||
                            c3 == 'e'.code && c4 == 'a'.code && c5 == 'd'.code) || c2 == 'b'.code && c3 == 'o'.code && c4 == 'd'.code && c5 == 'y'.code || c2 == 'H'.code && (c3 == 'T'.code && c4 == 'M'.code && c5 == 'L'.code ||
                            c3 == 'E'.code && c4 == 'A'.code && c5 == 'D'.code) || c2 == 'B'.code && c3 == 'O'.code && c4 == 'D'.code && c5 == 'Y'.code) {
                return "text/html"
            }
            if (c2 == '?'.code && c3 == 'x'.code && c4 == 'm'.code && c5 == 'l'.code && c6 == ' '.code) {
                return "application/xml"
            }
        }

        // big and little (identical) endian UTF-8 encodings, with BOM
        if (c1 == 0xef && c2 == 0xbb && c3 == 0xbf) {
            if (c4 == '<'.code && c5 == '?'.code && c6 == 'x'.code) {
                return "application/xml"
            }
        }

        // big and little endian UTF-16 encodings, with byte order mark
        if (c1 == 0xfe && c2 == 0xff) {
            if (c3 == 0 && c4 == '<'.code && c5 == 0 && c6 == '?'.code && c7 == 0 && c8 == 'x'.code) {
                return "application/xml"
            }
        }
        if (c1 == 0xff && c2 == 0xfe) {
            if (c3 == '<'.code && c4 == 0 && c5 == '?'.code && c6 == 0 && c7 == 'x'.code && c8 == 0) {
                return "application/xml"
            }
        }

        // big and little endian UTF-32 encodings, with BOM
        if (c1 == 0x00 && c2 == 0x00 && c3 == 0xfe && c4 == 0xff) {
            if (c5 == 0 && c6 == 0 && c7 == 0 && c8 == '<'.code && c9 == 0 && c10 == 0 && c11 == 0 && c12 == '?'.code && c13 == 0 && c14 == 0 && c15 == 0 && c16 == 'x'.code) {
                return "application/xml"
            }
        }
        if (c1 == 0xff && c2 == 0xfe && c3 == 0x00 && c4 == 0x00) {
            if (c5 == '<'.code && c6 == 0 && c7 == 0 && c8 == 0 && c9 == '?'.code && c10 == 0 && c11 == 0 && c12 == 0 && c13 == 'x'.code && c14 == 0 && c15 == 0 && c16 == 0) {
                return "application/xml"
            }
        }
        return null
    }
}