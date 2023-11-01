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
interface WhiteboardObjectImage : WhiteboardObject {
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
     * Returns the height (in pixels) of the WhiteboardObject.
     *
     * @return The height.
     */
    fun getHeight(): Double

    /**
     * Returns the width (in pixels) of the WhiteboardObject.
     *
     * @return The width.
     */
    fun getWidth(): Double

    /**
     * Sets the width (in pixels) of the WhiteboardObject.
     *
     * @param height
     * The new height.
     */
    fun setHeight(height: Double)

    /**
     * Sets the width (in pixels) of the WhiteboardObject.
     *
     * @param width
     * The new width.
     */
    fun setWidth(width: Double)

    /**
     * Specifies an image that should be displayed as the background of this object.
     *
     * @param background
     * a binary array containing the image that should be displayed as the object background.
     */
    fun setBackgroundImage(background: ByteArray?)

    /**
     * Returns a binary array containing the image that should be displayed as the background of
     * this `WhiteboardObject`.
     *
     * @return a binary array containing the image that should be displayed as the object
     * background.
     */
    fun getBackgroundImage(): ByteArray?

    companion object {
        /**
         * A type string constant indicating that an object is of type circle.
         */
        const val NAME = "WHITEBOARDOBJECTIMAGE"
    }
}