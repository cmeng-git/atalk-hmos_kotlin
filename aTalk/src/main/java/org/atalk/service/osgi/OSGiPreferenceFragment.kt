/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi

import android.content.*
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import org.atalk.hmos.R
import org.osgi.framework.BundleContext

/**
 * Class can be used to build [androidx.preference.PreferenceFragmentCompat]s that require OSGI services access.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class OSGiPreferenceFragment : PreferenceFragmentCompat(), OSGiUiPart {
    private var osGiActivity: OSGiActivity? = null
    protected var osgiContext: BundleContext? = null
    private var viewCreated = false
    private var osgiNotified = false
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        viewCreated = true
        if (!osgiNotified && osgiContext != null) {
            onOSGiConnected()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onDestroyView() {
        viewCreated = false
        osgiNotified = false
        super.onDestroyView()
    }

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        osGiActivity = activity as OSGiActivity?
        if (osGiActivity != null) osGiActivity!!.registerOSGiFragment(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun onDetach() {
        osGiActivity!!.unregisterOSGiFragment(this)
        super.onDetach()
    }

    /**
     * Fired when OSGI is started and the `bundleContext` is available.
     *
     * @param bundleContext the OSGI bundle context.
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext?) {
        osgiContext = bundleContext
        if (viewCreated && !osgiNotified) {
            onOSGiConnected()
        }
    }

    /**
     * Method fired when OSGI context is attached, but after the `View` is created.
     */
    protected fun onOSGiConnected() {
        osgiNotified = true
    }

    /**
     * Fired when parent `OSGiActivity` is being stopped or this fragment is being detached.
     *
     * @param bundleContext the OSGI bundle context.
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext?) {
        osgiContext = null
    }

    /**
     * Set preference title using android inbuilt toolbar
     *
     * @param resId preference tile resourceID
     */
    fun setPrefTitle(resId: Int) {
        if (activity == null) return
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        if (actionBar != null) {
            actionBar.displayOptions = (ActionBar.DISPLAY_SHOW_HOME
                    or ActionBar.DISPLAY_USE_LOGO
                    or ActionBar.DISPLAY_SHOW_TITLE)
            actionBar.setLogo(R.drawable.ic_icon)
            actionBar.setTitle(resId)
        }
    }
}