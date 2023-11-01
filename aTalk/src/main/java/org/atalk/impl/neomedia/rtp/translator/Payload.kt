package org.atalk.impl.neomedia.rtp.translator

import javax.media.rtp.OutputDataStream

/**
 * A `Payload` type that can be written to an `OutputDataStream`.
 *
 * @author George Politis
 */
interface Payload {
    fun writeTo(stream: OutputDataStream)
}