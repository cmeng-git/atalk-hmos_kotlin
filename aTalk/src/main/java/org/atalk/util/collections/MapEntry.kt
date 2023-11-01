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
package org.atalk.util.collections

/**
 * A constructable implementation of Map.Entry (none is provided by the
 * stdlib by default)
 * @param <K> the key type
 * @param <V> the value type
</V></K> */
class MapEntry<K, V>(
        /**
         * {@inheritDoc}
         */
        override val key: K,
        /**
         * {@inheritDoc}
         */
        override var value: V
) : MutableMap.MutableEntry<K, V> {

    /**
     * {@inheritDoc}
     */
    override fun setValue(value: V): V {
        val old = this.value
        this.value = value
        return old
    }
}