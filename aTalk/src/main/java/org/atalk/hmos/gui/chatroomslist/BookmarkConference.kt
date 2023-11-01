/**
 *
 * Copyright 2003-2007 Jive Software.
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
package org.atalk.hmos.gui.chatroomslist

import org.jivesoftware.smackx.bookmarks.BookmarkedConference
import org.jxmpp.jid.EntityBareJid
import org.jxmpp.jid.parts.Resourcepart

/**
 * Represents a Conference Room bookmarked on the server using XEP-0048 Bookmark Storage XEP.
 *
 * @author Eng Chong Meng
 */
class BookmarkConference {
    val jid: EntityBareJid

    var nickname: Resourcepart
    var name: String?

    var password: String?
    var isAutoJoin: Boolean

    var isBookmark = false
    var isShared = false

    constructor(bookmark: BookmarkedConference) {
        name = bookmark.name
        jid = bookmark.jid
        isAutoJoin = bookmark.isAutoJoin
        nickname = bookmark.nickname
        password = bookmark.password
    }

    constructor(name: String, jid: EntityBareJid, autoJoin: Boolean, nickname: Resourcepart, password: String) {
        this.name = name
        this.jid = jid
        this.isAutoJoin = autoJoin
        this.nickname = nickname
        this.password = password
    }

    override fun equals(other: Any?): Boolean {
        if (other !is BookmarkedConference) {
            return false
        }
        return other.jid.equals(jid)
    }

    override fun hashCode(): Int {
        return jid.hashCode()
    }
}