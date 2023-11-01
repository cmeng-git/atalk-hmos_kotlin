/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util.logging2

import com.google.common.collect.ImmutableMap
import org.atalk.util.collections.JMap
import java.lang.ref.WeakReference
import java.util.stream.Collectors

/**
 * Maintains a map of key-value pairs (both Strings) which holds
 * arbitrary context to use as a prefix for log messages.  Sub-contexts
 * can be created and will inherit any context values from their ancestors' context.
 */
// Supress warnings about access since this is a library and usages will occur outside this repo
open class LogContext protected constructor(context: Map<String, String>, ancestorsContext: ImmutableMap<String, String>) {
    /**
     * All context inherited from the 'ancestors' of this
     * LogContext
     */
    protected var ancestorsContext: ImmutableMap<String, String>

    /**
     * The context held by this specific LogContext.
     */
    protected var context: ImmutableMap<String, String>

    /**
     * The formatted String representing the total context
     * (the combination of the ancestors' context and this context)
     */
    var formattedContext: String? = null

    /**
     * Child LogContext's of this LogContext (which will be notified anytime this context changes)
     */
    private val childContexts = ArrayList<WeakReference<LogContext>>()

    @JvmOverloads
    constructor(context: Map<String, String> = emptyMap()) : this(context, ImmutableMap.of<String, String>()) {
    }

    init {
        this.context = ImmutableMap.copyOf(context)
        this.ancestorsContext = ancestorsContext
        updateFormattedContext()
    }

    @Synchronized
    protected fun updateFormattedContext() {
        val combined = combineMaps(ancestorsContext, context)
        formattedContext = formatContext(combined)
        updateChildren(combined)
    }

    @Synchronized
    fun createSubContext(childContextData: Map<String, String>): LogContext {
        val childAncestorContext = combineMaps(ancestorsContext, context)
        val child = LogContext(childContextData, childAncestorContext)
        childContexts.add(WeakReference(child))
        return child
    }

    fun addContext(key: String, value: String) {
        addContext(JMap.of(key, value))
    }

    @Synchronized
    fun addContext(addedContext: Map<String, String>) {
        context = combineMaps(context, addedContext)
        updateFormattedContext()
    }

    /**
     * Notify children of changes in this context
     */
    @Synchronized
    private fun updateChildren(newAncestorContext: ImmutableMap<String, String>) {
        val iter = childContexts.iterator()
        while (iter.hasNext()) {
            val c = iter.next().get()
            if (c != null) {
                c.ancestorContextUpdated(newAncestorContext)
            } else {
                iter.remove()
            }
        }
    }

    /**
     * Handle a change in the ancestors' context
     * @param newAncestorContext the ancestors' new context
     */
    @Synchronized
    protected fun ancestorContextUpdated(newAncestorContext: ImmutableMap<String, String>) {
        ancestorsContext = newAncestorContext
        updateFormattedContext()
    }

    override fun toString(): String {
        return formattedContext!!
    }

    companion object {
        private const val CONTEXT_START_TOKEN = "["
        private const val CONTEXT_END_TOKEN = "]"

        /**
         * Combine all the given maps into a new map.  Note that the order in which the maps
         * are passed matters: keys in later maps will override duplicates in earlier maps.
         * @param maps the maps to combine, in order of lowest to highest priority for keys
         * @return an *unmodifiable* combined map containing all the data of the given maps
         */
        @SafeVarargs
        private fun combineMaps(vararg maps: Map<String, String>): ImmutableMap<String, String> {
            val combinedMap = HashMap<String?, String>()
            for (map in maps) {
                combinedMap.putAll(map)
            }
            return ImmutableMap.copyOf(combinedMap)
        }

        protected fun formatContext(context: Map<String, String>): String {
            val contextString = java.lang.StringBuilder()
            val data = context.entries
                .stream()
                .map { (key, value): Map.Entry<String, String> -> "$key=$value" }
                .collect(Collectors.joining(" "))
            contextString.append(data)

            return if (contextString.isNotEmpty()) {
                CONTEXT_START_TOKEN + contextString + CONTEXT_END_TOKEN
            }
            else {
                ""
            }
        }
    }
}