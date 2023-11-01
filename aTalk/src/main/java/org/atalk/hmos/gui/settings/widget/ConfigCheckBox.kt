/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceManager
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.service.configuration.ConfigurationService

/**
 * Checkbox preference that persists the value through `ConfigurationService`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ConfigCheckBox : CheckBoxPreference {
    /**
     * `ConfigWidgetUtil` used by this instance.
     */
    private val configUtil = ConfigWidgetUtil(this)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        configUtil.parseAttributes(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        configUtil.parseAttributes(context, attrs)
    }

    constructor(context: Context?) : super(context!!) {}

    /**
     * {@inheritDoc}
     */
    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        configUtil.updateSummary(isChecked)
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        // Force load default value from configuration service
        setDefaultValue(getPersistedBoolean(false))
        super.onAttachedToHierarchy(preferenceManager)
    }

    /**
     * {@inheritDoc}
     */
    override fun getPersistedBoolean(defaultReturnValue: Boolean): Boolean {
        val configService = AndroidGUIActivator.configurationService
                ?: return defaultReturnValue
        return configService.getBoolean(key, defaultReturnValue)
    }

    /**
     * {@inheritDoc}
     */
    override fun persistBoolean(value: Boolean): Boolean {
        super.persistBoolean(value)
        // Sets boolean value in the ConfigurationService
        configUtil.handlePersistValue(value)
        return true
    }
}