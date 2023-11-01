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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.ViewUtil
import timber.log.Timber
import java.util.regex.Pattern

/**
 * The class implements the WebViewClient for App internal web access
 * https://developer.android.com/guide/webapps/webview
 *
 * @author Eng Chong Meng
 */
class MyWebViewClient(private val viewFragment: WebViewFragment) : WebViewClient() {
    // Domain match pattern for last two segments of host
    private val pattern = Pattern.compile("^.*?[.](.*?[.].+?)$")
    private var mContext = viewFragment.context!!

    private lateinit var mPasswordField: EditText

    override fun shouldOverrideUrlLoading(webView: WebView, url: String): Boolean {
        // Timber.d("shouldOverrideUrlLoading for url (webView url): %s (%s)", url, webView.getUrl());
        // This user clicked url is from the same website, so do not override; let MyWebViewClient load the page
        if (isDomainMatch(webView, url)) {
            viewFragment.addLastUrl(url)
            return false
        }

        // Otherwise, the link is not for a page on my site, so launch another Activity that handle it
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            viewFragment.startActivity(intent)
        } catch (e: Exception) {
            // catch ActivityNotFoundException for xmpp:info@example.com. so let own webView load and display the error
            Timber.w("Failed to load url '%s' : %s", url, e.message)
            val origin = Uri.parse(webView.url).host!!
            val originDomain = pattern.matcher(origin).replaceAll("$1")
            if (url.contains(originDomain)) return false
        }
        return true
    }

    /**
     * If you click on any link inside the webpage of the WebView, that page will not be loaded inside your WebView.
     * In order to do that you need to extend your class from WebViewClient and override the method below.
     * https://developer.android.com/guide/webapps/webview#HandlingNavigation
     *
     * @param view The WebView that is initiating the callback.
     * @param request Object containing the details of the request.
     * @return `true` to cancel the current load, otherwise return `false`.
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return shouldOverrideUrlLoading(view, url)
    }

    // public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
    // {
    //     view.loadUrl("file:///android_asset/movim/error.html");
    // }
    // public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error)
    // {
    //     // view.loadUrl("file:///android_asset/movim/ssl.html");
    // }
    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String,
            realm: String) {
        val httpAuth = arrayOfNulls<String>(2)
        val viewAuth = view.getHttpAuthUsernamePassword(host, realm)
        httpAuth[0] = if (viewAuth != null) viewAuth[0] else ""
        httpAuth[1] = if (viewAuth != null) viewAuth[1] else ""
        if (handler.useHttpAuthUsernamePassword()) {
            handler.proceed(httpAuth[0], httpAuth[1])
            return
        }
        val inflater = LayoutInflater.from(mContext)
        val authView = inflater.inflate(R.layout.http_login_dialog, view, false)
        val usernameInput = authView.findViewById<EditText>(R.id.username)
        usernameInput.setText(httpAuth[0])
        mPasswordField = authView.findViewById(R.id.passwordField)
        mPasswordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        mPasswordField.setText(httpAuth[1])
        val showPasswordCheckBox = authView.findViewById<CheckBox>(R.id.show_password)
        showPasswordCheckBox.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(mPasswordField, isChecked) }
        val authDialog = AlertDialog.Builder(mContext)
                .setTitle(R.string.service_gui_USER_LOGIN)
                .setView(authView)
                .setCancelable(false)
        val dialog = authDialog.show()
        authView.findViewById<View>(R.id.button_signin).setOnClickListener { v: View? ->
            httpAuth[0] = ViewUtil.toString(usernameInput)
            httpAuth[1] = ViewUtil.toString(mPasswordField)
            view.setHttpAuthUsernamePassword(host, realm, httpAuth[0], httpAuth[1])
            handler.proceed(httpAuth[0], httpAuth[1])
            dialog.dismiss()
        }
        authView.findViewById<View>(R.id.button_cancel).setOnClickListener { v: View? ->
            handler.cancel()
            dialog.dismiss()
        }
    }

    /**
     * Match non-case sensitive for whole or at least last two segment of host
     *
     * @param webView the current webView
     * @param url to be loaded
     * @return true if match
     */
    private fun isDomainMatch(webView: WebView, url: String): Boolean {
        val origin = Uri.parse(webView.url).host!!
        val aim = Uri.parse(url).host!!

        // return true if this is the first time url loading or exact match of host
        if (TextUtils.isEmpty(origin) || origin.equals(aim, ignoreCase = true)) return true

        // return false if aim contains no host string i.e. not a url e.g. mailto:info[at]example.com
        if (TextUtils.isEmpty(aim)) return false
        val originDomain = pattern.matcher(origin).replaceAll("$1")
        val aimDomain = pattern.matcher(aim).replaceAll("$1")
        return originDomain.equals(aimDomain, ignoreCase = true)
    }
}