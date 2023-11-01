/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.opengl.*
import android.view.*

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 *
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface() and uses that to create
 * an EGL window surface. Calls to eglSwapBuffers() cause a frame of data to be sent to the video encoder.
 *
 * This object owns the Surface -- releasing this will release the Surface too.
 */
class CodecInputSurface
/**
 * Creates a CodecInputSurface from a Surface.
 *
 * @param surface the input surface.
 * @param sharedContext shared context if any.
 */
(private val mSurface: Surface, sharedContext: EGLContext?) : OpenGLContext(true, mSurface, sharedContext) {
    /**
     * {@inheritDoc}
     */
    override fun release() {
        super.release()
        mSurface.release()
    }
}