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
package org.atalk.hmos.gui.share

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import org.atalk.hmos.R
import org.atalk.hmos.gui.chatroomslist.ChatRoomListFragment
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.util.DepthPageTransformer
import org.atalk.service.osgi.OSGiActivity

/**
 * ShareActivity is defined as SingleTask, to avoid multiple instances being created if user does not exit
 * this activity before start another sharing.
 *
 * ShareActivity provides multiple contacts sharing. However, this requires aTalk does not have any
 * chatFragment current in active open state. Otherwise, Android OS destroys this activity on first
 * contact sharing; and multiple contacts sharing is no possible.
 *
 * @author Eng Chong Meng
 */
class ShareActivity : OSGiActivity() {
    /**
     * mCategories is used in aTalk to sore msgContent if multiple type sharing is requested by user
     */
    private class Share {
        var mCategories: Set<String>? = null
        var uris = ArrayList<Uri>()
        var action: String? = null
        var type: String? = null
        var text: String? = null
        fun clear() {
            mCategories = null
            uris = ArrayList()
            action = null
            type = null
            text = null
        }
    }

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sharewith_view)
        // configureToolBar();
        val actionBar = supportActionBar
        if (actionBar != null) {
            var tv = findViewById<TextView>(R.id.actionBarTitle)
            tv.setText(R.string.APPLICATION_NAME)
            tv = findViewById(R.id.actionBarStatus)
            tv.setText(R.string.service_gui_SHARE)
            actionBar.setBackgroundDrawable(ColorDrawable(resources.getColor(R.color.color_bg_share, null)))
        }

        /*
         * The pager widget, which handles animation and allows swiping horizontally to access previous
         * and next wizard steps.
         */
        val mPager = findViewById<ViewPager>(R.id.shareViewPager)
        val mPagerAdapter = SharePagerAdapter(supportFragmentManager)
        mPager.adapter = mPagerAdapter
        mPager.setPageTransformer(true, DepthPageTransformer())
        mShare = Share()
        handleIntent(intent)
    }

    /**
     * Called when new `Intent` is received(this `Activity` is launched in `singleTask` mode.
     *
     * @param intent new `Intent` data.
     */
    protected override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Decides what should be displayed based on supplied `Intent` and instance state.
     *
     * @param intent `Activity` `Intent`.
     */
    private fun handleIntent(intent: Intent?) {
        super.onStart()
        if (intent == null) {
            return
        }
        val type = intent.type
        val action = intent.action
        mShare!!.clear()
        mShare!!.type = type
        mShare!!.action = action
        if (Intent.ACTION_SEND == action) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (type != null && uri != null) {
                mShare!!.uris.clear()
                mShare!!.uris.add(uri)
            } else {
                mShare!!.text = text
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            mShare!!.uris = uris ?: ArrayList()

            // aTalk send extra_text in categories in this case
            mShare!!.mCategories = intent.getCategories()
        }
    }

    protected override fun onDestroy() {
        super.onDestroy()
        mShare!!.clear()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        getMenuInflater().inflate(R.menu.share_with, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_done) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * A simple pager adapter that represents 3 Screen Slide PageFragment objects, in sequence.
     */
    private class SharePagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment {
            return if (position == 0) {
                ContactListFragment()
            } else {
                ChatRoomListFragment()
            }
        }

        override fun getCount(): Int {
            return NUM_PAGES
        }
    }

    companion object {
        /**
         * The number of pages (wizard steps) to show.
         */
        private const val NUM_PAGES = 2

        /**
         * A reference of the share object
         */
        private var mShare: Share? = null

        /**
         * Retrieve the earlier saved Share object parameters for use with chatIntent
         *
         * @param shareIntent Sharable Intent
         * @return a reference copy of the update chatIntent
         */
        fun getShareIntent(shareIntent: Intent): Intent? {
            if (mShare == null) {
                return null
            }
            shareIntent.setAction(mShare!!.action)
            shareIntent.setType(mShare!!.type)
            if (Intent.ACTION_SEND == mShare!!.action) {
                if (!mShare!!.uris.isEmpty()) {
                    shareIntent.setAction(Intent.ACTION_SEND)
                    shareIntent.putExtra(Intent.EXTRA_STREAM, mShare!!.uris[0])
                } else {
                    shareIntent.putExtra(Intent.EXTRA_TEXT, mShare!!.text)
                }
            } else if (Intent.ACTION_SEND_MULTIPLE == mShare!!.action) {
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, mShare!!.uris)

                // aTalk has the extra_text in Intent.category in this case
                if (mShare!!.mCategories != null) shareIntent.addCategory(mShare!!.mCategories.toString())
            }
            return shareIntent
        }
    }
}