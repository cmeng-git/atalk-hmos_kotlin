/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording

import org.atalk.service.neomedia.recording.RecorderEvent
import org.atalk.service.neomedia.recording.RecorderEventHandler
import org.atalk.util.MediaType
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

/**
 * Implements a `RecorderEventHandler` which handles `RecorderEvents` by writing them
 * to a file in JSON format.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class RecorderEventHandlerJSONImpl(filename: String?) : RecorderEventHandler {
    var file: File
    private var closed = false
    private val audioEvents = LinkedList<RecorderEvent?>()
    private val videoEvents = LinkedList<RecorderEvent?>()

    /**
     * {@inheritDoc}
     */
    init {
        file = File(filename)
        if (!file.createNewFile()) throw IOException("File exists or cannot be created: $file")
        if (!file.canWrite()) throw IOException("Cannot write to file: $file")
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun handleEvent(ev: RecorderEvent?): Boolean {
        if (closed) return false
        val mediaType = ev!!.mediaType
        val type = ev.type
        val duration = ev.duration
        val ssrc = ev.ssrc

        /*
         * For a RECORDING_ENDED event without a valid instant, find it's associated (i.e. with the
         * same SSRC) RECORDING_STARTED event and compute the RECORDING_ENDED instance based on its
         * duration.
         */
        if (RecorderEvent.Type.RECORDING_ENDED == type && ev.instant == -1L && duration != -1L) {
            val events = if (MediaType.AUDIO == mediaType) audioEvents else videoEvents
            var start: RecorderEvent? = null
            for (e in events) {
                if (RecorderEvent.Type.RECORDING_STARTED == e!!.type && e.ssrc == ssrc) {
                    start = e
                    break
                }
            }
            if (start != null) ev.instant = start.instant + duration
        }
        if (MediaType.AUDIO == mediaType) audioEvents.add(ev) else if (MediaType.VIDEO == mediaType) videoEvents.add(ev)
        try {
            writeAllEvents()
        } catch (ioe: IOException) {
            Timber.w("Failed to write recorder events to file: %s", ioe.message)
            return false
        }
        return true
    }

    /**
     * {@inheritDoc}
     */
    @Synchronized
    override fun close() {
        // XXX do we want to write everything again?
        try {
            writeAllEvents()
        } catch (ioe: IOException) {
            Timber.w("Failed to write recorder events to file: %s", ioe.message)
        } finally {
            closed = true
        }
    }

    @Throws(IOException::class)
    private fun writeAllEvents() {
        Collections.sort(audioEvents, eventComparator)
        Collections.sort(videoEvents, eventComparator)
        val nbAudio = audioEvents.size
        val nbVideo = videoEvents.size
        if (nbAudio + nbVideo > 0) {
            val writer = FileWriter(file, false)
            writer.write("{\n")
            if (nbAudio > 0) {
                writer.write("  \"audio\" : [\n")
                writeEvents(audioEvents, writer)
                if (nbVideo > 0) writer.write("  ],\n\n") else writer.write("  ]\n\n")
            }
            if (nbVideo > 0) {
                writer.write("  \"video\" : [\n")
                writeEvents(videoEvents, writer)
                writer.write("  ]\n")
            }
            writer.write("}\n")
            writer.close()
        }
    }

    @Throws(IOException::class)
    private fun writeEvents(events: List<RecorderEvent?>, writer: FileWriter) {
        val size = events.size
        for ((idx, ev) in events.withIndex()) {
            if (idx + 1 == size)
                writer.write("    ${getJSON(ev)}\n")
            else
                writer.write("    ${getJSON(ev)},\n")
        }
    }

    private fun getJSON(ev: RecorderEvent?): String {
        val json = JSONObject()
        try {
            json.put("instant", ev!!.instant)
            json.put("type", ev.type.toString())
            val mediaType = ev.mediaType
            if (mediaType != null) json.put("mediaType", mediaType.toString())
            json.put("ssrc", ev.ssrc)
            val audioSsrc = ev.audioSsrc
            if (audioSsrc != -1L) json.put("audioSsrc", audioSsrc)
            val aspectRatio = ev.aspectRatio
            if (aspectRatio != RecorderEvent.AspectRatio.ASPECT_RATIO_UNKNOWN)
                json.put("aspectRatio", aspectRatio.toString())
            val rtpTimestamp = ev.rtpTimestamp
            if (rtpTimestamp != -1L)
                json.put("rtpTimestamp", rtpTimestamp)
            val endpointId = ev.endpointId
            if (endpointId != null)
                json.put("endpointId", endpointId)
            val filename = ev.filename
            if (filename != null) {
                var bareFilename = filename
                val idx = filename.lastIndexOf('/')
                val len = filename.length
                if (idx != -1 && idx != len - 1) bareFilename = filename.substring(1 + idx, len)
                json.put("filename", bareFilename)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return json.toString()
    }

    companion object {
        /**
         * Compares `RecorderEvent`s by their instant (e.g. timestamp).
         */
        private val eventComparator: Comparator<RecorderEvent?> = Comparator { a, b -> java.lang.Long.compare(a!!.instant, b!!.instant) }
    }
}