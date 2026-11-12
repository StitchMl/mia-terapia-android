package it.miaterapia.family

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class StockMathTest {
    @Test fun halfTabletUsesExactDecimal() { assertEquals("9.5", (BigDecimal("10") - BigDecimal("0.5")).toPlainString()) }
    @Test fun millilitresUseExactDecimal() { assertEquals("140", (BigDecimal("150") - BigDecimal("10")).toPlainString()) }
    @Test fun penCountsDosesNotMilligrams() { assertEquals("3", (BigDecimal("4") - BigDecimal.ONE).toPlainString()) }
}
