package org.fossify.math.helpers

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * Expression text ("1234.5+3×4") as shown on the screen ("1 234,5 + 3 × 4"): the phone's decimal and grouping
 * separators, numbers kept exactly as typed (a trailing "," or "0" stays), operators in the accent color with a thin
 * space around them so they stand apart from the grouping spaces.
 */
object ExpressionDisplay {
    private const val THIN_SPACE = ' '
    private val SPACED = setOf(
        ExpressionEvaluator.PLUS, ExpressionEvaluator.MINUS, ExpressionEvaluator.TIMES, ExpressionEvaluator.DIVIDE
    )

    fun format(
        expression: String,
        operatorColor: Int? = null,
        decimalSeparator: String = getDecimalSeparator(),
        groupingSeparator: String = getGroupingSeparator()
    ): CharSequence {
        val result = SpannableStringBuilder()
        var i = 0
        while (i < expression.length) {
            val char = expression[i]
            if (char.isDigit() || char == '.') {
                var end = i
                while (end < expression.length && (expression[end].isDigit() || expression[end] == '.')) {
                    end++
                }
                result.append(formatNumber(expression.substring(i, end), decimalSeparator, groupingSeparator))
                i = end
                continue
            }

            if (char == 'E') {
                // exponent of a big result: E+20 / E-12, shown as is
                var end = i + 1
                if (end < expression.length && (expression[end] == '+' || expression[end] == '-')) {
                    end++
                }
                while (end < expression.length && expression[end].isDigit()) {
                    end++
                }
                result.append(expression.substring(i, end))
                i = end
                continue
            }

            val isUnary = char == ExpressionEvaluator.MINUS && isUnaryPosition(expression, i)
            val text = if (char in SPACED && !isUnary) "$THIN_SPACE$char$THIN_SPACE" else char.toString()
            val start = result.length
            result.append(text)
            // a minus sign of a number keeps the number's color
            if (operatorColor != null && !isUnary) {
                result.setSpan(ForegroundColorSpan(operatorColor), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            i++
        }
        return result
    }

    /** "1234.50" → "1 234,50" (grouping only in the whole part, the decimals stay as typed). */
    fun formatNumber(number: String, decimalSeparator: String, groupingSeparator: String): String {
        val whole = number.substringBefore('.')
        val grouped = whole.reversed().chunked(3).joinToString(groupingSeparator).reversed()
        return if (number.contains('.')) "$grouped$decimalSeparator${number.substringAfter('.')}" else grouped
    }

    private fun isUnaryPosition(expression: String, index: Int): Boolean {
        val before = expression.getOrNull(index - 1) ?: return true
        return before == '(' || before == ExpressionEvaluator.ROOT || before in ExpressionEvaluator.BINARY_OPERATORS
    }
}
