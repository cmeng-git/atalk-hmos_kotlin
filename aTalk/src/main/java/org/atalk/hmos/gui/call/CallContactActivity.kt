/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.call

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import org.atalk.hmos.gui.aTalk
import org.atalk.service.osgi.OSGiActivity

/**
 * Tha `CallContactActivity` can be used to call contact. The phone number can be filled
 * from `Intent` data.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CallContactActivity : OSGiActivity() {
    private var ccFragment: CallContactFragment? = null

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     * then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // There's no need to create fragment if the Activity is being restored.
        if (savedInstanceState == null) {
            // Create new call contact fragment
            var phoneNumber: String? = null
            val intent = intent
            if (intent.dataString != null) phoneNumber = PhoneNumberUtils.getNumberFromIntent(intent, this)
            ccFragment = CallContactFragment.newInstance(phoneNumber)
            supportFragmentManager.beginTransaction().add(android.R.id.content, ccFragment!!).commit()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        // If request is canceled, the result arrays are empty.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == aTalk.PRC_GET_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission granted, so proceed
                ccFragment!!.initAndroidAccounts()
            }
        }
    }
}