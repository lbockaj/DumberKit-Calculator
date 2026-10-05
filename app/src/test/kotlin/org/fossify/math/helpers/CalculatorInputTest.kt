package org.fossify.math.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorInputTest {
    /** Types like the keypad: digits, '*' rotate, '#' decimal point, '=' OK, '<' Back. */
    private fun type(keys: String, input: CalculatorInput = CalculatorInput()): CalculatorInput {
        keys.forEach {
            when (it) {
                in '0'..'9' -> input.digit(it - '0')
                '*' -> input.rotateOperator()
                '#' -> input.decimalPoint()
                '=' -> input.evaluate()
                '<' -> input.backspace()
            }
        }
        return input
    }

    @Test
    fun starRotatesOperators() {
        assertEquals("5+", type("5*").expression)
        assertEquals("5−", type("5**").expression)
        assertEquals("5×", type("5***").expression)
        assertEquals("5÷", type("5****").expression)
        assertEquals("5+", type("5*****").expression)
        assertEquals("5×3", type("5***3").expression)
    }

    @Test
    fun negativeAtStart() {
        assertEquals("−", type("*").expression)
        assertEquals("", type("**").expression)
        assertEquals("−5+2", type("*5*2").expression)
        assertEquals("−3", type("*5*2=").expression)
    }

    @Test
    fun decimals() {
        assertEquals("0.5", type("#5").expression)
        assertEquals("1.25", type("1#2#5").expression)
        assertEquals("3+0.5", type("3*#5").expression)
        assertEquals("0.3", type("0#1*0#2=").expression)
        assertEquals("7", type("007").expression)
    }

    @Test
    fun resultContinues() {
        val input = type("2*3=")
        assertEquals("5", input.expression)
        assertTrue(input.isResult)
        assertEquals("5+", type("*", input).expression)
        assertEquals("9", type("9", type("2*3=")).expression)
    }

    @Test
    fun orderOfOperations() {
        assertEquals("11", type("10**2*3=").expression)
        assertEquals("14", type("2*3***4=").expression)
    }

    @Test
    fun backspace() {
        assertEquals("12", type("123<").expression)
        assertEquals("", type("2*3=<").expression)
        assertFalse(CalculatorInput().backspace())
    }

    @Test
    fun moreFunctions() {
        val percent = type("50*10").apply { percent() }
        assertEquals("55", type("=", percent).expression)

        val sign = type("5***").apply { toggleSign() }
        assertEquals("5×−", sign.expression)
        assertEquals("-15", type("3=", sign).expression.replace('−', '-'))

        val root = CalculatorInput().apply { root() }
        assertEquals("3", type("9=", root).expression)

        val rootOfResult = type("4*5=").apply { root() }
        assertEquals("3", type("=", rootOfResult).expression)

        val parentheses = CalculatorInput().apply { openParenthesis() }
        type("2*3", parentheses)
        assertTrue(parentheses.closeParenthesis())
        parentheses.rotateOperator()
        parentheses.rotateOperator()
        parentheses.rotateOperator()
        assertEquals("20", type("4=", parentheses).expression)
    }

    @Test
    fun historyFormula() {
        assertEquals("12+4" to "16", type("12*4#").evaluate())
        val parentheses = CalculatorInput().apply { openParenthesis() }
        assertEquals("(2+3)" to "5", type("2*3", parentheses).evaluate())
    }

    @Test
    fun bigResults() {
        assertEquals("1E+20", type("10000000000***10000000000=").expression)
        assertEquals("2E+20", type("10000000000***10000000000***2=").expression)
        assertEquals("0.3333333333333", type("1****3=").expression.take(15))
    }

    @Test
    fun preview() {
        assertEquals(null, type("5").preview())
        assertEquals("8", type("5*3").preview()?.toPlainString())
        assertEquals(null, type("5*").preview())
    }
}
