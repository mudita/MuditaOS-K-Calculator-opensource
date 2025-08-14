package com.mudita.calc

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.mudita.opencalculator.R
import com.mudita.opencalculator.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val decimalSeparatorSymbol =
        DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    private val groupingSeparatorSymbol =
        DecimalFormatSymbols.getInstance().groupingSeparator.toString()
    private var isEqualLastAction = false
    private var isDegreeModeActivated = true // Set degree by default
    private var lastAction = ""
    private lateinit var binding: ActivityMainBinding
    private var calculationResult = BigDecimal.ZERO

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Themes
        val themes = Themes(this)
        themes.applyDayNightOverride()
        setTheme(themes.getTheme())

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Disable the keyboard on display EditText
        binding.input.showSoftInputOnFocus = false

        // Set default animations and disable the fade out default animation
        // https://stackoverflow.com/questions/19943466/android-animatelayoutchanges-true-what-can-i-do-if-the-fade-out-effect-is-un
        val lt = LayoutTransition()
        lt.disableTransitionType(LayoutTransition.DISAPPEARING)
        binding.tableLayout.layoutTransition = lt

        // Set decimalSeparator
        binding.pointButton.text = if (decimalSeparatorSymbol == ",") "," else "."

        // Focus by default
        binding.input.requestFocus()

        // Do not clear after equal button if you move the cursor
        binding.input.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                super.sendAccessibilityEvent(host, eventType)
                if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
                    isEqualLastAction = false

                if (!binding.input.isCursorVisible)
                    binding.input.isCursorVisible = true
            }
        }

        binding.input.setOnLongClickListener {
            if (binding.input.text.isNotBlank()) {
                binding.input.background =
                    ContextCompat.getColor(applicationContext, android.R.color.transparent)
                        .toDrawable()
                copyTextButton(binding.input)
            }
            true
        }

        // Handle changes into input to update resultDisplay
        binding.input.doOnTextChanged { _, _, _, _ -> updateResultDisplay() }

        binding.resultDisplay.setOnLongClickListener {
            if (binding.resultDisplay.text.isNotBlank()) {
                binding.resultDisplay.background =
                    ContextCompat.getColor(applicationContext, android.R.color.transparent)
                        .toDrawable()
                copyTextButton(binding.resultDisplay)
            }
            true
        }

        binding.input.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.input.customInsertionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(p0: ActionMode?, p1: Menu?): Boolean = false
                override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean = false
                override fun onActionItemClicked(p0: ActionMode?, p1: MenuItem?): Boolean = false
                override fun onDestroyActionMode(p0: ActionMode?) {}
            }
        }
    }

    private fun keyVibration(view: View) {
        if (MyPreferences(this).vibrationMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            }
        }
    }

    private fun updateDisplay(view: View, value: String) {
        if (isEqualLastAction && binding.input.text.contains(decimalSeparatorSymbol) && value == decimalSeparatorSymbol) return
        lastAction = value
        val actionsList = listOf('+', '-', '÷', '×')
        val inputCharArray = binding.input.text.toString().toCharArray()
        val valueNoSeparators = value.replace(groupingSeparatorSymbol, "")
        val isValueInt = valueNoSeparators.toIntOrNull() != null

        if (
            inputCharArray.isNotEmpty() &&
            inputCharArray[inputCharArray.size - 1] == '(' &&
            actionsList.contains(value.last()) &&
            value.last() != '(' &&
            value.last() != '-'
        )
            return

        if (inputCharArray.isNotEmpty() && actionsList.contains(value.last())) {
            if (
                binding.input.selectionStart != 0 &&
                actionsList.contains(inputCharArray[binding.input.selectionStart - 1]) ||
                (
                        binding.input.selectionStart < inputCharArray.size &&
                                actionsList.contains(inputCharArray[binding.input.selectionStart])
                        )
            ) {
                if (
                    binding.input.selectionStart - 1 > 0 &&
                    actionsList.contains(inputCharArray[binding.input.selectionStart - 1])
                ) {
                    if (binding.input.selectionStart != 0)
                        inputCharArray[binding.input.selectionStart - 1] = value.last()

                    val formattedInput = String(inputCharArray)
                    binding.input.setText(formattedInput)
                    binding.input.setSelection(formattedInput.length + value.length - 1)
                    updateResultDisplay()

                    return
                }

                if (
                    binding.input.selectionStart < inputCharArray.size &&
                    actionsList.contains(inputCharArray[binding.input.selectionStart])
                ) {
                    if (binding.input.selectionStart != 0)
                        inputCharArray[binding.input.selectionStart] = value.last()

                    val formattedInput = String(inputCharArray)

                    binding.input.setText(formattedInput)

                    binding.input.setSelection(formattedInput.length + value.length - 1)
                    updateResultDisplay()

                    return
                }
            }
        }

        if (
            inputCharArray.isNotEmpty() &&
            actionsList.contains(value.last()) &&
            actionsList.contains(inputCharArray[inputCharArray.size - 1])
        ) {
            inputCharArray[inputCharArray.size - 1] = value.last()
            val formattedInput = String(inputCharArray)

            binding.input.setText(formattedInput)

            binding.input.setSelection(formattedInput.length + value.length - 1)
            updateResultDisplay()

            return
        }

        // Reset input with current number if following "equal"
        if (isEqualLastAction) {
            val anyNumber = "0123456789".toCharArray().map {
                it.toString()
            }
            if (anyNumber.contains(value))
                binding.input.setText("")
            else
                binding.input.setSelection(binding.input.text.length)
            isEqualLastAction = false
        }

        if (!binding.input.isCursorVisible) {
            binding.input.isCursorVisible = true
        }

        lifecycleScope.launch(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                // Vibrate when key pressed
                keyVibration(view)
            }

            val formerValue = binding.input.text.toString()
            val cursorPosition = binding.input.selectionStart
            val leftValue = formerValue.subSequence(0, cursorPosition).toString()
            val leftValueFormatted =
                NumberFormatter.format(leftValue, decimalSeparatorSymbol, groupingSeparatorSymbol)
            val rightValue = formerValue.subSequence(cursorPosition, formerValue.length).toString()

            val newValue = leftValue + value + rightValue

            var newValueFormatted = NumberFormatter.format(newValue, decimalSeparatorSymbol, groupingSeparatorSymbol)

            withContext(Dispatchers.Main) {
                val textLength = binding.input.text.length

                if (textLength > 0) {

                    // Get next / previous characters relative to the cursor
                    val nextChar =
                        if (textLength - cursorPosition > 0) binding.input.text[cursorPosition].toString() else "0" // use "0" as default like it's not a symbol

                    val previousChar =
                        if (cursorPosition > 0) binding.input.text[cursorPosition - 1].toString() else "0"

                    val currentValue = getValueAroundCursor(cursorPosition)

                    if(value == decimalSeparatorSymbol && currentValue.contains(decimalSeparatorSymbol) ){
                        return@withContext
                    }

                    if(value == decimalSeparatorSymbol  && (previousChar == decimalSeparatorSymbol || nextChar == decimalSeparatorSymbol)){
                        return@withContext
                    }
                }
                // 2. When you click on a former calculation from the history
                if (binding.input.text.isNotEmpty()
                    && cursorPosition > 0
                    && decimalSeparatorSymbol in value
                    && value != decimalSeparatorSymbol // The value should not be *only* the decimal separator
                ) {
                    if (NumberFormatter.extractNumbers(value, decimalSeparatorSymbol).isNotEmpty()) {
                        val firstValueNumber = NumberFormatter.extractNumbers(value, decimalSeparatorSymbol).first()
                        val lastValueNumber = NumberFormatter.extractNumbers(value, decimalSeparatorSymbol).last()
                        val tmpNewValue: String
                        if (decimalSeparatorSymbol in firstValueNumber || decimalSeparatorSymbol in lastValueNumber) {
                            var numberBefore = NumberFormatter.extractNumbers(
                                binding.input.text.toString().substring(0, cursorPosition),
                                decimalSeparatorSymbol
                            ).last()
                            var numberAfter = ""
                            if (cursorPosition < binding.input.text.length - 1) {
                                numberAfter = NumberFormatter.extractNumbers(
                                    binding.input.text.toString()
                                        .substring(cursorPosition, binding.input.text.length),
                                    decimalSeparatorSymbol
                                ).first()
                            }
                            var tmpValue = value
                            var numberBeforeParenthesisLength = 0
                            if (decimalSeparatorSymbol in numberBefore) {
                                numberBefore = "($numberBefore)"
                                numberBeforeParenthesisLength += 2
                            }
                            if (decimalSeparatorSymbol in numberAfter) {
                                tmpValue = "($value)"
                            }
                            tmpNewValue = binding.input.text.toString().substring(
                                0,
                                (cursorPosition + numberBeforeParenthesisLength - numberBefore.length)
                            ) + numberBefore + tmpValue + rightValue
                            newValueFormatted = NumberFormatter.format(tmpNewValue, decimalSeparatorSymbol, groupingSeparatorSymbol)
                        }
                    }
                }

                if (value == ".") {
                    if (binding.input.text.isEmpty()) {
                        newValueFormatted = "0."
                    } else if (cursorPosition == 0 || formerValue[cursorPosition-1] !in "0123456789") {
                        newValueFormatted = newValueFormatted.replaceRange(cursorPosition,cursorPosition + 1,"0.")
                    }
                }
                // Update Display
                binding.input.setText(newValueFormatted)

                // Increase cursor position
                if (leftValueFormatted.isBlank()) {
                    binding.input.setSelection(newValueFormatted.length)
                } else {
                    val cursorOffset = newValueFormatted.length - newValue.length
                    val selection = cursorPosition + value.length + cursorOffset
                    binding.input.setSelection(selection)
                }
                // Update resultDisplay
                updateResultDisplay()
            }
        }
    }

    /**
     * Find current value between two math symbols relative to the cursor
     * @param cursorPosition The cursor position
     */
    private fun getValueAroundCursor(cursorPosition: Int): String {

        val text = binding.input.text.toString()

        if (cursorPosition < 0 || cursorPosition > text.length) {
            return ""
        }

        val delimiters = charArrayOf('+', '-', '×', '÷', '(', ')')

        // Find the start of the text around the cursor by looking for the previous delimiter
        val start = text.substring(0, cursorPosition).lastIndexOfAny(delimiters).let {
            if (it == -1) 0 else it + 1
        }

        // Find the end of the text around the cursor by looking for the next delimiter
        val end = text.indexOfAny(delimiters, cursorPosition).let {
            if (it == -1) text.length else it
        }

        // Return the text around the cursor position
        return text.substring(start, end)
    }


    private fun updateResultDisplay() {
        lifecycleScope.launch(Dispatchers.Default) {
            val calculation = binding.input.text.toString().removeSuffix(".")

            if (calculation != "") {
                division_by_0 = false
                domain_error = false
                syntax_error = false
                is_infinity = false
                require_real_number = false

                val calculationTmp = Expression().getCleanExpression(
                    binding.input.text.toString(),
                    decimalSeparatorSymbol,
                    groupingSeparatorSymbol
                )
                calculationResult = Calculator().evaluate(calculationTmp, isDegreeModeActivated)

                // If result is a number and it is finite
                if (!(division_by_0 || domain_error || syntax_error || is_infinity || require_real_number)) {

                    // Round
                    calculationResult = roundResult(calculationResult)
                    var formattedResult = NumberFormatter.format(
                        calculationResult.toString().replace(".", decimalSeparatorSymbol),
                        decimalSeparatorSymbol,
                        groupingSeparatorSymbol
                    )

                    // Remove zeros at the end of the results (after point)
                    if (!false || !(calculationResult >= BigDecimal(9999) || calculationResult <= BigDecimal(0.1))) {
                        val resultSplited = calculationResult.toString().split('.')
                        if (resultSplited.size > 1) {
                            val resultPartAfterDecimalSeparator = resultSplited[1].trimEnd('0')
                            var resultWithoutZeros = resultSplited[0]
                            if (resultPartAfterDecimalSeparator != "") {
                                resultWithoutZeros =
                                    resultSplited[0] + "." + resultPartAfterDecimalSeparator
                            }
                            formattedResult = NumberFormatter.format(
                                resultWithoutZeros.replace(".", decimalSeparatorSymbol),
                                decimalSeparatorSymbol,
                                groupingSeparatorSymbol
                            )
                        }
                    }

                    val formattedResultWithoutTrailingZeros = if (formattedResult.contains(decimalSeparatorSymbol)) formattedResult.trimEnd('0').trimEnd(decimalSeparatorSymbol[0]) else formattedResult
                    val inputWithoutTrailingZeros = if (calculation.contains(decimalSeparatorSymbol)) calculation.trimEnd('0').trimEnd(decimalSeparatorSymbol[0]) else calculation

                    withContext(Dispatchers.Main) {
                        if (formattedResultWithoutTrailingZeros != inputWithoutTrailingZeros)
                            binding.resultDisplay.setText(formattedResult)
                        else
                            binding.resultDisplay.setText("")
                    }

                } else
                    withContext(Dispatchers.Main) {
                        if (is_infinity && !division_by_0 && !domain_error && !require_real_number) {
                            if (calculationResult < BigDecimal.ZERO)
                                binding.resultDisplay.setText("-" + getString(R.string.infinity))
                            else
                                binding.resultDisplay.setText(getString(R.string.value_too_large))
                        } else
                            binding.resultDisplay.setText("")
                    }
            } else {
                withContext(Dispatchers.Main) {
                    binding.resultDisplay.setText("")
                }
            }
        }
    }

    private fun roundResult(result: BigDecimal): BigDecimal {
        val numberPrecision = 10
        var newResult = result.setScale(numberPrecision, RoundingMode.HALF_EVEN)
        if (false && (newResult >= BigDecimal(9999) || newResult <= BigDecimal(0.1))) {
            val scientificString = String.format(Locale.US, "%.4g", result)
            newResult = BigDecimal(scientificString)
        }

        // Fix how is displayed 0 with BigDecimal
        val tempResult = newResult.toString().replace("E-", "").replace("E", "")
        val allCharsEqualToZero = tempResult.all { it == '0' }
        if (allCharsEqualToZero || newResult.toString().startsWith("0E"))
            return BigDecimal.ZERO

        return newResult
    }

    fun keyDigitPadMappingToDisplay(view: View) {
        val leftCharacterIndex = binding.input.selectionStart - 1
        val leftCharacter = binding.input.text.toString().getOrNull(leftCharacterIndex)
        if (leftCharacter == '%') return
        updateDisplay(view, (view as Button).text as String)
    }

    private fun addSymbol(view: View, currentSymbol: String) {

        val textLength = binding.input.text.length

        if (textLength > 0) {
            val cursorPosition = binding.input.selectionStart
            val nextChar =
                if (textLength - cursorPosition > 0) binding.input.text[cursorPosition].toString() else "0" // use "0" as default like it's not a symbol
            val previousChar =
                if (cursorPosition > 0) binding.input.text[cursorPosition - 1].toString() else "0"

            val noDigitsInInput = binding.input.text.toString().all { it !in "0123456789" }

            if(previousChar == "-" && currentSymbol.matches("[+\\-÷×^]".toRegex()) && noDigitsInInput) {
                return
            }

            if (currentSymbol != previousChar // Ignore multiple presses of the same button
                && currentSymbol != nextChar
                && previousChar != decimalSeparatorSymbol // Ensure that the previous character is not a comma
                && (previousChar != "(" // Ensure that we are not at the beginning of a parenthesis
                        || currentSymbol == "-")
            ) { // Minus symbol is an override
                // If previous character is a symbol, replace it
                if (previousChar.matches("[+\\-÷×^]".toRegex())) {
                    keyVibration(view)

                    val leftString = binding.input.text.subSequence(0, cursorPosition - 1).toString()
                    val rightString = binding.input.text.subSequence(cursorPosition, textLength).toString()

                    // Add a parenthesis if there is another symbol before minus
                    if (currentSymbol == "-") {
                        if (previousChar in "×÷+-") {
                            binding.input.setText(leftString + currentSymbol + rightString)
                            binding.input.setSelection(cursorPosition)
                        } else {
                            binding.input.setText(leftString + previousChar + currentSymbol + rightString)
                            binding.input.setSelection(cursorPosition + 1)
                        }
                    } else if (cursorPosition > 1 && binding.input.text[cursorPosition - 2] != '(') {
                        // check if sign operator before previous one is  * or ÷
                        val beforePrevSignOperator = binding.input.text[cursorPosition - 2]

                        if(!beforePrevSignOperator.toString().matches("[÷×^]".toRegex())) {
                            binding.input.setText(leftString + currentSymbol + rightString)
                            binding.input.setSelection(cursorPosition)
                        } else if (currentSymbol == "+" || currentSymbol == "-") {
                            binding.input.setText(leftString + rightString)
                            binding.input.setSelection(cursorPosition - 1)
                        }
                    } else if (currentSymbol == "+") {
                        binding.input.setText(leftString + rightString)
                        binding.input.setSelection(cursorPosition - 1)
                    }
                }
                // If next character is a symbol, replace it
                else if (nextChar.matches("[+\\-÷×^%!]".toRegex())
                    && currentSymbol != "%"
                ) { // Make sure that percent symbol doesn't replace succeeding symbols
                    keyVibration(view)

                    val leftString = binding.input.text.subSequence(0, cursorPosition).toString()
                    val rightString = binding.input.text.subSequence(cursorPosition + 1, textLength).toString()

                    if (cursorPosition > 0 && previousChar != "(") {
                        binding.input.setText(leftString + currentSymbol + rightString)
                        binding.input.setSelection(cursorPosition + 1)
                    } else if (currentSymbol == "+") binding.input.setText(leftString + rightString)
                }
                // Otherwise just update the display
                else if (cursorPosition > 0 || nextChar != "0" && currentSymbol == "-") {
                    updateDisplay(view, currentSymbol)
                } else keyVibration(view)
            } else keyVibration(view)
        } else { // Allow minus symbol, even if the input is empty
            if (currentSymbol == "-") updateDisplay(view, currentSymbol)
            else keyVibration(view)
        }
    }

    fun addButton(view: View) = addSymbol(view, "+")
    fun subtractButton(view: View)  = addSymbol(view, "-")
    fun divideButton(view: View) = addSymbol(view, "÷")
    fun multiplyButton(view: View) = addSymbol(view, "×")

    fun percentButton(view: View) {
        addSymbol(view, "%")
    }

    fun plusAndMinusButton(view: View) {
        if (binding.resultDisplay.text.isNotEmpty()) {
            val result = binding.resultDisplay.text.toString()
            val isResultRowInvalid = result.isEmpty()
                    || result == getString(com.mudita.frontitude.R.string.calculator_error_h1_formaterror)
                    || result == getString(com.mudita.frontitude.R.string.calculator_error_h1_error)
                    || result == getString(R.string.require_real_number)
                    || result.toIntOrNull() == 0

            if (isResultRowInvalid) return

            if (result.startsWith("-")) {
                binding.resultDisplay.setText(result.substring(1))
            } else {
                binding.resultDisplay.setText("-$result")
            }
        }
    }


    fun pointButton(view: View) {
        updateDisplay(view, decimalSeparatorSymbol)
    }

    fun clearButton(view: View) {
        keyVibration(view)
        binding.input.setText("")
        binding.resultDisplay.setText("")
    }

    fun equalsButton(view: View) {
        lifecycleScope.launch(Dispatchers.Default) {
            keyVibration(view)

            val calculation = binding.resultDisplay.text.toString()


            if (calculation.isNotEmpty() || isCalculationError()) {
                var formattedResult = Expression().getCleanExpression(
                    calculation.replace(".", decimalSeparatorSymbol),
                    decimalSeparatorSymbol,
                    groupingSeparatorSymbol
                )

                // If result is a number and it is finite
                if (isCalculationError().not()) {
                    // Remove zeros at the end of the results (after point)
                    val resultSplited = calculation.split('.')
                    if (resultSplited.size > 1) {
                        val resultPartAfterDecimalSeparator = resultSplited[1].trimEnd('0')
                        var resultWithoutZeros = resultSplited[0]
                        if (resultPartAfterDecimalSeparator != "") {
                            resultWithoutZeros =
                                resultSplited[0] + "." + resultPartAfterDecimalSeparator
                        }
                        formattedResult = NumberFormatter.format(
                            resultWithoutZeros.replace(
                                ".",
                                decimalSeparatorSymbol
                            ), decimalSeparatorSymbol, groupingSeparatorSymbol
                        )
                    }

                    withContext(Dispatchers.Main) {
                        // Hide the cursor before updating binding.input to avoid weird cursor movement
                        binding.input.isCursorVisible = false

                        // Display result
                        binding.input.setText(formattedResult)

                        // Set cursor: Scroll to the end
                        binding.input.setSelection(binding.input.length())

                        // Set cursor: Hide the cursor (do not remove this, it's not a duplicate)
                        binding.input.isCursorVisible = false

                        // Set cursor: Clear resultDisplay
                        binding.resultDisplay.setText("")
                    }
                    isEqualLastAction = true
                } else {
                    withContext(Dispatchers.Main) {
                        if (syntax_error)
                            binding.resultDisplay.setText(getString(com.mudita.frontitude.R.string.calculator_error_h1_formaterror))
                        else if (domain_error)
                            binding.resultDisplay.setText(getString(com.mudita.frontitude.R.string.calculator_error_h1_error))
                        else if (require_real_number)
                            binding.resultDisplay.setText(getString(R.string.require_real_number))
                        else if (division_by_0)
                            binding.resultDisplay.setText(com.mudita.frontitude.R.string.calculator_error_h1_error)
                        else if (is_infinity) {
                            if (calculationResult < BigDecimal.ZERO)
                                binding.resultDisplay.setText("-" + getString(R.string.infinity))
                            else
                                binding.resultDisplay.setText(getString(R.string.value_too_large))
                        } else {
                            binding.resultDisplay.setText(formattedResult)
                            isEqualLastAction = true // Do not clear the calculation (if you click into a number) if there is an error
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) { binding.resultDisplay.setText("") }
            }
        }
    }

    fun backspaceButton(view: View) {
        keyVibration(view)

        var cursorPosition = binding.input.selectionStart
        val textLength = binding.input.text.length
        var newValue = ""
        var isFunction = false
        var isDecimal = false
        var functionLength = 0

        if (isEqualLastAction) {
            cursorPosition = textLength
        }

        if (cursorPosition != 0 && textLength != 0) {
            // Check if it is a function to delete
            val functionsList =
                listOf("cos⁻¹(", "sin⁻¹(", "tan⁻¹(", "cos(", "sin(", "tan(", "ln(", "log(", "exp(")
            for (function in functionsList) {
                val leftPart = binding.input.text.subSequence(0, cursorPosition).toString()
                if (leftPart.endsWith(function)) {
                    newValue = binding.input.text.subSequence(0, cursorPosition - function.length)
                        .toString() +
                            binding.input.text.subSequence(cursorPosition, textLength).toString()
                    isFunction = true
                    functionLength = function.length - 1
                    break
                }
            }
            // Else
            if (!isFunction) {
                // remove the grouping separator
                val leftPart = binding.input.text.subSequence(0, cursorPosition).toString()
                val leftPartWithoutSpaces = leftPart.replace(groupingSeparatorSymbol, "")
                functionLength = leftPart.length - leftPartWithoutSpaces.length

                newValue = leftPartWithoutSpaces.subSequence(0, leftPartWithoutSpaces.length - 1)
                    .toString() +
                        binding.input.text.subSequence(cursorPosition, textLength).toString()

                isDecimal = binding.input.text[cursorPosition - 1] == decimalSeparatorSymbol[0]
            }

            // Handle decimal deletion as a special case when finding cursor position
            var rightSideCommas = 0
            if (isDecimal) {
                val oldString = binding.input.text
                var immediateRightDigits = 0
                var index = cursorPosition
                // Find number of digits that were previously to the right of the decimal
                while (index < textLength && oldString[index].isDigit()) {
                    index++
                    immediateRightDigits++
                }
                // Determine how many thousands separators that gives us to our right
                if (immediateRightDigits > 3)
                    rightSideCommas = immediateRightDigits / 3
            }

            val newValueFormatted =
                NumberFormatter.format(newValue, decimalSeparatorSymbol, groupingSeparatorSymbol)
            var cursorOffset = newValueFormatted.length - newValue.length - rightSideCommas
            if (cursorOffset < 0) cursorOffset = 0

            binding.input.setText(newValueFormatted)
            binding.input.setSelection((cursorPosition - 1 + cursorOffset - functionLength).takeIf { it > 0 }
                ?: 0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun copyTextButton(view: EditText) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_copy, null)

        val wid = LinearLayout.LayoutParams.WRAP_CONTENT
        val high = LinearLayout.LayoutParams.WRAP_CONTENT
        val focus = true
        val popupWindow = PopupWindow(popupView, wid, high, focus)

        popupWindow.showAtLocation(view, Gravity.TOP or Gravity.RIGHT, 4.dp, view.y.toInt() - 10.dp)

        popupView.setOnTouchListener { v, event ->
            popupWindow.dismiss()
            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                getString(com.mudita.frontitude.R.string.common_button_copy),
                view.text
            )
            clipboardManager.setPrimaryClip(clip)
            true
        }

        popupWindow.setOnDismissListener {
            view.background =
                ContextCompat.getColor(applicationContext, android.R.color.transparent).toDrawable()
        }
    }

    override fun onResume() {
        super.onResume()

        // Disable the keyboard on display EditText
        binding.input.showSoftInputOnFocus = false
    }

    private val Int.dp: Int
        get() = (toFloat() * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
}
