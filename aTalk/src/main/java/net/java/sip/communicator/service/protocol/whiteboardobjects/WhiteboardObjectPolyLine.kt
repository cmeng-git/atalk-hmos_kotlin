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
interface WhiteboardObjectPolyLine : WhiteboardObject {
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

    companion object {
        /**
         * A type string constant indicating that an object is of type polyline.
         */
        const val NAME = "WHITEBOARDOBJECTPOLYLINE"
    }
}