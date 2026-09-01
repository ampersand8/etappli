package com.nuelto.etappli.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseDecimalTest {

    @Test
    fun `parses dot and comma separators`() {
        assertEquals(12.5, parseDecimal("12.5")!!, 1e-9)
        assertEquals(12.5, parseDecimal("12,5")!!, 1e-9)
        assertEquals(7.0, parseDecimal("7")!!, 1e-9)
    }

    @Test
    fun `trims whitespace`() {
        assertEquals(3.25, parseDecimal("  3.25 ")!!, 1e-9)
    }

    @Test
    fun `rejects non numbers`() {
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("1.2.3"))
    }
}
