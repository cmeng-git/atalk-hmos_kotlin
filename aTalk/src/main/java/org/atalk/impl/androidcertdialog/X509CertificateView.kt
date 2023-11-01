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
package org.atalk.impl.androidcertdialog

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.webkit.WebView
import org.atalk.hmos.R
import timber.log.Timber
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.*

/**
 * Form that shows the content of an X509Certificate.
 *
 * @author Eng Chong Meng
 */
class X509CertificateView : Dialog {
    private var certificate: Certificate? = null
    private var mContext: Context?

    /**
     * Constructs a X509 certificate form. Mainly use by external to format certificate to html string
     */
    constructor(context: Context?) : super(context!!) {
        mContext = context
    }

    /**
     * Constructs a X509 certificate form from certificate[] chain
     * Default to assume only one certificate in the certificate chain.
     *
     * @param certificates `X509Certificate` object
     */
    constructor(context: Context?, certificates: Array<Certificate?>) : super(context!!) {
        mContext = context
        certificate = certificates[0]
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.x509_certificate_view)
        setTitle(mContext!!.getString(R.string.service_gui_CERT_INFO_CHAIN))
        val certInfo = findViewById<WebView>(R.id.certificateInfo)
        val settings = certInfo.settings
        settings.defaultFontSize = 10
        settings.defaultFixedFontSize = 10
        settings.builtInZoomControls = true

        // android API-29 cannot handle character "#", so replaced it with "&sharp;"
        val certHtml = toString(certificate).replace("#", "&sharp;")
        certInfo.loadData(certHtml, "text/html", "utf-8")
    }

    /**
     * Creates a html String representation of the given object.
     *
     * @param certificate to print
     * @return the String representation
     */
    fun toString(certificate: Any?): String {
        val sb = StringBuilder()
        sb.append("<html><body>\n")
        if (certificate is X509Certificate) {
            renderX509(sb, certificate)
        } else {
            sb.append("<pre>\n")
            sb.append(certificate.toString())
            sb.append("</pre>\n")
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    /**
     * Appends an HTML representation of the given X509Certificate.
     *
     * @param sb StringBuilder to append to
     * @param certificate to print
     */
    private fun renderX509(sb: StringBuilder, certificate: X509Certificate) {
        var rdnNames: Map<String, String>
        val issuer = certificate.issuerX500Principal
        val subject = certificate.subjectX500Principal
        sb.append("<table cellspacing='1' cellpadding='1'>\n")

        // subject
        addTitle(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_ISSUED_TO))
        rdnNames = splitRdn(subject.name)
        if (!rdnNames.isEmpty()) {
            for ((nameType, value) in rdnNames) {
                val lblKey = "service_gui_CERT_INFO_$nameType"
                var lbl: String
                val resID = mContext!!.resources.getIdentifier(lblKey, "string", mContext!!.packageName)
                lbl = try {
                    mContext!!.getString(resID)
                } catch (e: Resources.NotFoundException) {
                    Timber.w("Unknown certificate subject label: %s", nameType)
                    nameType
                }
                if ("!$lblKey!" == lbl) lbl = nameType
                addField(sb, lbl, value)
            }
        } else {
            addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_CN), subject.name)
        }

        // issuer
        addTitle(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_ISSUED_BY))
        rdnNames = splitRdn(issuer.name)
        if (!rdnNames.isEmpty()) {
            for ((nameType, value) in rdnNames) {
                val lblKey = "service_gui_CERT_INFO_$nameType"
                var lbl: String
                val resID = mContext!!.resources.getIdentifier(lblKey, "string", mContext!!.packageName)
                lbl = try {
                    mContext!!.getString(resID)
                } catch (e: Resources.NotFoundException) {
                    Timber.w("Unknown certificate issuer label: %s", nameType)
                    nameType
                }
                if ("!$lblKey!" == lbl) lbl = nameType
                addField(sb, lbl, value)
            }
        } else {
            addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_CN), issuer.name)
        }

        // validity
        addTitle(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_VALIDITY))
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_ISSUED_ON), certificate.notBefore.toString())
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_EXPIRES_ON), certificate.notAfter.toString())
        addTitle(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_FINGERPRINTS))
        try {
            val sha256String = getThumbprint(certificate, "SHA-256")
            addField(sb, "SHA256:", sha256String, 48)
            val sha1String = getThumbprint(certificate, "SHA1")
            addField(sb, "SHA1:", sha1String, 48)
        } catch (e: CertificateException) {
            // do nothing as we cannot show this value
        }
        addTitle(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_CERT_DETAILS))
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_SER_NUM), certificate.serialNumber.toString())
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_VER), certificate.version.toString())
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_SIGN_ALG), certificate.sigAlgName.toString())
        addTitle(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_PUB_KEY_INFO))
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_ALG), certificate.publicKey.algorithm)
        if (certificate.publicKey.algorithm == "RSA") {
            val key = certificate.publicKey as RSAPublicKey
            addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_PUB_KEY),
                    mContext!!.getString(R.string.service_gui_CERT_INFO_KEY_BITS_PRINT, ((key.modulus.toByteArray().size - 1) * 8).toString()),
                    getHex(key.modulus.toByteArray()), 48)
            addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_EXP),
                    key.publicExponent.toString())
            addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_KEY_SIZE),
                    mContext!!.getString(R.string.service_gui_CERT_INFO_KEY_BITS_PRINT, key.modulus.bitLength().toString()))
        } else if (certificate.publicKey.algorithm == "DSA") {
            val key = certificate.publicKey as DSAPublicKey
            addField(sb, "Y:", key.y.toString(16))
        }
        addField(sb, mContext!!.getString(R.string.service_gui_CERT_INFO_SIGN),
                mContext!!.getString(R.string.service_gui_CERT_INFO_KEY_BITS_PRINT, (certificate.signature.size * 8).toString()),
                getHex(certificate.signature), 48)
        sb.append("</table>\n")
    }

    /**
     * Add a title.
     *
     * @param sb StringBuilder to append to
     * @param title to print
     */
    private fun addTitle(sb: StringBuilder, title: String) {
        sb.append("<tr><td colspan='2'")
                .append(" style='margin-top: 10pt; white-space: nowrap'><p><b>")
                .append(title).append("</b></p></td></tr>\n")
    }

    /**
     * Add a field.
     *
     * @param sb StringBuilder to append to
     * @param field name of the certificate field
     * @param value to print
     * @param wrap force-wrap after number of characters
     */
    private fun addField(sb: StringBuilder, field: String, value: String, wrap: Int) {
        addField(sb, field, value, null, wrap)
    }
    /**
     * Add a field.
     *
     * @param sb StringBuilder to append to
     * @param field name of the certificate field
     * @param value to print (not wrapped)
     * @param otherValue second line of value to print (wrapped)
     * @param wrap force-wrap after number of characters
     */
    /**
     * Add a field.
     *
     * @param sb StringBuilder to append to
     * @param field name of the certificate field
     * @param value to print
     */
    private fun addField(sb: StringBuilder, field: String, value: String, otherValue: String? = null, wrap: Int = 0) {
        // use &bull; instead of &#8226; as sdk-29+ webview cannot accept &#xxxx coding.
        var mValue = value
        sb.append("<tr><td style='margin-right: 25pt;")
                .append("white-space: nowrap' valign='top'>&bull; ")
                .append(field).append("</td><td><span")
        if (otherValue != null) {
            sb.append('>').append(mValue).append("</span><br/><span")
            mValue = otherValue
        }
        if (wrap > 0) {
            sb.append(" style='font-family:monospace'>")
            for (i in 0 until mValue.length) {
                if (i % wrap == 0 && i > 0) {
                    sb.append("<br/>")
                }
                sb.append(mValue[i])
            }
        } else {
            sb.append(">")
            sb.append(mValue)
        }
        sb.append("</span></td></tr>")
    }

    /**
     * Converts the byte array to hex string.
     *
     * @param raw the data.
     * @return the hex string.
     */
    private fun getHex(raw: ByteArray?): String? {
        if (raw == null) return null
        val hex = StringBuilder(2 * raw.size)
        Formatter(hex).use { f -> for (b in raw) f.format(BYTE_FORMAT, b) }
        return hex.substring(0, hex.length - 1)
    }

    companion object {
        private const val BYTE_FORMAT = "%02x:"

        /**
         * Calculates the hash of the certificate known as the "thumbprint" and returns it as a string representation.
         *
         * @param cert The certificate to hash.
         * @param algorithm The hash algorithm to use.
         * @return The SHA-1 hash of the certificate.
         * @throws CertificateException if exception
         */
        @Throws(CertificateException::class)
        private fun getThumbprint(cert: X509Certificate, algorithm: String): String {
            val digest = try {
                MessageDigest.getInstance(algorithm)
            } catch (e: NoSuchAlgorithmException) {
                throw CertificateException(e)
            }
            val encodedCert = cert.encoded
            val sb = StringBuilder(encodedCert.size * 2)
            Formatter(sb).use { f -> for (b in digest.digest(encodedCert)) f.format(BYTE_FORMAT, b) }
            return sb.substring(0, sb.length - 1)
        }

        /**
         * @param rdnNames attribute strings with "," as seperator
         * @return Map of keys and values for each attribute
         */
        private fun splitRdn(rdnNames: String): Map<String, String> {
            val rdn_pairs = LinkedHashMap<String, String>()
            val pairs = rdnNames.split(",")
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                if (idx > 0) rdn_pairs[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
            return rdn_pairs
        } /*
     * Construct a "simplified name" based on the subject DN from the
     * certificate. The purpose is to have something shorter to display in the
     * list. The name used is one of the following DN parts, if
     * available, otherwise the complete DN: 'CN', 'OU' or else 'O'.
     *
     * @param cert to read subject DN from
     * @return the simplified name
     */
        //    private static String getSimplifiedName(X509Certificate cert)
        //    {
        //        final HashMap<String, String> parts = new HashMap<>();
        //        try {
        //            for (Rdn name : new LdapName(cert.getSubjectX500Principal().getName()).getRdns()) {
        //                if (name.getType() != null && name.getValue() != null) {
        //                    parts.put(name.getType(), name.getValue().toString());
        //                }
        //            }
        //        } catch (InvalidNameException ex) // NOPMD
        //        {
        //            ex.printStackTrace();
        //        }
        //
        //        String result = parts.get("CN");
        //        if (result == null) {
        //            result = parts.get("OU");
        //        }
        //        if (result == null) {
        //            result = parts.get("O");
        //        }
        //        if (result == null) {
        //            result = cert.getSubjectX500Principal().getName();
        //        }
        //        return result;
        //    }
    }
}