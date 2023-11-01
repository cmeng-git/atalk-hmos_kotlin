/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.customcontactactions

import net.java.sip.communicator.service.protocol.OperationFailedException

/**
 * A custom contact action, used to define an action that can be represented in
 * the contact list entry in the user interface.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 */
interface ContactAction<T> {
    /**
     * Invoked when an action occurs.
     *
     * @param actionSource the source of the action
     * @param x the x coordinate of the action
     * @param y the y coordinate of the action
     */
    @Throws(OperationFailedException::class)
    fun actionPerformed(actionSource: T, x: Int, y: Int)

    /**
     * The icon used by the UI to visualize this action.
     * @return the button icon.
     */
    fun getIcon(): ByteArray?

    /**
     * The icon used by the UI to visualize the roll over state of the button.
     * @return the button icon.
     */
    fun getRolloverIcon(): ByteArray?

    /**
     * The icon used by the UI to visualize the pressed state of the button
     * @return the button icon.
     */
    fun getPressedIcon(): ByteArray?

    /**
     * Returns the tool tip text of the component to create for this contact
     * action.
     *
     * @return the tool tip text of the component to create for this contact
     * action
     */
    fun getToolTipText(): String?

    /**
     * Indicates if this action is visible for the given `actionSource`.
     *
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return `true` if the action should be visible for the given
     * `actionSource`, `false` - otherwise
     */
    fun isVisible(actionSource: T): Boolean
}