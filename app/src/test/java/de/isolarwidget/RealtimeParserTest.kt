package de.isolarwidget

import de.isolarwidget.data.RealtimeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealtimeParserTest {

    private fun parse(json: String, invert: Boolean = false) =
        RealtimeParser.parse(Json.parseToJsonElement(json).jsonObject, "123", invert)

    @Test
    fun parsesFlatEntriesWithBattery() {
        val v = parse("""{"device_point_list":[{"ps_id":123,"p83067":"2450.0","p83252":"0.87","p83549":"-1200","p83022":"10340"}]}""")
        assertEquals(2450.0, v.pvPowerW!!, 0.01)
        assertEquals(87.0, v.batterySocPct!!, 0.01)
        assertEquals(-1200.0, v.gridPowerW!!, 0.01)
        assertEquals(10.34, v.dailyYieldKwh!!, 0.001)
    }

    @Test
    fun missingBatteryIsNullAndFallbacksWork() {
        val v = parse("""{"device_point_list":[{"device_point":{"ps_id":"123","p83033":"800","p83022":"--","p83331":"5000"}}]}""")
        assertEquals(800.0, v.pvPowerW!!, 0.01)
        assertNull(v.batterySocPct)
        assertNull(v.gridPowerW)
        assertEquals(5.0, v.dailyYieldKwh!!, 0.001)
    }

    @Test
    fun invertsGridSign() {
        val v = parse("""{"device_point_list":[{"ps_id":"123","p83549":"300"}]}""", invert = true)
        assertEquals(-300.0, v.gridPowerW!!, 0.01)
    }
}
