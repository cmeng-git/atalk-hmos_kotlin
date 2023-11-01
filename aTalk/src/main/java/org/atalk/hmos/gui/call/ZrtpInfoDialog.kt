/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer
import org.atalk.hmos.R
import org.atalk.hmos.gui.call.ZrtpInfoDialog.SasVerificationListener
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.neomedia.MediaStream
import org.atalk.service.neomedia.ZrtpControl
import org.atalk.service.osgi.OSGiDialogFragment
import org.atalk.util.MediaType
import org.atalk.util.event.VideoEvent
import org.atalk.util.event.VideoListener
import timber.log.Timber
import java.awt.Component

/**
 * The dialog shows security information for ZRTP protocol. Allows user to verify/clear security authentication string.
 * It will be shown only if the call is secured (i.e. there is security control available).
 * Parent `Activity` should implement [SasVerificationListener] in order to receive SAS
 * verification status updates performed by this dialog.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ZrtpInfoDialog : OSGiDialogFragment(), CallPeerSecurityListener, VideoListener {
    /**
     * The listener object that will be notified on SAS string verification status change.
     */
    private var verificationListener: SasVerificationListener? = null

    /**
     * The [MediaAwareCallPeer] used by this dialog.
     */
    private var mediaAwarePeer: MediaAwareCallPeer<*, *, *>? = null

    /**
     * The [ZrtpControl] used as a master security controller. Retrieved from AUDIO stream.
     */
    private var masterControl: ZrtpControl? = null

    /**
     * Dialog view container for ZRTP info display
     */
    private var viewContainer: View? = null

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        if (context is SasVerificationListener) {
            verificationListener = context
        }
        super.onAttach(context)
    }

    /**
     * {@inheritDoc}
     */
    override fun onDetach() {
        verificationListener = null
        super.onDetach()
    }

    /**
     * Notifies the listener(if any) about the SAS verification update.
     *
     * @param isVerified `true` if the SAS string has been verified by the user.
     */
    private fun notifySasVerified(isVerified: Boolean) {
        if (verificationListener != null) verificationListener!!.onSasVerificationChanged(isVerified)
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Retrieves the call from manager.
        val callKey = arguments!!.getString(EXTRA_CALL_KEY)!!
        val call = CallManager.getActiveCall(callKey)
        if (call != null) {
            // Gets first media aware call peer
            val callPeers = call.getCallPeers()
            if (callPeers.hasNext()) {
                val callPeer = callPeers.next()
                if (callPeer is MediaAwareCallPeer<*, *, *>) {
                    mediaAwarePeer = callPeer
                }
            }
        }
        // Retrieves security control for master stream(AUDIO)
        if (mediaAwarePeer != null) {
            val srtpCtrl = mediaAwarePeer!!.mediaHandler.getEncryptionMethod(MediaType.AUDIO)!!
            if (srtpCtrl is ZrtpControl) {
                masterControl = srtpCtrl
            }
        }
        viewContainer = inflater.inflate(R.layout.zrtp_info_dialog, container, false)
        val cancelBtn = viewContainer!!.findViewById<View>(R.id.zrtp_ok)
        cancelBtn.setOnClickListener { view: View? -> dismiss() }
        val confirmBtn = viewContainer!!.findViewById<View>(R.id.security_confirm)
        confirmBtn.setOnClickListener { view: View? ->
            if (mediaAwarePeer!!.getCall() == null) return@setOnClickListener

            // Confirms / clears SAS confirmation status
            masterControl!!.setSASVerification(!masterControl!!.isSecurityVerified)
            updateVerificationStatus()
            notifySasVerified(masterControl!!.isSecurityVerified)
        }
        if (dialog != null) dialog!!.setTitle(R.string.service_gui_SECURITY_INFO)
        return viewContainer
    }

    /**
     * {@inheritDoc}
     */
    override fun onStart() {
        super.onStart()
        if (mediaAwarePeer == null) {
            showToast("This call does not contain media information")
            dismiss()
            return
        }
        if (masterControl == null) {
            showToast("This call does not contain security information")
            dismiss()
            return
        }
        mediaAwarePeer!!.addCallPeerSecurityListener(this)
        mediaAwarePeer!!.getMediaHandler().addVideoListener(this)
        ViewUtil.setTextViewValue(viewContainer!!, R.id.security_auth_str, getSecurityString())
        ViewUtil.setTextViewValue(viewContainer!!, R.id.security_cipher,
                getString(R.string.service_gui_security_CIPHER, masterControl!!.cipherString))
        updateVerificationStatus()
        val isAudioSecure = masterControl != null && masterControl!!.secureCommunicationStatus
        updateAudioSecureStatus(isAudioSecure)
        val videoStream = mediaAwarePeer!!.mediaHandler.getStream(MediaType.VIDEO)
        updateVideoSecureStatus(videoStream != null && videoStream.srtpControl.secureCommunicationStatus)
    }

    /**
     * Updates SAS verification status display.
     */
    private fun updateVerificationStatus() {
        val verified = masterControl!!.isSecurityVerified
        Timber.d("Is sas verified? %s", verified)
        val txt = when {
            verified -> getString(R.string.service_gui_security_STRING_COMPARED)
            else -> getString(R.string.service_gui_security_COMPARE_WITH_PARTNER_SHORT)
        }
        ViewUtil.setTextViewValue(viewContainer!!, R.id.security_compare, txt)
        val confirmTxt = when {
            verified -> getString(R.string.service_gui_security_CLEAR)
            else -> getString(R.string.service_gui_security_CONFIRM)
        }
        ViewUtil.setTextViewValue(viewContainer!!, R.id.security_confirm, confirmTxt)
    }

    /**
     * {@inheritDoc}
     */
    override fun onStop() {
        if (mediaAwarePeer != null) {
            mediaAwarePeer!!.removeCallPeerSecurityListener(this)
            mediaAwarePeer!!.mediaHandler.removeVideoListener(this)
        }
        super.onStop()
    }

    /**
     * Shows the toast on the screen with given `text`.
     *
     * @param text the message text that will be used.
     */
    private fun showToast(text: String) {
        val toast = Toast.makeText(activity, text, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Formats the security string.
     *
     * @return Returns formatted security authentication string.
     */
    private fun getSecurityString(): String {
        val securityString = masterControl!!.securityString
        return if (securityString != null) {
            securityString[0].toString() + ' ' +
                    securityString[1] + ' ' +
                    securityString[2] + ' ' +
                    securityString[3]
        } else {
            ""
        }
    }

    /**
     * Updates audio security displays according to given status flag.
     *
     * @param isSecure `true` if the audio is secure.
     */
    private fun updateAudioSecureStatus(isSecure: Boolean) {
        val audioStr = when {
            isSecure -> getString(R.string.service_gui_security_SECURE_AUDIO)
            else -> getString(R.string.service_gui_security_AUDIO_NOT_SECURED)
        }
        ViewUtil.setTextViewValue(viewContainer!!, R.id.secure_audio_text, audioStr)
        val iconId = if (isSecure) R.drawable.secure_audio_on_light else R.drawable.secure_audio_off_light
        ViewUtil.setImageViewIcon(viewContainer!!, R.id.secure_audio_icon, iconId)
    }

    /**
     * Checks video stream security status.
     *
     * @return `true` if the video is secure.
     */
    private fun isVideoSecure(): Boolean {
        val videoStream = mediaAwarePeer!!.getMediaHandler().getStream(MediaType.VIDEO)
        return videoStream != null && videoStream.srtpControl.secureCommunicationStatus
    }

    /**
     * Updates video security displays.
     *
     * @param isSecure `true` if video stream is secured.
     */
    private fun updateVideoSecureStatus(isSecure: Boolean) {
        var isVideo = false
        val videoTelephony = mediaAwarePeer!!.getProtocolProvider().getOperationSet(OperationSetVideoTelephony::class.java)
        if (videoTelephony != null) {
            /*
             * The invocation of MediaAwareCallPeer.isLocalVideoStreaming() is cheaper than the invocation of
             * OperationSetVideoTelephony.getVisualComponents(CallPeer).
             */
            isVideo = mediaAwarePeer!!.isLocalVideoStreaming()
            if (!isVideo) {
                val videos = videoTelephony.getVisualComponents(mediaAwarePeer!!)
                isVideo = videos != null && videos.isNotEmpty()
            }
        }
        ViewUtil.ensureVisible(viewContainer!!, R.id.secure_video_text, isVideo)
        ViewUtil.ensureVisible(viewContainer!!, R.id.secure_video_icon, isVideo)

        /*
         * If there's no video skip this part, as controls will be hidden.
         */
        if (!isVideo) return
        val videoText = if (isSecure) {
            getString(R.string.service_gui_security_SECURE_VIDEO)
        }
        else {
            getString(R.string.service_gui_security_VIDEO_NOT_SECURED)
        }
        runOnUiThread {
            ViewUtil.setTextViewValue(viewContainer!!, R.id.secure_video_text, videoText)
            ViewUtil.setImageViewIcon(viewContainer!!, R.id.secure_video_icon, if (isSecure) R.drawable.secure_video_on_light else R.drawable.secure_video_off_light)
        }
    }

    /**
     * The handler for the security event received. The security event represents an indication of change in the
     * security status.
     *
     * @param securityEvent the security event received
     */
    override fun securityOn(securityEvent: CallPeerSecurityOnEvent) {
        val sessionType = securityEvent.getSessionType()
        if (sessionType == CallPeerSecurityStatusEvent.AUDIO_SESSION) {
            // Audio security on
            updateAudioSecureStatus(true)
        } else if (sessionType == CallPeerSecurityStatusEvent.VIDEO_SESSION) {
            // Video security on
            updateVideoSecureStatus(true)
        }
    }

    /**
     * The handler for the security event received. The security event represents an indication of change in the
     * security status.
     *
     * @param securityEvent the security event received
     */
    override fun securityOff(securityEvent: CallPeerSecurityOffEvent) {
        val sessionType = securityEvent.getSessionType()
        if (sessionType == CallPeerSecurityStatusEvent.AUDIO_SESSION) {
            // Audio security off
            updateAudioSecureStatus(false)
        } else if (sessionType == CallPeerSecurityStatusEvent.VIDEO_SESSION) {
            // Video security off
            updateVideoSecureStatus(false)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun securityTimeout(securityTimeoutEvent: CallPeerSecurityTimeoutEvent) {}

    /**
     * {@inheritDoc}
     */
    override fun securityMessageReceived(event: CallPeerSecurityMessageEvent) {
        Timber.i("### ZRTP security Message Received: %s", event.getMessage())
    }

    /**
     * {@inheritDoc}
     */
    override fun securityNegotiationStarted(securityStartedEvent: CallPeerSecurityNegotiationStartedEvent) {}

    /**
     * Refreshes video security displays on GUI thread.
     */
    private fun refreshVideoOnUIThread() {
        runOnUiThread { updateVideoSecureStatus(isVideoSecure()) }
    }

    /**
     * {@inheritDoc}
     */
    override fun videoAdded(event: VideoEvent) {
        refreshVideoOnUIThread()
    }

    /**
     * {@inheritDoc}
     */
    override fun videoRemoved(event: VideoEvent) {
        refreshVideoOnUIThread()
    }

    /**
     * {@inheritDoc}
     */
    override fun videoUpdate(event: VideoEvent) {
        refreshVideoOnUIThread()
    }

    /**
     * The security authentication string verification status listener.
     */
    interface SasVerificationListener {
        /**
         * Called when SAS verification status is updated.
         *
         * @param isVerified `true` if SAS is verified by the user.
         */
        fun onSasVerificationChanged(isVerified: Boolean)
    }

    companion object {
        /**
         * The extra key for call ID managed by [CallManager].
         */
        private const val EXTRA_CALL_KEY = "org.atalk.hmos.call_id"

        /**
         * Creates new parametrized instance of [ZrtpInfoDialog].
         *
         * @param callKey the call key managed by [CallManager].
         * @return parametrized instance of `ZrtpInfoDialog`.
         */
        fun newInstance(callKey: String?): ZrtpInfoDialog {
            val infoDialog = ZrtpInfoDialog()
            val arguments = Bundle()
            arguments.putString(EXTRA_CALL_KEY, callKey)
            infoDialog.arguments = arguments
            return infoDialog
        }
    }
}