/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui

import android.graphics.Point
import net.java.sip.communicator.service.contactlist.MetaContact
import net.java.sip.communicator.service.gui.Chat
import net.java.sip.communicator.service.gui.Container
import net.java.sip.communicator.service.gui.ExportedWindow
import net.java.sip.communicator.service.gui.PopupDialog
import net.java.sip.communicator.service.gui.UIService
import net.java.sip.communicator.service.gui.WindowID
import net.java.sip.communicator.service.gui.event.ChatListener
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.SecurityAuthority
import net.java.sip.communicator.util.account.LoginManager
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.call.CallManager
import org.atalk.hmos.gui.chat.ChatPanel
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chat.conference.ConferenceChatManager
import java.awt.Dimension

/**
 * Android `UIService` stub. Currently used only for supplying the
 * `SecurityAuthority` to the reconnect plugin.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidUIServiceImpl(defaultSecurityAuthority: SecurityAuthority) : UIService {
    /**
     * Returns the chat conference manager.
     *
     * @return the chat conference manager.
     */
    val conferenceChatManager = ConferenceChatManager()

    /**
     * Default security authority.
     */
    private val defaultSecurityAuthority: SecurityAuthority

    /**
     * private LoginManager loginManager; Creates new instance of `AndroidUIService`.
     *
     * defaultSecurityAuthority default security authority that will be used.
     */
    init {
        this.defaultSecurityAuthority = defaultSecurityAuthority
    }
    /**
     * Returns TRUE if the application is visible and FALSE otherwise. This method is meant to be
     * used by the systray service in order to detect the visibility of the application.
     *
     * @return `true</code> if the application is visible and <code>false` otherwise.
     * @see .setVisible
     */
    /**
     * Shows or hides the main application window depending on the value of parameter
     * `visible`. Meant to be used by the systray when it needs to show or hide the
     * application.
     *
     * visible if `true`, shows the main application window; otherwise, hides the main application window.
     * @see .isVisible
     */
    override val isVisible: Boolean
        get() = aTalkApp.getCurrentActivity() != null

    /**
     * Returns the current location of the main application window. The returned point is the top
     * left corner of the window.
     *
     * @return The top left corner coordinates of the main application window.
     */
    override val location: Point
        get() = Point(0, 0)

    /**
     * Locates the main application window to the new x and y coordinates.
     *
     * @param x The new x coordinate.
     * @param y The new y coordinate.
     */
    override fun setLocation(x: Int, y: Int) {}

    /**
     * Returns the size of the main application window.
     *
     * @return the size of the main application display window.
     */
    override val size: Dimension
        get() = aTalkApp.displaySize

    /**
     * Sets the size of the main application window.
     *
     * @param width The width of the window.
     * @param height The height of the window.
     */
    override fun setSize(width: Int, height: Int) {}

    /**
     * Minimizes the main application window.
     */
    override fun minimize() {}

    /**
     * Maximizes the main application window.
     */
    override fun maximize() {}

    /**
     * Restores the main application window.
     */
    override fun restore() {}

    /**
     * Resize the main application window with the given width and height.
     *
     * @param width The new width.
     * @param height The new height.
     */
    override fun resize(width: Int, height: Int) {}

    /**
     * Moves the main application window to the given coordinates.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    override fun move(x: Int, y: Int) {}

    /**
     * Brings the focus to the main application window.
     */
    override fun bringToFront() {}
    /**
     * Returns TRUE if the application could be exited by closing the main application window,
     * otherwise returns FALSE.
     *
     * @return Returns TRUE if the application could be exited by closing the main application
     * window, otherwise returns FALSE
     */
    /**
     * Sets the exitOnClose property. When TRUE, the user could exit the application by simply
     * closing the main application window (by clicking the X button or pressing Alt-F4). When
     * set to FALSE the main application window will be only hidden.
     *
     * exitOnClose When TRUE, the user could exit the application by simply closing the main application
     * window (by clicking the X button or pressing Alt-F4). When set to FALSE the main
     * application window will be only hidden.
     */
    override var exitOnMainWindowClose: Boolean
        get() = false
        set(exitOnClose) {}

    /**
     * Returns an exported window given by the `WindowID`. This could be for example the
     * "Add contact" window or any other window within the application. The `windowID`
     * should be one of the WINDOW_XXX obtained by the `getSupportedExportedWindows`
     * method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified `windowID` is not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    @Throws(IllegalArgumentException::class)
    override fun getExportedWindow(windowID: WindowID): ExportedWindow? {
        return null
    }

    /**
     * Returns an exported window given by the `WindowID`. This could be for example the
     * "Add contact" window or any other window within the application. The `windowID`
     * should be one of the WINDOW_XXX obtained by the `getSupportedExportedWindows`
     * method.
     *
     * @param windowID One of the WINDOW_XXX WindowID-s.
     * @param params The parameters to be passed to the returned exported window.
     * @return the window to be shown.
     * @throws IllegalArgumentException if the specified `windowID` is not recognized by the implementation (note that
     * implementations MUST properly handle all WINDOW_XXX ID-s.
     */
    @Throws(IllegalArgumentException::class)
    override fun getExportedWindow(windowID: WindowID, params: Array<String>): ExportedWindow? {
        return null
    }

    /**
     * Returns a configurable popup dialog, that could be used to show either a warning message,
     * error message, information message, etc. or to prompt user for simple one field input or
     * to question the user.
     *
     * @return a `PopupDialog`.
     * @see net.java.sip.communicator.service.gui.PopupDialog
     */
    override val popupDialog: PopupDialog?
        get() = null

    /**
     * Returns the `Chat` corresponding to the given `Contact`.
     *
     * @param contact the `Contact` for which the searched chat is about.
     * @return the `Chat` corresponding to the given `Contact`.
     */
    override fun getChat(contact: Contact): Chat? {
        return ChatSessionManager.createChatForContact(contact)
    }

    /**
     * Returns the `Chat` corresponding to the given `ChatRoom`.
     *
     * @param chatRoom the `ChatRoom` for which the searched chat is about.
     * @return the `Chat` corresponding to the given `ChatRoom`.
     */
    override fun getChat(chatRoom: ChatRoom): Chat? {
        return ChatSessionManager.getMultiChat(chatRoom, true)
    }

    /**
     * Returns a list of all open Chats
     *
     * @return A list of all open Chats
     */
    override val chats: List<Chat>
        get() = ChatSessionManager.getActiveChats()

    /**
     * Get the MetaContact corresponding to the chat. The chat must correspond to a one on one
     * conversation. If it is a group chat an exception will be thrown.
     *
     * @param chat The chat to get the MetaContact from
     * @return The MetaContact corresponding to the chat.
     */
    override fun getChatContact(chat: Chat): MetaContact {
        val chatPanel = chat as ChatPanel
        return chatPanel.chatSession!!.descriptor as MetaContact
    }

    /**
     * Returns the selected `Chat`.
     *
     * @return the selected `Chat`.
     */
    override val currentChat: Chat?
        get() = ChatSessionManager.currentChatPanel
    /**
     * Returns the phone number currently entered in the phone number field. This method is meant
     * to be used by plugins that are interested
     * in operations with the currently entered phone number.
     *
     * @return the phone number currently entered in the phone number field.
     */
    /**
     * Sets the phone number in the phone number field. This method is meant to be used by plugins
     * that are interested in operations with
     * the currently entered phone number.
     *
     * phoneNumber the phone number to enter.
     */
    override var currentPhoneNumber: String?
        get() = null
        set(phoneNumber) {}

    /**
     * Returns a default implementation of the `SecurityAuthority` interface that can be
     * used by non-UI components that would like to
     * launch the registration process for a protocol provider. Initially this method was meant
     * for use by the systray bundle and the
     * protocol URI handlers.
     *
     * @param protocolProvider the `ProtocolProviderService` for which the authentication window is about.
     * @return a default implementation of the `SecurityAuthority` interface that can be
     * used by non-UI components that would like to
     * launch the registration process for a protocol provider.
     */
    override fun getDefaultSecurityAuthority(protocolProvider: ProtocolProviderService): SecurityAuthority {
        return defaultSecurityAuthority
    }

    /**
     * Returns an iterator over a set of windowID-s. Each `WindowID` points to a window in
     * the current UI implementation. Each
     * `WindowID` in the set is one of the constants in the `ExportedWindow`
     * interface. The method is meant to be used by
     * bundles that would like to have access to some windows in the gui - for example the "Add
     * contact" window, the "Settings" window, the
     * "Chat window", etc.
     *
     * @return Iterator An iterator to a set containing WindowID-s representing all exported
     * windows supported by the current UI
     * implementation.
     */
    override val supportedExportedWindows: Iterator<WindowID>?
        get() = null

    /**
     * Checks if a window with the given `WindowID` is contained in the current UI
     * implementation.
     *
     * @param windowID one of the `WindowID`-s, defined in the `ExportedWindow` interface.
     * @return `true` if the component with the given `WindowID` is contained in
     * the current UI implementation,
     * `false` otherwise.
     */
    override fun isExportedWindowSupported(windowID: WindowID): Boolean {
        return false
    }

    /**
     * Returns an iterator over a set containing containerID-s pointing to containers supported by
     * the current UI implementation. Each
     * containerID in the set is one of the CONTAINER_XXX constants. The method is meant to be
     * used by plugins or bundles that would like to
     * add components to the user interface. Before adding any component they should use this
     * method to obtain all possible places, which
     * could contain external components, like different menus, toolbars, etc.
     *
     * @return Iterator An iterator to a set containing containerID-s representing all containers
     * supported by the current UI
     * implementation.
     */
    override val supportedContainers: Iterator<Container>?
        get() = null

    /**
     * Checks if the container with the given `Container` is supported from the current UI
     * implementation.
     *
     * @param containderID One of the CONTAINER_XXX Container-s.
     * @return `true` if the container with the given `Container` is supported
     * from the current UI implementation,
     * `false` otherwise.
     */
    override fun isContainerSupported(containderID: Container): Boolean {
        return false
    }

    /**
     * Determines whether the Mac OS X screen menu bar is being used by the UI for its main menu
     * instead of the Windows-like menu bars at the top of the windows.
     *
     *
     * A common use of the returned indicator is for the purposes of platform-sensitive UI since
     * Mac OS X employs a single screen menu bar, Windows and Linux/GTK+ use per-window menu bars
     * and it is inconsistent on Mac OS X to have the Window-like menu bars.
     *
     *
     * @return `true` if the Mac OS X screen menu bar is being used by the UI for its main
     * menu instead of the Windows-like menu bars at the top of the windows; otherwise,
     * `false`
     */
    override fun useMacOSXScreenMenuBar(): Boolean {
        return false
    }

    /**
     * Provides all currently instantiated `Chats`.
     *
     * @return all active `Chats`.
     */
    override val allChats: Collection<Chat>
        get() = ChatSessionManager.getActiveChats()

    /**
     * Registers a `NewChatListener` to be informed when new `Chats` are created.
     *
     * @param listener listener to be registered
     */
    override fun addChatListener(listener: ChatListener) {
        ChatSessionManager.addChatListener(listener)
    }

    /**
     * Removes the registration of a `NewChatListener`.
     *
     * @param listener listener to be unregistered
     */
    override fun removeChatListener(listener: ChatListener) {
        ChatSessionManager.removeChatListener(listener)
    }

    /**
     * Repaints and revalidates the whole UI. This method is meant to be used to runtime apply a
     * skin and refresh automatically the user interface.
     */
    override fun repaintUI() {}

    /**
     * Creates a new `Call` with a specific set of participants.
     *
     * @param participants an array of `String` values specifying the participants to be included into the
     * newly created `Call`
     */
    override fun createCall(participants: Array<String>) {}

    /**
     * Starts a new `Chat` with a specific set of participants.
     *
     * @param participants an array of `String` values specifying the participants to be included into the
     * newly created `Chat`
     */
    override fun startChat(participants: Array<String>) {}

    /**
     * Starts a new `Chat` with a specific set of participants.
     *
     * @param participants an array of `String` values specifying the participants to be included into the
     * newly created `Chat`
     * @param isSmsEnabled whether sms option should be enabled if possible
     */
    override fun startChat(participants: Array<String>, isSmsEnabled: Boolean) {}

    /**
     * Returns a collection of all currently in progress calls
     *
     * @return a collection of all currently in progress calls.
     */
    override val inProgressCalls: Collection<Call<*>?>
        get() = CallManager.getActiveCalls()

    /**
     * Returns the login manager used by the current UI implementation.
     *
     * @return the login manager used by the current UI implementation
     */
    override val loginManager: LoginManager
        get() = AndroidGUIActivator.loginManager

    /**
     * Opens a chat room window for the given `ChatRoomWrapper` instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    override fun openChatRoomWindow(chatRoom: ChatRoomWrapper) {
        val chatPanel = ChatSessionManager.getMultiChat(chatRoom, true)
        // ChatSessionManager.openChat(chatPanel, true);
        ChatSessionManager.setCurrentChatId(chatPanel!!.chatSession!!.chatId)
    }

    /**
     * Closes the chat room window for the given `ChatRoomWrapper` instance.
     *
     * @param chatRoom the chat room associated with the chat room window
     */
    override fun closeChatRoomWindow(chatRoom: ChatRoomWrapper) {
        val chatPanel = ChatSessionManager.getMultiChat(chatRoom, false)
        if (chatPanel != null) {
            ChatSessionManager.removeActiveChat(chatPanel)
        }
    }

    /**
     * Shows Add chat room dialog.
     */
    override fun showAddChatRoomDialog() {
        // ChatRoomTableDialog.showChatRoomTableDialog();
    }

    /**
     * Shows chat room open automatically configuration dialog.
     *
     * @param chatRoomId the chat room id of the chat room associated with the dialog
     * @param pps the protocol provider service of the chat room
     */
    override fun showChatRoomAutoOpenConfigDialog(pps: ProtocolProviderService, chatRoomId: String) {
        // ChatRoomAutoOpenConfigDialog.showChatRoomAutoOpenConfigDialog(pps, chatRoomId);
    }
}