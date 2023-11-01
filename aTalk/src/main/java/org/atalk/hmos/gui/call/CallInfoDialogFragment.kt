/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.media.CallPeerMediaHandler
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer
import net.java.sip.communicator.util.GuiUtils
import okhttp3.internal.notify
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.MediaStreamStats
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.StreamConnector
import org.atalk.service.neomedia.StreamConnector.*
import org.atalk.service.neomedia.ZrtpControl
import org.atalk.service.osgi.OSGiDialogFragment
import org.atalk.util.MediaType
import java.awt.Dimension
import java.net.InetSocketAddress
import java.util.*

// Disambiguation
/**
 * Dialog fragment displaying technical call information. To create dialog instance factory method
 * [.newInstance] should be used. As an argument it takes the call key that identifies a call in
 * [CallManager].
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallInfoDialogFragment : OSGiDialogFragment() {
    /**
     * The call handled by this dialog.
     */
    private var call: Call<*>? = null

    /**
     * Reference to the thread that calculates media statistics and updates the view.
     */
    private var pollingThread: InfoUpdateThread? = null

    /**
     * Dialog view container for call info display
     */
    private lateinit var viewContainer: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Retrieves the call from manager.
        val callKey = arguments!!.getString(CALL_KEY_EXTRA)
        call = CallManager.getActiveCall(callKey)
        // Inflates the view.
        viewContainer = inflater.inflate(R.layout.call_info, container, true)
        val cancelBtn = viewContainer.findViewById<View>(R.id.call_info_ok)
        cancelBtn.setOnClickListener { dismiss() }

        // Sets the title.
        if (dialog != null)
            dialog!!.setTitle(R.string.service_gui_callinfo_TECHNICAL_CALL_INFO)
        return viewContainer
    }

    /**
     * Triggers the view update on UI thread.
     */
    private fun updateView() {
        runOnUiThread { if (view != null) doUpdateView() }
    }

    /**
     * Sets given `text` on the `TextView` identified by the `id`. The `TextView` must be
     * inside the view hierarchy.
     *
     * @param id the id of `TextView` we want to edit.
     * @param text string value that will be set on the `TextView`.
     */
    private fun setTextViewValue(id: Int, text: String) {
        ViewUtil.setTextViewValue(viewContainer, id, text)
    }

    /**
     * Sets given `text` on the `TextView` identified by the `id`. The `TextView` must be
     * inside `container` view hierarchy.
     *
     * @param container the `View` that contains the `TextView`.
     * @param id the id of `TextView` we want to edit.
     * @param text string value that will be set on the `TextView`.
     */
    private fun setTextViewValue(container: View, id: Int, text: String?) {
        ViewUtil.setTextViewValue(container, id, text)
    }

    /**
     * Ensures that the `View` is currently in visible or hidden state which depends on `isVisible` flag.
     *
     * @param container parent `View` that contains displayed `View`.
     * @param viewId the id of `View` that will be shown/hidden.
     * @param isVisible flag telling whether the `View` has to be shown or hidden.
     */
    private fun ensureVisible(container: View, viewId: Int, isVisible: Boolean) {
        ViewUtil.ensureVisible(container, viewId, isVisible)
    }

    /**
     * Updates the view to display actual call information.
     */
    private fun doUpdateView() {
        val conference = call!!.getConference()
        val calls = conference.calls
        if (calls.isEmpty()) return
        val aCall = calls[0]
        // Identity.
        setTextViewValue(R.id.identity, aCall.pps.accountID.displayName!!)
        // Peer count.
        setTextViewValue(R.id.peerCount, conference.callPeerCount.toString())
        // Conference focus.
        setTextViewValue(R.id.conferenceFocus, conference.isConferenceFocus.toString())
        // Preferred transport.
        val preferredTransport = aCall.pps.transportProtocol
        setTextViewValue(R.id.transport, preferredTransport.toString())
        val callPeers = conference.callPeers
        if (callPeers.isNotEmpty()) constructPeerInfo(callPeers[0]!!)
    }

    /**
     * Constructs peer info.
     *
     * @param callPeer the `CallPeer`, for which we'll construct the info.
     */
    private fun constructPeerInfo(callPeer: CallPeer) {
        // Peer name.
        setTextViewValue(R.id.callPeer, callPeer.getAddress()!!)

        // Call duration.
        val startTime = Date(callPeer.getCallDurationStartTime())
        val durationStr = GuiUtils.formatTime(startTime.time, System.currentTimeMillis())
        setTextViewValue(R.id.callDuration, durationStr)
        val callPeerMediaHandler: CallPeerMediaHandler<*>?
        if (callPeer is MediaAwareCallPeer<*, *, *>) {
            callPeerMediaHandler = callPeer.mediaHandler
            // Audio stream info.
            updateAudioVideoInfo(callPeerMediaHandler!!, MediaType.AUDIO)
            // Video stream info.
            updateAudioVideoInfo(callPeerMediaHandler, MediaType.VIDEO)
            // ICE info.
            updateIceSection(callPeerMediaHandler)
        }
    }

    /**
     * Updates section displaying ICE information for given `callPeerMediaHandler`.
     *
     * @param callPeerMediaHandler the call peer for which ICE information will be displayed.
     */
    private fun updateIceSection(callPeerMediaHandler: CallPeerMediaHandler<*>?) {
        // ICE state.
        var iceState: String? = null
        if (callPeerMediaHandler != null) {
            iceState = callPeerMediaHandler.iceState
        }
        val iceStateVisible = iceState != null && iceState != "Terminated"
        ensureVisible(viewContainer, R.id.iceState, iceStateVisible)
        ensureVisible(viewContainer, R.id.iceStateLabel, iceStateVisible)
        if (iceStateVisible) {
            val resource = resources
            val strId = resource.getIdentifier("service_gui_callinfo_ICE_STATE_" + iceState!!.uppercase(),
                    "string", activity!!.packageName)
            setTextViewValue(R.id.iceState, resource.getString(strId))
        }

        // Total harvesting time.
        var harvestingTime = 0L
        if (callPeerMediaHandler != null) {
            harvestingTime = callPeerMediaHandler.totalHarvestingTime
        }
        val isTotalHarvestTime = harvestingTime != 0L
        ensureVisible(viewContainer, R.id.totalHarvestTime, isTotalHarvestTime)
        ensureVisible(viewContainer, R.id.totalHarvestLabel, isTotalHarvestTime)
        if (isTotalHarvestTime) {
            val harvestCount = callPeerMediaHandler!!.nbHarvesting
            setTextViewValue(viewContainer, R.id.totalHarvestTime,
                    getString(R.string.service_gui_callinfo_HARVESTING_DATA, harvestingTime, harvestCount))
        }

        // Current harvester time if ICE agent is harvesting.
        val harvesterNames = arrayOf(
                "GoogleTurnCandidateHarvester",
                "GoogleTurnSSLCandidateHarvester",
                "HostCandidateHarvester",
                "JingleNodesHarvester",
                "StunCandidateHarvester",
                "TurnCandidateHarvester",
                "UPNPHarvester"
        )
        val harvesterLabels = intArrayOf(
                R.id.googleTurnLabel,
                R.id.googleTurnSSlLabel,
                R.id.hostHarvesterLabel,
                R.id.jingleNodesLabel,
                R.id.stunHarvesterLabel,
                R.id.turnHarvesterLabel,
                R.id.upnpHarvesterLabel
        )
        val harvesterValues = intArrayOf(
                R.id.googleTurnTime,
                R.id.googleTurnSSlTime,
                R.id.hostHarvesterTime,
                R.id.jingleNodesTime,
                R.id.stunHarvesterTime,
                R.id.turnHarvesterTime,
                R.id.upnpHarvesterTime)
        for (i in harvesterLabels.indices) {
            harvestingTime = 0
            if (callPeerMediaHandler != null) {
                harvestingTime = callPeerMediaHandler.getHarvestingTime(harvesterNames[i])
            }
            val visible = harvestingTime != 0L
            ensureVisible(viewContainer, harvesterLabels[i], visible)
            ensureVisible(viewContainer, harvesterValues[i], visible)
            if (visible) {
                setTextViewValue(viewContainer, harvesterValues[i],
                        getString(R.string.service_gui_callinfo_HARVESTING_DATA, harvestingTime,
                                callPeerMediaHandler!!.nbHarvesting))
            }
        }
    }

    /**
     * Creates the string for the stream encryption method (null, MIKEY, SDES, ZRTP) used for a given media stream (type
     * AUDIO or VIDEO).
     *
     * @param callPeerMediaHandler The media handler containing the different media streams.
     * @param mediaStream the `MediaStream` that gives us access to audio/video info.
     * @param mediaType The media type used to determine which stream of the media handler must returns it encryption method.
     */
    private fun getStreamEncryptionMethod(callPeerMediaHandler: CallPeerMediaHandler<*>, mediaStream: MediaStream?, mediaType: MediaType): String {
        val resources = resources
        var transportProtocolString = ""
        val transportProtocol = mediaStream!!.transportProtocol
        if (transportProtocol != null) {
            transportProtocolString = transportProtocol.toString()
        }
        val rtpType: String
        val srtpControl = callPeerMediaHandler.getEncryptionMethod(mediaType)
        // If the stream is secured.
        rtpType = if (srtpControl != null) {
            val info = if (srtpControl is ZrtpControl) {
                "ZRTP " + srtpControl.cipherString
            }
            else {
                "SDES"
            }
            (resources.getString(R.string.service_gui_callinfo_MEDIA_STREAM_SRTP) + " (" + resources.getString(R.string.service_gui_callinfo_KEY_EXCHANGE_PROTOCOL) + ": " + info + ")")
        }
        else {
            resources.getString(R.string.service_gui_callinfo_MEDIA_STREAM_RTP)
        }
        return "$transportProtocolString / $rtpType"
    }

    /**
     * Updates audio video peer info.
     *
     * @param callPeerMediaHandler The `CallPeerMediaHandler` containing the AUDIO/VIDEO stream.
     * @param mediaType The media type used to determine which stream of the media handler will be used.
     */
    private fun updateAudioVideoInfo(callPeerMediaHandler: CallPeerMediaHandler<*>, mediaType: MediaType) {
        val container = if (mediaType === MediaType.AUDIO) viewContainer.findViewById(R.id.audioInfo)
        else viewContainer.findViewById<View>(R.id.videoInfo)
        val mediaStream = callPeerMediaHandler.getStream(mediaType)
        var mediaStreamStats: MediaStreamStats? = null
        if (mediaStream != null) {
            mediaStreamStats = mediaStream.mediaStreamStats
        }

        // Hides the whole section if stats are not available.
        ensureVisible(viewContainer, container.id, mediaStreamStats != null)
        if (mediaStreamStats == null) {
            return
        }

        // Sets the encryption status String.
        setTextViewValue(container, R.id.mediaTransport, getStreamEncryptionMethod(callPeerMediaHandler, mediaStream, mediaType))
        // Set the title label to Video info if it's a video stream.
        if (mediaType === MediaType.VIDEO) {
            setTextViewValue(container, R.id.audioVideoLabel, getString(R.string.service_gui_callinfo_VIDEO_INFO))
        }
        var hasVideoSize = false
        if (mediaType === MediaType.VIDEO) {
            val downloadVideoSize = mediaStreamStats.downloadVideoSize
            val uploadVideoSize = mediaStreamStats.uploadVideoSize
            // Checks that at least one video stream is active.
            if (downloadVideoSize != null || uploadVideoSize != null) {
                hasVideoSize = true
                setTextViewValue(container, R.id.videoSize,
                        DOWN_ARROW + " " + videoSizeToString(downloadVideoSize) + " "
                                + UP_ARROW + " " + videoSizeToString(uploadVideoSize))
            }
        }

        // Shows video size if it's available(always false for AUDIO)
        ensureVisible(container, R.id.videoSize, hasVideoSize)
        ensureVisible(container, R.id.videoSizeLabel, hasVideoSize)

        // Codec.
        setTextViewValue(container, R.id.codec, (mediaStreamStats.encoding + " / " + mediaStreamStats.encodingClockRate) + " Hz")
        var displayedIpPort = false

        // ICE candidate type.
        val iceCandidateExtendedType = callPeerMediaHandler.getICECandidateExtendedType(mediaType.toString())
        val iceCandidateExtVisible = iceCandidateExtendedType != null
        ensureVisible(container, R.id.iceExtType, iceCandidateExtVisible)
        ensureVisible(container, R.id.iceExtTypeLabel, iceCandidateExtVisible)
        if (iceCandidateExtVisible) {
            setTextViewValue(container, R.id.iceExtType, iceCandidateExtendedType)
            displayedIpPort = true
        }

        // Local host address.
        val iceLocalHostAddress = callPeerMediaHandler.getICELocalHostAddress(mediaType.toString())
        val iceLocalHostVisible = iceLocalHostAddress != null
        ensureVisible(container, R.id.iceLocalHost, iceLocalHostVisible)
        ensureVisible(container, R.id.localHostLabel, iceLocalHostVisible)
        if (iceLocalHostVisible) {
            setTextViewValue(container, R.id.iceLocalHost, iceLocalHostAddress!!.address.hostAddress!!
                    + "/" + iceLocalHostAddress.port)
            displayedIpPort = true
        }

        // Local reflexive address.
        val iceLocalReflexiveAddress = callPeerMediaHandler.getICELocalReflexiveAddress(mediaType.toString())
        val iceLocalReflexiveVisible = iceLocalReflexiveAddress != null
        ensureVisible(container, R.id.iceLocalReflx, iceLocalReflexiveVisible)
        ensureVisible(container, R.id.iceLocalReflxLabel, iceLocalReflexiveVisible)
        if (iceLocalReflexiveVisible) {
            setTextViewValue(container, R.id.iceLocalReflx, iceLocalReflexiveAddress!!.address.hostAddress!!
                    + "/" + iceLocalReflexiveAddress.port)
            displayedIpPort = true
        }

        // Local relayed address.
        val iceLocalRelayedAddress = callPeerMediaHandler.getICELocalRelayedAddress(mediaType.toString())
        val iceLocalRelayedVisible = iceLocalRelayedAddress != null
        ensureVisible(container, R.id.iceLocalRelayed, iceLocalRelayedVisible)
        ensureVisible(container, R.id.iceLocalRelayedLabel, iceLocalRelayedVisible)
        if (iceLocalRelayedAddress != null) {
            setTextViewValue(container, R.id.iceLocalRelayed, iceLocalRelayedAddress.address.hostAddress!!
                    + "/" + iceLocalRelayedAddress.port)
            displayedIpPort = true
        }

        // Remote relayed address.
        val iceRemoteRelayedAddress = callPeerMediaHandler.getICERemoteRelayedAddress(mediaType.toString())
        val isIceRemoteRelayed = iceRemoteRelayedAddress != null
        ensureVisible(container, R.id.iceRemoteRelayed, isIceRemoteRelayed)
        ensureVisible(container, R.id.iceRemoteRelayedLabel, isIceRemoteRelayed)
        if (isIceRemoteRelayed) {
            setTextViewValue(container, R.id.iceRemoteRelayed, iceRemoteRelayedAddress!!.address.hostAddress!!
                    + "/" + iceRemoteRelayedAddress.port)
            displayedIpPort = true
        }

        // Remote reflexive address.
        val iceRemoteReflexiveAddress = callPeerMediaHandler.getICERemoteReflexiveAddress(mediaType.toString())
        val isIceRemoteReflexive = iceRemoteReflexiveAddress != null
        ensureVisible(container, R.id.iceRemoteReflexive, isIceRemoteReflexive)
        ensureVisible(container, R.id.iceRemoteReflxLabel, isIceRemoteReflexive)
        if (isIceRemoteReflexive) {
            setTextViewValue(container, R.id.iceRemoteReflexive,
                    iceRemoteReflexiveAddress!!.address.hostAddress!! + "/" + iceRemoteReflexiveAddress.port)
            displayedIpPort = true
        }

        // Remote host address.
        val iceRemoteHostAddress = callPeerMediaHandler.getICERemoteHostAddress(mediaType.toString())
        val isIceRemoteHost = iceRemoteHostAddress != null
        ensureVisible(container, R.id.iceRemoteHostLabel, isIceRemoteHost)
        ensureVisible(container, R.id.iceRemoteHost, isIceRemoteHost)
        if (isIceRemoteHost) {
            setTextViewValue(container, R.id.iceRemoteHost, iceRemoteHostAddress!!.address.hostAddress!!
                    + "/" + iceRemoteHostAddress.port)
            displayedIpPort = true
        }

        // If the stream does not use ICE, then show the transport IP/port.
        ensureVisible(container, R.id.localIp, !displayedIpPort)
        ensureVisible(container, R.id.localIpLabel, !displayedIpPort)
        ensureVisible(container, R.id.remoteIp, !displayedIpPort)
        ensureVisible(container, R.id.remoteIpLabel, !displayedIpPort)
        if (!displayedIpPort) {
            setTextViewValue(container, R.id.localIp, mediaStreamStats.localIPAddress
                    + " / " + mediaStreamStats.localPort)
            setTextViewValue(container, R.id.remoteIp, mediaStreamStats.remoteIPAddress
                    + " / " + mediaStreamStats.remotePort)
        }

        // Bandwidth.
        val bandwidthStr = (DOWN_ARROW + " " + mediaStreamStats.downloadRateKiloBitPerSec.toInt() + " Kbps " + " " + UP_ARROW + " "
                + mediaStreamStats.uploadRateKiloBitPerSec.toInt() + " Kbps")
        setTextViewValue(container, R.id.bandwidth, bandwidthStr)

        // Loss rate.
        val lossRateStr = (DOWN_ARROW + " " + mediaStreamStats.downloadPercentLoss.toInt() + "% " + UP_ARROW + " "
                + mediaStreamStats.uploadPercentLoss.toInt() + "%")
        setTextViewValue(container, R.id.lossRate, lossRateStr)

        // Decoded with FEC.
        setTextViewValue(container, R.id.decodedWithFEC, java.lang.String.valueOf(mediaStreamStats.nbFec))

        // Discarded percent.
        setTextViewValue(container, R.id.discardedPercent, mediaStreamStats.percentDiscarded.toInt().toString() + "%")

        // Discarded total.
        val discardedTotalStr = mediaStreamStats.nbDiscarded.toString() + " (" + mediaStreamStats.nbDiscardedLate.toString() + " late, " +
        mediaStreamStats.nbDiscardedFull.toString() + " full, " + mediaStreamStats.nbDiscardedShrink.toString() + " shrink, " +
        mediaStreamStats.nbDiscardedReset.toString() + " reset)"
        setTextViewValue(container, R.id.discardedTotal, discardedTotalStr)

        // Adaptive jitter buffer.
        setTextViewValue(container, R.id.adaptiveJitterBuffer, if (mediaStreamStats.isAdaptiveBufferEnabled) "enabled" else "disabled")

        // Jitter buffer delay.
        val jitterDelayStr = ("~" + mediaStreamStats.jitterBufferDelayMs + "ms; currently in queue: "
                + mediaStreamStats.packetQueueCountPackets) + "/" + mediaStreamStats.packetQueueSize + " packets"
        setTextViewValue(container, R.id.jitterBuffer, jitterDelayStr)

        // RTT
        val naStr = getString(R.string.service_gui_callinfo_NA)
        val rttMs = mediaStreamStats.rttMs
        val rttStr = if (rttMs != -1L) "$rttMs ms" else naStr
        setTextViewValue(container, R.id.RTT, rttStr)

        // Jitter.
        setTextViewValue(container, R.id.jitter,
                DOWN_ARROW + " " + mediaStreamStats.downloadJitterMs.toInt() + " ms " + UP_ARROW + mediaStreamStats.uploadJitterMs.toInt() + " ms")
    }

    /**
     * Converts a video size Dimension into its String representation.
     *
     * @param videoSize The video size Dimension, containing the width and the height of the video.
     * @return The String representation of the video width and height, or a String with "Not Available (N.A.)" if the
     * videoSize is null.
     */
    private fun videoSizeToString(videoSize: Dimension?): String {
        return if (videoSize == null) {
            getString(R.string.service_gui_callinfo_NA)
        }
        else videoSize.getWidth().toInt().toString() + " x " + videoSize.getHeight().toInt()
    }

    override fun onStart() {
        super.onStart()
        if (call == null) {
            dismiss()
            return
        }
        startUpdateThread()
    }

    override fun onStop() {
        stopUpdateThread()
        super.onStop()
    }

    /**
     * Starts the update thread.
     */
    private fun startUpdateThread() {
        pollingThread = InfoUpdateThread()
        pollingThread!!.start()
    }

    /**
     * Stops the update thread ensuring that it has finished it's job.
     */
    private fun stopUpdateThread() {
        if (pollingThread != null) {
            pollingThread!!.ensureFinished()
            pollingThread = null
        }
    }

    /**
     * Calculates media statistics for all peers. This must be executed on non UI thread or the network on UI thread
     * exception will occur.
     */
    private fun updateMediaStats() {
        val conference = call!!.getConference()
        for (callPeer in conference.callPeers) {
            if (callPeer !is MediaAwareCallPeer<*, *, *>) {
                continue
            }
            val callPeerMediaHandler = callPeer.getMediaHandler() ?: continue
            calcStreamMediaStats(callPeerMediaHandler.getStream(MediaType.AUDIO))
            calcStreamMediaStats(callPeerMediaHandler.getStream(MediaType.VIDEO))
        }
    }

    /**
     * Calculates media stream statistics.
     *
     * @param mediaStream the media stream that will have it's statistics recalculated.
     */
    private fun calcStreamMediaStats(mediaStream: MediaStream?) {
        if (mediaStream == null) return
        mediaStream.mediaStreamStats.updateStats()
    }

    /**
     * The thread that periodically recalculates media stream statistics and triggers view updates.
     */
    internal inner class InfoUpdateThread : Thread() {
        /**
         * The polling loop flag.
         */
        private var run = true

        /**
         * Stops and joins the thread.
         */
        fun ensureFinished() {
            try {
                // Immediately stop any further update attempt
                run = false
                synchronized(this) { this.notify() }
                this.join()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }

        override fun run() {
            synchronized(this) {
                while (run) {
                    try {
                        // Recalculate statistics and refresh view.
                        updateMediaStats()
                        updateView()

                        // place loop in wait for next update and release lock
                        (this as Object).wait(1000)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
    }

    companion object {
        /**
         * The extra key pointing to the "call key" that will be used to retrieve active call from [CallManager].
         */
        private const val CALL_KEY_EXTRA = "CALL_KEY"

        /**
         * Unicode constant for up arrow.
         */
        private const val UP_ARROW = "\u2191"

        /**
         * Unicode constant for down arrow.
         */
        private const val DOWN_ARROW = "\u2193"

        /**
         * Factory method that creates new dialog fragment and injects the `callKey` into the dialog arguments
         * bundle.
         *
         * @param callKey the key string that identifies active call in [CallManager].
         * @return new, parametrized instance of [CallInfoDialogFragment].
         */
        fun newInstance(callKey: String?): CallInfoDialogFragment {
            val f = CallInfoDialogFragment()
            val args = Bundle()
            args.putString(CALL_KEY_EXTRA, callKey)
            f.arguments = args
            return f
        }
    }
}