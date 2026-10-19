package com.memdroid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test
    fun checkinSumsSinceChargeRowsPerUid() {
        val out = """
            9,0,i,vers,36,214,AP4A,AP4A
            9,10123,l,pwi,uid,12.5,0,0,0
            9,10123,l,pwi,uid,2.5,0,0,0
            9,10456,l,pwi,uid,40.0,0,0,0
            9,10456,u,pwi,uid,99.0,0,0,0
            9,0,l,pwi,scrn,300.0,0,0,0
            9,1010789,l,pwi,uid,7.0,0,0,0
        """.trimIndent()
        val parsed = Parsers.batteryCheckin(out)
        assertEquals(15.0, parsed[10123]!!, 0.001)
        assertEquals(40.0, parsed[10456]!!, 0.001)
        assertEquals(7.0, parsed[1010789]!!, 0.001)
        assertEquals(3, parsed.size)
    }

    @Test
    fun textSectionParsesUserAppAndPlainUids() {
        val out = """
            Statistics since last charge:
              Estimated power use (mAh):
                Capacity: 5060, Computed drain: 1234, actual drain: 1200-1300
                Global
                UID u0a245: 51.2 fg: 40.1 bg: 11.1 ( screen=30.0 cpu=21.2 )
                UID 1000: 20.5 ( cpu=20.5 )
                UID u10a12: 3.0 ( cpu=3.0 )
            Other section:
                UID u0a999: 1000.0
        """.trimIndent()
        val parsed = Parsers.batteryText(out)
        assertEquals(51.2, parsed[10245]!!, 0.001)
        assertEquals(20.5, parsed[1000]!!, 0.001)
        assertEquals(3.0, parsed[10 * 100_000 + 10_012]!!, 0.001)
        assertFalse(parsed.containsKey(10999))
    }

    @Test
    fun textWithoutSectionIsEmpty() {
        assertTrue(Parsers.batteryText("nothing here").isEmpty())
    }

    @Test
    fun userIdSeparatesProfiles() {
        assertEquals(0, Parsers.userIdOf(10245))
        assertEquals(10, Parsers.userIdOf(1_010_696))
    }

    @Test
    fun psGroupsProcessesByPackage() {
        val out = """
              RSS NAME
            204800 com.whatsapp
             51200 com.whatsapp:pushservice
            100000 system_server
             20480 /system/bin/surfaceflinger
             10240 com.google.android.gms.persistent
        """.trimIndent()
        val parsed = Parsers.psByPackage(out)
        assertEquals(Parsers.ProcessUsage((204800L + 51200L) * 1024, 2), parsed["com.whatsapp"])
        assertEquals(1, parsed["com.google.android.gms.persistent"]!!.processes)
        assertFalse(parsed.containsKey("/system/bin/surfaceflinger"))
    }

    @Test
    fun packageListReadsFrozenApps() {
        val out = "package:com.example.one\npackage:com.example.two\n\nWarning: junk line\n"
        assertEquals(setOf("com.example.one", "com.example.two"), Parsers.packageList(out))
    }

    @Test
    fun backgroundRestrictedFindsIgnoredApps() {
        val out = """
            com.example.one RUN_ANY_IN_BACKGROUND: ignore; time=+2d3h
            com.example.two RUN_ANY_IN_BACKGROUND: allow
            com.example.three No operations.
            com.example.four RUN_ANY_IN_BACKGROUND: ignore
        """.trimIndent()
        assertEquals(setOf("com.example.one", "com.example.four"), Parsers.backgroundRestricted(out))
    }
}

