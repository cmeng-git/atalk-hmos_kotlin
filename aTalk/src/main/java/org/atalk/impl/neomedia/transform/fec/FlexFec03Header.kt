/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.transform.fec

/**
 * Model of the data contained in the FlexFEC -03 header
 *
 * @author bbaldino
 */
class FlexFec03Header
/**
 * Ctor.  This constructor is intended to be used when parsing an existing
 * FlexFEC-03 packet
 * @param protectedSsrc the media ssrc this fec packet protects
 * @param seqNumBase the base sequence number for this flexfec packet
 * @param protectedSeqNums the list of media sequence numbers this flexfec
 * packet protects
 * @param size the size of the header (in bytes)
 */
(
        /**
         * The SSRC of the media stream protected by this FEC packet
         */
        var protectedSsrc: Long,
        /**
         * The base sequence number from which the mask defines the sequence numbers
         * of the media packets protected by this packet
         */
        var seqNumBase: Int,
        /**
         * The list of sequence numbers of packets protected by this fec packet
         */
        var protectedSeqNums: List<Int>,
        /**
         * The size of the FlexFec header (in bytes) in this packet
         */
        var size: Int)