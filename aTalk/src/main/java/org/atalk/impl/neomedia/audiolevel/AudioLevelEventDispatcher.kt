/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel

import okhttp3.internal.notify
import org.atalk.service.neomedia.event.SimpleAudioLevelListener
import javax.media.Buffer

/**
 * The class implements an audio level measurement thread. The thread will measure new data every
 * time it is added through the `addData()` method and would then deliver it to a registered
 * listener if any. (No measurement would be performed until we have a `levelListener`). We
 * use a separate thread so that we could compute and deliver audio levels in a way that won't delay
 * the media processing thread.
 *
 *
 * Note that, for performance reasons this class is not 100% thread safe and you should not modify
 * add or remove audio listeners in this dispatcher in the notification thread (i.e. in the thread
 * where you were notified of an audio level change).
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class AudioLevelEventDispatcher
/**
 * Initializes a new `AudioLevelEventDispatcher` instance which is to use a specific
 * name
 * for its `Thread` which is to run the actual audio level calculations and to dispatch
 * to its `SimpleAudioLevelListener`
 *
 * @param threadName
 */
    (
        /**
         * The name of the `Thread` which is to run the actual audio level calculations and to dispatch to [.listener].
         */
        private val threadName: String?,
) {

    private val IDLE_TIMEOUT = 30 * 1000L

    /**
     * The `AudioLevelMap` in which the audio calculations run by this
     * `AudioLevelEventDispatcher` are to be cached in addition to dispatching them to [.listener].
     */
    private var cache: AudioLevelMap? = null

    /**
     * The data to process.
     */
    private var data: ByteArray? = null

    /**
     * The length of the data last recorded in the `data` array.
     */
    private var dataLength = 0

    /**
     * The listener which is interested in audio level changes.
     */
    private var listener: SimpleAudioLevelListener? = null

    /**
     * The SSRC of the stream we are measuring that we should use as a key for entries of the levelMap level cache.
     */
    private var ssrc = -1L

    /**
     * The `Thread` which runs the actual audio level calculations and dispatches to [.listener].
     */
    private var thread: Thread? = null

    /**
     * Runs the actual audio level calculations and dispatches to the [.listener].
     */
    private fun run() {
        var idleTimeoutStart = -1L

        loop@ while (true) {
            var listener: SimpleAudioLevelListener?
            var cache: AudioLevelMap?
            var ssrc: Long
            var data: ByteArray? = null
            var dataLength = 0

            var cont = false
            var brk = false
            if (Thread.currentThread() != thread) break

            synchronized(this) {
                listener = this.listener
                cache = this.cache
                ssrc = this.ssrc

                /*
				 * If no one is interested in the audio level, do not even keep the Thread waiting.
				 */
                if ((listener == null) && ((cache == null) || (ssrc == -1L))) {
                    brk = true
                }
                else {
                    data = this.data
                    dataLength = this.dataLength

                    /*
                     * If there is no data to calculate the audio level of, wait for such data to be provided.
                     */
                    if (data == null || dataLength < 1) {
                        // The current thread is idle.
                        if (idleTimeoutStart == -1L) {
                            idleTimeoutStart = System.currentTimeMillis()
                        }
                        else if (System.currentTimeMillis() - idleTimeoutStart < IDLE_TIMEOUT) {
                            var interrupted = false

                            try {
                                (this as Object).wait(IDLE_TIMEOUT)
                            } catch (ie: InterruptedException) {
                                interrupted = true
                            }

                            if (interrupted) Thread.currentThread().interrupt()
                            cont = true
                        }
                    }
                }

                if (!brk && !cont) {
                    // The values of data and dataLength seem valid so consume them.
                    this.data = null
                    this.dataLength = 0
                    // The current thread is no longer idle.
                    idleTimeoutStart = -1
                }
            }

            if (brk) {
                break
            }
            if (cont) {
                continue
            }

            // FIXME The audio level is expressed in -dBov.
            var level = AudioLevelCalculator.calculateAudioLevel(data!!, 0, dataLength).toInt()
            level = AudioLevelCalculator.MIN_AUDIO_LEVEL - level

            /*
             * In order to try to mitigate the issue with allocating data, try to return the one
             * which we have just calculated the audio level of.
             */
            synchronized(this) {
                if ((this.data == null) && (this.listener == null)
                        && ((this.cache == null) || (this.ssrc == -1L))) {
                    this.data = data
                }
            }

            // Cache the newLevel if requested.
            if (cache != null && ssrc != -1L)
                cache!!.putLevel(ssrc, level)
            // Notify the listener about the newLevel if requested.
            if (listener != null)
                listener!!.audioLevelChanged(level)
        }
    }

    /**
     * Adds data to be processed.
     *
     * @param buffer the data that we'd like to queue for processing.
     */
    @Synchronized
    fun addData(buffer: Buffer) {
        /*
         * If no one is interested in the audio level, do not even add the Buffer data.
         */
        if ((listener == null) && ((cache == null) || (ssrc == -1L)))
            return

        dataLength = buffer.length
        if (dataLength > 0) {
            if (data == null || data!!.size < dataLength)
                data = ByteArray(dataLength)

            val bufferData = buffer.data
            if (bufferData != null) {
                System.arraycopy(bufferData, buffer.offset, data!!, 0, dataLength)
            }
            if (thread == null)
                startThread()
            else notify()
        }
    }

    /**
     * Sets the new listener that will be gathering all events from this dispatcher.
     *
     * @param listener the listener that we will be notifying or `null` if we are to remove it.
     */
    @Synchronized
    fun setAudioLevelListener(listener: SimpleAudioLevelListener?) {
        if (this.listener != listener) {
            this.listener = listener
            startOrNotifyThread()
        }
    }

    /**
     * Sets an `AudioLevelMap` that this dispatcher could use to cache levels it's measuring
     * in addition to simply delivering them to a listener.
     *
     * @param cache the `AudioLevelMap` where this dispatcher should cache measured results.
     * @param ssrc the SSRC key where entries should be logged
     */
    @Synchronized
    fun setAudioLevelCache(cache: AudioLevelMap?, ssrc: Long) {
        if (this.cache != cache || this.ssrc != ssrc) {
            this.cache = cache
            this.ssrc = ssrc
            startOrNotifyThread()
        }
    }

    /**
     * Starts the `Thread` which is to run the audio level calculations and to dispatch to
     * [.listener] if necessary or notifies it about a change it the state on which it depends.
     */
    @Synchronized
    private fun startOrNotifyThread() {
        if ((this.listener == null) && ((cache == null) || (ssrc == -1L))) {
            thread = null
            (this as Object).notify()
        }
        else if (data != null && dataLength > 0) {
            if (thread == null)
                startThread()
            else
                (this as Object).notify()
        }
    }

    /**
     * Starts the `Thread` which is to run the audio level calculations and to dispatch to [.listener].
     */
    @Synchronized
    private fun startThread() {
        thread = object : Thread() {
            override fun run() {
                try {
                    this@AudioLevelEventDispatcher.run()
                } finally {
                    synchronized(this@AudioLevelEventDispatcher) {
                        if (currentThread() == thread)
                            thread = null
                        /*
                         * If the thread of this AudioLevelEventDispatcher is dying yet the state
                         * suggests that it should be running, restart it.
                         */
                        if ((thread == null)
                                && ((listener != null) || ((cache != null) && (ssrc != -1L)))
                                && (data != null)
                                && (dataLength > 0))
                            startThread()
                    }
                }
            }
        }

        thread?.isDaemon = true
        if (threadName != null)
            thread?.name = threadName

        thread?.start()
    }
}