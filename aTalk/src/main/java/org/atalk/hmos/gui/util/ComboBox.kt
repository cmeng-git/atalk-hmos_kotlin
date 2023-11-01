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

import android.content.Context
import android.database.Cursor
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import org.atalk.hmos.R

/**
 * Custom ComboBox for Android
 *
 * @author Eng Chong Meng
 */
class ComboBox : LinearLayout {
    protected var _text: AutoCompleteTextView? = null
    protected var spinnerList: List<String?>? = null
    private val unit = TypedValue.COMPLEX_UNIT_SP
    private val fontSize = 15f
    private val fontBlack = resources.getColor(R.color.textColorBlack, null)
    private var mContext: Context? = null
    private var inflater: LayoutInflater? = null

    constructor(context: Context) : super(context) {
        createChildControls(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        createChildControls(context)
    }

    private fun createChildControls(context: Context) {
        mContext = context
        inflater = mContext!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        this.orientation = HORIZONTAL
        this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        _text = AutoCompleteTextView(context)
        _text!!.dropDownWidth = -1 // set the dropdown width to match screen
        _text!!.setTextSize(unit, fontSize)
        _text!!.setTextColor(fontBlack)
        _text!!.setSingleLine()
        _text!!.inputType = (InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        _text!!.setRawInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD)
        this.addView(_text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f))
        val _button = ImageButton(context)
        _button.setImageResource(android.R.drawable.arrow_down_float)
        _button.setOnClickListener { v: View? ->
            if (!TextUtils.isEmpty(text) && !spinnerList!!.contains(text)) {
                ViewUtil.hideKeyboard(mContext, _text!!)
                setSuggestionSource(spinnerList) // rest to user supplied list
            }
            _text!!.showDropDown()
        }
        this.addView(_button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * Sets the source for DDLB suggestions. Cursor MUST be managed by supplier!!
     *
     * @param source Source of suggestions.
     * @param column Which column from source to show.
     */
    fun setSuggestionSource(source: Cursor, column: String) {
        val from = arrayOf(column)
        val to = intArrayOf(android.R.id.text1)
        val cursorAdapter = SimpleCursorAdapter(this.context,
                R.layout.simple_spinner_dropdown_item, source, from, to)

        // this is to ensure that when suggestion is selected it provides the value to the textBox
        cursorAdapter.stringConversionColumn = source.getColumnIndex(column)
        _text!!.setAdapter(cursorAdapter)
    }

    fun setSuggestionSource(list: List<String?>?) {
        spinnerList = list

        // Create an ArrayAdapter using the string array and custom spinner item with radio button
        val mAdapter = object : ArrayAdapter<String?>(this.context, R.layout.simple_spinner_item, list!!) {
            // Allow to change font style in dropdown vew
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var convertView = convertView
                if (convertView == null) {
                    convertView = inflater!!.inflate(R.layout.adapter_radio_item, null)
                }
                val name = convertView!!.findViewById<TextView>(R.id.item_name)
                val radio = convertView.findViewById<RadioButton>(R.id.item_radio)
                val variation = list!![position]
                name.text = variation
                val mSelected = list.indexOf(text)
                radio.isChecked = position == mSelected
                return convertView
            }
        }

        // Specify the layout to use when the list of choices appears
        mAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)

        // Apply the adapter to the ComboBox
        _text!!.setAdapter(mAdapter)
    }
    /**
     * Gets the text in the combo box.
     *
     * @return Text or null if text isEmpty().
     */
    /**
     * Sets the text in combo box.
     */
    var text: String?
        get() = ViewUtil.toString(_text)
        set(text) {
            _text!!.setText(text)
        }

    /**
     * Sets the textSize in comboBox.
     */
    fun setTextSize(size: Float) {
        this.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }

    /**
     * Sets the unit and textSize in comboBox.
     */
    fun setTextSize(unit: Int, size: Float) {
        _text!!.setTextSize(unit, size)
    }

    /**
     * Set the call back when an item in the combo box dropdown list item is selected
     *
     * @param l AdapterView OnItemClickListener
     */
    fun setOnItemClickListener(l: OnItemClickListener?) {
        _text!!.onItemClickListener = l
    }
}