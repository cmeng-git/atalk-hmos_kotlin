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
 * A custom contact action menu item, used to define an action that can be
 * represented in the contact list entry in the user interface.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface ContactActionMenuItem<T> {
    /**
     * Invoked when an action occurs.
     *
     * @param actionSource the source of the action
     */
    @Throws(OperationFailedException::class)
    fun actionPerformed(actionSource: T)

    /**
     * The icon used by the UI to visualize this action.
     * @return the button icon.
     */
    fun getIcon(): ByteArray?

    /**
     * Returns the text of the component to create for this contact
     * action.
     *
     * @param actionSource the action source for associated with the
     * action.
     * @return the tool tip text of the component to create for this contact
     * action
     */
    fun getText(actionSource: T): String?

    /**
     * Indicates if this action is visible for the given `actionSource`.
     *
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return `true` if the action should be visible for the given
     * `actionSource`, `false` - otherwise
     */
    fun isVisible(actionSource: T): Boolean

    /**
     *
     * @return
     */
    fun getMnemonics(): Char

    /**
     * Returns `true` if the item should be enabled and `false`
     * - not.
     *
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return `true` if the item should be enabled and `false`
     * - not.
     */
    fun isEnabled(actionSource: T): Boolean

    /**
     * Returns `true` if the item should be a check box and
     * `false` if not
     *
     * @return `true` if the item should be a check box and
     * `false` if not
     */
    fun isCheckBox(): Boolean

    /**
     * Returns the state of the item if the item is check box.
     *
     * @param actionSource the action source for which we're verifying the
     * action.
     * @return the state of the item.
     */
    fun isSelected(actionSource: T): Boolean
}