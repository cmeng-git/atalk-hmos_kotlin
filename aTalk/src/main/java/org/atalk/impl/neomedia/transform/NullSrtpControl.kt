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
package org.atalk.impl.neomedia.transform

import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.service.neomedia.AbstractSrtpControl
import org.atalk.service.neomedia.SrtpControl
import org.atalk.service.neomedia.SrtpControlType
import org.atalk.util.MediaType

/**
 * Implements a no-op [SrtpControl], i.e. one which does not perform
 * SRTP and does not have a transform engine.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class NullSrtpControl
/**
 * Initializes a new [NullSrtpControl] instance.
 */
    : AbstractSrtpControl<SrtpControl.TransformEngine>(SrtpControlType.NULL) {

    /**
     * {@inheritDoc}
     */
    override val secureCommunicationStatus = false

    /**
     * {@inheritDoc}
     */
    override fun requiresSecureSignalingTransport(): Boolean {
        return false
    }

    /**
     * {@inheritDoc}
     */
    override fun setConnector(connector: AbstractRTPConnector?) {}

    /**
     * {@inheritDoc}
     */
    override fun start(mediaType: MediaType) {}

    /**
     * {@inheritDoc}
     */
    override fun createTransformEngine(): SrtpControl.TransformEngine? {
        return null
    }
}