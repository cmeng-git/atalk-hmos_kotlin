/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import org.atalk.hmos.R
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.hmos.gui.settings.widget.ConfigCheckBox
import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.device.DeviceConfiguration
import org.atalk.impl.neomedia.device.DeviceSystem
import org.atalk.impl.neomedia.device.util.AndroidCamera
import org.atalk.service.osgi.OSGiPreferenceFragment

/**
 * The preferences fragment implements for Expert settings.
 *
 * @author Eng Chong Meng
 */
class ExpertSettingsFragment : OSGiPreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * The device configuration
     */
    private lateinit var mDeviceConfig: DeviceConfiguration
    private lateinit var mAudioSystem: AudioSystem
    private var mPreferenceScreen: PreferenceScreen? = null
    private lateinit var shPrefs: SharedPreferences

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private val summaryMapper = SummaryMapper()
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the expert_preferences from an XML resource
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.expert_preferences, rootKey)
        setPrefTitle(R.string.service_gui_settings_EXPERT)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        mPreferenceScreen = preferenceScreen
        shPrefs = preferenceManager.sharedPreferences!!
        shPrefs.registerOnSharedPreferenceChangeListener(this)
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)
        if (!MainMenuActivity.disableMediaServiceOnFault) {
            val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
            if (mediaServiceImpl != null) {
                mDeviceConfig = mediaServiceImpl.deviceConfiguration
                mAudioSystem = mDeviceConfig.audioSystem!!
            } else {
                // Do not proceed if mediaServiceImpl == null; else system crashes on NPE
                disableMediaOptions()
                return
            }
            // Audio section
            initAudioPreferences()

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

    /**
     * Initializes audio settings.
     */
    private fun initAudioPreferences() {
        val audioSystemFeatures = mAudioSystem.features

        // Echo cancellation
        val echoCancelPRef = findPreference<CheckBoxPreference>(P_KEY_AUDIO_ECHO_CANCEL)!!
        val hasEchoFeature = AudioSystem.FEATURE_ECHO_CANCELLATION and audioSystemFeatures != 0
        echoCancelPRef.isEnabled = hasEchoFeature
        echoCancelPRef.isChecked = hasEchoFeature && mAudioSystem.isEchoCancel

        // Automatic gain control
        val agcPRef = findPreference<CheckBoxPreference>(P_KEY_AUDIO_AGC)
        val hasAgcFeature = AudioSystem.FEATURE_AGC and audioSystemFeatures != 0
        agcPRef!!.isEnabled = hasAgcFeature
        agcPRef.isChecked = hasAgcFeature && mAudioSystem.isAutomaticGainControl

        // Denoise
        val denoisePref = findPreference<CheckBoxPreference>(P_KEY_AUDIO_DENOISE)!!
        val hasDenoiseFeature = AudioSystem.FEATURE_DENOISE and audioSystemFeatures != 0
        denoisePref.isEnabled = hasDenoiseFeature
        denoisePref.isChecked = hasDenoiseFeature && mAudioSystem.isDenoise
    }

    // Disable all media options when MediaServiceImpl is not initialized due to text-relocation in ffmpeg
    private fun disableMediaOptions() {
        // android OS cannot support removal of nested PreferenceCategory, so just disable all advance settings
        val myPrefCat = findPreference<PreferenceCategory>(PC_KEY_ADVANCED)
        if (myPrefCat != null) {
            mPreferenceScreen!!.removePreference(myPrefCat)

            // myPrefCat = (PreferenceCategory) findPreference(PC_KEY_VIDEO);
            // if (myPrefCat != null) {
            //     preferenceScreen.removePreference(myPrefCat);
            // }

            // myPrefCat = (PreferenceCategory) findPreference(PC_KEY_AUDIO);
            // if (myPrefCat != null) {
            //     preferenceScreen.removePreference(myPrefCat);
            // }
        }
    }

    /**
     * Initializes video preferences part.
     */
    private fun initVideoPreferences() {
        updateHwCodecStatus()

        // Frame rate
        val defaultFpsStr = "20"
        val limitFpsPref = findPreference<CheckBoxPreference>(P_KEY_VIDEO_LIMIT_FPS)!!
        val targetFps = mDeviceConfig.getFrameRate()
        limitFpsPref.isChecked = targetFps != -1
        val targetFpsPref = findPreference<EditTextPreference>(P_KEY_VIDEO_TARGET_FPS)
        targetFpsPref!!.text = if (targetFps != DeviceConfiguration.DEFAULT_VIDEO_FRAMERATE) targetFps.toString() else defaultFpsStr

        // Max bandwidth
        var videoMaxBandwith = mDeviceConfig.videoRTPPacingThreshold
        // Accord the current value with the maximum allowed value. Fixes existing
        // configurations that have been set to a number larger than the advised maximum value.
        videoMaxBandwith = videoMaxBandwith.coerceAtMost(999)
        val maxBWPref = findPreference<EditTextPreference>(P_KEY_VIDEO_MAX_BANDWIDTH)!!
        maxBWPref.text = videoMaxBandwith.toString()

        // Video bitrate
        val bitrate = mDeviceConfig.videoBitrate
        val bitratePref = findPreference<EditTextPreference>(P_KEY_VIDEO_BITRATE)
        bitratePref!!.text = bitrate.toString()

        // Summaries mapping
        summaryMapper.includePreference(targetFpsPref, defaultFpsStr)
        summaryMapper.includePreference(maxBWPref, DeviceConfiguration.DEFAULT_VIDEO_RTP_PACING_THRESHOLD.toString())
        summaryMapper.includePreference(bitratePref, DeviceConfiguration.DEFAULT_VIDEO_BITRATE.toString())
    }

    /**
     * Update the android codec preferences enabled status based on camera device selected option.
     *
     * Note: Current aTalk implementation requires direct surface option to be enabled in order
     * for fmj to use the android codec if enabled. So couple both the surface and codec options
     *
     * @see ConfigWidgetUtil.handlePersistValue
     */
    private fun updateHwCodecStatus() {
        val selectedCamera = AndroidCamera.selectedCameraDevInfo

        // MediaCodecs only work with AndroidCameraSystem(at least for now)
        val enableMediaCodecs = (selectedCamera != null
                && DeviceSystem.LOCATOR_PROTOCOL_ANDROIDCAMERA == selectedCamera.cameraProtocol)
        findPreference<CheckBoxPreference>(P_KEY_VIDEO_HW_ENCODE)!!.isEnabled = enableMediaCodecs
        findPreference<CheckBoxPreference>(P_KEY_VIDEO_HW_DECODE)!!.isEnabled = enableMediaCodecs

        // findPreference(P_KEY_VIDEO_ENC_DIRECT_SURFACE).setEnabled(AndroidUtils.hasAPI(18));
        findPreference<ConfigCheckBox>(P_KEY_VIDEO_ENC_DIRECT_SURFACE)!!.isEnabled = false
        findPreference<ConfigCheckBox>(P_KEY_VIDEO_DEC_DIRECT_SURFACE)!!.isEnabled = false
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
        // Echo cancellation
        if (key == P_KEY_AUDIO_ECHO_CANCEL) {
            mAudioSystem.isEchoCancel = shPreferences.getBoolean(P_KEY_AUDIO_ECHO_CANCEL, true)
        } else if (key == P_KEY_AUDIO_AGC) {
            mAudioSystem.isAutomaticGainControl = shPreferences.getBoolean(P_KEY_AUDIO_AGC, true)
        } else if (key == P_KEY_AUDIO_DENOISE) {
            mAudioSystem.isDenoise = shPreferences.getBoolean(P_KEY_AUDIO_DENOISE, true)
        } else if (key == P_KEY_VIDEO_LIMIT_FPS || key == P_KEY_VIDEO_TARGET_FPS) {
            val isLimitOn = shPreferences.getBoolean(P_KEY_VIDEO_LIMIT_FPS, false)
            if (isLimitOn) {
                val fpsPref = findPreference<EditTextPreference>(P_KEY_VIDEO_TARGET_FPS)
                val fpsStr = fpsPref!!.text
                if (!TextUtils.isEmpty(fpsStr)) {
                    var fps = fpsStr!!.toInt()
                    if (fps > 30) {
                        fps = 30
                    } else if (fps < 5) {
                        fps = 5
                    }
                    mDeviceConfig.setFrameRate(fps)
                    fpsPref.text = fps.toString()
                }
            } else {
                mDeviceConfig.setFrameRate(DeviceConfiguration.DEFAULT_VIDEO_FRAMERATE)
            }
        } else if (key == P_KEY_VIDEO_MAX_BANDWIDTH) {
            val resStr = shPreferences.getString(P_KEY_VIDEO_MAX_BANDWIDTH, null)
            if (resStr.isNullOrEmpty()) {
                var maxBw = resStr!!.toInt()
                if (maxBw > 999) {
                    maxBw = 999
                } else if (maxBw < 1) {
                    maxBw = 1
                }
                mDeviceConfig.videoRTPPacingThreshold = maxBw
            } else {
                mDeviceConfig.videoRTPPacingThreshold = DeviceConfiguration.DEFAULT_VIDEO_RTP_PACING_THRESHOLD
            }
            findPreference<EditTextPreference>(P_KEY_VIDEO_MAX_BANDWIDTH)!!.text = mDeviceConfig.videoRTPPacingThreshold.toString()
        } else if (key == P_KEY_VIDEO_BITRATE) {
            val bitrateStr = shPreferences.getString(P_KEY_VIDEO_BITRATE, "")
            var bitrate = 0
            if (bitrateStr != null) {
                bitrate = if (!TextUtils.isEmpty(bitrateStr)) bitrateStr.toInt() else DeviceConfiguration.DEFAULT_VIDEO_BITRATE
            }
            if (bitrate < 1) {
                bitrate = 1
            }
            mDeviceConfig.videoBitrate = bitrate
            (findPreference<EditTextPreference>(P_KEY_VIDEO_BITRATE) as EditTextPreference).text = bitrate.toString()
        }
    }

    companion object {
        // Advance video/audio settings
        private const val PC_KEY_ADVANCED = "pref.cat.settings.advanced"

        // private static final String PC_KEY_VIDEO = "pref.cat.settings.video";
        // private static final String PC_KEY_AUDIO = "pref.cat.settings.audio";
        // Audio settings
        private const val P_KEY_AUDIO_ECHO_CANCEL = "pref.key.audio.echo_cancel"
        private const val P_KEY_AUDIO_AGC = "pref.key.audio.agc"
        private const val P_KEY_AUDIO_DENOISE = "pref.key.audio.denoise"

        // Hardware encoding/decoding (>=API16)
        private const val P_KEY_VIDEO_HW_ENCODE = "neomedia.android.hw_encode"
        private const val P_KEY_VIDEO_HW_DECODE = "neomedia.android.hw_decode"

        // Direct surface encoding(hw encoding required and API18)
        private const val P_KEY_VIDEO_ENC_DIRECT_SURFACE = "neomedia.android.surface_encode"
        private const val P_KEY_VIDEO_DEC_DIRECT_SURFACE = "neomedia.android.surface_decode"

        // Video advanced settings
        private const val P_KEY_VIDEO_LIMIT_FPS = "pref.key.video.limit_fps"
        private const val P_KEY_VIDEO_TARGET_FPS = "pref.key.video.frame_rate"
        private const val P_KEY_VIDEO_MAX_BANDWIDTH = "pref.key.video.max_bandwidth"
        private const val P_KEY_VIDEO_BITRATE = "pref.key.video.bitrate"
    }
}