/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import org.atalk.hmos.R
import org.atalk.hmos.gui.actionbar.ActionBarToggleFragment
import org.atalk.impl.neomedia.NeomediaActivator
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.MediaType
import java.util.*

/**
 * Activity allows user to edit audio or video encodings settings and set their priority.
 * The intent starting this activity must be parametrized with:<br></br>
 * - [.ENC_MEDIA_TYPE_KEY] with [MediaType] which specifies if audio or video encoding will be edited<br></br>
 * - [.EXTRA_KEY_ENC_REG] with [EncodingsRegistrationUtil] instance which  encoding properties<br></br>
 * <br></br>
 * After activity finishes it's job it return in [Intent] the
 * [EncodingsRegistrationUtil] under its key and additional `boolean` flag
 * indicating whether any changes has been made under key: [.EXTRA_KEY_HAS_CHANGES].
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class MediaEncodingActivity : OSGiActivity(), ActionBarToggleFragment.ActionBarToggleModel {
    /**
     * Holds the properties we need to get/set for the encoding preferences
     */
    private val encodingProperties = HashMap<String, String>()

    /**
     * The encoding configuration object used to manipulate on the properties
     */
    private lateinit var encodingConfiguration: EncodingConfiguration

    /**
     * Flag storing info if the global settings are overridden
     */
    private var isOverrideEncodings = false

    /**
     * The [EncodingsRegistrationUtil] object that stores encoding and their priorities
     */
    private var mEncReg: EncodingsRegistrationUtil? = null

    /**
     * Flag indicating whether any changes has been made to the configuration
     */
    private var hasChanges = false

    /**
     * The encodings edit list fragment
     */
    private lateinit var encodingsFragment: MediaEncodingsFragment

    /**
     * Audio or video media type that is currently used.
     */
    private lateinit var mediaType: MediaType

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadEncodings(savedInstanceState)
        if (savedInstanceState == null) {
            val toggleFragment = ActionBarToggleFragment.newInstance(getString(R.string.service_gui_ENC_OVERRIDE_GLOBAL))
            supportFragmentManager.beginTransaction()
                    .add(toggleFragment, "action_bar_toggle")
                    .commit()
        }
    }

    /**
     * Loads properties passed by intent's extras and initializes the activity.
     *
     * @param savedInstanceState bundle that contains the state or `null` if the `Activity` was just
     * created.
     */
    private fun loadEncodings(savedInstanceState: Bundle?) {
        //val intent: Intent = intent
        if (savedInstanceState == null) {
            mEncReg = intent.getSerializableExtra(EXTRA_KEY_ENC_REG) as EncodingsRegistrationUtil
        } else {
            mEncReg = savedInstanceState.getSerializable(STATE_ENC_REG) as EncodingsRegistrationUtil
            hasChanges = savedInstanceState.getBoolean(STATE_HAS_CHANGES)
        }
        isOverrideEncodings = mEncReg!!.isOverrideEncodings()

        val encodingProperties = mEncReg!!.getEncodingProperties()
        val mediaServiceImpl = NeomediaActivator.getMediaServiceImpl()
        if (mediaServiceImpl != null) {
            encodingConfiguration = mediaServiceImpl.createEmptyEncodingConfiguration()
            encodingConfiguration.loadProperties(encodingProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX)
            encodingConfiguration.storeProperties(encodingProperties,
                    ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".")
            mediaType = intent.getSerializableExtra(ENC_MEDIA_TYPE_KEY) as MediaType

            if (savedInstanceState == null) {
                val encodings = getEncodings(encodingConfiguration, mediaType)
                val priorities = getPriorities(encodings, encodingConfiguration)
                val encodingsStrs = getEncodingsStr(encodings.iterator())
                encodingsFragment = MediaEncodingsFragment.newInstance(encodingsStrs, priorities)
                supportFragmentManager.beginTransaction().replace(android.R.id.content, encodingsFragment).commit()
            } else {
                encodingsFragment = supportFragmentManager.findFragmentById(android.R.id.content) as MediaEncodingsFragment
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        commitChanges()
        outState.putSerializable(STATE_ENC_REG, mEncReg)
        outState.putBoolean(STATE_HAS_CHANGES, hasChanges)
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        encodingsFragment.setEnabled(isOverrideEncodings)
        return true
    }

    /**
     * Catches the back key and returns edited state in `Intent` extra. <br></br>
     * {@inheritDoc}
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Catch the back key code and store results
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            commitChanges()
            val result = Intent()
            result.putExtra(EXTRA_KEY_ENC_REG, mEncReg)
            hasChanges = hasChanges || encodingsFragment.hasChanges()
            result.putExtra(EXTRA_KEY_HAS_CHANGES, hasChanges)
            setResult(Activity.RESULT_OK, result)
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Commits user changes.
     */
    private fun commitChanges() {
        commitPriorities(encodingConfiguration, mediaType, encodingsFragment)
        encodingConfiguration.storeProperties(encodingProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".")
        mEncReg!!.setOverrideEncodings(isOverrideEncodings)
        mEncReg!!.setEncodingProperties(encodingProperties)
    }

    override var isChecked: Boolean
        get() = isOverrideEncodings
        set(isChecked) {
            isOverrideEncodings = isChecked
            encodingsFragment.setEnabled(isOverrideEncodings)
            hasChanges = true
        }

    companion object {
        /**
         * The intent's key for [MediaType]
         */
        const val ENC_MEDIA_TYPE_KEY = "media_type"

        /**
         * The intent's key for [EncodingsRegistrationUtil]
         */
        const val EXTRA_KEY_ENC_REG = "encRegObj"

        /**
         * The intent's key for flag indicating whether any changes has been made
         */
        const val EXTRA_KEY_HAS_CHANGES = "encHasChanges"

        /**
         * State key for encodings registration object.
         */
        private const val STATE_ENC_REG = "state_enc_reg"

        /**
         * State key for "has changes" flag.
         */
        private const val STATE_HAS_CHANGES = "state_has_changes"

        /**
         * Returns the string representing encoding on the list
         *
         * @param mf [MediaFormat] for encoding list row
         * @return the string representing encoding list item
         */
        private fun getEncodingStr(mf: MediaFormat): String {
            return mf.encoding + "/" + mf.clockRateString
        }

        /**
         * Initializes encodings list
         */
        fun getEncodings(encodingConfig: EncodingConfiguration, mediaType: MediaType): MutableList<MediaFormat?> {
            var availableEncodings = encodingConfig.getAllEncodings(mediaType)
            val availableEncodingSet = HashMap<String, MediaFormat>()
            for (availableEncoding in availableEncodings) {
                availableEncodingSet[availableEncoding.encoding + "/"
                        + availableEncoding.clockRateString] = availableEncoding
            }
            availableEncodings = availableEncodingSet.values.toTypedArray()

            val encodingCount = availableEncodings.size
            val encodings = arrayOfNulls<MediaFormat>(encodingCount)
            System.arraycopy(availableEncodings, 0, encodings, 0, encodingCount)

            // Display the encodings in decreasing priority.
            Arrays.sort(encodings, 0, encodingCount) { format0: MediaFormat?, format1: MediaFormat? ->
                var ret = encodingConfig.getPriority(format1!!) - encodingConfig.getPriority(format0!!)
                if (ret == 0) {
                    ret = format0.encoding.compareTo(format1.encoding, ignoreCase = true)
                    if (ret == 0) {
                        ret = format1.clockRate.compareTo(format0.clockRate)
                    }
                }
                ret
            }
            val outList = ArrayList<MediaFormat?>(encodings.size)
            Collections.addAll(outList, *encodings)
            return outList
        }

        /**
         * Creates string representation for given set of `MediaFormat`s.
         *
         * @param encodings the iterator with `MediaFormat` to be converted into strings.
         * @return string representation of given `MediaFormat`s.
         */
        fun getEncodingsStr(encodings: MutableIterator<MediaFormat?>): List<String> {
            val outList = ArrayList<String>()
            while (encodings.hasNext()) {
                outList.add(getEncodingStr(encodings.next()!!))
            }
            return outList
        }

        /**
         * Select `MediaFormat` by string representation created using [.getEncodingStr].
         *
         * @param encodings set of `MediaFormat`s from which the one will be selected.
         * @param str string representation of `MediaFormat`.
         * @return selected `MediaFormat` from the given set by matching string representation.
         */
        private fun getEncodingFromStr(encodings: MutableIterator<MediaFormat?>, str: String?): MediaFormat {
            while (encodings.hasNext()) {
                val mf = encodings.next()
                if (getEncodingStr(mf!!) == str) {
                    return mf
                }
            }
            throw IllegalArgumentException("Invalid format str: $str")
        }

        /**
         * Gets the priorities list for given set of encodings and encodings configuration.
         *
         * @param encodings the set of encodings that will be used.
         * @param encodingConfig encodings configuration that stores priorities.
         * @return priorities list for given set of encodings.
         */
        fun getPriorities(
                encodings: MutableList<MediaFormat?>,
                encodingConfig: EncodingConfiguration,
        ): List<Int> {
            val count = encodings.size
            val priorities = ArrayList<Int>(count)
            for (i in 0 until count) {
                val current = encodingConfig.getPriority(encodings[i]!!)
                val orderPriority = MediaEncodingsFragment.calcPriority(encodings, i)
                priorities.add(if (current > 0) orderPriority else 0)
            }
            return priorities
        }

        /**
         * Commits priorities edited by the `EncodingsFragment` into given
         * `EncodingConfiguration`.
         *
         * @param encodingConf configuration that will store encodings priorities.
         * @param mediaType audio or video media type that was edited.
         * @param encFragment the fragment which edited encodings priorities.
         */
        fun commitPriorities(
                encodingConf: EncodingConfiguration, mediaType: MediaType,
                encFragment: MediaEncodingsFragment,
        ) {
            if (!encFragment.hasChanges()) {
                return
            }
            val formats = getEncodings(encodingConf, mediaType)
            val encodings = encFragment.encodings
            val priorities = encFragment.priorities
            for (i in encodings!!.indices) {
                encodingConf.setPriority(getEncodingFromStr(formats.iterator(), encodings[i]), priorities!![i])
            }
        }
    }
}