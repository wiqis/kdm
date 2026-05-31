package xdman.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilitiesTest {
    @Test
    fun formatSize() {
        assertEquals("---", FormatUtilities.formatSize(-1.0))
        assertEquals("0 B", FormatUtilities.formatSize(0.0))
        assertEquals("100 B", FormatUtilities.formatSize(100.0))
        assertEquals("1023 B", FormatUtilities.formatSize(1023.0))
        assertEquals("1.0 KB", FormatUtilities.formatSize(1024.0))
        assertEquals("1.5 KB", FormatUtilities.formatSize(1536.0))
        assertEquals("1.0 MB", FormatUtilities.formatSize(1024.0 * 1024.0))
        assertEquals("1.5 MB", FormatUtilities.formatSize(1536.0 * 1024.0))
    }

    @Test
    fun hms() {
        assertEquals("00:00:00", FormatUtilities.hms(0))
        assertEquals("00:00:01", FormatUtilities.hms(1))
        assertEquals("00:01:00", FormatUtilities.hms(60))
        assertEquals("01:00:00", FormatUtilities.hms(3600))
        assertEquals("12:34:56", FormatUtilities.hms(12 * 3600 + 34 * 60 + 56))
    }
}
