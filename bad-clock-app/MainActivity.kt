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

// ---------- The actual activity: everything lives here ----------

class MainActivity : Activity() {

    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var debugText: TextView
    private lateinit var root: LinearLayout

    private var helper: EverythingHelper? = null
    private var formatterStrategy: TimeFormatterStrategy = ColonTimeFormatterStrategy()
    private var rawCounter = 0
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
