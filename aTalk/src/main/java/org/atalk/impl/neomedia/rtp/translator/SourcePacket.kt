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
package org.atalk.impl.neomedia.rtp.translator

import javax.media.Buffer

/**
 * Privately used by [PushSourceStreamImpl] at the time of this writing and extracted into its
 * own file for the sake of readability.
 *
 * @author Lyubomir Marinov
 */
internal class SourcePacket(buf: ByteArray, off: Int, len: Int) : Buffer() {

    var buffer: ByteArray? = null
        private set

    var streamDesc: PushSourceStreamDesc? = null

    init {
        setData(buf)
        setOffset(off)
        setLength(len)
    }

    /**
     * {@inheritDoc}
     */
    override fun setData(data: Any) {
        super.setData(data)
        buffer = data as ByteArray
    }
}