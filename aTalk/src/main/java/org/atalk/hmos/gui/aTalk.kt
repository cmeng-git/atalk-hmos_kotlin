/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import de.cketti.library.changelog.ChangeLog
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.actionbar.ActionBarStatusFragment
import org.atalk.hmos.gui.call.CallHistoryFragment
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.chat.chatsession.ChatSessionFragment
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.util.DepthPageTransformer
import org.atalk.hmos.gui.util.EntityListHelper
import org.atalk.hmos.gui.webview.WebViewFragment
import timber.log.Timber

/**
 * The main `Activity` for aTalk application with pager slider for both contact and chatRoom list windows.
 *
 * @author Eng Chong Meng
 */
class aTalk : MainMenuActivity(), EntityListHelper.TaskCompleted {
    /**
     * The main pager view fragment containing the contact List
     */
    lateinit var contactListFragment: ContactListFragment

    /**
     * Variable caches instance state stored for example on rotate event to prevent from
     * recreating the contact list after rotation. It is passed as second argument of
     * [.handleIntent] when called from [.onNewIntent].
     */
    private var mInstanceState: Bundle? = null

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private lateinit var mPager: ViewPager

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Checks if OSGi has been started and if not starts LauncherActivity which will restore this Activity from its Intent.
        if (postRestoreIntent()) {
            return
        }
        setContentView(R.layout.main_view)
        if (savedInstanceState == null) {
            // Inserts ActionBar functionality
            supportFragmentManager.beginTransaction().add(ActionBarStatusFragment(), "action_bar").commit()
        }

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = findViewById(R.id.mainViewPager)
        // The pager adapter, which provides the pages to the view pager widget.
        mFragmentManager = supportFragmentManager
        val mPagerAdapter = MainPagerAdapter(mFragmentManager)
        mPager.adapter = mPagerAdapter
        mPager.setPageTransformer(true, DepthPageTransformer())
        handleIntent(intent, savedInstanceState)

        // allow 15 seconds for first launch login to complete before showing history log if the activity is still active
        val cl = ChangeLog(this)
        if (cl.isFirstRun) {
            runOnUiThread {
                Handler().postDelayed({
                    if (!isFinishing) {
                        cl.logDialog.show()
                    }
                }, 15000)
            }
        }
    }

    /**
     * Called when new `Intent` is received(this `Activity` is launched in `singleTask` mode.
     *
     * @param intent new `Intent` data.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, mInstanceState)
    }

    /**
     * Decides what should be displayed based on supplied `Intent` and instance state.
     *
     * @param intent `Activity` `Intent`.
     * @param instanceState `Activity` instance state.
     */
    private fun handleIntent(intent: Intent, instanceState: Bundle?) {
        val add = mInstances.add(/* element = */ this)
        val action = intent.action
        if (Intent.ACTION_SEARCH == action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            Timber.w("Search intent not handled for query: %s", query)
        }
        // Start aTalk with contactList UI for IM setup
        if (Intent.ACTION_SENDTO == action) {
            mPager.currentItem = 0
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mInstanceState = savedInstanceState
    }

    override fun onPause() {
        super.onPause()
        mInstanceState = null
    }

    override fun onResume() {
        super.onResume()
        /*
         * Need to restart whole app to make aTalkApp Locale change working
         * Note: Start aTalk Activity does not apply to aTalkApp Application class.
         */
        if (mPrefChange >= Locale_Change) {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            // ProcessPhoenix.triggerRebirth(this, intent);
            val componentName = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        } else if (mPrefChange == Theme_Change) {
            mPrefChange = 0
            finish()
            startActivity(aTalk::class.java)
        }
    }

    /*
     * If the user is currently looking at the first page, allow the system to handle the
     * Back button. If Telephony fragment is shown, backKey closes the fragment only.
     * The call finish() on this activity and pops the back stack.
     */
    override fun onBackPressed() {
        if (mPager.currentItem == 0) {
            // mTelephony is not null if Telephony is closed by Cancel button.
            if (mTelephony != null) {
                if (!mTelephony!!.closeFragment()) {
                    super.onBackPressed()
                }
                mTelephony = null
            } else {
                super.onBackPressed()
            }
        } else {
            // Otherwise, select the previous page.
            mPager.currentItem = mPager.currentItem - 1
        }
    }

    /**
     * Called when an activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        synchronized(this) {
            val bundleContext = bundleContext
            if (bundleContext != null) {
                try {
                    stop(bundleContext)
                } catch (t: Throwable) {
                    Timber.e(t, "Error stopping application:%s", t.localizedMessage)
                    if (t is ThreadDeath) throw t
                }
            }
        }
    }

    /**
     * Handler for contactListFragment chatSessions on completed execution of
     *
     * @see EntityListHelper.eraseEntityChatHistory
     * @see EntityListHelper.eraseAllEntityHistory
     */
    override fun onTaskComplete(result: Int, deletedUUIDs: List<String>?) {
        if (result == EntityListHelper.CURRENT_ENTITY) {
            val clickedContact = contactListFragment.getClickedContact()
            val clickedChat = ChatSessionManager.getActiveChat(clickedContact)
            if (clickedChat != null) {
                contactListFragment.onCloseChat(clickedChat)
            }
        } else if (result == EntityListHelper.ALL_ENTITIES) {
            contactListFragment.onCloseAllChats()
        } else { // failed
            val errMsg = getString(R.string.service_gui_HISTORY_REMOVE_ERROR,
                    contactListFragment.getClickedContact()!!.getDisplayName())
            aTalkApp.showToastMessage(errMsg)
        }
    }

    /**
     * A simple pager adapter that represents 3 Screen Slide PageFragment objects, in sequence.
     */
    private inner class MainPagerAdapter(fm: FragmentManager?) : FragmentPagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                CL_FRAGMENT -> {
                    contactListFragment = ContactListFragment()
                    contactListFragment
                }
                CRL_FRAGMENT -> ChatRoomListFragment()
                CHAT_SESSION_FRAGMENT -> ChatSessionFragment()
                CALL_HISTORY_FRAGMENT -> CallHistoryFragment()
                else -> WebViewFragment()
            }
        }

        /**
         * Save the reference of position to FragmentPagerAdapter fragmentTag in mFragmentTags
         *
         * @param container The viewGroup
         * @param position The pager position
         *
         * @return Fragment object at the specific location
         */
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val obj = super.instantiateItem(container, position)
            if (obj is Fragment) {
                assert(obj.tag != null)
                mFragmentTags[position] = obj.tag
            }
            return obj
        }

        override fun getCount(): Int {
            return NUM_PAGES
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        Timber.d("onRequestPermissionsResult: %s => %s", requestCode, permissions)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PRC_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && PackageManager.PERMISSION_GRANTED != grantResults[0]) {
                aTalkApp.showToastMessage(R.string.audio_permission_denied_feedback)
            }
        } else if (requestCode == PRC_CAMERA) {
            if (grantResults.isNotEmpty() && PackageManager.PERMISSION_GRANTED != grantResults[0]) {
                aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback)
            }
        }
    }

    companion object {
        /**
         * A map reference to find the FragmentPagerAdapter's fragmentTag (String) by a given position (Integer)
         */
        private val mFragmentTags = HashMap<Int, String?>()
        private var mFragmentManager: FragmentManager? = null
        const val CL_FRAGMENT = 0 
        const val CRL_FRAGMENT = 1
        const val CHAT_SESSION_FRAGMENT = 2
        const val CALL_HISTORY_FRAGMENT = 3

        // public final static int WP_FRAGMENT = 4;
        // android Permission Request Code
        const val PRC_CAMERA = 2000
        const val PRC_GET_CONTACTS = 2001
        const val PRC_RECORD_AUDIO = 2002
        const val PRC_WRITE_EXTERNAL_STORAGE = 2003

        /**
         * The action that will show contacts.
         */
        const val ACTION_SHOW_CONTACTS = "org.atalk.show_contacts"
        const val Theme_Change = 1
        const val Locale_Change = 2
        var mPrefChange = 0
        private val mInstances = ArrayList<aTalk>()

        /**
         * The number of pages (wizard steps) to show.
         */
        private const val NUM_PAGES = 5
        fun setPrefChange(change: Int) {
            if (Locale_Change == change) aTalkApp.showToastMessage(R.string.service_gui_settings_Restart_Hint)
            mPrefChange = mPrefChange or change
        }

        /**
         * Get the fragment reference for the given position in pager
         *
         * @param position position in the mFragmentTags
         *
         * @return the requested fragment for the specified position or null
         */
        fun getFragment(position: Int): Fragment? {
            val tag = mFragmentTags[position]
            return if (mFragmentManager != null) mFragmentManager!!.findFragmentByTag(tag) else null
        }

        val instance: aTalk?
            get() = if (mInstances.isEmpty()) null else mInstances[0]

        // =========== Runtime permission handlers ==========
        /**
         * Check the WRITE_EXTERNAL_STORAGE state; proceed to request for permission if requestPermission == true.
         * Require to support WRITE_EXTERNAL_STORAGE pending aTalk installed API version.
         *
         * @param callBack the requester activity to receive onRequestPermissionsResult()
         * @param requestPermission Proceed to request for the permission if was denied; check only if false
         *
         * @return the current WRITE_EXTERNAL_STORAGE permission state
         */
        fun hasWriteStoragePermission(callBack: Activity?, requestPermission: Boolean): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                true
            } else hasPermission(callBack, requestPermission, PRC_WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        fun hasPermission(callBack: Activity?, requestPermission: Boolean, requestCode: Int, permission: String): Boolean {
            // Timber.d(new Exception(),"Callback: %s => %s (%s)", callBack, permission, requestPermission);
            // Do not use getInstance() as mInstances may be empty
            if (aTalkApp.instance.let { ActivityCompat.checkSelfPermission(it, permission) } != PackageManager.PERMISSION_GRANTED) {
                if (requestPermission && callBack != null) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(callBack, permission)) {
                        ActivityCompat.requestPermissions(callBack, arrayOf(permission), requestCode)
                    } else {
                        showHintMessage(requestCode, permission)
                    }
                }
                return false
            }
            return true
        }

        // ========== Media call resource permission requests ==========
        fun isMediaCallAllowed(isVideoCall: Boolean): Boolean {
            // Check for resource permission before continue
            return if (hasPermission(instance, true, PRC_RECORD_AUDIO, Manifest.permission.RECORD_AUDIO)) {
                !isVideoCall || hasPermission(instance, true, PRC_CAMERA, Manifest.permission.CAMERA)
            } else false
        }

        private fun showHintMessage(requestCode: Int, permission: String) {
            when (requestCode) {
                PRC_RECORD_AUDIO -> {
                    aTalkApp.showToastMessage(R.string.audio_permission_denied_feedback)
                }
                PRC_CAMERA -> {
                    aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback)
                }
                else -> {
                    aTalkApp.showToastMessage(aTalkApp.getResString(R.string.permission_rationale_title) + ": " + permission)
                }
            }
        }
    }
}