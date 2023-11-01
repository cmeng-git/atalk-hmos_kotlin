/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.atalk.hmos.gui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import org.atalk.persistance.FileBackend.getMimeType
import timber.log.Timber
import java.io.File
import java.util.*

class Attachment : Parcelable {
    internal constructor(pIn: Parcel) {
        uri = pIn.readParcelable(Uri::class.java.classLoader)
        mime = pIn.readString()
        uuid = UUID.fromString(pIn.readString())
        type = Type.valueOf(pIn.readString()!!)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(uri, flags)
        dest.writeString(mime)
        dest.writeString(uuid.toString())
        dest.writeString(type.toString())
    }

    override fun describeContents(): Int {
        return 0
    }

    enum class Type {
        FILE, IMAGE, LOCATION, RECORDING
    }

    val uri: Uri?
    val type: Type
    val uuid: UUID
    val mime: String?

    private constructor(uuid: UUID, uri: Uri, type: Type, mime: String?) {
        this.uri = uri
        this.type = type
        this.mime = mime
        this.uuid = uuid
    }

    private constructor(uri: Uri, type: Type, mime: String?) {
        this.uri = uri
        this.type = type
        this.mime = mime
        uuid = UUID.randomUUID()
    }

    fun renderThumbnail(): Boolean {
        return (type == Type.IMAGE || type == Type.FILE && mime != null && (mime.startsWith("video/") || mime.startsWith("image/")))
    }

    companion object {
        @JvmField
        val CREATOR = object : Creator<Attachment?> {
            override fun createFromParcel(`in`: Parcel): Attachment {
                return Attachment(`in`)
            }

            override fun newArray(size: Int): Array<Attachment?> {
                return arrayOfNulls(size)
            }
        }

        fun canBeSendInband(attachments: List<Attachment>): Boolean {
            for (attachment in attachments) {
                if (attachment.type != Type.LOCATION) {
                    return false
                }
            }
            return true
        }

        fun of(context: Context, uri: Uri, type: Type): List<Attachment> {
            val mime = if (type == Type.LOCATION) null else getMimeType(context, uri)
            return listOf(Attachment(uri, type, mime))
        }

        fun of(context: Context, uris: List<Uri>): List<Attachment> {
            val attachments = ArrayList<Attachment>()
            for (uri in uris) {
                val mime = getMimeType(context, uri)
                attachments.add(Attachment(uri, if (mime != null && mime.startsWith("image/")) Type.IMAGE else Type.FILE, mime))
            }
            return attachments
        }

        fun of(uuid: UUID, file: File?, mime: String?): Attachment {
            return Attachment(uuid, Uri.fromFile(file), if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) Type.IMAGE else Type.FILE, mime)
        }

        fun extractAttachments(context: Context, intent: Intent, type: Type): List<Attachment> {
            val uris = ArrayList<Attachment>()
            if (intent == null) {
                return uris
            }
            val contentType = intent.type
            val data = intent.data
            if (data == null) {
                val clipData = intent.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        val mime = getMimeType(context, uri, contentType)
                        Timber.d("uri = %s; contentType = %s; mime = %s", uri, contentType, mime)
                        uris.add(Attachment(uri, type, mime))
                    }
                }
            } else {
                // final String mime = MimeUtils.guessMimeTypeFromUriAndMime(context, data, contentType);
                val mime = getMimeType(context, data, contentType)
                uris.add(Attachment(data, type, mime))
            }
            return uris
        }
    }
}