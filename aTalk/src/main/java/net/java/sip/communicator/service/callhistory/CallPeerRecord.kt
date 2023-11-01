/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("LeakingThis")

package net.java.sip.communicator.service.callhistory

import net.java.sip.communicator.service.protocol.CallPeerState
import org.jxmpp.jid.BareJid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import java.util.*

/**
 * Structure used for encapsulating data when writing or reading Call History Data. Also These
 * records are used for returning data from the Call History Service
 *
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
open class CallPeerRecord(peerAddress: String, startTime: Date, endTime: Date?) {
    /**
     * The peer address - entityFullJid or callParticipantID.
     */
    open var peerAddress: String? = null
        protected set

    /**
     * The display name - entityJid or callParticipantNames.
     */
    open var displayName: String? = null
        protected set

    /**
     * The start time of the record.
     */
    open var startTime: Date? = null
        protected set

    /**
     * The end time of the record.
     */
    open var endTime: Date? = null
        protected set

    /**
     * The secondary address of the peer - secondaryCallParticipantID
     */
    var peerSecondaryAddress: String? = null

    /**
     * The state of `CallPeer`.
     */
    open var state = CallPeerState.UNKNOWN
        protected set

    /**
     * Creates CallPeerRecord
     */
    init {
        this.peerAddress = peerAddress
        this.startTime = startTime
        this.endTime = endTime
    }

    val peerJid: BareJid?
        get() {
            val peer = peerAddress!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            try {
                return JidCreate.bareFrom(peer)
            } catch (e: XmppStringprepException) {
                e.printStackTrace()
            }
            return null
        }
}