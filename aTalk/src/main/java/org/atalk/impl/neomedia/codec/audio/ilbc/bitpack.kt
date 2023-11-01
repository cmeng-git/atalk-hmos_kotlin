/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.ilbc

/**
 * @author Jean Lorchat
 */
internal class bitpack {
    var firstpart: Int
    var rest: Int

    constructor() {
        firstpart = 0
        rest = 0
    }

    constructor(fp: Int, r: Int) {
        firstpart = fp
        rest = r
    }
}