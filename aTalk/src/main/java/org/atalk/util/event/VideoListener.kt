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

import java.util.*

/**
 * Defines the notification support informing about changes in the availability
 * of visual `Component`s representing video such as adding and removing.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface VideoListener : EventListener {
    /**
     * Notifies that a visual `Component` representing video has been
     * added to the provider this listener has been added to.
     *
     * @param event a `VideoEvent` describing the added visual
     * `Component` representing video and the provider it was added into
     */
    fun videoAdded(event: VideoEvent)

    /**
     * Notifies that a visual `Component` representing video has been
     * removed from the provider this listener has been added to.
     *
     * @param event a `VideoEvent` describing the removed visual
     * `Component` representing video and the provider it was removed
     * from
     */
    fun videoRemoved(event: VideoEvent)

    /**
     * Notifies about an update to a visual `Component` representing video.
     *
     * @param event a `VideoEvent` describing the visual
     * `Component` related to the update and the details of the specific
     * update
     */
    fun videoUpdate(event: VideoEvent)
}