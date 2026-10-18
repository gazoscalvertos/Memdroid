package com.memdroid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatTest {
    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun formatsDecimalUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 kB", formatBytes(1_500))
        assertEquals("2.0 GB", formatBytes(2_000_000_000))
    }

    @Test
    fun formatsDurations() {
        assertEquals("<1 min", formatDuration(30_000))
        assertEquals("5 min", formatDuration(300_000))
        assertEquals("1 h 30 min", formatDuration(90 * 60_000L))
    }

    @Test
    fun snapshotRoundTripsThroughCsv() {
        val s = Snapshot(1L, 2L, 4L, 5L, 10L, 80)
        assertEquals(s, Snapshot.fromCsv(s.toCsv()))
        assertEquals(50, s.ramAvailablePercent)
        assertNull(Snapshot.fromCsv("not,a,row"))
    }
}
