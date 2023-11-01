/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.widget

import android.content.Context
import android.content.res.TypedArray
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.impl.neomedia.codec.video.AndroidDecoder
import org.atalk.impl.neomedia.codec.video.AndroidEncoder

/**
 * Class that handles common attributes and operations for all configuration widgets.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
internal class ConfigWidgetUtil : EditTextPreference.OnBindEditTextListener {
    /**
     * The parent `Preference` handled by this instance.
     */
    private val parent: Preference

    /**
     * Flag indicates whether configuration property should be stored in separate thread to prevent network on main
     * thread exceptions.
     */
    private var useNewThread = false

    /**
     * Flag indicates whether value should be mapped to the summary.
     */
    private var mapSummary = false
    private var mInputType = EditorInfo.TYPE_NULL

    /**
     * Creates new instance of `ConfigWidgetUtil` for given `parent` `Preference`.
     *
     * @param parent the `Preference` that will be handled by this instance.
     */
    constructor(parent: Preference) {
        this.parent = parent
    }

    /**
     * Creates new instance of `ConfigWidgetUtil` for given `parent` `Preference`.
     *
     * @param parent the `Preference` that will be handled by this instance.
     * @param mapSummary indicates whether value should be displayed as a summary
     */
    constructor(parent: Preference, mapSummary: Boolean) {
        this.parent = parent
        this.mapSummary = true
    }

    /**
     * PArses the attributes. Should be called by parent `Preference`.
     *
     * @param context the Android context
     * @param attrs the attribute set
     */
    fun parseAttributes(context: Context, attrs: AttributeSet?) {
        val attArray = context.obtainStyledAttributes(attrs, R.styleable.ConfigWidget)
        useNewThread = attArray.getBoolean(R.styleable.ConfigWidget_storeInNewThread, false)
        mapSummary = attArray.getBoolean(R.styleable.ConfigWidget_mapSummary, mapSummary)
    }

    /**
     * Updates the summary if necessary. Should be called by parent `Preference` on value initialization.
     *
     * @param value the current value
     */
    fun updateSummary(value: Any?) {
        if (mapSummary) {
            var text = value?.toString() ?: ""
            if (parent is EditTextPreference) {
                if (mInputType != EditorInfo.TYPE_NULL && mInputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                    text = text.replace("(?s).".toRegex(), "*")
                }
            }
            parent.summary = text
        }
    }

    override fun onBindEditText(editText: EditText) {
        mInputType = editText.getInputType()
    }

    /**
     * Persists new value through the `getConfigurationService`.
     *
     * @param value the new value to persist.
     */
    fun handlePersistValue(value: Any?) {
        updateSummary(value)
        val store = object : Thread() {
            override fun run() {
                val confService = AndroidGUIActivator.configurationService
                confService.setProperty(parent.key, value)
                if (parent.key == AndroidDecoder.HW_DECODING_ENABLE_PROPERTY) {
                    setSurfaceOption(AndroidDecoder.DIRECT_SURFACE_DECODE_PROPERTY, value)
                } else if (parent.key == AndroidEncoder.HW_ENCODING_ENABLE_PROPERTY) {
                    setSurfaceOption(AndroidEncoder.DIRECT_SURFACE_ENCODE_PROPERTY, value)
                }
            }
        }
        if (useNewThread) store.start() else store.start()
    }

    /**
     * Couple the codec surface enable option to the codec option state;
     * Current aTalk implementation requires surface option for android codec to be selected by fmj
     *
     * @param key surface preference key
     * @param value the value to persist.
     */
    private fun setSurfaceOption(key: String, value: Any?) {
        AndroidGUIActivator.configurationService.setProperty(key, value)
        val surfaceEnable = parent.preferenceManager.findPreference<ConfigCheckBox>(key)
        if (surfaceEnable != null) {
            surfaceEnable.isChecked = (value as Boolean?)!!
        }
    }
}