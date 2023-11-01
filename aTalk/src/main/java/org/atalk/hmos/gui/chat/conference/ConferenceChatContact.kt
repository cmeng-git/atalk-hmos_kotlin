/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.hmos.gui.chat.conference

import net.java.sip.communicator.service.protocol.ChatRoomMember
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatContact

/**
 * The `ConferenceChatContact` represents a `ChatContact` in a conference chat.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class ConferenceChatContact
/**
 * Creates an instance of `ChatContact` by passing to it the `ChatRoomMember` for which it is created.
 *
 * @param chatRoomMember the `ChatRoomMember` for which this `ChatContact` is created.
 */
(chatRoomMember: ChatRoomMember?) : ChatContact<ChatRoomMember?>(chatRoomMember) {
    /**
     * Implements ChatContact#getAvatarBytes(). Delegates to chatRoomMember.
     */
    override fun getAvatarBytes(): ByteArray? {
        return descriptor!!.getAvatar()
    }

    /**
     * Returns the contact name.
     *
     * @return the contact name
     */
    override val name: String?
        get() {
            var name = descriptor!!.getNickName()
            if (StringUtils.isEmpty(name)) name = aTalkApp.getResString(R.string.service_gui_UNKNOWN_USER)
            return name
        }

    val role: ChatRoomMemberRole?
        get() = descriptor!!.getRole()

    /**
     * Implements ChatContact#getUID(). Delegates to ChatRoomMember#getContactAddress() because it's supposed to be unique.
     */
    override val uID: String
        get() = descriptor!!.getContactAddress()
}