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
package org.atalk.hmos.gui.webview

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.BuildConfig
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.service.osgi.OSGiFragment
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * The class displays the content accessed via given web link
 * https://developer.android.com/guide/webapps/webview
 *
 * @author Eng Chong Meng
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewFragment : OSGiFragment(), View.OnKeyListener {
    private lateinit var webView: WebView
    private lateinit var progressbar: ProgressBar

    // stop webView.goBack() once we have started reload from urlStack
    private var isLoadFromStack = false
    private var webUrl: String? = null
    private var mUploadMessageArray: ValueCallback<Array<Uri>>? = null

    @SuppressLint("JavascriptInterface")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val contentView = inflater.inflate(R.layout.webview_main, container, false)
        progressbar = contentView.findViewById(R.id.progress)
        progressbar.isIndeterminate = true
        webView = contentView.findViewById(R.id.webview)
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true

        // https://developer.android.com/guide/webapps/webview#BindingJavaScript
        webView.addJavascriptInterface(aTalkApp.globalContext, "Android")
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowUniversalAccessFromFileURLs = true
        val mGetContents = fileUris
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                progressbar.progress = progress
                if (progress in 1..99 && progressbar.visibility == ProgressBar.GONE) {
                    progressbar.isIndeterminate = true
                    progressbar.visibility = ProgressBar.VISIBLE
                }
                if (progress == 100) {
                    progressbar.visibility = ProgressBar.GONE
                }
            }

            override fun onShowFileChooser(webView: WebView, uploadMessageArray: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams): Boolean {
                if (mUploadMessageArray != null) mUploadMessageArray!!.onReceiveValue(null)
                mUploadMessageArray = uploadMessageArray
                mGetContents.launch("*/*")
                return true
            }
        }

        // https://developer.android.com/guide/webapps/webview#HandlingNavigation
        webView.webViewClient = MyWebViewClient(this)

        // init webUrl with urlStack.pop() if non-empty, else load from default in DB
        if (urlStack.isEmpty()) {
            webUrl = ConfigurationUtils.webPage
            urlStack.push(webUrl)
        } else {
            webUrl = urlStack.pop()
        }
        webView.loadUrl(webUrl!!)
        return contentView
    }

    override fun onResume() {
        super.onResume()

        // setup keyPress listener - must re-enable every time on resume
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.setOnKeyListener(this)
    }

    /**
     * Push the last loaded/user clicked url page to the urlStack for later retrieval in onCreateView(),
     * allow same web page to be shown when user slides and returns to the webView
     *
     * @param url loaded/user clicked url
     */
    fun addLastUrl(url: String?) {
        urlStack.push(url)
        isLoadFromStack = false
    }

    /**
     * Opens a FileChooserDialog to let the user pick files for upload
     */
    private val fileUris: ActivityResultLauncher<String>
        get() {
            return registerForActivityResult(ActivityResultContracts.GetMultipleContents(), object : ActivityResultCallback<List<Uri>?> {
                override fun onActivityResult(result: List<Uri>?) {
                    when {
                        result != null -> {
                            if (mUploadMessageArray == null) return

                            var uriArray = arrayOfNulls<Uri>(result.size)
                            uriArray = (result as ArrayList).toArray(uriArray)
                            mUploadMessageArray!!.onReceiveValue(uriArray as Array<Uri>)
                            mUploadMessageArray = null
                        }
                        else -> {
                            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST)
                        }
                    }
                }
            })
        }

    // Prevent the webView from reloading on device rotation
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    /**
     * Handler for user enter Back Key
     * User Back Key entry will return to previous web access pages until root; before return to caller
     *
     * @param v view
     * @param keyCode the entered key keycode
     * @param event the key Event
     * @return true if process
     */
    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // android OS will not pass in KEYCODE_MENU???
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                webView.loadUrl("javascript:MovimTpl.toggleMenu()")
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (!isLoadFromStack && webView.canGoBack()) {
                    // Remove the last saved/displayed url push in addLastUrl, so an actual previous page is shown
                    if (!urlStack.isEmpty()) urlStack.pop()
                    webView.goBack()
                    return true
                } else if (!urlStack.isEmpty()) {
                    isLoadFromStack = true
                    webUrl = urlStack.pop()
                    Timber.w("urlStack pop(): %s", webUrl)
                    webView.loadUrl(webUrl!!)
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private val urlStack = Stack<String?>()

        /**
         * Init webView so it download root url stored in DB on next init
         */
        fun initWebView() {
            urlStack.clear()
        }

        fun getBitmapFromURL(src: String?): Bitmap? {
            return try {
                val url = URL(src)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                BitmapFactory.decodeStream(input)
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }
}