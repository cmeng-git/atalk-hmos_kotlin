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
package org.atalk.hmos.gui.chatroomslist

import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import net.java.sip.communicator.service.muc.ChatRoomWrapper
import org.atalk.hmos.R
import org.atalk.hmos.gui.util.MultiSelectionSpinner
import org.atalk.hmos.gui.util.MultiSelectionSpinner.MultiSpinnerListener
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiFragment
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.XMPPException
import org.jivesoftware.smackx.muc.MultiUserChat
import org.jivesoftware.smackx.muc.MultiUserChatManager
import org.jivesoftware.smackx.xdata.BooleanFormField
import org.jivesoftware.smackx.xdata.FormField
import org.jivesoftware.smackx.xdata.FormField.Option
import org.jivesoftware.smackx.xdata.FormField.Type
import org.jivesoftware.smackx.xdata.ListMultiFormField
import org.jivesoftware.smackx.xdata.ListSingleFormField
import org.jivesoftware.smackx.xdata.form.FillableForm
import org.jivesoftware.smackx.xdata.form.Form
import timber.log.Timber
import java.util.*

/**
 * The user interface that allows user to configure the room properties.
 *
 * @author Eng Chong Meng
 */
class ChatRoomConfiguration : OSGiFragment() {
    private var mContext: Context? = null
    private var multiUserChat: MultiUserChat? = null

    /**
     * The list of form fields in the room configuration stanza
     */
    private var formFields: List<FormField> = ArrayList()

    /**
     * Room configuration reply submit form
     */
    private var replyForm: FillableForm? = null

    /**
     * Map contains a list of the user selected/changed room properties
     */
    private val configUpdates = HashMap<String, Any>()

    /**
     * The Room configuration list view adapter for user selection
     */
    private var configListAdapter: ConfigListAdapter? = null

    /**
     * View for room configuration title description from the room configuration form
     */
    private var mTitle: TextView? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mContext = this.context
        val contentView = inflater.inflate(R.layout.chatroom_config, container, false)
        mTitle = contentView.findViewById(R.id.config_title)
        val configListView = contentView.findViewById<ListView>(R.id.formListView)
        configListAdapter = ConfigListAdapter(inflater)
        configListView.setAdapter(configListAdapter)
        val cancelButton = contentView.findViewById<Button>(R.id.rcb_Cancel)
        cancelButton.setOnClickListener { v: View? -> onBackPressed() }
        val submitButton = contentView.findViewById<Button>(R.id.rcb_Submit)
        submitButton.setOnClickListener { v: View? -> if (processRoomConfiguration()) onBackPressed() }
        return contentView
    }

    /**
     * Use internal or call from ChatActivity: method not supported in a fragment.
     * Fragment does not support onBackPressed method.
     */
    fun onBackPressed() {
        val fm = parentFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack()
            if (mCrcListener != null) mCrcListener!!.onConfigComplete(configUpdates)
        }
    }

    /**
     * Process the user selected room configurations (in configAnswers) and submit them to server.
     * Note: any properties for no persistent room will be reset to default when last participant left.
     */
    private fun processRoomConfiguration(): Boolean {
        val updates = Collections.synchronizedMap(configUpdates)
        for ((variable, value) in updates) {
            try {
                if (value is Boolean) {
                    replyForm!!.setAnswer(variable, value)
                } else if (value is String) {
                    replyForm!!.setAnswer(variable, value)
                } else if (value is ArrayList<*>) {
                    replyForm!!.setAnswer(variable, value as Collection<CharSequence>)
                } else {
                    Timber.w("UnSupported argument type: %s -> %s", variable, value)
                }
            } catch (e: IllegalArgumentException) {
                Timber.w("Illegal Argument Exception: %s -> %s; %s", variable, value, e.message)
            }
        }
        // submit the room configuration to server
        if (updates.isNotEmpty() && multiUserChat != null) {
            try {
                multiUserChat!!.sendConfigurationForm(replyForm)
            } catch (e: SmackException.NoResponseException) {
                Timber.w("Room configuration submit exception: %s", e.message)
            } catch (e: XMPPException.XMPPErrorException) {
                Timber.w("Room configuration submit exception: %s", e.message)
            } catch (e: SmackException.NotConnectedException) {
                Timber.w("Room configuration submit exception: %s", e.message)
            } catch (e: InterruptedException) {
                Timber.w("Room configuration submit exception: %s", e.message)
            }
        }
        return true
    }

    /**
     * Adapter displaying all the available room configuration properties for user selection.
     */
    private inner class ConfigListAdapter(inflater: LayoutInflater) : BaseAdapter() {
        private val mInflater: LayoutInflater

        init {
            mInflater = inflater
            getRoomConfig().execute()
        }

        override fun getCount(): Int {
            return formFields.size
        }

        override fun getItem(position: Int): Any {
            return formFields[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            var viewType = -1
            val formField = formFields[position]
            if (formField != null) {
                viewType = formField.type.ordinal
            }
            return viewType
        }

        override fun getViewTypeCount(): Int {
            return Type.values().size
        }

        override fun isEmpty(): Boolean {
            return getCount() == 0
        }

        override fun getView(position: Int, convertView_: View?, parent: ViewGroup): View {
            var convertView = convertView_!!
            val ffOptions: List<Option>
            val optionList = ArrayList<String>()
            var valueList = MutableList(0){""}
            val mapOption = HashMap<String, String>()
            val textLabel: TextView
            val editText: EditText
            val ff = formFields[position]
            val mSpinner = convertView.findViewById<MultiSelectionSpinner>(R.id.cr_Spinner)

            if (ff != null) {
                val fieldName = ff.fieldName
                val label = ff.label
                var firstValue = ff.firstValue
                val objValue = configUpdates[fieldName]
                val formType = ff.type

                try {
                    when (formType) {
                        Type.bool -> {
                            convertView = mInflater.inflate(R.layout.chatroom_config_boolean, parent, false)
                            val cb = convertView.findViewById<CheckBox>(R.id.cb_formfield)
                            cb.text = label
                            if (objValue is Boolean) {
                                cb.isChecked = objValue
                            } else {
                                cb.isChecked = (ff as BooleanFormField).valueAsBoolean
                            }
                            cb.setOnCheckedChangeListener { cb1: CompoundButton?, isChecked: Boolean -> configUpdates.put(fieldName, isChecked) }
                        }
                        Type.list_multi -> {
                            convertView = mInflater.inflate(R.layout.chatroom_config_list_multi, parent, false)
                            textLabel = convertView.findViewById(R.id.cr_attr_label)
                            textLabel.text = label
                            valueList = if (objValue is ArrayList<*>) {
                                objValue as MutableList<String>
                            } else {
                                ff.valuesAsString
                            }

                            // Create both optionList and valueList both using optLabels as keys
                            mapOption.clear()
                            ffOptions = (ff as ListMultiFormField).options
                            for (option in ffOptions) {
                                val optLabel = option.label
                                val optValue = option.valueString
                                mapOption[optLabel] = optValue
                                val index = valueList.indexOf(optValue)
                                if (index != -1) valueList[index] = optLabel
                                optionList.add(optLabel)
                            }

                            mSpinner.setItems(optionList, object : MultiSpinnerListener {
                                override fun onItemsSelected(multiSelectionSpinner: MultiSelectionSpinner?, selected: BooleanArray?) {
                                    val selection = ArrayList<String?>()
                                    for (i in optionList.indices) {
                                        if (selected!![i]) {
                                            val optSelected = optionList[i]
                                            selection.add(mapOption[optSelected])
                                        }
                                    }
                                    configUpdates[fieldName] = selection
                                }
                            })
                            mSpinner.setSelection(valueList)
                        }

                        Type.list_single -> {
                            convertView = mInflater.inflate(R.layout.chatroom_config_list_single, parent, false)
                            textLabel = convertView.findViewById(R.id.cr_attr_label)
                            textLabel.text = label
                            mapOption.clear()
                            ffOptions = (ff as ListSingleFormField).options
                            for (option in ffOptions) {
                                val optLabel = option.label
                                val optValue = option.valueString

                                mapOption[optLabel] = optValue
                                valueList.add(optValue)
                                optionList.add(optLabel)
                            }

                            val arrayAdapter = ArrayAdapter(mContext!!, R.layout.simple_spinner_item, optionList)
                            arrayAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
                            mSpinner.adapter = arrayAdapter
                            if (objValue is String) {
                                firstValue = objValue
                            }

                            mSpinner.setSelection(valueList.indexOf(firstValue), false)
                            mSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, position: Int, id: Long) {
                                    val optSelected = optionList[position]
                                    configUpdates[fieldName] = mapOption[optSelected] as Any
                                }

                                override fun onNothingSelected(parentView: AdapterView<*>?) {
                                    // your code here
                                }
                            }
                        }
                        Type.text_private -> {
                            convertView = mInflater.inflate(R.layout.chatroom_config_text_private, parent, false)
                            textLabel = convertView.findViewById(R.id.cr_attr_label)
                            textLabel.text = label
                            if (objValue is String) {
                                firstValue = objValue
                            }
                            editText = convertView.findViewById(R.id.passwordField)
                            editText.setText(firstValue)
                            editText.addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    if (s != null) {
                                        configUpdates[fieldName] = s.toString()
                                    }
                                }

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            })
                            val pwdCheckBox = convertView.findViewById<CheckBox>(R.id.show_password)
                            pwdCheckBox.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> ViewUtil.showPassword(editText, isChecked) }
                        }
                        Type.text_single, Type.text_multi -> {
                            convertView = mInflater.inflate(R.layout.chatroom_config_text_single, parent, false)
                            textLabel = convertView.findViewById(R.id.cr_attr_label)
                            textLabel.text = label
                            if (objValue is String) {
                                firstValue = objValue
                            }
                            editText = convertView.findViewById(R.id.cr_attr_value)
                            editText.setText(firstValue)
                            editText.addTextChangedListener(object : TextWatcher {
                                override fun afterTextChanged(s: Editable?) {
                                    if (s != null) {
                                        configUpdates[fieldName] = s.toString()
                                    }
                                }

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            })
                        }
                        Type.fixed, Type.jid_multi, Type.jid_single -> {
                            Timber.w("Unhandled formField type: %s; variable: %s; %s=%s", formType.toString(),
                                    fieldName, label, firstValue)
                            // convertView cannot be null, so just return an empty view
                            convertView = mInflater.inflate(R.layout.chatroom_config_none, parent, false)
                        }
                        Type.hidden -> convertView = mInflater.inflate(R.layout.chatroom_config_none, parent, false)
                    }
                    convertView.tag = fieldName
                } catch (e: Exception) {
                    Timber.w("Exception in get View for variable %s; %s=%s; %s %s", fieldName, label, firstValue,
                            configUpdates[fieldName], e.message)
                }
            }
            return convertView
        }

        /**
         * Retrieve the chatRoom configuration fields from the server and init the default replyFrom
         * Populate the fragment with the available options in getView()
         */
        private inner class getRoomConfig : AsyncTask<Void?, Void?, Form?>() {
            override fun onPreExecute() {
                val mMucMgr = MultiUserChatManager.getInstanceFor(mChatRoomWrapper!!.protocolProvider!!.connection)
                multiUserChat = mMucMgr.getMultiUserChat(mChatRoomWrapper!!.entityBareJid)
            }

            override fun doInBackground(vararg params: Void?): Form {
                var initForm: Form? = null
                try {
                    initForm = multiUserChat!!.getConfigurationForm()
                } catch (e: SmackException.NoResponseException) {
                    Timber.w("Exception in get room configuration form %s", e.message)
                } catch (e: XMPPException.XMPPErrorException) {
                    Timber.w("Exception in get room configuration form %s", e.message)
                } catch (e: SmackException.NotConnectedException) {
                    Timber.w("Exception in get room configuration form %s", e.message)
                } catch (e: InterruptedException) {
                    Timber.w("Exception in get room configuration form %s", e.message)
                }
                return initForm!!
            }

            override fun onPostExecute(initForm: Form?) {
                if (initForm != null) {
                    mTitle!!.text = initForm.title
                    formFields = initForm.dataForm.fields
                    replyForm = initForm.fillableForm
                }
                configListAdapter!!.notifyDataSetChanged()
            }
        }
    }

    interface ChatRoomConfigListener {
        fun onConfigComplete(configUpdates: Map<String, Any>?)
    }

    companion object {
        /**
         * Declare as static to support rotation, otherwise crash when user rotate
         * Instead of using save Bundle approach
         */
        private var mChatRoomWrapper: ChatRoomWrapper? = null
        private var mCrcListener: ChatRoomConfigListener? = null

        /**
         * Constructs the `ChatRoomConfiguration`.
         *
         * mContext the `ChatActivity` corresponding to the `Chat Session`
         * @param chatRoomWrapper user joined ChatRoomWrapper for the `Chat Session`
         */
        fun getInstance(chatRoomWrapper: ChatRoomWrapper?, crcListener: ChatRoomConfigListener?): ChatRoomConfiguration {
            val fragment = ChatRoomConfiguration()
            mChatRoomWrapper = chatRoomWrapper
            mCrcListener = crcListener
            return fragment
        }
    }
}