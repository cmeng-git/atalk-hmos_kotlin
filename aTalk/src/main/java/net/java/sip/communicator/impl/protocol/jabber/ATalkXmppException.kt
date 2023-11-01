package net.java.sip.communicator.impl.protocol.jabber

import org.jivesoftware.smack.XMPPException

/**
 * An implementation XMPPException for aTalk: mainly use of securityException
 *
 * @author Eng Chong Meng
 */
class ATalkXmppException : XMPPException {
    /**
     * @param ex the original exception root cause
     */
    constructor(message: String?) : super(message) {}

    /**
     * @param message the exception message
     * @param ex the original exception root cause
     */
    constructor(message: String?, ex: Exception?) : super(message, ex) {}
}