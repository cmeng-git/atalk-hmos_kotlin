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
package org.atalk.hmos.gui.util

import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.text.Html
import android.text.Html.ImageGetter
import android.text.method.LinkMovementMethod
import android.widget.*
import java.io.IOException
import java.net.URL

/**
 * Utility class that implements `Html.ImageGetter` interface and can be used
 * to display url images in `TextView` through the HTML syntax.
 *
 * @author Eng Chong Meng
 */
class XhtmlImageParser
/**
 * Construct the XhtmlImageParser which will execute AsyncTask and refresh the TextView
 * Usage: htmlTextView.setText(Html.fromHtml(HtmlString, new XhtmlImageParser(htmlTextView, HtmlString), null));
 *
 * @param tv the textView to be populated with return result
 * @param str the xhtml string
 */
(private val mTextView: TextView, private val XhtmlString: String) : ImageGetter {
    /**
     * {@inheritDoc}
     */
    override fun getDrawable(source: String): Drawable? {
        val httpGetDrawableTask = HttpGetDrawableTask()
        httpGetDrawableTask.execute(source)
        return null
    }

    /**
     * Execute fetch url image as async task: else 'android.os.NetworkOnMainThreadException'
     */
    inner class HttpGetDrawableTask : AsyncTask<String?, Void?, Drawable?>() {
        override fun doInBackground(vararg params: String?): Drawable? {
            val source = params[0]!!
            return getDrawable(source)
        }

        override fun onPostExecute(result: Drawable?) {
            if (result != null) {
                mTextView.text = Html.fromHtml(XhtmlString, { source: String? -> result }, null)
            } else {
                mTextView.text = Html.fromHtml(XhtmlString, null, null)
            }
            mTextView.movementMethod = LinkMovementMethod.getInstance()
        }

        /***
         * Get the Drawable from the given URL (change to secure https if necessary)
         * aTalk/android supports only secure https connection
         *
         * @param urlString url string
         * @return drawable
         */
        fun getDrawable(urlString: String): Drawable? {
            var urlString = urlString
            return try {
                // urlString = "https://cmeng-git.github.io/atalk/img/09.atalk_avatar.png";
                urlString = urlString.replace("http:", "https:")
                val sourceURL = URL(urlString)
                val urlConnection = sourceURL.openConnection()
                urlConnection.connect()
                val inputStream = urlConnection.getInputStream()
                val drawable = Drawable.createFromStream(inputStream, "src")
                drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable
            } catch (e: IOException) {
                null
            }
        }
    }
}