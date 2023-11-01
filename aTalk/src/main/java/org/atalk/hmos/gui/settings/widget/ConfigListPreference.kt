/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.widget

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.service.configuration.ConfigurationService

/**
 * List preference that stores its value through the `ConfigurationService`. It also supports
 * "disable dependents value" attribute.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ConfigListPreference : ListPreference {
    /**
     * The optional attribute which contains value that disables all dependents.
     */
    private var dependentValue: String? = null

    /**
     * Disables dependents when current value is different than `dependentValue`.
     */
    private var disableOnNotEqual = false

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttributes(context, attrs)
    }

    constructor(context: Context?) : super(context!!) {}

    /**
     * Parses attribute set.
     *
     * @param context Android context.
     * @param attrs attribute set.
     */
    private fun initAttributes(context: Context, attrs: AttributeSet?) {
        val attArray = context.obtainStyledAttributes(attrs, R.styleable.ConfigListPreference)
        for (i in 0 until attArray.indexCount) {
            val attrIdx = attArray.getIndex(i)
            when (attrIdx) {
                R.styleable.ConfigListPreference_disableDependentsValue -> dependentValue = attArray.getString(attrIdx)
                R.styleable.ConfigListPreference_disableOnNotEqualValue -> disableOnNotEqual = attArray.getBoolean(attrIdx, false)
            }
        }
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        // Force load default value from configuration service
        setDefaultValue(getPersistedString(null.toString()))
        super.onAttachedToHierarchy(preferenceManager)
    }

    /**
     * {@inheritDoc}
     * // Update summary every time the value is read
     */
    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        updateSummary(value)
    }

    /**
     * {@inheritDoc}
     */
    override fun getPersistedString(defaultReturnValue: String): String {
        val configService = AndroidGUIActivator.configurationService
                ?: return defaultReturnValue
        return configService.getString(key, defaultReturnValue)!!
    }

    /**
     * {@inheritDoc}
     */
    override fun persistString(value: String): Boolean {
        val persistString = super.persistString(value)
        val configService = AndroidGUIActivator.configurationService
                ?: return false

        // Update summary when the value has changed
        configService.setProperty(key, value)
        updateSummary(value)
        return true
    }

    /**
     * Updates the summary using entry corresponding to currently selected value.
     *
     * @param value the current value
     */
    private fun updateSummary(value: String) {
        val idx = findIndexOfValue(value)
        if (idx != -1) {
            summary = entries[idx]
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun setValue(value: String) {
        super.setValue(value)

        // Disables dependents
        notifyDependencyChange(shouldDisableDependents())
    }

    /**
     * {@inheritDoc}
     *
     * Additionally checks if current value is equal to disable dependents value.
     */
    override fun shouldDisableDependents(): Boolean {
        return super.shouldDisableDependents() || dependentValue != null && disableOnNotEqual != (dependentValue == value)
    }
}