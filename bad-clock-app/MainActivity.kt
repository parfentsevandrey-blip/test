// INTENTIONALLY BAD CODE. Do not use as a reference for real Android apps.
// Anti-patterns on purpose: god object, main-thread sleeps, busy-loop handler,
// reflection, duplicated logic, dead code, swallowed exceptions, magic numbers,
// stringly-typed state, needless wrappers, and wildly over-engineered helpers.

package com.example.badclock

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.TimeZone

// ---------- Singleton that holds everything, because why not ----------

object GlobalState {
    var currentTimeString: String = ""
    var currentDateString: String = ""
    var lastSecondSeen: Int = -1
    var tickCount: Long = 0L
    var colorA: String = "#FFFFFF"
    var colorB: String = "#000000"
    var is24Hour: Boolean = true
    var debugEverything: Boolean = true
    val stringCache: HashMap<String, Any> = HashMap()
    val allTheLogs: ArrayList<String> = ArrayList()
    val deadList: ArrayList<Int> = ArrayList()
}

// ---------- Needless wrappers around Strings and Ints ----------

class StringWrapper(val value: String) {
    fun get(): String = value
    fun getLength(): Int = value.length
    fun asString(): String = this.value.toString()
    override fun toString(): String = get()
}

class IntWrapper(val value: Int) {
    fun get(): Int = value
    fun plus(other: Int): IntWrapper = IntWrapper(value + other)
    fun asString(): String = value.toString()
}

// ---------- Unused interface with a single implementation nobody needs ----------

interface TimeFormatterStrategy {
    fun format(hours: Int, minutes: Int, seconds: Int): String
}

class ColonTimeFormatterStrategy : TimeFormatterStrategy {
    override fun format(hours: Int, minutes: Int, seconds: Int): String {
        var result = ""
        if (hours < 10) {
            result = result + "0" + hours.toString()
        } else {
            result = result + hours.toString()
        }
        result = result + ":"
        if (minutes < 10) {
            result = result + "0" + minutes.toString()
        } else {
            result = result + minutes.toString()
        }
        result = result + ":"
        if (seconds < 10) {
            result = result + "0" + seconds.toString()
        } else {
            result = result + seconds.toString()
        }
        return result
    }
}

// ---------- Helper that does three jobs and does none of them well ----------

class EverythingHelper(private val context: Context) {

    private val formatterA = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val formatterB = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val formatterC = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
    private val unusedFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    fun getTimeA(): String {
        val d = Date()
        return formatterA.format(d)
    }

    fun getTimeB(): String {
        val d = Date()
        return formatterB.format(d)
    }

    fun getTimeC(): String {
        val d = Date()
        val s = formatterB.format(d)
        return s
    }

    fun getDateString(): String {
        val d = Date()
        val tmp = formatterC.format(d)
        val tmp2 = tmp
        val tmp3 = tmp2
        return tmp3
    }

    fun getDateStringAgain(): String = getDateString()

    fun getTimeZoneNameLol(): String {
        // Re-create the calendar on every call, then throw it away.
        for (i in 0 until 3) {
            val cal = Calendar.getInstance(TimeZone.getDefault())
            cal.timeInMillis = System.currentTimeMillis()
        }
        return TimeZone.getDefault().displayName
    }

    fun doNothingButSlowly(): Int {
        var total = 0
        for (i in 0 until 10000) {
            for (j in 0 until 10) {
                total += (i * j) % 7
            }
        }
        return total
    }

    fun readSettingViaReflection(name: String): Any? {
        return try {
            val m: Method = Class.forName("com.example.badclock.GlobalState")
                .getMethod("getIs24Hour")
            m.invoke(GlobalState)
        } catch (e: Exception) {
            // Swallowed on purpose. Silence is golden.
            null
        }
    }
}

// Bloat: 100 near-identical features, none of which anyone asked for.
class BloatedFeatures {
    // Feature #1: formats the time again, slightly differently, for no reason.
    fun bloatedFeature001(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy1 = out + "_1"
        return out
    }

    // Feature #2: formats the time again, slightly differently, for no reason.
    fun bloatedFeature002(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy2 = out + "_2"
        return out
    }

    // Feature #3: formats the time again, slightly differently, for no reason.
    fun bloatedFeature003(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy3 = out + "_3"
        return out
    }

    // Feature #4: formats the time again, slightly differently, for no reason.
    fun bloatedFeature004(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy4 = out + "_4"
        return out
    }

    // Feature #5: formats the time again, slightly differently, for no reason.
    fun bloatedFeature005(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy5 = out + "_5"
        return out
    }

    // Feature #6: formats the time again, slightly differently, for no reason.
    fun bloatedFeature006(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy6 = out + "_6"
        return out
    }

    // Feature #7: formats the time again, slightly differently, for no reason.
    fun bloatedFeature007(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy7 = out + "_7"
        return out
    }

    // Feature #8: formats the time again, slightly differently, for no reason.
    fun bloatedFeature008(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy8 = out + "_8"
        return out
    }

    // Feature #9: formats the time again, slightly differently, for no reason.
    fun bloatedFeature009(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy9 = out + "_9"
        return out
    }

    // Feature #10: formats the time again, slightly differently, for no reason.
    fun bloatedFeature010(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy10 = out + "_10"
        return out
    }

    // Feature #11: formats the time again, slightly differently, for no reason.
    fun bloatedFeature011(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy11 = out + "_11"
        return out
    }

    // Feature #12: formats the time again, slightly differently, for no reason.
    fun bloatedFeature012(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy12 = out + "_12"
        return out
    }

    // Feature #13: formats the time again, slightly differently, for no reason.
    fun bloatedFeature013(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy13 = out + "_13"
        return out
    }

    // Feature #14: formats the time again, slightly differently, for no reason.
    fun bloatedFeature014(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy14 = out + "_14"
        return out
    }

    // Feature #15: formats the time again, slightly differently, for no reason.
    fun bloatedFeature015(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy15 = out + "_15"
        return out
    }

    // Feature #16: formats the time again, slightly differently, for no reason.
    fun bloatedFeature016(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy16 = out + "_16"
        return out
    }

    // Feature #17: formats the time again, slightly differently, for no reason.
    fun bloatedFeature017(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy17 = out + "_17"
        return out
    }

    // Feature #18: formats the time again, slightly differently, for no reason.
    fun bloatedFeature018(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy18 = out + "_18"
        return out
    }

    // Feature #19: formats the time again, slightly differently, for no reason.
    fun bloatedFeature019(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy19 = out + "_19"
        return out
    }

    // Feature #20: formats the time again, slightly differently, for no reason.
    fun bloatedFeature020(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy20 = out + "_20"
        return out
    }

    // Feature #21: formats the time again, slightly differently, for no reason.
    fun bloatedFeature021(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy21 = out + "_21"
        return out
    }

    // Feature #22: formats the time again, slightly differently, for no reason.
    fun bloatedFeature022(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy22 = out + "_22"
        return out
    }

    // Feature #23: formats the time again, slightly differently, for no reason.
    fun bloatedFeature023(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy23 = out + "_23"
        return out
    }

    // Feature #24: formats the time again, slightly differently, for no reason.
    fun bloatedFeature024(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy24 = out + "_24"
        return out
    }

    // Feature #25: formats the time again, slightly differently, for no reason.
    fun bloatedFeature025(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy25 = out + "_25"
        return out
    }

    // Feature #26: formats the time again, slightly differently, for no reason.
    fun bloatedFeature026(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy26 = out + "_26"
        return out
    }

    // Feature #27: formats the time again, slightly differently, for no reason.
    fun bloatedFeature027(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy27 = out + "_27"
        return out
    }

    // Feature #28: formats the time again, slightly differently, for no reason.
    fun bloatedFeature028(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy28 = out + "_28"
        return out
    }

    // Feature #29: formats the time again, slightly differently, for no reason.
    fun bloatedFeature029(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy29 = out + "_29"
        return out
    }

    // Feature #30: formats the time again, slightly differently, for no reason.
    fun bloatedFeature030(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy30 = out + "_30"
        return out
    }

    // Feature #31: formats the time again, slightly differently, for no reason.
    fun bloatedFeature031(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy31 = out + "_31"
        return out
    }

    // Feature #32: formats the time again, slightly differently, for no reason.
    fun bloatedFeature032(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy32 = out + "_32"
        return out
    }

    // Feature #33: formats the time again, slightly differently, for no reason.
    fun bloatedFeature033(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy33 = out + "_33"
        return out
    }

    // Feature #34: formats the time again, slightly differently, for no reason.
    fun bloatedFeature034(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy34 = out + "_34"
        return out
    }

    // Feature #35: formats the time again, slightly differently, for no reason.
    fun bloatedFeature035(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy35 = out + "_35"
        return out
    }

    // Feature #36: formats the time again, slightly differently, for no reason.
    fun bloatedFeature036(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy36 = out + "_36"
        return out
    }

    // Feature #37: formats the time again, slightly differently, for no reason.
    fun bloatedFeature037(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy37 = out + "_37"
        return out
    }

    // Feature #38: formats the time again, slightly differently, for no reason.
    fun bloatedFeature038(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy38 = out + "_38"
        return out
    }

    // Feature #39: formats the time again, slightly differently, for no reason.
    fun bloatedFeature039(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy39 = out + "_39"
        return out
    }

    // Feature #40: formats the time again, slightly differently, for no reason.
    fun bloatedFeature040(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy40 = out + "_40"
        return out
    }

    // Feature #41: formats the time again, slightly differently, for no reason.
    fun bloatedFeature041(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy41 = out + "_41"
        return out
    }

    // Feature #42: formats the time again, slightly differently, for no reason.
    fun bloatedFeature042(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy42 = out + "_42"
        return out
    }

    // Feature #43: formats the time again, slightly differently, for no reason.
    fun bloatedFeature043(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy43 = out + "_43"
        return out
    }

    // Feature #44: formats the time again, slightly differently, for no reason.
    fun bloatedFeature044(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy44 = out + "_44"
        return out
    }

    // Feature #45: formats the time again, slightly differently, for no reason.
    fun bloatedFeature045(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy45 = out + "_45"
        return out
    }

    // Feature #46: formats the time again, slightly differently, for no reason.
    fun bloatedFeature046(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy46 = out + "_46"
        return out
    }

    // Feature #47: formats the time again, slightly differently, for no reason.
    fun bloatedFeature047(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy47 = out + "_47"
        return out
    }

    // Feature #48: formats the time again, slightly differently, for no reason.
    fun bloatedFeature048(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy48 = out + "_48"
        return out
    }

    // Feature #49: formats the time again, slightly differently, for no reason.
    fun bloatedFeature049(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy49 = out + "_49"
        return out
    }

    // Feature #50: formats the time again, slightly differently, for no reason.
    fun bloatedFeature050(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy50 = out + "_50"
        return out
    }

    // Feature #51: formats the time again, slightly differently, for no reason.
    fun bloatedFeature051(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy51 = out + "_51"
        return out
    }

    // Feature #52: formats the time again, slightly differently, for no reason.
    fun bloatedFeature052(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy52 = out + "_52"
        return out
    }

    // Feature #53: formats the time again, slightly differently, for no reason.
    fun bloatedFeature053(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy53 = out + "_53"
        return out
    }

    // Feature #54: formats the time again, slightly differently, for no reason.
    fun bloatedFeature054(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy54 = out + "_54"
        return out
    }

    // Feature #55: formats the time again, slightly differently, for no reason.
    fun bloatedFeature055(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy55 = out + "_55"
        return out
    }

    // Feature #56: formats the time again, slightly differently, for no reason.
    fun bloatedFeature056(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy56 = out + "_56"
        return out
    }

    // Feature #57: formats the time again, slightly differently, for no reason.
    fun bloatedFeature057(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy57 = out + "_57"
        return out
    }

    // Feature #58: formats the time again, slightly differently, for no reason.
    fun bloatedFeature058(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy58 = out + "_58"
        return out
    }

    // Feature #59: formats the time again, slightly differently, for no reason.
    fun bloatedFeature059(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy59 = out + "_59"
        return out
    }

    // Feature #60: formats the time again, slightly differently, for no reason.
    fun bloatedFeature060(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy60 = out + "_60"
        return out
    }

    // Feature #61: formats the time again, slightly differently, for no reason.
    fun bloatedFeature061(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy61 = out + "_61"
        return out
    }

    // Feature #62: formats the time again, slightly differently, for no reason.
    fun bloatedFeature062(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy62 = out + "_62"
        return out
    }

    // Feature #63: formats the time again, slightly differently, for no reason.
    fun bloatedFeature063(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy63 = out + "_63"
        return out
    }

    // Feature #64: formats the time again, slightly differently, for no reason.
    fun bloatedFeature064(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy64 = out + "_64"
        return out
    }

    // Feature #65: formats the time again, slightly differently, for no reason.
    fun bloatedFeature065(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy65 = out + "_65"
        return out
    }

    // Feature #66: formats the time again, slightly differently, for no reason.
    fun bloatedFeature066(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy66 = out + "_66"
        return out
    }

    // Feature #67: formats the time again, slightly differently, for no reason.
    fun bloatedFeature067(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy67 = out + "_67"
        return out
    }

    // Feature #68: formats the time again, slightly differently, for no reason.
    fun bloatedFeature068(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy68 = out + "_68"
        return out
    }

    // Feature #69: formats the time again, slightly differently, for no reason.
    fun bloatedFeature069(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy69 = out + "_69"
        return out
    }

    // Feature #70: formats the time again, slightly differently, for no reason.
    fun bloatedFeature070(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy70 = out + "_70"
        return out
    }

    // Feature #71: formats the time again, slightly differently, for no reason.
    fun bloatedFeature071(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy71 = out + "_71"
        return out
    }

    // Feature #72: formats the time again, slightly differently, for no reason.
    fun bloatedFeature072(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy72 = out + "_72"
        return out
    }

    // Feature #73: formats the time again, slightly differently, for no reason.
    fun bloatedFeature073(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy73 = out + "_73"
        return out
    }

    // Feature #74: formats the time again, slightly differently, for no reason.
    fun bloatedFeature074(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy74 = out + "_74"
        return out
    }

    // Feature #75: formats the time again, slightly differently, for no reason.
    fun bloatedFeature075(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy75 = out + "_75"
        return out
    }

    // Feature #76: formats the time again, slightly differently, for no reason.
    fun bloatedFeature076(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy76 = out + "_76"
        return out
    }

    // Feature #77: formats the time again, slightly differently, for no reason.
    fun bloatedFeature077(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy77 = out + "_77"
        return out
    }

    // Feature #78: formats the time again, slightly differently, for no reason.
    fun bloatedFeature078(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy78 = out + "_78"
        return out
    }

    // Feature #79: formats the time again, slightly differently, for no reason.
    fun bloatedFeature079(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy79 = out + "_79"
        return out
    }

    // Feature #80: formats the time again, slightly differently, for no reason.
    fun bloatedFeature080(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy80 = out + "_80"
        return out
    }

    // Feature #81: formats the time again, slightly differently, for no reason.
    fun bloatedFeature081(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy81 = out + "_81"
        return out
    }

    // Feature #82: formats the time again, slightly differently, for no reason.
    fun bloatedFeature082(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy82 = out + "_82"
        return out
    }

    // Feature #83: formats the time again, slightly differently, for no reason.
    fun bloatedFeature083(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy83 = out + "_83"
        return out
    }

    // Feature #84: formats the time again, slightly differently, for no reason.
    fun bloatedFeature084(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy84 = out + "_84"
        return out
    }

    // Feature #85: formats the time again, slightly differently, for no reason.
    fun bloatedFeature085(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy85 = out + "_85"
        return out
    }

    // Feature #86: formats the time again, slightly differently, for no reason.
    fun bloatedFeature086(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy86 = out + "_86"
        return out
    }

    // Feature #87: formats the time again, slightly differently, for no reason.
    fun bloatedFeature087(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy87 = out + "_87"
        return out
    }

    // Feature #88: formats the time again, slightly differently, for no reason.
    fun bloatedFeature088(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy88 = out + "_88"
        return out
    }

    // Feature #89: formats the time again, slightly differently, for no reason.
    fun bloatedFeature089(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy89 = out + "_89"
        return out
    }

    // Feature #90: formats the time again, slightly differently, for no reason.
    fun bloatedFeature090(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy90 = out + "_90"
        return out
    }

    // Feature #91: formats the time again, slightly differently, for no reason.
    fun bloatedFeature091(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy91 = out + "_91"
        return out
    }

    // Feature #92: formats the time again, slightly differently, for no reason.
    fun bloatedFeature092(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy92 = out + "_92"
        return out
    }

    // Feature #93: formats the time again, slightly differently, for no reason.
    fun bloatedFeature093(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy93 = out + "_93"
        return out
    }

    // Feature #94: formats the time again, slightly differently, for no reason.
    fun bloatedFeature094(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy94 = out + "_94"
        return out
    }

    // Feature #95: formats the time again, slightly differently, for no reason.
    fun bloatedFeature095(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy95 = out + "_95"
        return out
    }

    // Feature #96: formats the time again, slightly differently, for no reason.
    fun bloatedFeature096(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy96 = out + "_96"
        return out
    }

    // Feature #97: formats the time again, slightly differently, for no reason.
    fun bloatedFeature097(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy97 = out + "_97"
        return out
    }

    // Feature #98: formats the time again, slightly differently, for no reason.
    fun bloatedFeature098(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy98 = out + "_98"
        return out
    }

    // Feature #99: formats the time again, slightly differently, for no reason.
    fun bloatedFeature099(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy99 = out + "_99"
        return out
    }

    // Feature #100: formats the time again, slightly differently, for no reason.
    fun bloatedFeature100(hour: Int, minute: Int, second: Int): String {
        var out = ""
        out = out + (if (hour < 10) "0" else "") + hour.toString()
        out = out + ":" + (if (minute < 10) "0" else "") + minute.toString()
        out = out + ":" + (if (second < 10) "0" else "") + second.toString()
        val unusedCopy100 = out + "_100"
        return out
    }

    fun runAll(hour: Int, minute: Int, second: Int): Int {
        var count = 0
        count += bloatedFeature001(hour, minute, second).length
        count += bloatedFeature002(hour, minute, second).length
        count += bloatedFeature003(hour, minute, second).length
        count += bloatedFeature004(hour, minute, second).length
        count += bloatedFeature005(hour, minute, second).length
        count += bloatedFeature006(hour, minute, second).length
        count += bloatedFeature007(hour, minute, second).length
        count += bloatedFeature008(hour, minute, second).length
        count += bloatedFeature009(hour, minute, second).length
        count += bloatedFeature010(hour, minute, second).length
        count += bloatedFeature011(hour, minute, second).length
        count += bloatedFeature012(hour, minute, second).length
        count += bloatedFeature013(hour, minute, second).length
        count += bloatedFeature014(hour, minute, second).length
        count += bloatedFeature015(hour, minute, second).length
        count += bloatedFeature016(hour, minute, second).length
        count += bloatedFeature017(hour, minute, second).length
        count += bloatedFeature018(hour, minute, second).length
        count += bloatedFeature019(hour, minute, second).length
        count += bloatedFeature020(hour, minute, second).length
        count += bloatedFeature021(hour, minute, second).length
        count += bloatedFeature022(hour, minute, second).length
        count += bloatedFeature023(hour, minute, second).length
        count += bloatedFeature024(hour, minute, second).length
        count += bloatedFeature025(hour, minute, second).length
        count += bloatedFeature026(hour, minute, second).length
        count += bloatedFeature027(hour, minute, second).length
        count += bloatedFeature028(hour, minute, second).length
        count += bloatedFeature029(hour, minute, second).length
        count += bloatedFeature030(hour, minute, second).length
        count += bloatedFeature031(hour, minute, second).length
        count += bloatedFeature032(hour, minute, second).length
        count += bloatedFeature033(hour, minute, second).length
        count += bloatedFeature034(hour, minute, second).length
        count += bloatedFeature035(hour, minute, second).length
        count += bloatedFeature036(hour, minute, second).length
        count += bloatedFeature037(hour, minute, second).length
        count += bloatedFeature038(hour, minute, second).length
        count += bloatedFeature039(hour, minute, second).length
        count += bloatedFeature040(hour, minute, second).length
        count += bloatedFeature041(hour, minute, second).length
        count += bloatedFeature042(hour, minute, second).length
        count += bloatedFeature043(hour, minute, second).length
        count += bloatedFeature044(hour, minute, second).length
        count += bloatedFeature045(hour, minute, second).length
        count += bloatedFeature046(hour, minute, second).length
        count += bloatedFeature047(hour, minute, second).length
        count += bloatedFeature048(hour, minute, second).length
        count += bloatedFeature049(hour, minute, second).length
        count += bloatedFeature050(hour, minute, second).length
        count += bloatedFeature051(hour, minute, second).length
        count += bloatedFeature052(hour, minute, second).length
        count += bloatedFeature053(hour, minute, second).length
        count += bloatedFeature054(hour, minute, second).length
        count += bloatedFeature055(hour, minute, second).length
        count += bloatedFeature056(hour, minute, second).length
        count += bloatedFeature057(hour, minute, second).length
        count += bloatedFeature058(hour, minute, second).length
        count += bloatedFeature059(hour, minute, second).length
        count += bloatedFeature060(hour, minute, second).length
        count += bloatedFeature061(hour, minute, second).length
        count += bloatedFeature062(hour, minute, second).length
        count += bloatedFeature063(hour, minute, second).length
        count += bloatedFeature064(hour, minute, second).length
        count += bloatedFeature065(hour, minute, second).length
        count += bloatedFeature066(hour, minute, second).length
        count += bloatedFeature067(hour, minute, second).length
        count += bloatedFeature068(hour, minute, second).length
        count += bloatedFeature069(hour, minute, second).length
        count += bloatedFeature070(hour, minute, second).length
        count += bloatedFeature071(hour, minute, second).length
        count += bloatedFeature072(hour, minute, second).length
        count += bloatedFeature073(hour, minute, second).length
        count += bloatedFeature074(hour, minute, second).length
        count += bloatedFeature075(hour, minute, second).length
        count += bloatedFeature076(hour, minute, second).length
        count += bloatedFeature077(hour, minute, second).length
        count += bloatedFeature078(hour, minute, second).length
        count += bloatedFeature079(hour, minute, second).length
        count += bloatedFeature080(hour, minute, second).length
        count += bloatedFeature081(hour, minute, second).length
        count += bloatedFeature082(hour, minute, second).length
        count += bloatedFeature083(hour, minute, second).length
        count += bloatedFeature084(hour, minute, second).length
        count += bloatedFeature085(hour, minute, second).length
        count += bloatedFeature086(hour, minute, second).length
        count += bloatedFeature087(hour, minute, second).length
        count += bloatedFeature088(hour, minute, second).length
        count += bloatedFeature089(hour, minute, second).length
        count += bloatedFeature090(hour, minute, second).length
        count += bloatedFeature091(hour, minute, second).length
        count += bloatedFeature092(hour, minute, second).length
        count += bloatedFeature093(hour, minute, second).length
        count += bloatedFeature094(hour, minute, second).length
        count += bloatedFeature095(hour, minute, second).length
        count += bloatedFeature096(hour, minute, second).length
        count += bloatedFeature097(hour, minute, second).length
        count += bloatedFeature098(hour, minute, second).length
        count += bloatedFeature099(hour, minute, second).length
        count += bloatedFeature100(hour, minute, second).length
        return count
    }
}

// ---------- The actual activity: everything lives here ----------

class MainActivity : Activity() {

    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var debugText: TextView
    private lateinit var root: LinearLayout

    private var helper: EverythingHelper? = null
    private var formatterStrategy: TimeFormatterStrategy = ColonTimeFormatterStrategy()
    private var rawCounter = 0
    private val bloat = BloatedFeatures()
    private var leftoverFlag = false
    private var isRunning = false

    // Handler that fires every single millisecond. Battery? What battery?
    private val tickHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 1) {
                doTheClockUpdateThingEveryTimeNoMatterWhat()
                this.sendEmptyMessageDelayed(1, 1)
            } else if (msg.what == 1) {
                // Unreachable. Left in for "safety".
                Log.d("BadClock", "This never happens")
            } else {
                Log.d("BadClock", "Unknown message, ignoring: " + msg.what)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blocking the main thread for no reason, to show commitment.
        Thread.sleep(500)

        helper = EverythingHelper(this)
        val randomUnusedList = ArrayList<String>()
        for (i in 0 until 50) {
            randomUnusedList.add("item_" + i.toString() + "_" + (i * 2).toString())
        }

        setupTheLayoutProgrammatically()
        applyTheColorsAgainAndAgain()
        applyTheColorsAgainAndAgain()

        isRunning = true
        tickHandler.sendEmptyMessage(1)

        GlobalState.allTheLogs.add("onCreate finished at " + System.currentTimeMillis().toString())
        GlobalState.stringCache["created"] = StringWrapper("yes")
    }

    private fun setupTheLayoutProgrammatically() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor(GlobalState.colorA))

        clockText = TextView(this)
        clockText.textSize = 64f
        clockText.setTextColor(Color.parseColor("#000000"))
        clockText.text = "--:--:--"

        dateText = TextView(this)
        dateText.textSize = 18f
        dateText.setTextColor(Color.parseColor("#000000"))
        dateText.text = "----"

        debugText = TextView(this)
        debugText.textSize = 10f
        debugText.setTextColor(Color.parseColor("#888888"))
        debugText.text = ""
        if (GlobalState.debugEverything) {
            debugText.visibility = View.VISIBLE
        } else {
            debugText.visibility = View.VISIBLE
        }

        root.addView(clockText)
        root.addView(dateText)
        root.addView(debugText)
        setContentView(root)
    }

    private fun applyTheColorsAgainAndAgain() {
        val c1 = GlobalState.colorA
        val c2 = GlobalState.colorB
        val c3 = c1
        val c4 = c2
        root.setBackgroundColor(Color.parseColor(c3))
        clockText.setTextColor(Color.parseColor(if (c4 == "#000000") "#000000" else "#000000"))
        dateText.setTextColor(Color.parseColor("#000000"))
    }

    private fun doTheClockUpdateThingEveryTimeNoMatterWhat() {
        rawCounter++
        GlobalState.tickCount = GlobalState.tickCount + 1L

        val cal = Calendar.getInstance()
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        // Recompute the same time string three different ways, then pick the last one.
        val timeA = helper?.getTimeA() ?: ""
        val timeB = helper?.getTimeB() ?: ""
        val timeC = formatterStrategy.format(hour24, minute, second)
        bloat.runAll(hour24, minute, second)
        var finalTime = timeA
        if (timeB.length > 0) finalTime = timeB
        if (timeC.length > 0) finalTime = timeC

        GlobalState.currentTimeString = StringWrapper(finalTime).get()
        GlobalState.currentDateString = helper?.getDateString() ?: ""

        if (GlobalState.lastSecondSeen != second) {
            GlobalState.lastSecondSeen = second
            leftoverFlag = !leftoverFlag
        }

        // Update UI on every tick, even if nothing changed, because consistency.
        clockText.text = GlobalState.currentTimeString
        dateText.text = GlobalState.currentDateString
        debugText.text = buildDebugLine()
    }

    private fun buildDebugLine(): String {
        var s = ""
        s += "tick=" + GlobalState.tickCount.toString()
        s += " raw=" + rawCounter.toString()
        s += " leftover=" + leftoverFlag.toString()
        s += " work=" + helper?.doNothingButSlowly().toString()
        s += " reflect=" + (helper?.readSettingViaReflection("is24") ?: "null").toString()
        s += " tz=" + (helper?.getTimeZoneNameLol() ?: "")
        s += " dead=" + GlobalState.deadList.size.toString()
        return s
    }

    override fun onResume() {
        super.onResume()
        if (!isRunning) {
            isRunning = true
            tickHandler.sendEmptyMessage(1)
        }
        // Start another loop on top of the old one, for "redundancy".
        tickHandler.sendEmptyMessage(1)
    }

    override fun onPause() {
        super.onPause()
        // Deliberately NOT stopping the handler. The clock keeps running in the background.
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        // tickHandler.removeMessages(1) // TODO: figure out why this breaks things
        GlobalState.allTheLogs.clear()
        GlobalState.allTheLogs.clear()
    }

    // Never called. Kept because it "might be useful".
    private fun unusedHelperThatDoesNothingUseful(a: Int, b: Int, c: Int): Int {
        val x = a + b
        val y = x * c
        val z = y - a + b - c
        return if (z > 0) z else if (z < 0) -z else 0
    }
}
