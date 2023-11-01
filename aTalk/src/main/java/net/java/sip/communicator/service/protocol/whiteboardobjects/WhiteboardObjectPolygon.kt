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
interface WhiteboardObjectPolygon : WhiteboardObject {
    /**
     * The fill state of the WhiteboardObject.
     *
     * @return True is filled, false is unfilled.
     */
    fun isFill(): Boolean

    /**
     * Sets the fill state of the WhiteboardObject. True is filled, false is unfilled.
     *
     * @param fill
     * The new fill state.
     */
    fun setFill(fill: Boolean)

    /**
     * Returns a list of all the `WhiteboardPoint` instances that this
     * `WhiteboardObject` is composed of.
     *
     * @return the list of `WhiteboardPoint`s composing this object.
     */
    fun getPoints(): List<WhiteboardPoint?>?

    /**
     * Sets the list of `WhiteboardPoint` instances that this `WhiteboardObject` is
     * composed of.
     *
     * @param points
     * the list of `WhiteboardPoint` instances that this `WhiteboardObject` is
     * composed of.
     */
    fun setPoints(points: List<WhiteboardPoint?>?)

    /**
     * Specifies the background color for this object. The color parameter must be encoded with
     * standard RGB encoding: bits 24-31 are alpha, 16-23 are red, 8-15 are green, 0-7 are blue.
     *
     * @param color
     * the color that we'd like to set for the background of this `WhiteboardObject`
     * (using standard RGB encoding).
     */
    fun setBackgroundColor(color: Int)

    /**
     * Returns an integer representing the background color of this object. The return value uses
     * standard RGB encoding: bits 24-31 are alpha, 16-23 are red, 8-15 are green, 0-7 are blue.
     *
     * @return the RGB value of the background color of this object.
     */
    fun getBackgroundColor(): Int

    companion object {
        /**
         * A type string constant indicating that an object is of type polygon.
         */
        const val NAME = "WHITEBOARDOBJECTPOLYGON"
    }
}