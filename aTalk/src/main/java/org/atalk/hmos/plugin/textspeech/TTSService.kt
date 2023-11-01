package org.atalk.hmos.plugin.textspeech

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.text.TextUtils
import org.atalk.hmos.aTalkApp

class TTSService : Service(), OnInitListener {
    private var mTTS: TextToSpeech? = null
    private var isInit = false
    private var handler: Handler? = null
    private var message: String? = "Text to speech is ready"
    private var qMode = true
    override fun onCreate() {
        super.onCreate()
        handler = Handler()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        handler!!.removeCallbacksAndMessages(null)
        message = intent.getStringExtra(EXTRA_MESSAGE)
        qMode = intent.getBooleanExtra(EXTRA_QMODE, true)
        if (mTTS == null) {
            mTTS = TextToSpeech(applicationContext, this)
        } else if (isInit) {
            speak(message, qMode)
        }

        // Hold the tts for 60 minutes min before release the resource
        handler!!.postDelayed({ this.stopSelf() }, (60 * 60 * 1000).toLong())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (mTTS != null) {
            mTTS!!.stop()
            mTTS!!.shutdown()
        }
        super.onDestroy()
    }

    /**
     * Called to signal the completion of the TextToSpeech engine initialization.
     *
     * @param status [TextToSpeech.SUCCESS] or [TextToSpeech.ERROR].
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            if (mTTS != null) {
                val language = mTTS!!.defaultVoice.locale
                if (language != null) {
                    val result = mTTS!!.setLanguage(language)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        aTalkApp.showToastMessage("TTS language is not supported")
                    } else {
                        speak(message, qMode)
                        isInit = true
                    }
                }
            } else {
                aTalkApp.showToastMessage("TTS initialization failed")
            }
        }
    }

    fun speak(text: String?, qMode: Boolean) {
        var text = text
        var qMode = qMode
        if (mTTS != null && !TextUtils.isEmpty(text)) {
            text = text!!.replace("<.*?>".toRegex(), "")
            val messages = splitEqually(text)
            qMode = messages.size > 1 || qMode
            for (segmentText in messages) {
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, segmentText)
                if (qMode) mTTS!!.speak(segmentText, TextToSpeech.QUEUE_ADD, params, null) else mTTS!!.speak(segmentText, TextToSpeech.QUEUE_FLUSH, params, null)
            }
        }
    }

    override fun onBind(arg0: Intent): IBinder? {
        return null
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_QMODE = "qmode"

        /**
         * Split the text to speak into List each of less than TextToSpeech.getMaxSpeechInputLength()
         *
         * @param text speak text string
         * @return Split text in List<String>
        </String> */
        fun splitEqually(text: String): List<String> {
            val size = TextToSpeech.getMaxSpeechInputLength() - 1

            // Give the list the right capacity to start with.
            val ret = ArrayList<String>((text.length + size - 1) / size)
            var start = 0
            while (start < text.length) {
                ret.add(text.substring(start, Math.min(text.length, start + size)))
                start += size
            }
            return ret
        }
    }
}