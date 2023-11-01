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
package org.atalk.impl.neomedia.device

import org.atalk.service.neomedia.MediaDirection
import javax.media.Player
import javax.media.Processor
import javax.media.protocol.CaptureDevice
import javax.media.protocol.DataSource

/**
 * Implements a `MediaDevice` which provides silence in the form of audio
 * media and does not play back any (audio) media (because Jitsi Videobridge is
 * a server-side technology).
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
class AudioSilenceMediaDevice : AudioMediaDeviceImpl {
    /**
     * The value to pass as the `clockOnly` flag to [ ] when creating a [CaptureDevice].
     * See [AudioSilenceCaptureDevice.clockOnly].
     */
    private var clockOnly = true

    /**
     * Initializes a new [AudioSilenceMediaDevice] instance.
     */
    constructor() {}

    /**
     * Initializes a new [AudioSilenceMediaDevice] instance.
     * @param clockOnly the value to set to [.clockOnly].
     */
    constructor(clockOnly: Boolean) {
        this.clockOnly = clockOnly
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to initialize a `CaptureDevice`
     * without asking FMJ to initialize one for a `CaptureDeviceInfo`.
     */
    override fun createCaptureDevice(): CaptureDevice {
        return AudioSilenceCaptureDevice(clockOnly)
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to disable the very playback because
     * Jitsi Videobridge is a server-side technology.
     */
    override fun createPlayer(dataSource: DataSource): Processor? {
        return null
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to initialize a
     * `MediaDeviceSession` which disables the very playback because
     * Jitsi Videobridge is a server-side technology.
     */
    override fun createSession(): MediaDeviceSession {
        return object : AudioMediaDeviceSession(this as AbstractMediaDevice) {
            /**
             * {@inheritDoc}
             *
             * Overrides the super implementation to disable the
             * very playback because Jitsi Videobridge is a
             * server-side technology.
             */
            override fun createPlayer(dataSource: DataSource): Player? {
                return null
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to always return
     * [MediaDirection.SENDRECV] because this instance stands for a relay
     * and because the super bases the `MediaDirection` on the
     * `CaptureDeviceInfo` which this instance does not have.
     */
    override val direction: MediaDirection
        get() = MediaDirection.SENDRECV
}