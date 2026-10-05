package org.fossify.math.helpers

import org.fossify.math.helpers.ExpressionEvaluator.EvaluationException
import org.fossify.math.helpers.ExpressionEvaluator.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class ExpressionEvaluatorTest {
    private fun eval(expression: String) = ExpressionEvaluator.evaluate(expression)

    private fun assertResult(expected: String, expression: String) {
        val result = eval(expression)
        assertEquals("$expression = $result", 0, BigDecimal(expected).compareTo(result))
    }

    private fun assertFails(reason: Reason, expression: String) {
        try {
            fail("$expression = ${eval(expression)}")
        } catch (e: EvaluationException) {
            assertEquals(expression, reason, e.reason)
        }
    }

    @Test
    fun orderOfOperations() {
        assertResult("11", "10−2+3")
        assertResult("8", "8÷2×2")
        assertResult("14", "2+3×4")
        assertResult("5", "10−3−2")
        assertResult("2", "8÷2÷2")
        assertResult("512", "2^3^2")
        assertResult("-4", "−2^2")
        assertResult("0.5", "2^−1")
    }

    @Test
    fun decimals() {
        assertResult("0.3", "0.1+0.2")
        assertResult("16777217", "16777216+1")
        assertResult("3.5", "7÷2")
        assertResult("0.3333333333333333333333333333333333", "1÷3")
        assertResult("5", "5.")
    }

    @Test
    fun negativeNumbers() {
        assertResult("-5", "−5")
        assertResult("-6", "3×−2")
        assertResult("-2", "−5+3")
        assertResult("8", "−5−−13")
    }

    @Test
    fun percent() {
        assertResult("55", "50+10%")
        assertResult("45", "50−10%")
        assertResult("20", "200×10%")
        assertResult("0.1", "10%")
        assertResult("2000", "200÷10%")
    }

    @Test
    fun rootsAndParentheses() {
        assertResult("3", "√9")
        assertResult("1.5", "√2.25")
        assertResult("6", "2√9")
        assertResult("20", "(2+3)×4")
        assertResult("20", "4(2+3")
        assertResult("5", "√(9+16)")
    }

    @Test
    fun errors() {
        assertFails(Reason.DIVISION_BY_ZERO, "5÷0")
        assertFails(Reason.DIVISION_BY_ZERO, "5÷(2−2)")
        assertFails(Reason.INVALID, "5+")
        assertFails(Reason.INVALID, "×5")
        assertFails(Reason.INVALID, "1.2.3")
        assertFails(Reason.INVALID, "√−4")
        assertFails(Reason.INVALID, "")
        assertFails(Reason.TOO_BIG, "2^100000")
    }
}
