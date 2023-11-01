/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.service.protocol

import org.atalk.service.neomedia.device.MediaDevice
import java.text.ParseException

/**
 * Represents an `OperationSet` giving access to desktop streaming specific functionality.
 *
 * @author Sebastien Vincent
 * @author Yana Stamcheva
 */
interface OperationSetDesktopStreaming : OperationSetVideoTelephony {
    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param uri
     * the address of the callee that we should invite to a new call.
     * @param mediaDevice
     * the media device to use for the desktop streaming
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipatnt instance with the use of the
     * corresponding method.
     * @throws OperationFailedException
     * with the corresponding code if we fail to create the video call.
     * @throws ParseException
     * if `callee` is not a valid address string.
     */
    @Throws(OperationFailedException::class, ParseException::class)
    fun createVideoCall(uri: String?, mediaDevice: MediaDevice?): Call<*>?

    /**
     * Create a new video call and invite the specified CallPeer to it.
     *
     * @param callee
     * the address of the callee that we should invite to a new call.
     * @param mediaDevice
     * the media device to use for the desktop streaming
     * @return CallPeer the CallPeer that will represented by the specified uri. All following state
     * change events will be delivered through that call peer. The Call that this peer is a
     * member of could be retrieved from the CallParticipant instance with the use of the
     * corresponding method.
     * @throws OperationFailedException
     * with the corresponding code if we fail to create the video call.
     */
    @Throws(OperationFailedException::class)
    fun createVideoCall(callee: Contact?, mediaDevice: MediaDevice?): Call<*>?

    /**
     * Sets the indicator which determines whether the streaming of local video in a specific
     * `Call` is allowed. The setting does not reflect the availability of actual video
     * capture devices, it just expresses the desire of the user to have the local video streamed in
     * the case the system is actually able to do so.
     *
     * @param call
     * the `Call` to allow/disallow the streaming of local video for
     * @param mediaDevice
     * the media device to use for the desktop streaming
     * @param allowed
     * `true` to allow the streaming of local video for the specified `Call`;
     * `false` to disallow it
     *
     * @throws OperationFailedException
     * if initializing local video fails.
     */
    @Throws(OperationFailedException::class)
    fun setLocalVideoAllowed(call: Call<*>?, mediaDevice: MediaDevice?, allowed: Boolean)

    /**
     * If the streaming is partial (not the full desktop).
     *
     * @param call
     * the `Call` whose video transmission properties we are interested in.
     * @return true if streaming is partial, false otherwise
     */
    fun isPartialStreaming(call: Call<*>?): Boolean

    /**
     * Move origin of a partial desktop streaming.
     *
     * @param call the `Call` whose video transmission properties we are interested in.
     * @param x new x coordinate origin
     * @param y new y coordinate origin
     */
    fun movePartialDesktopStreaming(call: Call<*>?, x: Int, y: Int)
}