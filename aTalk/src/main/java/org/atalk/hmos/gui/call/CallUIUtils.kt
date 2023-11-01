package org.atalk.hmos.gui.call

import android.text.TextUtils
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.util.UtilActivator
import org.jivesoftware.smack.util.StringUtils

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
object CallUIUtils {
    const val DEFAULT_PERSONAL_PHOTO = "personphoto"

    fun getCalleeAvatar(incomingCall: Call<*>?): ByteArray? {
        val peers = incomingCall!!.getCallPeers()
        if (incomingCall.callPeerCount == 1) {
            val peer = peers.next()
            val image = CallManager.getPeerImage(peer)
            if (image != null && image.isNotEmpty()) return image
        }
        return UtilActivator.resources.getImageInBytes(DEFAULT_PERSONAL_PHOTO)
    }

    fun getCalleeAddress(incomingCall: Call<*>): String {
        val peers = incomingCall.getCallPeers()
        var textAddress = ""
        while (peers.hasNext()) {
            val peer = peers.next()
            // More peers.
            if (peers.hasNext()) {
                val peerAddress = getPeerDisplayAddress(peer)
                if (StringUtils.isNotEmpty(peerAddress)) textAddress = "$textAddress$peerAddress, "
            } else {
                val peerAddress = getPeerDisplayAddress(peer)
                if (StringUtils.isNotEmpty(peerAddress)) textAddress = peerAddress!!
            }
        }
        return textAddress
    }

    /**
     * Initializes the label of the received call.
     *
     * @param incomingCall the call
     */
    fun getCalleeDisplayName(incomingCall: Call<*>): String {
        val peers = incomingCall.getCallPeers()
        val hasMorePeers = false
        var textDisplayName = ""
        while (peers.hasNext()) {
            val peer = peers.next()

            // More peers.
            textDisplayName = if (peers.hasNext()) {
                textDisplayName + getPeerDisplayName(peer) + ", "
            } else {
                getPeerDisplayName(peer)
            }
        }

        // Remove the last semicolon.
        if (hasMorePeers) textDisplayName = textDisplayName.substring(0, textDisplayName.lastIndexOf(","))
        return textDisplayName
    }

    /**
     * Finds first `Contact` for given `Call`.
     *
     * @param call the call to check for `Contact`.
     *
     * @return first `Contact` for given `Call`.
     */
    fun getCallee(call: Call<*>): Contact? {
        val peers = call.getCallPeers()
        return if (peers.hasNext()) {
            peers.next().getContact()!!
        } else null
    }

    /**
     * A informative text to show for the peer. If display name is missing return the address.
     *
     * @param peer the peer.
     *
     * @return the text contain display name.
     */
    private fun getPeerDisplayName(peer: CallPeer): String {
        val displayName = peer.getDisplayName()
        return if (TextUtils.isEmpty(displayName)) peer.getAddress() else displayName!!
    }

    /**
     * A informative text to show for the peer. If display name and address are the same return null.
     *
     * @param peer the peer.
     *
     * @return the text contain address.
     */
    private fun getPeerDisplayAddress(peer: CallPeer): String? {
        val peerAddress = peer.getAddress()
        return if (TextUtils.isEmpty(peerAddress)) null else {
            if (peerAddress.equals(peer.getDisplayName(), ignoreCase = true)) null else peerAddress
        }
    }
}