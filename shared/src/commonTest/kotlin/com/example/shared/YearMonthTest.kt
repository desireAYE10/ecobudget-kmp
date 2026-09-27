package com.example.shared

import com.example.shared.model.YearMonth
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de non-régression pour [YearMonth] après remplacement de `java.util.Calendar`
 * par `kotlinx-datetime` lors de la migration vers `commonMain`.
 */
class YearMonthTest {

    @Test
    fun `previous and next wrap around the year boundary`() {
        val december = YearMonth(2026, 11)
        assertEquals(YearMonth(2027, 0), december.next())
        assertEquals(YearMonth(2025, 11), YearMonth(2026, 0).previous())
    }

    @Test
    fun `current matches the system clock`() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val current = YearMonth.current()
        assertEquals(today.year, current.year)
        assertEquals(today.monthNumber - 1, current.month)
    }

    @Test
    fun `containsTimestamp correctly matches the same month only`() {
        val current = YearMonth.current()
        val now = Clock.System.now().toEpochMilliseconds()
        assertTrue(current.containsTimestamp(now))
        assertFalse(current.previous().containsTimestamp(now))
        assertFalse(current.next().containsTimestamp(now))
    }

    @Test
    fun `displayLabel renders a capitalized French month name`() {
        assertEquals("Août 2026", YearMonth(2026, 7).displayLabel)
        assertEquals("Janvier 2027", YearMonth(2027, 0).displayLabel)
    }
}
