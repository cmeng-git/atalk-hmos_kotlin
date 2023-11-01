/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.util

import android.content.*
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.util.AttributeSet
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import org.atalk.hmos.R
import timber.log.Timber
import java.util.*

/**
 * Implementation of a multiple selection spinner interface:
 * The dropdown list is implemented as a dialog in which items are kept separately from the spinner list.
 * Displaying of user selection is by setting spinner list to single item built from user selected items
 *
 * Note: Do not change extends Spinner, ignored android error highlight
 *
 * @author Eng Chong Meng
 */
class MultiSelectionSpinner : androidx.appcompat.widget.AppCompatSpinner, OnMultiChoiceClickListener, DialogInterface.OnCancelListener {
    private var items: List<String>? = null
    private var mSelected: BooleanArray? = null
    private var listener: MultiSpinnerListener? = null
    var mAdapter: ArrayAdapter<String>

    constructor(context: Context?) : super(context!!) {
        mAdapter = ArrayAdapter(context, R.layout.simple_spinner_item)
        super.setAdapter(mAdapter)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        mAdapter = ArrayAdapter(context, R.layout.simple_spinner_item)
        super.setAdapter(mAdapter)
    }

    override fun onClick(dialog: DialogInterface, which: Int, isChecked: Boolean) {
        if (mSelected != null && which < mSelected!!.size) {
            mSelected!![which] = isChecked
        } else {
            Timber.w("IllegalArgument Exception - 'which' is out of bounds. %s", which)
        }
    }

    /**
     * Internal call and when exit MultiSelectionSpinner:
     * update spinner UI and return call back to registered listener for action
     *
     * @param dialog
     */
    override fun onCancel(dialog: DialogInterface) {
        updateSpinnerSelection()
        if (listener != null) listener!!.onItemsSelected(this, mSelected)
    }

    /**
     * Build the dropdown list alert for user selection
     *
     * @return true always.
     */
    override fun performClick(): Boolean {
        val builder = AlertDialog.Builder(context)
        builder.setMultiChoiceItems(items!!.toTypedArray<CharSequence>(), mSelected, this)
        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, which: Int -> dialog.cancel() }
        builder.setOnCancelListener(this)
        builder.show()
        return true
    }

    /**
     * Build the dropdown list for user selection
     *
     * @param items list of options for user selection
     * @param listener callback when user exit the dialog
     */
    fun setItems(items: List<String>, listener: MultiSpinnerListener?) {
        this.items = items
        this.listener = listener

        // Default all to unselected; until setSelection()
        mSelected = BooleanArray(items.size)
        Arrays.fill(mSelected, false)
        mAdapter = ArrayAdapter(context, R.layout.simple_spinner_item, arrayOf("NONE"))
        adapter = mAdapter
    }

    /**
     * init the default selected options; usually followed by called to setItems
     *
     * @param selection default selected options
     */
    fun setSelection(selection: List<String>) {
        for (i in mSelected!!.indices) {
            mSelected!![i] = false
        }
        for (sel in selection) {
            for (j in items!!.indices) {
                if (items!![j] == sel) {
                    mSelected!![j] = true
                }
            }
        }
        updateSpinnerSelection()
    }

    /**
     * Select single option as per the given index
     *
     * @param index index to the items list
     */
    override fun setSelection(index: Int) {
        for (i in mSelected!!.indices) {
            mSelected!![i] = false
        }
        if (index >= 0 && index < mSelected!!.size) {
            mSelected!![index] = true
        } else {
            throw IllegalArgumentException("Index $index is out of bounds.")
        }
        updateSpinnerSelection()
    }

    /**
     * Select options as per the given indices
     *
     * @param index indices to the items list
     */
    fun setSelection(selectedIndices: IntArray) {
        for (i in mSelected!!.indices) {
            mSelected!![i] = false
        }
        for (index in selectedIndices) {
            if (index >= 0 && index < mSelected!!.size) {
                mSelected!![index] = true
            } else {
                throw IllegalArgumentException("Index $index is out of bounds.")
            }
        }
        updateSpinnerSelection()
    }

    /**
     * Return the selected options in List<String>
     *
     * @return List of selected options
    </String> */
    val selectedStrings: List<String>
        get() {
            val selection = LinkedList<String>()
            for (i in items!!.indices) {
                if (mSelected!![i]) {
                    selection.add(items!![i])
                }
            }
            return selection
        }

    /**
     * Return the selected options indices in List<Integer>
     *
     * @return List of selected options indeces
    </Integer> */
    val selectedIndices: List<Int>
        get() {
            val selection = LinkedList<Int>()
            for (i in items!!.indices) {
                if (mSelected!![i]) {
                    selection.add(i)
                }
            }
            return selection
        }

    /**
     * Build the user selected options as "," separated String
     *
     * @return selected options as String
     */
    val selectedItemsAsString: String
        get() {
            val sb = StringBuilder()
            var selectedItem = 0
            for (i in items!!.indices) {
                if (mSelected!![i]) {
                    if (selectedItem != 0) {
                        sb.append(", ")
                    }
                    sb.append(items!![i])
                    selectedItem++
                }
            }
            if (selectedItem == 0) {
                return selected_NONE
            } else if (selectedItem == items!!.size) {
                return selected_ALL
            }
            return sb.toString()
        }

    /**
     * Display the selected options in spinner UI
     */
    fun updateSpinnerSelection() {
        val spinnerText = selectedItemsAsString
        mAdapter = ArrayAdapter(context, R.layout.simple_spinner_item, arrayOf(spinnerText))
        adapter = mAdapter
    }

    /**
     * call back when use has exited the MultiSelectionSpinner
     * Note: the returned selected checks must be in the same order as the given items
     */
    interface MultiSpinnerListener {
        fun onItemsSelected(multiSelectionSpinner: MultiSelectionSpinner?, selected: BooleanArray?)
    }

    companion object {
        private const val selected_ALL = "ALL"
        private const val selected_NONE = "NONE"
    }
}