/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol

import java.util.*

/**
 * A point representing a location in `(x,y)` coordinate space, specified in integer
 * precision.
 *
 *
 * This class has been inspired by the java.awt.Point class.
 *
 *
 *
 * @author Julien Waechter
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class WhiteboardPoint
/**
 * Constructs and initializes a point at the specified `(x,y)` location in the coordinate
 * space.
 *
 * @param x
 * the X coordinate of the newly constructed `Point`
 * @param y
 * the Y coordinate of the newly constructed `Point`
 * @since 1.0
 */
(
        /**
         * The X coordinate of this WhiteboadPoint.
         */
        private var x: Double,
        /**
         * The Y coordinate of this `Point`.
         */
        private var y: Double) : Cloneable {
    /**
     * Constructs and initializes a point with the same location as the specified `Point`
     * object.
     *
     * @param p
     * a point
     */
    constructor(p: WhiteboardPoint) : this(p.x, p.y) {}

    /**
     * Returns the X coordinate of this `WhiteboardPoint`.
     *
     * @return the x coordinate of this `WhiteboardPoint`.
     */
    fun getX(): Double {
        return x
    }

    /**
     * Returns the Y coordinate of this `WhiteboardPoint`.
     *
     * @return the y coordinate of this `WhiteboardPoint`.
     */
    fun getY(): Double {
        return y
    }

    /**
     * Sets a new value to the x coordinate.
     *
     * @param x
     * the new value of the x coordinate
     */
    fun setX(x: Double) {
        this.x = x
    }

    /**
     * Sets a new value to the y coordinate.
     *
     * @param y
     * the new value of the y coordinate
     */
    fun setY(y: Double) {
        this.y = y
    }

    /**
     * Determines whether or not two points are equal. Two instances of `WhiteboardPoint` are
     * equal if the values of their `x` and `y` member fields, representing their
     * position in the coordinate space, are the same.
     *
     * @param obj
     * an object to be compared with this `WhiteboardPoint`
     *
     * @return `true` if the object to be compared is an instance of `WhiteboardPoint`
     * and has the same values; `false` otherwise.
     */
    override fun equals(obj: Any?): Boolean {
        if (obj is WhiteboardPoint) {
            val pt = obj
            return x == pt.x && y == pt.y
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(x, y)
    }

    /**
     * Returns a string representation of this point and its location in the `(x,y)`
     * coordinate space. This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between implementations.
     *
     * The returned string may be empty but may not be `null`.
     *
     * @return a string representation of this point
     */
    override fun toString(): String {
        return javaClass.name + "[x=" + x + ",y=" + y + "]"
    }

    /**
     * Creates and returns a copy of this `WhiteboardPoint`.
     *
     * @return a clone of this `WhiteboardPoint` instance.
     */
    override fun clone(): Any {
        return WhiteboardPoint(this)
    }

    /**
     * Calculates the distance from this point the given point.
     *
     * @param p
     * the point to which to calculate the distance
     * @return the distance between this point and the given point
     */
    fun distance(p: WhiteboardPoint): Double {
        val PX = p.getX() - getX()
        val PY = p.getY() - getY()
        return Math.sqrt(PX * PX + PY * PY)
    }
}