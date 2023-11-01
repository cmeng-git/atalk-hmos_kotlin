/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.service.configuration.ConfigurationService

/**
 * Edit text preference which persists it's value through the `ConfigurationService`. Current value is reflected
 * in the summary. It also supports minimum and maximum value limits of integer or float type.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ConfigEditText : EditTextPreference, Preference.OnPreferenceChangeListener {
    /**
     * Integer upper bound for accepted value
     */
    private var intMax: Int? = null

    /**
     * Integer lower limit for accepted value
     */
    private var intMin: Int? = null

    /**
     * Float upper bound for accepted value
     */
    private var floatMin: Float? = null

    /**
     * Float lower limit for accepted value
     */
    private var floatMax: Float? = null

    /**
     * `ConfigWidgetUtil` used by this instance
     */
    private val configUtil = ConfigWidgetUtil(this, true)

    /**
     * Flag indicates if this edit text field is editable.
     */
    private var editable = true

    /**
     * Flag indicates if we want to allow empty values to go thought the value range check.
     */
    private var allowEmpty = true

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        initAttributes(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttributes(context, attrs)
    }

    constructor(context: Context?) : super(context!!) {}

    /**
     * Parses attributes array.
     *
     * @param context the Android context.
     * @param attrs attributes set.
     */
    private fun initAttributes(context: Context, attrs: AttributeSet?) {
        val attArray = context.obtainStyledAttributes(attrs, R.styleable.ConfigEditText)
        for (i in 0 until attArray.indexCount) {
            when (val attribute = attArray.getIndex(i)) {
                R.styleable.ConfigEditText_intMax -> intMax = attArray.getInt(attribute, -1)
                R.styleable.ConfigEditText_intMin -> intMin = attArray.getInt(attribute, -1)
                R.styleable.ConfigEditText_floatMax -> floatMax = attArray.getFloat(attribute, -1f)
                R.styleable.ConfigEditText_floatMin -> floatMin = attArray.getFloat(attribute, -1f)
                R.styleable.ConfigEditText_editable -> editable = attArray.getBoolean(attribute, true)
                R.styleable.ConfigEditText_allowEmpty -> allowEmpty = attArray.getBoolean(attribute, true)
            }
        }
        // Register listener to perform checks before new value is accepted
        onPreferenceChangeListener = this
        configUtil.parseAttributes(context, attrs)
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        // Force load default value from configuration service
        setDefaultValue(getPersistedString(null))
        super.onAttachedToHierarchy(preferenceManager)
    }

    /**
     * {@inheritDoc}
     * // Set summary on init
     */
    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        configUtil.updateSummary(text)
    }

    /**
     * {@inheritDoc}
     */
    override fun getPersistedString(defaultReturnValue: String?): String? {
        val configService = AndroidGUIActivator.configurationService
                ?: return defaultReturnValue
        return configService.getString(key, defaultReturnValue)!!
    }

    /**
     * {@inheritDoc}
     */
    override fun persistString(value: String): Boolean {
        super.persistString(value)
        configUtil.handlePersistValue(value)
        return true
    }

    /**
     * {@inheritDoc}
     *
     * Performs value range checks before the value is accepted.
     */
    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (allowEmpty && StringUtils.isEmpty(newValue as String)) {
            return true
        }
        if (intMax != null && intMin != null) {
            // Integer range check
            return try {
                val newInt = (newValue as String).toInt()
                intMin!! <= newInt && newInt <= intMax!!
            } catch (e: NumberFormatException) {
                false
            }
        } else if (floatMin != null && floatMax != null) {
            // Float range check
            return try {
                val newFloat = (newValue as String).toFloat()
                floatMin!! <= newFloat && newFloat <= floatMax!!
            } catch (e: NumberFormatException) {
                false
            }
        }
        // No checks by default
        return true
    }

    /**
     * {@inheritDoc}
     */
    override fun onClick() {
        if (editable) super.onClick()
    }
}