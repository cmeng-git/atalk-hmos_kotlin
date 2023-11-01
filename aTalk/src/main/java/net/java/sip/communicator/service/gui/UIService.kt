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
package net.java.sip.communicator.service.gui

import android.graphics.Point
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.event.ChatListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.SecurityAuthority
import net.java.sip.communicator.util.account.LoginManager
import java.awt.Dimension

/**
 * The `UIService` offers generic access to the graphical user interface
 * for all modules that would like to interact with the user.
 *
 *
 * Through the `UIService` all modules can add their own components in
 * different menus, toolbars, etc. within the ui. Each `UIService`
 * implementation should export its supported "pluggable" containers - a set of
 * `Container`s corresponding to different "places" in the application,
 * where a module can add a component.
 *
 *
 * The `UIService` provides also methods that would allow to other
 * modules to control the visibility, size and position of the main application
 * window. Some of these methods are: setVisible, minimize, maximize, resize,
 * move, etc.
 *
 *
 * A way to show different types of simple windows is provided to allow other
 * modules to show different simple messages, like warning or error messages. In
 * order to show a simple warning message, a module should invoke the
 * getPopupDialog method and then one of the showXXX methods, which corresponds
 * best to the required dialog.
 *
 *
 * Certain components within the GUI, like "AddContact" window for example,
 * could be also shown from outside the UI bundle. To make one of these
 * component exportable, the `UIService` implementation should attach to
 * it an `WindowID`. A window then could be shown, by invoking
 * `getExportedWindow(WindowID)` and then `show`. The
 * `WindowID` above should be obtained from
 * `getSupportedExportedWindows`.
 *
 * @author Yana Stamcheva
 * @author Dmitri Melnikov
 * @author Adam Netocny
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
interface UIService {
    /**
     * Returns TRUE if the application is visible and FALSE otherwise. This
     * method is meant to be used by the systray service in order to detect the
     * visibility of the application.
     *
     * @return `true` if the application is visible and
     * `false` otherwise.
     * @see .setVisible
     */
    /**
     * Shows or hides the main application window depending on the value of
     * parameter `visible`. Meant to be used by the systray when it
     * needs to show or hide the application.
     *
     * @param visible if `true`, shows the main application window;
     * otherwise, hides the main application window.
     * @see .isVisible
     */
    val isVisible: Boolean

    /**
     * Returns the current location of the main application window. The returned
     * point is the top left corner of the window.
     *
     * @return The top left corner coordinates of the main application window.
     */
    val location: Point?

    /**
     * Locates the main application window to the new x and y coordinates.
     *
     * @param x The new x coordinate.
     * @param y The new y coordinate.
     */
    fun setLocation(x: Int, y: Int)

    /**
     * Returns the size of the main application window.
     *
     * @return the size of the main application window.
     */
    val size: Dimension?

    /**
     * Sets the size of the main application window.
     *
     * @param width The width of the window.
     * @param height The height of the window.
     */
    fun setSize(width: Int, height: Int)

    /**
     * Minimizes the main application window.
     */
    fun minimize()

    /**
     * Maximizes the main application window.
     */
    fun maximize()

    /**
     * Restores the main application window.
     */
    fun restore()

    /**
     * Resizes the main application window with the given width and height.
     *
     * @param width The new width.
     * @param height The new height.
     */
    fun resize(width: Int, height: Int)

    /**
     * Moves the main application window to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    fun move(x: Int, y: Int)

    /**
     * Brings the focus to the main application window.
     */
    fun bringToFront()
    /**
     * Returns TRUE if the application could be exited by closing the main
     * application window, otherwise returns FALSE.
     *
     * @return Returns TRUE if the application could be exited by closing the
     * main application window, otherwise returns FALSE
     */
    /**
     * Sets the exitOnClose property. When TRUE, the user could exit the
     * application by simply closing the main application window (by clicking
     * the X button or pressing Alt-F4). When set to FALSE the main application
     * window will be only hidden.
     *
     * @param exitOnClose When TRUE, the user could exit the application by
     * simply closing the main application window (by clicking the X
     * button or pressing Alt-F4). When set to FALSE the main
     * application window will be only hidden.
     */
    var exitOnMainWindowClose: Boolean

    /**
     * Returns an exported window given by the `WindowID`. This could be
     * for example the "Add contact" window or any other window within the
     * application. The `windowID` should be one of the WINDOW_XXX
     * obtained by the `getSupportedExportedWindows` method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified `windowID` is
     * not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    @Throws(IllegalArgumentException::class)
    fun getExportedWindow(windowID: WindowID): ExportedWindow?

    /**
     * Returns an exported window given by the `WindowID`. This could be
     * for example the "Add contact" window or any other window within the
     * application. The `windowID` should be one of the WINDOW_XXX
     * obtained by the `getSupportedExportedWindows` method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @param params The parameters to be passed to the returned exported window.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified `windowID` is
     * not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    @Throws(IllegalArgumentException::class)
    fun getExportedWindow(windowID: WindowID, params: Array<String>): ExportedWindow?

    /**
     * Returns a configurable popup dialog, that could be used to show either a
     * warning message, error message, information message, etc. or to prompt
     * user for simple one field input or to question the user.
     *
     * @return a `PopupDialog`.
     * @see PopupDialog
     */
    val popupDialog: PopupDialog?

    /**
     * Returns the `Chat` corresponding to the given `Contact`.
     *
     * @param contact the `Contact` for which the searched chat is about.
     * @return the `Chat` corresponding to the given `Contact`.
     */
    fun getChat(contact: Contact): Chat?

    /**
     * Returns the `Chat` corresponding to the given `ChatRoom`.
     *
     * @param chatRoom the `ChatRoom` for which the searched chat is about.
     * @return the `Chat` corresponding to the given `ChatRoom`.
     */
    fun getChat(chatRoom: ChatRoom): Chat?

    /**
     * Returns a list of all open Chats
     *
     * @return A list of all open Chats
     */
    val chats: List<Chat>?

    /**
     * Get the MetaContact corresponding to the chat.
     * The chat must correspond to a one on one conversation. If it is a
     * group chat an exception will be thrown.
     *
     * @param chat The chat to get the MetaContact from
     * @return The MetaContact corresponding to the chat.
     */
    fun getChatContact(chat: Chat): MetaContact?

    /**
     * Returns the selected `Chat`.
     *
     * @return the selected `Chat`.
     */
    val currentChat: Chat?
    /**
     * Returns the phone number currently entered in the phone number field.
     * This method is meant to be used by plugins that are interested in
     * operations with the currently entered phone number.
     *
     * @return the phone number currently entered in the phone number field.
     */
    /**
     * Sets the phone number in the phone number field. This method is meant to
     * be used by plugins that are interested in operations with the currently
     * entered phone number.
     *
     * @param phoneNumber the phone number to enter.
     */
    var currentPhoneNumber: String?

    /**
     * Returns a default implementation of the `SecurityAuthority`
     * interface that can be used by non-UI components that would like to launch
     * the registration process for a protocol provider. Initially this method
     * was meant for use by the systray bundle and the protocol URI handlers.
     *
     * @param protocolProvider the `ProtocolProviderService` for which
     * the authentication window is about.
     * @return a default implementation of the `SecurityAuthority`
     * interface that can be used by non-UI components that would like
     * to launch the registration process for a protocol provider.
     */
    fun getDefaultSecurityAuthority(protocolProvider: ProtocolProviderService): SecurityAuthority?

    /**
     * Returns an iterator over a set of windowID-s. Each `WindowID`
     * points to a window in the current UI implementation. Each
     * `WindowID` in the set is one of the constants in the
     * `ExportedWindow` interface. The method is meant to be used by
     * bundles that would like to have access to some windows in the gui - for
     * example the "Add contact" window, the "Settings" window, the "Chat window", etc.
     *
     * @return Iterator An iterator to a set containing WindowID-s representing
     * all exported windows supported by the current UI implementation.
     */
    val supportedExportedWindows: Iterator<WindowID>?

    /**
     * Checks if a window with the given `WindowID` is contained in the
     * current UI implementation.
     *
     * @param windowID one of the `WindowID`-s, defined in the
     * `ExportedWindow` interface.
     * @return `true` if the component with the given
     * `WindowID` is contained in the current UI implementation,
     * `false` otherwise.
     */
    fun isExportedWindowSupported(windowID: WindowID): Boolean

    /**
     * Returns an iterator over a set containing containerID-s pointing to
     * containers supported by the current UI implementation. Each containerID
     * in the set is one of the CONTAINER_XXX constants. The method is meant to
     * be used by plugins or bundles that would like to add components to the
     * user interface. Before adding any component they should use this method
     * to obtain all possible places, which could contain external components,
     * like different menus, toolbars, etc.
     *
     * @return Iterator An iterator to a set containing containerID-s
     * representing all containers supported by the current UI implementation.
     */
    val supportedContainers: Iterator<Container>?

    /**
     * Checks if the container with the given `Container` is supported
     * from the current UI implementation.
     *
     * @param containderID One of the CONTAINER_XXX Container-s.
     * @return `true` if the container with the given
     * `Container` is supported from the current UI
     * implementation, `false` otherwise.
     */
    fun isContainerSupported(containderID: Container): Boolean

    /**
     * Determines whether the Mac OS X screen menu bar is being used by the UI for
     * its main menu instead of the Windows-like menu bars at the top of the windows.
     *
     *
     * A common use of the returned indicator is for the purposes of
     * platform-sensitive UI since Mac OS X employs a single screen menu bar,
     * Windows and Linux/GTK+ use per-window menu bars and it is inconsistent on
     * Mac OS X to have the Window-like menu bars.
     *
     *
     * @return `true` if the Mac OS X screen menu bar is being used by
     * the UI for its main menu instead of the Windows-like menu bars at
     * the top of the windows; otherwise, `false`
     */
    fun useMacOSXScreenMenuBar(): Boolean

    /**
     * Provides all currently instantiated `Chats`.
     *
     * @return all active `Chats`.
     */
    val allChats: Collection<Chat?>?

    /**
     * Registers a `NewChatListener` to be informed when new `Chats` are created.
     *
     * @param listener listener to be registered
     */
    fun addChatListener(listener: ChatListener)

    /**
     * Removes the registration of a `NewChatListener`.
     *
     * @param listener listener to be unregistered
     */
    fun removeChatListener(listener: ChatListener)

    /**
     * Repaints and revalidates the whole UI. This method is meant to be used
     * to runtime apply a skin and refresh automatically the user interface.
     */
    fun repaintUI()

    /**
     * Creates a new `Call` with a specific set of participants.
     *
     * @param participants an array of `String` values specifying the
     * participants to be included into the newly created `Call`
     */
    fun createCall(participants: Array<String>)

    /**
     * Starts a new `Chat` with a specific set of participants.
     *
     * @param participants an array of `String` values specifying the
     * participants to be included into the newly created `Chat`
     */
    fun startChat(participants: Array<String>)

    /**
     * Starts a new `Chat` with a specific set of participants.
     *
     * @param participants an array of `String` values specifying the
     * participants to be included into the newly created `Chat`
     * @param isSmsEnabled whether sms option should be enabled if possible
     */
    fun startChat(participants: Array<String>, isSmsEnabled: Boolean)

    /**
     * Returns a collection of all currently in progress calls.
     *
     * @return a collection of all currently in progress calls.
     */
    val inProgressCalls: Collection<Call<*>?>?

    /**
     * Returns the login manager used by the current UI implementation.
     *
     * @return the login manager used by the current UI implementation
     */
    val loginManager: LoginManager?

    /**
     * Opens a chat room window for the given `ChatRoomWrapper` instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    fun openChatRoomWindow(chatRoom: ChatRoomWrapper)

    /**
     * Closes the chat room window for the given `ChatRoomWrapper`
     * instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    fun closeChatRoomWindow(chatRoom: ChatRoomWrapper)

    /**
     * Shows Add chat room dialog.
     */
    fun showAddChatRoomDialog()

    /**
     * Shows chat room open automatically configuration dialog.
     *
     * @param chatRoomId the chat room id of the chat room associated with the
     * dialog
     * @param pps the protocol provider service of the chat room
     */
    fun showChatRoomAutoOpenConfigDialog(pps: ProtocolProviderService, chatRoomId: String)
}