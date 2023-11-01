/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.AbstractOperationSetAvatar
import net.java.sip.communicator.service.protocol.OperationSetServerStoredAccountInfo

/**
 * A simple implementation of the `OperationSetAvatar` interface for the jabber protocol.
 *
 * Actually there isn't any maximum size for the jabber protocol but GoogleTalk fix it a 96x96.
 *
 * @author Damien Roth
 * @author Eng Chong Meng
 */
class OperationSetAvatarJabberImpl
/**
 * Creates a new instances of `OperationSetAvatarJabberImpl`.
 *
 * @param parentProvider a reference to the `ProtocolProviderServiceJabberImpl` instance that created us.
 * @param accountInfoOpSet a reference to the `OperationSetServerStoredAccountInfo`.
 */
(parentProvider: ProtocolProviderServiceJabberImpl?,
        accountInfoOpSet: OperationSetServerStoredAccountInfo?) : AbstractOperationSetAvatar<ProtocolProviderServiceJabberImpl?>(parentProvider, accountInfoOpSet!!, 96, 96, 0)