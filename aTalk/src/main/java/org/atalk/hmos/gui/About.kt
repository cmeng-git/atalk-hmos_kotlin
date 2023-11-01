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

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.*
import android.webkit.WebView
import android.widget.*
import de.cketti.library.changelog.ChangeLog
import net.java.sip.communicator.service.update.UpdateService
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.*
import org.atalk.hmos.R.id

/**
 * About activity
 *
 * @author Eng Chong Meng
 */
class About : BaseActivity(), View.OnClickListener {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.about)
        // crash if enabled under FragmentActivity
        // requestWindowFeature(Window.FEATURE_LEFT_ICON);
        // setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_info);
        setMainTitle(R.string.AboutDialog_title)
        val atalkUrl = findViewById<View>(id.atalk_link)
        atalkUrl.setOnClickListener(this)
        val atalkHelp = findViewById<TextView>(id.atalk_help)
        atalkHelp.setTextColor(resources.getColor(R.color.blue50, null))
        atalkHelp.setOnClickListener(this)
        findViewById<View>(id.ok_button).setOnClickListener(this)
        findViewById<View>(id.history_log).setOnClickListener(this)
        val btn_submitLogs = findViewById<View>(id.submit_logs)
        btn_submitLogs.setOnClickListener(this)
        if (BuildConfig.DEBUG) {
            val btn_update = findViewById<View>(id.check_new_version)
            btn_update.visibility = View.VISIBLE
            btn_update.setOnClickListener(this)
        }
        val aboutInfo = aboutInfo
        val wv = findViewById<WebView>(id.AboutDialog_Info)
        wv.loadDataWithBaseURL("file:///android_res/drawable/", aboutInfo, "text/html", "utf-8", null)
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val textView = findViewById<TextView>(id.AboutDialog_Version)
            textView.text = String.format(getString(R.string.AboutDialog_Version), pi.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            id.ok_button -> finish()
            id.check_new_version -> object : Thread() {
                override fun run() {
                    val updateService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, UpdateService::class.java)
                    updateService?.checkForUpdates()
                }
            }.start()
            id.submit_logs -> aTalkApp.showSendLogsDialog()
            id.history_log -> {
                val cl = ChangeLog(this, DEFAULT_CSS)
                cl.fullLogDialog.show()
            }
            id.atalk_help, id.atalk_link -> atalkUrlAccess(this, getString(R.string.AboutDialog_Link))
            else -> finish()
        }
    }

    private val aboutInfo: String
        get() {
            val html = StringBuilder()
                    .append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"/>")
                    .append("<html><head><style type=\"text/css\">")
                    .append(DEFAULT_CSS)
                    .append("</style></head><body>")
            val xeps = StringBuilder().append("<ul>")
            for (feature in SUPPORTED_XEP) {
                xeps.append("<li><a href=\"")
                        .append(feature[1])
                        .append("\">")
                        .append(feature[0])
                        .append("</a></li>")
            }
            xeps.append("</ul>")
            html.append(String.format(getString(R.string.app_xeps), xeps.toString()))
                    .append("</p><hr/><p>")
            val libs = StringBuilder().append("<ul>")
            for (library in USED_LIBRARIES) {
                libs.append("<li><a href=\"")
                        .append(library[1])
                        .append("\">")
                        .append(library[0])
                        .append("</a></li>")
            }
            libs.append("</ul>")
            html.append(String.format(getString(R.string.app_libraries), libs.toString()))
                    .append("</p><hr/><p>")
            html.append("</body></html>")
            return html.toString()
        }

    companion object {
        private val USED_LIBRARIES = arrayOf(arrayOf("Android Support Library", "https://developer.android.com/topic/libraries/support-library/index.html"), arrayOf("android-betterpickers", "https://github.com/code-troopers/android-betterpickers"), arrayOf("Android-EasyLocation", "https://github.com/akhgupta/Android-EasyLocation"), arrayOf("annotations-java5", "https://mvnrepository.com/artifact/org.jetbrains/annotations"), arrayOf("bouncycastle", "https://github.com/bcgit/bc-java"), arrayOf("butterknife", "https://github.com/JakeWharton/butterknife"), arrayOf("ckChangeLog", "https://github.com/cketti/ckChangeLog"), arrayOf("commons-lang", "https://commons.apache.org/proper/commons-lang/"), arrayOf("Dexter", "https://github.com/Karumi/Dexter"), arrayOf("dhcp4java", "https://github.com/ggrandes-clones/dhcp4java"), arrayOf("ExoPlayer", "https://github.com/google/ExoPlayer"), arrayOf("FFmpeg", "https://github.com/FFmpeg/FFmpeg"), arrayOf("glide", "https://github.com/bumptech/glide"), arrayOf("Google Play Services", "https://developers.google.com/android/guides/overview"), arrayOf("httpclient-android", "https://github.com/smarek/httpclient-android"), arrayOf("IPAddress", "https://github.com/seancfoley/IPAddress"), arrayOf("ice4j", "https://github.com/jitsi/ice4j"), arrayOf("jitsi", "https://github.com/jitsi/jitsi"), arrayOf("jitsi-android", "https://github.com/jitsi/jitsi-android"), arrayOf("jmdns", "https://github.com/jmdns/jmdns"), arrayOf("jxmpp-jid", "https://github.com/igniterealtime/jxmpp"), arrayOf("libjitsi", "https://github.com/jitsi/libjitsi"), arrayOf("libphonenumber", "https://github.com/googlei18n/libphonenumber"), arrayOf("libvpx", "https://github.com/webmproject/libvpx"), arrayOf("Mime4j", "https://james.apache.org/mime4j/"), arrayOf("miniDNS", "https://github.com/MiniDNS/minidns"), arrayOf("Noembed", "https://noembed.com/"), arrayOf("osmdroid", "https://github.com/osmdroid/osmdroid"), arrayOf("otr4j", "https://github.com/jitsi/otr4j"), arrayOf("opensles", "https://github.com/openssl/openssl "), arrayOf("osgi.core", "http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0"), arrayOf("sdes4j", "https://github.com/ibauersachs/sdes4j"), arrayOf("sdp-api", "https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api"), arrayOf("Smack", "https://github.com/igniterealtime/Smack"), arrayOf("speex", "https://github.com/xiph/speex"), arrayOf("Timber", "https://github.com/JakeWharton/timber"), arrayOf("TokenAutoComplete", "https://github.com/splitwise/TokenAutoComplete"), arrayOf("uCrop", "https://github.com/Yalantis/uCrop"), arrayOf("weupnp", "https://github.com/bitletorg/weupnp"), arrayOf("x264", "https://git.videolan.org/git/x264.git"), arrayOf("zrtp4j-light", "https://github.com/jitsi/zrtp4j"))
        private val SUPPORTED_XEP = arrayOf(arrayOf("XEP-0012: Last Activity 2.0", "https://xmpp.org/extensions/xep-0012.html"), arrayOf("XEP-0030: Service Discovery 2.5rc3", "https://xmpp.org/extensions/xep-0030.html"), arrayOf("XEP-0045: Multi-User Chat 1.34.3", "https://xmpp.org/extensions/xep-0045.html"), arrayOf("XEP-0047: In-Band Bytestreams 2.0.1", "https://xmpp.org/extensions/xep-0047.html"), arrayOf("XEP-0048: Bookmarks 1.2", "https://xmpp.org/extensions/xep-0048.html"), arrayOf("XEP-0054: vcard-temp 1.2", "https://xmpp.org/extensions/xep-0054.html"), arrayOf("XEP-0060: Publish-Subscribe 1.24.1", "https://xmpp.org/extensions/xep-0060.html"), arrayOf("XEP-0065: SOCKS5 Bytestreams 1.8.2", "https://xmpp.org/extensions/xep-0065.html"), arrayOf("XEP-0070: Verifying HTTP Requests via XMPP 1.0.1", "https://xmpp.org/extensions/xep-0070.html"), arrayOf("XEP-0071: XHTML-IM 1.5.4", "https://xmpp.org/extensions/xep-0071.html"), arrayOf("XEP-0077: In-Band Registration 2.4", "https://xmpp.org/extensions/xep-0077.html"), arrayOf("XEP-0084: User Avatar 1.1.4", "https://xmpp.org/extensions/xep-0084.html"), arrayOf("XEP-0085: Chat State Notifications 2.1", "https://xmpp.org/extensions/xep-0085.html"), arrayOf("XEP-0092: Software Version 1.1", "https://xmpp.org/extensions/xep-0092.html"), arrayOf("XEP-0095: Stream Initiation 1.2", "https://xmpp.org/extensions/xep-0095.html"), arrayOf("XEP-0096: SI File Transfer 1.3.1", "https://xmpp.org/extensions/xep-0096.html"), arrayOf("XEP-0100: Gateway Interaction 1.0", "https://xmpp.org/extensions/xep-0100.html"), arrayOf("XEP-0115: Entity Capabilities 1.6.0", "https://xmpp.org/extensions/xep-0115.html"), arrayOf("XEP-0124: Bidirectional-streams Over Synchronous HTTP (BOSH) 1.11.2", "https://xmpp.org/extensions/xep-0124.html"), arrayOf("XEP-0138: Stream Compression 2.1", "https://xmpp.org/extensions/xep-0138.html"), arrayOf("XEP-0153: vCard-Based Avatar 1.1", "https://xmpp.org/extensions/xep-0153.html"), arrayOf("XEP-0158: CAPTCHA Forms 1.5.8", "https://xmpp.org/extensions/xep-0158.html"), arrayOf("XEP-0163: Personal Eventing Protocol 1.2.2", "https://xmpp.org/extensions/xep-0163.html"), arrayOf("XEP-0166: Jingle 1.1.2", "https://xmpp.org/extensions/xep-0166.html"), arrayOf("XEP-0167: Jingle RTP Sessions 1.2.1", "https://xmpp.org/extensions/xep-0167.html"), arrayOf("XEP-0172: User Nickname 1.1", "https://xmpp.org/extensions/xep-0172.html"), arrayOf("XEP-0176: Jingle ICE-UDP Transport Method 1.1.1", "https://xmpp.org/extensions/xep-0176.html"), arrayOf("XEP-0177: Jingle Raw UDP Transport Method 1.1.1", "https://xmpp.org/extensions/xep-0177.html"), arrayOf("XEP-0178: Best Practices for Use of SASL EXTERNAL with Certificates 1.2", "https://xmpp.org/extensions/xep-0178.html"), arrayOf("XEP-0184: Message Delivery Receipts 1.4.0", "https://xmpp.org/extensions/xep-0184.html"), arrayOf("XEP-0198: Stream Management 1.6", "https://xmpp.org/extensions/xep-0198.html"), arrayOf("XEP-0199: XMPP Ping 2.0.1", "https://xmpp.org/extensions/xep-0199.html"), arrayOf("XEP-0203: Delayed Delivery 2.0", "https://xmpp.org/extensions/xep-0203.html"), arrayOf("XEP-0206: XMPP Over BOSH 1.4", "https://xmpp.org/extensions/xep-0206.html"), arrayOf("XEP-0215: External Service Discovery 1.0.0", "https://xmpp.org/extensions/xep-0215.html"), arrayOf("XEP-0231: Bits of Binary 1.0", "https://xmpp.org/extensions/xep-0231.html"), arrayOf("XEP-0234: Jingle File Transfer 0.19.1", "https://xmpp.org/extensions/xep-0234.html"), arrayOf("XEP-0237: Roster Versioning 1.3", "https://xmpp.org/extensions/xep-0237.html"), arrayOf("XEP-0249: Direct MUC Invitations 1.2", "https://xmpp.org/extensions/xep-0249.html"), arrayOf("XEP-0251: Jingle Session Transfer 0.2", "https://xmpp.org/extensions/xep-0251.html"), arrayOf("XEP-0260: Jingle SOCKS5 Bytestreams Transport Method 1.0.3", "https://xmpp.org/extensions/xep-0260.html"), arrayOf("XEP-0261: Jingle In-Band Bytestreams Transport Method 1.0", "https://xmpp.org/extensions/xep-0261.html"), arrayOf("XEP-0262: Use of ZRTP in Jingle RTP Sessions 1.0", "https://xmpp.org/extensions/xep-0262.html"), arrayOf("XEP-0264: File Transfer Thumbnails 0.4", "https://xmpp.org/extensions/xep-0264.html"), arrayOf("XEP-0278: Jingle Relay Nodes 0.4.1", "https://xmpp.org/extensions/xep-0278.html"), arrayOf("XEP-0280: Message Carbons 1.0.1", "https://xmpp.org/extensions/xep-0280.html"), arrayOf("XEP-0293: Jingle RTP Feedback Negotiation 1.0.1", "https://xmpp.org/extensions/xep-0293.html"), arrayOf("XEP-0294: Jingle RTP Header Extensions Negotiation 1.1.1", "https://xmpp.org/extensions/xep-0294.html"), arrayOf("XEP-0298: Delivering Conference Information to Jingle Participants (Coin) 0.2", "https://xmpp.org/extensions/xep-0298.html"), arrayOf("XEP-0308: Last Message Correction 1.2.0", "https://xmpp.org/extensions/xep-0308.html"), arrayOf("XEP-0313: Message Archive Management 1.0.1", "https://xmpp.org/extensions/xep-0313.html"), arrayOf("XEP-0319: Last User Interaction in Presence 1.0.2", "https://xmpp.org/extensions/xep-0319.html"), arrayOf("XEP-0320: Use of DTLS-SRTP in Jingle Sessions 1.0.0", "https://xmpp.org/extensions/xep-0320.html"), arrayOf("XEP-0338: Jingle Grouping Framework 1.0.0", "https://xmpp.org/extensions/xep-0338.html"), arrayOf("XEP-0339: Source-Specific Media Attributes in Jingle 1.0.1", "https://xmpp.org/extensions/xep-0339.html"), arrayOf("XEP-0343: Signaling WebRTC datachannels in Jingle 0.3.1", "https://xmpp.org/extensions/xep-0343.html"), arrayOf("XEP-0352: Client State Indication 1.0.0", "https://xmpp.org/extensions/xep-0352.html"), arrayOf("XEP-0353: Jingle Message Initiation 0.4.0", "https://xmpp.org/extensions/xep-0353.html"), arrayOf("XEP-0363: HTTP File Upload 1.1.0", "https://xmpp.org/extensions/xep-0363.html"), arrayOf("XEP-0364: Off-the-Record Messaging (V2/3) 0.3.2", "https://xmpp.org/extensions/xep-0364.html"), arrayOf("XEP-0371: Jingle ICE Transport Method 0.3.1", "https://xmpp.org/extensions/xep-0371.html"), arrayOf("XEP-0384: OMEMO Encryption 0.8.3", "https://xmpp.org/extensions/xep-0384.html"), arrayOf("XEP-0391: Jingle Encrypted Transports 0.1.2", "https://xmpp.org/extensions/xep-0391.html"), arrayOf("XEP-0441: Message Archive Management Preferences 0.2.0", "https://xmpp.org/extensions/xep-0441.htmll"), arrayOf("XEP-xxxx: OMEMO Media sharing 0.0.2", "https://xmpp.org/extensions/inbox/omemo-media-sharing.html"))

        /**
         * Default CSS styles used to format the change log.
         */
        const val DEFAULT_CSS = "h1 { margin-left: 0px; font-size: 1.2em; }" + "\n" +
                "li { margin-left: 0px; font-size: 0.9em;}" + "\n" +
                "ul { padding-left: 2em; }"

        fun atalkUrlAccess(context: Context, url: String?) {
            var url = url
            if (url == null) url = context.getString(R.string.AboutDialog_Link)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        }
    }
}