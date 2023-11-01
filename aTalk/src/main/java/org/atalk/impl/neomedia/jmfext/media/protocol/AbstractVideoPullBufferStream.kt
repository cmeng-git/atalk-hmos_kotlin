/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol

import java.io.IOException
import javax.media.Buffer
import javax.media.control.FormatControl
import javax.media.control.FrameRateControl
import javax.media.protocol.PullBufferDataSource

/**
 * Provides a base implementation of `PullBufferStream` for video in order to facilitate
 * implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
abstract class AbstractVideoPullBufferStream<T : PullBufferDataSource?>
/**
 * Initializes a new `AbstractVideoPullBufferStream` instance which is to have its
 * `Format`-related information abstracted by a specific `FormatControl`.
 *
 * @param dataSource
 * the `PullBufferDataSource` which is creating the new instance so that it
 * becomes one of its `streams`
 * @param formatControl
 * the `FormatControl` which is to abstract the `Format`-related
 * information of the new instance
 */
protected constructor(dataSource: T, formatControl: FormatControl?) : AbstractPullBufferStream<T>(dataSource, formatControl) {
    /**
     * The output frame rate of this `AbstractVideoPullBufferStream` which has been
     * specified by [.frameRateControl] and depending on which
     * [.minimumVideoFrameInterval] has been calculated.
     */
    private var frameRate = 0f

    /**
     * The `FrameRateControl` which gets and sets the output frame rate of this
     * `AbstractVideoPullBufferStream`.
     */
    private var frameRateControl: FrameRateControl? = null

    /**
     * The minimum interval in milliseconds between consecutive video frames i.e. the reverse of
     * [.frameRate].
     */
    private var minimumVideoFrameInterval: Long = 0

    /**
     * Blocks and reads into a `Buffer` from this `PullBufferStream`.
     *
     * @param buffer
     * the `Buffer` this `PullBufferStream` is to read into
     * @throws IOException
     * if an I/O error occurs while this `PullBufferStream` reads into the specified
     * `Buffer`
     */
    @Throws(IOException::class)
    protected abstract fun doRead(buffer: Buffer)

    /**
     * Blocks and reads into a `Buffer` from this `PullBufferStream`.
     *
     * @param buffer
     * the `Buffer` this `PullBufferStream` is to read into
     * @throws IOException
     * if an I/O error occurs while this `PullBufferStream` reads into the specified
     * `Buffer`
     */
    @Throws(IOException::class)
    override fun read(buffer: Buffer) {
        val frameRateControl = frameRateControl
        if (frameRateControl != null) {
            val frameRate = frameRateControl.frameRate
            if (frameRate > 0) {
                if (this.frameRate != frameRate) {
                    minimumVideoFrameInterval = (1000 / frameRate).toLong()
                    this.frameRate = frameRate
                }
                if (minimumVideoFrameInterval > 0) {
                    val startTime = System.currentTimeMillis()
                    doRead(buffer)
                    if (!buffer.isDiscard) {
                        var interrupted = false
                        while (true) {
                            // Sleep to respect the frame rate as much as possible.
                            val sleep = (minimumVideoFrameInterval
                                    - (System.currentTimeMillis() - startTime))
                            if (sleep > 0) {
                                try {
                                    Thread.sleep(sleep)
                                } catch (ie: InterruptedException) {
                                    interrupted = true
                                }
                            } else {
                                // Yield a little bit to not use all the whole CPU.
                                Thread.yield()
                                break
                            }
                        }
                        if (interrupted) Thread.currentThread().interrupt()
                    }
                    // We've executed #doRead(Buffer).
                    return
                }
            }
        }
        // If there is no frame rate to be respected, just #doRead(Buffer).
        doRead(buffer)
    }

    /**
     * Starts the transfer of media data from this `AbstractBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while starting the transfer of media data from this
     * `AbstractBufferStream`
     * @see AbstractBufferStream.start
     */
    @Throws(IOException::class)
    override fun start() {
        super.start()
        frameRateControl = dataSource!!
                .getControl(FrameRateControl::class.java.name) as FrameRateControl
    }

    /**
     * Stops the transfer of media data from this `AbstractBufferStream`.
     *
     * @throws IOException
     * if anything goes wrong while stopping the transfer of media data from this
     * `AbstractBufferStream`
     * @see AbstractBufferStream.stop
     */
    @Throws(IOException::class)
    override fun stop() {
        super.stop()
        frameRateControl = null
    }
}