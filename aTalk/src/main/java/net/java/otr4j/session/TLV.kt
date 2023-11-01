package net.java.otr4j.session

class TLV(type: Int, value: ByteArray?) {
    var type = 0
    var value: ByteArray?

    init {
        this.type = type
        this.value = value
    }

    companion object {
        /* This is just padding for the encrypted message, and should be ignored. */
        const val PADDING = 0

        /* The sender has thrown away his OTR session keys with you */
        const val DISCONNECTED = 0x0001

        /* The message contains a step in the Socialist Millionaires' Protocol. */
        const val SMP1 = 0x0002
        const val SMP2 = 0x0003
        const val SMP3 = 0x0004
        const val SMP4 = 0x0005
        const val SMP_ABORT = 0x0006

        /*
	 * Like OTRL_TLV_SMP1, but there's a question for the buddy at the beginning
	 */
        const val SMP1Q = 0x0007
    }
}