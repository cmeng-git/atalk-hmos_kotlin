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
package org.atalk.hmos.gui.chat.conference

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.chat.ChatMessage
import org.atalk.hmos.gui.util.ViewUtil
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.StanzaCollector
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smack.filter.StanzaIdFilter
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.packet.Message
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smackx.bob.element.BoBDataExtension
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension
import org.jivesoftware.smackx.captcha.packet.CaptchaIQ
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.TextSingleFormField
import org.jivesoftware.smackx.xdata.TextSingleFormField.*
import org.jivesoftware.smackx.xdata.packet.DataForm
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL

/**
 * The dialog pops up when the user joining chat room receive a normal message containing
 * captcha challenge for spam protection
 *
 * @author Eng Chong Meng
 */
class CaptchaDialog(private val mContext: Context, multiUserChat: MultiUserChat, message: Message, listener: CaptchaDialogListener) : Dialog(mContext) {
    private var mCaptchaText: EditText? = null
    private var mReason: TextView? = null
    private var mImageView: ImageView? = null
    private var mAcceptButton: Button? = null
    private var mCancelButton: Button? = null
    private var mOKButton: Button? = null
    private var mCaptcha: Bitmap? = null
    private var mDataForm: DataForm? = null
    private var formBuilder: DataForm.Builder? = null
    private var mReasonText: String? = null
    private val callBack: CaptchaDialogListener

    interface CaptchaDialogListener {
        fun onResult(state: Int)
        fun addMessage(msg: String, msgType: Int)
    }

    init {
        mConnection = multiUserChat.xmppConnection
        mMessage = message
        callBack = listener
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.captcha_challenge)
        setTitle(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_CHALLENGE)
        mImageView = findViewById(R.id.captcha)
        mCaptchaText = findViewById(R.id.input)
        mReason = findViewById(R.id.reason_field)

        // initial visibility states in xml
        mAcceptButton = findViewById(R.id.button_accept)
        mOKButton = findViewById(R.id.button_ok)
        mCancelButton = findViewById(R.id.button_cancel)
        if (initCaptchaData()) {
            showCaptchaContent()
            initializeViewListeners()
        }
        setCancelable(false)
    }

    private fun closeDialog() {
        cancel()
    }

    /*
     * Update dialog content with the received captcha information for form presentation.
     */
    private fun showCaptchaContent() {
        // Scale the captcha to the display resolution
        val metrics = mContext.resources.displayMetrics
        val captcha = Bitmap.createScaledBitmap(mCaptcha!!, (mCaptcha!!.width * metrics.scaledDensity).toInt(), (mCaptcha!!.height * metrics.scaledDensity).toInt(), false)
        mImageView!!.setImageBitmap(captcha)
        val bodyExt = mMessage.getExtension(Message.Body::class.java)
        mReasonText = if (bodyExt != null) bodyExt.message else mMessage.body
        mReason!!.text = mReasonText
        mCaptchaText!!.requestFocus()
    }

    /**
     * Setup all the dialog buttons. listeners for the required actions on user click
     */
    private fun initializeViewListeners() {
        mImageView!!.setOnClickListener { mCaptchaText!!.requestFocus() }
        mAcceptButton!!.setOnClickListener {
            if (TextUtils.isEmpty(ViewUtil.toString(mCaptchaText))) {
                aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_TEXT_EMPTY)
            } else {
                showResult(onAcceptClicked(false))
            }
        }

        // force terminate smack wait loop early by sending empty reply
        mCancelButton!!.setOnClickListener { showResult(onAcceptClicked(true)) }
        mOKButton!!.setOnClickListener { closeDialog() }
    }

    /**
     * Handles the `ActionEvent` triggered when one user clicks on the Submit button.
     * Reply with the following Captcha IQ
     * <iq type='set' from='robot@abuser.com/zombie' to='victim.com' xml:lang='en' id='z140r0s'>
     * <captcha xmlns='urn:xmpp:captcha'>
     * <x xmlns='jabber:x:data' type='submit'>
     * <field var='FORM_TYPE'><value>urn:xmpp:captcha</value></field>
     * <field var='from'><value>innocent@victim.com</value></field>
     * * <field var='challenge'><value>F3A6292C</value></field>
     * <field var='sid'><value>spam1</value></field>
     * <field var='ocr'><value>7nHL3</value></field>
    </x> *
    </captcha> *
    </iq> *
     *
     * @param isCancel true is user cancel; send empty reply and callback with cancel
     *
     * @return the captcha reply result success or failure
     */
    private fun onAcceptClicked(isCancel: Boolean): Boolean {
        formBuilder = DataForm.builder(DataForm.Type.submit)
                .addField(mDataForm!!.getField(FormField.FORM_TYPE))
                .addField(mDataForm!!.getField(CaptchaExtension.FROM))
                .addField(mDataForm!!.getField(CaptchaExtension.CHALLENGE))
                .addField(mDataForm!!.getField(CaptchaExtension.SID))

        // Only localPart is required
        val userName = mMessage.to.toString()
        addFormField(CaptchaExtension.USER_NAME, userName)
        val rc = mCaptchaText!!.text
        if (rc != null) {
            addFormField(CaptchaExtension.OCR, rc.toString())
        }

        /*
         * Must immediately inform caller before sending reply; otherwise may have race condition
         * i.e. <presence/> exception get process before mCaptchaState is update
         */
        if (isCancel) callBack.onResult(cancel)
        val iqCaptcha = CaptchaIQ(formBuilder!!.build())
        iqCaptcha.type = IQ.Type.set
        iqCaptcha.to = mMessage.from
        try {
            createStanzaCollectorAndSend(iqCaptcha).nextResultOrThrow<Stanza>()
            callBack.onResult(validated)
            mReasonText = mContext.getString(R.string.service_gui_CHATROOM_JOIN_CAPTCHA_VERIFICATION_VALID)
            callBack.addMessage(mReasonText!!, ChatMessage.MESSAGE_SYSTEM)
            return true
        } catch (ex: SmackException.NoResponseException) {

            // Not required. The return error message will contain the descriptive text
            // if (ex instanceof XMPPException.XMPPErrorException) {
            //    StanzaError xmppError = ((XMPPException.XMPPErrorException) ex).getStanzaError();
            //    errMsg += "\n: " + xmppError.getDescriptiveText();
            // }
            mReasonText = ex.message
            if (isCancel) {
                callBack.addMessage(mReasonText!!, ChatMessage.MESSAGE_ERROR)
            } else {
                // caller will retry, so do not show error.
                callBack.onResult(failed)
            }
            Timber.e("Captcha Exception: %s => %s", isCancel, mReasonText)
        } catch (ex: XMPPException.XMPPErrorException) {
            mReasonText = ex.message
            if (isCancel)  {
                callBack.addMessage(mReasonText!!, ChatMessage.MESSAGE_ERROR)
            } else {
                callBack.onResult(failed)
            }
            Timber.e("Captcha Exception: %s => %s", isCancel, mReasonText)
        } catch (ex: SmackException.NotConnectedException) {
            mReasonText = ex.message
            if (isCancel) {
                callBack.addMessage(mReasonText!!, ChatMessage.MESSAGE_ERROR)
            } else {
                callBack.onResult(failed)
            }
            Timber.e("Captcha Exception: %s => %s", isCancel, mReasonText)
        } catch (ex: InterruptedException) {
            mReasonText = ex.message
            if (isCancel) {
                callBack.addMessage(mReasonText!!, ChatMessage.MESSAGE_ERROR)
            } else {
                callBack.onResult(failed)
            }
            Timber.e("Captcha Exception: %s => %s", isCancel, mReasonText)
        }
        return false
    }

    /**
     * Add field / value to formBuilder for registration
     *
     * @param name the FormField variable
     * @param value the FormField value
     */
    private fun addFormField(name: String, value: String) {
        val field = FormField.builder(name)
        field.setValue(value)
        formBuilder!!.addField(field.build())
    }

    /*
     * set Captcha IQ and receive reply
     */
    @Throws(SmackException.NotConnectedException::class, InterruptedException::class)
    private fun createStanzaCollectorAndSend(req: IQ): StanzaCollector {
        return mConnection.createStanzaCollectorAndSend(StanzaIdFilter(req.stanzaId), req)
    }

    /**
     * Perform the InBand Registration for the accountId on the defined XMPP connection by pps.
     * Registration can either be:
     * - simple username and password or
     * - With captcha protection using form with embedded captcha image if available, else the
     * image is retrieved from the given url in the form.
     */
    private fun initCaptchaData(): Boolean {
        try {
            // do not proceed if dataForm is null
            val captchaExt = mMessage.getExtension(CaptchaExtension::class.java)
            val dataForm = captchaExt.dataForm
            if (dataForm == null) {
                callBack.onResult(failed)
                return false
            }
            var bmCaptcha: Bitmap? = null
            val bob = mMessage.getExtension(BoBDataExtension::class.java)
            if (bob != null) {
                val bytData = bob.bobData.content
                val stream = ByteArrayInputStream(bytData)
                bmCaptcha = BitmapFactory.decodeStream(stream)
            } else {
                /*
                 * <field var='ocr' label='Enter the text you see'>
                 *   <media xmlns='urn:xmpp:media-element' height='80' width='290'>
                 *     <uri type='image/jpeg'>http://www.victim.com/challenges/ocr.jpeg?F3A6292C</uri>
                 *     <uri type='image/jpeg'>cid:sha1+f24030b8d91d233bac14777be5ab531ca3b9f102@bob.xmpp.org</uri>
                 *   </media>
                 * </field>
                 */
                // not working - smack does not support get media element embedded in ocr field data
                // FormField ocrField = dataForm.getField("ocr");
                // String mediaElement = ocrField.getDescription();
                val urlField = dataForm.getField("ocr")
                if (urlField != null) {
                    val urlString: String = urlField.getFirstValue()
                    if (urlString.contains("http://")) {
                        val uri = URL(urlString)
                        bmCaptcha = BitmapFactory.decodeStream(uri.openConnection().getInputStream())
                    }
                }
            }
            mDataForm = dataForm
            mCaptcha = bmCaptcha

            // use web link for captcha challenge to user if null
            if (bmCaptcha == null) callBack.onResult(failed) else callBack.onResult(awaiting)
            return true
        } catch (e: IOException) {
            mReasonText = e.message
            callBack.onResult(failed)
            showResult(false)
        }
        return false
    }

    /**
     * Shows IBR registration result.
     *
     * @param success Captcha reply return result
     */
    private fun showResult(success: Boolean) {
        if (success) {
            mReason!!.text = mReasonText
            mCaptchaText!!.setEnabled(false)
            mAcceptButton!!.visibility = View.GONE
            mCancelButton!!.visibility = View.GONE
            mOKButton!!.visibility = View.VISIBLE
        } else {
            closeDialog()
        }
    }

    companion object {
        /* Captcha response state */
        const val unknown = -1
        const val validated = 0
        const val awaiting = 1
        const val failed = 2
        const val cancel = 3
        private lateinit var mConnection: XMPPConnection
        private lateinit var mMessage: Message
    }
}