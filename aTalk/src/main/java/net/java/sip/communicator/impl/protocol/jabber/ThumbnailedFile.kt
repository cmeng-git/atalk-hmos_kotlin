/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import java.io.File

/**
 * A `ThumbnailedFile` is a file with a thumbnail.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ThumbnailedFile
/**
 * Creates a `ThumbnailedFile`, by specifying the base `file`, the
 * `thumbnailWidth` and `thumbnailHeight`, the `thumbnailMimeType` and the
 * `thumbnail` itself.
 *
 * @param file the base file
 * @param thumbnailWidth the width of the thumbnail
 * @param thumbnailHeight the height of the thumbnail
 * @param thumbnailMimeType the mime type
 * @param thumbnail the thumbnail
 */
(file: File,
        /**
         * Returns the thumbnail width.
         *
         * @return the thumbnail width
         */
        val thumbnailWidth: Int,
        /**
         * Returns the thumbnail height.
         *
         * @return the thumbnail height
         */
        val thumbnailHeight: Int,
        /**
         * Returns the thumbnail mime type.
         *
         * @return the thumbnail mime type
         */
        val thumbnailMimeType: String,
        /**
         * Returns the thumbnail of this file.
         *
         * @return the thumbnail of this file
         */
        val thumbnailData: ByteArray) : File(file.path) {

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}