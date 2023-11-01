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
package org.atalk.hmos.gui.util

import org.jivesoftware.smack.packet.*
import org.jivesoftware.smackx.xhtmlim.XHTMLManager
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension

/**
 * Utility class that implements `XHtml` utility
 *
 * @author Eng Chong Meng
 */
object XhtmlUtil {
    /**
     * return the extracted content of the XHTMLExtension for the given message
     *
     * @param xhtmlExt XHTMLExtension extension
     * @return XHTML String of the given message
     */
    fun getXhtmlExtension(message: Message): String? {
        var xhtmlString: String? = null
        if (XHTMLManager.isXHTMLMessage(message)) {
            val xhtmlExt = message.getExtension(XHTMLExtension::class.java)

            // parse all bodies
            val bodies = xhtmlExt.bodies
            val messageBuff = StringBuilder()
            for (body in bodies) {
                messageBuff.append(body)
            }

            // Convert to proper xml format before parse
            if (messageBuff.length > 0) {
                xhtmlString = messageBuff.toString() // removes <body> start tag
                        .replace("<[bB][oO][dD][yY].*?>".toRegex(), "") // removes </body> end tag
                        .replace("</[bB][oO][dD][yY].*?>".toRegex(), "")
                        .replace("&lt;".toRegex(), "<")
                        .replace("&gt;".toRegex(), ">")
                        .replace("&apos;".toRegex(), "\"")
            }
        }
        return xhtmlString
    }
}