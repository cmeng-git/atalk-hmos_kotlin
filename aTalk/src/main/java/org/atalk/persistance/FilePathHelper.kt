/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import org.atalk.hmos.gui.share.Attachment
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * FilePath Helper utilities to handle android content:// scheme uri
 *
 * @author Eng Chong Meng
 */
object FilePathHelper {
    /**
     * Get the real local file path of the given attachement; create and copy to a new local file on failure
     *
     * @param ctx the reference Context
     * @param attachment a wrapper of file with other properties {@see Attachment}
     * @return real local file path of uri or newly created file
     */
    fun getFilePath(ctx: Context, attachment: Attachment): String? {
        val uri = attachment.uri
        var filePath = getFilePath(ctx, uri)
        if (filePath == null) {
            filePath = getFilePathWithCreate(ctx, uri)
        }
        return filePath
    }

    /**
     * Get the real local file path of the given uri if accessible;
     * Else create and copy to a new local file on failure
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return real local file path of uri or newly created file
     */
    fun getFilePath(ctx: Context, uri: Uri?): String? {
        var filePath: String? = null
        try {
            filePath = getUriRealPath(ctx, uri)
        } catch (e: Exception) {
            Timber.d("FilePath Catch: %s", uri.toString())
        }
        if (TextUtils.isEmpty(filePath)) filePath = getFilePathWithCreate(ctx, uri)
        return filePath
    }

    /**
     * To create a new file based on the given uri (usually on ContentResolver failure)
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return file name with the guessed ext if none is given.
     */
    private fun getFilePathWithCreate(ctx: Context, uri: Uri?): String? {
        var fileName: String? = null
        if (!TextUtils.isEmpty(uri!!.path)) {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor == null) fileName = uri.path else {
                cursor.moveToFirst()
                val idx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                fileName = cursor.getString(idx)
                cursor.close()
            }
        }
        if (!TextUtils.isEmpty(fileName)) {
            val destFile = File(FileBackend.getaTalkStore(FileBackend.TMP, true), fileName)
            if (!destFile.exists()) {
                Timber.d("FilePath copyFile: %s", destFile)
                copy(ctx, uri, destFile)
            }
            return destFile.absolutePath
        }
        return null
    }

    /**
     * Copy the content from the given uri to the defined destFile
     *
     * @param context the reference Context
     * @param srcUri content:// or file:// or whatever suitable Uri you want.
     * @param dstFile the destination file to be copied to
     */
    fun copy(context: Context, srcUri: Uri?, dstFile: File?) {
        try {
            val inputStream = context.contentResolver.openInputStream(srcUri!!) ?: return
            val outputStream = FileOutputStream(dstFile)
            FileBackend.copy(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
        } catch (e: Exception) { // IOException
            e.printStackTrace()
        }
    }

    /**
     * Get the uri real path for OS with KitKat and above.
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     */
    @Throws(Exception::class)
    private fun getUriRealPath(ctx: Context?, uri: Uri?): String? {
        var filePath: String? = ""
        if (ctx != null && uri != null) {
            // Get uri authority.
            val uriAuthority = uri.authority
            if (isContentUri(uri)) {
                filePath = if (isGooglePhotoDoc(uriAuthority)) {
                    uri.lastPathSegment
                } else {
                    getRealPath(ctx.contentResolver, uri, null)
                }
            } else if (isFileUri(uri)) {
                filePath = uri.path
            } else if (isDocumentUri(ctx, uri)) {
                // Get uri related document id.
                val documentId = DocumentsContract.getDocumentId(uri)
                if (isMediaDoc(uriAuthority)) {
                    val idArr = documentId.split(":")
                    if (idArr.size == 2) {
                        // First item is document type.
                        val docType = idArr[0]

                        // Second item is document real id.
                        val realDocId = idArr[1]

                        // Get content uri by document type.
                        var mediaContentUri: Uri? = null
                        if ("image" == docType) {
                            mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        } else if ("video" == docType) {
                            mediaContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        } else if ("audio" == docType) {
                            mediaContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        }
                        if (mediaContentUri != null) {
                            // Get where clause with real document id.
                            val whereClause = MediaStore.Images.Media._ID + " = " + realDocId
                            filePath = getRealPath(ctx.contentResolver, mediaContentUri, whereClause)
                        }
                    }
                } else if (isDownloadDoc(uriAuthority)) {
                    // Build download uri.
                    val downloadUri = Uri.parse("content://downloads/public_downloads")

                    // Append download document id at uri end.
                    val downloadUriAppendId = ContentUris.withAppendedId(downloadUri, documentId.toLong())
                    filePath = getRealPath(ctx.contentResolver, downloadUriAppendId, null)
                } else if (isExternalStoreDoc(uriAuthority)) {
                    val idArr = documentId.split(":")
                    if (idArr.size == 2) {
                        val type = idArr[0]
                        val realDocId = idArr[1]
                        if ("primary".equals(type, ignoreCase = true)) {
                            filePath = ctx.getExternalFilesDir(realDocId)!!.absolutePath
                        }
                    }
                }
            }
        }
        return filePath
    }

    /**
     * Check whether this uri represent a document or not.
     *
     * @param uri content:// or file:// or whatever suitable Uri you want.
     */
    private fun isDocumentUri(ctx: Context?, uri: Uri?): Boolean {
        var ret = false
        if (ctx != null && uri != null) {
            ret = DocumentsContract.isDocumentUri(ctx, uri)
        }
        return ret
    }

    /**
     * Check whether this uri is a content uri or not.
     *
     * @param uri content uri e.g. content://media/external/images/media/1302716
     */
    private fun isContentUri(uri: Uri?): Boolean {
        var ret = false
        if (uri != null) {
            val uriSchema = uri.scheme
            if ("content".equals(uriSchema, ignoreCase = true)) {
                ret = true
            }
        }
        return ret
    }

    /**
     * Check whether this uri is a file uri or not.
     *
     * @param uri file uri e.g. file:///storage/41B7-12F1/DCIM/Camera/IMG_20180211_095139.jpg
     */
    private fun isFileUri(uri: Uri?): Boolean {
        var ret = false
        if (uri != null) {
            val uriSchema = uri.scheme
            if ("file".equals(uriSchema, ignoreCase = true)) {
                ret = true
            }
        }
        return ret
    }

    /* Check whether this document is provided by ExternalStorageProvider. */
    private fun isExternalStoreDoc(uriAuthority: String?): Boolean {
        return "com.android.externalstorage.documents" == uriAuthority
    }

    /* Check whether this document is provided by DownloadsProvider. */
    private fun isDownloadDoc(uriAuthority: String?): Boolean {
        return "com.android.providers.downloads.documents" == uriAuthority
    }

    /* Check whether this document is provided by MediaProvider. */
    private fun isMediaDoc(uriAuthority: String?): Boolean {
        return "com.android.providers.media.documents" == uriAuthority
    }

    /* Check whether this document is provided by google photo. */
    private fun isGooglePhotoDoc(uriAuthority: String?): Boolean {
        return "com.google.android.apps.photos.content" == uriAuthority
    }

    /* Return uri represented document file real local path.*/
    @SuppressLint("Recycle")
    @Throws(Exception::class)
    private fun getRealPath(contentResolver: ContentResolver, uri: Uri, whereClause: String?): String {
        var filePath = ""
        // Query the uri with condition.
        val cursor = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), whereClause, null, null)
        if (cursor != null) {
            // Get column value which is the uri related file path.
            if (cursor.moveToFirst()) {
                filePath = cursor.getString(0)
            }
            cursor.close()
        } else {
            throw Exception("Cursor is null!")
        }
        return filePath
    }
}