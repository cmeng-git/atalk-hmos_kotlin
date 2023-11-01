/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util.event

import java.awt.Component
import java.util.*

/**
 * Represents an event fired by providers of visual `Component`s depicting video to notify
 * about changes in the availability of such `Component`s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class VideoEvent
/**
 * Initializes a new `VideoEvent` which is to notify about a specific
 * change in the availability of a specific visual `Component`
 * depicting video and being provided by a specific source.
 *
 * @param source
 * the source of the new `VideoEvent` and the provider
 * of the visual `Component` depicting video
 * @param type
 * the type of the availability change which has caused the new
 * `VideoEvent` to be fired
 * @param visualComponent
 * the visual `Component` depicting video
 * which had its availability in the `source` provider changed
 * @param origin
 * the origin of the video the new `VideoEvent` is to
 * notify about
 */
(source: Any?,
    /**
     * The type of availability change this `VideoEvent` notifies about
     * which is one of [.VIDEO_ADDED] and [.VIDEO_REMOVED].
     */
    val type: Int,
    /**
     * The visual `Component` depicting video which had its availability
     * changed and which this `VideoEvent` notifies about.
     */
    val visualComponent: Component?,
    /**
     * The origin of the video this `VideoEvent` notifies about which is
     * one of [.LOCAL] and [.REMOTE].
     */
    val origin: Int) : EventObject(source) {
    /**
     * Determines whether this event and, more specifically, the visual
     * `Component` it describes have been consumed and should be
     * considered owned, referenced (which is important because
     * `Component`s belong to a single `Container` at a time).
     *
     * @return `true` if this event and, more specifically, the visual
     * `Component` it describes have been consumed and should be
     * considered owned, referenced (which is important because
     * `Component`s belong to a single `Container` at a time);
     * otherwise, `false`
     */
    /**
     * The indicator which determines whether this event and, more specifically,
     * the visual `Component` it describes have been consumed and should
     * be considered owned, referenced (which is important because
     * `Component`s belong to a single `Container` at a time).
     */
    var isConsumed = false
        private set
    /**
     * Gets the origin of the video this `VideoEvent` notifies about
     * which is one of [.LOCAL] and [.REMOTE].
     *
     * @return one of [.LOCAL] and [.REMOTE] which specifies the
     * origin of the video this `VideoEvent` notifies about
     */
    /**
     * Gets the type of availability change this `VideoEvent` notifies
     * about which is one of [.VIDEO_ADDED] and [.VIDEO_REMOVED].
     *
     * @return one of [.VIDEO_ADDED] and [.VIDEO_REMOVED] which
     * describes the type of availability change this `VideoEvent`
     * notifies about
     */
    /**
     * Gets the visual `Component` depicting video which had its
     * availability changed and which this `VideoEvent` notifies about.
     *
     * @return the visual `Component` depicting video which had its
     * availability changed and which this `VideoEvent` notifies about
     */

    /**
     * Initializes a new instance of the run-time type of this instance which
     * has the same property values as this instance except for the source which
     * is set on the new instance to a specific value.
     *
     * @param source
     * the `Object` which is to be reported as the source
     * of the new instance
     * @return a new instance of the run-time type of this instance which has
     * the same property values as this instance except for the source which is
     * set on the new instance to the specified `source`
     */
    open fun clone(source: Any?): VideoEvent {
        return VideoEvent(source,
                type, visualComponent, origin)
    }

    /**
     * Consumes this event and, more specifically, marks the `Component`
     * it describes as owned, referenced in order to let other potential
     * consumers know about its current ownership status (which is important
     * because `Component`s belong to a single `Container` at a time).
     */
    fun consume() {
        isConsumed = true
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The video origin of a `VideoEvent` which is local to the executing
         * client such as a local video capture device.
         */
        const val LOCAL = 1

        /**
         * The video origin of a `VideoEvent` which is remote to the
         * executing client such as a video being remotely streamed from a
         * `CallPeer`.
         */
        const val REMOTE = 2

        /**
         * The type of a `VideoEvent` which notifies about a specific visual
         * `Component` depicting video being made available by the firing
         * provider.
         */
        const val VIDEO_ADDED = 1

        /**
         * The type of a `VideoEvent` which notifies about a specific visual
         * `Component` depicting video no longer being made available by the
         * firing provider.
         */
        const val VIDEO_REMOVED = 2

        /**
         * The type of a `VideoEvent` which notifies about an update to the
         * size of a specific visual `Component` depicting video.
         */
        const val VIDEO_SIZE_CHANGE = 3

        /**
         * Returns a human-readable representation of a specific `VideoEvent`
         * origin constant in the form of a `String` value.
         *
         * @param origin
         * one of the `VideoEvent` origin constants such as
         * [.LOCAL] or [.REMOTE]
         * @return a `String` value which gives a human-readable
         * representation of the specified `VideoEvent` `origin`
         * constant
         */
        fun originToString(origin: Int): String {
            return when (origin) {
                LOCAL -> "LOCAL"
                REMOTE -> "REMOTE"
                else -> throw IllegalArgumentException("origin")
            }
        }

        /**
         * Returns a human-readable representation of a specific `VideoEvent`
         * type constant in the form of a `String` value.
         *
         * @param type
         * one of the `VideoEvent` type constants such as
         * [.VIDEO_ADDED] or [.VIDEO_REMOVED]
         * @return a `String` value which gives a human-readable
         * representation of the specified `VideoEvent` `type` constant
         */
        fun typeToString(type: Int): String {
            return when (type) {
                VIDEO_ADDED -> "VIDEO_ADDED"
                VIDEO_REMOVED -> "VIDEO_REMOVED"
                else -> throw IllegalArgumentException("type")
            }
        }
    }
}