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
import org.commcare.suite.model.QueryPrompt
import org.commcare.utils.DateRangeUtils
import org.commcare.views.ManagedUi
import org.commcare.views.UiElement
import org.commcare.views.ViewUtil
import org.commcare.views.widgets.SpinnerWidget
import org.commcare.views.widgets.WidgetUtils
import org.javarosa.core.services.locale.Localizer
import java.text.ParseException
import java.util.*

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
            remoteQuerySessionManager.answerUserPrompt(entry.key, entry.value)
            val promptView = promptsBoxes[entry.key]
            if (promptView is EditText) {
                promptView.setText(entry.value)
            } else if (promptView is Spinner) {
                val queryPrompt = remoteQuerySessionManager.neededUserInputDisplays[entry.key]
                remoteQuerySessionManager.populateItemSetChoices(queryPrompt)
                setSpinnerData(queryPrompt!!, (promptView as Spinner?)!!)
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
                remoteQuerySessionManager.answerUserPrompt(queryPrompt.key, s.toString())
                updateAnswerAndRefresh(queryPrompt, s.toString())
            }
        })
        return promptEditText
    }

    private fun updateAnswerAndRefresh(queryPrompt: QueryPrompt, answer: String) {
        val userAnswers = remoteQuerySessionManager.userAnswers
        val oldAnswer = userAnswers[queryPrompt.key]
        if (oldAnswer == null || !oldAnswer.contentEquals(answer)) {
            remoteQuerySessionManager.answerUserPrompt(queryPrompt.key, answer)
            remoteQuerySessionManager.refreshItemSetChoices()
            refreshView()
        }
    }

    private fun buildSpinnerView(promptView: View, queryPrompt: QueryPrompt): Spinner? {
        val promptSpinner = promptView.findViewById<Spinner>(R.id.prompt_spinner)
        promptSpinner.visibility = View.VISIBLE
        promptView.findViewById<View>(R.id.prompt_et).visibility = View.GONE
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
        val items = queryPrompt.itemsetBinding!!.choices
        val choices = arrayOfNulls<String>(items.size)
        var selectedPosition = -1
        val userAnswers: Hashtable<String, String> = remoteQuerySessionManager.userAnswers
        val answer = userAnswers[queryPrompt.key]
        for (i in items.indices) {
            val item = items[i]
            choices[i] = item.labelInnerText
            if (item.value == answer) {
                selectedPosition = i + 1 // first choice is blank in adapter
            }
        }
        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            queryRequestActivity,
            android.R.layout.simple_spinner_item,
            SpinnerWidget.getChoicesWithEmptyFirstSlot(choices)
        )
        promptSpinner.adapter = adapter
        if (selectedPosition != -1) {
            promptSpinner.setSelection(selectedPosition)
        }
    }

    private fun buildDateRangeView(promptView: View, queryPrompt: QueryPrompt): View? {
        val promptEditText = promptView.findViewById<EditText>(R.id.prompt_et)
        promptEditText.visibility = View.VISIBLE
        promptEditText.isFocusable = false
        promptView.findViewById<View>(R.id.prompt_spinner).visibility = View.GONE
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
                dateRangePickerBuilder.setSelection(DateRangeUtils.parseHumanReadableDate(currentDateRangeText))
            } catch (e: ParseException) {
                // do nothing
                e.printStackTrace()
            }
        }
        val dateRangePicker = dateRangePickerBuilder.build()
        dateRangePicker.addOnPositiveButtonClickListener { selection: Pair<Long, Long>? ->
            val startDate = DateRangeUtils.getDateFromTime(
                selection!!.first
            )
            val endDate = DateRangeUtils.getDateFromTime(selection.second)
            remoteQuerySessionManager.answerUserPrompt(
                queryPrompt.key,
                DateRangeUtils.formatDateRangeAnswer(startDate, endDate)
            )
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
}
