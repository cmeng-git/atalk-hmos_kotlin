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
package org.atalk.hmos.gui.chatroomslist.model

import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.jivesoftware.smack.util.StringUtils

/**
 * Class used to obtain UI specific data for `ChatRoom` instances.
 *
 * @author Eng Chong MEng
 */
class ChatRoomRenderer : UIChatRoomRenderer {
    override fun isSelected(chatRoomWrapper: Any): Boolean {
        return ChatRoomListAdapter.Companion.isChatRoomWrapperSelected((chatRoomWrapper as ChatRoomWrapper).chatRoomID)
    }

    override fun getDisplayName(chatRoomWrapper: Any): String {
        return (chatRoomWrapper as ChatRoomWrapper).chatRoomID
    }

    override fun getStatusMessage(chatRoomWrapper: Any): String {
        var displayDetail = getDisplayDetail(chatRoomWrapper)
        if (StringUtils.isEmpty(displayDetail)) displayDetail = getChatRoomID(chatRoomWrapper).split("@")[0]
        return displayDetail
    }

    override fun isDisplayBold(crWrapper: Any): Boolean {
        val chatRoomWrapper = crWrapper as ChatRoomWrapper
        val chatPanel = ChatSessionManager.getActiveChat(chatRoomWrapper.chatRoomID)
        if (chatPanel != null) {
            if (chatRoomWrapper.chatRoom!!.isJoined()) return true else {
                ChatSessionManager.removeActiveChat(chatPanel)
            }
        }
        return false

        // return ChatSessionManager.getActiveChat(chatRoomWrapper.getChatRoomID()) != null;
    }

    override fun getChatRoomIcon(chatRoomWrapper: Any?): Drawable? {
        return ResourcesCompat.getDrawable(aTalkApp.appResources, R.drawable.ic_chatroom, null)
    }

    override fun getChatRoomID(chatRoomWrapper: Any): String {
        return (chatRoomWrapper as ChatRoomWrapper).chatRoomID
    }

    override fun isAutoJoin(chatRoomWrapper: Any): Boolean {
        return (chatRoomWrapper as ChatRoomWrapper).isAutoJoin
    }

    override fun isBookmark(chatRoomWrapper: Any): Boolean {
        return (chatRoomWrapper as ChatRoomWrapper).isBookmarked
    }

    companion object {
        /**
         * Returns the display details for the underlying `ChatRoomWrapper`.
         *
         * @param chatRoomWrapper the `ChatRoomWrapper`, which details we're looking for
         * @return the display details for the underlying `ChatRoomWrapper`
         */
        private fun getDisplayDetail(chatRoomWrapper: Any): String {
            return (chatRoomWrapper as ChatRoomWrapper).bookmarkName!!
        }
    }
}