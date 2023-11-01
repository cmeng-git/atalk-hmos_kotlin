/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.alaw

import com.ibm.media.codec.audio.AudioPacketizer
import org.atalk.service.neomedia.codec.Constants
import javax.media.*
import javax.media.format.AudioFormat

/**
 * Implements an RTP packetizer for the A-law codec.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
class Packetizer : AudioPacketizer() {
    /**
     * Initializes a new `Packetizer` instance.
     */
    init {
        defaultOutputFormats = arrayOf(AudioFormat(Constants.ALAW_RTP,
                Format.NOT_SPECIFIED.toDouble(), 8, 1, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, 8,
                Format.NOT_SPECIFIED.toDouble(), Format.byteArray))
        packetSize = 160
        PLUGIN_NAME = "A-law Packetizer"
        supportedInputFormats = arrayOf(AudioFormat(AudioFormat.ALAW,
                Format.NOT_SPECIFIED.toDouble(), 8, 1, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, 8,
                Format.NOT_SPECIFIED.toDouble(), Format.byteArray))
    }

    override fun getControls(): Array<Any> {
        if (controls == null) {
            controls = arrayOf<Control>(PacketSizeAdapter(this, packetSize, true))
        }
        return controls
    }

    override fun getMatchingOutputFormats(inFormat: Format): Array<out Format> {
        val af = inFormat as AudioFormat
        val sampleRate = af.sampleRate
        supportedOutputFormats = arrayOf(
                AudioFormat(Constants.ALAW_RTP,
                        sampleRate, 8, 1,
                        Format.NOT_SPECIFIED,
                        Format.NOT_SPECIFIED,
                        8, sampleRate,
                        Format.byteArray))
        return supportedOutputFormats
    }

    @Throws(ResourceUnavailableException::class)
    override fun open() {
        this.packetSize = packetSize
        reset()
    }

    /**
     * Sets the packet size to be used by this `Packetizer`.
     *
     * @param newPacketSize
     * the new packet size to be used by this `Packetizer`
     */
    @set:Synchronized
    var packetSize: Int
        get() = super.packetSize
        private set(newPacketSize) {
            packetSize = newPacketSize
            sample_count = packetSize
            if (history == null) {
                history = ByteArray(packetSize)
            }
            else if (packetSize > history.size) {
                val newHistory = ByteArray(packetSize)
                System.arraycopy(history, 0, newHistory, 0, historyLength)
                history = newHistory
            }
        }

    private class PacketSizeAdapter(owner: Codec?, packetSize: Int, settable: Boolean) : com.sun.media.controls.PacketSizeAdapter(owner, packetSize, settable) {
        override fun setPacketSize(numBytes: Int): Int {
            var numBytes = numBytes
            if (numBytes < 10) numBytes = 10
            if (numBytes > 8000) numBytes = 8000
            packetSize = numBytes
            (owner as Packetizer).packetSize = packetSize
            return packetSize
        }
    }
}