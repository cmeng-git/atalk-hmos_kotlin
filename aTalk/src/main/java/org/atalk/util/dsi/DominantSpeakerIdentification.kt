/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atalk.util.dsi

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.util.concurrent.ExecutorUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.beans.PropertyChangeListener
import java.lang.ref.WeakReference
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Implements [ActiveSpeakerDetector] with inspiration from the paper &quot;Dominant Speaker
 * Identification for Multipoint Videoconferencing&quot; by Ilana Volfin and Israel Cohen.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class DominantSpeakerIdentification
/**
 * Initializes a new `DominantSpeakerIdentification instance.
` */
    : AbstractActiveSpeakerDetector() {
    /**
     * The background thread which repeatedly makes the (global) decision about speaker switches.
     */
    private var decisionMaker: DecisionMaker? = null

    /**
     * The synchronization source identifier/SSRC of the dominant speaker in this multipoint
     * conference.
     */
    private var dominantSSRC: Long? = null

    /**
     * The last/latest time at which this `DominantSpeakerIdentification` made a (global)
     * decision about speaker switches. The (global) decision about switcher switches should be
     * made every [.DECISION_INTERVAL] milliseconds.
     */
    private var lastDecisionTime: Long = 0

    /**
     * The time in milliseconds of the most recent (audio) level report or measurement (regardless
     * of the `Speaker`).
     */
    private var lastLevelChangedTime: Long = 0

    /**
     * The last/latest time at which this `DominantSpeakerIdentification` notified the
     * `Speaker`s who have not received or measured audio levels for a certain time (i.e.
     * [.LEVEL_IDLE_TIMEOUT]) that they will very likely not have a level within a certain
     * time-frame of the algorithm.
     */
    private var lastLevelIdleTime: Long = 0

    /**
     * The `PropertyChangeNotifier` which facilitates the implementations of adding and
     * removing `PropertyChangeListener`s to and from this instance and firing
     * `PropertyChangeEvent`s to the added `PropertyChangeListener`s.
     */
    private val propertyChangeNotifier = PropertyChangeNotifier()

    /**
     * The relative speech activities for the immediate, medium and long time-intervals,
     * respectively, which were last calculated for a `Speaker`. Simply reduces the
     * number of allocations and the penalizing effects of the garbage collector.
     */
    private val relativeSpeechActivities = DoubleArray(3)

    /**
     * The `Speaker`s in the multipoint conference associated with this
     * `ActiveSpeakerDetector`.
     */
    private val speakers = HashMap<Long, Speaker>()

//    /**
//     * Adds a `PropertyChangeListener` to the list of listeners interested in and notified
//     * about changes in the values of the properties of this `DominantSpeakerIdentification`.
//     *
//     * @param listener a `PropertyChangeListener` to be notified about changes in the values of the
//     * properties of this `DominantSpeakerIdentification`
//     */
//    fun addPropertyChangeListener(listener: PropertyChangeListener) {
//        propertyChangeNotifier.addPropertyChangeListener(listener)
//    }

    /**
     * Notifies this `DominantSpeakerIdentification` instance that a specific
     * `DecisionMaker` has permanently stopped executing (in its background/daemon
     * `Thread`). If the specified `decisionMaker` is the one utilized by this
     * `DominantSpeakerIdentification` instance, the latter will update its state to reflect
     * that the former has exited.
     *
     * @param decisionMaker the `DecisionMaker` which has exited
     */
    @Synchronized
    fun decisionMakerExited(decisionMaker: DecisionMaker) {
        if (this.decisionMaker === decisionMaker) this.decisionMaker = null
    }

    /**
     * Retrieves a JSON representation of this instance for the purposes of the REST API of Videobridge.
     *
     * By the way, the method name reflects the fact that the method handles an HTTP GET request.
     *
     * @return a `JSONObject` which represents this instance of the purposes of the REST API of Videobridge
     */
    fun doGetJSON(): JSONObject? {
        var jsonObject: JSONObject?
        if (TimberLog.isTraceEnable) {
            synchronized(this) {
                jsonObject = JSONObject()

                // dominantSpeaker
                val dominantSpeaker = dominantSpeaker
                val any = try {
                    jsonObject!!.put("dominantSpeaker", if (dominantSpeaker == -1L) null else dominantSpeaker)
                    // speakers
                    val speakersCollection: Collection<Speaker> = speakers.values
                    val speakersArray = JSONArray()
                    for (speaker in speakersCollection) {
                        // ssrc
                        val speakerJSONObject = JSONObject()
                        speakerJSONObject.put("ssrc", java.lang.Long.valueOf(speaker.ssrc))

                        // levels
                        speakerJSONObject.put("levels", speaker.getLevels())
                        speakersArray.put(speakerJSONObject)
                    }
                    jsonObject!!.put("speakers", speakersArray)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
                any
            }
        } else {
            // Retrieving a JSON representation of a DominantSpeakerIdentification has been
            // implemented for the purposes of debugging only.
            jsonObject = null
        }
        return jsonObject
    }

    /**
     * Fires a new `PropertyChangeEvent` to the `PropertyChangeListener`s registered
     * with this `DominantSpeakerIdentification` in order to notify about a change in the
     * value of a specific property which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this `DominantSpeakerIdentification` which had its
     * value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    private fun firePropertyChange(property: String, oldValue: Long?, newValue: Long?) {
        firePropertyChange(property, oldValue as Any?, newValue as Any?)
        if (DOMINANT_SPEAKER_PROPERTY_NAME == property) {
            val ssrc = newValue ?: -1
            fireActiveSpeakerChanged(ssrc)
        }
    }

    /**
     * Fires a new `PropertyChangeEvent` to the `PropertyChangeListener`s registered
     * with this `DominantSpeakerIdentification` in order to notify about a change in the
     * value of a specific property which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this `DominantSpeakerIdentification` which had its
     * value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?) {
        propertyChangeNotifier.firePropertyChange(property, oldValue, newValue)
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the dominant speaker in this multipoint conference.
     *
     * @return the synchronization source identifier (SSRC) of the dominant speaker in this multipoint conference
     */
    private val dominantSpeaker: Long
        get() {
            val dominantSSRC = dominantSSRC
            return dominantSSRC ?: -1
        }

    /**
     * Gets the `Speaker` in this multipoint conference identified by a specific SSRC. If no
     * such `Speaker` exists, a new `Speaker` is initialized with the specified
     * `ssrc`, added to this multipoint conference and returned.
     *
     * @param ssrc the SSRC identifying the `Speaker` to return
     * @return the `Speaker` in this multipoint conference identified by the specified `ssrc`
     */
    @Synchronized
    private fun getOrCreateSpeaker(ssrc: Long): Speaker {
        var speaker = speakers[ssrc]
        if (speaker == null) {
            speaker = Speaker(ssrc)
            speakers[ssrc] = speaker

            // Since we've created a new Speaker in the multipoint conference, we'll very likely
            // need to make a decision whether there have been speaker switch events soon.
            maybeStartDecisionMaker()
        }
        return speaker
    }

    /**
     * {@inheritDoc}
     */
    override fun levelChanged(ssrc: Long, level: Int) {
        var speaker: Speaker
        val now = System.currentTimeMillis()
        synchronized(this) {
            speaker = getOrCreateSpeaker(ssrc)

            // Note that this ActiveSpeakerDetector is still in use. When it is
            // not in use long enough, its DecisionMaker i.e. background thread
            // will prepare itself and, consequently, this
            // DominantSpeakerIdentification for garbage collection.
            if (lastLevelChangedTime < now) {
                lastLevelChangedTime = now

                // A report or measurement of an audio level indicates that this
                // DominantSpeakerIdentification is in use and, consequently,
                // that it'll very likely need to make a decision whether there
                // have been speaker switch events soon.
                maybeStartDecisionMaker()
            }
        }
        if (speaker != null) speaker.levelChanged(level, now)
    }

    /**
     * Makes the decision whether there has been a speaker switch event. If there has been such an
     * event, notifies the registered listeners that a new speaker is dominating the multipoint conference.
     */
    private fun makeDecision() {
        // If we have to fire events to any registered listeners eventually, we
        // will want to do it outside the synchronized block.
        var oldDominantSpeakerValue: Long? = null
        var newDominantSpeakerValue: Long? = null
        synchronized(this) {
            val speakerCount = speakers.size
            var newDominantSSRC: Long?
            if (speakerCount == 0) {
                // If there are no Speakers in a multipoint conference, then
                // there are no speaker switch events to detect.
                newDominantSSRC = null
            } else if (speakerCount == 1) {
                // If there is a single Speaker in a multipoint conference, then
                // his/her speech surely dominates.
                newDominantSSRC = speakers.keys.iterator().next()
            } else {
                var dominantSpeaker = if (dominantSSRC == null) null else speakers[dominantSSRC]

                // If there is no dominant speaker, nominate one at random and then
                // let the other speakers compete with the nominated one.
                if (dominantSpeaker == null) {
                    val (key, value) = speakers.entries.iterator().next()
                    dominantSpeaker = value
                    newDominantSSRC = key
                } else {
                    newDominantSSRC = null
                }
                dominantSpeaker.evaluateSpeechActivityScores()
                val relativeSpeechActivities = relativeSpeechActivities
                // If multiple speakers cause speaker switches, they compete among themselves by
                // their relative speech activities in the middle time-interval.
                var newDominantC2 = C2
                for ((key, speaker) in speakers) {

                    // The dominant speaker does not compete with itself. In other words, there
                    // is no use detecting a speaker switch from the dominant speaker to the
                    // dominant speaker. Technically, the relative speech activities are all
                    // zeroes for the dominant speaker.
                    if (speaker === dominantSpeaker) continue
                    speaker.evaluateSpeechActivityScores()

                    // Compute the relative speech activities for the immediate,
                    // medium and long time-intervals.
                    for (interval in relativeSpeechActivities.indices) {
                        relativeSpeechActivities[interval] = ln(speaker.getSpeechActivityScore(interval)
                                / dominantSpeaker.getSpeechActivityScore(interval))
                    }
                    val c1 = relativeSpeechActivities[0]
                    val c2 = relativeSpeechActivities[1]
                    val c3 = relativeSpeechActivities[2]
                    if (c1 > C1 && c2 > C2 && c3 > C3 && c2 > newDominantC2) {
                        // If multiple speakers cause speaker switches, they compete among
                        // themselves by their relative speech  in the middle time-interval.
                        newDominantC2 = c2
                        newDominantSSRC = key
                    }
                }
            }
            if (newDominantSSRC != null && newDominantSSRC != dominantSSRC) {
                oldDominantSpeakerValue = dominantSSRC
                dominantSSRC = newDominantSSRC
                newDominantSpeakerValue = dominantSSRC
            } // synchronized (this)
        }

        // Now that we are outside the synchronized block, fire events, if any,
        // to any registered listeners.
        if (newDominantSpeakerValue != null &&
                newDominantSpeakerValue != oldDominantSpeakerValue) {
            firePropertyChange(DOMINANT_SPEAKER_PROPERTY_NAME,
                    oldDominantSpeakerValue, newDominantSpeakerValue)
        }
    }

    /**
     * Starts a background thread which is to repeatedly make the (global) decision about speaker
     * switches if such a background thread has not been started yet and if the current state of
     * this `DominantSpeakerIdentification` justifies the start of such a background thread
     * (e.g. there is at least one `Speaker` in this multipoint conference).
     */
    @Synchronized
    private fun maybeStartDecisionMaker() {
        if (decisionMaker == null && speakers.isNotEmpty()) {
            val decisionMaker = DecisionMaker(this)
            var scheduled = false
            this.decisionMaker = decisionMaker
            scheduled = try {
                threadPool.execute(decisionMaker)
                true
            } finally {
                if (!scheduled && this.decisionMaker === decisionMaker) this.decisionMaker = null
            }
        }
    }

    /**
     * Removes a `PropertyChangeListener` from the list of listeners interested in and
     * notified about changes in the values of the properties of this
     * `DominantSpeakerIdentification`.
     *
     * @param listener a `PropertyChangeListener` to no longer be notified about changes in the values
     * of the properties of this `DominantSpeakerIdentification`
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeNotifier.removePropertyChangeListener(listener)
    }

    /**
     * Runs in the background/daemon `Thread` of [.decisionMaker] and makes the
     * decision whether there has been a speaker switch event.
     *
     * @return a negative integer if the `DecisionMaker` is to exit or a non-negative
     * integer to specify the time in milliseconds until the next execution of the `DecisionMaker`
     */
    private fun runInDecisionMaker(): Long {
        val now = System.currentTimeMillis()
        val levelIdleTimeout = LEVEL_IDLE_TIMEOUT - (now - lastLevelIdleTime)
        var sleep: Long = 0
        if (levelIdleTimeout <= 0) {
            if (lastLevelIdleTime != 0L) timeoutIdleLevels(now)
            lastLevelIdleTime = now
        } else {
            sleep = levelIdleTimeout
        }
        var decisionTimeout = DECISION_INTERVAL - (now - lastDecisionTime)
        if (decisionTimeout <= 0) {
            // The identification of the dominant active speaker may be a
            // time-consuming ordeal so the time of the last decision is the
            // time of the beginning of a decision iteration.
            lastDecisionTime = now
            makeDecision()
            // The identification of the dominant active speaker may be a
            // time-consuming ordeal so the timeout to the next decision
            // iteration should be computed after the end of the decision iteration.
            decisionTimeout = DECISION_INTERVAL - (System.currentTimeMillis() - now)
        }
        if (decisionTimeout in 1 until sleep) sleep = decisionTimeout
        return sleep
    }

    /**
     * Runs in the background/daemon `Thread` of a specific `DecisionMaker` and makes
     * the decision whether there has been a speaker switch event.
     *
     * @param decisionMaker the `DecisionMaker` invoking the method
     * @return a negative integer if the `decisionMaker` is to exit or a non-negative
     * integer to specify the time in milliseconds until the next execution of the `decisionMaker`
     */
    fun runInDecisionMaker(decisionMaker: DecisionMaker): Long {
        synchronized(this) {

            // Most obviously, DecisionMakers no longer employed by this
            // DominantSpeakerIdentification should cease to exist as soon as possible.
            if (this.decisionMaker != decisionMaker) return -1

            // If the decisionMaker has been unnecessarily executing long enough, kill it in
            // order to have a more deterministic behavior with respect to disposal.
            if (0 < lastDecisionTime) {
                val idle = lastDecisionTime - lastLevelChangedTime
                if (idle >= DECISION_MAKER_IDLE_TIMEOUT) return -1
            }
        }
        return runInDecisionMaker()
    }

    /**
     * Notifies the `Speaker`s in this multipoint conference who have not received or
     * measured audio levels for a certain time (i.e. [.LEVEL_IDLE_TIMEOUT]) that they will
     * very likely not have a level within a certain time-frame of the
     * `DominantSpeakerIdentification` algorithm. Additionally, removes the non-dominant
     * `Speaker`s who have not received or measured audio levels for far too long (i.e.
     * [.SPEAKER_IDLE_TIMEOUT]).
     *
     * @param now the time at which the timing out is being detected
     */
    @Synchronized
    private fun timeoutIdleLevels(now: Long) {
        val i = speakers.entries.iterator()
        while (i.hasNext()) {
            val speaker = i.next().value
            val idle = now - speaker.lastLevelChangedTime

            // Remove a non-dominant Speaker if he/she has been idle for far too long.
            if (SPEAKER_IDLE_TIMEOUT < idle && (dominantSSRC == null || speaker.ssrc != dominantSSRC)) {
                i.remove()
            } else if (LEVEL_IDLE_TIMEOUT < idle) {
                speaker.levelTimedOut()
            }
        }
    }

    /**
     * Represents the background thread which repeatedly makes the (global) decision about speaker
     * switches. Weakly references an associated `DominantSpeakerIdentification` instance in
     * order to eventually detect that the multipoint conference has actually expired and that the
     * background `Thread` should perish.
     *
     * @author Lyubomir Marinov
     */
    class DecisionMaker(algorithm: DominantSpeakerIdentification) : Runnable {
        /**
         * The `DominantSpeakerIdentification` instance which is repeatedly run into this
         * background thread in order to make the (global) decision about speaker switches. It is a
         * `WeakReference` in order to eventually detect that the multipoint conference has
         * actually expired and that this background `Thread` should perish.
         */
        private val algorithm: WeakReference<DominantSpeakerIdentification>

        /**
         * Initializes a new `DecisionMaker` instance which is to repeatedly run a specific
         * `DominantSpeakerIdentification` into a background thread in order to make the
         * (global) decision about speaker switches.
         *
         * algorithm the `DominantSpeakerIdentification` to be repeatedly run by the new
         * instance in order to make the (global) decision about speaker switches
         */
        init {
            this.algorithm = WeakReference(algorithm)
        }

        /**
         * Repeatedly runs [.algorithm] i.e. makes the (global) decision about speaker
         * switches until the multipoint conference expires.
         */
        override fun run() {
            try {
                do {
                    var algorithm = algorithm.get()
                    if (algorithm == null) {
                        break
                    } else {
                        val sleep = algorithm.runInDecisionMaker(this)

                        // A negative sleep value is explicitly supported i.e.
                        // expected and is contracted to mean that this DecisionMaker is
                        // instructed by the algorithm to commit suicide.
                        if (sleep < 0) {
                            break
                        } else if (sleep > 0) {
                            // Before sleeping, make the currentThread release its reference to
                            // the associated DominantSpeakerIdentification instance.
                            algorithm = null
                            try {
                                Thread.sleep(sleep)
                            } catch (ie: InterruptedException) {
                                // Continue with the next iteration.
                            }
                        }
                    }
                } while (true)
            } finally {
                // Notify the algorithm that this background thread will no
                // longer run it in order to make the (global) decision about
                // speaker switches. Subsequently, the algorithm may decide to
                // spawn another background thread to run the same task.
                val algorithm = algorithm.get()
                algorithm?.decisionMakerExited(this)
            }
        }
    }

    /**
     * Facilitates this `DominantSpeakerIdentification` in the implementations of adding and
     * removing `PropertyChangeListener` s and firing `PropertyChangeEvent`s to the
     * added `PropertyChangeListener`s.
     *
     * @author Lyubomir Marinov
     */
    private inner class PropertyChangeNotifier : org.atalk.util.event.PropertyChangeNotifier() {
        /**
         * {@inheritDoc}
         *
         * Makes the super implementations (which is protected) public.
         */
        public override fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?) {
            super.firePropertyChange(property, oldValue, newValue)
        }

        /**
         * {@inheritDoc}
         *
         * Always returns this `DominantSpeakerIdentification`.
         */
        override fun getPropertyChangeSource(property: String?, oldValue: Any?, newValue: Any?): Any {
            return this@DominantSpeakerIdentification
        }
    }

    /**
     * Represents a speaker in a multipoint conference identified by synchronization source identifier/SSRC.
     *
     * @author Lyubomir Marinov
     */
    private class Speaker(
            /**
             * The synchronization source identifier/SSRC of this `Speaker` which is unique
             * within a multipoint conference.
             */
            val ssrc: Long) {
        private val immediates = ByteArray(LONG_COUNT * N3 * N2)

        /**
         * The speech activity score of this `Speaker` for the immediate time-interval.
         */
        private var immediateSpeechActivityScore = MIN_SPEECH_ACTIVITY_SCORE
        /**
         * Gets the time in milliseconds at which an actual (audio) level was reported or measured
         * for this `Speaker` last.
         *
         * @return the time in milliseconds at which an actual (audio) level was reported or
         * measured for this `Speaker` last
         */
        /**
         * The time in milliseconds of the most recent invocation of [.levelChanged]
         * i.e. the last time at which an actual (audio) level was reported or measured for this
         * `Speaker`. If no level is reported or measured for this `Speaker` long
         * enough i.e. [.LEVEL_IDLE_TIMEOUT], the associated
         * `DominantSpeakerIdentification` will presume that this `Speaker` was muted
         * for the duration of a certain frame.
         */
        @get:Synchronized
        var lastLevelChangedTime = System.currentTimeMillis()
            private set

        /**
         * The (history of) audio levels received or measured for this `Speaker`.
         */
        private val levels = ByteArray(immediates.size)
        private val longs = ByteArray(LONG_COUNT)

        /**
         * The speech activity score of this `Speaker` for the long time-interval.
         */
        private var longSpeechActivityScore = MIN_SPEECH_ACTIVITY_SCORE
        private val mediums = ByteArray(LONG_COUNT * N3)

        /**
         * The speech activity score of this `Speaker` for the medium time-interval.
         */
        private var mediumSpeechActivityScore = MIN_SPEECH_ACTIVITY_SCORE

        /**
         * The minimum (audio) level received or measured for this `Speaker`. Since
         * `MIN_LEVEL` is specified for samples generated by a muted audio source, a value
         * equal to `MIN_LEVEL` indicates that the minimum level for this `Speaker`
         * has not been determined yet.
         */
        private var minLevel = MIN_LEVEL.toByte()

        /**
         * The (current) estimate of the minimum (audio) level received or measured for this
         * `Speaker`. Used to increase the value of [.minLevel]
         */
        private var nextMinLevel = MIN_LEVEL.toByte()

        /**
         * The number of subsequent (audio) levels received or measured for this `Speaker`
         * which have been monitored thus far in order to estimate an up-to-date minimum (audio)
         * level received or measured for this `Speaker`.
         */
        private var nextMinLevelWindowLength = 0

        private fun computeImmediates(): Boolean {
            // The minimum audio level received or measured for this Speaker is
            // the level of "silence" for this Speaker. Since the various
            // Speakers may differ in their levels of "silence", put all
            // Speakers on equal footing by replacing the individual levels of
            // "silence" with the uniform level of absolute silence.
            val immediates = immediates
            val levels = levels
            val minLevel = (minLevel + N1_SUBUNIT_LENGTH).toByte()
            var changed = false
            for (i in immediates.indices) {
                var level = levels[i]
                if (level < minLevel) level = MIN_LEVEL.toByte()
                val immediate = (level / N1_SUBUNIT_LENGTH).toByte()
                if (immediates[i] != immediate) {
                    immediates[i] = immediate
                    changed = true
                }
            }
            return changed
        }

        private fun computeLongs(): Boolean {
            return computeBigs(mediums, longs, LONG_THRESHOLD)
        }

        private fun computeMediums(): Boolean {
            return computeBigs(immediates, mediums, MEDIUM_THRESHOLD)
        }

        /**
         * Computes/evaluates the speech activity score of this `Speaker` for the immediate time-interval.
         */
        private fun evaluateImmediateSpeechActivityScore() {
            immediateSpeechActivityScore = computeSpeechActivityScore(immediates[0].toInt(), N1, 0.5, 0.78)
        }

        /**
         * Computes/evaluates the speech activity score of this `Speaker` for the long time-interval.
         */
        private fun evaluateLongSpeechActivityScore() {
            longSpeechActivityScore = computeSpeechActivityScore(longs[0].toInt(), N3, 0.5, 47.0)
        }

        /**
         * Computes/evaluates the speech activity score of this `Speaker` for the medium time-interval.
         */
        private fun evaluateMediumSpeechActivityScore() {
            mediumSpeechActivityScore = computeSpeechActivityScore(mediums[0].toInt(), N2, 0.5, 24.0)
        }

        /**
         * Evaluates the speech activity scores of this `Speaker` for the immediate, medium,
         * and long time-intervals. Invoked when it is time to decide whether there has been a
         * speaker switch event.
         */
        @Synchronized
        fun evaluateSpeechActivityScores() {
            if (computeImmediates()) {
                evaluateImmediateSpeechActivityScore()
                if (computeMediums()) {
                    evaluateMediumSpeechActivityScore()
                    if (computeLongs()) evaluateLongSpeechActivityScore()
                }
            }
        }

        /**
         * Gets the (history of) audio levels received or measured for this `Speaker`.
         *
         * @return a `byte` array which represents the (history of) audio levels received or
         * measured for this `Speaker`
         */
        fun getLevels(): ByteArray {
            // The levels of Speaker are internally maintained starting with the
            // last audio level received or measured for this Speaker and ending
            // with the first audio level received or measured for this Speaker.
            // Unfortunately, the method is expected to return levels in reverse order.
            val src = levels
            val dst = ByteArray(src.size)
            var s = src.size - 1
            var d = 0
            while (d < dst.size) {
                dst[d] = src[s]
                --s
                ++d
            }
            return dst
        }

        /**
         * Gets the speech activity score of this `Speaker` for a specific time-interval.
         *
         * @param interval `0` for the immediate time-interval, `1` for the medium
         * time-interval, or `2` for the long time-interval
         * @return the speech activity score of this `Speaker` for the time-interval
         * specified by `index`
         */
        fun getSpeechActivityScore(interval: Int): Double {
            return when (interval) {
                0 -> immediateSpeechActivityScore
                1 -> mediumSpeechActivityScore
                2 -> longSpeechActivityScore
                else -> throw IllegalArgumentException("interval $interval")
            }
        }

        /**
         * Notifies this `Speaker` that a new audio level has been received or measured.
         *
         * @param level the audio level which has been received or measured for this `Speaker`
         */
        fun levelChanged(level: Int) {
            levelChanged(level, System.currentTimeMillis())
        }

        /**
         * Notifies this `Speaker` that a new audio level has been received or measured at a specific time.
         *
         * @param level the audio level which has been received or measured for this `Speaker`
         * @param time the (local `System`) time in milliseconds at which the specified
         * `level` has been received or measured
         */
        @Synchronized
        fun levelChanged(level: Int, time: Long) {
            // It sounds relatively reasonable that late audio levels should better be discarded.
            if (lastLevelChangedTime <= time) {
                lastLevelChangedTime = time

                // Ensure that the specified level is within the supported range.
                val b = when {
                    level < MIN_LEVEL -> MIN_LEVEL.toByte()
                    level > MAX_LEVEL -> MAX_LEVEL.toByte()
                    else -> level.toByte()
                }

                // Push the specified level into the history of audio levels
                // received or measured for this Speaker.
                System.arraycopy(levels, 0, levels, 1, levels.size - 1)
                levels[0] = b

                // Determine the minimum level received or measured for this Speaker.
                updateMinLevel(b)
            }
        }

        /**
         * Notifies this `Speaker` that no new audio level has been received or measured for
         * a certain time which very likely means that this `Speaker` will not have a level
         * within a certain time-frame of a `DominantSpeakerIdentification` algorithm.
         */
        @Synchronized
        fun levelTimedOut() {
            levelChanged(MIN_LEVEL, lastLevelChangedTime)
        }

        /**
         * Updates the minimum (audio) level received or measured for this `Speaker` in
         * light of the receipt of a specific level.
         *
         * @param level the audio level received or measured for this `Speaker`
         */
        private fun updateMinLevel(level: Byte) {
            if (level.toInt() != MIN_LEVEL) {
                if (minLevel.toInt() == MIN_LEVEL || minLevel > level) {
                    minLevel = level
                    nextMinLevel = MIN_LEVEL.toByte()
                    nextMinLevelWindowLength = 0
                } else {
                    // The specified (audio) level is greater than the minimum
                    // level received or measure for this Speaker. However, the
                    // minimum level may be out-of-date by now. Estimate an
                    // up-to-date minimum level and, eventually, make it the
                    // minimum level received or measured for this Speaker.
                    if (nextMinLevel.toInt() == MIN_LEVEL) {
                        nextMinLevel = level
                        nextMinLevelWindowLength = 1
                    } else {
                        if (nextMinLevel > level) {
                            nextMinLevel = level
                        }
                        nextMinLevelWindowLength++
                        if (nextMinLevelWindowLength >= MIN_LEVEL_WINDOW_LENGTH) {
                            // The arithmetic mean will increase the minimum
                            // level faster than the geometric mean. Since the
                            // goal is to track a minimum, it sounds reasonable
                            // to go with a slow increase.
                            var newMinLevel = sqrt(minLevel * nextMinLevel.toDouble())

                            // Ensure that the new minimum level is within the supported range.
                            if (newMinLevel < MIN_LEVEL) newMinLevel = MIN_LEVEL.toDouble() else if (newMinLevel > MAX_LEVEL) newMinLevel = MAX_LEVEL.toDouble()
                            minLevel = newMinLevel.toInt().toByte()
                            nextMinLevel = MIN_LEVEL.toByte()
                            nextMinLevelWindowLength = 0
                        }
                    }
                }
            }
        }
    }

    companion object {
        /**
         * The threshold of the relevant speech activities in the immediate time-interval in
         * &quot;global decision&quot;/&quot;Dominant speaker selection&quot; phase of the algorithm.
         */
        private const val C1 = 3.0

        /**
         * The threshold of the relevant speech activities in the medium time-interval in &quot;global
         * decision&quot;/&quot;Dominant speaker selection&quot; phase of the algorithm.
         */
        private const val C2 = 2.0

        /**
         * The threshold of the relevant speech activities in the long time-interval in &quot;global
         * decision&quot;/&quot;Dominant speaker selection&quot; phase of the algorithm.
         */
        private const val C3 = 0.0

        /**
         * The interval in milliseconds of the activation of the identification of the dominant speaker
         * in a multipoint conference.
         */
        private const val DECISION_INTERVAL: Long = 300

        /**
         * The interval of time in milliseconds of idle execution of `DecisionMaker` after which
         * the latter should cease to exist. The interval does not have to be very long because the
         * background threads running the `DecisionMaker`s are pooled anyway.
         */
        private const val DECISION_MAKER_IDLE_TIMEOUT = (15 * 1000).toLong()

        /**
         * The name of the `DominantSpeakerIdentification` property `dominantSpeaker`
         * which specifies the dominant speaker identified by synchronization source identifier (SSRC).
         */
        val DOMINANT_SPEAKER_PROPERTY_NAME = DominantSpeakerIdentification::class.java.name + ".dominantSpeaker"

        /**
         * The interval of time without a call to [Speaker.levelChanged] after which
         * `DominantSpeakerIdentification` assumes that there will be no report of a
         * `Speaker`'s level within a certain time-frame. The default value of `40` is
         * chosen in order to allow non-aggressive fading of the last received or measured level and to
         * be greater than the most common RTP packet durations in milliseconds i.e. `20` and
         * `30`.
         */
        private const val LEVEL_IDLE_TIMEOUT: Long = 40

        /**
         * The (total) number of long time-intervals used for speech activity score evaluation at a
         * specific time-frame.
         */
        private const val LONG_COUNT = 1

        /**
         * The threshold in terms of active medium-length blocks which is used during the speech
         * activity evaluation step for the long time-interval.
         */
        private const val LONG_THRESHOLD = 4

        /**
         * The maximum value of audio level supported by `DominantSpeakerIdentification`.
         */
        private const val MAX_LEVEL = 127

        /**
         * The minimum value of audio level supported by `DominantSpeakerIdentification`.
         */
        private const val MIN_LEVEL = 0

        /**
         * The number of (audio) levels received or measured for a `Speaker` to be monitored in
         * order to determine that the minimum level for the `Speaker` has increased.
         */
        private const val MIN_LEVEL_WINDOW_LENGTH = (15 /* seconds */ * 1000 /* milliseconds */
                / 20 /* milliseconds per level */)

        /**
         * The minimum value of speech activity score supported by
         * `DominantSpeakerIdentification`. The value must be positive because (1) we are going
         * to use it as the argument of a logarithmic function and the latter is undefined for negative
         * arguments and (2) we will be dividing by the speech activity score.
         */
        private const val MIN_SPEECH_ACTIVITY_SCORE = 0.0000000001

        /**
         * The threshold in terms of active sub-bands in a frame which is used during the speech
         * activity evaluation step for the medium length time-interval.
         */
        private const val MEDIUM_THRESHOLD = 7

        /**
         * The (total) number of sub-bands in the frequency range evaluated for immediate speech
         * activity. The implementation of the class `DominantSpeakerIdentification` does not
         * really operate on the representation of the signal in the frequency domain, it works with
         * audio levels derived from RFC 6465 &quot;A Real-time Transport Protocol (RTP) Header
         * Extension for Mixer-to-Client Audio Level Indication&quot;.
         */
        private const val N1 = 13

        /**
         * The length/size of a sub-band in the frequency range evaluated for immediate speech activity.
         * In the context of the implementation of the class `DominantSpeakerIdentification`, it
         * specifies the length/size of a sub-unit of the audio level range defined by RFC 6465.
         */
        private const val N1_SUBUNIT_LENGTH = (MAX_LEVEL - MIN_LEVEL + N1 - 1) / N1

        /**
         * The number of frames (i.e. [Speaker.immediates] evaluated for medium speech activity.
         */
        private const val N2 = 5

        /**
         * The number of medium-length blocks constituting a long time-interval.
         */
        private const val N3 = 10

        /**
         * The interval of time without a call to [Speaker.levelChanged] after which
         * `DominantSpeakerIdentification` assumes that a non-dominant `Speaker` is to be
         * automatically removed from [.speakers].
         */
        private const val SPEAKER_IDLE_TIMEOUT = (60 * 60 * 1000).toLong()

        /**
         * The pool of `Thread`s which run `DominantSpeakerIdentification`s.
         */
        private val threadPool = ExecutorUtils.newCachedThreadPool(true, "DominantSpeakerIdentification")

        /**
         * Computes the binomial coefficient indexed by `n` and `r` i.e. the number of
         * ways of picking `r` unordered outcomes from `n` possibilities.
         *
         * @param n the number of possibilities to pick from
         * @param r_ the number unordered outcomes to pick from `n`
         * @return the binomial coefficient indexed by `n` and `r` i.e. the number of
         * ways of picking `r` unordered outcomes from `n` possibilities
         */
        private fun binomialCoefficient(n: Int, r_: Int): Long {
            var r = r_
            val m = n - r // r = Math.max(r, n - r);
            if (r < m) r = m
            var t: Long = 1
            var i = n
            var j = 1
            while (i > r) {
                t = t * i / j
                i--
                j++
            }
            return t
        }

        private fun computeBigs(littles: ByteArray, bigs: ByteArray, threshold: Int): Boolean {
            val bigLength = bigs.size
            val littleLengthPerBig = littles.size / bigLength
            var changed = false
            var b = 0
            var l = 0
            while (b < bigLength) {
                var sum: Byte = 0
                val lEnd = l + littleLengthPerBig
                while (l < lEnd) {
                    if (littles[l] > threshold) sum++
                    l++
                }
                if (bigs[b] != sum) {
                    bigs[b] = sum
                    changed = true
                }
                b++
            }
            return changed
        }

        private fun computeSpeechActivityScore(vL: Int, nR: Int, p: Double, lambda: Double): Double {
            var speechActivityScore = ln(binomialCoefficient(nR, vL).toDouble()) + vL * Math.log(p) + (nR - vL) * Math.log(1 - p) - Math.log(lambda) + lambda * vL
            if (speechActivityScore < MIN_SPEECH_ACTIVITY_SCORE) speechActivityScore = MIN_SPEECH_ACTIVITY_SCORE
            return speechActivityScore
        }
    }
}