/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedEvent
import net.java.sip.communicator.service.protocol.event.ChatRoomConferencePublishedListener
import org.jxmpp.jid.parts.Resourcepart
import java.util.*

/**
 * An abstract class with a default implementation of some of the methods of the `ChatRoom` interface.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class AbstractChatRoom : ChatRoom {
    /**
     * The list of listeners to be notified when a member of the chat room publishes a `ConferenceDescription`
     */
    private val conferencePublishedListeners = ArrayList<ChatRoomConferencePublishedListener>()

    /**
     * The list of all `ConferenceDescription` that were announced and are not yet processed.
     */
    private val cachedConferenceDescriptions = HashMap<Resourcepart?, ConferenceDescription?>()

    override fun join(password: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun getPrivateContactByNickname(nickname: String): Contact? {
        TODO("Not yet implemented")
    }

    /**
     * {@inheritDoc}
     */
    override fun addConferencePublishedListener(listener: ChatRoomConferencePublishedListener) {
        synchronized(conferencePublishedListeners) { conferencePublishedListeners.add(listener) }
    }

    /**
     * {@inheritDoc}
     */
    override fun removeConferencePublishedListener(listener: ChatRoomConferencePublishedListener) {
        synchronized(conferencePublishedListeners) { conferencePublishedListeners.remove(listener) }
    }

    /**
     * Returns cached `ConferenceDescription` instances.
     * @return the cached `ConferenceDescription` instances.
     */
    override fun getCachedConferenceDescriptions(): Map<String, ConferenceDescription?> {
        val tmpCachedConferenceDescriptions = HashMap<String, ConferenceDescription?>()
        synchronized(cachedConferenceDescriptions) {
            for ((key, value) in cachedConferenceDescriptions) {
                tmpCachedConferenceDescriptions[key.toString()] = value
            }
        }
        return tmpCachedConferenceDescriptions
    }

    /**
     * Returns the number of cached `ConferenceDescription` instances.
     * @return the number of cached `ConferenceDescription` instances.
     */
    @Synchronized
    override fun getCachedConferenceDescriptionSize(): Int {
        return cachedConferenceDescriptions.size
    }

    /**
     * Creates the corresponding `ChatRoomConferencePublishedEvent` and
     * notifies all `ChatRoomConferencePublishedListener`s that
     * `member` has published a conference description.
     *
     * @param member the `ChatRoomMember` that published `cd`.
     * @param cd the `ConferenceDescription` that was published.
     * @param eventType the type of the event.
     */
    protected fun fireConferencePublishedEvent(member: ChatRoomMember?, cd: ConferenceDescription?, eventType: Int) {
        val evt = ChatRoomConferencePublishedEvent(eventType, this, member!!, cd!!)
        var listeners: List<ChatRoomConferencePublishedListener>
        synchronized(conferencePublishedListeners) { listeners = LinkedList(conferencePublishedListeners) }
        for (listener in listeners) {
            listener.conferencePublished(evt)
        }
    }

    /**
     * Processes the `ConferenceDescription` instance and adds/removes it to the list of conferences.
     *
     * @param cd the `ConferenceDescription` instance to process.
     * @param participantNick the name of the participant that sent the `ConferenceDescription`.
     * @return `true` on success and `false` if fail.
     */
    protected fun processConferenceDescription(cd: ConferenceDescription, participantNick: Resourcepart?): Boolean {
        if (cd.isAvailable()) {
            if (cachedConferenceDescriptions.containsKey(participantNick)) {
                return false
            }
            cachedConferenceDescriptions[participantNick] = cd
        } else {
            val cachedDescription = cachedConferenceDescriptions[participantNick]
            if (cachedDescription == null || !cd.compareConferenceDescription(cachedDescription)) {
                return false
            }
            cachedConferenceDescriptions.remove(participantNick)
        }
        return true
    }

    /**
     * Clears the list with the chat room conferences.
     */
    protected fun clearCachedConferenceDescriptionList() {
        cachedConferenceDescriptions.clear()
    }
}