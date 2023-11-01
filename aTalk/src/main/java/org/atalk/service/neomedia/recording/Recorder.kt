/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording

import org.atalk.service.neomedia.MediaException
import org.atalk.service.neomedia.MediaStream
import java.io.IOException

/**
 * The call recording interface. Provides the capability to start and stop call recording.
 *
 * @author Dmitri Melnikov
 * @author Lubomir Marinov
 * @author Boris Grozev
 */
interface Recorder {
    /**
     * Adds a new `Listener` to the list of listeners interested in notifications from this
     * `Recorder`.
     *
     * @param listener
     * the new `Listener` to be added to the list of listeners interested in
     * notifications from this `Recorder`
     */
    fun addListener(listener: Listener)

    /**
     * Gets a list of the formats in which this `Recorder` supports recording media.
     *
     * @return a `List` of the formats in which this `Recorder` supports recording
     * media
     */
    val supportedFormats: List<String>?

    /**
     * Removes an existing `Listener` from the list of listeners interested in notifications
     * from this `Recorder`.
     *
     * @param listener
     * the existing `Listener` to be removed from the list of listeners interested in
     * notifications from this `Recorder`
     */
    fun removeListener(listener: Listener)

    /**
     * Starts the recording of the media associated with this `Recorder` (e.g. the media
     * being sent and received in a `Call`) into a file with a specific name.
     *
     * @param format the format into which the media associated with this `Recorder` is to be
     * recorded into the specified file
     * @param filename the name of the file into which the media associated with this
     * `Recorder` is to be recorded
     * @throws IOException if anything goes wrong with the input and/or output performed by this `Recorder`
     * @throws MediaException if anything else goes wrong while starting the recording of media performed by this
     * `Recorder`
     */
    @Throws(IOException::class, MediaException::class)
    fun start(format: String?, filename: String?)

    /**
     * Stops the recording of the media associated with this `Recorder` (e.g. the media being
     * sent and received in a `Call`) if it has been started and prepares this
     * `Recorder` for garbage collection.
     */
    fun stop()

    /**
     * Represents a listener interested in notifications from a `Recorder`.
     *
     * @author Lubomir Marinov
     */
    interface Listener {
        /**
         * Notifies this `Listener` that a specific `Recorder` has stopped recording
         * the media associated with it.
         *
         * @param recorder
         * the `Recorder` which has stopped recording its associated media
         */
        fun recorderStopped(recorder: Recorder)
    }

    /**
     * Put the recorder in mute state. It won't record the local input. This is used when the local
     * call is muted and we don't won't to record the local input.
     *
     * @param mute
     * the new value of the mute property
     */
    fun setMute(mute: Boolean)

    /**
     * Returns the filename we are last started or stopped recording to, null if not started.
     *
     * @return the filename we are last started or stopped recording to, null if not started.
     */
    val filename: String?

    /**
     * Sets the `RecorderEventHandler` which this `Recorder` should notify when events
     * related to recording (such as start/end of a recording) occur.
     *
     * @param eventHandler the `RecorderEventHandler` to set.
     */
    fun setEventHandler(eventHandler: RecorderEventHandler?)
    /**
     * Gets the `Synchronizer` of this `Recorder`.
     *
     * @return the `Synchronizer` of this `Recorder`.
     */
    /**
     * Sets the `Synchronizer` that this instance should use.
     *
     * synchronizer the `Synchronizer` to set.
     */
    val synchronizer: Synchronizer?

    /**
     * Gets the `MediaStream` associated with this `Recorder`.
     *
     * @return the `MediaStream` associated with this `Recorder`.
     */
    val mediaStream: MediaStream?

    companion object {
        /**
         * The name of the configuration property the value of which specifies the full path to the
         * directory with media recorded by `Recorder` (e.g. the media being sent and received in
         * a `Call`).
         */
        const val SAVED_CALLS_PATH = "neomedia.SAVED_CALLS_PATH"

        /**
         * The name of the configuration property the value of which specifies the format in which media
         * is to be recorded by `Recorder` (e.g. the media being sent and received in a
         * `Call`).
         */
        const val FORMAT = "neomedia.Recorder.FORMAT"
    }
}