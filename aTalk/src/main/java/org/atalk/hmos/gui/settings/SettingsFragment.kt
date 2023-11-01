/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import net.java.sip.communicator.service.systray.PopupMessageHandler
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.UtilActivator
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.hmos.gui.util.LocaleHelper
import org.atalk.hmos.gui.util.PreferenceUtil
import org.atalk.hmos.gui.util.ThemeHelper
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.impl.neomedia.device.AndroidCameraSystem
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.impl.neomedia.device.util.CameraUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.osgi.OSGiPreferenceFragment
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.awt.Dimension
import javax.media.MediaLocator

/**
 * The preferences fragment implements aTalk settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
class SettingsFragment : OSGiPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * The device configuration
     */
    private lateinit var mDeviceConfig: DeviceConfiguration
    private lateinit var mPreferenceScreen: PreferenceScreen
    private lateinit var shPrefs: SharedPreferences
    private lateinit var activity: AppCompatActivity

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private val summaryMapper = SummaryMapper()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = context as AppCompatActivity
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the preferences from an XML resource
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        setPrefTitle(R.string.system_settings)

        // FFR: v2.1.5 NPE; use UtilActivator instead of AndroidGUIActivator which was initialized much later
        mConfigService = UtilActivator.configurationService!!
        mPreferenceScreen = preferenceScreen
        shPrefs = preferenceManager.sharedPreferences!!
        shPrefs.registerOnSharedPreferenceChangeListener(this)
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)

        // init display locale and theme (not implemented)
        initLocale()
        initTheme()
        initWebPagePreference()

        // Messages section
        initMessagesPreferences()

        // Notifications section
        initNotificationPreferences()
        initAutoStart()
        if (!MainMenuActivity.disableMediaServiceOnFault) {
            val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
            mDeviceConfig = if (mediaServiceImpl != null) {
                mediaServiceImpl.deviceConfiguration
            } else {
                // Do not proceed if mediaServiceImpl == null; else system crashes on NPE
                disableMediaOptions()
                return
            }

            // Call section
            initCallPreferences()

            // Video section
            initVideoPreferences()
        } else {
            disableMediaOptions()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onStop() {
        shPrefs.unregisterOnSharedPreferenceChangeListener(this)
        shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper)
        super.onStop()
    }

    private fun initAutoStart() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            ConfigurationUtils.setAutoStart(false)
            findPreference<CheckBoxPreference>(P_KEY_AUTO_START)!!.isEnabled = false
        }
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_AUTO_START,
                ConfigurationUtils.isAutoStartEnable)
    }

    /**
     * Initialize web default access page
     */
    private fun initWebPagePreference() {
        // Updates displayed history size summary.
        val webPagePref = findPreference<EditTextPreference>(P_KEY_WEB_PAGE)!!
        webPagePref.text = ConfigurationUtils.webPage
        updateWebPageSummary()
    }

    private fun updateWebPageSummary() {
        val webPagePref = findPreference<EditTextPreference>(P_KEY_WEB_PAGE)!!
        webPagePref.summary = ConfigurationUtils.webPage
    }

    /**
     * Initialize interface Locale
     */
    private fun initLocale() {
        // Immutable empty {@link CharSequence} array
        val EMPTY_CHAR_SEQUENCE_ARRAY = arrayOfNulls<CharSequence>(0)
        val pLocale = findPreference<ListPreference>(P_KEY_LOCALE)
        val entryVector = ArrayList(listOf(*pLocale!!.entries))
        val entryValueVector = ArrayList(listOf(*pLocale.entryValues))
        val supportedLanguages = resources.getStringArray(R.array.supported_languages)
        val supportedLanguageSet = HashSet(listOf(*supportedLanguages))
        for (i in entryVector.size - 1 downTo -1 + 1) {
            if (!supportedLanguageSet.contains(entryValueVector[i].toString())) {
                entryVector.removeAt(i)
                entryValueVector.removeAt(i)
            }
        }
        val entries = entryVector.toArray<CharSequence>(EMPTY_CHAR_SEQUENCE_ARRAY)
        val entryValues = entryValueVector.toArray<CharSequence>(EMPTY_CHAR_SEQUENCE_ARRAY)
        val language = LocaleHelper.language
        pLocale.entries = entries
        pLocale.entryValues = entryValues
        pLocale.value = language
        pLocale.summary = pLocale.entry

        // summaryMapper not working for Locale, so use this instead
        pLocale.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference?, value: Any ->
            val language1 = value.toString()
            pLocale.value = language1
            pLocale.summary = pLocale.entry

            // Save selected language in DB
            mConfigService.setProperty(P_KEY_LOCALE, language1)

            // Need to destroy and restart to set new language if there is a change
            if (language != value) {
                // All language setting changes must call via aTalkApp so its contextWrapper is updated
                LocaleHelper.setLocale(activity, language1)

                // must get aTalk to restart onResume to show correct UI for preference menu
                aTalk.setPrefChange(aTalk.Locale_Change)

                // do destroy activity last
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                activity.finish()
            }
            true
        }
    }

    /**
     * Initialize interface Theme
     */
    protected fun initTheme() {
        val pTheme = findPreference<ListPreference>(P_KEY_THEME)!!
        val nTheme = if (ThemeHelper.isAppTheme(ThemeHelper.Theme.LIGHT)) "light" else "dark"
        pTheme.value = nTheme
        pTheme.summary = pTheme.entry

        // summaryMapper not working for Theme. so use this instead
        pTheme.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference?, value: Any ->
            pTheme.value = value as String
            pTheme.summary = pTheme.entry

            // Save Display Theme to DB
            val vTheme = if (value == "light") ThemeHelper.Theme.LIGHT else ThemeHelper.Theme.DARK
            mConfigService.setProperty(P_KEY_THEME, vTheme.ordinal)

            // Need to destroy and restart to set new Theme if there is a change
            if (nTheme != value) {
                ThemeHelper.setTheme(activity, vTheme)

                // must get aTalk to restart onResume to show new Theme
                aTalk.setPrefChange(aTalk.Theme_Change)
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                activity.finish()
            }
            true
        }
    }

    /**
     * Initializes messages section
     */
    private fun initMessagesPreferences() {
        // mhs may be null if user access settings before the mhs service is properly setup
        val mhs = MessageHistoryActivator.messageHistoryService
        val isHistoryLoggingEnabled = mhs.isHistoryLoggingEnabled
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_LOG_CHAT_HISTORY, isHistoryLoggingEnabled)
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_SHOW_HISTORY, ConfigurationUtils.isHistoryShown())

        // Updates displayed history size summary.
        val historySizePref = findPreference<EditTextPreference>(P_KEY_HISTORY_SIZE)!!
        historySizePref.text = ConfigurationUtils.getChatHistorySize().toString()
        updateHistorySizeSummary()
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_MESSAGE_DELIVERY_RECEIPT,
                ConfigurationUtils.isSendMessageDeliveryReceipt())
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_CHAT_STATE_NOTIFICATIONS,
                ConfigurationUtils.isSendChatStateNotifications())
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_XFER_THUMBNAIL_PREVIEW,
                ConfigurationUtils.isSendThumbnail())
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_PRESENCE_SUBSCRIBE_MODE,
                ConfigurationUtils.isPresenceSubscribeAuto())
        initAutoAcceptFileSize()
        // GeoPreferenceUtil.setCheckboxVal(this, P_KEY_CHAT_ALERTS, ConfigurationUtils.isAlerterEnabled());
    }

    /**
     * Updates displayed history size summary.
     */
    private fun updateHistorySizeSummary() {
        val historySizePref = findPreference<EditTextPreference>(P_KEY_HISTORY_SIZE)!!
        historySizePref.summary = getString(R.string.service_gui_settings_CHAT_HISTORY_SUMMARY,
                ConfigurationUtils.getChatHistorySize())
    }

    /**
     * Initialize auto accept file size
     */
    private fun initAutoAcceptFileSize() {
        val fileSizeList = findPreference<ListPreference>(P_KEY_AUTO_ACCEPT_FILE)!!
        fileSizeList.setEntries(R.array.filesizes)
        fileSizeList.setEntryValues(R.array.filesizes_values)
        val filesSize = ConfigurationUtils.autoAcceptFileSize
        fileSizeList.value = filesSize.toString()
        fileSizeList.summary = fileSizeList.entry

        // summaryMapper not working for auto accept fileSize so use this instead
        fileSizeList.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference: Preference?, value: Any ->
            val fileSize = value.toString()
            fileSizeList.value = fileSize
            fileSizeList.summary = fileSizeList.entry
            ConfigurationUtils.setAutoAcceptFileSizeSize(fileSize.toInt())
            true
        }
    }

    /**
     * Initializes notifications section
     */
    private fun initNotificationPreferences() {
        // Remove for android play store release
        // GeoPreferenceUtil.setCheckboxVal(preferenceScreen, P_KEY_AUTO_UPDATE_CHECK_ENABLE,
        //		cfg.getBoolean(AUTO_UPDATE_CHECK_ENABLE, true));
        val bc = AndroidGUIActivator.bundleContext!!
        val handlerRefs = ServiceUtils.getServiceReferences(bc, PopupMessageHandler::class.java)
        val names = arrayOfNulls<String>(handlerRefs.size + 1) // +1 Auto
        val values = arrayOfNulls<String>(handlerRefs.size + 1)
        names[0] = getString(R.string.impl_popup_auto)
        values[0] = "Auto"
        var selectedIdx = 0 // Auto by default

        // mCongService may be null feedback NPE from the field report, so just assume null i.e.
        // "Auto" selected. Delete the user's preference and select the best available handler.
        val configuredHandler = mConfigService.getString("systray.POPUP_HANDLER")
        val idx = 1
        for (ref in handlerRefs) {
            val handler = bc.getService(ref as ServiceReference<PopupMessageHandler>)
            names[idx] = handler.toString()
            values[idx] = handler.javaClass.name
            if (configuredHandler != null && configuredHandler == handler.javaClass.name) {
                selectedIdx = idx
            }
        }

        // Configures ListPreference
        val handlerList = findPreference<ListPreference>(P_KEY_POPUP_HANDLER)!!
        handlerList.entries = names
        handlerList.entryValues = values
        handlerList.setValueIndex(selectedIdx)
        // Summaries mapping
        summaryMapper.includePreference(handlerList, "Auto")
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_HEADS_UP_ENABLE,
                ConfigurationUtils.isHeadsUpEnable)
    }

    // Disable all media options when MediaServiceImpl is not initialized due to text-relocation in ffmpeg
    private fun disableMediaOptions() {
        var myPrefCat = findPreference<PreferenceCategory>(P_KEY_MEDIA_CALL)
        if (myPrefCat != null) mPreferenceScreen.removePreference(myPrefCat)
        myPrefCat = findPreference(P_KEY_CALL)
        if (myPrefCat != null) mPreferenceScreen.removePreference(myPrefCat)

        // android OS cannot support removal of nested PreferenceCategory, so just disable all advance settings
        myPrefCat = findPreference(P_KEY_ADVANCED)
        if (myPrefCat != null) {
            mPreferenceScreen.removePreference(myPrefCat)
        }
    }

    /**
     * Initializes call section
     */
    private fun initCallPreferences() {
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_NORMALIZE_PNUMBER,
                ConfigurationUtils.isNormalizePhoneNumber())
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_ACCEPT_ALPHA_PNUMBERS,
                ConfigurationUtils.acceptPhoneNumberWithAlphaChars())
    }

    /**
     * Initializes video preferences part.
     */
    private fun initVideoPreferences() {
        val cameras = AndroidCamera.cameras
        val names = arrayOfNulls<String>(cameras.size)
        val values = arrayOfNulls<String>(cameras.size)
        for (i in cameras.indices) {
            names[i] = cameras[i]!!.name
            values[i] = cameras[i]!!.locator.toString()
        }
        val cameraList = findPreference<ListPreference>(P_KEY_VIDEO_CAMERA)!!
        cameraList.entries = names
        cameraList.entryValues = values

        // Get camera from configuration
        val currentCamera = AndroidCamera.selectedCameraDevInfo
        if (currentCamera != null) cameraList.value = currentCamera.locator.toString()

        // Resolutions
        val resolutionSize = CameraUtils.PREFERRED_SIZES.size
        val resolutionValues = arrayOfNulls<String>(resolutionSize)
        for (i in 0 until resolutionSize) {
            resolutionValues[i] = resToStr(CameraUtils.PREFERRED_SIZES[i])
        }
        val resList = findPreference<ListPreference>(P_KEY_VIDEO_RES)!!
        resList.entries = resolutionValues
        resList.entryValues = resolutionValues

        // Init current resolution
        resList.value = resToStr(mDeviceConfig.getVideoSize())

        // Summaries mapping
        summaryMapper.includePreference(cameraList, getString(R.string.service_gui_settings_NO_CAMERA))
        summaryMapper.includePreference(resList, "720x480")
    }

    /**
     * Retrieves currently registered `PopupMessageHandler` for given `clazz` name.
     *
     * @param clazz the class name of `PopupMessageHandler` implementation.
     *
     * @return implementation of `PopupMessageHandler` for given class name registered in OSGI context.
     */
    private fun getHandlerForClassName(clazz: String): PopupMessageHandler? {
        val bc = AndroidGUIActivator.bundleContext!!
        val handlerRefs = ServiceUtils.getServiceReferences(bc, PopupMessageHandler::class.java)
        for (sRef in handlerRefs) {
            val handler = bc.getService(sRef as ServiceReference<PopupMessageHandler>)
            if (handler.javaClass.name == clazz) return handler
        }
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
        when (key) {
            P_KEY_LOG_CHAT_HISTORY -> {
                val mhs = MessageHistoryActivator.messageHistoryService
                var enable = false
                if (mhs != null) {
                    enable = shPreferences.getBoolean(P_KEY_LOG_CHAT_HISTORY, mhs.isHistoryLoggingEnabled)
                    mhs.isHistoryLoggingEnabled = enable
                }
                enableMam(enable)
            }
            P_KEY_SHOW_HISTORY -> ConfigurationUtils.setHistoryShown(shPreferences.getBoolean(P_KEY_SHOW_HISTORY, ConfigurationUtils.isHistoryShown()))
            P_KEY_HISTORY_SIZE -> {
                val intStr = shPreferences.getString(P_KEY_HISTORY_SIZE, ConfigurationUtils.getChatHistorySize().toString())!!
                ConfigurationUtils.setChatHistorySize(intStr.toInt())
                updateHistorySizeSummary()
            }
            P_KEY_WEB_PAGE -> {
                val wpStr = shPreferences.getString(P_KEY_WEB_PAGE, ConfigurationUtils.webPage)!!
                ConfigurationUtils.webPage = wpStr
                updateWebPageSummary()
            }
            P_KEY_AUTO_START -> ConfigurationUtils.setAutoStart(shPreferences.getBoolean(
                    P_KEY_AUTO_START, ConfigurationUtils.isAutoStartEnable))
            P_KEY_MESSAGE_DELIVERY_RECEIPT -> ConfigurationUtils.setSendMessageDeliveryReceipt(shPreferences.getBoolean(
                    P_KEY_MESSAGE_DELIVERY_RECEIPT, ConfigurationUtils.isSendMessageDeliveryReceipt()))
            P_KEY_CHAT_STATE_NOTIFICATIONS -> ConfigurationUtils.setSendChatStateNotifications(shPreferences.getBoolean(
                    P_KEY_CHAT_STATE_NOTIFICATIONS, ConfigurationUtils.isSendChatStateNotifications()))
            P_KEY_XFER_THUMBNAIL_PREVIEW -> ConfigurationUtils.setSendThumbnail(shPreferences.getBoolean(
                    P_KEY_XFER_THUMBNAIL_PREVIEW, ConfigurationUtils.isSendThumbnail()))
            P_KEY_PRESENCE_SUBSCRIBE_MODE -> ConfigurationUtils.setPresenceSubscribeAuto(shPreferences.getBoolean(
                    P_KEY_PRESENCE_SUBSCRIBE_MODE, ConfigurationUtils.isPresenceSubscribeAuto()))
            P_KEY_POPUP_HANDLER -> {
                val handler = shPreferences.getString(P_KEY_POPUP_HANDLER, "Auto")
                val systray = AndroidGUIActivator.systrayService!!
                if ("Auto" == handler) {
                    // "Auto" selected. Delete the user's preference and select the best available handler.
                    ConfigurationUtils.setPopupHandlerConfig(null)
                    systray.selectBestPopupMessageHandler()
                } else {
                    ConfigurationUtils.setPopupHandlerConfig(handler)
                    val handlerInstance = getHandlerForClassName(handler!!)
                    if (handlerInstance == null) {
                        Timber.w("No handler found for name: %s", handler)
                    } else {
                        systray.setActivePopupMessageHandler(handlerInstance)
                    }
                }
            }
            P_KEY_HEADS_UP_ENABLE -> ConfigurationUtils.setHeadsUp(shPreferences.getBoolean(P_KEY_HEADS_UP_ENABLE, true))
            P_KEY_NORMALIZE_PNUMBER -> ConfigurationUtils.setNormalizePhoneNumber(shPreferences.getBoolean(P_KEY_NORMALIZE_PNUMBER, true))
            P_KEY_VIDEO_CAMERA -> {
                val cameraName = shPreferences.getString(P_KEY_VIDEO_CAMERA, null)
                AndroidCamera.setSelectedCamera(MediaLocator(cameraName))
            }
            P_KEY_VIDEO_RES -> {
                val resStr = shPreferences.getString(P_KEY_VIDEO_RES, null)
                val videoRes = resolutionForStr(resStr)
                mDeviceConfig.setVideoSize(videoRes)
            }
        }
    }

    /**
     * Enable or disable MAM service according per the P_KEY_LOG_CHAT_HISTORY new setting.
     *
     * @param enable mam state to be updated with.
     */
    private fun enableMam(enable: Boolean) {
        val providers = AccountUtils.registeredProviders
        for (pps in providers) {
            if (pps.isRegistered) {
                ProtocolProviderServiceJabberImpl.enableMam(pps.connection, enable)
            } else {
                aTalkApp.showToastMessage(R.string.service_gui_settings_HISTORY_WARNING, pps.accountID.bareJid)
            }
        }
    }

    companion object {
        // PreferenceScreen and PreferenceCategories
        private const val P_KEY_MEDIA_CALL = "pref.cat.settings.media_call"
        private const val P_KEY_CALL = "pref.cat.settings.call"

        // Advance video/audio & Provisioning preference settings
        private const val P_KEY_ADVANCED = "pref.cat.settings.advanced"

        // Interface Display settings
        const val P_KEY_LOCALE = "pref.key.locale"
        const val P_KEY_THEME = "pref.key.theme"
        private const val P_KEY_WEB_PAGE = "gui.WEB_PAGE_ACCESS"

        // Message section
        private const val P_KEY_AUTO_START = "org.atalk.hmos.auto_start"
        private const val P_KEY_LOG_CHAT_HISTORY = "pref.key.msg.history_logging"
        private const val P_KEY_SHOW_HISTORY = "pref.key.msg.show_history"
        private const val P_KEY_HISTORY_SIZE = "pref.key.msg.chat_history_size"
        private const val P_KEY_MESSAGE_DELIVERY_RECEIPT = "pref.key.message_delivery_receipt"
        private const val P_KEY_CHAT_STATE_NOTIFICATIONS = "pref.key.msg.chat_state_notifications"
        private const val P_KEY_XFER_THUMBNAIL_PREVIEW = "pref.key.send_thumbnail"
        private const val P_KEY_AUTO_ACCEPT_FILE = "pref.key.auto_accept_file"
        private const val P_KEY_PRESENCE_SUBSCRIBE_MODE = "pref.key.presence_subscribe_mode"

        // Notifications
        private const val P_KEY_POPUP_HANDLER = "pref.key.notification.popup_handler"
        const val P_KEY_HEADS_UP_ENABLE = "pref.key.notification.heads_up_enable"

        // Call section
        private const val P_KEY_NORMALIZE_PNUMBER = "pref.key.call.remove.special"
        private const val P_KEY_ACCEPT_ALPHA_PNUMBERS = "pref.key.call.convert.letters"

        // Video settings
        private const val P_KEY_VIDEO_CAMERA = "pref.key.video.camera"

        // Video resolutions
        private const val P_KEY_VIDEO_RES = "pref.key.video.resolution"

        // User option property names
        const val AUTO_UPDATE_CHECK_ENABLE = "user.AUTO_UPDATE_CHECK_ENABLE"
        private lateinit var mConfigService: ConfigurationService

        /**
         * Converts resolution to string.
         *
         * @param d resolution as `Dimension`
         *
         * @return resolution string.
         */
        private fun resToStr(d: Dimension): String {
            return d.getWidth().toInt().toString() + "x" + d.getHeight().toInt()
        }

        /**
         * Selects resolution from supported resolutions list for given string.
         *
         * @param resStr resolution string created with method [.resToStr].
         *
         * @return resolution `Dimension` for given string representation created with method
         * [.resToStr]
         */
        private fun resolutionForStr(resStr: String?): Dimension {
            val resolutions = AndroidCameraSystem.SUPPORTED_SIZES
            for (resolution in resolutions) {
                if (resToStr(resolution!!) == resStr) return resolution
            }
            // "Auto" string won't match the defined resolution strings so will return default for auto
            return Dimension(DeviceConfiguration.DEFAULT_VIDEO_WIDTH, DeviceConfiguration.DEFAULT_VIDEO_HEIGHT)
        }
    }
}