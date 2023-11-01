package org.atalk.hmos.plugin.textspeech

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import net.java.sip.communicator.util.ConfigurationUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.persistance.FileBackend
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*

class TTSActivity : OSGiActivity(), OnInitListener, View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private var permissionCount = 0
    private val mUtteranceID = "totts"
    private var ttsDelay: String? = null
    private lateinit var mTtsText: TextView
    private lateinit var mTtsLocale: TextView
    private lateinit var mTtsDelay: EditText
    private lateinit var btnPlay: Button
    private lateinit var btnSave: Button
    lateinit var cbTts: CheckBox
    private var mTTS: TextToSpeech? = null
    private var requestCode = REQUEST_DEFAULT

    enum class State {
        LOADING, DOWNLOAD_FAILED, ERROR, SUCCESS, UNKNOWN
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tts_main)
        setMainTitle(R.string.service_gui_TTS_SETTINGS)
        mTtsText = findViewById(R.id.tts_text)
        mTtsText.addTextChangedListener(mTextWatcher)
        cbTts = findViewById(R.id.tts_enable)
        cbTts.isChecked = ConfigurationUtils.isTtsEnable()
        cbTts.setOnCheckedChangeListener(this)
        mTtsLocale = findViewById(R.id.tts_locale)
        mTtsDelay = findViewById(R.id.tts_delay)
        ttsDelay = ConfigurationUtils.getTtsDelay().toString()
        mTtsDelay.setText(ttsDelay)
        mTtsDelay.addTextChangedListener(mTextWatcher)

        // Use standard ActivityResultContract instead (both methods work)
        // ActivityResultLauncher<String> mGetTTSInfo = getTTSInfo();
        findViewById<View>(R.id.tts_setting).setOnClickListener { view: View? ->
            requestCode = REQUEST_DEFAULT
            // mGetTTSInfo.launch(ACTION_TTS_SETTINGS);
            mStartForResult.launch(Intent(ACTION_TTS_SETTINGS))
        }
        btnPlay = findViewById(R.id.tts_play)
        btnPlay.setOnClickListener(this)
        btnPlay.isEnabled = false
        btnSave = findViewById(R.id.tts_save)
        btnSave.setOnClickListener(this)
        btnSave.isEnabled = false
        val btnOK = findViewById<Button>(R.id.tts_ok)
        btnOK.setOnClickListener(this)
        initButton()

        // Perform the dynamic permission request
        aTalk.hasWriteStoragePermission(this, true)
        setState(State.LOADING)

        /*
         * Device without TTS engine will cause aTalk to crash; Check to see if we have TTS voice data
         * Launcher the voice data verifier.
         */
        try {
            requestCode = ACT_CHECK_TTS_DATA
            // mGetTTSInfo.launch(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            mStartForResult.launch(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
        } catch (ex: ActivityNotFoundException) {
            aTalkApp.showToastMessage(ex.message)
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        val tmp = ViewUtil.toString(mTtsDelay)
        if (tmp != null && ttsDelay != tmp) {
            ConfigurationUtils.setTtsDelay(tmp.toInt())
        }
    }

    private val ttsLanguage: Locale?
        get() {
            if (mTTS != null) {
                try {
                    val voice = mTTS!!.voice
                    if (voice != null) {
                        return voice.locale
                    }
                } catch (e: Exception) {
                    cbTts.isEnabled = false
                    val errMsg = "TTS get voice exception: " + e.message
                    mTtsLocale.text = errMsg
                    Timber.e(errMsg)
                }
            }
            return null
        }

    /**
     * Handles the result of TTS engine initialization. Either displays an error
     * dialog or populates the activity's UI.
     *
     * @param status The TTS engine initialization status.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setState(State.SUCCESS)
            val language = ttsLanguage
            if (language != null) {
                mTtsLocale.text = language.displayName
                val result = mTTS!!.setLanguage(language)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    aTalkApp.showToastMessage("TTS language is not supported")
                    setState(State.ERROR)
                } else {
                    Timber.i("Text to Speech is ready with status: %s", status)
                    // mTtsText.setText("Text to Speech is ready");
                    // btnPlay.performClick();
                }
            } else {
                setState(State.ERROR)
            }
        } else {
            setState(State.ERROR)
            Timber.e("Initialization failed (status: %s)", status)
        }
    }

    override fun onClick(v: View) {
        val ttsText = ViewUtil.toString(mTtsText)
        when (v.id) {
            R.id.tts_play -> if (ttsText != null) {
                val spkIntent = Intent(this, TTSService::class.java)
                spkIntent.putExtra(TTSService.EXTRA_MESSAGE, ttsText)
                spkIntent.putExtra(TTSService.EXTRA_QMODE, false)
                startService(spkIntent)
            }
            R.id.tts_save -> ttsText?.let { saveToAudioFile(it) }
            R.id.tts_ok -> finish()
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView.id == R.id.tts_enable) {
            ConfigurationUtils.setTtsEnable(isChecked)
        }
    }
    /* Use standard ActivityResultContract instead */ //    /**
    //     * GetTTSInfo class ActivityResultContract implementation.
    //     */
    //    public class GetTTSInfo extends ActivityResultContract<String, Integer>
    //    {
    //        @NonNull
    //        @Override
    //        public Intent createIntent(@NonNull Context context, @NonNull String action)
    //        {
    //            return new Intent(action);
    //        }
    //
    //        @Override
    //        public Integer parseResult(int resultCode, @Nullable Intent result)
    //        {
    //            if (((REQUEST_DEFAULT == requestCode) && resultCode != Activity.RESULT_OK || result == null)) {
    //                return null;
    //            }
    //            return resultCode;
    //        }
    //    }
    //
    //    /**
    //     * Handler for Activity Result callback
    //     */
    //    private ActivityResultLauncher<String> getTTSInfo()
    //    {
    //        return registerForActivityResult(new GetTTSInfo(), resultCode -> {
    //            switch (requestCode) {
    //                case ACT_CHECK_TTS_DATA:
    //                    onDataChecked(resultCode);
    //                    break;
    //
    //                case REQUEST_DEFAULT:
    //                    initializeEngine();
    //                    break;
    //            }
    //        });
    //    }
    /**
     * standard ActivityResultContract#StartActivityForResult
     */
    private var mStartForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val resultCode = result.resultCode
        when (requestCode) {
            ACT_CHECK_TTS_DATA -> onDataChecked(resultCode)
            REQUEST_DEFAULT -> if (resultCode == RESULT_OK) {
                initializeEngine()
            }
        }
    }

    /**
     * Handles the result of voice data verification. If verification fails
     * following a successful installation, displays an error dialog. Otherwise,
     * either launches the installer or attempts to initialize the TTS engine.
     *
     * @param resultCode The result of voice data verification.
     */
    private fun onDataChecked(resultCode: Int) {
        if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
//            Intent intent = new Intent(this, TTSService.class);
//            startService(intent);
            // Data exists, so we instantiate the TTS engine
            initializeEngine()
        } else {
            Timber.e("Voice data check failed (error code: %s", resultCode)
            setState(State.ERROR)

            // Data is missing, so we start the TTS installation process
            val installIntent = Intent()
            installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            startActivity(installIntent)
        }
    }

    /**
     * Initializes the TTS engine.
     */
    private fun initializeEngine() {
        if (mTTS != null) mTTS = null
        mTTS = TextToSpeech(this, this)
    }

    /**
     * Sets the UI state.
     *
     * @param state The current state.
     */
    private fun setState(state: State) {
        Companion.state = state
        if (Companion.state == State.LOADING) {
            findViewById<View>(R.id.loading).visibility = View.VISIBLE
            findViewById<View>(R.id.success).visibility = View.GONE
        } else {
            findViewById<View>(R.id.loading).visibility = View.GONE
            findViewById<View>(R.id.success).visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == aTalk.PRC_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && PackageManager.PERMISSION_GRANTED == grantResults[0]) permissionCount++
        }
    }

    private var mTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            initButton()
        }

        override fun afterTextChanged(s: Editable) {
            initButton()
        }
    }

    private fun initButton() {
        val enable = State.SUCCESS == state && mTtsText.text.isNotEmpty()
        btnPlay.isEnabled = enable
        btnSave.isEnabled = enable
        val alpha = if (enable) 1.0f else 0.5f
        btnPlay.alpha = alpha
        btnSave.alpha = alpha
    }

    private fun saveToAudioFile(text: String) {
        // Create tts audio file
        val ttsFile = createTtsSpeechFile() ?: return
        val audioFilename = ttsFile.absolutePath
        mTTS!!.synthesizeToFile(text, null, File(audioFilename), mUtteranceID)
        mTTS!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) {
                if (utteranceId == mUtteranceID) {
                    aTalkApp.showToastMessage("Saved to $audioFilename")
                }
            }

            override fun onError(utteranceId: String) {}
        })
    }

    override fun onDestroy() {
        if (mTTS != null) {
            mTTS!!.stop()
            mTTS!!.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
        private const val ACT_CHECK_TTS_DATA = 1001
        private const val REQUEST_DEFAULT = 1003
        var state = State.UNKNOWN
            private set

        /**
         * Create the audio file if it does not exist
         *
         * @return Voice file for saving audio
         */
        private fun createTtsSpeechFile(): File? {
            var ttsFile: File? = null
            val mediaDir = FileBackend.getaTalkStore(FileBackend.MEDIA_VOICE_SEND, true)
            if (!mediaDir.exists() && !mediaDir.mkdirs()) {
                Timber.d("Fail to create Media voice directory!")
                return null
            }
            try {
                ttsFile = File.createTempFile("tts_", ".wav", mediaDir)
            } catch (e: IOException) {
                Timber.d("Fail to create Media voice file!")
            }
            return ttsFile
        }
    }
}