package io.github.nindzyago.torrentodrive.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortSizeTest {
    @Test fun wholeUnitsFromTen() {
        assertEquals("20 GB", shortSize("19.98 GB"))
        assertEquals("32 GB", shortSize("32.24 GB"))
        assertEquals("701 MB", shortSize("700.5 MB"))
        assertEquals("15 ГБ", shortSize("15,33 ГБ"))
    }

    @Test fun oneDecimalBelowTen() {
        assertEquals("8.7 GB", shortSize("8.73 GB"))
        assertEquals("2.2 GB", shortSize("2.21 GB"))
        assertEquals("1,5 ГБ", shortSize("1,46 ГБ"))
    }

    @Test fun roundingUpToTenDropsTheDecimal() {
        assertEquals("10 GB", shortSize("9.97 GB"))
        assertEquals("3 GB", shortSize("2.98 GB"))
    }

    @Test fun noSpaceAndWholeNumbers() {
        assertEquals("6.4 GB", shortSize("6.38GB"))
        assertEquals("700 MB", shortSize("700 MB"))
    }

    @Test fun unknownStaysAsIs() {
        assertEquals("", shortSize(""))
        assertEquals("big", shortSize("big"))
    }

    @Test
    fun russianUnitsAndComma() {
        assertEquals("14,29 ГБ", localSize("14.29 GB", "ru"))
        assertEquals("746 МБ", localSize("746 MB", "ru"))
        assertEquals("1,2 ТБ", localSize("1.2 TB", "ru"))
    }

    @Test
    fun otherLanguagesAndUnknownFormatsUnchanged() {
        assertEquals("14.29 GB", localSize("14.29 GB", "en"))
        assertEquals("about 3 GB", localSize("about 3 GB", "ru"))
    }
}
