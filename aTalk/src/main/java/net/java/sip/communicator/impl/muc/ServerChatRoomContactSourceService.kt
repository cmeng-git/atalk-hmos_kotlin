/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc

import net.java.sip.communicator.service.contactsource.ContactQuery
import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper

/**
 * Contact source service for the existing chat rooms on the server.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class ServerChatRoomContactSourceService(private val provider: ChatRoomProviderWrapper?) : ContactSourceService {

    override val type: Int
        get() = ContactSourceService.DEFAULT_TYPE

    /**
     * Returns a user-friendly string that identifies this contact source.
     *
     * @return the display name of this contact source
     */
    override val displayName: String?
        get() = MUCActivator.resources!!.getI18NString("service.gui.SERVER_CHAT_ROOMS")

    /**
     * Creates query for the given `queryString`.
     *
     * @param queryString the string to search for
     * @return the created query
     */
    override fun createContactQuery(queryString: String): ContactQuery {
        return createContactQuery(queryString, -1)
    }

    /**
     * Creates query for the given `queryString`.
     *
     * @param queryString the string to search for
     * @param contactCount the maximum count of result contacts
     * @return the created query
     */
    override fun createContactQuery(queryString: String, contactCount: Int): ContactQuery {
        var mQueryString = queryString
        if (mQueryString == null) mQueryString = ""
        return ServerChatRoomQuery(mQueryString, this, provider)
    }

    /**
     * Returns the index of the contact source in the result list.
     *
     * @return the index of the contact source in the result list
     */
    override val index: Int
        get() = -1
}