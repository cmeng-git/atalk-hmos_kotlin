/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2022 Eng Chong Meng
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
package org.atalk.service.httputil

import android.text.TextUtils
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.apache.http.HttpStatus
import org.apache.http.ParseException
import org.apache.http.auth.AuthScope
import org.apache.http.auth.AuthenticationException
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.protocol.HttpClientContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import javax.net.ssl.SSLContext

/**
 * Common http utils querying http locations, handling redirects, self-signed certificates, host verify
 * on certificates, password protection and storing and reusing credentials for password protected sites.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
object HttpUtils {
    /**
     * The prefix used when storing credentials for sites when no property is provided.
     */
    private const val HTTP_CREDENTIALS_PREFIX = "http.credential."
    private const val USER_NAME = "username"
    private const val PASSWORD = "password"

    /*
     * Local global variables use in getHttpClient() and in other methods if required;
     */
    private val credentialsProvider: HTTPCredentialsProvider? = null

    /**
     * Executes the method and return the result. Handle ask for password when hitting password protected site.
     * Keep asking for password till user clicks cancel or enters correct password.
     * When 'remember password' is checked password is saved, if this
     * password and username are not correct clear them, if there are correct they stay saved.
     *
     * @param httpClient the configured http client to use.
     * @param postRequest the request for now it is get or post.
     * username and password in order to avoid asking the user twice.
     * @return the result http entity.
     */
    @Throws(Throwable::class)
    private fun executeMethod(httpClient: OkHttpClient, postRequest: Request): ResponseBody? {
        // do it when response (first execution) or till we are unauthorized
        var response: Response? = null
        var cancelableCall: Call
        while (response == null || HttpStatus.SC_UNAUTHORIZED == response.code || HttpStatus.SC_FORBIDDEN == response.code) {
            // if we were unauthorized, lets clear the method and recreate it for new connection with new credentials.
            if (response != null && (HttpStatus.SC_UNAUTHORIZED == response.code || HttpStatus.SC_FORBIDDEN == response.code)) {
                Timber.d("Will retry http connect and credentials input as latest are not correct!")
                throw AuthenticationException("Authorization needed")
            } else {
                Timber.i("Auto checking for software update: %s", postRequest)
                cancelableCall = httpClient.newCall(postRequest)
                response = cancelableCall.execute()
            }

            // if user click cancel no need to retry, stop trying
            if (!credentialsProvider!!.retry()) {
                Timber.d("User canceled credentials input.")
                cancelableCall.cancel()
                break
            }
        }

        // if we finally managed to login return the result or null if user has cancelled.
        return if (HttpStatus.SC_OK == response!!.code) response.body else null
    }

    /**
     * Posting form to <tt>url</tt>. For submission we use POST method i.e. "application/x-www-form-urlencoded" encoded.
     *
     * @param url HTTP address.
     * @param usernamePropertyName the property to use to retrieve/store username value
     * if protected site is hit, for username ConfigurationService service is used.
     * @param passwordPropertyName the property to use to retrieve/store password value
     * if protected site is hit, for password CredentialsStorageService service is used.
     * @param jsonObject the Json parameter to include in post.
     * @param username the username parameter if any, otherwise null
     * @param password the password parameter if any, otherwise null
     * @param headerParamNames additional header name to include
     * @param headerParamValues corresponding header value to include
     * @return the result or throws IOException
     */
    @Throws(IOException::class)
    fun postForm(
            url: String, usernamePropertyName: String?, passwordPropertyName: String?,
            jsonObject: JSONObject, username: String?, password: String?,
            headerParamNames: List<String>?, headerParamValues: List<String>?,
    ): HTTPResponseResult {
        val requestJsonBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                jsonObject.toString()
        )
        val prBuilder = Request.Builder()
                .url(url)
                .post(requestJsonBody)

        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) prBuilder.addHeader("Authorization", basic(username!!, password!!))
        
        if (headerParamNames != null && headerParamValues != null) {
            for (i in headerParamNames.indices) {
                prBuilder.addHeader(headerParamNames[i], headerParamValues[i])
            }
        }
        val httpClient = HttpConnectionManager.buildHttpClient(url, 10)
        val response = httpClient.newCall(prBuilder.build()).execute()
        return HTTPResponseResult(response.body, httpClient)
    }

    /**
     * Returns the preconfigured http client, using CertificateVerificationService, timeouts, user-agent,
     * hostname verifier, proxy settings are used from global java settings,
     * if protected site is hit asks for credentials using util.swing.AuthenticationWindow.
     *
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     * @param credentialsProvider if not null provider will bre reused in the new client
     * @param url the HTTP url we will be connecting to
     */
    //    public static DefaultHttpClient getHttpClient(final String url,
    //            CredentialsProvider credentialsProvider, RedirectStrategy redirectStrategy)
    //            throws IOException
    @Throws(IOException::class)
    fun getHttpClient(url: String?): OkHttpClient {
        var sslContext: SSLContext? = null
        sslContext = try {
            HttpUtilActivator.certificateVerificationService
                    .getSSLContext(HttpUtilActivator.certificateVerificationService.getTrustManager(url))
        } catch (e: GeneralSecurityException) {
            throw IOException(e.message)
        }
        return HttpConnectionManager.buildHttpClient(url, 10)
    }

    /**
     * The provider asking for the password that is inserted into httpclient.
     */
    private class HTTPCredentialsProvider
    /**
     * Creates HTTPCredentialsProvider.
     *
     * @param usernamePropertyName the property uses to retrieve/store username value
     * if protected site is hit; for username retrieval, the ConfigurationService service is used.
     * @param passwordPropertyName the property uses to retrieve/store password value
     * if protected site is hit, for password retrieval, the  CredentialsStorageService service is used.
     */
    (
            /**
             * The property uses to retrieve/store username value if protected site is hit,
             * for the username retrieval, ConfigurationService service is used.
             */
            private var usernamePropertyName: String?,
            /**
             * The property uses to retrieve/store password value if protected site is hit,
             * for the password retrieval, CredentialsStorageService service is used.
             */
            private var passwordPropertyName: String?,
    ) : CredentialsProvider {
        /**
         * Should we continue retrying, this is set when user hits cancel.
         */
        private var retry = true

        /**
         * The last scope we have used, no problem overriding cause
         * we use new HTTPCredentialsProvider instance for every httpclient/request.
         */
        private var usedScope: AuthScope? = null
        /**
         * Returns authentication username if any
         *
         * @return authentication username or null
         */
        /**
         * Authentication username if any.
         */
        var authenticationUsername: String? = null
            private set
        /**
         * Returns authentication password if any
         *
         * @return authentication password or null
         */
        /**
         * Authentication password if any.
         */
        var authenticationPassword: String? = null
            private set

        /**
         * Error message.
         */
        private var errorMessage: String? = null

        /**
         * Not used.
         */
        override fun setCredentials(authscope: AuthScope, credentials: Credentials) {}

        /**
         * Get the [Credentials][org.apache.hc.client5.http.auth.Credentials] for the given authentication scope.
         *
         * @param authScope the [authentication scope][org.apache.hc.client5.http.auth.AuthScope]
         * @return the credentials
         * @see .setCredentials
         */
        // @Override
        override fun getCredentials(authScope: AuthScope): Credentials? {
            usedScope = authScope

            // Use specified password and username property if provided, else create one from the scope/site we are connecting to.
            // cmeng: same property name for both??? so changed
            if (passwordPropertyName == null) passwordPropertyName = getCredentialProperty(authScope, PASSWORD)
            if (usernamePropertyName == null) usernamePropertyName = getCredentialProperty(authScope, USER_NAME)

            // load the password; if password is not saved ask user for credentials
            authenticationPassword = HttpUtilActivator.getCredentialsService().loadPassword(passwordPropertyName!!)
            if (authenticationPassword == null) {
                val authenticationWindowService = HttpUtilActivator.authenticationWindowService
                if (authenticationWindowService == null) {
                    Timber.e("No AuthenticationWindowService implementation")
                    return null
                }
                val authWindow = authenticationWindowService.create(
                        authenticationUsername, null, authScope.host, true, false,
                        null, null, null, null, null, errorMessage,
                        HttpUtilActivator.resources.getSettingsString("plugin.provisioning.SIGN_UP_LINK"))
                authWindow!!.setVisible(true)
                if (!authWindow.isCanceled) {
                    val credentials = UsernamePasswordCredentials(authWindow.userName,
                            String(authWindow.password!!))
                    authenticationUsername = authWindow.userName
                    authenticationPassword = String(authWindow.password!!)

                    // Save passwords if password remember is checked, if they seem not correct later will be removed.
                    if (authWindow.isRememberPassword) {
                        HttpUtilActivator.getConfigurationService()
                                .setProperty(usernamePropertyName!!, authWindow.userName)
                        HttpUtilActivator.getCredentialsService()
                                .storePassword(passwordPropertyName!!, String(authWindow.password!!))
                    }
                    return credentials
                }

                // User canceled credentials input stop retry asking him if credentials are not correct
                retry = false
            } else {
                // we have saved values lets return them
                authenticationUsername = HttpUtilActivator.getConfigurationService().getString(usernamePropertyName!!)
                return UsernamePasswordCredentials(authenticationUsername, authenticationPassword)
            }
            return null
        }

        /**
         * Clear saved password. Used when we are in a situation that
         * saved username and password are no longer valid.
         */
        override fun clear() {
            if (usedScope != null) {
                if (passwordPropertyName == null) passwordPropertyName = getCredentialProperty(usedScope!!, PASSWORD)
                if (usernamePropertyName == null) usernamePropertyName = getCredentialProperty(usedScope!!, USER_NAME)
                HttpUtilActivator.getConfigurationService().removeProperty(usernamePropertyName!!)
                HttpUtilActivator.getCredentialsService().removePassword(passwordPropertyName!!)
            }
            authenticationUsername = null
            authenticationPassword = null
            errorMessage = null
        }

        /**
         * Whether we need to continue retrying.
         *
         * @return whether we need to continue retrying.
         */
        fun retry(): Boolean {
            return retry
        }

        companion object {
            /**
             * Constructs property name for save if one is not specified.
             * Its in the form HTTP_CREDENTIALS_PREFIX.host.realm.port
             *
             * @param authscope the scope, holds host,realm, port info about the host we are reaching.
             * @return return the constructed property.
             */
            private fun getCredentialProperty(authscope: AuthScope): String {
                return HTTP_CREDENTIALS_PREFIX + authscope.host +
                        "." + authscope.realm +
                        "." + authscope.port
            }

            /**
             * Constructs property name for saving if one is not specified.
             * It's in the form HTTP_CREDENTIALS_PREFIX.host.realm.port
             *
             * @param authScope the scope, holds host,realm, port info about the host we are reaching.
             * @return return the constructed property.
             */
            private fun getCredentialProperty(authScope: AuthScope, propertyName: String): String {
                return HTTP_CREDENTIALS_PREFIX + authScope.host +
                        "." + authScope.realm +
                        "." + authScope.port +
                        "." + propertyName
            }
        }
    }

    /**
     * Utility class wraps the http requests result and some utility methods for retrieving info and content for the result.
     */
    class HTTPResponseResult
    /**
     * Creates HTTPResponseResult.
     *
     * @param responseBody the httpclient responseBody.
     * @param httpClient the httpclient.
     */ internal constructor(
            /**
             * The httpclient entity.
             */
            var responseBody: ResponseBody?,
            /**
             * The httpclient.
             */
            var httpClient: OkHttpClient?,
    ) {
        /**
         * Tells the length of the content, if known.
         *
         * @return the number of bytes of the content, or a negative number if unknown. If the content length
         * is known but exceeds [Long.MAX_VALUE], a negative number is returned.
         */
        val contentLength: Long
            get() = responseBody!!.contentLength()

        /**
         * Returns a content stream of the entity.
         *
         * @return content stream of the entity.
         * @throws IOException if the stream could not be created
         * @throws IllegalStateException if content stream cannot be created.
         */
        @get:Throws(IOException::class, IllegalStateException::class)
        val content: InputStream
            get() = responseBody!!.byteStream()

        /**
         * Returns a content string of the entity.
         *
         * @return content string of the entity.
         * @throws IOException if the stream could not be created
         */
        @get:Throws(IOException::class)
        val contentString: String?
            get() {
                try {
                    return responseBody!!.string()
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
                return null
            }

        /**
         * Get the credentials used by the request.
         *
         * @return the credentials (login at index 0 and password at index 1)
         */
        val credentials: Array<String?>
            get() {
                val cred = arrayOfNulls<String>(2)
                if (httpClient != null) {
                    val prov = HttpClientContext.create().credentialsProvider as HTTPCredentialsProvider
                    cred[0] = prov.authenticationUsername
                    cred[1] = prov.authenticationPassword
                }
                return cred
            }
    }
}