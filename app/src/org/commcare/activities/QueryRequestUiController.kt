package org.commcare.activities

import android.content.ActivityNotFoundException
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.Pair
import com.google.android.material.datepicker.MaterialDatePicker
import org.commcare.dalvik.R
import org.commcare.interfaces.CommCareActivityUIController
import org.commcare.session.RemoteQuerySessionManager
import org.commcare.session.RemoteQuerySessionManager.extractMultipleChoices
import org.commcare.suite.model.QueryPrompt
import org.commcare.util.DateRangeUtils
import org.commcare.utils.AndroidXUtils
import org.commcare.views.ManagedUi
import org.commcare.views.UiElement
import org.commcare.views.ViewUtil
import org.commcare.views.widgets.SpinnerWidget
import org.commcare.views.widgets.WidgetUtils
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localizer
import java.text.ParseException
import java.util.*
import kotlin.collections.ArrayList

@ManagedUi(R.layout.http_request_layout)
class QueryRequestUiController(
    private val queryRequestActivity: QueryRequestActivity,
    private val remoteQuerySessionManager: RemoteQuerySessionManager
) : CommCareActivityUIController {

    companion object {
        private const val APPEARANCE_BARCODE_SCAN = "barcode_scan"
        private const val DATE_PICKER_FRAGMENT_TAG = "date_picker_dialog"
    }

    private var mPendingPromptId: String? = null
    private val promptsBoxes = Hashtable<String, View>()

    @UiElement(value = R.id.request_button, locale = "query.button")
    private lateinit var queryButton: Button

    @UiElement(value = R.id.error_message)
    private lateinit var errorTextView: TextView

    override fun setupUI() {
        buildPromptUI()
        queryButton.setOnClickListener { v ->
            ViewUtil.hideVirtualKeyboard(queryRequestActivity)
            queryRequestActivity.makeQueryRequest()
        }
    }

    override fun refreshView() {
        promptsBoxes.forEach { entry ->
            val input = entry.value
            if (input is Spinner) {
                setSpinnerData(
                    remoteQuerySessionManager.neededUserInputDisplays[entry.key]!!,
                    input
                )
            }
        }
    }
    fun reloadStateUsingAnswers(answeredPrompts: MutableMap<String, String>) {
        answeredPrompts.forEach { entry ->
            answerUserPrompt(entry.key, entry.value)
            val promptView = promptsBoxes[entry.key]
            val queryPrompt = remoteQuerySessionManager.neededUserInputDisplays[entry.key]
            if (queryPrompt!!.isSelect) {
                remoteQuerySessionManager.populateItemSetChoices(queryPrompt)
            }
            when (promptView) {
                is EditText -> {
                    promptView.setText(entry.value)
                }
                is Spinner -> {
                    setSpinnerData(queryPrompt, promptView)
                }
                is LinearLayout -> {
                    if (promptView.tag == QueryPrompt.INPUT_TYPE_CHECKBOX) {
                        setCheckboxData(queryPrompt, promptView, entry.value)
                    }
                }
            }
        }
    }

    fun setPendingPromptResult(result: String) {
        if (!TextUtils.isEmpty(mPendingPromptId)) {
            val input: View = promptsBoxes[mPendingPromptId]!!
            if (input is EditText) {
                input.setText(result)
            }
        }
    }

    fun showError(errorMessage: String) {
        errorTextView.text = errorMessage
        errorTextView.visibility = View.VISIBLE
    }

    fun hideError() {
        errorTextView.visibility = View.GONE
    }

    private fun buildPromptUI() {
        val promptsLayout: LinearLayout = queryRequestActivity.findViewById(R.id.query_prompts)
        val userInputDisplays = remoteQuerySessionManager.neededUserInputDisplays
        var promptCount = 1
        val en: Enumeration<*> = userInputDisplays.keys()
        while (en.hasMoreElements()) {
            val promptId = en.nextElement() as String
            val isLastPrompt = promptCount++ == userInputDisplays.size
            buildPromptEntry(
                promptsLayout, promptId,
                userInputDisplays[promptId]!!, isLastPrompt
            )
        }
    }

    private fun buildPromptEntry(
        promptsLayout: LinearLayout, promptId: String,
        queryPrompt: QueryPrompt, isLastPrompt: Boolean
    ) {
        if (remoteQuerySessionManager.isPromptSupported(queryPrompt)) {
            val promptView: View =
                LayoutInflater.from(queryRequestActivity)
                    .inflate(R.layout.query_prompt_layout, promptsLayout, false)
            setLabelText(promptView, queryPrompt)
            val inputView = buildPromptInputView(promptView, queryPrompt, isLastPrompt)
            setUpBarCodeScanButton(promptView, promptId, queryPrompt)
            promptsLayout.addView(promptView)
            promptsBoxes[promptId] = inputView
        }
    }

    private fun buildPromptInputView(promptView: View, queryPrompt: QueryPrompt, isLastPrompt: Boolean): View? {
        val input = queryPrompt.input
        var inputView: View? = null
        if (input == null) {
            inputView = buildEditTextView(promptView, queryPrompt, isLastPrompt)
        } else if (input.contentEquals(QueryPrompt.INPUT_TYPE_SELECT1)) {
            inputView = buildSpinnerView(promptView, queryPrompt)
        } else if (input.contentEquals(QueryPrompt.INPUT_TYPE_DATERANGE)) {
            inputView = buildDateRangeView(promptView, queryPrompt)
        } else if (input.contentEquals(QueryPrompt.INPUT_TYPE_CHECKBOX)) {
            inputView = buildCheckboxView(promptView, queryPrompt)
        }
        return inputView
    }

    private fun buildEditTextView(
        promptView: View, queryPrompt: QueryPrompt,
        isLastPrompt: Boolean
    ): EditText? {
        val promptEditText = promptView.findViewById<EditText>(R.id.prompt_et)
        promptEditText.visibility = View.VISIBLE
        promptView.findViewById<View>(R.id.prompt_spinner).visibility = View.GONE

        // needed to allow 'done' and 'next' keyboard action
        if (isLastPrompt) {
            promptEditText.imeOptions = EditorInfo.IME_ACTION_DONE
        } else {
            // replace 'done' on keyboard with 'next'
            promptEditText.imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        val userAnswers = remoteQuerySessionManager.userAnswers
        promptEditText.setText(userAnswers[queryPrompt.key])
        promptEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                answerUserPrompt(queryPrompt.key, s.toString())
                updateAnswerAndRefresh(queryPrompt, s.toString())
            }
        })
        return promptEditText
    }

    private fun answerUserPrompt(key: String, value: String) {
        // todo mobile don't support blank search yet
        if ("".equals(value)) {
            remoteQuerySessionManager.answerUserPrompt(key, null)
        } else {
            remoteQuerySessionManager.answerUserPrompt(key, value)
        }
    }

    private fun updateAnswerAndRefresh(queryPrompt: QueryPrompt, answer: String) {
        val userAnswers = remoteQuerySessionManager.userAnswers
        val oldAnswer = userAnswers[queryPrompt.key]
        if ((oldAnswer == null && !"".equals(answer)) || (oldAnswer != null && !oldAnswer.contentEquals(answer))) {
            answerUserPrompt(queryPrompt.key, answer)
            remoteQuerySessionManager.refreshItemSetChoices()
            refreshView()
        }
    }

    private fun buildCheckboxView(promptView: View, queryPrompt: QueryPrompt): View {
        val checkboxView = promptView.findViewById<LinearLayout>(R.id.prompt_checkbox)
        checkboxView.visibility = View.VISIBLE
        checkboxView.tag = QueryPrompt.INPUT_TYPE_CHECKBOX
        remoteQuerySessionManager.populateItemSetChoices(queryPrompt)
        var selectedPosAndChoices = calculateItemChoices(queryPrompt)
        val selectedPositions = selectedPosAndChoices.first
        val choices = selectedPosAndChoices.second
        val items = queryPrompt.itemsetBinding!!.choices
        items.forEachIndexed { index, item ->
            addCheckboxView(checkboxView, item, choices[index]!!, index in selectedPositions, items, queryPrompt)
        }
        return checkboxView
    }

    private fun addCheckboxView(
        promptInputView: LinearLayout,
        item: SelectChoice,
        choice: String,
        checked: Boolean,
        items: Vector<SelectChoice>,
        queryPrompt: QueryPrompt
    ) {
        val checkBox = CheckBox(queryRequestActivity)
        checkBox.text = choice
        checkBox.isChecked = checked
        checkBox.tag = item.index
        checkBox.setOnCheckedChangeListener { buttonView: CompoundButton, isChecked: Boolean ->
            val numberOfChoices = promptInputView.childCount
            val checkboxAnswers = ArrayList<String>()
            for (i in 0 until numberOfChoices) {
                val checkbox = promptInputView.getChildAt(i) as CheckBox
                if(checkbox.isChecked){
                    checkboxAnswers.add(items[checkbox.tag as Int].value)
                }
            }
            val answer = RemoteQuerySessionManager.joinMultipleChoices(checkboxAnswers)
            updateAnswerAndRefresh(queryPrompt, answer)
        }
        promptInputView.addView(checkBox)
    }


    private fun setCheckboxData(queryPrompt: QueryPrompt, promptView: LinearLayout, answer: String) {
        val promptAnswers = extractMultipleChoices(answer)
        val items = queryPrompt.itemsetBinding!!.choices
        val numberOfChoices = promptView.childCount
        for (i in 0 until numberOfChoices) {
            val checkbox = promptView.getChildAt(i) as CheckBox
            checkbox.isChecked = items[checkbox.tag as Int].value in promptAnswers
        }
    }

    private fun buildSpinnerView(promptView: View, queryPrompt: QueryPrompt): Spinner? {
        val promptSpinner = promptView.findViewById<Spinner>(R.id.prompt_spinner)
        promptSpinner.visibility = View.VISIBLE
        promptSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                var value = ""
                if (position > 0) {
                    val choices = queryPrompt.itemsetBinding!!.choices
                    val selectChoice = choices[position - 1]
                    value = selectChoice.value
                }
                updateAnswerAndRefresh(queryPrompt, value)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        remoteQuerySessionManager.populateItemSetChoices(queryPrompt)
        setSpinnerData(queryPrompt, promptSpinner)
        return promptSpinner
    }

    private fun setSpinnerData(queryPrompt: QueryPrompt, promptSpinner: Spinner) {
        var selectedPosAndChoices = calculateItemChoices(queryPrompt)
        val selectedPositions = selectedPosAndChoices.first
        val choices = selectedPosAndChoices.second
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            queryRequestActivity,
            android.R.layout.simple_spinner_item,
            SpinnerWidget.getChoicesWithEmptyFirstSlot(choices)
        )
        promptSpinner.adapter = adapter
        if (selectedPositions.size > 1) {
            throw InvalidPromptValueException("Can't set multiple values to Spinner")
        }
        for (selectedPosition in selectedPositions) {
            // first choice is blank in adapter
            promptSpinner.setSelection(selectedPosition + 1)
        }
    }

    private fun calculateItemChoices(queryPrompt: QueryPrompt): Pair<ArrayList<Int>, Array<String?>> {
        val items = queryPrompt.itemsetBinding!!.choices
        val choices = arrayOfNulls<String>(items.size)
        val userAnswers: Hashtable<String, String> = remoteQuerySessionManager.userAnswers
        val promptAnswers = extractMultipleChoices(userAnswers[queryPrompt.key])
        val selectedPositions = ArrayList<Int>()
        for (i in items.indices) {
            val item = items[i]
            choices[i] = item.labelInnerText
            if (item.value in promptAnswers) {
                selectedPositions.add(i)
            }
        }
        return Pair(selectedPositions, choices)
    }

    private fun buildDateRangeView(promptView: View, queryPrompt: QueryPrompt): View? {
        val promptEditText = promptView.findViewById<EditText>(R.id.prompt_et)
        promptEditText.visibility = View.VISIBLE
        promptEditText.isFocusable = false
        val userAnswers = remoteQuerySessionManager.userAnswers
        val humanReadableDateRange = DateRangeUtils.getHumanReadableDateRange(userAnswers[queryPrompt.key])
        promptEditText.setText(humanReadableDateRange)

        // Setup edit button to show date picker
        val editDateIcon = promptView.findViewById<ImageView>(R.id.assist_view)
        editDateIcon.visibility = View.VISIBLE
        editDateIcon.setImageDrawable(
            ResourcesCompat.getDrawable(
                queryRequestActivity.resources,
                R.drawable.ic_create,
                null
            )
        )
        editDateIcon.setOnClickListener {
            showDateRangePicker(
                promptEditText,
                queryPrompt
            )
        }
        return promptEditText
    }

    private fun showDateRangePicker(promptEditText: EditText, queryPrompt: QueryPrompt) {
        val dateRangePickerBuilder: MaterialDatePicker.Builder<Pair<Long, Long>?> =
            MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getLabel(queryPrompt))
                .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)

        // Set Current Range
        val currentDateRangeText = promptEditText.text.toString()
        if (!TextUtils.isEmpty(currentDateRangeText)) {
            try {
                val humanReadableDate = DateRangeUtils.parseHumanReadableDate(currentDateRangeText)
                dateRangePickerBuilder.setSelection(AndroidXUtils.toPair(humanReadableDate!!.first, humanReadableDate.second))
            } catch (e: ParseException) {
                Logger.exception("Error parsing date range $currentDateRangeText", e)
            }
        }
        val dateRangePicker = dateRangePickerBuilder.build()
        dateRangePicker.addOnPositiveButtonClickListener { selection: Pair<Long, Long>? ->
            val startDate = DateRangeUtils.getDateFromTime(
                selection!!.first
            )
            val endDate = DateRangeUtils.getDateFromTime(selection.second)
            answerUserPrompt(queryPrompt.key, DateRangeUtils.formatDateRangeAnswer(startDate, endDate))
            promptEditText.setText(DateRangeUtils.getHumanReadableDateRange(startDate, endDate))
        }
        dateRangePicker.show(queryRequestActivity.supportFragmentManager, DATE_PICKER_FRAGMENT_TAG)
    }

    private fun setUpBarCodeScanButton(promptView: View, promptId: String, queryPrompt: QueryPrompt) {
        // Only show for free text input
        if (queryPrompt.input == null) {
            val barcodeScannerView = promptView.findViewById<ImageView>(R.id.assist_view)
            barcodeScannerView.visibility = if (isBarcodeEnabled(queryPrompt)) View.VISIBLE else View.INVISIBLE
            barcodeScannerView.setBackgroundColor(queryRequestActivity.resources.getColor(R.color.blue))
            barcodeScannerView.setImageDrawable(
                ResourcesCompat.getDrawable(
                    queryRequestActivity.resources,
                    R.drawable.startup_barcode,
                    null
                )
            )
            barcodeScannerView.tag = promptId
            barcodeScannerView.setOnClickListener { v: View -> callBarcodeScanIntent(v.tag as String) }
        }
    }

    private fun isBarcodeEnabled(queryPrompt: QueryPrompt): Boolean {
        return APPEARANCE_BARCODE_SCAN == queryPrompt.appearance
    }

    private fun callBarcodeScanIntent(promptId: String) {
        val intent = WidgetUtils.createScanIntent(queryRequestActivity)
        mPendingPromptId = promptId
        try {
            queryRequestActivity.startActivityForResult(intent, EntitySelectActivity.BARCODE_FETCH)
        } catch (anfe: ActivityNotFoundException) {
            Toast.makeText(
                queryRequestActivity,
                "No barcode reader available! You can install one " +
                    "from the android market.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setLabelText(promptView: View, queryPrompt: QueryPrompt) {
        (promptView.findViewById<View>(R.id.prompt_label) as TextView).text =
            getLabel(queryPrompt)
    }

    private fun getLabel(queryPrompt: QueryPrompt): String {
        val displayData = queryPrompt.display.evaluate()
        return Localizer.processArguments(displayData.name, arrayOf("")).trim { it <= ' ' }
    }

    // Thrown when we are setting an invalid value to the prompt,
    // for ex- trying to set multiple values to a single valued prompt
    class InvalidPromptValueException(message: String) : Throwable(message)
}
