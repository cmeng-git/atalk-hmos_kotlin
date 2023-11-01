/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ChatRoomConfigurationForm
import net.java.sip.communicator.service.protocol.ChatRoomConfigurationFormField
import net.java.sip.communicator.service.protocol.OperationFailedException
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.form.FillableForm
import org.jivesoftware.smackx.xdata.form.Form
import timber.log.Timber
import java.util.*

/**
 * The Jabber implementation of the `ChatRoomConfigurationForm` interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ChatRoomConfigurationFormJabberImpl(
        /**
         * The smack multi-user chat is the one to which we'll send the form once filled out.
         */
        private val smackMultiUserChat: MultiUserChat?,
        /**
         * The smack chat room configuration form.
         */
        protected var smackConfigForm: Form) : ChatRoomConfigurationForm {
    /**
     * The form that will be filled out and submitted by user.
     */
    protected var smackSubmitForm: FillableForm

    /**
     * Creates an instance of `ChatRoomConfigurationFormJabberImpl` by specifying the
     * corresponding smack multi-user chat and smack configuration form.
     *
     * @param multiUserChat the smack multi-user chat, to which we'll send the configuration form once filled out
     * @param smackConfigForm the smack configuration form.
     */
    init {
        smackSubmitForm = smackConfigForm.fillableForm
    }

    /**
     * Returns an Iterator over a list of `ChatRoomConfigurationFormFields`.
     *
     * @return an Iterator over a list of `ChatRoomConfigurationFormFields`
     */
    override fun getConfigurationSet(): Iterator<ChatRoomConfigurationFormField?>? {
        val configFormFields = Vector<ChatRoomConfigurationFormField>()
        for (smackFormField in smackConfigForm.dataForm.fields) {
            if (smackFormField == null || smackFormField.type == FormField.Type.hidden) continue
            val jabberConfigField = ChatRoomConfigurationFormFieldJabberImpl(smackFormField, smackSubmitForm)
            configFormFields.add(jabberConfigField)
        }
        return Collections.unmodifiableList(configFormFields).iterator()
    }

    /**
     * Sends the ready smack configuration form to the multi user chat.
     */
    @Throws(OperationFailedException::class)
    override fun submit() {
        Timber.log(TimberLog.FINER, "Sends chat room configuration form to the server.")
        try {
            smackMultiUserChat!!.sendConfigurationForm(smackSubmitForm)
        } catch (e: XMPPException) {
            Timber.e(e, "Failed to submit the configuration form.")
            throw OperationFailedException("Failed to submit the configuration form.",
                    OperationFailedException.GENERAL_ERROR)
        } catch (e: NoResponseException) {
            Timber.e(e, "Failed to submit the configuration form.")
            throw OperationFailedException("Failed to submit the configuration form.",
                    OperationFailedException.GENERAL_ERROR)
        } catch (e: InterruptedException) {
            Timber.e(e, "Failed to submit the configuration form.")
            throw OperationFailedException("Failed to submit the configuration form.",
                    OperationFailedException.GENERAL_ERROR)
        } catch (e: NotConnectedException) {
            Timber.e(e, "Failed to submit the configuration form.")
            throw OperationFailedException("Failed to submit the configuration form.",
                    OperationFailedException.GENERAL_ERROR)
        }
    }
}