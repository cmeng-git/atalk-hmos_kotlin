/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.imgstreaming

import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Provides utility functions used by the `imgstreaming` package(s).
 *
 * @author Sebastien Vincent
 */
object ImgStreamingUtils {
    /**
     * Get a scaled `BufferedImage`.
     *
     *
     * Mainly inspired by: http://java.developpez.com/faq/gui/?page=graphique_general_images
     * #GRAPHIQUE_IMAGE_redimensionner
     *
     * @param src source image
     * @param width width of scaled image
     * @param height height of scaled image
     * @param type `BufferedImage` type
     * @return scaled `BufferedImage`
     */
    fun getScaledImage(src: BufferedImage, width: Int, height: Int, type: Int): BufferedImage? {
        val scaleWidth = width / src.width.toDouble()
        val scaleHeight = height / src.height.toDouble()
        val tx = AffineTransform()

        // Skip rescaling if input and output size are the same.
        if (java.lang.Double.compare(scaleWidth, 1.0) != 0 || java.lang.Double.compare(scaleHeight, 1.0) != 0) tx.scale(scaleWidth, scaleHeight)

        // cmeng - need more work if want to enable
        // AffineTransformOp op = new AffineTransformOp(tx,
        // AffineTransformOp.TYPE_BILINEAR);
        val dst = BufferedImage(width, height, type)
        return null // op.filter(src, dst);
    }

    /**
     * Get raw bytes from ARGB `BufferedImage`.
     *
     * @param src ARGB <BufferImage></BufferImage>
     * @param output output buffer, if not null and if its length is at least image's (width * height) * 4,
     * method will put bytes in it.
     * @return raw bytes or null if src is not an ARGB `BufferedImage`
     */
    fun getImageBytes(src: BufferedImage, output: ByteArray?): ByteArray {
        require(src.type == BufferedImage.TYPE_INT_ARGB) { "src.type" }
        val raster = src.raster
        val width = src.width
        val height = src.height
        val size = width * height * 4
        var off = 0
        val pixel = IntArray(4)
        var data: ByteArray? = null
        data = if (output == null || output.size < size) {
            /* allocate our bytes array */
            ByteArray(size)
        } else {
            /* use output */
            output
        }
        for (y in 0 until height) for (x in 0 until width) {
            raster.getPixel(x, y, pixel)
            data[off++] = pixel[0].toByte()
            data[off++] = pixel[1].toByte()
            data[off++] = pixel[2].toByte()
            data[off++] = pixel[3].toByte()
        }
        return data
    }
}