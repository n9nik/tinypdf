package com.n9nik.pdftools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParseRangesTest {

    @Test
    fun singlePages() {
        assertEquals(listOf(0..0, 4..4), PdfOps.parseRanges("1, 5", 10))
    }

    @Test
    fun dashRange() {
        assertEquals(listOf(0..2), PdfOps.parseRanges("1-3", 10))
    }

    @Test
    fun mixedRanges() {
        assertEquals(listOf(0..2, 4..4, 6..8), PdfOps.parseRanges("1-3, 5, 7-9", 10))
    }

    @Test
    fun reversedRangeIsNormalized() {
        assertEquals(listOf(0..2), PdfOps.parseRanges("3-1", 10))
    }

    @Test
    fun whitespaceTolerated() {
        assertEquals(listOf(0..2), PdfOps.parseRanges("  1 - 3 , ", 10))
    }

    @Test
    fun outOfRangeThrows() {
        assertThrows(IllegalArgumentException::class.java) { PdfOps.parseRanges("1-11", 10) }
        assertThrows(IllegalArgumentException::class.java) { PdfOps.parseRanges("0", 10) }
    }

    @Test
    fun garbageThrows() {
        assertThrows(IllegalArgumentException::class.java) { PdfOps.parseRanges("abc", 10) }
        assertThrows(IllegalArgumentException::class.java) { PdfOps.parseRanges("", 10) }
        assertThrows(IllegalArgumentException::class.java) { PdfOps.parseRanges("1-", 10) }
    }
}
