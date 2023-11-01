package net.java.otr4j

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
interface OtrKeyManagerStore {
    fun getPropertyBytes(id: String?): ByteArray?
    fun getPropertyBoolean(id: String, defaultValue: Boolean): Boolean
    fun setProperty(id: String?, value: ByteArray?)
    fun setProperty(id: String?, value: Boolean)
    fun removeProperty(id: String)
}