package net.java.otr4j.session

import java.net.ProtocolException

class UnknownInstanceException(host: String?) : ProtocolException(host)