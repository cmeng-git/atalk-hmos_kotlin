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
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object ArrayUtils {
    /**
     * Adds a specific element to a specific array with a specific component
     * type if the array does not contain the element yet.
     *
     * @param array the array to add `element` to
     * @param componentType the component type of `array`
     * @param element the element to add to `array`
     * @return an array with the specified `componentType` and
     * containing `element`. If `array` contained `element`
     * already, returns `array`.
     */
    fun <T> add(array: Array<T>?, componentType: Class<T>, element: T?): Array<T> {
        var array_ = array

        if (element == null) throw NullPointerException("element")

        array_ = if (array_ == null) {
            java.lang.reflect.Array.newInstance(componentType, 1) as Array<T>
        } else {
            for (a in array_) {
                if (element == a) return array_
            }
            val newArray = java.lang.reflect.Array.newInstance(componentType, array_.size + 1) as Array<T>
            System.arraycopy(array_, 0, newArray, 0, array_.size)
            newArray
        }
        array_[array_.size - 1] = element
        return array_
    }

    /**
     * Inserts the given element into an open (null) slot in the array if there is one,
     * otherwise creates a new array and adds all existing elements and the given element
     *
     * @param element the element to add
     * @param array the array to add to, if possible
     * @param componentType the class type of the array (used if a new one needs to be allocated)
     * @param <T> the type of the element
     * @return an array containing all the elements in the array that was passed,
     * as well as the given element.  May or may not be the original array.
    </T> */
    fun <T> insert(array: Array<T>?, componentType: Class<T>, element: T): Array<T> {
        var arrayToReturn = array!!
        var inserted = false
        for (i in array.indices) {
            if (array[i] == null) {
                array[i] = element
                inserted = true
                break
            }
        }
        if (!inserted) {
            arrayToReturn = add(array, componentType, element)
        }
        return arrayToReturn
    }

    /**
     * Concatenates two arrays.
     *
     * @param first
     * @param second
     * @param <T>
     * @return
    </T> */
    fun <T> concat(first: Array<T>, second: Array<T>): Array<T> {
        return if (isNullOrEmpty(first)) {
            second
        } else if (isNullOrEmpty(second)) {
            first
        } else {
            val result = Arrays.copyOf(first, first.size + second.size)
            System.arraycopy(second, 0, result, first.size, second.size)
            result
        }
    }

    /**
     * Tests whether the array passed in as an argument is null or empty.
     *
     * @param array
     * @param <T>
     * @return
    </T> */
    fun <T> isNullOrEmpty(array: Array<T>?): Boolean {
        return array == null || array.isEmpty()
    }
}