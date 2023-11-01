package org.atalk.hmos.plugin.timberlog

import timber.log.Timber.Forest.plant
import java.util.*

object TimberLogImpl {
    fun init() {
        plant(object : DebugTreeExt() {
            override fun createStackElementTag(element: StackTraceElement): String? {
                return String.format(Locale.US, "(%s:%s)#%s",
                        element.fileName,
                        element.lineNumber,
                        element.methodName)
            }
        })
    }
}