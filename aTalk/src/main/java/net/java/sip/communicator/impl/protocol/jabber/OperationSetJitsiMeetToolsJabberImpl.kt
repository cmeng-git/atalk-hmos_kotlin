/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.CallPeer
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetJitsiMeetTools
import net.java.sip.communicator.service.protocol.OperationSetJitsiMeetTools.JitsiMeetRequestListener
import org.jivesoftware.smack.packet.ExtensionElement
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Jabber protocol provider implementation of [OperationSetJitsiMeetTools]
 *
 * @author Pawel Domas
 * @author Cristian Florin Ghita
 * @author Eng Chong Meng
 */
class OperationSetJitsiMeetToolsJabberImpl
/**
 * Creates new instance of `OperationSetJitsiMeetToolsJabberImpl`.
 *
 * @param parentProvider parent Jabber protocol provider service instance.
 */ constructor(private val parentProvider: ProtocolProviderServiceJabberImpl) : OperationSetJitsiMeetTools {
    /**
     * The list of [JitsiMeetRequestListener].
     */
    private val requestHandlers: MutableList<JitsiMeetRequestListener?> = CopyOnWriteArrayList()

    /**
     * {@inheritDoc}
     */
    public override fun addSupportedFeature(featureName: String?) {
        parentProvider.discoveryManager!!.addFeature(featureName)
    }

    /**
     * {@inheritDoc}
     */
    public override fun removeSupportedFeature(featureName: String?) {
        parentProvider.discoveryManager!!.removeFeature(featureName)
    }

    /**
     * {@inheritDoc}
     */
    public override fun sendPresenceExtension(chatRoom: ChatRoom?, extension: ExtensionElement?) {
        (chatRoom as ChatRoomJabberImpl?)!!.sendPresenceExtension((extension)!!)
    }

    /**
     * {@inheritDoc}
     */
    public override fun removePresenceExtension(chatRoom: ChatRoom?, extension: ExtensionElement?) {
        (chatRoom as ChatRoomJabberImpl?)!!.removePresenceExtension((extension)!!)
    }

    /**
     * {@inheritDoc}
     */
    public override fun setPresenceStatus(chatRoom: ChatRoom?, statusMessage: String?) {
        (chatRoom as ChatRoomJabberImpl?)!!.publishPresenceStatus(statusMessage)
    }

    public override fun addRequestListener(listener: JitsiMeetRequestListener?) {
        requestHandlers.add(listener)
    }

    public override fun removeRequestListener(listener: JitsiMeetRequestListener?) {
        requestHandlers.remove(listener)
    }

    /**
     * Event is fired after startmuted extension is received.
     *
     * @param startMutedFlags startMutedFlags[0] represents
     * the muted status of audio stream.
     * startMuted[1] represents the muted status of video stream.
     */
    fun notifySessionStartMuted(startMuted: BooleanArray?) {
        var handled: Boolean = false
        for (l: JitsiMeetRequestListener? in requestHandlers) {
            l!!.onSessionStartMuted(startMuted)
            handled = true
        }
        if (!handled) {
            Timber.w("Unhandled join onStartMuted Jitsi Meet request!")
        }
    }

    /**
     * {@inheritDoc}
     */
    @Throws(OperationFailedException::class)
    override fun sendJSON(callPeer: CallPeer?, jsonObject: JSONObject?, params: Map<String?, Any?>?) {
        throw OperationFailedException("Operation not supported for this protocol yet!",
                OperationFailedException.NOT_SUPPORTED_OPERATION)
    }
}