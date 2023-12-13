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
package org.atalk.hmos.gui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ViewUtil.setTextViewValue
import org.atalk.service.osgi.OSGiFragment

/**
 * Fragment for history message delete with media delete option
 *
 * @author Eng Chong Meng
 */
class AccountDeleteFragment : OSGiFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val viewAccountDelete = inflater.inflate(R.layout.account_delete, container, false)
        setTextViewValue(viewAccountDelete, R.id.textView, arguments!!.getString(ARG_MESSAGE))
        return viewAccountDelete
    }

    companion object {
        const val ARG_MESSAGE = "dialog_message"
    }
}