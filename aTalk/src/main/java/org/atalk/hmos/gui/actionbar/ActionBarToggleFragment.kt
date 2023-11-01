/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.actionbar

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.service.osgi.OSGiFragment

/**
 * Fragment adds a toggle button to the action bar with text description to the right of it.
 * Button is handled through the `ActionBarToggleModel` which must be implemented by
 * parent `Activity`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ActionBarToggleFragment : OSGiFragment() {
    /**
     * Button model
     */
    private var model: ActionBarToggleModel? = null

    /**
     * Menu instance used to update the button
     */
    private var mToggleCB: CompoundButton? = null

    /**
     * Creates new instance of `ActionBarToggleFragment`
     */
    init {
        setHasOptionsMenu(true)
    }

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        model = context as ActionBarToggleModel
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.actionbar_toggle_menu, menu)

        // Binds the button
        mToggleCB = menu.findItem(R.id.toggleView).actionView!!.findViewById(android.R.id.toggle)
        mToggleCB!!.setOnCheckedChangeListener { cb: CompoundButton?, checked: Boolean -> model!!.isChecked = checked }
        // Set label text
        (menu.findItem(R.id.toggleView).actionView!!.findViewById<View>(android.R.id.text1) as TextView).text = arguments!!.getString(ARG_LABEL_TEXT)
        updateChecked()
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        updateChecked()
    }

    /**
     * {@inheritDoc}
     */
    private fun updateChecked() {
        if (mToggleCB != null) {
            mToggleCB!!.isChecked = model!!.isChecked
        }
    }

    /**
     * Toggle button's model that has to be implemented by parent `Activity`.
     */
    interface ActionBarToggleModel {
        /**
         * Return `true` if button's model is currently in checked state.
         *
         * @return `true` if button's model is currently in checked state.
         */
        var isChecked: Boolean

    }

    companion object {
        /**
         * Text description's argument key
         */
        private const val ARG_LABEL_TEXT = "text"

        /**
         * Creates new instance of `ActionBarToggleFragment` with given description(can be
         * empty but not `null`).
         *
         * @param labelText toggle button's description(can be empty, but not `null`).
         * @return new instance of `ActionBarToggleFragment` parametrized with description argument.
         */
        fun newInstance(labelText: String?): ActionBarToggleFragment {
            val fragment = ActionBarToggleFragment()
            val args = Bundle()
            args.putString(ARG_LABEL_TEXT, labelText)
            fragment.setArguments(args)
            return fragment
        }
    }
}