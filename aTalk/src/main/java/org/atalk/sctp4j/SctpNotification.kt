/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.sctp4j

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Partially implemented SCTP notifications for which the native wrapper
 * currently registers for.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class SctpNotification private constructor(data: ByteArray) {
    val sn_type: Int
    val sn_flags: Int
    val sn_length: Int
    protected val buffer: ByteBuffer

    init {
        // FIXME: unsigned types
        buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        sn_type = buffer.char.code
        sn_flags = buffer.char.code
        sn_length = buffer.int
    }

    override fun toString(): String {
        when (sn_type) {
            SCTP_ASSOC_CHANGE -> return "SCTP_ASSOC_CHANGE"
            SCTP_PEER_ADDR_CHANGE -> return "SCTP_PEER_ADDR_CHANGE"
            SCTP_REMOTE_ERROR -> return "SCTP_REMOTE_ERROR"
            SCTP_SEND_FAILED -> return "SCTP_SEND_FAILED"
            SCTP_SHUTDOWN_EVENT -> return "SCTP_SHUTDOWN_EVENT"
            SCTP_ADAPTATION_INDICATION -> return "SCTP_ADAPTATION_INDICATION"
            SCTP_PARTIAL_DELIVERY_EVENT -> return "SCTP_PARTIAL_DELIVERY_EVENT"
            SCTP_AUTHENTICATION_EVENT -> return "SCTP_AUTHENTICATION_EVENT"
            SCTP_STREAM_RESET_EVENT -> return "SCTP_STREAM_RESET_EVENT"
            SCTP_SENDER_DRY_EVENT -> return "SCTP_SENDER_DRY_EVENT"
            SCTP_NOTIFICATIONS_STOPPED_EVENT -> return "SCTP_NOTIFICATIONS_STOPPED_EVENT"
            SCTP_ASSOC_RESET_EVENT -> return "SCTP_ASSOC_RESET_EVENT"
            SCTP_STREAM_CHANGE_EVENT -> return "SCTP_STREAM_CHANGE_EVENT"
            SCTP_SEND_FAILED_EVENT -> return "SCTP_SEND_FAILED_EVENT"
        }
        return "SCTP_NOTIFICATION_0x" + Integer.toHexString(sn_type)
    }

    /**
     * Association change event
     * struct sctp_assoc_change {
     * uint16_t sac_type;
     * uint16_t sac_flags;
     * uint32_t sac_length;
     * uint16_t sac_state;
     * uint16_t sac_error;
     * uint16_t sac_outbound_streams;
     * uint16_t sac_inbound_streams;
     * sctp_assoc_t sac_assoc_id; //uint32_t
     * uint8_t sac_info[]; // not available yet
     * };
     */
    class AssociationChange(data: ByteArray) : SctpNotification(data) {
        val state: Int
        val error: Int
        val outboundStreams: Int
        val inboundStreams: Int
        val assocId: Long

        init {
            // FIXME: UINT types
            state = buffer.char.code
            error = buffer.char.code
            outboundStreams = buffer.char.code
            inboundStreams = buffer.char.code
            assocId = buffer.int.toLong()
        }

        override fun toString(): String {
            var str = super.toString()
            str += ":assocId:0x" + java.lang.Long.toHexString(assocId)
            str += when (state) {
                SCTP_COMM_UP -> ",COMM_UP"
                SCTP_COMM_LOST -> ",COMM_LOST"
                SCTP_RESTART -> ",RESTART"
                SCTP_SHUTDOWN_COMP -> ",SHUTDOWN_COMP"
                SCTP_CANT_STR_ASSOC -> ",CANT_STR_ASSOC"
                else -> ",0x" + Integer.toHexString(state)
            }
            // in/out streams supported
            str += ",(in/out)($inboundStreams/$outboundStreams)"
            // error
            str += ",err0x" + Integer.toHexString(error)
            return str
        }

        companion object {
            /* sac_state values */
            const val SCTP_COMM_UP = 0x0001
            const val SCTP_COMM_LOST = 0x0002
            const val SCTP_RESTART = 0x0003
            const val SCTP_SHUTDOWN_COMP = 0x0004
            const val SCTP_CANT_STR_ASSOC = 0x0005

            /* sac_info values */
            const val SCTP_ASSOC_SUPPORTS_PR = 0x01
            const val SCTP_ASSOC_SUPPORTS_AUTH = 0x02
            const val SCTP_ASSOC_SUPPORTS_ASCONF = 0x03
            const val SCTP_ASSOC_SUPPORTS_MULTIBUF = 0x04
            const val SCTP_ASSOC_SUPPORTS_RE_CONFIG = 0x05
            const val SCTP_ASSOC_SUPPORTS_MAX = 0x05
        }
    }

    /**
     * Address event
     * struct sctp_paddr_change {
     * uint16_t spc_type;
     * uint16_t spc_flags;
     * uint32_t spc_length;
     * struct sockaddr_storage spc_aaddr;
     * uint32_t spc_state;
     * uint32_t spc_error;
     * sctp_assoc_t spc_assoc_id; //uint32_t
     * uint8_t spc_padding[4];
     * };
     */
    class PeerAddressChange(data: ByteArray) : SctpNotification(data) {
        val state: Int
        val error: Long
        val assocId: Long

        init {

            // Skip struct sockaddr_storage
            val sockAddrStorageLen = data.size - 24
            buffer.position(buffer.position() + sockAddrStorageLen)
            state = buffer.int
            error = buffer.int.toLong()
            assocId = buffer.int.toLong()
        }

        override fun toString(): String {
            var base = super.toString()
            base += ",assocId:0x" + java.lang.Long.toHexString(assocId)
            base += when (state) {
                SCTP_ADDR_AVAILABLE -> ",ADDR_AVAILABLE"
                SCTP_ADDR_UNREACHABLE -> ",ADDR_UNREACHABLE"
                SCTP_ADDR_REMOVED -> ",ADDR_REMOVED"
                SCTP_ADDR_ADDED -> ",ADDR_ADDED"
                SCTP_ADDR_MADE_PRIM -> ",ADDR_MADE_PRIM"
                SCTP_ADDR_CONFIRMED -> ",ADDR_CONFIRMED"
                else -> "," + Integer.toHexString(state)
            }

            // Error
            base += ",err:" + java.lang.Long.toHexString(error)
            return base
        }

        companion object {
            /* paddr state values */
            const val SCTP_ADDR_AVAILABLE = 0x0001
            const val SCTP_ADDR_UNREACHABLE = 0x0002
            const val SCTP_ADDR_REMOVED = 0x0003
            const val SCTP_ADDR_ADDED = 0x0004
            const val SCTP_ADDR_MADE_PRIM = 0x0005
            const val SCTP_ADDR_CONFIRMED = 0x0006
        }
    }

    /**
     * SCTP send failed event
     *
     *
     * struct sctp_send_failed_event {
     * uint16_t ssfe_type;
     * uint16_t ssfe_flags;
     * uint32_t ssfe_length;
     * uint32_t ssfe_error;
     * struct sctp_sndinfo ssfe_info;
     * sctp_assoc_t ssfe_assoc_id;
     * uint8_t  ssfe_data[];
     * };
     *
     *
     * struct sctp_sndinfo {
     * uint16_t snd_sid;
     * uint16_t snd_flags;
     * uint32_t snd_ppid;
     * uint32_t snd_context;
     * sctp_assoc_t snd_assoc_id; // uint32
     * };
     */
    class SendFailed(data: ByteArray) : SctpNotification(data) {
        val error: Long

        init {
            error = buffer.int.toLong()
        }

        override fun toString(): String {
            var base = super.toString()
            if (sn_flags and SCTP_DATA_SENT > 0) base += ",DATA_SENT"
            if (sn_flags and SCTP_DATA_UNSENT > 0) base += ",DATA_UNSENT"

            // error
            base += ",err0x" + java.lang.Long.toHexString(error)
            return base
        }

        companion object {
            /* flag that indicates state of data */
            /**
             * Inqueue never on wire.
             */
            const val SCTP_DATA_UNSENT = 0x0001

            /**
             * On wire at failure.
             */
            const val SCTP_DATA_SENT = 0x0002
        }
    }

    /**
     * SCTP sender dry event
     *
     *
     * struct sctp_sender_dry_event {
     * uint16_t sender_dry_type;
     * uint16_t sender_dry_flags;
     * uint32_t sender_dry_length;
     * sctp_assoc_t sender_dry_assoc_id;
     * };
     */
    class SenderDry(data: ByteArray) : SctpNotification(data) {
        private val assocId: Long

        init {
            assocId = buffer.int.toLong()
        }

        override fun toString(): String {
            var base = super.toString()
            base += ",assocID:0x" + java.lang.Long.toHexString(assocId)
            return base
        }
    }

    /**
     * Stream reset event
     *
     *
     * struct sctp_stream_reset_event {
     * uint16_t strreset_type;
     * uint16_t strreset_flags;
     * uint32_t strreset_length;
     * sctp_assoc_t strreset_assoc_id;
     * uint16_t strreset_stream_list[];
     * };
     */
    class StreamReset(data: ByteArray) : SctpNotification(data) {
        companion object {
            /* flags in stream_reset_event (strreset_flags) */
            const val SCTP_STREAM_RESET_INCOMING_SSN = 0x0001
            const val SCTP_STREAM_RESET_OUTGOING_SSN = 0x0002
            const val SCTP_STREAM_RESET_DENIED = 0x0004
            const val SCTP_STREAM_RESET_FAILED = 0x0008
            const val SCTP_STREAM_CHANGED_DENIED = 0x0010
            const val SCTP_STREAM_RESET_INCOMING = 0x00000001
            const val SCTP_STREAM_RESET_OUTGOING = 0x00000002
        }
    }

    companion object {
        /********  Notifications   */ /*
        union sctp_notification {
            struct sctp_tlv {
                uint16_t sn_type;
                uint16_t sn_flags;
                uint32_t sn_length;
            } sn_header;
            struct sctp_assoc_change sn_assoc_change;
            struct sctp_paddr_change sn_paddr_change;
            struct sctp_remote_error sn_remote_error;
            struct sctp_shutdown_event sn_shutdown_event;
            struct sctp_adaptation_event sn_adaptation_event;
            struct sctp_pdapi_event sn_pdapi_event;
            struct sctp_authkey_event sn_auth_event;
            struct sctp_sender_dry_event sn_sender_dry_event;
            struct sctp_send_failed_event sn_send_failed_event;
            struct sctp_stream_reset_event sn_strreset_event;
            struct sctp_assoc_reset_event  sn_assocreset_event;
            struct sctp_stream_change_event sn_strchange_event;
        };
    */
        /* notification types */
        const val SCTP_ASSOC_CHANGE = 0x0001
        const val SCTP_PEER_ADDR_CHANGE = 0x0002
        const val SCTP_REMOTE_ERROR = 0x0003
        const val SCTP_SEND_FAILED = 0x0004
        const val SCTP_SHUTDOWN_EVENT = 0x0005
        const val SCTP_ADAPTATION_INDICATION = 0x0006
        const val SCTP_PARTIAL_DELIVERY_EVENT = 0x0007
        const val SCTP_AUTHENTICATION_EVENT = 0x0008
        const val SCTP_STREAM_RESET_EVENT = 0x0009

        /**
         * When the SCTP implementation has no user data anymore to send or
         * retransmit this notification is given to the user.
         */
        const val SCTP_SENDER_DRY_EVENT = 0x000a
        const val SCTP_NOTIFICATIONS_STOPPED_EVENT = 0x000b
        const val SCTP_ASSOC_RESET_EVENT = 0x000c
        const val SCTP_STREAM_CHANGE_EVENT = 0x000d
        const val SCTP_SEND_FAILED_EVENT = 0x000e
        fun parse(data: ByteArray): SctpNotification {
            val type = data[1].toInt() and 0xFF shl 8 or (data[0].toInt() and 0xFF)
            return when (type) {
                SCTP_ASSOC_CHANGE -> AssociationChange(data)
                SCTP_PEER_ADDR_CHANGE -> PeerAddressChange(data)
                SCTP_SEND_FAILED -> SendFailed(data)
                SCTP_SENDER_DRY_EVENT -> SenderDry(data)
                SCTP_STREAM_RESET_EVENT -> StreamReset(data)
                else -> SctpNotification(data)
            }
        }
    }
}