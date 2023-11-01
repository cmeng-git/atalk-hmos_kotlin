/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.whiteboardobjects

import net.java.sip.communicator.service.protocol.WhiteboardPoint

/**
 * Used to access the content of instant whiteboard objects that are sent or received via the
 * WhiteboardOperationSet.
 *
 * @author Julien Waechter
 */
interface WhiteboardObjectText : WhiteboardObject {
    /**
     * Returns the coordinates of this whiteboard object.
     *
     * @return the coordinates of this object.
     */
    fun getWhiteboardPoint(): WhiteboardPoint?

    /**
     * Sets the coordinates of this whiteboard object.
     *
     * @param whiteboardPoint
     * the coordinates of this object.
     */
    fun setWhiteboardPoint(whiteboardPoint: WhiteboardPoint?)

    /**
     * Returns the WhiteboardObjectText's text.
     *
     * @return the WhiteboardObjectText's text.
     */
    fun getText(): String?

    /**
     * Sets the WhiteboardObjectText's text.
     *
     * @param text
     * the new WhiteboardObjectText's text.
     */
    fun setText(text: String?)

    /**
     * Returns the WhiteboardObjectText's font size.
     *
     * @return the WhiteboardObjectText's font size.
     */
    fun getFontSize(): Int

    /**
     * Sets the WhiteboardObjectText's font size.
     *
     * @param fontSize
     * the new WhiteboardObjectText's font size.
     */
    fun setFontSize(fontSize: Int)

    /**
     * Returns the WhiteboardObjectText's font name. (By default Dialog)
     *
     * @return the new WhiteboardObjectText's font name.
     */
    fun getFontName(): String?

    /**
     * Sets the WhiteboardObjectText's font name.
     *
     * @param fontName
     * the new WhiteboardObjectText's font name.
     */
    fun setFontName(fontName: String?)

    companion object {
        /**
         * A type string constant indicating that an object is of type text.
         */
        const val NAME = "WHITEBOARDOBJECTTEXT"
    }
}