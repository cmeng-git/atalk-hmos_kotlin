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
package org.atalk.hmos.gui.chat

import net.java.sip.communicator.service.contactlist.MetaContact
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp

/**
 * The `MetaContactChatContact` represents a `ChatContact` in a
 * user-to-user chat.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class MetaContactChatContact
/**
 * Creates an instance of `ChatContact` by passing to it the corresponding `MetaContact` and `Contact`.
 *
 * @param metaContact the `MetaContact` encapsulating the given `Contact`
 */
/*
 * Implements ChatContact#getAvatarBytes(). Delegates to metaContact.
 */
(metaContact: MetaContact?) : ChatContact<MetaContact?>(metaContact) {
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
            var name = descriptor!!.getDisplayName()
            if (StringUtils.isEmpty(name)) name = aTalkApp.getResString(R.string.service_gui_UNKNOWN_USER)
            return name
        }

    /*
     * Implements ChatContact#getUID(). Delegates to MetaContact#getMetaUID() because it's known to be unique.
     */
    override val uID: String
        get() = descriptor!!.getMetaUID()
}