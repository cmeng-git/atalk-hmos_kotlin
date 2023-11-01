package org.atalk.impl.neomedia.recording

import java.io.IOException

class WebmWriter(filename: String?) {
    private val glob: Long
    private external fun allocCfg(): Long

    /**
     * Free-s `glob` and closes the file opened for writing.
     *
     * @param glob
     */
    private external fun freeCfg(glob: Long)
    private external fun openFile(glob: Long, fileName: String?): Boolean
    private external fun writeWebmFileHeader(glob: Long, width: Int, height: Int)
    fun writeWebmFileHeader(width: Int, height: Int) {
        writeWebmFileHeader(glob, width, height)
    }

    private external fun writeWebmBlock(glob: Long, fd: FrameDescriptor)
    private external fun writeWebmFileFooter(glob: Long, hash: Long)

    init {
        glob = allocCfg()
        if (glob == 0L) {
            throw IOException("allocCfg() failed")
        }
        if (openFile(glob, filename)) {
            throw IOException("Can not open $filename for writing")
        }
    }

    fun close() {
        writeWebmFileFooter(glob, 0)
        freeCfg(glob) // also closes the file
    }

    fun writeFrame(fd: FrameDescriptor) {
        writeWebmBlock(glob, fd)
    }

    class FrameDescriptor {
        var buffer: ByteArray? = null
        var offset = 0
        var length = 0L
        var pts = 0L
        var flags = 0
    }

    companion object {
        init {
            System.loadLibrary("jnvpx")
            System.loadLibrary(WebmWriter::class.java.name)
        }

        /**
         * Constant corresponding to `VPX_FRAME_IS_KEY` from libvpx's `vpx/vpx_encoder.h`
         */
        var FLAG_FRAME_IS_KEY = 0x01

        /**
         * Constant corresponding to `VPX_FRAME_IS_INVISIBLE` from libvpx's
         * `vpx/vpx_encoder.h`
         */
        var FLAG_FRAME_IS_INVISIBLE = 0x04
    }
}