/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import java.io.File

/**
 * The `OperationSetThumbnailedFileFactory` is meant to be used by bundles interested in
 * making files with thumbnails. For example the user interface can be interested in sending files
 * with thumbnails through the `OperationSetFileTransfer`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface OperationSetThumbnailedFileFactory : OperationSet {
    /**
     * Creates a file, by attaching the thumbnail, given by the details, to it.
     *
     * @param file the base file
     * @param thumbnailWidth the width of the thumbnail
     * @param thumbnailHeight the height of the thumbnail
     * @param thumbnailMimeType the mime type of the thumbnail
     * @param thumbnail the thumbnail data
     * @return a file with a thumbnail
     */
    fun createFileWithThumbnail(file: File, thumbnailWidth: Int, thumbnailHeight: Int,
            thumbnailMimeType: String, thumbnail: ByteArray): File
}