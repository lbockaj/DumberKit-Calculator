package org.fossify.math.helpers

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Evaluates a typed expression like "12.5+3×4−10%" with the usual order of operations: power first, then × and ÷,
 * then + and −, left to right within the same level. Numbers use '.' as the decimal point (the screen shows the
 * phone's own separator).
 *
 * - unary minus: "−5×2", "3×−2"
 * - "^" power, "√" square root (prefix, "2√9" means 2×√9)
 * - "%": "50+10%" is 55 and "50−10%" is 45 (percent of the left side), elsewhere "x%" is x/100
 * - parentheses "(" ")", missing closing ones at the end are added
 */
object ExpressionEvaluator {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    const val POWER = '^'
    const val ROOT = '√'
    const val PERCENT = '%'

    val BINARY_OPERATORS = listOf(PLUS, MINUS, TIMES, DIVIDE, POWER)

    private val MATH: MathContext = MathContext.DECIMAL128
    private val MAX_POWER = BigDecimal(9999)

    class EvaluationException(val reason: Reason) : Exception(reason.name)

    enum class Reason { INVALID, DIVISION_BY_ZERO, TOO_BIG }

    fun evaluate(expression: String): BigDecimal {
        val input = expression.trim()
        if (input.isEmpty()) {
            throw EvaluationException(Reason.INVALID)
        }

        val parser = Parser(input)
        val value = parser.parseExpression()
        parser.skipClosingParentheses()
        if (!parser.isAtEnd()) {
            throw EvaluationException(Reason.INVALID)
        }
        return value.value.stripTrailingZeros()
    }

    /** A value, and whether it was written as a percentage (needed for "50+10%"). */
    private class Term(val value: BigDecimal, val isPercent: Boolean = false)

    private class Parser(private val input: String) {
        private var pos = 0

        fun isAtEnd() = pos >= input.length

        fun skipClosingParentheses() {
            while (peek() == ')') {
                pos++
            }
        }

        private fun peek(): Char? = input.getOrNull(pos)

        private fun accept(char: Char): Boolean {
            if (peek() == char || (char == MINUS && peek() == '-')) {
                pos++
                return true
            }
            return false
        }

        // sum := product (('+' | '−') product)*
        fun parseExpression(): Term {
            var left = parseProduct()
            while (true) {
                val isPlus = when {
                    accept(PLUS) -> true
                    accept(MINUS) -> false
                    else -> return left
                }
                val right = parseProduct()
                val amount = if (right.isPercent) left.value.multiply(right.value, MATH) else right.value
                left = Term(if (isPlus) left.value.add(amount, MATH) else left.value.subtract(amount, MATH))
            }
        }

        // product := unary (('×' | '÷') unary | unary-without-operator after a root or parenthesis)*
        private fun parseProduct(): Term {
            var left = parseUnary()
            while (true) {
                when {
                    accept(TIMES) || accept('*') -> left = Term(left.value.multiply(parseUnary().value, MATH))
                    accept(DIVIDE) || accept('/') -> {
                        val right = parseUnary().value
                        if (right.signum() == 0) {
                            throw EvaluationException(Reason.DIVISION_BY_ZERO)
                        }
                        left = Term(left.value.divide(right, MATH))
                    }
                    // "2√9" and "2(3+1)" multiply
                    peek() == ROOT || peek() == '(' -> left = Term(left.value.multiply(parseUnary().value, MATH))
                    else -> return left
                }
            }
        }

        // unary := ('−' | '+') unary | '√' unary | power   (so −2^2 = −4)
        private fun parseUnary(): Term {
            if (accept(MINUS)) {
                val term = parseUnary()
                return Term(term.value.negate(), term.isPercent)
            }
            if (accept(PLUS)) {
                return parseUnary()
            }
            if (accept(ROOT)) {
                return Term(squareRoot(parseUnary().value))
            }
            return parsePower()
        }

        // power := postfix ('^' unary)?   (right to left: 2^3^2 = 2^9, 2^−1 = 0.5)
        private fun parsePower(): Term {
            val base = parsePostfix()
            if (!accept(POWER)) {
                return base
            }
            return Term(power(base.value, parseUnary().value))
        }

        // postfix := primary '%'?
        private fun parsePostfix(): Term {
            val value = parsePrimary()
            if (accept(PERCENT)) {
                return Term(value.divide(BigDecimal(100), MATH), isPercent = true)
            }
            return Term(value)
        }

        // primary := number | '(' sum ')'
        private fun parsePrimary(): BigDecimal {
            if (accept('(')) {
                val value = parseExpression().value
                // a missing ')' before the end is fine, it's added like on most calculators
                accept(')')
                return value
            }

            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) {
                pos++
            }
            val number = input.substring(start, pos).removeSuffix(".")
            if (number.isEmpty() || number.count { it == '.' } > 1) {
                throw EvaluationException(Reason.INVALID)
            }

            // a big or tiny earlier result, like 1.2E+20
            var exponent = ""
            if (peek() == 'E') {
                val expStart = pos++
                if (peek() == '+' || peek() == '-') {
                    pos++
                }
                while (peek()?.isDigit() == true) {
                    pos++
                }
                exponent = input.substring(expStart, pos)
            }

            return try {
                BigDecimal(number + exponent)
            } catch (e: NumberFormatException) {
                throw EvaluationException(Reason.INVALID)
            }
        }
    }

    private fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (exponent.abs() > MAX_POWER) {
            throw EvaluationException(Reason.TOO_BIG)
        }

        val isWhole = exponent.stripTrailingZeros().scale() <= 0
        if (isWhole) {
            val n = exponent.toInt()
            if (n < 0) {
                if (base.signum() == 0) {
                    throw EvaluationException(Reason.DIVISION_BY_ZERO)
                }
                return BigDecimal.ONE.divide(base.pow(-n, MATH), MATH)
            }
            return base.pow(n, MATH)
        }

        // fractions of a power go through doubles, enough for a phone calculator
        val result = Math.pow(base.toDouble(), exponent.toDouble())
        if (result.isNaN()) {
            throw EvaluationException(Reason.INVALID)
        }
        if (result.isInfinite()) {
            throw EvaluationException(Reason.TOO_BIG)
        }
        return BigDecimal(result, MathContext.DECIMAL64)
    }

    private fun squareRoot(value: BigDecimal): BigDecimal {
        if (value.signum() < 0) {
            throw EvaluationException(Reason.INVALID)
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO
        }

        // Newton's method from the double estimate, exact for perfect squares like √2.25
        var x = BigDecimal(Math.sqrt(value.toDouble()), MATH)
        val two = BigDecimal(2)
        repeat(SQRT_ITERATIONS) {
            x = x.add(value.divide(x, MATH), MATH).divide(two, MATH)
        }
        return x.round(MathContext(MATH.precision - 2, RoundingMode.HALF_EVEN))
    }

    private const val SQRT_ITERATIONS = 5
}
