/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.chat.chatsession

import org.atalk.hmos.gui.chat.ChatSession
import org.jxmpp.jid.EntityBareJid
import java.util.*

/**
 * Add Source call to the CallRecord
 *
 * @author Eng Chong Meng
 */
open class ChatSessionRecord(
        /**
         * The id that uniquely identifies the chat session record.
         */
        var sessionUuid: String,
        /**
         * The owner (from) of the chat session record.
         */
        var accountUid: String, entityBareJid: EntityBareJid, chatMode: Int, chatType: Int, createTime: Date, mamDate: Date) {
    /**
     * The Session Uuid of this record
     *
     * @return sessionUuid
     */
    /**
     * The receiver of the chat session: contact bareJid or conference entity
     */
    var entityBareJid: EntityBareJid

    /**
     * 0 = 1:1 chat or 1 = multi chat session.
     *
     * @see ChatSession.MODE_XXX
     */
    var chatMode: Int
        protected set

    /**
     * Chat encryption mode: ChatSession.STATUS to store ChatFragment#chatType
     *
     * @see ChatFragment.MSGTYPE_XXX
     */
    var chatType: Int
        protected set

    /**
     * The chat session creation date.
     */
    var dateCreate: Date
        protected set
    var mamDate: Date
        protected set

    /**
     * Creates Call Record
     *
     * @param direction String
     * @param startTime Date
     * @param endTime Date
     */
    init {
        this.entityBareJid = entityBareJid
        this.chatMode = chatMode
        this.chatType = chatType
        dateCreate = createTime
        this.mamDate = mamDate
    }

    val accountUserId: String
        get() = accountUid.split(":")[1]
    val entityId: String
        get() = entityBareJid.toString()

}