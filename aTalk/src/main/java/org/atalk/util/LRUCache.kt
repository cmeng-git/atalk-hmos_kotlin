/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util

import java.util.*

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class LRUCache<K, V>
/**
 * Initializes a [LRUCache] with a given size using insertion order.
 *
 * @param cacheSize the maximum number of entries.
 */
@JvmOverloads constructor(
        /**
         * The maximum number of entries this cache will store.
         */
        private val cacheSize: Int, accessOrder: Boolean = false) : LinkedHashMap<K, V>(16 /* DEFAULT_INITIAL_CAPACITY */,
        0.75f /* DEFAULT_LOAD_FACTOR */,
        accessOrder) {
    /**
     * Initializes a [LRUCache] with a given size using either
     * insertion or access order depending on `accessOrder`.
     *
     * @param cacheSize the maximum number of entries.
     * @param accessOrder `true` to use access order, and `false` to use insertion order.
     */
    /**
     * {@inheritDoc}
     */
    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
        return size > cacheSize
    }

    companion object {
        /**
         * Creates a new LRU set. For a set with insertion order
         * (`accessOrder = false`), only inserting new elements in the set
         * is taken into account. With access order, any insertion (even for
         * elements already in the set) "touches" them.
         *
         * @param cacheSize the maximum number of entries.
         * @param accessOrder `true` to use access order, and `false` to use insertion order.
         */
        fun <T> lruSet(cacheSize: Int, accessOrder: Boolean): Set<T> {
            return Collections.newSetFromMap(LRUCache(cacheSize, accessOrder))
        }
    }
}