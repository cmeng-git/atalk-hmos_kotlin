/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

/**
 * Utility class that represents codecs color formats.
 *
 * @author Pawel Domas
 */
class CodecColorFormat private constructor(
        /**
         * Color name.
         */
        val name: String,
        /**
         * Color constant value.
         */
        val value: Int) {
    override fun toString(): String {
        return name + "(0x" + Integer.toString(value, 16) + ")"
    }

    companion object {
        private val values = arrayOf(
                CodecColorFormat("Monochrome", 1),
                CodecColorFormat("8bitRGB332", 2),
                CodecColorFormat("12bitRGB444", 3),
                CodecColorFormat("16bitARGB4444", 4),
                CodecColorFormat("16bitARGB1555", 5),
                CodecColorFormat("16bitRGB565", 6),
                CodecColorFormat("16bitBGR565", 7),
                CodecColorFormat("18bitRGB666", 8),
                CodecColorFormat("18bitARGB1665", 9),
                CodecColorFormat("19bitARGB1666", 10),
                CodecColorFormat("24bitRGB888", 11),
                CodecColorFormat("24bitBGR888", 12),
                CodecColorFormat("24bitARGB1887", 13),
                CodecColorFormat("25bitARGB1888", 14),
                CodecColorFormat("32bitBGRA8888", 15),
                CodecColorFormat("32bitARGB8888", 16),
                CodecColorFormat("YUV411Planar", 17),
                CodecColorFormat("YUV411PackedPlanar", 18),
                CodecColorFormat("YUV420Planar", 19),
                CodecColorFormat("YUV420PackedPlanar", 20),
                CodecColorFormat("YUV420SemiPlanar", 21),
                CodecColorFormat("YUV422Planar", 22),
                CodecColorFormat("YUV422PackedPlanar", 23),
                CodecColorFormat("YUV422SemiPlanar", 24),
                CodecColorFormat("YCbYCr", 25),
                CodecColorFormat("YCrYCb", 26),
                CodecColorFormat("CbYCrY", 27),
                CodecColorFormat("CrYCbY", 28),
                CodecColorFormat("YUV444Interleaved", 29),
                CodecColorFormat("RawBayer8bit", 30),
                CodecColorFormat("RawBayer10bit", 31),
                CodecColorFormat("RawBayer8bitcompressed", 32),
                CodecColorFormat("L2", 33),
                CodecColorFormat("L4", 34),
                CodecColorFormat("L8", 35),
                CodecColorFormat("L16", 36),
                CodecColorFormat("L24", 37),
                CodecColorFormat("L32", 38),
                CodecColorFormat("YUV420PackedSemiPlanar", 39),
                CodecColorFormat("YUV422PackedSemiPlanar", 40),
                CodecColorFormat("18BitBGR666", 41),
                CodecColorFormat("24BitARGB6666", 42),
                CodecColorFormat("24BitABGR6666", 43),
                CodecColorFormat("TI_FormatYUV420PackedSemiPlanar",
                        0x7f000100),  // new CodecColorFormat("Surface indicates that the data
                // will be a GraphicBuffer metadata reference.
                // In OMX this is called
                // OMX_new CodecColorFormat("AndroidOpaque.
                CodecColorFormat("Surface", 0x7F000789),
                CodecColorFormat("QCOM_FormatYUV420SemiPlanar",
                        0x7fa30c00)
        )

        /**
         * Returns `CodecColorFormat` for given int constant value.
         * @param value color constant value.
         * @return `CodecColorFormat` for given int constant value.
         */
        fun fromInt(value: Int): CodecColorFormat {
            for (value1 in values) {
                if (value1.value == value) return value1
            }
            return CodecColorFormat("VENDOR", value)
        }
    }
}