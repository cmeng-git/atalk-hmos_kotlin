/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("LeakingThis")

package net.java.sip.communicator.service.callhistory

import android.icu.text.MeasureFormat
import android.icu.text.MeasureFormat.FormatWidth
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.text.DateFormat
import java.util.*

/**
 * Structure used for encapsulating data when writing or reading Call History Data. Also these
 * records are used for returning data from the Call History Service.
 *
 * @author Damian Minkov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
open class CallRecord(uuid: String?, direction: String, startTime: Date, endTime: Date?) {
    /**
     * Return Vector of CallPeerRecords
     *
     * @return Vector
     */
    /**
     * A list of all peer records corresponding to this call record.
     */
    @JvmField
    val peerRecords = Vector<CallPeerRecord>()
    /**
     * Returns the direction of the call IN or OUT
     *
     * @return String
     */
    /**
     * The id that uniquely identifies the call record.
     */
    var callUuid: String
        protected set
    /**
     * Returns the direction of the call IN or OUT
     *
     * @return String
     */
    /**
     * Indicates the direction of the call - IN or OUT.
     */
    open lateinit var direction: String

    /**
     * The time when the call has began
     *
     * @return Date
     */
    /**
     * The start call date.
     */
    open lateinit var startTime: Date

    /**
     * Returns the time when the call has finished
     *
     * @return Date
     */
    /**
     * The end call date.
     */
    open var endTime: Date? = null

    /**
     * Returns the protocol provider used for the call. Could be null if the record has not saved the provider.
     *
     * @return the protocol provider used for the call
     */
    /**
     * The protocol provider (accountUid) through which the call was made.
     */
    open var protocolProvider: ProtocolProviderService? = null

    /**
     * This is the end reason of the call if any. -1 the default value for no reason specified.
     *
     * @return end reason code if any.
     */
    /**
     * This is the end reason of the call if any. -1 default value for no reason specified.
     */
    open var endReason = -1

    /**
     * Creates Call Record
     */
    init {
        var tmpUuid = uuid
        if (tmpUuid == null) {
            val date = Date()
            tmpUuid = date.time.toString() + Math.abs(date.hashCode())
        }

        callUuid = tmpUuid
        this.direction = direction
        this.startTime = startTime
        this.endTime = endTime
    }

    /**
     * Finds a CallPeer with the supplied address
     *
     * @param address EntityFullJid or callParticipantIDs
     * @return CallPeerRecord
     */
    fun findPeerRecord(address: String): CallPeerRecord? {
        for (item in peerRecords) {
            if (item.peerAddress == address) return item
        }
        return null
    }

    override fun toString(): String {
        val callStart: String
        val start = startTime.time
        callStart = if (DateUtils.isToday(start)) {
            val df = DateFormat.getTimeInstance(DateFormat.MEDIUM)
            df.format(startTime)
        } else {
            val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            df.format(startTime)
        }
        val callTime = endTime!!.time - start
        val callDuration = formatDuration(callTime)
        val callInfo = StringBuilder()
                .append(callStart)
                .append(" (")
                .append(callDuration)
                .append(")")
        return callInfo.toString()
    }

    companion object {
        /**
         * The outgoing call direction.
         */
        const val OUT = "out"

        /**
         * The incoming call direction.
         */
        const val IN = "in"

        fun formatDuration(millis: Long): CharSequence {
            val width = FormatWidth.SHORT
            val formatter = MeasureFormat.getInstance(Locale.getDefault(), width)
            return if (millis >= DateUtils.HOUR_IN_MILLIS) {
                val hours = ((millis + 1800000) / DateUtils.HOUR_IN_MILLIS).toInt()
                formatter.format(Measure(hours, MeasureUnit.HOUR))
            } else if (millis >= DateUtils.MINUTE_IN_MILLIS) {
                val minutes = ((millis + 30000) / DateUtils.MINUTE_IN_MILLIS).toInt()
                formatter.format(Measure(minutes, MeasureUnit.MINUTE))
            } else {
                val seconds = ((millis + 500) / DateUtils.SECOND_IN_MILLIS).toInt()
                formatter.format(Measure(seconds, MeasureUnit.SECOND))
            }
        }
    }
}