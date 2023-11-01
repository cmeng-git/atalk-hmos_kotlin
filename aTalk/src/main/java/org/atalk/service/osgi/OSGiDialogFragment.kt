/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi

import android.content.*
import android.os.Looper
import androidx.fragment.app.DialogFragment
import org.osgi.framework.BundleContext

/**
 * Class can be used to build [DialogFragment]s that require OSGI services access.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class OSGiDialogFragment : DialogFragment(), OSGiUiPart {
    private var osGiActivity: OSGiActivity? = null

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
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext?) {
    }

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext?) {
    }

    /**
     * Convenience method for running code on UI thread looper(instead of getActivity().runOnUIThread()). It is never
     * guaranteed that `getActivity()` will return not `null` value, hence it must be checked in the
     * `action`.
     *
     * @param action `Runnable` action to execute on UI thread.
     */
    protected fun runOnUiThread(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
            return
        }
        // Post action to the ui looper
        OSGiActivity.uiHandler.post(action)
    }
}