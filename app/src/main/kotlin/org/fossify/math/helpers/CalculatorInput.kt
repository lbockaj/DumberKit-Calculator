package org.fossify.math.helpers

import org.fossify.math.helpers.ExpressionEvaluator.DIVIDE
import org.fossify.math.helpers.ExpressionEvaluator.MINUS
import org.fossify.math.helpers.ExpressionEvaluator.PERCENT
import org.fossify.math.helpers.ExpressionEvaluator.PLUS
import org.fossify.math.helpers.ExpressionEvaluator.POWER
import org.fossify.math.helpers.ExpressionEvaluator.ROOT
import org.fossify.math.helpers.ExpressionEvaluator.TIMES
import java.math.BigDecimal
import java.math.MathContext

/**
 * The expression typed on the keypad, the way Calc-U-Later does it: digits go in directly, '*' puts in an operator and
 * each further press turns it into the next one (+ − × ÷), '#' is the decimal point. The text uses '.' for the decimal
 * point and E for big results (1.2E+20); the screen shows it with the phone's separators.
 */
class CalculatorInput(expression: String = "", evaluated: Boolean = false) {
    var expression = expression
        private set

    /** True right after OK showed a result: a digit then starts a new calculation, an operator continues with it. */
    var isResult = evaluated
        private set

    val isEmpty get() = expression.isEmpty()

    private val last get() = expression.lastOrNull()

    fun digit(digit: Int) {
        if (isResult) {
            expression = ""
            isResult = false
        }

        // a number right after ")" or "%" is multiplied
        if (last == ')' || last == PERCENT) {
            expression += TIMES
        }

        val number = currentNumber()
        expression = if (number == "0") {
            // no leading zeros: "0" then "5" is "5"
            expression.dropLast(1) + digit
        } else {
            expression + digit
        }
    }

    fun decimalPoint() {
        if (isResult) {
            expression = ""
            isResult = false
        }

        if (last == ')' || last == PERCENT) {
            expression += TIMES
        }

        val number = currentNumber()
        when {
            number.contains('.') || number.contains('E') -> return
            number.isEmpty() -> expression += "0."
            else -> expression += "."
        }
    }

    /** '*': a new operator after a number, or the next one in place of the last operator. */
    fun rotateOperator() {
        isResult = false
        val last = last
        when {
            last == null -> expression = MINUS.toString()
            isUnaryMinus(expression.length - 1) -> expression = expression.dropLast(1)
            last in ROTATION -> expression = expression.dropLast(1) + ROTATION[(ROTATION.indexOf(last) + 1) % ROTATION.size]
            last == POWER -> expression = expression.dropLast(1) + PLUS
            last == '.' -> expression = expression.dropLast(1) + PLUS
            last == '(' || last == ROOT -> expression += MINUS
            else -> expression += PLUS
        }
    }

    fun power() = binaryOperator(POWER)

    fun percent() {
        isResult = false
        when (last) {
            null, '(', ROOT, PERCENT -> return
            in ExpressionEvaluator.BINARY_OPERATORS -> {
                if (isUnaryMinus(expression.length - 1)) {
                    return
                }
                expression = expression.dropLast(1)
            }
            '.' -> expression = expression.dropLast(1)
        }
        expression += PERCENT
    }

    fun root() {
        if (isResult) {
            // √ of the result, OK computes it
            expression = "$ROOT$expression"
            isResult = false
            return
        }
        if (last == '.') {
            expression = expression.dropLast(1)
        }
        expression += ROOT
    }

    fun openParenthesis() {
        isResult = false
        if (last == '.') {
            expression = expression.dropLast(1)
        }
        expression += '('
    }

    /** Adds ")" if a parenthesis is open and something is in it. */
    fun closeParenthesis(): Boolean {
        isResult = false
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        if (open <= 0 || last == null || last == '(' || last == ROOT || last in ExpressionEvaluator.BINARY_OPERATORS) {
            return false
        }
        if (last == '.') {
            expression = expression.dropLast(1)
        }
        expression += ')'
        return true
    }

    /** ±: the number being typed (or the result) turns negative or back. */
    fun toggleSign() {
        isResult = false
        val numberStart = expression.length - currentNumber().length
        if (numberStart > 0 && isUnaryMinus(numberStart - 1)) {
            expression = expression.removeRange(numberStart - 1, numberStart)
        } else if (last == ')' || last == PERCENT) {
            return
        } else {
            expression = expression.substring(0, numberStart) + MINUS + expression.substring(numberStart)
        }
    }

    /** Back: one character, or the whole result. Returns false when there was nothing to delete. */
    fun backspace(): Boolean {
        if (expression.isEmpty()) {
            return false
        }

        if (isResult) {
            clear()
        } else {
            expression = expression.dropLast(1)
            // "1.2E+20" isn't typed by hand, a part of it can't stay alone
            if (expression.endsWith("E+") || expression.endsWith("E-")) {
                expression = expression.dropLast(2)
            }
            if (expression.endsWith('E')) {
                expression = expression.dropLast(1)
            }
        }
        return true
    }

    fun clear() {
        expression = ""
        isResult = false
    }

    /** Replaces the whole expression, e.g. with one from the history. */
    fun load(newExpression: String) {
        expression = newExpression
        isResult = false
    }

    /** The result, or null if the expression isn't complete yet or is just a number. */
    fun preview(): BigDecimal? {
        if (!hasOperation()) {
            return null
        }
        return try {
            ExpressionEvaluator.evaluate(expression)
        } catch (e: ExpressionEvaluator.EvaluationException) {
            null
        }
    }

    fun hasOperation(): Boolean {
        val withoutExponents = expression.replace(EXPONENT, "").removePrefix(MINUS.toString())
        return withoutExponents.any { it in OPERATION_CHARS }
    }

    /**
     * OK: computes the result and shows it in place of the expression. Returns the (expression, result) for the
     * history, null if it was only a number. Throws EvaluationException for an invalid expression.
     */
    fun evaluate(): Pair<String, String>? {
        if (isResult || !hasOperation()) {
            return null
        }

        val result = toExpressionText(ExpressionEvaluator.evaluate(expression))
        // as it's kept in the history: no decimal point without decimals ("4." → "4"), parentheses closed
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        val formula = expression.replace(DANGLING_POINT, "") + ")".repeat(open.coerceAtLeast(0))
        expression = result
        isResult = true
        return formula to result
    }

    /** The digits (and '.') being typed at the end, "" right after an operator. */
    private fun currentNumber(): String {
        var start = expression.length
        while (start > 0) {
            val char = expression[start - 1]
            val isExponentSign = (char == '+' || char == '-') && start >= 2 && expression[start - 2] == 'E'
            if (char.isDigit() || char == '.' || char == 'E' || isExponentSign) {
                start--
            } else {
                break
            }
        }
        return expression.substring(start)
    }

    /** A "−" that makes a number negative, not a subtraction: at the start or after another operator or "(". */
    private fun isUnaryMinus(index: Int): Boolean {
        if (expression.getOrNull(index) != MINUS) {
            return false
        }
        val before = expression.getOrNull(index - 1) ?: return true
        return before == '(' || before == ROOT || before in ExpressionEvaluator.BINARY_OPERATORS
    }

    /** An operator typed directly (hardware keyboard): a minus at the start or after an operator is a negative sign. */
    fun typeOperator(operator: Char) {
        val last = last
        if (operator == MINUS && !isResult && (last == null || last == '(' || last == ROOT ||
                (last in ExpressionEvaluator.BINARY_OPERATORS && !isUnaryMinus(expression.length - 1)))
        ) {
            expression += MINUS
            return
        }
        binaryOperator(operator)
    }

    private fun binaryOperator(operator: Char) {
        isResult = false
        when (last) {
            null, '(', ROOT -> return
            in ExpressionEvaluator.BINARY_OPERATORS -> {
                if (isUnaryMinus(expression.length - 1)) {
                    return
                }
                expression = expression.dropLast(1)
            }
            '.' -> expression = expression.dropLast(1)
        }
        expression += operator
    }

    companion object {
        private val ROTATION = listOf(PLUS, MINUS, TIMES, DIVIDE)
        private val OPERATION_CHARS = setOf(PLUS, MINUS, TIMES, DIVIDE, POWER, ROOT, PERCENT, '(')

        /** Results keep 15 significant digits, like Fossify Calculator shows them. */
        private val RESULT_PRECISION = MathContext(15)
        private val BIG = BigDecimal("1E15")
        private val TINY = BigDecimal("1E-10")
        private val EXPONENT = Regex("E[+-]?[0-9]*")
        private val DANGLING_POINT = Regex("\\.(?![0-9])")

        /** A result as expression text: plain digits, or 1.2E+20 when very big or very small. */
        fun toExpressionText(value: BigDecimal): String {
            val rounded = value.round(RESULT_PRECISION).stripTrailingZeros()
            if (rounded.signum() == 0) {
                return "0"
            }
            val abs = rounded.abs()
            val text = if (abs >= BIG || abs < TINY) rounded.toScientific() else rounded.toPlainString()
            return text.replace('-', MINUS).replace("E$MINUS", "E-")
        }

        private fun BigDecimal.toScientific(): String {
            val exponent = precision() - scale() - 1
            val mantissa = movePointLeft(exponent).stripTrailingZeros().toPlainString()
            return "${mantissa}E${if (exponent >= 0) "+" else ""}$exponent"
        }
    }
}
