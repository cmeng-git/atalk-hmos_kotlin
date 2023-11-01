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

/**
 * Represents a `VideoEvent` which notifies about an update to the size
 * of a specific visual `Component` depicting video.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class SizeChangeVideoEvent
/**
 * Initializes a new `SizeChangeVideoEvent` which is to notify about
 * an update to the size of a specific visual `Component` depicting video.
 *
 * @param source the source of the new `SizeChangeVideoEvent`
 * @param visualComponent the visual `Component` depicting video with the updated size
 * @param origin the origin of the video the new `SizeChangeVideoEvent` is to notify about
 * @param width the new width of `visualComponent`
 * @param height the new height of `visualComponent`
 */
(source: Any?, visualComponent: Component?, origin: Int,
    /**
     * The new width of the associated visual `Component`.
     */
    val width: Int,
    /**
     * The new height of the associated visual `Component`.
     */
    val height: Int) : VideoEvent(source, VideoEvent.VIDEO_SIZE_CHANGE, visualComponent, origin) {
    /**
     * Gets the new height of the associated visual `Component`.
     *
     * @return the new height of the associated visual `Component`
     */
    /**
     * Gets the new width of the associated visual `Component`.
     *
     * @return the new width of the associated visual `Component`
     */

    /**
     * {@inheritDoc}
     *
     *
     * Makes sure that the cloning of this instance initializes a new
     * `SizeChangeVideoEvent` instance.
     */
    override fun clone(source: Any?): VideoEvent {
        return SizeChangeVideoEvent(source, visualComponent, origin, width, height)
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}