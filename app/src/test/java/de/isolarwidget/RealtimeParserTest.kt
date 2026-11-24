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

    @Test
    fun parsesDailyValuesFromUserPlant() {
        val v = parse("""{"device_point_list":[{"ps_id":"123","p83033":"2592.0","p83106":"1429.0","p83118":"15100.0",
            "p83102":"8100.0","p83072":"0.0","p83322":"4300.0","p83323":"400.0","p83252":"0.446","p83022":"10900.0"}]}""")
        assertEquals(2592.0, v.pvPowerW!!, 0.01)
        assertEquals(1429.0, v.loadPowerW!!, 0.01)
        assertEquals(15.1, v.consumptionTodayKwh!!, 0.001)
        assertEquals(8.1, v.gridBuyTodayKwh!!, 0.001)
        assertEquals(0.0, v.feedInTodayKwh!!, 0.001)
        assertEquals(4.3, v.batteryChargeTodayKwh!!, 0.001)
        assertEquals(0.4, v.batteryDischargeTodayKwh!!, 0.001)
        assertEquals(44.6, v.batterySocPct!!, 0.01)
    }

    @Test
    fun parsesCurveIntoQuarterHourSlots() {
        val pv = arrayOfNulls<Float>(96)
        val load = arrayOfNulls<Float>(96)
        val data = Json.parseToJsonElement("""{"123":[
            {"time_stamp":"20261124100000","p83033":"2000","p83106":"500"},
            {"time_stamp":"20261124100500","p83033":"3000"},
            {"time_stamp":"20261124123000","p83033":"--","p83106":"900"}
        ],"point_dict":[]}""").jsonObject
        RealtimeParser.parseCurveInto(data, pv, load)
        assertEquals(2500f, pv[40]!!, 0.1f)   // 10:00-10:14 gemittelt
        assertEquals(500f, load[40]!!, 0.1f)
        assertNull(pv[50])                    // 12:30 ohne PV-Wert
        assertEquals(900f, load[50]!!, 0.1f)
    }
}
