/*
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
package net.java.otr4j.util

import java.util.*

/**
 * Map wrapper that additionally stores which item is selected. The selection
 * may be null. If it is not null, then the key must exist in the provided base map.
 * @author Danny van Heumen
 *
 * @param <K> Key type
 * @param <V> Value type
 */
class SelectableMap<K, V>(base: MutableMap<K, V>) : MutableMap<K, V> {
    /**
     * Create a selectable map with initial selection.
     *
     * @param base the base map
     *selected the initially selected entry
     */
    constructor(base: MutableMap<K, V>, selected: K) : this(base) {
        select(selected)
    }

    /**
     * Map instance that is at the basis of the selectable map.
     */
    private val base: MutableMap<K, V>

    /**
     * Indicates that a selection is made.
     *
     * If true, then a selection is made, even if the selected key is null.
     */
    @Volatile
    var isSelected: Boolean
        private set

    /**
     * The key of the selected entry in the map.
     */
    @Volatile
    private var selection: K?

    /**
     * Create a selectable map without initial selection.
     *
     * base the base map
     */
    init {
        if (base == null) {
            throw NullPointerException("base")
        }
        this.base = base
        selection = null
        isSelected = false
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = Collections.unmodifiableSet(base.entries)

    override val keys: MutableSet<K>
        get() = Collections.unmodifiableSet(base.keys)

    override val size: Int
        get() = base.size

    override val values: MutableCollection<V>
        get() = Collections.unmodifiableCollection(base.values)

    override fun isEmpty(): Boolean {
        return base.isEmpty()
    }

    override fun remove(key: K): V? {
        if (isSelected && selection == key) {
            deselect()
        }
        return base.remove(key)
    }

    override fun get(key: K): V? {
        return base[key]
    }

    override fun put(key: K, value: V): V? {
        return base.put(key, value)
    }

    override fun putAll(from: Map<out K, V>) {
        base.putAll(from)
    }

    override fun clear() {
        deselect()
        base.clear()
    }

    override fun containsValue(value: V): Boolean {
        return base.containsValue(value)
    }

    override fun containsKey(key: K): Boolean {
        return base.containsKey(key)
    }

    fun getSelected(): V? {
        check(isSelected) { "no selection available" }
        return base[selection]
    }

    fun select(key: K) {
        // verify that key exists before changing selected
        require(base.containsKey(key)) { "key is not in base map" }
        selection = key
        isSelected = true
    }

    fun deselect() {
        isSelected = false
    }
}