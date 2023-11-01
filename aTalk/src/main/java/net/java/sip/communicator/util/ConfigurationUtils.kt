/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.impl.protocol.jabber.OperationSetContactCapabilitiesJabberImpl
import net.java.sip.communicator.service.protocol.ChatRoom
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.util.account.AccountUtils.registeredProviders
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatFragment
import org.atalk.hmos.gui.chat.ChatSession
import org.atalk.hmos.gui.settings.QuietTimeFragment
import org.atalk.hmos.gui.settings.SettingsFragment
import org.atalk.hmos.gui.settings.TimePreference
import org.atalk.hmos.gui.util.ThemeHelper
import org.atalk.hmos.gui.util.ThemeHelper.appTheme
import org.atalk.hmos.gui.webview.WebViewFragment
import org.atalk.persistance.DatabaseBackend
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.util.MediaType
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smackx.chatstates.ChatStateManager
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager
import org.jivesoftware.smackx.receipts.DeliveryReceipt
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager
import org.json.JSONException
import org.json.JSONObject
import org.jxmpp.jid.Jid
import timber.log.Timber
import java.awt.Color
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.IOException
import java.util.*
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Cares about all common configurations. Storing and retrieving configuration values.
 *
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
object ConfigurationUtils {
    /**
     * The send message command defined by the Enter key.
     */
    const val ENTER_COMMAND = "Enter"

    /**
     * The send message command defined by the Ctrl-Enter key.
     */
    const val CTRL_ENTER_COMMAND = "Ctrl-Enter"

    /**
     * Indicates whether the message automatic pop-up is enabled.
     */
    private var autoPopupNewMessage = false

    /**
     * The send message command. ENTER or Ctrl-ENTER
     */
    private var sendMessageCommand: String? = null

    /**
     * The Web View access page
     */
    private var mWebPage: String? = null
    /**
     * Return TRUE if "showCallPanel" property is true, otherwise - return FALSE. Indicates to the
     * user interface whether the panel containing the call and hangup buttons should be shown.
     *
     * @return TRUE if "showCallPanel" property is true, otherwise - return FALSE.
     */
    /**
     * Indicates if the call panel is shown.
     */
    var isCallPanelShown = true
        private set

    /**
     * Indicates if the offline contacts are shown.
     */
    private var isShowOffline = true

    /**
     * Indicates if the application main window is visible by default.
     */
    private var isApplicationVisible = true

    /**
     * Indicates if the quit warning should be shown.
     */
    private var isQuitWarningShown = true

    /**
     * Indicates if aTalk will auto start on device reboot.
     */
    var isAutoStartEnable = true
        private set

    /**
     * Indicates if TTS is enable.
     */
    private var isTtsEnable = false

    /**
     * Indicates if message delivery receipt should be sent.
     */
    private var isSendMessageDeliveryReceipt = true

    /**
     * Indicates if chat state notifications should be sent.
     */
    private var isSendChatStateNotifications = true

    /**
     * Indicates if is send thumbnail option is offer during image file transfer.
     */
    private var isSendThumbnail = true

    /**
     * Indicates if presence subscription mode is auto approval.
     */
    private var isPresenceSubscribeAuto = true

    /**
     * Indicates if confirmation should be requested before really moving a contact.
     */
    private var isMoveContactConfirmationRequested = true

    /**
     * Indicates if tabs in chat window are enabled.
     */
    private var isMultiChatWindowEnabled = false
    /**
     * Returns `true` if the "isLeaveChatRoomOnWindowCloseEnabled" property is true,
     * otherwise - returns `false`. Indicates to the user interface whether when
     * closing the chat window we would leave the chat room.
     *
     * @return `true` if the "isLeaveChatRoomOnWindowCloseEnabled" property is true,
     * otherwise - returns `false`.
     */
    /**
     * Indicates whether we will leave chat room on window closing.
     */
    var isLeaveChatRoomOnWindowCloseEnabled = false
        private set
    /**
     * Returns `true` if the "isPrivateMessagingInChatRoomDisabled" property is true,
     * otherwise - returns `false`. Indicates to the user interface whether the
     * private messaging is disabled in chat rooms.
     *
     * @return `true` if the "isPrivateMessagingInChatRoomDisabled" property is true,
     * otherwise - returns `false`.
     */
    /**
     * Indicates if private messaging is enabled for chat rooms.
     */
    var isPrivateMessagingInChatRoomDisabled = false
        private set

    /**
     * Indicates if the history should be shown in the chat window.
     */
    private var isHistoryShown = false

    /**
     * Returns `true` if the "isRecentMessagesShown" property is true, otherwise -
     * returns `false`. Indicates to the user whether the recent messages are shown.
     *
     * @return `true` if the "isRecentMessagesShown" property is true, otherwise `false`
     * .
     */
    // configService.setProperty(MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED,

    /**
     * Indicates if the recent messages should be shown.
     */
    var isRecentMessagesShown = true

    /**
     * Initial default wait time for incoming message alert to end before start TTS
     */
    private var ttsDelay = 1200

    /**
     * The size of the chat history to show in chat window.
     */
    private var chatHistorySize = 0

    /**
     * The auto accept file size.
     */
    private var acceptFileSize = 0

    /**
     * The size of the chat write area.
     */
    private var chatWriteAreaSize = 0

    /**
     * The transparency of the window.
     */
    private var windowTransparency = 0

    /**
     * Indicates if transparency is enabled.
     */
    private var isTransparentWindowEnabled = false
    /**
     * Returns `true` if the "isWindowDecorated" property is true, otherwise - returns `false`..
     *
     * @return `true` if the "isWindowDecorated" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the window is decorated.
     */
    private var isWindowDecorated = false

    /**
     * Indicates if the chat tool bar is visible.
     */
    private var isChatToolbarVisible = false

    /**
     * Indicates if the chat style bar is visible.
     */
    private var isChatStyleBarVisible = false

    /**
     * Indicates if the chat simple theme is activated.
     */
    private var isChatSimpleThemeEnabled = false
    /**
     * Returns `true` if the "ADD_CONTACT_DISABLED" property is true, otherwise - returns `false`..
     *
     * @return `true` if the "ADD_CONTACT_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the add contact functionality is disabled.
     */
    private var isAddContactDisabled = false
    /**
     * Returns `true` if the "MERGE_CONTACT_DISABLED" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "MERGE_CONTACT_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the merge contact functionality is disabled.
     */
    private var isMergeContactDisabled = false
    /**
     * Returns `true` if the "GO_TO_CHATROOM_DISABLED" property is true, otherwise - returns `false`..
     *
     * @return `true` if the "GO_TO_CHATROOM_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the go to chatRoom functionality is disabled.
     */
    private var isGoToChatroomDisabled = false
    /**
     * Returns `true` if the "CREATE_GROUP_DISABLED" property is true, otherwise - returns `false` ..
     *
     * @return `true` if the "CREATE_GROUP_DISABLED" property is true, otherwise - returns `false`
     * .
     */
    /**
     * Indicates if the create group functionality is disabled.
     */
    private var isCreateGroupDisabled = false
    /**
     * Returns `true` if the "FLATTEN_GROUP_ENABLED" property is true, otherwise - returns `false` ..
     *
     * @return `true` if the "FLATTEN_GROUP_ENABLED" property is true, otherwise - returns `false`
     * .
     */
    /**
     * Indicates if the create group functionality is enabled.
     */
    private var isFlattenGroupEnabled = false
    /**
     * Returns `true` if the "REMOVE_CONTACT_DISABLED" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "REMOVE_CONTACT_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the remove contact functionality is disabled.
     */
    private var isRemoveContactDisabled = false
    /**
     * Returns `true` if the "CONTACT_MOVE_DISABLED" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "CONTACT_MOVE_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the move contact functionality is disabled.
     */
    private var isContactMoveDisabled = false
    /**
     * Returns `true` if the "CONTACT_RENAME_DISABLED" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "CONTACT_RENAME_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the rename contact functionality is disabled.
     */
    private var isContactRenameDisabled = false
    /**
     * Returns `true` if the "GROUP_REMOVE_DISABLED" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "GROUP_REMOVE_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the remove group functionality is disabled.
     */
    private var isGroupRemoveDisabled = false
    /**
     * returns `true` if the "GROUP_RENAME_DISABLED" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "GROUP_RENAME_DISABLED" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the rename group functionality is disabled.
     */
    private var isGroupRenameDisabled = false
    /**
     * returns `true` if the "PRESET_STATUS_MESSAGES" property is true, otherwise - returns `false`.
     *
     * @return `true` if the "PRESET_STATUS_MESSAGES" property is true, otherwise - returns `false`.
     */
    /**
     * Indicates if the pre set status messages are enabled.
     */
    private var isPresetStatusMessagesEnabled = false
    private var isQuiteHoursEnable = true
    private var quiteHoursStart = 0L
    private var quiteHoursEnd = 0L

    /**
     * Return TRUE if "sendChatStateNotifications" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether chat state notifications are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    var isHeadsUpEnable = true
        private set

    /**
     * The last directory used in file transfer.
     */
    private var sendFileLastDir: String? = null

    /**
     * The configuration service.
     */
    private var mConfigService: ConfigurationService? = null

    /**
     * The parent of the last contact.
     */
    private var lastContactParent: String? = null

    /**
     * The last conference call provider.
     */
    private var lastCallConferenceProvider: ProtocolProviderService? = null

    /**
     * Indicates if the "Advanced" configurations for an account should be disabled for the user.
     */
    private var isAdvancedAccountConfigDisabled = false

    /**
     * The default font family used in chat windows.
     */
    private var defaultFontFamily: String? = null

    /**
     * The default font size used in chat windows.
     */
    private var defaultFontSize: String? = null
    /**
     * Returns the default chat font bold.
     *
     * @return the default chat font bold
     */
    /**
     * Indicates if the font is bold in chat windows.
     */
    private var isChatFontBold = false
    /**
     * Returns the default chat font italic.
     *
     * @return the default chat font italic
     */
    /**
     * Indicates if the font is italic in chat windows.
     */
    private var isChatFontItalic = false
    /**
     * Returns the default chat font underline.
     *
     * @return the default chat font underline
     */
    /**
     * Indicates if the font is underline in chat windows.
     */
    private var isChatFontUnderline = false

    /**
     * The default font color used in chat windows.
     */
    private var defaultFontColor = -1
    /**
     * Returns `true` if status changed should be shown in chat history area, `false` otherwise.
     *
     * @return `true` if status changed should be shown in chat history area, `false` otherwise.
     */
    /**
     * whether to show the status changed message in chat history area.
     */
    var isShowStatusChangedInChat = false
        private set
    /**
     * Whether allow to use additional phone numbers to route video calls and desktop sharing through it.
     *
     * @return whether allow to use additional phone numbers to route video calls and desktop sharing through it.
     */
    /**
     * When enabled, allow to use the additional phone numbers to route video calls and desktop
     * sharing through it if possible.
     */
    var isRouteVideoAndDesktopUsingPhoneNumberEnabled = false
        private set
    /**
     * Whether allow user to select account when only a single account is available.
     *
     * @return whether allow user to select account when only a single account is available.
     */
    /**
     * Indicates that when we have a single account we can hide the select account option when possible.
     */
    private var isHideAccountSelectionWhenPossibleEnabled = false
    /**
     * Whether to hide account statuses from global menu.
     *
     * @return whether to hide account statuses.
     */
    /**
     * Hide accounts from accounts status list.
     */
    private var isHideAccountStatusSelectorsEnabled = false
    /**
     * Whether to hide extended away status from global menu.
     *
     * @return whether to hide extended away status.
     */
    /**
     * Hide extended away status.
     */
    var isHideExtendedAwayStatus = false
        private set
    /**
     * Whether creation of separate submenu for auto answer is disabled.
     *
     * @return whether creation of separate submenu for auto answer is disabled.
     */
    /**
     * Whether to disable creation of auto answer submenu.
     */
    private var isAutoAnswerDisableSubmenu = false
    /**
     * Indicates if the chat room user configuration functionality is disabled.
     *
     * @return `true` if the chat room configuration is disabled, `false` - otherwise
     */
    /**
     * Whether the chat room user configuration functionality is disabled.
     */
    private var isChatRoomConfigDisabled = false

    /**
     * Indicates if the single window interface is enabled.
     */
    private var isSingleWindowInterfaceEnabled = false
    /**
     * Whether addresses will be shown in call history tooltips.
     *
     * @return whether addresses will be shown in call history tooltips.
     */
    /**
     * Whether addresses will be shown in call history tooltips.
     */
    var isHideAddressInCallHistoryTooltipEnabled = false

    /**
     * The name of the property, whether to show addresses in call history tooltip.
     */
    private const val HIDE_ADDR_IN_CALL_HISTORY_TOOLTIP_PROPERTY = "gui.contactlist.HIDE_ADDRESS_IN_CALL_HISTORY_TOOLTIP_ENABLED"
    /**
     * Whether to display or not the text notifying that a message is a incoming or outgoing sms message.
     *
     * @return whether to display the text notifying that a message is sms.
     */
    /**
     * Texts to notify that sms has been sent or sms has been received.
     */
    private var isSmsNotifyTextDisabled = false

    /**
     * To disable displaying sms delivered message or sms received.
     */
    private const val SMS_MSG_NOTIFY_TEXT_DISABLED_PROP = "gui.contactlist.SMS_MSG_NOTIFY_TEXT_DISABLED_PROP"
    /**
     * Whether domain will be shown in receive call dialog.
     *
     * @return whether domain will be shown in receive call dialog.
     */
    /**
     * Whether domain will be shown in receive call dialog.
     */
    private var isHideDomainInReceivedCallDialogEnabled = false

    /**
     * The name of the property, whether to show addresses in call history tooltip.
     */
    private const val HIDE_DOMAIN_IN_RECEIVE_CALL_DIALOG_PROPERTY = "gui.call.HIDE_DOMAIN_IN_RECEIVE_CALL_DIALOG_ENABLED"

    /**
     * The name of the simple theme property.
     */
    private const val CHAT_SIMPLE_THEME_ENABLED_PROP = "gui.CHAT_SIMPLE_THEME_ENABLED"

    /**
     * The name of the chat room configuration property.
     */
    private const val CHAT_ROOM_CONFIG_DISABLED_PROP = "gui.CHAT_ROOM_CONFIG_DISABLED"

    /**
     * The name of the single interface property.
     */
    private const val SINGLE_WINDOW_INTERFACE_ENABLED = "gui.SINGLE_WINDOW_INTERFACE_ENABLED"

    /**
     * The names of the configuration properties.
     */
    private const val pAcceptFileSize = "gui.AUTO_ACCEPT_FILE_SIZE"
    private const val pAutoAnswerDisableSubmenu = "gui.AUTO_ANSWER_DISABLE_SUBMENU"
    private const val pAutoPopupNewMessage = "gui.AUTO_POPUP_NEW_MESSAGE"
    const val pAutoStart = "gui.AUTO_START_ON_REBOOT"
    private const val pChatHistorySize = "gui.MESSAGE_HISTORY_SIZE"
    private const val pChatWriteAreaSize = "gui.CHAT_WRITE_AREA_SIZE"
    private const val pHideAccountMenu = "gui.HIDE_SELECTION_ON_SINGLE_ACCOUNT"
    private const val pHideAccountStatusSelectors = "gui.HIDE_ACCOUNT_STATUS_SELECTORS"
    private const val pHideExtendedAwayStatus = "protocol.globalstatus.HIDE_EXTENDED_AWAY_STATUS"
    private const val pIsWindowDecorated = "gui.IS_WINDOW_DECORATED"
    private const val pLeaveChatRoomOnWindowClose = "gui.LEAVE_CHATROOM_ON_WINDOW_CLOSE"
    private const val pMsgCommand = "gui.SEND_MESSAGE_COMMAND"
    private const val pMessageDeliveryReceipt = "gui.SEND_MESSAGE_DELIVERY_RECEIPT"
    private const val pMessageHistoryShown = "gui.IS_MESSAGE_HISTORY_SHOWN"
    private const val pMultiChatWindowEnabled = "gui.IS_MULTI_CHAT_WINDOW_ENABLED"
    private const val pPresenceSubscribeAuto = "gui.PRESENCE_SUBSCRIBE_MODE_AUTO"
    private const val pQuiteHoursEnable = QuietTimeFragment.P_KEY_QUIET_HOURS_ENABLE
    private const val pQuiteHoursStart = QuietTimeFragment.P_KEY_QUIET_HOURS_START
    private const val pQuiteHoursEnd = QuietTimeFragment.P_KEY_QUIET_HOURS_END
    private const val pRouteVideoAndDesktopUsingPhoneNumber = "gui.ROUTE_VIDEO_AND_DESKTOP_TO_PNONENUMBER"
    private const val pSendThumbnail = "gui.sendThumbnail"
    private const val pShowStatusChangedInChat = "gui.SHOW_STATUS_CHANGED_IN_CHAT"
    private const val pTransparentWindowEnabled = "gui.IS_TRANSPARENT_WINDOW_ENABLED"
    private const val pTTSEnable = "gui.TTS_ENABLE"
    private const val pTTSDelay = "gui.TTS_DELAY"
    private const val pTypingNotification = "gui.SEND_TYPING_NOTIFICATIONS_ENABLED"
    private const val pWebPage = "gui.WEB_PAGE_ACCESS"
    private const val pWindowTransparency = "gui.WINDOW_TRANSPARENCY"
    private const val pHeadsUpEnable = SettingsFragment.P_KEY_HEADS_UP_ENABLE

    /**
     * Indicates if phone numbers should be normalized before dialed.
     */
    private var isNormalizePhoneNumber = false

    /**
     * Indicates if a string containing alphabetical characters might be considered as a phone number.
     */
    private var acceptPhoneNumberWithAlphaChars = false

    /**
     * The name of the single interface property.
     */
    private const val ALERTER_ENABLED_PROP = "chatalerter.ENABLED"

    /**
     * The name of the property which indicates whether the user should be
     * warned that master password is not set.
     */
    private const val MASTER_PASS_WARNING_PROP = "gui.main.SHOW_MASTER_PASSWORD_WARNING"

    /**
     * Indicates whether the user should be warned that master password is not set.
     */
    private var showMasterPasswordWarning = false

    /**
     * Indicates if window (task bar or dock icon) alerter is enabled.
     */
    private var alerterEnabled = false
    private lateinit var mDB: SQLiteDatabase
    private val contentValues = ContentValues()

    /**
     * Loads all user interface configurations.
     */
    fun loadGuiConfigurations() {
        // Do it here one more time, sometime see accessing preference crash with configService == null
        mConfigService = UtilActivator.configurationService
        if (mConfigService == null) {
            Timber.e("UtilActivator.getConfigurationService() returns null")
            return
        }
        mConfigService!!.addPropertyChangeListener(ConfigurationChangeListener())
        mDB = DatabaseBackend.writableDB

        // Init the aTalk app Theme before any activity use
        initAppTheme()

        // Load the UI language last selected by user or default to system language
        // Already defined in aTalkApp#attachBaseContext()
        // String language = mConfigService.getString(P_KEY_LOCALE, "");
        // LocaleHelper.setLanguage(language);

        // Load the "webPage" property.
        mWebPage = mConfigService!!.getString(pWebPage)
        if (StringUtils.isEmpty(mWebPage)) mWebPage = aTalkApp.getResString(R.string.service_gui_settings_WEBVIEW_SUMMARY)

        // Load the "auPopupNewMessage" property.
        var autoPopup = mConfigService!!.getString(pAutoPopupNewMessage)
        if (StringUtils.isEmpty(autoPopup)) autoPopup = UtilActivator.resources.getSettingsString(pAutoPopupNewMessage)
        if (StringUtils.isNotEmpty(autoPopup) && autoPopup.equals("yes", ignoreCase = true)) autoPopupNewMessage = true

        // Load the "sendMessageCommand" property.
        var messageCommand = mConfigService!!.getString(pMsgCommand)
        if (StringUtils.isEmpty(messageCommand)) messageCommand = UtilActivator.resources.getSettingsString(pMsgCommand)
        if (StringUtils.isNotEmpty(messageCommand)) sendMessageCommand = messageCommand

        // Load the showCallPanel property.
        isCallPanelShown = mConfigService!!.getBoolean("gui.showCallPanel", isCallPanelShown)

        // Load the "isAutoStartOnBoot" property.
        isAutoStartEnable = mConfigService!!.getBoolean(pAutoStart, isAutoStartEnable)

        // Load the "isTtsEnable" and delay property.
        isTtsEnable = mConfigService!!.getBoolean(pTTSEnable, isTtsEnable())
        ttsDelay = mConfigService!!.getInt(pTTSDelay, ttsDelay)

        // Load the "showOffline" property.
        isShowOffline = mConfigService!!.getBoolean("gui.showOffline", isShowOffline)

        // Load the "showApplication" property.
        isApplicationVisible = mConfigService!!.getBoolean("systray.showApplication", isApplicationVisible)

        // Load the "showAppQuitWarning" property.
        isQuitWarningShown = mConfigService!!.getBoolean("gui.quitWarningShown", isQuitWarningShown)

        // Load the "isSendMessageDeliveryReceipt" property.
        isSendMessageDeliveryReceipt = mConfigService!!.getBoolean(pMessageDeliveryReceipt, isSendMessageDeliveryReceipt)

        // Load the "sendTypingNotifications" property.
        var isSendTypingNotification = mConfigService!!.getString(pTypingNotification)
        if (StringUtils.isEmpty(isSendTypingNotification)) isSendTypingNotification = UtilActivator.resources.getSettingsString(pTypingNotification)
        if (StringUtils.isNotEmpty(isSendTypingNotification)) isSendChatStateNotifications = isSendTypingNotification.toBoolean()

        // Load the "sendThumbnail" property.
        val sendThumbNail = mConfigService!!.getString(pSendThumbnail)
        if (StringUtils.isNotEmpty(sendThumbNail)) {
            isSendThumbnail = sendThumbNail.toBoolean()
        }

        // Load the "isPresenceSubscribeMode" property.
        isPresenceSubscribeAuto = mConfigService!!.getBoolean(pPresenceSubscribeAuto, isPresenceSubscribeAuto)

        // Load the "isMoveContactConfirmationRequested" property.
        val isMoveContactConfirmationRequestedString = mConfigService!!.getString("gui.isMoveContactConfirmationRequested")
        if (StringUtils.isNotEmpty(isMoveContactConfirmationRequestedString)) {
            isMoveContactConfirmationRequested = isMoveContactConfirmationRequestedString.toBoolean()
        }

        // Load the "isMultiChatWindowEnabled" property.
        var isMultiChatWindowEnabledString = mConfigService!!.getString(pMultiChatWindowEnabled)
        if (StringUtils.isEmpty(isMultiChatWindowEnabledString)) isMultiChatWindowEnabledString = UtilActivator.resources.getSettingsString(pMultiChatWindowEnabled)
        if (StringUtils.isNotEmpty(isMultiChatWindowEnabledString)) {
            isMultiChatWindowEnabled = isMultiChatWindowEnabledString.toBoolean()
        }
        isPrivateMessagingInChatRoomDisabled = mConfigService!!.getBoolean("gui.IS_PRIVATE_CHAT_IN_CHATROOM_DISABLED", false)

        // Load the "isLeaveChatroomOnWindowCloseEnabled" property.
        var isLeaveChatRoomOnWindowCloseEnabledString = mConfigService!!.getString(pLeaveChatRoomOnWindowClose)
        if (StringUtils.isEmpty(isLeaveChatRoomOnWindowCloseEnabledString)) {
            isLeaveChatRoomOnWindowCloseEnabledString = UtilActivator.resources.getSettingsString(pLeaveChatRoomOnWindowClose)
        }
        if (StringUtils.isNotEmpty(isLeaveChatRoomOnWindowCloseEnabledString)) {
            isLeaveChatRoomOnWindowCloseEnabled = isLeaveChatRoomOnWindowCloseEnabledString.toBoolean()
        }

        // Load the "isHistoryShown" property.
        var isHistoryShownString = mConfigService!!.getString(pMessageHistoryShown)
        if (StringUtils.isEmpty(isHistoryShownString)) isHistoryShownString = UtilActivator.resources.getSettingsString(pMessageHistoryShown)
        if (StringUtils.isNotEmpty(isHistoryShownString)) {
            isHistoryShown = isHistoryShownString.toBoolean()
        }

        // Load the "isRecentMessagesShown" property.
        // isRecentMessagesShown = !configService.getBoolean(MessageHistoryService
        // .PNAME_IS_RECENT_MESSAGES_DISABLED, !isRecentMessagesShown);

        // Load the "acceptFileSize" property.
        val fileSize = mConfigService!!.getString(pAcceptFileSize, aTalkApp.getResString(R.string.auto_accept_filesize))
        acceptFileSize = fileSize!!.toInt()

        // Load the "chatHistorySize" property.
        val chatHistorySizeString = mConfigService!!.getString(pChatHistorySize, "30")
        chatHistorySize = chatHistorySizeString!!.toInt()

        // Load the "CHAT_WRITE_AREA_SIZE" property.
        var chatWriteAreaSizeString = mConfigService!!.getString(pChatWriteAreaSize)
        if (StringUtils.isEmpty(chatWriteAreaSizeString)) chatWriteAreaSizeString = UtilActivator.resources.getSettingsString(pChatWriteAreaSize)
        if (StringUtils.isNotEmpty(chatWriteAreaSizeString)) {
            chatWriteAreaSize = chatWriteAreaSizeString!!.toInt()
        }

        // Load the "isTransparentWindowEnabled" property.
        var isTransparentWindowEnabledString = mConfigService!!.getString(pTransparentWindowEnabled)
        if (StringUtils.isEmpty(isTransparentWindowEnabledString)) isTransparentWindowEnabledString = UtilActivator.resources.getSettingsString(pTransparentWindowEnabled)
        if (StringUtils.isNotEmpty(isTransparentWindowEnabledString)) {
            isTransparentWindowEnabled = isTransparentWindowEnabledString.toBoolean()
        }

        // Load the "windowTransparency" property.
        var windowTransparencyString = mConfigService!!.getString(pWindowTransparency)
        if (StringUtils.isEmpty(windowTransparencyString)) windowTransparencyString = UtilActivator.resources.getSettingsString(pWindowTransparency)
        if (StringUtils.isNotEmpty(windowTransparencyString)) {
            windowTransparency = windowTransparencyString!!.toInt()
        }

        // Load the "isWindowDecorated" property.
        var isWindowDecoratedString = mConfigService!!.getString(pIsWindowDecorated)
        if (StringUtils.isEmpty(isWindowDecoratedString)) isWindowDecoratedString = UtilActivator.resources.getSettingsString(pIsWindowDecorated)
        if (StringUtils.isNotEmpty(isWindowDecoratedString)) {
            isWindowDecorated = isWindowDecoratedString.toBoolean()
        }

        // Load the "isChatToolbarVisible" property
        isChatToolbarVisible = mConfigService!!.getBoolean("gui.chat.ChatWindow.showToolbar", true)
        // Load the "isChatToolbarVisible" property
        isChatStyleBarVisible = mConfigService!!.getBoolean("gui.chat.ChatWindow.showStylebar", true)

        // Load the "isChatSimpleThemeEnabled" property.
        isChatSimpleThemeEnabled = mConfigService!!.getBoolean(CHAT_SIMPLE_THEME_ENABLED_PROP, true)

        // Load the "lastContactParent" property.
        lastContactParent = mConfigService!!.getString("gui.addcontact.lastContactParent")

        // Load the "sendFileLastDir" property.
        sendFileLastDir = mConfigService!!.getString("gui.chat.filetransfer.SEND_FILE_LAST_DIR")

        // Load the "ADD_CONTACT_DISABLED" property.
        isAddContactDisabled = mConfigService!!.getBoolean("gui.contactlist.CONTACT_ADD_DISABLED", false)

        // Load the "MERGE_CONTACT_DISABLED" property.
        isMergeContactDisabled = mConfigService!!.getBoolean("gui.contactlist.CONTACT_MERGE_DISABLED", false)

        // Load the "CREATE_GROUP_DISABLED" property.
        isCreateGroupDisabled = mConfigService!!.getBoolean("gui.contactlist.CREATE_GROUP_DISABLED", false)

        // Load the "FLATTEN_GROUP_ENABLED" property.
        isFlattenGroupEnabled = mConfigService!!.getBoolean("gui.contactlist.FLATTEN_GROUP_ENABLED", false)

        // Load the "GO_TO_CHATROOM_DISABLED" property.
        isGoToChatroomDisabled = mConfigService!!.getBoolean("gui.chatroomslist.GO_TO_CHATROOM_DISABLED", false)

        // Load the "REMOVE_CONTACT_DISABLED" property.
        isRemoveContactDisabled = mConfigService!!.getBoolean("gui.contactlist.CONTACT_REMOVE_DISABLED", false)

        // Load the "CONTACT_MOVE_DISABLED" property.
        isContactMoveDisabled = mConfigService!!.getBoolean("gui.contactlist.CONTACT_MOVE_DISABLED", false)

        // Load the "CONTACT_RENAME_DISABLED" property.
        isContactRenameDisabled = mConfigService!!.getBoolean("gui.contactlist.CONTACT_RENAME_DISABLED", false)

        // Load the "GROUP_REMOVE_DISABLED" property.
        isGroupRemoveDisabled = mConfigService!!.getBoolean("gui.contactlist.GROUP_REMOVE_DISABLED", false)

        // Load the "GROUP_RENAME_DISABLED" property.
        isGroupRenameDisabled = mConfigService!!.getBoolean("gui.contactlist.GROUP_RENAME_DISABLED", false)

        // Load the "PRESET_STATUS_MESSAGES" property.
        isPresetStatusMessagesEnabled = mConfigService!!.getBoolean("gui.presence.PRESET_STATUS_MESSAGES", true)

        // Load the gui.main.account.ADVANCED_CONFIG_DISABLED" property.
        val advancedConfigDisabledDefaultProp = UtilActivator.resources.getSettingsString("gui.account.ADVANCED_CONFIG_DISABLED")
        var isAdvancedConfigDisabled = false
        if (StringUtils.isNotEmpty(advancedConfigDisabledDefaultProp)) isAdvancedConfigDisabled = advancedConfigDisabledDefaultProp.toBoolean()

        // Load the advanced account configuration disabled.
        isAdvancedAccountConfigDisabled = mConfigService!!.getBoolean("gui.account.ADVANCED_CONFIG_DISABLED", isAdvancedConfigDisabled)

        // Single interface enabled property.
        val singleInterfaceEnabledProp = UtilActivator.resources.getSettingsString(SINGLE_WINDOW_INTERFACE_ENABLED)
        val isEnabled = if (StringUtils.isNotEmpty(singleInterfaceEnabledProp)) singleInterfaceEnabledProp.toBoolean() else UtilActivator.resources.getSettingsString("gui.SINGLE_WINDOW_INTERFACE").toBoolean()

        // Load the advanced account configuration disabled.
        isSingleWindowInterfaceEnabled = mConfigService!!.getBoolean(SINGLE_WINDOW_INTERFACE_ENABLED, isEnabled)
        if (isFontSupportEnabled) {
            // Load default font family string.
            defaultFontFamily = mConfigService!!.getString("gui.chat.DEFAULT_FONT_FAMILY")

            // Load default font size.
            defaultFontSize = mConfigService!!.getString("gui.chat.DEFAULT_FONT_SIZE")

            // Load isBold chat property.
            isChatFontBold = mConfigService!!.getBoolean("gui.chat.DEFAULT_FONT_BOLD", isChatFontBold)

            // Load isItalic chat property.
            isChatFontItalic = mConfigService!!.getBoolean("gui.chat.DEFAULT_FONT_ITALIC", isChatFontItalic)

            // Load isUnderline chat property.
            isChatFontUnderline = mConfigService!!.getBoolean("gui.chat.DEFAULT_FONT_UNDERLINE", isChatFontUnderline)

            // Load default font color property.
            val colorSetting = mConfigService!!.getInt("gui.chat.DEFAULT_FONT_COLOR", -1)
            if (colorSetting != -1) defaultFontColor = colorSetting
        }
        val showStatusChangedInChatDefault = UtilActivator.resources.getSettingsString(pShowStatusChangedInChat)

        // if there is a default value use it
        if (StringUtils.isNotEmpty(showStatusChangedInChatDefault)) isShowStatusChangedInChat = showStatusChangedInChatDefault.toBoolean()
        isShowStatusChangedInChat = mConfigService!!.getBoolean(pShowStatusChangedInChat, isShowStatusChangedInChat)
        val routeVideoAndDesktopUsingPhoneNumberDefault = UtilActivator.resources.getSettingsString(pRouteVideoAndDesktopUsingPhoneNumber)
        if (StringUtils.isNotEmpty(routeVideoAndDesktopUsingPhoneNumberDefault)) isRouteVideoAndDesktopUsingPhoneNumberEnabled = routeVideoAndDesktopUsingPhoneNumberDefault.toBoolean()
        isRouteVideoAndDesktopUsingPhoneNumberEnabled = mConfigService!!.getBoolean(pRouteVideoAndDesktopUsingPhoneNumber, isRouteVideoAndDesktopUsingPhoneNumberEnabled)
        val hideAccountMenuDefaultValue = UtilActivator.resources.getSettingsString(pHideAccountMenu)
        if (StringUtils.isNotEmpty(hideAccountMenuDefaultValue)) isHideAccountSelectionWhenPossibleEnabled = hideAccountMenuDefaultValue.toBoolean()
        isHideAccountSelectionWhenPossibleEnabled = mConfigService!!.getBoolean(pHideAccountMenu, isHideAccountSelectionWhenPossibleEnabled)
        val hideAccountsStatusDefaultValue = UtilActivator.resources.getSettingsString(pHideAccountStatusSelectors)
        if (StringUtils.isNotEmpty(hideAccountsStatusDefaultValue)) isHideAccountStatusSelectorsEnabled = hideAccountsStatusDefaultValue.toBoolean()
        isHideAccountStatusSelectorsEnabled = mConfigService!!.getBoolean(pHideAccountStatusSelectors,
                isHideAccountStatusSelectorsEnabled)
        val autoAnswerDisableSubmenuDefaultValue = UtilActivator.resources.getSettingsString(pAutoAnswerDisableSubmenu)
        if (StringUtils.isNotEmpty(autoAnswerDisableSubmenuDefaultValue)) isAutoAnswerDisableSubmenu = autoAnswerDisableSubmenuDefaultValue.toBoolean()
        isAutoAnswerDisableSubmenu = mConfigService!!.getBoolean(pAutoAnswerDisableSubmenu, isAutoAnswerDisableSubmenu)
        isChatRoomConfigDisabled = mConfigService!!.getBoolean(CHAT_ROOM_CONFIG_DISABLED_PROP, isChatRoomConfigDisabled)
        isNormalizePhoneNumber = mConfigService!!.getBoolean("gui.NORMALIZE_PHONE_NUMBER", true)
        alerterEnabled = mConfigService!!.getBoolean(ALERTER_ENABLED_PROP, true)

        // Load the "ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS" property.
        acceptPhoneNumberWithAlphaChars = mConfigService!!.getBoolean("gui.ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS", true)
        isHideAddressInCallHistoryTooltipEnabled = mConfigService!!.getBoolean(HIDE_ADDR_IN_CALL_HISTORY_TOOLTIP_PROPERTY,
                isHideAddressInCallHistoryTooltipEnabled)
        isHideDomainInReceivedCallDialogEnabled = mConfigService!!.getBoolean(HIDE_DOMAIN_IN_RECEIVE_CALL_DIALOG_PROPERTY,
                isHideDomainInReceivedCallDialogEnabled)
        val hideExtendedAwayStatusDefaultValue = UtilActivator.resources.getSettingsString(pHideExtendedAwayStatus)
        if (StringUtils.isNotEmpty(hideExtendedAwayStatusDefaultValue)) isHideExtendedAwayStatus = hideExtendedAwayStatusDefaultValue.toBoolean()
        isHideExtendedAwayStatus = mConfigService!!.getBoolean(pHideExtendedAwayStatus, isHideExtendedAwayStatus)
        isSmsNotifyTextDisabled = mConfigService!!.getBoolean(SMS_MSG_NOTIFY_TEXT_DISABLED_PROP, isSmsNotifyTextDisabled)
        showMasterPasswordWarning = mConfigService!!.getBoolean(MASTER_PASS_WARNING_PROP, true)

        // Quite Time settings
        isQuiteHoursEnable = mConfigService!!.getBoolean(pQuiteHoursEnable, true)
        quiteHoursStart = mConfigService!!.getLong(pQuiteHoursStart, TimePreference.DEFAULT_VALUE)
        quiteHoursEnd = mConfigService!!.getLong(pQuiteHoursEnd, TimePreference.DEFAULT_VALUE)
        isHeadsUpEnable = mConfigService!!.getBoolean(pHeadsUpEnable, true)
    }

    /**
     * Checks whether font support is disabled, checking in default settings for the default value.
     *
     * @return is font support disabled.
     */
    private val isFontSupportEnabled: Boolean
        get() {
            val fontDisabledProp = "gui.FONT_SUPPORT_ENABLED"
            var defaultValue = false
            val defaultSettingStr = UtilActivator.resources.getSettingsString(fontDisabledProp)
            if (StringUtils.isNotEmpty(defaultSettingStr)) defaultValue = defaultSettingStr.toBoolean()
            return mConfigService!!.getBoolean(fontDisabledProp, defaultValue)
        }

    /**
     * Return TRUE if "autoPopupNewMessage" property is true, otherwise - return FALSE. Indicates
     * to the user interface whether new messages should be opened and bring to front.
     *
     * @return TRUE if "autoPopupNewMessage" property is true, otherwise - return FALSE.
     */
    fun isAutoPopupNewMessage(): Boolean {
        return autoPopupNewMessage
    }

    /**
     * Updates the "autoPopupNewMessage" property.
     *
     * @param autoPopup indicates to the user interface whether new messages should be opened and bring to front.
     */
    fun setAutoPopupNewMessage(autoPopup: Boolean) {
        autoPopupNewMessage = autoPopup
        if (autoPopupNewMessage) mConfigService!!.setProperty(pAutoPopupNewMessage, "yes") else mConfigService!!.setProperty(pAutoPopupNewMessage, "no")
    }

    /**
     * Return TRUE if "showOffline" property is true, otherwise - return FALSE. Indicates to the
     * user interface whether offline user should be shown in the contact list or not.
     *
     * @return TRUE if "showOffline" property is true, otherwise - return FALSE.
     */
    fun isShowOffline(): Boolean {
        return isShowOffline
    }

    /**
     * Return TRUE if "showApplication" property is true, otherwise - return FALSE. Indicates to
     * the user interface whether the main application window should shown or hidden on startup.
     *
     * @return TRUE if "showApplication" property is true, otherwise - return FALSE.
     */
    fun isApplicationVisible(): Boolean {
        return isApplicationVisible
    }

    /**
     * Return TRUE if "quitWarningShown" property is true, otherwise - return FALSE. Indicates to the user
     * interface whether the quit warning dialog should be shown when user clicks on the X button.
     *
     * @return TRUE if "quitWarningShown" property is true, otherwise - return FALSE. Indicates to
     * the user interface whether the quit warning dialog should be shown when user clicks on the X button.
     */
    fun isQuitWarningShown(): Boolean {
        return isQuitWarningShown
    }

    /**
     * Updates the "isAutoStartOnBoot" property through the `ConfigurationService`.
     *
     * @param autoStart `true` to auto start aTalk on device reboot
     */
    fun setAutoStart(autoStart: Boolean) {
        isAutoStartEnable = autoStart
        mConfigService!!.setProperty(pAutoStart, java.lang.Boolean.toString(autoStart))
    }

    /**
     * Return TRUE if "isTtsEnable" property is true, otherwise - return FALSE.
     * Indicates if TTS is enabled.
     *
     * @return TRUE if "isTtsEnable" property is true, otherwise - return FALSE.
     */
    fun isTtsEnable(): Boolean {
        return isTtsEnable
    }

    /**
     * Updates the "isTtsEnable" property through the `ConfigurationService`.
     *
     * @param ttsEnable `true` to enable tts option
     */
    fun setTtsEnable(ttsEnable: Boolean) {
        isTtsEnable = ttsEnable
        mConfigService!!.setProperty(pTTSEnable, java.lang.Boolean.toString(ttsEnable))
    }

    fun getTtsDelay(): Int {
        return ttsDelay
    }

    /**
     * Updates the "isTtsEnable" property through the `ConfigurationService`.
     *
     * @param delay is the amount of time to wait before start the TTS
     */
    fun setTtsDelay(delay: Int) {
        ttsDelay = delay
        mConfigService!!.setProperty(pTTSDelay, delay)
    }

    /**
     * Return TRUE if "sendMessageDeliveryReceipt" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether message delivery receipts are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    fun isSendMessageDeliveryReceipt(): Boolean {
        return isSendMessageDeliveryReceipt
    }

    /**
     * Updates the "sendChatStateNotifications" property through the `ConfigurationService`.
     *
     * @param isDeliveryReceipt `true` to indicate that message delivery receipts are enabled,
     * `false` otherwise.
     */
    fun setSendMessageDeliveryReceipt(isDeliveryReceipt: Boolean) {
        isSendMessageDeliveryReceipt = isDeliveryReceipt
        mConfigService!!.setProperty(pMessageDeliveryReceipt, java.lang.Boolean.toString(isDeliveryReceipt))
        updateDeliveryReceiptFeature(isDeliveryReceipt)
    }

    /**
     * Return TRUE if "sendChatStateNotifications" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether chat state notifications are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    fun isSendChatStateNotifications(): Boolean {
        return isSendChatStateNotifications
    }

    /**
     * Updates the "sendChatStateNotifications" property through the `ConfigurationService`.
     *
     * @param isChatStateNotification `true` to indicate that chat state notifications are enabled,
     * `false` otherwise.
     */
    fun setSendChatStateNotifications(isChatStateNotification: Boolean) {
        isSendChatStateNotifications = isChatStateNotification
        mConfigService!!.setProperty(pTypingNotification, java.lang.Boolean.toString(isChatStateNotification))
        updateChatStateCapsFeature(isChatStateNotification)
    }

    /**
     * Return TRUE if "sendChatStateNotifications" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether chat state notifications are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    fun isSendThumbnail(): Boolean {
        return isSendThumbnail
    }

    /**
     * Updates the "sendChatStateNotifications" property through the `ConfigurationService`.
     *
     * @param sendThumbnail `true` to indicate that chat state notifications are enabled,
     * `false` otherwise.
     */
    fun setSendThumbnail(sendThumbnail: Boolean) {
        isSendThumbnail = sendThumbnail
        mConfigService!!.setProperty(pSendThumbnail, java.lang.Boolean.toString(isSendThumbnail))
    }

    /**
     * Check to see the file size specified is autoAcceptable:
     * 1. size > 0
     * 2. acceptFileSize != 0 (never)
     * 3. size <= acceptFileSize
     *
     * @param size current file size
     *
     * @return true is auto accept to download
     */
    fun isAutoAcceptFile(size: Long): Boolean {
        return size > 0 && acceptFileSize != 0 && size <= acceptFileSize
    }

    /**
     * The maximum file size that user will automatically accept for download.
     *
     * @return the auto accept file size.
     */
    val autoAcceptFileSize: Long
        get() = acceptFileSize.toLong()

    /**
     * Updates the "acceptFileSize" property through the `ConfigurationService`.
     *
     * @param fileSize indicates if the maximum file size for auto accept.
     */
    fun setAutoAcceptFileSizeSize(fileSize: Int) {
        acceptFileSize = fileSize
        mConfigService!!.setProperty(pAcceptFileSize, acceptFileSize.toString())
    }

    /**
     * Return TRUE if "isPresenceSubscribeAuto" property is true, otherwise - return FALSE.
     * Indicates to user whether presence subscription mode is auto or manual approval.
     *
     * @return TRUE if "isPresenceSubscribeAuto" property is true, otherwise - return FALSE.
     */
    fun isPresenceSubscribeAuto(): Boolean {
        return isPresenceSubscribeAuto
    }

    /**
     * Updates the "isPresenceSubscribeAuto" property through the `ConfigurationService`.
     *
     * @param presenceSubscribeAuto `true` to indicate that chat state notifications are enabled,
     * `false` otherwise.
     */
    fun setPresenceSubscribeAuto(presenceSubscribeAuto: Boolean) {
        isPresenceSubscribeAuto = presenceSubscribeAuto
        mConfigService!!.setProperty(pPresenceSubscribeAuto, presenceSubscribeAuto.toString())
        if (presenceSubscribeAuto) Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all) else Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.manual)
    }

    /**
     * Returns TRUE if the "isMoveContactConfirmationRequested" property is true, otherwise -
     * returns FALSE. Indicates to the user interface whether the confirmation window during the
     * move contact process is enabled or not.
     *
     * @return TRUE if the "isMoveContactConfirmationRequested" property is true, otherwise - returns FALSE
     */
    fun isMoveContactConfirmationRequested(): Boolean {
        return isMoveContactConfirmationRequested
    }

    /**
     * Returns `true` if the "isMultiChatWindowEnabled" property is true, otherwise -
     * returns `false`. Indicates to the user interface whether the chat window could
     * contain multiple chats or just one chat.
     *
     * @return `true` if the "isMultiChatWindowEnabled" property is true, otherwise - returns `false`.
     */
    fun isMultiChatWindowEnabled(): Boolean {
        return isMultiChatWindowEnabled
    }

    /**
     * Updates the "isMultiChatWindowEnabled" property through the `ConfigurationService`.
     *
     * @param isEnabled indicates if the chat window could contain multiple chats or only one chat.
     */
    fun setMultiChatWindowEnabled(isEnabled: Boolean) {
        isMultiChatWindowEnabled = isEnabled
        mConfigService!!.setProperty(pMultiChatWindowEnabled, isMultiChatWindowEnabled.toString())
    }

    /**
     * Updates the "isLeaveChatRoomOnWindowClose" property through the `ConfigurationService`.
     *
     * @param isLeave indicates whether to leave chat room on window close.
     */
    fun setLeaveChatRoomOnWindowClose(isLeave: Boolean) {
        isLeaveChatRoomOnWindowCloseEnabled = isLeave
        mConfigService!!.setProperty(pLeaveChatRoomOnWindowClose, java.lang.Boolean.toString(isLeaveChatRoomOnWindowCloseEnabled))
    }

    /**
     * Returns `true` if the "isHistoryShown" property is true, otherwise - returns
     * `false`. Indicates to the user whether the history is shown in the chat window.
     *
     * @return `true` if the "isHistoryShown" property is true, otherwise - returns `false`.
     */
    fun isHistoryShown(): Boolean {
        return isHistoryShown
    }

    /**
     * Updates the "isHistoryShown" property through the `ConfigurationService`.
     *
     * @param isShown indicates if the message history is shown
     */
    fun setHistoryShown(isShown: Boolean) {
        isHistoryShown = isShown
        mConfigService!!.setProperty(pMessageHistoryShown, java.lang.Boolean.toString(isHistoryShown))
    }

    /**
     * Returns `true`if the "isChatToolbarVisible" property is true, otherwise - returns `false`..
     *
     * @return `true` if the "isChatToolbarVisible" property is true, otherwise - returns `false`.
     */
    fun isChatToolbarVisible(): Boolean {
        return isChatToolbarVisible
    }

    /**
     * Returns `true` if the "isChatStyleBarVisible" property is true, otherwise - returns `false`..
     *
     * @return `true`if the "isChatStyleBarVisible" property is true, otherwise - returns `false`
     * .
     */
    fun isChatStyleBarVisible(): Boolean {
        return isChatStyleBarVisible
    }

    /**
     * Returns `true` if the "isChatSimpleTheme" property is true, otherwise - returns `false`..
     *
     * @return `true` if the "isChatSimpleTheme" property is true, otherwise - returns `false`.
     */
    fun isChatSimpleThemeEnabled(): Boolean {
        return isChatSimpleThemeEnabled
    }

    /**
     * 'true` if the "ADVANCED_CONFIG_DISABLED" property is true, otherwise - returns `false`..
     */
    fun isAdvancedAccountConfigDisabled(): Boolean {
        return isAdvancedAccountConfigDisabled
    }

    /**
     * The default chat font family.
     */
    var chatDefaultFontFamily: String?
        get() = defaultFontFamily
        set(fontFamily) {
            defaultFontFamily = fontFamily
            mConfigService!!.setProperty("gui.chat.DEFAULT_FONT_FAMILY", fontFamily)
        }

    /**
     * The default chat font size.
     */
    var chatDefaultFontSize: Int
        get() = if (StringUtils.isNotEmpty(defaultFontSize)) defaultFontSize!!.toInt() else -1
        set(fontSize) {
            defaultFontSize = fontSize.toString()
            mConfigService!!.setProperty("gui.chat.DEFAULT_FONT_SIZE", fontSize)
        }

    /**
     * The default chat font color.
     */
    val chatDefaultFontColor: Color?
        get() = if (defaultFontColor == -1) null else Color(defaultFontColor)

    /**
     * Sets the advanced account config disabled property.
     */
    fun setAdvancedAccountConfigDisabled(disabled: Boolean) {
        isAdvancedAccountConfigDisabled = disabled
        mConfigService!!.setProperty("gui.account.ADVANCED_CONFIG_DISABLED",
                isAdvancedAccountConfigDisabled.toString())
    }

    /**
     * Return the "sendMessageCommand" property that was saved previously through the
     * `ConfigurationService`. Indicates to the user interface whether the default send
     * message command is Enter or  CTRL-Enter.
     *
     * @return "Enter" or "CTRL-Enter" message commands.
     */
    fun getSendMessageCommand(): String? {
        return sendMessageCommand
    }

    /**
     * Updates the "sendMessageCommand" property through the `ConfigurationService`.
     *
     * @param newMessageCommand the command used to send a message ( it could be ENTER_COMMAND or CTRL_ENTER_COMMAND)
     */
    fun setSendMessageCommand(newMessageCommand: String?) {
        sendMessageCommand = newMessageCommand
        mConfigService!!.setProperty(pMsgCommand, newMessageCommand)
    }

    /**
     * Return the "lastContactParent" property that was saved previously through the
     * `ConfigurationService`. Indicates the last selected group on adding new contact
     *
     * @return group name of the last selected group when adding contact.
     */
    fun getLastContactParent(): String? {
        return lastContactParent
    }

    /**
     * Returns the call conference provider used for the last conference call.
     *
     * @return the call conference provider used for the last conference call
     */
    fun getLastCallConferenceProvider(): ProtocolProviderService? {
        return if (lastCallConferenceProvider != null) lastCallConferenceProvider else findProviderFromAccountId(mConfigService!!.getString("gui.call.lastCallConferenceProvider"))

        // Obtain the "lastCallConferenceAccount" property from the configuration service
    }

    /**
     * Returns the protocol provider associated with the given `accountId`.
     *
     * @param savedAccountId the identifier of the account
     *
     * @return the protocol provider associated with the given `accountId`
     */
    private fun findProviderFromAccountId(savedAccountId: String?): ProtocolProviderService? {
        var protocolProvider: ProtocolProviderService? = null
        for (providerFactory in UtilActivator.protocolProviderFactories.values) {
            for (accountId in providerFactory.getRegisteredAccounts()) {
                // We're interested only in the savedAccountId
                if (accountId.accountUniqueID != savedAccountId) continue
                val serRef = providerFactory.getProviderForAccount(accountId)
                protocolProvider = UtilActivator.bundleContext!!.getService(serRef)
                if (protocolProvider != null) break
            }
        }
        return protocolProvider
    }
    /**
     * Returns the number of messages from chat history that would be shown in the chat window.
     *
     * @return the number of messages from chat history that would be shown in the chat window.
     */
    /**
     * Updates the "webPage" property through the `ConfigurationService`.
     *
     * @param webPage the web page for access.
     */
    var webPage: String
        get() {
            WebViewFragment.initWebView()
            return if (StringUtils.isBlank(mWebPage)) aTalkApp.getResString(R.string.service_gui_settings_WEBVIEW_SUMMARY) else mWebPage!!
        }
        set(webPage) {
            mWebPage = if (StringUtils.isEmpty(webPage)) webPage else webPage.trim { it <= ' ' }
            mConfigService!!.setProperty(pWebPage, webPage)
        }

    /**
     * Returns the number of messages from chat history that would be shown in the chat window.
     *
     * @return the number of messages from chat history that would be shown in the chat window.
     */
    fun getChatHistorySize(): Int {
        return chatHistorySize
    }

    /**
     * Updates the "chatHistorySize" property through the `ConfigurationService`.
     *
     * @param historySize indicates if the history logging is enabled.
     */
    fun setChatHistorySize(historySize: Int) {
        chatHistorySize = historySize
        mConfigService!!.setProperty(pChatHistorySize, chatHistorySize.toString())
    }

    /**
     * Returns the preferred height of the chat write area.
     *
     * @return the preferred height of the chat write area.
     */
    fun getChatWriteAreaSize(): Int {
        return chatWriteAreaSize
    }

    /**
     * Returns `true` if transparent windows are enabled, `false` otherwise.
     *
     * @return `true` if transparent windows are enabled, `false` otherwise.
     */
    fun isTransparentWindowEnabled(): Boolean {
        return isTransparentWindowEnabled
    }

    /**
     * Returns the transparency value for all transparent windows.
     *
     * @return the transparency value for all transparent windows.
     */
    fun getWindowTransparency(): Int {
        return windowTransparency
    }

    /**
     * Returns the last opened directory of the send file file chooser.
     *
     * @return the last opened directory of the send file file chooser
     */
    fun getSendFileLastDir(): String? {
        return sendFileLastDir
    }

    /**
     * Returns `true` if phone numbers should be normalized, `false` otherwise.
     *
     * @return `true` if phone numbers should be normalized, `false` otherwise.
     */
    fun isNormalizePhoneNumber(): Boolean {
        return isNormalizePhoneNumber
    }

    /**
     * Updates the "NORMALIZE_PHONE_NUMBER" property.
     *
     * @param isNormalize indicates to the user interface whether all dialed phone numbers should be normalized
     */
    fun setNormalizePhoneNumber(isNormalize: Boolean) {
        isNormalizePhoneNumber = isNormalize
        mConfigService!!.setProperty("gui.NORMALIZE_PHONE_NUMBER", isNormalize.toString())
    }

    /**
     * Returns `true` if window alerter is enabled (tack bar or dock icon).
     *
     * @return `true` if window alerter is enables, `false` otherwise.
     */
    fun isAlerterEnabled(): Boolean {
        return alerterEnabled
    }

    /**
     * Updates the "chatalerter.ENABLED" property.
     *
     * @param isEnabled indicates whether to enable or disable alerter.
     */
    fun setAlerterEnabled(isEnabled: Boolean) {
        alerterEnabled = isEnabled
        mConfigService!!.setProperty(ALERTER_ENABLED_PROP, isEnabled.toString())
    }

    fun setQuiteHour(property: String, value: Any) {
        if (value is Boolean) {
            setQuiteHoursEnable(value)
        } else if (pQuiteHoursStart == property) {
            setQuiteHoursStart(value as Long)
        } else {
            setQuiteHoursEnd(value as Long)
        }
    }

    /**
     * Returns `true` if Quite Hours is enabled
     *
     * @return `true` if Quite Hours is enables, `false` otherwise.
     */
    fun isQuiteHoursEnable(): Boolean {
        return isQuiteHoursEnable
    }

    /**
     * Updates the Quite Hours property.
     *
     * @param isEnabled indicates whether to enable or disable quite hours.
     */
    fun setQuiteHoursEnable(isEnabled: Boolean) {
        isQuiteHoursEnable = isEnabled
        mConfigService!!.setProperty(pQuiteHoursEnable, isEnabled.toString())
    }

    /**
     * Returns Quite Hours start time.
     *
     * @return `true` get the Quite Hours start time.
     */
    fun getQuiteHoursStart(): Long {
        return quiteHoursStart
    }

    /**
     * Updates the Quite Hours start time
     *
     * @param time is the quite hours start time.
     */
    fun setQuiteHoursStart(time: Long) {
        quiteHoursStart = time
        mConfigService!!.setProperty(pQuiteHoursStart, time)
    }

    /**
     * Returns Quite Hours end time.
     *
     * @return `true` get the Quite Hours end time.
     */
    fun getQuiteHoursEnd(): Long {
        return quiteHoursEnd
    }

    /**
     * Updates the Quite Hours end time
     *
     * @param time is the quite hours end time.
     */
    fun setQuiteHoursEnd(time: Long) {
        quiteHoursEnd = time
        mConfigService!!.setProperty(pQuiteHoursEnd, time)
    }

    /**
     * Updates the "sendChatStateNotifications" property through the `ConfigurationService`.
     *
     * @param headsUp `true` to indicate HeadUp notifications are enabled,
     * `false` otherwise.
     */
    fun setHeadsUp(headsUp: Boolean) {
        isHeadsUpEnable = headsUp
        mConfigService!!.setProperty(pHeadsUpEnable, isHeadsUpEnable.toString())
    }

    /**
     * Returns `true` if a string with a alphabetical character might be considered as
     * a phone number. `false` otherwise.
     *
     * @return `true` if a string with a alphabetical character might be considered as
     * a phone number. `false` otherwise.
     */
    fun acceptPhoneNumberWithAlphaChars(): Boolean {
        return acceptPhoneNumberWithAlphaChars
    }

    /**
     * Updates the "ACCEPT_PHONE_NUMBER_WITH_CHARS" property.
     *
     * @param accept indicates to the user interface whether a string with alphabetical characters might be
     * accepted as a phone number.
     */
    fun setAcceptPhoneNumberWithAlphaChars(accept: Boolean) {
        acceptPhoneNumberWithAlphaChars = accept
        mConfigService!!.setProperty("gui.ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS",
                acceptPhoneNumberWithAlphaChars.toString())
    }

    /**
     * Indicates if the single interface is enabled.
     *
     * @return `true` if the single window interface is enabled, `false` - otherwise
     */
    fun isSingleWindowInterfaceEnabled(): Boolean {
        return isSingleWindowInterfaceEnabled
    }

    /**
     * Whether to show or not the master password warning.
     *
     * @return `true` to show it, and `false` otherwise.
     */
    fun showMasterPasswordWarning(): Boolean {
        return showMasterPasswordWarning
    }

    /**
     * Updates the value of whether to show master password warning.
     *
     * @param value the new value to set.
     */
    fun setShowMasterPasswordWarning(value: Boolean) {
        showMasterPasswordWarning = value
        mConfigService!!.setProperty(MASTER_PASS_WARNING_PROP, value)
    }

    /**
     * Updates the "singleWindowInterface" property through the `ConfigurationService`.
     *
     * @param isEnabled `true` to indicate that the single window interface is enabled, `false` - otherwise
     */
    fun setSingleWindowInterfaceEnabled(isEnabled: Boolean) {
        isSingleWindowInterfaceEnabled = isEnabled
        mConfigService!!.setProperty(SINGLE_WINDOW_INTERFACE_ENABLED, isEnabled)
    }

    /**
     * Sets the transparency value for all transparent windows.
     *
     * @param transparency the transparency value for all transparent windows.
     */
    fun setWindowTransparency(transparency: Int) {
        windowTransparency = transparency
    }

    /**
     * Updates the "showOffline" property through the `ConfigurationService`.
     *
     * @param isShowOffline `true` to indicate that the offline users should be shown, `false` otherwise.
     */
    fun setShowOffline(isShowOffline: Boolean) {
        ConfigurationUtils.isShowOffline = isShowOffline
        mConfigService!!.setProperty("gui.showOffline", isShowOffline.toString())
    }

    /**
     * Updates the "showCallPanel" property through the `ConfigurationService`.
     *
     * @param isCallPanelShown `true` to indicate that the call panel should be shown, `false` otherwise.
     */
    fun setShowCallPanel(isCallPanelShown: Boolean) {
        ConfigurationUtils.isCallPanelShown = isCallPanelShown
        mConfigService!!.setProperty("gui.showCallPanel", isCallPanelShown.toString())
    }

    /**
     * Updates the "showApplication" property through the `ConfigurationService`.
     *
     * @param isVisible `true` to indicate that the application should be shown, `false` otherwise.
     */
    fun setApplicationVisible(isVisible: Boolean) {
        // If we're already in the desired visible state, don't change anything.
        if (isApplicationVisible == isVisible) return
        isApplicationVisible = isVisible
        mConfigService!!.setProperty("systray.showApplication", isVisible.toString())
    }

    /**
     * Updates the "showAppQuitWarning" property through the `ConfigurationService`.
     *
     * @param isWarningShown indicates if the message warning the user that the application would not be closed if
     * she clicks the X button would be shown again.
     */
    fun setQuitWarningShown(isWarningShown: Boolean) {
        isQuitWarningShown = isWarningShown
        mConfigService!!.setProperty("gui.quitWarningShown", isQuitWarningShown.toString())
    }

    /**
     * Saves the popup handler choice made by the user.
     *
     * @param handler the handler which will be used
     */
    fun setPopupHandlerConfig(handler: String?) {
        mConfigService!!.setProperty("systray.POPUP_HANDLER", handler)
    }

    /**
     * Updates the "lastContactParent" property through the `ConfigurationService`.
     *
     * @param groupName the group name of the selected group when adding last contact
     */
    fun setLastContactParent(groupName: String?) {
        lastContactParent = groupName
        mConfigService!!.setProperty("gui.addcontact.lastContactParent", groupName)
    }

    /**
     * Updates the "isMoveContactQuestionEnabled" property through the `ConfigurationService`.
     *
     * @param isRequested indicates if a confirmation would be requested from user during the move contact process.
     */
    fun setMoveContactConfirmationRequested(isRequested: Boolean) {
        isMoveContactConfirmationRequested = isRequested
        mConfigService!!.setProperty("gui.isMoveContactConfirmationRequested",
                isMoveContactConfirmationRequested.toString())
    }

    /**
     * Updates the "isTransparentWindowEnabled" property through the `ConfigurationService`.
     *
     * @param isTransparent indicates if the transparency is enabled in the application.
     */
    fun setTransparentWindowEnabled(isTransparent: Boolean) {
        isTransparentWindowEnabled = isTransparent
        mConfigService!!.setProperty(pTransparentWindowEnabled, isTransparentWindowEnabled.toString())
    }

    /**
     * Updates the "isChatToolbarVisible" property through the `ConfigurationService`.
     *
     * @param isVisible indicates if the chat toolbar is visible.
     */
    fun setChatToolbarVisible(isVisible: Boolean) {
        isChatToolbarVisible = isVisible
        mConfigService!!.setProperty("gui.chat.ChatWindow.showToolbar", isChatToolbarVisible.toString())
    }

    /**
     * Updates the "isChatSimpleThemeEnabled" property through the `ConfigurationService`.
     *
     * @param isEnabled indicates if the chat simple theme is enabled
     */
    fun setChatSimpleThemeEnabled(isEnabled: Boolean) {
        isChatSimpleThemeEnabled = isEnabled
        mConfigService!!.setProperty(CHAT_SIMPLE_THEME_ENABLED_PROP, isChatSimpleThemeEnabled.toString())
    }

    /**
     * Updates the "isChatStyleBarVisible" property through the `ConfigurationService`.
     *
     * @param isVisible indicates if the chat styleBar is visible.
     */
    fun setChatStyleBarVisible(isVisible: Boolean) {
        isChatStyleBarVisible = isVisible
        mConfigService!!.setProperty("gui.chat.ChatWindow.showStylebar", isChatStyleBarVisible.toString())
    }

    /**
     * Updates the pChatWriteAreaSize property through the `ConfigurationService`.
     *
     * @param size the new size to set
     */
    fun setChatWriteAreaSize(size: Int) {
        chatWriteAreaSize = size
        mConfigService!!.setProperty(pChatWriteAreaSize, Integer.toString(chatWriteAreaSize))
    }

    /**
     * Updates the "SEND_FILE_LAST_DIR" property through the `ConfigurationService`.
     *
     * @param lastDir last download directory
     */
    fun setSendFileLastDir(lastDir: String?) {
        sendFileLastDir = lastDir
        mConfigService!!.setProperty("gui.chat.filetransfer.SEND_FILE_LAST_DIR", lastDir)
    }

    /**
     * Sets the call conference provider used for the last conference call.
     *
     * @param protocolProvider the call conference provider used for the last conference call
     */
    fun setLastCallConferenceProvider(protocolProvider: ProtocolProviderService) {
        lastCallConferenceProvider = protocolProvider
        mConfigService!!.setProperty("gui.call.lastCallConferenceProvider",
                protocolProvider.accountID.accountUniqueID)
    }

    /**
     * Sets the default isBold property.
     *
     * @param isBold indicates if the default chat font is bold
     */
    fun setChatFontIsBold(isBold: Boolean) {
        isChatFontBold = isBold
        mConfigService!!.setProperty("gui.chat.DEFAULT_FONT_BOLD", isBold)
    }

    /**
     * Sets the default isItalic property.
     *
     * @param isItalic indicates if the default chat font is italic
     */
    fun setChatFontIsItalic(isItalic: Boolean) {
        isChatFontItalic = isItalic
        mConfigService!!.setProperty("gui.chat.DEFAULT_FONT_ITALIC", isItalic)
    }

    /**
     * Sets the default isUnderline property.
     *
     * @param isUnderline indicates if the default chat font is underline
     */
    fun setChatFontIsUnderline(isUnderline: Boolean) {
        isChatFontUnderline = isUnderline
        mConfigService!!.setProperty("gui.chat.DEFAULT_FONT_UNDERLINE", isUnderline)
    }

    /**
     * Sets the default font color.
     *
     * @param fontColor the default font color
     */
    fun setChatDefaultFontColor(fontColor: Color) {
        defaultFontColor = fontColor.rgb
        mConfigService!!.setProperty("gui.chat.DEFAULT_FONT_COLOR", defaultFontColor)
    }

    /**
     * Initialize aTalk app Theme;default to Theme.DARK if not defined
     */
    fun initAppTheme() {
        val theme: ThemeHelper.Theme
        val themeValue = mConfigService!!.getInt(SettingsFragment.P_KEY_THEME, ThemeHelper.Theme.DARK.ordinal)
        theme = if (themeValue == ThemeHelper.Theme.DARK.ordinal || themeValue == android.R.style.Theme) {
            ThemeHelper.Theme.DARK
        } else {
            ThemeHelper.Theme.LIGHT
        }
        appTheme = theme
    }

    /**
     * Updates the value of a contact option property through the `ConfigurationService`.
     * The property-value pair is stored a JSONObject element in contact options
     *
     * @param contactJid the identifier/BareJid of the contact table to update
     * @param property the property name in the contact options
     * @param value the value of the contact options property if null, property will be removed
     */
    fun updateContactProperty(contactJid: Jid, property: String, value: String?) {
        val options = getContactOptions(contactJid)
        try {
            if (value == null) options.remove(property) else options.put(property, value)
        } catch (e: JSONException) {
            Timber.w("Contact property update failed: %s: %s", contactJid, property)
        }
        val args = arrayOf(contactJid.toString())
        contentValues.clear()
        contentValues.put(Contact.OPTIONS, options.toString())
        mDB.update(Contact.TABLE_NAME, contentValues, Contact.CONTACT_JID + "=?", args)
    }

    /**
     * Returns the contact options, saved via the `ConfigurationService`.
     *
     * @param contactJid the identifier/BareJid of the contact table to retrieve
     * @param property the property name in the contact options
     *
     * @return the value of the contact options property, saved via the `ConfigurationService`.
     */
    fun getContactProperty(contactJid: Jid, property: String): String? {
        val options = getContactOptions(contactJid)
        try {
            return options.getString(property)
        } catch (e: JSONException) {
            // Timber.w("ChatRoom property not found for: " + chatRoomId + ": " + property);
        }
        return null
    }

    /**
     * Returns the options saved in `ConfigurationService` associated with the `Contact`.
     *
     * @param contactJid the identifier/BareJid of the contact table to update
     *
     * @return the contact options saved in `ConfigurationService`.
     */
    private fun getContactOptions(contactJid: Jid): JSONObject {
        // mDB is null when access during restoring process

        if (mDB == null) mDB = DatabaseBackend.writableDB
        val columns = arrayOf(Contact.OPTIONS)
        val args = arrayOf(contactJid.asBareJid().toString())
        val cursor = mDB.query(Contact.TABLE_NAME, columns, Contact.CONTACT_JID + "=?", args,
                null, null, null)
        var options = JSONObject()
        while (cursor.moveToNext()) {
            val value = cursor.getString(0)
            options = try {
                JSONObject(value ?: "")
            } catch (e: JSONException) {
                JSONObject()
            }
        }
        cursor.close()
        return options
    }

    /**
     * Saves a chat room through the `ConfigurationService`.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param oldChatRoomId the old identifier of the chat room
     * @param newChatRoomId the new identifier of the chat room  = newChatRoomName
     */
    fun saveChatRoom(protocolProvider: ProtocolProviderService, oldChatRoomId: String, newChatRoomId: String) {
        val columns = arrayOf(ChatSession.SESSION_UUID)
        val accountUid = protocolProvider.accountID.accountUniqueID
        var args = arrayOf(accountUid, oldChatRoomId)
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null)
        contentValues.clear()
        if (cursor.count > 0) {
            if (oldChatRoomId != newChatRoomId) {
                cursor.moveToNext()
                args = arrayOf(cursor.getString(0))
                contentValues.put(ChatSession.ENTITY_JID, newChatRoomId)
                mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.SESSION_UUID + "=?", args)
            }
        } else {
            val timeStamp = System.currentTimeMillis().toString()
            val sessionUuid = timeStamp + Math.abs(timeStamp.hashCode())
            val accountUuid = protocolProvider.accountID.accountUuid
            val attributes = JSONObject().toString()
            contentValues.put(ChatSession.SESSION_UUID, sessionUuid)
            contentValues.put(ChatSession.ACCOUNT_UUID, accountUuid)
            contentValues.put(ChatSession.ACCOUNT_UID, accountUid)
            contentValues.put(ChatSession.ENTITY_JID, newChatRoomId)
            contentValues.put(ChatSession.CREATED, timeStamp)
            contentValues.put(ChatSession.STATUS, ChatFragment.MSGTYPE_OMEMO)
            contentValues.put(ChatSession.MODE, ChatSession.MODE_MULTI)
            contentValues.put(ChatSession.ATTRIBUTES, attributes)
            mDB.insert(ChatSession.TABLE_NAME, null, contentValues)
        }
        cursor.close()
    }

    /**
     * Removes a chatRoom through the `ConfigurationService`.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param chatRoomId the identifier of the chat room to remove
     */
    fun removeChatRoom(protocolProvider: ProtocolProviderService, chatRoomId: String?) {
        val accountUid = protocolProvider.accountID.accountUniqueID
        val args = arrayOf(accountUid, chatRoomId)
        mDB.delete(ChatSession.TABLE_NAME, ChatSession.ACCOUNT_UID + "=? AND "
                + ChatSession.ENTITY_JID + "=?", args)
    }

    /**
     * Updates the status of the chat room through the `ConfigurationService`.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param chatRoomId the identifier of the chat room to update
     * @param chatRoomStatus the new status of the chat room
     */
    fun updateChatRoomStatus(protocolProvider: ProtocolProviderService,
            chatRoomId: String, chatRoomStatus: String?) {
        updateChatRoomProperty(protocolProvider, chatRoomId, ChatRoom.CHATROOM_LAST_STATUS, chatRoomStatus)
    }

    /**
     * Returns the last chat room status, saved through the `ConfigurationService`.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomId the identifier of the chat room
     *
     * @return the last chat room status, saved through the `ConfigurationService`.
     */
    fun getChatRoomStatus(protocolProvider: ProtocolProviderService, chatRoomId: String): String? {
        return getChatRoomProperty(protocolProvider, chatRoomId, ChatRoom.CHATROOM_LAST_STATUS)
    }

    /**
     * Updates the value of a chat room property through the `ConfigurationService`.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param chatRoomId the identifier of the chat room to update
     * @param property the name of the property of the chat room
     * @param value the value of the property if null, property will be removed
     */
    fun updateChatRoomProperty(protocolProvider: ProtocolProviderService,
            chatRoomId: String, property: String, value: String?,
    ) {
        val attributes = getChatRoomAttributes(protocolProvider, chatRoomId)
        try {
            if (value == null) attributes.remove(property) else attributes.put(property, value)
        } catch (e: JSONException) {
            Timber.w("ChatRoom property update failed: %s: %s", chatRoomId, property)
        }
        val accountUid = protocolProvider.accountID.accountUniqueID
        val args = arrayOf(accountUid, chatRoomId)
        contentValues.clear()
        contentValues.put(ChatSession.ATTRIBUTES, attributes.toString())
        mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args)
    }

    /**
     * Returns the chat room property, saved through the `ConfigurationService`.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomId the identifier of the chat room
     * @param property the property name, saved through the `ConfigurationService`.
     *
     * @return the value of the property, saved through the `ConfigurationService`.
     */
    fun getChatRoomProperty(protocolProvider: ProtocolProviderService,
            chatRoomId: String, property: String,
    ): String? {
        val attributes = getChatRoomAttributes(protocolProvider, chatRoomId)
        try {
            return attributes.getString(property)
        } catch (e: JSONException) {
            // Timber.w("ChatRoom property not found for: " + chatRoomId + ": " + property);
        }
        return null
    }

    /**
     * Returns the chat room prefix saved in `ConfigurationService` associated with the
     * `accountID` and `chatRoomID`.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomId the chat room id
     *
     * @return the chat room prefix saved in `ConfigurationService`.
     */
    private fun getChatRoomAttributes(protocolProvider: ProtocolProviderService, chatRoomId: String?): JSONObject {
        //mDB is null when access during restoring process
        if (mDB == null) mDB = DatabaseBackend.writableDB
        val columns = arrayOf(ChatSession.ATTRIBUTES)
        val accountUid = protocolProvider.accountID.accountUniqueID
        val args = arrayOf(accountUid, chatRoomId)
        val cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null)
        var attributes = JSONObject()
        while (cursor.moveToNext()) {
            val value = cursor.getString(0)
            attributes = try {
                JSONObject(value ?: "")
            } catch (e: JSONException) {
                JSONObject()
            }
        }
        cursor.close()
        return attributes
    }

    /**
     * Returns the chatRoom prefix saved in `ConfigurationService` associated with the
     * `accountID` and `chatRoomID`.
     *
     *
     * chatRoomPrefix is used as property to store encrypted password in DB. So need to start
     * with AccountUuid for it to handle properly and to auto-clean when the account is removed.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomID the chat room id (cmeng: can contain account serviceName e.g. example.org??)
     *
     * @return the chatRoom sessionUid saved in `ConfigurationService`.
     */
    fun getChatRoomPrefix(protocolProvider: ProtocolProviderService, chatRoomID: String?): String? {
        val accountID = protocolProvider.accountID
        val mhs = MessageHistoryActivator.messageHistoryService
        val sessionUuid = mhs.getSessionUuidByJid(accountID, chatRoomID!!)
        if (StringUtils.isEmpty(sessionUuid)) {
            Timber.w("Failed to get MUC prefix for chatRoom: %s", chatRoomID)
            return null
        }
        return accountID.accountUuid + ".muc_" + sessionUuid
    }

    /**
     * Stores the last group `status` for the given `groupID`.
     *
     * @param groupID the identifier of the group (prefixed with group)
     * @param isCollapsed indicates if the group is collapsed or expanded
     */
    fun setContactListGroupCollapsed(groupID: String, isCollapsed: Boolean) {
        val prefix = "gui.contactlist.groups"
        val groups = mConfigService!!.getPropertyNamesByPrefix(prefix, true)
        var isExistingGroup = false
        for (groupRootPropName in groups) {
            val storedID = mConfigService!!.getString(groupRootPropName)
            if (storedID == groupID) {
                mConfigService!!.setProperty("$groupRootPropName.isClosed", isCollapsed.toString())
                isExistingGroup = true
                break
            }
        }
        if (!isExistingGroup) {
            val groupNodeName = "group" + System.currentTimeMillis()
            val groupPackage = "$prefix.$groupNodeName"
            mConfigService!!.setProperty(groupPackage, groupID)
            mConfigService!!.setProperty("$groupPackage.isClosed", isCollapsed.toString())
        }
    }

    /**
     * Returns `true` if the group given by `groupID` is collapsed or `false` otherwise.
     *
     * @param groupID the identifier of the group
     *
     * @return `true` if the group given by `groupID` is collapsed or `false` otherwise
     */
    fun isContactListGroupCollapsed(groupID: String): Boolean {
        val prefix = "gui.contactlist.groups"
        val groups = mConfigService!!.getPropertyNamesByPrefix(prefix, true)
        for (groupRootPropName in groups) {
            val storedID = mConfigService!!.getString(groupRootPropName)
            if (storedID == groupID) {
                val status = mConfigService!!.getProperty("$groupRootPropName.isClosed") as String
                return status.toBoolean()
            }
        }
        return false
    }

    /**
     * Indicates if the account configuration is disabled.
     *
     * @return `true` if the account manual configuration and creation is disabled, otherwise return `false`
     */
    val isShowAccountConfig: Boolean
        get() {
            val SHOW_ACCOUNT_CONFIG_PROP = "gui.configforms.SHOW_ACCOUNT_CONFIG"
            val defaultValue = !UtilActivator.resources.getSettingsString("gui.account.ACCOUNT_CONFIG_DISABLED").toBoolean()
            return mConfigService!!.getBoolean(SHOW_ACCOUNT_CONFIG_PROP, defaultValue)
        }

    /**
     * Returns the package name under which we would store information for the given factory.
     *
     * @param factory the `ProtocolProviderFactory`, which package name we're looking for
     *
     * @return the package name under which we would store information for the given factory
     */
    fun getFactoryImplPackageName(factory: ProtocolProviderFactory): String {
        val className = factory.javaClass.name
        return className.substring(0, className.lastIndexOf('.'))
    }
    /**
     * Returns the configured client port.
     *
     * @return the client port
     */
    /**
     * Sets the client port.
     */
    var clientPort: Int
        get() = mConfigService!!.getInt(ProtocolProviderFactory.PREFERRED_CLEAR_PORT_PROPERTY_NAME, 5060)
        set(port) {
            mConfigService!!.setProperty(ProtocolProviderFactory.PREFERRED_CLEAR_PORT_PROPERTY_NAME, port)
        }
    /**
     * Returns the client secure port.
     *
     * @return the client secure port
     */
    /**
     * Sets the client secure port.
     */
    var clientSecurePort: Int
        get() = mConfigService!!.getInt(ProtocolProviderFactory.PREFERRED_SECURE_PORT_PROPERTY_NAME, 5061)
        set(port) {
            mConfigService!!.setProperty(ProtocolProviderFactory.PREFERRED_SECURE_PORT_PROPERTY_NAME, port)
        }

    /**
     * Returns the list of enabled SSL protocols.
     *
     * @return the list of enabled SSL protocols
     */
    val enabledSslProtocols: Array<String>
        get() {
            val enabledSslProtocols = mConfigService!!.getString("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS")
            if (StringUtils.isBlank(enabledSslProtocols)) {
                val temp: SSLSocket
                return try {
                    temp = SSLSocketFactory.getDefault().createSocket() as SSLSocket
                    temp.enabledProtocols
                } catch (e: IOException) {
                    Timber.e(e)
                    availableSslProtocols
                }
            }
            return enabledSslProtocols!!.split("(,)|(,\\s)".toRegex()).toTypedArray()
        }

    /**
     * Returns the list of available SSL protocols.
     *
     * @return the list of available SSL protocols
     */
    val availableSslProtocols: Array<String>
        get() {
            val temp: SSLSocket
            return try {
                temp = SSLSocketFactory.getDefault().createSocket() as SSLSocket
                temp.supportedProtocols
            } catch (e: IOException) {
                Timber.e(e)
                arrayOf()
            }
        }

    /**
     * Sets the enables SSL protocols list.
     *
     * @param enabledProtocols the list of enabled SSL protocols to set
     */
    fun setEnabledSslProtocols(enabledProtocols: Array<String?>?) {
        if (enabledProtocols == null || enabledProtocols.isEmpty()) mConfigService!!.removeProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS") else {
            val protocols = Arrays.toString(enabledProtocols)
            mConfigService!!.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS",
                    protocols.substring(1, protocols.length - 1))
        }
    }

    /**
     * Returns `true` if the account associated with `protocolProvider` has at least
     * one video format enabled in it's configuration, `false` otherwise.
     *
     * @return `true` if the account associated with `protocolProvider` has at least
     * one video format enabled in it's configuration, `false` otherwise.
     */
    fun hasEnabledVideoFormat(protocolProvider: ProtocolProviderService): Boolean {
        val accountProperties = protocolProvider.accountID.accountProperties
        val encodingConfiguration: EncodingConfiguration?
        val overrideEncodings = accountProperties[ProtocolProviderFactory.OVERRIDE_ENCODINGS]
        if (overrideEncodings.toBoolean()) {
            encodingConfiguration = UtilActivator.mediaService!!.createEmptyEncodingConfiguration()
            encodingConfiguration!!.loadProperties(accountProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX)
        } else {
            encodingConfiguration = UtilActivator.mediaService!!.currentEncodingConfiguration
        }
        return encodingConfiguration!!.hasEnabledFormat(MediaType.VIDEO)
    }

    /**
     * Update EntityCaps when <DeliveryReceipt></DeliveryReceipt> feature is enabled or disable
     *
     * @param isDeliveryReceiptEnable indicates whether Message Delivery Receipt feature is enable or disable
     */
    private fun updateDeliveryReceiptFeature(isDeliveryReceiptEnable: Boolean) {
        val ppServices = registeredProviders
        for (pps in ppServices) {
            val connection = pps.connection
            if (connection != null) {
                /* XEP-0184: Message Delivery Receipts - global option */
                val discoveryManager = ServiceDiscoveryManager.getInstanceFor(connection)
                val deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection)
                if (isDeliveryReceiptEnable) {
                    discoveryManager.addFeature(DeliveryReceipt.NAMESPACE)
                    deliveryReceiptManager.autoReceiptMode = DeliveryReceiptManager.AutoReceiptMode.ifIsSubscribed
                } else {
                    discoveryManager.removeFeature(DeliveryReceipt.NAMESPACE)
                    deliveryReceiptManager.autoReceiptMode = DeliveryReceiptManager.AutoReceiptMode.disabled
                }
            }
        }
    }

    /**
     * Update EntityCaps when <ChatState></ChatState> feature is enabled or disable
     *
     * @param isChatStateEnable indicates whether ChatState feature is enable or disable
     */
    private fun updateChatStateCapsFeature(isChatStateEnable: Boolean) {
        val ppServices = registeredProviders
        for (pps in ppServices) {
            val connection = pps.connection
            if (connection != null) {
                val discoveryManager = ServiceDiscoveryManager.getInstanceFor(connection)
                OperationSetContactCapabilitiesJabberImpl.setOperationSetChatStateFeatures(isChatStateEnable)
                // cmeng: not required for both?
                // MetaContactChatTransport.setChatStateSupport(isChatStateEnable);
                // ConferenceChatTransport.setChatStateSupport(isChatStateEnable);
                if (isChatStateEnable) {
                    discoveryManager.addFeature(ChatStateManager.NAMESPACE)
                } else {
                    discoveryManager.removeFeature(ChatStateManager.NAMESPACE)
                }
            }
        }
    }

    // ====================== Function use when aTalk app is not fully initialize e.g. init UI language =======================
    // Note: aTalkApp get initialize much earlier than ConfigurationUtils
    private val sqlStore = SQLiteConfigurationStore(aTalkApp.instance)

    /**
     * Direct fetching of the property from SQLiteConfigurationStore on system startup
     *
     * @param propertyName of the value to retrieve
     * @param defValue default value to use
     *
     * @return the retrieve value
     */
    fun getProperty(propertyName: String, defValue: String): String {
        val objValue = sqlStore.getProperty(propertyName)
        return objValue?.toString() ?: defValue
    }

    /**
     * Listens for changes of the properties.
     */
    private class ConfigurationChangeListener : PropertyChangeListener {
        override fun propertyChange(evt: PropertyChangeEvent) {
            // All properties we're interested in here are Strings.
            if (evt.newValue !is String) return
            val newValue = evt.newValue as String

            when (evt.propertyName) {
                "gui.addcontact.lastContactParent" -> {
                    lastContactParent = newValue
                }
                pAutoPopupNewMessage -> {
                    autoPopupNewMessage = "yes".equals(newValue, ignoreCase = true)
                }
                pMsgCommand -> {
                    sendMessageCommand = newValue
                }
                "gui.showCallPanel" -> {
                    isCallPanelShown = newValue.toBoolean()
                }
                pAutoStart -> {
                    isAutoStartEnable = newValue.toBoolean()
                }
                "gui.showOffline" -> {
                    isShowOffline = newValue.toBoolean()
                }
                "systray.showApplication" -> {
                    isApplicationVisible = newValue.toBoolean()
                }
                "gui.quitWarningShown" -> {
                    isQuitWarningShown = newValue.toBoolean()
                }
                pMessageDeliveryReceipt -> {
                    isSendMessageDeliveryReceipt = newValue.toBoolean()
                }
                pTypingNotification -> {
                    isSendChatStateNotifications = newValue.toBoolean()
                }
                pPresenceSubscribeAuto -> {
                    isPresenceSubscribeAuto = newValue.toBoolean()
                }
                "gui.isMoveContactConfirmationRequested" -> {
                    isMoveContactConfirmationRequested = newValue.toBoolean()
                }
                pMultiChatWindowEnabled -> {
                    isMultiChatWindowEnabled = newValue.toBoolean()
                }
                "gui.IS_PRIVATE_CHAT_IN_CHATROOM_DISABLED" -> {
                    isPrivateMessagingInChatRoomDisabled = newValue.toBoolean()
                }
                pLeaveChatRoomOnWindowClose -> {
                    isLeaveChatRoomOnWindowCloseEnabled = newValue.toBoolean()
                }
                pMessageHistoryShown -> {
                    isHistoryShown = newValue.toBoolean()
                }
                pChatHistorySize -> {
                    chatHistorySize = newValue.toInt()
                }
                pChatWriteAreaSize -> {
                    chatWriteAreaSize = newValue.toInt()
                }
                pTransparentWindowEnabled -> {
                    isTransparentWindowEnabled = newValue.toBoolean()
                }
                pWindowTransparency -> {
                    windowTransparency = newValue.toInt()
                }
                "gui.chat.ChatWindow.showStylebar" -> {
                    isChatStyleBarVisible = newValue.toBoolean()
                }
                "gui.chat.ChatWindow.showToolbar" -> {
                    isChatToolbarVisible = newValue.toBoolean()
                }
                "gui.call.lastCallConferenceProvider" -> {
                    lastCallConferenceProvider = findProviderFromAccountId(newValue)
                }
                pShowStatusChangedInChat -> {
                    isShowStatusChangedInChat = newValue.toBoolean()
                }
            }
        }
    }
}