package org.atalk.service.httputil

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Request
import org.apache.http.conn.ssl.StrictHostnameVerifier
import org.atalk.hmos.BuildConfig
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory

object HttpConnectionManager // extends AbstractConnectionManager
{
    val EXECUTOR = Executors.newFixedThreadPool(4)
    private var OK_HTTP_CLIENT: OkHttpClient? = null

    init {
        OK_HTTP_CLIENT = Builder()
                .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                    val original = chain.request()
                    val modified = original.newBuilder()
                            .header("User-Agent", userAgent)
                            .build()
                    chain.proceed(modified)
                })
                .build()
    }

    //        return String.format("%s/%s", System.getProperty(Version.PNAME_APPLICATION_NAME,
    //                System.getProperty(Version.PNAME_APPLICATION_VERSION));
    val userAgent: String
        get() = String.format("%s/%s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME)

    //        return String.format("%s/%s", System.getProperty(Version.PNAME_APPLICATION_NAME,
    //                System.getProperty(Version.PNAME_APPLICATION_VERSION));
    val proxy: Proxy
        get() {
            val localhost = try {
                InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
            } catch (e: UnknownHostException) {
                throw IllegalStateException(e)
            }
            return Proxy(Proxy.Type.SOCKS, InetSocketAddress(localhost, 9050))
        }

    fun buildHttpClient(url: String?, readTimeout: Int): OkHttpClient {
        val builder = OK_HTTP_CLIENT!!.newBuilder()
                .callTimeout(10, TimeUnit.SECONDS) // default timeout for complete calls
                .writeTimeout(10, TimeUnit.SECONDS) // default write timeout for new connections
                .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS) // default read timeout for new connections
                .followRedirects(true) // follow requests redirects
                .followSslRedirects(true) // follow HTTP tp HTTPS redirects
                .retryOnConnectionFailure(true) // retry or not when a connectivity problem is encountered
                .proxy(proxy)
        setupTrustManager(builder, url)
        return builder.build()
    }

    private fun setupTrustManager(builder: Builder, url: String?) {
        try {
            val trustManager = HttpUtilActivator.certificateVerificationService!!.getTrustManager(url)!!
            val sf = SSLSocketFactory.getDefault() as SSLSocketFactory
            builder.sslSocketFactory(sf, trustManager)
            builder.hostnameVerifier(StrictHostnameVerifier())
        } catch (ignored: GeneralSecurityException) {
        }
    }

    @Throws(IOException::class)
    fun open(url: String?, tor: Boolean): InputStream? {
        return open(url!!.toHttpUrl(), tor)
    }

    @Throws(IOException::class)
    fun open(httpUrl: HttpUrl, tor: Boolean): InputStream? {
        val builder = OK_HTTP_CLIENT!!.newBuilder()
        if (tor) {
            builder.proxy(proxy).build()
        }
        val client = builder.build()
        val request = Request.Builder().get().url(httpUrl).build()
        val body = client.newCall(request).execute().body
        if (body == null) {
            // throw new IOException("No response body found");
            Timber.e("No response body found: %s", httpUrl)
            return null
        }
        return body.byteStream()
    }
}