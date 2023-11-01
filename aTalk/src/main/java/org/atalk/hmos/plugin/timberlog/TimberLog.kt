package org.atalk.hmos.plugin.timberlog

object TimberLog {
    /**
     * Priority constant for the println method; use Timber.log; mainly for fine tracing debug messages
     */
    const val FINER = 10
    const val FINEST = 11

    /*
     * Set this to true to enable Timber.FINEST for tracing debug message.
     * It is also used to collect and format info for more detailed debug message display.
     */
    @JvmField
    var isFinestEnable = false

    /*
     * Set this to true to enable Timber.FINER tracing debug message
     */
    @JvmField
    var isTraceEnable = false

    /**
     * To specify if the info logging is enabled for released version
     */
    @JvmField
    var isInfoEnable = true
}