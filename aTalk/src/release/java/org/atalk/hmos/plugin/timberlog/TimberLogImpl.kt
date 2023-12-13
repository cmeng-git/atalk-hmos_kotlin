package org.atalk.hmos.plugin.timberlog

import timber.log.Timber.Forest.plant

object TimberLogImpl {
    fun init() {
        // Init the crash reporting lib
        // Crashlytics.start();
        plant(object : ReleaseTree() {
            override fun createStackElementTag(element: StackTraceElement): String {
                return String.format("(%s:%s)#%s",
                    element.fileName,
                    element.lineNumber,
                    element.methodName)
            }
        })
    }
}