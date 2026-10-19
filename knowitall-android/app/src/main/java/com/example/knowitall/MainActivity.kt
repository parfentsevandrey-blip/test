package com.example.knowitall

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Random

/**
 * Mr. Knowitall: one app to replace all the others.
 *
 * Architecture: one file, one object, one Activity. Global mutable state for convenience.
 * Everything runs on the main thread so nobody can say we parallelized anything.
 */
object Knowitall {
    lateinit var prefs: SharedPreferences
    var calcHistory = ""
    var notes = ""
    var contacts = "" // format: "Name=number;Name=number;" - yes, instead of a database
    var todos = "" // format: "task|0;task|1;" (0 = not done, 1 = done)
    var steps = 0 // steps we "walked" ourselves
}

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var tabsRow: LinearLayout

    private val modules: List<Pair<String, () -> View>> = listOf(
        "Calculator" to { calculatorScreen() },
        "Dialer" to { dialerScreen() },
        "Notes" to { notesScreen() },
        "Contacts" to { contactsScreen() },
        "Oracle" to { oracleScreen() },
        "Timer" to { timerScreen() },
        "Converter" to { converterScreen() },
        "Todo" to { todoScreen() },
        "Password" to { passwordScreen() },
        "Weather" to { weatherScreen() },
        "Stopwatch" to { stopwatchScreen() },
        "Dice" to { diceScreen() },
        "Alarm" to { alarmScreen() },
        "Guess" to { guessScreen() },
        "Coin" to { coinScreen() },
        "Cipher" to { cipherScreen() },
        "Translator" to { translatorScreen() },
        "Calendar" to { calendarScreen() },
        "Steps" to { pedometerScreen() },
        "Player" to { playerScreen() },
        "Words" to { wordCountScreen() },
        "Quotes" to { quotesScreen() },
        "Table" to { multiplyTableScreen() },
        "Percent" to { percentScreen() },
        "Length" to { lengthScreen() },
        "Emoji" to { emojiScreen() },
        "Binary" to { binaryScreen() },
        "Pomodoro" to { pomodoroScreen() },
        "Roman" to { romanScreen() },
        "Names" to { nameScreen() },
        "Horoscope" to { horoscopeScreen() },
        "Lottery" to { lotteryScreen() },
        "Quiz" to { quizScreen() },
        "Recipes" to { recipesScreen() },
        "Sleep" to { sleepScreen() },
        "Clock" to { clockScreen() },
        "Codes" to { asciiScreen() },
        "Palindrome" to { palindromeScreen() },
        "Factorial" to { factorialScreen() },
        "Primes" to { primesScreen() },
        "Compliment" to { complimentScreen() },
        "Joke" to { jokeScreen() },
        "Magic ball" to { magicBallScreen() },
        "Days" to { daysLeftScreen() },
        "Color" to { colorScreen() },
        "Rating" to { ratingScreen() },
        "Lifehack" to { lifehackScreen() },
        "Clicks" to { clickCounterScreen() },
        "Water" to { waterScreen() },
        "Diary" to { diaryScreen() },
        "Tic-tac-toe" to { ticTacToeScreen() },
        "Hangman" to { hangmanScreen() },
        "Elements" to { elementsScreen() },
        "Temperature" to { temperatureScreen() },
        "Speed" to { speedScreen() },
        "Area" to { areaScreen() },
        "Tip" to { tipScreen() },
        "Metronome" to { metronomeScreen() },
        "Tree" to { treeScreen() },
        "Exercise" to { exerciseScreen() },
        "Number words" to { numberWordsScreen() },
        "Squares" to { squaresScreen() },
        "Lot" to { lotScreen() },
        "Chess" to { chessScreen() },
        "Notes music" to { notesMusicScreen() },
        "Tarot" to { tarotScreen() },
        "Shopping" to { shoppingScreen() },
        "Budget" to { budgetScreen() },
        "BMI" to { bmiScreen() },
        "Pulse" to { pulseScreen() },
        "Books" to { booksScreen() },
        "Movies" to { moviesScreen() },
        "Checklist" to { checklistScreen() },
        "Fractions" to { fractionsScreen() },
        "Football" to { footballScreen() },
        "Bytes" to { bytesScreen() },
        "Bot" to { chatBotScreen() },
        "Truth table" to { truthTableScreen() },
        "Coffee" to { coffeeScreen() },
        "Stairs" to { stairsScreen() },
        "Laundry" to { laundryScreen() },
        "Frost" to { frostScreen() },
        "Acceleration" to { accelerationScreen() },
        "Proportions" to { proportionsScreen() },
        "Holidays" to { holidaysScreen() },
        "Word of the day" to { wordOfDayScreen() },
        "Dates" to { dateDiffScreen() },
        "Ohm" to { ohmScreen() },
        "Density" to { densityScreen() },
        "Efficiency" to { efficiencyScreen() },
        "Calories" to { caloriesScreen() },
        "Weekday" to { weekdayScreen() },
        "Body temp" to { bodyTempScreen() },
        "Pizza" to { pizzaScreen() },
        "Fibonacci" to { fibonacciScreen() },
        "Twin primes" to { twinPrimesScreen() },
        "Seconds" to { secondsScreen() },
        "Root" to { sqrtScreen() },
        "Deposit" to { depositScreen() },
        "Loan" to { loanScreen() },
        "Estimate" to { estimateScreen() },
        "Box" to { boxScreen() },
        "Perimeter" to { perimeterScreen() },
        "Circle" to { circleLengthScreen() },
        "Triangle" to { triangleScreen() },
        "Goal" to { goalScreen() },
        "Streak" to { streakScreen() },
        "Brushing" to { brushingScreen() },
        "Password check" to { passwordCheckScreen() },
        "Anagram" to { anagramScreen() },
        "Reverse words" to { reverseWordsScreen() },
        "Letters" to { letterFrequencyScreen() },
        "Dice score" to { diceScoreScreen() },
        "ROT13" to { rot13Screen() },
        "Morse" to { morseScreen() },
        "Digit sum" to { digitSumScreen() },
        "Armstrong" to { armstrongScreen() },
        "Triples" to { triplesScreen() },
        "Power" to { powerScreen() },
        "GCD" to { gcdScreen() },
        "LCM" to { lcmScreen() },
        "Log" to { logScreen() },
        "Sine" to { sineTableScreen() },
        "Roulette" to { rouletteScreen() },
        "Stickers" to { stickersScreen() },
        "Perfect numbers" to { perfectNumbersScreen() },
        "Discount" to { discountScreen() },
        "Minutes" to { minutesScreen() },
        "Average" to { statsScreen() },
        "Parity" to { parityScreen() },
        "Distance" to { distanceScreen() },
        "Tally" to { tallyScreen() },
        "Angle" to { angleScreen() },
        "Swap" to { swapScreen() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Knowitall.prefs = getSharedPreferences("everything", MODE_PRIVATE)
        Knowitall.notes = Knowitall.prefs.getString("notes", "") ?: ""
        Knowitall.contacts = Knowitall.prefs.getString("contacts", "") ?: ""

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content = FrameLayout(this)

        // There are many tabs, so they live in a horizontal scroll row that cuts the last one in half
        root.addView(HorizontalScrollView(this).apply { addView(tabsRow) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        modules.forEachIndexed { i, (title, _) ->
            tabsRow.addView(
                Button(this).apply {
                    text = title
                    textSize = 10f
                    setOnClickListener { showModule(i) }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        setContentView(root)
        showModule(0)
    }

    private fun showModule(index: Int) {
        // Every time, the screen is destroyed and rebuilt from scratch, even if it is the same screen
        content.removeAllViews()
        content.addView(modules[index].second())
        Toast.makeText(this, modules[index].first, Toast.LENGTH_SHORT).show()
    }

    // ---------- Shared "utilities" (hand-written, no libraries - more honest) ----------

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 32, 32, 32)
    }

    private fun label(s: String, size: Float = 16f): TextView = TextView(this).apply {
        text = s
        textSize = size
        setPadding(16, 16, 16, 16)
    }

    private fun scroll(child: View): ScrollView = ScrollView(this).apply { addView(child) }

    private fun EditText.onChange(block: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = block(s.toString())
        })
    }

    // ---------- Calculator ----------

    private fun calculatorScreen(): View {
        val display = label("", 36f)
        val result = label("", 20f)
        fun refresh() {
            display.text = Knowitall.calcHistory
            result.text = "= " + liveResult()
        }

        val grid = GridLayout(this).apply { columnCount = 4 }
        val keys = listOf(
            "(", ")", "C", "/",
            "7", "8", "9", "*",
            "4", "5", "6", "-",
            "1", "2", "3", "+",
            "0", ".", "="
        )
        keys.forEach { key ->
            grid.addView(Button(this).apply {
                text = key
                setOnClickListener {
                    onCalcKey(key)
                    refresh()
                }
            })
        }
        refresh()
        return column().apply {
            addView(display)
            addView(result)
            addView(grid)
        }
    }

    private fun onCalcKey(key: String) {
        Thread.sleep(120) // "for button feel"; on the main thread, of course
        when (key) {
            "C" -> Knowitall.calcHistory = ""
            "=" -> Toast.makeText(this, "I already show everything 🤓", Toast.LENGTH_SHORT).show()
            else -> Knowitall.calcHistory += key
        }
    }

    private fun liveResult(): String {
        if (Knowitall.calcHistory.isEmpty()) return ""
        // Recalculate the WHOLE history from scratch, not just the last expression
        return try {
            val v = evaluate(Knowitall.calcHistory.replace(" ", ""))
            if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        } catch (e: Exception) {
            "don't know 🤷"
        }
    }

    private fun replaceMatch(s: String, m: MatchResult, with: String): String =
        s.substring(0, m.range.first) + with + s.substring(m.range.last + 1)

    private fun evaluate(expr: String): Double {
        var s = expr
        // Parentheses: expand the innermost ones while they exist
        val inner = Regex("\\(([^()]*)\\)")
        while (inner.containsMatchIn(s)) {
            val m = inner.find(s)!!
            s = replaceMatch(s, m, evaluate(m.groupValues[1]).toString())
        }
        // Multiplication and division: one pair at a time, rewriting the string every time
        val mul = Regex("(-?\\d+\\.?\\d*)([*/])(-?\\d+\\.?\\d*)")
        while (true) {
            val m = mul.find(s) ?: break
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[3].toDouble()
            s = replaceMatch(s, m, (if (m.groupValues[2] == "*") a * b else a / b).toString())
        }
        // Addition and subtraction - same idea
        val add = Regex("(-?\\d+\\.?\\d*)([+-])(-?\\d+\\.?\\d*)")
        while (true) {
            val m = add.find(s) ?: break
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[3].toDouble()
            s = replaceMatch(s, m, (if (m.groupValues[2] == "+") a + b else a - b).toString())
        }
        return s.toDouble()
    }

    // ---------- Dialer ----------

    private fun dialerScreen(): View {
        val number = EditText(this).apply {
            hint = "Number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val call = Button(this).apply { text = "Call" }
        call.setOnClickListener {
            // Wait 300 ms, so the button has time to realize it was pressed
            val until = System.currentTimeMillis() + 300
            while (System.currentTimeMillis() < until) { /* busy waiting */ }
            // It does not call by itself. It opens the real dialer. It replaces exactly itself.
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.text)))
        }
        return column().apply {
            addView(label("Dialer: opens the real dialer, but at least honestly"))
            addView(number)
            addView(call)
        }
    }

    // ---------- Notes ----------

    private fun notesScreen(): View {
        val editor = EditText(this).apply {
            setText(Knowitall.notes)
            hint = "Write here"
            minLines = 8
            gravity = Gravity.TOP
        }
        val search = EditText(this).apply { hint = "Search notes" }
        val found = label("")

        editor.onChange { text ->
            Knowitall.notes = text
            // The whole notebook, synchronously, on every character. Plus a backup - forever.
            Knowitall.prefs.edit().putString("notes", text).commit()
            Knowitall.prefs.edit().putString("backup_" + System.nanoTime(), text).commit()
        }
        search.onChange { query ->
            found.text = searchNotes(query)
        }

        return column().apply {
            addView(editor)
            addView(search)
            addView(found)
        }
    }

    private fun searchNotes(q: String): String {
        var out = ""
        // Linear search, character by character, copying the tail of the string at every step
        for (i in 0 until Knowitall.notes.length) {
            if (Knowitall.notes.substring(i).startsWith(q) && q.isNotEmpty()) {
                out += Knowitall.notes.substring(i, minOf(i + 20, Knowitall.notes.length)).replace("\n", " ") + "\n"
            }
        }
        return out.ifEmpty { "nothing found" }
    }

    // ---------- Contacts ----------

    private fun contactsScreen(): View {
        val name = EditText(this).apply { hint = "Name" }
        val number = EditText(this).apply {
            hint = "Number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val list = label("")

        fun render() {
            list.text = sortedContacts().joinToString("\n") { "${it.first}: ${it.second}" }
        }

        val add = Button(this).apply {
            text = "Add"
            setOnClickListener {
                // No duplicate check: it's a "super" app. We write the whole table every time.
                Knowitall.contacts += "${name.text}=${number.text};"
                Knowitall.prefs.edit().putString("contacts", Knowitall.contacts).apply()
                render()
            }
        }

        render()
        return scroll(column().apply {
            addView(name)
            addView(number)
            addView(add)
            addView(list)
        })
    }

    private fun sortedContacts(): List<Pair<String, String>> {
        // Parse the string again on every access. Caching is for the weak.
        val rows = Knowitall.contacts
            .split(";")
            .filter { it.contains("=") }
            .map { val p = it.split("="); p[0] to p[1] }
            .toMutableList()
        // Bubble sort, because sorting is just sorting
        for (i in rows.indices) {
            for (j in 0 until rows.size - 1 - i) {
                if (rows[j].first > rows[j + 1].first) {
                    val t = rows[j]
                    rows[j] = rows[j + 1]
                    rows[j + 1] = t
                }
            }
        }
        return rows
    }

    // ---------- Oracle ----------

    private fun oracleScreen(): View {
        val question = EditText(this).apply { hint = "Ask a question" }
        val answer = label("Ask a question. I know everything.")
        val ask = Button(this).apply {
            text = "Ask"
            setOnClickListener {
                answer.text = "Thinking..."
                // We compute the answer to the ultimate question by brute force. The answer is known in advance.
                var x = 0
                while (x != 42) {
                    x++
                    Thread.sleep(50)
                }
                // The screen updates only after the handler returns, so nobody ever sees "Thinking..."
                answer.text = "Answer to \"${question.text}\": $x. Confidence 100%. Did I check? No."
            }
        }
        return column().apply {
            addView(question)
            addView(ask)
            addView(answer)
        }
    }

    // ---------- Timer ----------

    private fun timerScreen(): View {
        val seconds = EditText(this).apply {
            hint = "Seconds"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val display = label("Timer not running")
        val start = Button(this).apply { text = "Start" }
        start.setOnClickListener {
            var left = seconds.text.toString().toIntOrNull() ?: 10
            // Countdown on the main thread. The screen updates only when the countdown is over.
            while (left > 0) {
                Thread.sleep(1000)
                left--
                display.text = "Left: $left s"
            }
            Toast.makeText(this, "Ding! (but you won't hear it while we sleep)", Toast.LENGTH_LONG).show()
        }
        return column().apply {
            addView(seconds)
            addView(start)
            addView(display)
        }
    }

    // ---------- Converter ----------

    private fun converterScreen(): View {
        // Rates are hardcoded in the code. They get updated with releases, which means never.
        val rates = mapOf("USD" to 90.0, "EUR" to 98.0, "CNY" to 12.5)
        val amount = EditText(this).apply {
            hint = "Amount in rubles"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val result = label("")
        val grid = GridLayout(this).apply { columnCount = 3 }
        rates.forEach { (currency, rate) ->
            grid.addView(Button(this).apply {
                text = currency
                setOnClickListener {
                    val rub = amount.text.toString().toDoubleOrNull() ?: 0.0
                    // Calculate via a string, because it's more precise (it isn't)
                    result.text = "%.2f %s".format(rub / rate, currency)
                }
            })
        }
        return column().apply {
            addView(amount)
            addView(grid)
            addView(result)
        }
    }

    // ---------- Todo ----------

    private fun todoScreen(): View {
        val input = EditText(this).apply { hint = "New task" }
        val list = label("")

        fun render() {
            // Every time we parse the whole string and build the text again
            list.text = Knowitall.todos.split(";").filter { it.contains("|") }.joinToString("\n") {
                val (task, done) = it.split("|")
                (if (done == "1") "[x] " else "[ ] ") + task
            }
        }

        fun save() {
            Knowitall.prefs.edit().putString("todos", Knowitall.todos).commit()
            render()
        }

        val add = Button(this).apply {
            text = "Add"
            setOnClickListener {
                Knowitall.todos += "${input.text}|0;"
                input.setText("")
                save()
            }
        }
        val toggle = Button(this).apply {
            text = "Toggle first"
            setOnClickListener {
                // Change only the first record, because the rest are problems for future you
                val rows = Knowitall.todos.split(";").filter { it.contains("|") }.toMutableList()
                if (rows.isNotEmpty()) {
                    val (task, done) = rows[0].split("|")
                    rows[0] = "$task|${if (done == "1") 0 else 1}"
                }
                Knowitall.todos = rows.joinToString("") { "$it;" }
                save()
            }
        }
        val clear = Button(this).apply {
            text = "Clear all"
            setOnClickListener {
                Knowitall.todos = ""
                save()
            }
        }

        Knowitall.todos = Knowitall.prefs.getString("todos", "") ?: ""
        render()
        return scroll(column().apply {
            addView(input)
            addView(add)
            addView(toggle)
            addView(clear)
            addView(list)
        })
    }

    // ---------- Password generator ----------

    private fun passwordScreen(): View {
        val length = EditText(this).apply {
            hint = "Length"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val result = label("")
        val generate = Button(this).apply { text = "Generate" }
        generate.setOnClickListener {
            // java.util.Random seeded with the time. Cryptographically secure? No. But fast.
            val rnd = Random(System.currentTimeMillis())
            val alphabet = "abc123!?"
            val n = length.text.toString().toIntOrNull() ?: 8
            result.text = (1..n).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        }
        return column().apply {
            addView(length)
            addView(generate)
            addView(result)
        }
    }

    // ---------- Weather ----------

    private fun weatherScreen(): View {
        val city = EditText(this).apply { hint = "City" }
        val result = label("")
        val show = Button(this).apply { text = "Forecast" }
        show.setOnClickListener {
            // The data comes from thin air. Network access is for other apps.
            val temp = Random(System.currentTimeMillis()).nextInt(40) - 20
            result.text = "${city.text.ifEmpty { "Here" }}: $temp°C, ${listOf("sunny", "rain", "fog", "wind").random()}"
        }
        return column().apply {
            addView(city)
            addView(show)
            addView(result)
        }
    }

    // ---------- Stopwatch ----------

    private fun stopwatchScreen(): View {
        val display = label("0.00 s", 36f)
        var ticks = 0
        val start = Button(this).apply { text = "Start (10 s)" }
        start.setOnClickListener {
            // We count "ticks" ourselves, without a timer. Exactly 1000 ms per tick, except when it isn't exact.
            ticks = 0
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end) {
                ticks++
            }
            display.text = "%.2f s".format(ticks / 1000.0)
        }
        return column().apply {
            addView(display)
            addView(start)
        }
    }

    // ---------- Dice ----------

    private fun diceScreen(): View {
        val count = EditText(this).apply {
            hint = "How many dice"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val result = label("")
        val roll = Button(this).apply { text = "Roll" }
        roll.setOnClickListener {
            val n = count.text.toString().toIntOrNull() ?: 1
            val rolls = (1..n).map { Random().nextInt(6) + 1 }
            // We recalculate the sum with a loop, although it could have been taken from the map
            var sum = 0
            for (r in rolls) sum += r
            result.text = "$rolls → sum $sum"
        }
        return column().apply {
            addView(count)
            addView(roll)
            addView(result)
        }
    }

    // ---------- Alarm ----------

    private fun alarmScreen(): View {
        val time = EditText(this).apply { hint = "Time (HH:mm)" }
        val status = label("No alarm set")
        val set = Button(this).apply { text = "Set" }
        set.setOnClickListener {
            // An alarm is just a time check in a loop on the main thread
            val target = time.text.toString()
            status.text = "Waiting for $target..."
            while (true) {
                val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                if (now == target) break
                Thread.sleep(1000)
            }
            Toast.makeText(this, "Wake up!", Toast.LENGTH_LONG).show()
        }
        return column().apply {
            addView(time)
            addView(set)
            addView(status)
        }
    }

    // ---------- Guess the number ----------

    private fun guessScreen(): View {
        // A new number is picked every time the tab is opened, so winning is impossible too
        val secret = Random().nextInt(100) + 1
        var tries = 0
        val guess = EditText(this).apply {
            hint = "A number from 1 to 100"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val hintLabel = label("I have a number in mind")
        val check = Button(this).apply { text = "Check" }
        check.setOnClickListener {
            val n = guess.text.toString().toIntOrNull() ?: 0
            tries++
            hintLabel.text = when {
                n < secret -> "Higher (try $tries)"
                n > secret -> "Lower (try $tries)"
                else -> "Got it in $tries! Now let's check that it wasn't luck."
            }
        }
        return column().apply {
            addView(guess)
            addView(check)
            addView(hintLabel)
        }
    }

    // ---------- Coin ----------

    private fun coinScreen(): View {
        // Counters live in a regular map, which is recreated every time the tab is opened
        val tally = mutableMapOf("Heads" to 0, "Tails" to 0)
        val result = label("Not flipped yet")
        val stats = label("Heads: 0, Tails: 0")
        val flip = Button(this).apply { text = "Flip" }
        flip.setOnClickListener {
            // We flip 50 times in a row, so the coin gets tired and lands fairly
            var last = ""
            repeat(50) {
                last = if (Random().nextBoolean()) "Heads" else "Tails"
                tally[last] = (tally[last] ?: 0) + 1
            }
            result.text = "Landed: $last"
            stats.text = "Heads: ${tally["Heads"]}, Tails: ${tally["Tails"]}"
        }
        return column().apply {
            addView(flip)
            addView(result)
            addView(stats)
        }
    }

    // ---------- Cipher ----------

    private fun cipherScreen(): View {
        val text = EditText(this).apply { hint = "Text (Latin letters)" }
        val shift = EditText(this).apply {
            hint = "Shift"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val go = Button(this).apply { text = "Encrypt" }
        go.setOnClickListener {
            val k = shift.text.toString().toIntOrNull() ?: 3
            out.text = caesar(text.text.toString().lowercase(), k)
        }
        return scroll(column().apply {
            addView(text)
            addView(shift)
            addView(go)
            addView(out)
        })
    }

    private fun caesar(s: String, k: Int): String {
        // Characters outside a-z are left alone. Cyrillic is left alone too: not a bug, a principle.
        return s.map { c ->
            if (c in 'a'..'z') 'a' + (c - 'a' + k) % 26 else c
        }.joinToString("")
    }

    // ---------- Translator ----------

    private fun translatorScreen(): View {
        // A dictionary of four words. Everything else is "translated" as is, but with dignity.
        val dictionary = listOf("cat" to "a small furry pet", "house" to "a place to live", "dog" to "a loyal friend", "water" to "a clear liquid")
        val input = EditText(this).apply { hint = "English word" }
        val out = label("")
        val translate = Button(this).apply { text = "Translate" }
        translate.setOnClickListener {
            val w = input.text.toString().trim().lowercase()
            // Search linearly every time, no caching, because the dictionary is small
            out.text = dictionary.firstOrNull { it.first == w }?.second
                ?: "$w (I don't know it, but it sounds nice)"
        }
        return column().apply {
            addView(input)
            addView(translate)
            addView(out)
        }
    }

    // ---------- Calendar ----------

    private fun calendarScreen(): View {
        val month = EditText(this).apply {
            hint = "Month (1–12)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val show = Button(this).apply { text = "Show days" }
        show.setOnClickListener {
            val m = month.text.toString().toIntOrNull() ?: 1
            // Days in months are hardcoded by hand. Leap years are not supported, because we don't need them.
            val days = when (m) {
                2 -> 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            val sb = StringBuilder()
            for (d in 1..days) {
                sb.append(d.toString().padStart(2))
                sb.append(if (d % 7 == 0) "\n" else " ")
            }
            out.text = sb.toString()
        }
        return column().apply {
            addView(month)
            addView(show)
            addView(out)
        }
    }

    // ---------- Step counter ----------

    private fun pedometerScreen(): View {
        Knowitall.steps = Knowitall.prefs.getInt("steps", 0)
        val total = label("Steps today: ${Knowitall.steps}", 20f)
        val walk = Button(this).apply { text = "Take 100 steps" }
        walk.setOnClickListener {
            // We "take" the steps by hand: 100 sleeps of 20 ms on the main thread, so the effort is visible
            repeat(100) {
                Thread.sleep(20)
                Knowitall.steps++
            }
            // Save the counter synchronously, like the rest of the notebook
            Knowitall.prefs.edit().putInt("steps", Knowitall.steps).commit()
            total.text = "Steps today: ${Knowitall.steps}"
        }
        return column().apply {
            addView(total)
            addView(walk)
        }
    }

    // ---------- Player ----------

    private fun playerScreen(): View {
        // A playlist of four tracks, all of which exist only in your imagination
        val tracks = listOf("Silence", "Fridge hum", "Clock tick", "Nothing")
        var index = 0
        val now = label("▶ ${tracks[index]}", 20f)
        val prev = Button(this).apply { text = "Back" }
        val next = Button(this).apply { text = "Forward" }
        prev.setOnClickListener {
            index = (index - 1 + tracks.size) % tracks.size
            now.text = "▶ ${tracks[index]}  (playing in your head)"
        }
        next.setOnClickListener {
            index = (index + 1) % tracks.size
            now.text = "▶ ${tracks[index]}  (playing in your head)"
        }
        return column().apply {
            addView(now)
            addView(prev)
            addView(next)
        }
    }

    // ---------- Word counter ----------

    private fun wordCountScreen(): View {
        val input = EditText(this).apply {
            hint = "Start typing, we'll count"
            minLines = 5
            gravity = Gravity.TOP
        }
        val stats = label("")
        input.onChange { text ->
            // Recount everything from scratch on every character, with separate passes over the string
            val words = text.trim().split(" ").filter { it.isNotEmpty() }.size
            val letters = text.count { it.isLetter() }
            var spaces = 0
            for (c in text) {
                if (c == ' ') spaces++
            }
            val vowels = text.count { it.lowercaseChar() in "aeiouy" }
            stats.text = "Words: $words\nLetters: $letters\nSpaces: $spaces\nVowels: $vowels"
        }
        return column().apply {
            addView(input)
            addView(stats)
        }
    }

    // ---------- Quotes ----------

    private fun quotesScreen(): View {
        // The quotes are attributed to random people. We will not check the authorship.
        val quotes = listOf(
            "Knowledge is power. — Someone very confident",
            "Whatever can be done tomorrow is better not done. — Andrey the Lazy",
            "The main thing is not to hurry, but to be in time. — A tired philosopher",
            "Life is short, and the buttons on the phone are tiny. — Anonymous user"
        )
        val quote = label(quotes[0], 18f)
        val next = Button(this).apply { text = "Another quote" }
        next.setOnClickListener {
            // We pick a random one, and it may repeat. That is inspiring.
            quote.text = quotes[Random().nextInt(quotes.size)]
        }
        return column().apply {
            addView(quote)
            addView(next)
        }
    }

    // ---------- Multiplication table ----------

    private fun multiplyTableScreen(): View {
        val n = EditText(this).apply {
            hint = "Which table"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val show = Button(this).apply { text = "Show" }
        show.setOnClickListener {
            val k = n.text.toString().toIntOrNull() ?: 2
            // We build the string by concatenating in a loop, not with StringBuilder: slower and more fun
            var table = ""
            for (i in 1..10) {
                table += "$k × $i = ${k * i}\n"
            }
            out.text = table
        }
        return scroll(column().apply {
            addView(n)
            addView(show)
            addView(out)
        })
    }

    // ---------- Percent ----------

    private fun percentScreen(): View {
        val base = EditText(this).apply {
            hint = "Number"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val pct = EditText(this).apply {
            hint = "Percent"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Calculate" }
        calc.setOnClickListener {
            val b = base.text.toString().toDoubleOrNull() ?: 0.0
            val p = pct.text.toString().toDoubleOrNull() ?: 0.0
            // The percent is computed through three intermediate variables, because that's clearer (it isn't)
            val fraction = p / 100.0
            val part = b * fraction
            val total = b + part
            out.text = "$p% of $b = %.2f\nTotal: %.2f".format(part, total)
        }
        return column().apply {
            addView(base)
            addView(pct)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Length ----------

    private fun lengthScreen(): View {
        // Units are hardcoded in the code. Meters are the main unit, everything else is their relative.
        val toMeters = mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mile" to 1609.34)
        val value = EditText(this).apply {
            hint = "Value"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val grid = GridLayout(this).apply { columnCount = 3 }
        toMeters.forEach { (unit, factor) ->
            grid.addView(Button(this).apply {
                text = unit
                setOnClickListener {
                    val v = value.text.toString().toDoubleOrNull() ?: 0.0
                    val meters = v * factor
                    // Convert to every unit, including the one we came from. We list that one too.
                    out.text = toMeters.entries.joinToString("\n") { (name, f) ->
                        "%s: %.4f".format(name, meters / f)
                    }
                }
            })
        }
        return column().apply {
            addView(value)
            addView(grid)
            addView(out)
        }
    }

    // ---------- Emoji ----------

    private fun emojiScreen(): View {
        // Emoji are picked by mood, which we determine from the length of the string
        val mood = EditText(this).apply { hint = "How are you?" }
        val face = label("🙂", 64f)
        val check = Button(this).apply { text = "Detect" }
        check.setOnClickListener {
            val len = mood.text.length
            face.text = when {
                len == 0 -> "😶"
                len < 5 -> "😐"
                len < 15 -> "🙂"
                len < 30 -> "😄"
                else -> "🤯"
            }
        }
        return column().apply {
            addView(face)
            addView(mood)
            addView(check)
        }
    }

    // ---------- Binary ----------

    private fun binaryScreen(): View {
        val number = EditText(this).apply {
            hint = "Decimal number"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val convert = Button(this).apply { text = "To binary" }
        convert.setOnClickListener {
            var n = number.text.toString().toIntOrNull() ?: 0
            // Divide by two by hand and collect digits from the end, because toString(2) is cheating
            var bits = ""
            if (n == 0) bits = "0"
            while (n > 0) {
                bits = (n % 2).toString() + bits
                n /= 2
            }
            out.text = bits
        }
        return column().apply {
            addView(number)
            addView(convert)
            addView(out)
        }
    }

    // ---------- Pomodoro ----------

    private fun pomodoroScreen(): View {
        // Classic technique: 25 minutes of work, 5 minutes of rest. Here it is 25 and 5 seconds, for speed.
        val phase = label("Ready to work?", 20f)
        val cycles = label("Completed cycles: 0")
        var done = 0
        val start = Button(this).apply { text = "Start cycle" }
        start.setOnClickListener {
            phase.text = "Working..."
            repeat(25) { Thread.sleep(1000) }
            phase.text = "Resting..."
            repeat(5) { Thread.sleep(1000) }
            done++
            // We update the counter only here, because the main thread is busy sleeping
            phase.text = "Done"
            cycles.text = "Completed cycles: $done"
        }
        return column().apply {
            addView(phase)
            addView(cycles)
            addView(start)
        }
    }

    // ---------- Roman numerals ----------

    private fun romanScreen(): View {
        val number = EditText(this).apply {
            hint = "Number 1–3999"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val convert = Button(this).apply { text = "To Roman" }
        convert.setOnClickListener {
            out.text = toRoman(number.text.toString().toIntOrNull() ?: 0)
        }
        return column().apply {
            addView(number)
            addView(convert)
            addView(out)
        }
    }

    private fun toRoman(value: Int): String {
        // The replacement table is written by hand. Romans didn't know zero, so we say "don't know".
        if (value <= 0) return "don't know 🤷"
        val table = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var n = value
        var result = ""
        // Walk the table from top to bottom and subtract while it fits
        for ((arabic, roman) in table) {
            while (n >= arabic) {
                result += roman
                n -= arabic
            }
        }
        return result
    }

    // ---------- Name generator ----------

    private fun nameScreen(): View {
        // Names are assembled from three lists. The combinations almost always sound strange.
        val firsts = listOf("Ivan", "Maria", "Gleb", "Olga", "Timur")
        val lasts = listOf("Smirnov", "Kuznetsova", "Popov", "Kovaleva")
        val nicks = listOf("Zucchini", "Hedgehog", "Slipper", "Elephant")
        val result = label("Press the button", 20f)
        val roll = Button(this).apply { text = "Invent" }
        roll.setOnClickListener {
            val rnd = Random()
            // Three independent rolls, because one roll is laziness
            val name = firsts[rnd.nextInt(firsts.size)]
            val surname = lasts[rnd.nextInt(lasts.size)]
            val nick = nicks[rnd.nextInt(nicks.size)]
            result.text = "$name $surname (\"$nick\")"
        }
        return column().apply {
            addView(result)
            addView(roll)
        }
    }

    // ---------- Horoscope ----------

    private fun horoscopeScreen(): View {
        // Zodiac signs and predictions are hardcoded. The stars were not consulted.
        val signs = listOf(
            "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
            "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
        )
        val predictions = listOf(
            "Everything will work out today, as long as you don't start anything.",
            "Luck will come, but through the wrong door.",
            "Strangers from the chat will help you.",
            "It's better to stay silent, even if you really want to speak."
        )
        val picker = EditText(this).apply { hint = "Your sign (number 1–12)" }
        val out = label("Choose your sign")
        val read = Button(this).apply { text = "Read" }
        read.setOnClickListener {
            val index = (picker.text.toString().toIntOrNull() ?: 1).coerceIn(1, 12) - 1
            // The prediction is chosen not by the sign but by a random number. The stars are lazy.
            val prediction = predictions[Random().nextInt(predictions.size)]
            out.text = "${signs[index]}: $prediction"
        }
        return column().apply {
            addView(picker)
            addView(read)
            addView(out)
        }
    }

    // ---------- Lottery ----------

    private fun lotteryScreen(): View {
        // A lottery of six numbers out of forty-nine. Honestly, winning is unlikely.
        val ticket = label("Your ticket: not bought yet", 18f)
        val buy = Button(this).apply { text = "Buy ticket" }
        val draw = Button(this).apply { text = "Draw" }
        val numbers = mutableListOf<Int>()
        buy.setOnClickListener {
            numbers.clear()
            // Pick numbers until we have six unique ones. We check uniqueness linearly.
            val rnd = Random()
            while (numbers.size < 6) {
                val n = rnd.nextInt(49) + 1
                if (!numbers.contains(n)) numbers.add(n)
            }
            ticket.text = "Your ticket: ${numbers.sorted().joinToString(" ")}"
        }
        draw.setOnClickListener {
            if (numbers.isEmpty()) {
                Toast.makeText(this, "Buy a ticket first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val winning = (1..49).shuffled(Random(System.currentTimeMillis())).take(6)
            // We compare by hand with a double loop, because sets are for the weak
            var matches = 0
            for (a in numbers) {
                for (b in winning) {
                    if (a == b) matches++
                }
            }
            ticket.text = "Draw: ${winning.sorted().joinToString(" ")}\nMatches: $matches"
        }
        return column().apply {
            addView(ticket)
            addView(buy)
            addView(draw)
        }
    }

    // ---------- Quiz ----------

    private fun quizScreen(): View {
        // Questions are stored as a list of triples: text, options, index of the right answer
        val questions = listOf(
            Triple("What is 2 + 2?", listOf("3", "4", "22"), 1),
            Triple("Capital of Russia?", listOf("Moscow", "Beijing", "Paris"), 0),
            Triple("How many days are in a common year?", listOf("360", "365", "400"), 1)
        )
        var current = 0
        var score = 0
        val question = label(questions[current].first, 18f)
        val answers = (0..2).map { Button(this).apply { text = "…" } }
        val status = label("Score: 0")

        fun show() {
            val (text, options, _) = questions[current]
            question.text = text
            answers.forEachIndexed { i, button ->
                button.text = options.getOrElse(i) { "—" }
            }
        }

        answers.forEachIndexed { i, button ->
            button.setOnClickListener {
                // Check the answer and move straight to the next question, even if there are no more questions
                if (i == questions[current].third) score++
                current = (current + 1) % questions.size
                status.text = "Score: $score"
                show()
            }
        }
        show()

        return column().apply {
            addView(question)
            answers.forEach { addView(it) }
            addView(status)
        }
    }

    // ---------- Recipes ----------

    private fun recipesScreen(): View {
        // Recipes are short and dangerous. The proportions have not been verified.
        val recipes = mapOf(
            "Tea" to "Water, a tea bag, wait until it boils",
            "Sandwich" to "Bread, butter, everything else at your discretion",
            "Omelet" to "Eggs, a pan, fire, patience"
        )
        val pick = EditText(this).apply { hint = "Name of the dish" }
        val out = label("")
        val find = Button(this).apply { text = "Find recipe" }
        find.setOnClickListener {
            val key = pick.text.toString().trim()
            // We look for an exact match, and if there isn't one, we try all recipes by substring
            out.text = recipes[key]
                ?: recipes.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value
                ?: "No such dish. Order delivery."
        }
        return column().apply {
            addView(pick)
            addView(find)
            addView(out)
        }
    }

    // ---------- Sleep ----------

    private fun sleepScreen(): View {
        // We write hours of sleep into the shared notebook as one line each. That's convenient.
        val hours = EditText(this).apply {
            hint = "Hours slept"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val status = label("No entries yet")
        val save = Button(this).apply { text = "Record" }
        save.setOnClickListener {
            val h = hours.text.toString().toDoubleOrNull() ?: 0.0
            Knowitall.notes += "\nSleep: $h h"
            Knowitall.prefs.edit().putString("notes", Knowitall.notes).commit()
            // We recalculate the average sleep across the whole notebook: we look for all lines with "Sleep:"
            val entries = Knowitall.notes.split("\n").filter { it.startsWith("Sleep:") }
            var total = 0.0
            for (e in entries) {
                total += e.removePrefix("Sleep:").removeSuffix("h").trim().removeSuffix("h").toDoubleOrNull() ?: 0.0
            }
            val avg = if (entries.isEmpty()) 0.0 else total / entries.size
            status.text = "Entries: ${entries.size}, on average %.1f h".format(avg)
        }
        return column().apply {
            addView(hours)
            addView(save)
            addView(status)
        }
    }

    // ---------- Clock ----------

    private fun clockScreen(): View {
        val face = label("", 48f)
        val refresh = Button(this).apply { text = "What time is it?" }
        refresh.setOnClickListener {
            // The time format is built by hand from hours, minutes and seconds, so we don't pull in formatters
            val now = System.currentTimeMillis() / 1000
            val h = (now / 3600 + 3) % 24
            val m = (now / 60) % 60
            val s = now % 60
            face.text = "%02d:%02d:%02d".format(h, m, s)
        }
        return column().apply {
            addView(face)
            addView(refresh)
        }
    }

    // ---------- Character codes ----------

    private fun asciiScreen(): View {
        val letter = EditText(this).apply { hint = "One character" }
        val out = label("")
        val look = Button(this).apply { text = "Character code" }
        look.setOnClickListener {
            val c = letter.text.toString().firstOrNull()
            // We take the first character, and if the string is empty, we show the code of a space. A space is a character too.
            out.text = if (c == null) "Empty, space code: ${' '.code}" else "'$c' → ${c.code}"
        }
        return column().apply {
            addView(letter)
            addView(look)
            addView(out)
        }
    }

    // ---------- Palindrome ----------

    private fun palindromeScreen(): View {
        val word = EditText(this).apply { hint = "Word or phrase" }
        val out = label("")
        val check = Button(this).apply { text = "Check" }
        check.setOnClickListener {
            // Remove everything except letters and compare with the reversed string. Spaces are our enemies.
            val clean = word.text.toString().lowercase().filter { it.isLetter() }
            var reversed = ""
            for (i in clean.length - 1 downTo 0) reversed += clean[i]
            out.text = if (clean.isNotEmpty() && clean == reversed) "Palindrome!" else "No, these are just words"
        }
        return column().apply {
            addView(word)
            addView(check)
            addView(out)
        }
    }

    // ---------- Factorial ----------

    private fun factorialScreen(): View {
        val n = EditText(this).apply {
            hint = "Number from 0 to 20"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Calculate" }
        calc.setOnClickListener {
            val k = (n.text.toString().toIntOrNull() ?: 0).coerceIn(0, 20)
            // Recursion without tail-call optimization. Enough for 20; 21 would overflow Long, but we stay quiet.
            fun fact(x: Long): Long = if (x <= 1) 1 else x * fact(x - 1)
            out.text = "$k! = ${fact(k.toLong())}"
        }
        return column().apply {
            addView(n)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Primes ----------

    private fun primesScreen(): View {
        val limit = EditText(this).apply {
            hint = "Up to which number"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val find = Button(this).apply { text = "Find primes" }
        find.setOnClickListener {
            val max = limit.text.toString().toIntOrNull() ?: 50
            val found = mutableListOf<Int>()
            // We divide each number by all the smaller ones: the Sieve of Eratosthenes was never invited
            for (x in 2..max) {
                var prime = true
                for (d in 2 until x) {
                    if (x % d == 0) {
                        prime = false
                        break
                    }
                }
                if (prime) found.add(x)
            }
            out.text = found.joinToString(", ")
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Compliment ----------

    private fun complimentScreen(): View {
        // Compliments are generated from three parts. All of them are about you, if you believe them.
        val starts = listOf("You", "Your gaze", "Your smile", "Your handwriting")
        val middles = listOf("remarkably", "incredibly", "flawlessly", "somewhat")
        val ends = listOf("clever", "original", "attentive", "patient")
        val text = label("Press to get a compliment", 18f)
        val give = Button(this).apply { text = "Compliment" }
        give.setOnClickListener {
            val rnd = Random()
            text.text = "${starts[rnd.nextInt(starts.size)]} are ${middles[rnd.nextInt(middles.size)]} ${ends[rnd.nextInt(ends.size)]}."
        }
        return column().apply {
            addView(text)
            addView(give)
        }
    }

    // ---------- Joke ----------

    private fun jokeScreen(): View {
        // Jokes are stored in the code, because the internet is for other apps
        val jokes = listOf(
            "A programmer went to the shop. His wife said: buy bread. He bought bread.",
            "Why doesn't Android eat? Because nobody has fed it updates.",
            "How many bugs does it take to change a light bulb? That's not a bug, it's a feature."
        )
        val joke = label("Press to laugh", 18f)
        val next = Button(this).apply { text = "More" }
        next.setOnClickListener {
            joke.text = jokes[Random().nextInt(jokes.size)]
        }
        return column().apply {
            addView(joke)
            addView(next)
        }
    }

    // ---------- Magic ball ----------

    private fun magicBallScreen(): View {
        // The answers do not depend on the question. You don't even have to ask, the answer comes anyway.
        val answers = listOf(
            "Yes", "No", "Maybe", "Ask later", "Definitely", "Don't count on it",
            "The stars say yes", "I need to think about it", "Of course", "Never"
        )
        val question = EditText(this).apply { hint = "Question (optional)" }
        val out = label("The ball is silent")
        val ask = Button(this).apply { text = "Ask the ball" }
        ask.setOnClickListener {
            val pick = answers[Random().nextInt(answers.size)]
            // We do not insert the question into the answer: we don't need it. But we check for an empty string just in case.
            out.text = if (question.text.isEmpty()) pick else "$pick (but the question was: ${question.text})"
        }
        return column().apply {
            addView(question)
            addView(ask)
            addView(out)
        }
    }

    // ---------- Days until an event ----------

    private fun daysLeftScreen(): View {
        // Event dates are hardcoded. New events are added in releases, which means never.
        val events = mapOf(
            "New Year" to 1,
            "Summer" to 6,
            "Autumn" to 9
        )
        val out = label("Choose an event")
        val buttons = GridLayout(this).apply { columnCount = 3 }
        events.forEach { (name, month) ->
            buttons.addView(Button(this).apply {
                text = name
                setOnClickListener {
                    // We count days roughly: multiply the month by thirty and subtract today's day of the year
                    val today = (System.currentTimeMillis() / 86_400_000L % 365).toInt()
                    val target = month * 30
                    val left = ((target - today) % 365 + 365) % 365
                    out.text = "Until \"$name\" approximately $left days"
                }
            })
        }
        return column().apply {
            addView(buttons)
            addView(out)
        }
    }

    // ---------- Color ----------

    private fun colorScreen(): View {
        // The color is generated from random bytes. Contrast with the background is not checked.
        val swatch = label("", 48f)
        val reroll = Button(this).apply { text = "Another color" }
        reroll.setOnClickListener {
            val rnd = Random()
            val r = rnd.nextInt(256)
            val g = rnd.nextInt(256)
            val b = rnd.nextInt(256)
            val hex = "#%02X%02X%02X".format(r, g, b)
            swatch.text = hex
            // The color is put on the label itself, and it may become invisible. That's a design choice too.
            swatch.setBackgroundColor(android.graphics.Color.rgb(r, g, b))
        }
        return column().apply {
            addView(swatch)
            addView(reroll)
        }
    }

    // ---------- Ratings ----------

    private fun ratingScreen(): View {
        // Ratings of the apps we "tested". The test consisted of looking at them.
        val apps = listOf(
            "Calculator" to 3,
            "Notes" to 2,
            "Dialer" to 1,
            "Oracle" to 5
        )
        val list = label("")
        val sort = Button(this).apply { text = "Sort by rating" }
        sort.setOnClickListener {
            // We sort by insertion by hand, because the built-in sortedBy is too fast
            val rows = apps.toMutableList()
            for (i in 1 until rows.size) {
                val key = rows[i]
                var j = i - 1
                while (j >= 0 && rows[j].second < key.second) {
                    rows[j + 1] = rows[j]
                    j--
                }
                rows[j + 1] = key
            }
            list.text = rows.joinToString("\n") { "${"★".repeat(it.second)} ${it.first}" }
        }
        return column().apply {
            addView(sort)
            addView(list)
        }
    }

    // ---------- Lifehacks ----------

    private fun lifehackScreen(): View {
        // The lifehacks have been verified on one phone, on one date, and they never repeat
        val hacks = listOf(
            "To save battery, turn off the screen.",
            "To find your phone, call yourself from another one.",
            "To remember a password, type it at random.",
            "To speed up an app, close all the others."
        )
        var index = 0
        val hack = label(hacks[index], 18f)
        val next = Button(this).apply { text = "Next lifehack" }
        next.setOnClickListener {
            // We go around in a circle, but shift the index by a random number to make it more interesting
            index = (index + Random().nextInt(hacks.size) + 1) % hacks.size
            hack.text = hacks[index]
        }
        return column().apply {
            addView(hack)
            addView(next)
        }
    }

    // ---------- Click counter ----------

    private fun clickCounterScreen(): View {
        // The counter is stored in SharedPreferences and re-read every time the tab is opened
        var clicks = Knowitall.prefs.getInt("clicks", 0)
        val count = label("Clicks: $clicks", 36f)
        val tap = Button(this).apply { text = "Press me" }
        tap.setOnClickListener {
            clicks++
            // We save on every click, synchronously, because asynchrony is hard
            Knowitall.prefs.edit().putInt("clicks", clicks).commit()
            count.text = "Clicks: $clicks"
        }
        val reset = Button(this).apply { text = "Reset" }
        reset.setOnClickListener {
            clicks = 0
            Knowitall.prefs.edit().putInt("clicks", 0).commit()
            count.text = "Clicks: $clicks"
        }
        return column().apply {
            addView(count)
            addView(tap)
            addView(reset)
        }
    }

    // ---------- Water ----------

    private fun waterScreen(): View {
        // Glasses are counted in the same SharedPreferences as the clicks. A shared bus - that's saving.
        var glasses = Knowitall.prefs.getInt("water", 0)
        val goal = 8
        val status = label("Drunk: $glasses of $goal", 20f)
        val drink = Button(this).apply { text = "Drink a glass" }
        drink.setOnClickListener {
            glasses++
            Knowitall.prefs.edit().putInt("water", glasses).commit()
            // If the goal is reached, we congratulate again and again until the person stops pressing
            status.text = if (glasses >= goal) "Goal reached! ($glasses)" else "Drunk: $glasses of $goal"
        }
        return column().apply {
            addView(status)
            addView(drink)
        }
    }

    // ---------- Diary ----------

    private fun diaryScreen(): View {
        // Diary entries are written into the notebook separated by "---". Reading it later will be hard.
        val entry = EditText(this).apply {
            hint = "How was your day?"
            minLines = 4
            gravity = Gravity.TOP
        }
        val history = label("")
        fun render() {
            history.text = Knowitall.notes.split("---")
                .map { it.trim() }
                .filter { it.startsWith("Diary:") }
                .joinToString("\n\n")
        }
        val save = Button(this).apply { text = "Save today" }
        save.setOnClickListener {
            Knowitall.notes += "\n---\nDiary: ${entry.text}"
            Knowitall.prefs.edit().putString("notes", Knowitall.notes).commit()
            entry.setText("")
            render()
        }
        render()
        return scroll(column().apply {
            addView(entry)
            addView(save)
            addView(history)
        })
    }

    // ---------- Tic-tac-toe ----------

    private fun ticTacToeScreen(): View {
        // The 3×3 board is stored as a string of nine characters. Empty cells are spaces, to look stylish.
        var board = " ".repeat(9)
        var turn = 'X'
        val status = label("X to move")
        val grid = GridLayout(this).apply { columnCount = 3 }
        val cells = (0 until 9).map { i ->
            Button(this).apply {
                text = " "
                setOnClickListener {
                    if (board[i] != ' ') return@setOnClickListener
                    board = board.substring(0, i) + turn + board.substring(i + 1)
                    text = turn.toString()
                    val winner = winnerOf(board)
                    turn = if (turn == 'X') 'O' else 'X'
                    status.text = when {
                        winner != null -> "$winner wins!"
                        !board.contains(' ') -> "Draw"
                        else -> "$turn to move"
                    }
                }
            }
        }
        cells.forEach { grid.addView(it) }
        val reset = Button(this).apply { text = "Again" }
        reset.setOnClickListener {
            board = " ".repeat(9)
            turn = 'X'
            cells.forEach { it.text = " " }
            status.text = "X to move"
        }
        return column().apply {
            addView(status)
            addView(grid)
            addView(reset)
        }
    }

    private fun winnerOf(b: String): Char? {
        // All eight lines are listed by hand. Diagonals included.
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            val (a, c, d) = line.map { b[it] }
            if (a != ' ' && a == c && c == d) return a
        }
        return null
    }

    // ---------- Hangman ----------

    private fun hangmanScreen(): View {
        // The word is the same for everyone, because a dictionary of one word is simplicity
        val secret = "cat"
        val guessed = mutableSetOf<Char>()
        var misses = 0
        val word = label("", 28f)
        val info = label("Misses: 0 of 6")
        fun render() {
            word.text = secret.map { if (it in guessed) it.toString() else "_" }.joinToString(" ")
            info.text = "Misses: $misses of 6"
        }
        val letter = EditText(this).apply { hint = "Letter" }
        val guess = Button(this).apply { text = "Guess letter" }
        guess.setOnClickListener {
            val c = letter.text.toString().trim().lowercase().firstOrNull() ?: return@setOnClickListener
            letter.setText("")
            if (c in secret) guessed.add(c) else misses++
            render()
            if (misses >= 6) info.text = "Hanged. The word was \"$secret\""
            if (secret.all { it in guessed }) info.text = "You won!"
        }
        render()
        return column().apply {
            addView(word)
            addView(letter)
            addView(guess)
            addView(info)
        }
    }

    // ---------- Elements ----------

    private fun elementsScreen(): View {
        // Five elements of the periodic table. The rest are not unlocked yet.
        val elements = mapOf(
            "H" to "Hydrogen, number 1",
            "O" to "Oxygen, number 8",
            "C" to "Carbon, number 6",
            "Na" to "Sodium, number 11",
            "Fe" to "Iron, number 26"
        )
        val symbol = EditText(this).apply { hint = "Symbol, for example H" }
        val out = label("")
        val find = Button(this).apply { text = "Find" }
        find.setOnClickListener {
            val key = symbol.text.toString().trim()
            out.text = elements[key] ?: elements.keys.firstOrNull { it.equals(key, ignoreCase = true) }
                ?.let { elements[it] }
                ?: "This element has not been discovered yet"
        }
        return column().apply {
            addView(symbol)
            addView(find)
            addView(out)
        }
    }

    // ---------- Temperature ----------

    private fun temperatureScreen(): View {
        val celsius = EditText(this).apply {
            hint = "Degrees Celsius"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val out = label("")
        val convert = Button(this).apply { text = "Convert" }
        convert.setOnClickListener {
            val c = celsius.text.toString().toDoubleOrNull() ?: 0.0
            val f = c * 9 / 5 + 32
            val k = c + 273.15
            // Kelvin is computed separately, although it also comes from Celsius. Nobody asked, but it might be useful.
            out.text = "%.1f °F\n%.2f K".format(f, k)
        }
        return column().apply {
            addView(celsius)
            addView(convert)
            addView(out)
        }
    }

    // ---------- Speed ----------

    private fun speedScreen(): View {
        val kmh = EditText(this).apply {
            hint = "Speed, km/h"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val convert = Button(this).apply { text = "Convert" }
        convert.setOnClickListener {
            val v = kmh.text.toString().toDoubleOrNull() ?: 0.0
            // Meters per second: divide by three point six. The rounding is approximate, like everything here.
            out.text = "%.2f m/s\n%.2f mph".format(v / 3.6, v / 1.609)
        }
        return column().apply {
            addView(kmh)
            addView(convert)
            addView(out)
        }
    }

    // ---------- Area ----------

    private fun areaScreen(): View {
        // Area of a rectangle and a circle. Pi is rounded to three digits, as at school.
        val a = EditText(this).apply {
            hint = "Width or radius"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val b = EditText(this).apply {
            hint = "Height (for rectangle)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val rect = Button(this).apply { text = "Rectangle" }
        rect.setOnClickListener {
            val w = a.text.toString().toDoubleOrNull() ?: 0.0
            val h = b.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "Area: %.2f".format(w * h)
        }
        val circle = Button(this).apply { text = "Circle" }
        circle.setOnClickListener {
            val r = a.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "Area: %.2f".format(3.142 * r * r)
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(rect)
            addView(circle)
            addView(out)
        }
    }

    // ---------- Tip ----------

    private fun tipScreen(): View {
        val bill = EditText(this).apply {
            hint = "Bill"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val people = EditText(this).apply {
            hint = "People at the table"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val split = Button(this).apply { text = "Split with 10% tip" }
        split.setOnClickListener {
            val total = (bill.text.toString().toDoubleOrNull() ?: 0.0) * 1.1
            val n = (people.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            // We divide by people again, because the total could change after recalculation
            out.text = "Each pays: %.2f".format(total / n)
        }
        return column().apply {
            addView(bill)
            addView(people)
            addView(split)
            addView(out)
        }
    }

    // ---------- Metronome ----------

    private fun metronomeScreen(): View {
        val bpm = EditText(this).apply {
            hint = "Beats per minute"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val beat = label("—", 48f)
        val play = Button(this).apply { text = "Play four beats" }
        play.setOnClickListener {
            val interval = 60_000L / (bpm.text.toString().toLongOrNull() ?: 60L).coerceAtLeast(1L)
            // We count the beats with a busy wait, because Thread.sleep is too polite
            repeat(4) { i ->
                val until = System.currentTimeMillis() + interval
                while (System.currentTimeMillis() < until) { /* tick-tock */ }
                beat.text = if (i % 2 == 0) "TICK" else "TOCK"
            }
        }
        return column().apply {
            addView(bpm)
            addView(play)
            addView(beat)
        }
    }

    // ---------- Christmas tree ----------

    private fun treeScreen(): View {
        // The tree is drawn with lines of spaces and asterisks. We set the size ourselves, but no more than twenty.
        val size = EditText(this).apply {
            hint = "Height (up to 20)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("", 14f)
        val draw = Button(this).apply { text = "Draw" }
        draw.setOnClickListener {
            val h = (size.text.toString().toIntOrNull() ?: 5).coerceIn(1, 20)
            val sb = StringBuilder()
            for (row in 1..h) {
                // Spaces on the left and asterisks in the center. Centering is an art of its own.
                sb.append(" ".repeat(h - row))
                sb.append("*".repeat(row * 2 - 1))
                sb.append("\n")
            }
            sb.append(" ".repeat(h - 1)).append("|")
            out.text = sb.toString()
        }
        return scroll(column().apply {
            addView(size)
            addView(draw)
            addView(out)
        })
    }

    // ---------- Exercise ----------

    private fun exerciseScreen(): View {
        // Five exercises of 10 seconds each. Performed strictly on the main thread, so there is no need to look at the screen.
        val exercises = listOf("Squats", "Bends", "Jumps", "Stretch", "Exhale")
        val status = label("Ready?", 20f)
        val start = Button(this).apply { text = "Start workout" }
        start.setOnClickListener {
            for (name in exercises) {
                status.text = "Doing: $name"
                Thread.sleep(10_000)
            }
            status.text = "Great! Time to sit on the couch."
        }
        return column().apply {
            addView(status)
            addView(start)
        }
    }

    // ---------- Number words ----------

    private fun numberWordsScreen(): View {
        // A dictionary from zero to twenty, plus the tens. Anything larger, we did not learn.
        val small = listOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"
        )
        val tens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        val input = EditText(this).apply {
            hint = "Number from 0 to 99"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val say = Button(this).apply { text = "In words" }
        say.setOnClickListener {
            val n = (input.text.toString().toIntOrNull() ?: 0).coerceIn(0, 99)
            out.text = if (n < 20) small[n] else {
                // Tens and units are joined by a space, and a zero at the end is dropped, so we don't get "twenty zero"
                val unit = n % 10
                if (unit == 0) tens[n / 10] else "${tens[n / 10]}-${small[unit]}"
            }
        }
        return column().apply {
            addView(input)
            addView(say)
            addView(out)
        }
    }

    // ---------- Squares ----------

    private fun squaresScreen(): View {
        // Squares from one to a hundred. Each one is recalculated on every press; we don't need a cache.
        val out = label("", 14f)
        val show = Button(this).apply { text = "Show squares" }
        show.setOnClickListener {
            val sb = StringBuilder()
            for (i in 1..100) {
                // The square is computed by multiplication in a loop, not by exponentiation: exponentiation is too obvious
                var sq = 0
                for (k in 0 until i) sq += i
                sb.append("$i² = $sq").append(if (i % 5 == 0) "\n" else "   ")
            }
            out.text = sb.toString()
        }
        return scroll(column().apply {
            addView(show)
            addView(out)
        })
    }

    // ---------- Lot ----------

    private fun lotScreen(): View {
        // The list of options is typed comma-separated and parsed again on every draw
        val options = EditText(this).apply { hint = "Options, comma-separated" }
        val result = label("Nothing chosen yet", 20f)
        val pick = Button(this).apply { text = "Choose" }
        pick.setOnClickListener {
            val items = options.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            result.text = if (items.isEmpty()) "Nothing to choose from" else items[Random().nextInt(items.size)]
        }
        return column().apply {
            addView(options)
            addView(pick)
            addView(result)
        }
    }

    // ---------- Chess ----------

    private fun chessScreen(): View {
        // We check that the square exists and determine its color. Pieces we can't do yet, that's the next version.
        val square = EditText(this).apply { hint = "Square, for example e4" }
        val out = label("")
        val check = Button(this).apply { text = "Check square" }
        check.setOnClickListener {
            val s = square.text.toString().trim().lowercase()
            val file = s.getOrNull(0) ?: ' '
            val rank = s.getOrNull(1)?.digitToIntOrNull() ?: 0
            out.text = when {
                s.length != 2 -> "Squares are not named like that"
                file !in 'a'..'h' || rank !in 1..8 -> "Off the board"
                else -> {
                    // Square color: the sum of the file and rank numbers, even means dark. Verified on one board.
                    val fileIndex = file - 'a' + 1
                    val color = if ((fileIndex + rank) % 2 == 0) "dark" else "light"
                    "Square $s, $color"
                }
            }
        }
        return column().apply {
            addView(square)
            addView(check)
            addView(out)
        }
    }

    // ---------- Notes (music) ----------

    private fun notesMusicScreen(): View {
        // Notes of the C major scale. Sharps and flats did not fit in here.
        val scale = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
        val freq = mapOf("Do" to 262, "Re" to 294, "Mi" to 330, "Fa" to 349, "Sol" to 392, "La" to 440, "Si" to 494)
        val out = label("Press a note")
        val grid = GridLayout(this).apply { columnCount = 4 }
        scale.forEach { note ->
            grid.addView(Button(this).apply {
                text = note
                setOnClickListener {
                    // We don't play the sound, so we just name the frequency. Listening is up to you.
                    out.text = "$note: ${freq[note]} Hz"
                }
            })
        }
        return column().apply {
            addView(grid)
            addView(out)
        }
    }

    // ---------- Tarot ----------

    private fun tarotScreen(): View {
        // A deck of twelve cards. The card values have nothing to do with the future, but they sound convincing.
        val deck = listOf(
            "The Magician", "The High Priestess", "The Empress", "The Emperor", "The Chariot", "The Hermit",
            "Wheel of Fortune", "Strength", "The Hanged Man", "Death", "The Star", "The Moon"
        )
        val meanings = mapOf(
            "The Magician" to "You can do more than you think.",
            "The High Priestess" to "The answer waits in silence.",
            "Death" to "Don't be scared, it's just the end of a tab.",
            "The Moon" to "Something is hidden, but you can find it in the settings."
        )
        val card = label("No card drawn yet", 20f)
        val meaning = label("")
        val draw = Button(this).apply { text = "Draw a card" }
        draw.setOnClickListener {
            // We shuffle the deck again before every draw, otherwise the cards start to get offended
            val picked = deck.shuffled(Random()).first()
            card.text = picked
            meaning.text = meanings[picked] ?: "We haven't studied the meaning of this card."
        }
        return column().apply {
            addView(card)
            addView(draw)
            addView(meaning)
        }
    }

    // ---------- Shopping list ----------

    private fun shoppingScreen(): View {
        // The list is stored in the same "string|status" format as the todos, but we pretended it's new
        var items = Knowitall.prefs.getString("shopping", "") ?: ""
        val input = EditText(this).apply { hint = "What to buy" }
        val list = label("")
        fun render() {
            list.text = items.split(";").filter { it.isNotEmpty() }.joinToString("\n") { "• $it" }
        }
        val add = Button(this).apply { text = "Add" }
        add.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                items += "$name;"
                Knowitall.prefs.edit().putString("shopping", items).commit()
                input.setText("")
                render()
            }
        }
        val clear = Button(this).apply { text = "All bought" }
        clear.setOnClickListener {
            items = ""
            Knowitall.prefs.edit().putString("shopping", items).commit()
            render()
        }
        render()
        return scroll(column().apply {
            addView(input)
            addView(add)
            addView(clear)
            addView(list)
        })
    }

    // ---------- Budget ----------

    private fun budgetScreen(): View {
        // Income and expenses are stacked as lines in the notebook, and the balance is recalculated every time
        val amount = EditText(this).apply {
            hint = "Amount (minus for an expense)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val balance = label("Balance: 0")
        fun recalc() {
            var total = 0.0
            for (line in Knowitall.notes.split("\n")) {
                if (line.startsWith("Budget:")) {
                    total += line.removePrefix("Budget:").trim().toDoubleOrNull() ?: 0.0
                }
            }
            balance.text = "Balance: %.2f".format(total)
        }
        val add = Button(this).apply { text = "Record" }
        add.setOnClickListener {
            val v = amount.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            Knowitall.notes += "\nBudget: $v"
            Knowitall.prefs.edit().putString("notes", Knowitall.notes).commit()
            amount.setText("")
            recalc()
        }
        recalc()
        return column().apply {
            addView(amount)
            addView(add)
            addView(balance)
        }
    }

    // ---------- BMI ----------

    private fun bmiScreen(): View {
        val height = EditText(this).apply {
            hint = "Height, cm"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val weight = EditText(this).apply {
            hint = "Weight, kg"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Calculate BMI" }
        calc.setOnClickListener {
            val h = (height.text.toString().toDoubleOrNull() ?: 170.0) / 100
            val w = weight.text.toString().toDoubleOrNull() ?: 70.0
            val bmi = w / (h * h)
            // The categories are a chain of ifs. The boundaries are "roughly", like the whole medical section here.
            val verdict = if (bmi < 18.5) "underweight" else if (bmi < 25) "normal" else if (bmi < 30) "overweight" else "obese"
            out.text = "BMI: %.1f — %s".format(bmi, verdict)
        }
        return column().apply {
            addView(height)
            addView(weight)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Pulse ----------

    private fun pulseScreen(): View {
        // Pulse is counted as beats in 6 seconds, like in PE class. We multiply by ten.
        val beats = EditText(this).apply {
            hint = "Beats in 6 seconds"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Calculate pulse" }
        calc.setOnClickListener {
            val n = beats.text.toString().toIntOrNull() ?: 0
            val bpm = n * 10
            val zone = when {
                bpm == 0 -> "no pulse, you're doing something wrong"
                bpm < 60 -> "calm"
                bpm < 100 -> "lively"
                else -> "the heart is in a hurry"
            }
            out.text = "Pulse: $bpm bpm, $zone"
        }
        return column().apply {
            addView(beats)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Books ----------

    private fun booksScreen(): View {
        // Books with ratings. The ratings were given by the cover, reading is optional.
        val books = listOf(
            "War and Peace" to 5,
            "The Master and Margarita" to 5,
            "Washing machine manual" to 4,
            "Shopping list" to 2
        )
        val list = label("")
        val minRating = EditText(this).apply {
            hint = "Minimum rating"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val filter = Button(this).apply { text = "Show books" }
        filter.setOnClickListener {
            val min = minRating.text.toString().toIntOrNull() ?: 0
            // We filter by hand with a loop, not with filter, so every book is visible
            val result = StringBuilder()
            for ((title, rating) in books) {
                if (rating >= min) {
                    result.append("★".repeat(rating)).append("  ").append(title).append("\n")
                }
            }
            list.text = if (result.isEmpty()) "Nothing found" else result.toString()
        }
        return column().apply {
            addView(minRating)
            addView(filter)
            addView(list)
        }
    }

    // ---------- Movies ----------

    private fun moviesScreen(): View {
        // The movie is picked by lot. The genre too, but in a different order.
        val movies = listOf("A comedy about programmers", "A drama about a kettle", "A thriller without a plot", "A documentary about clouds")
        val genres = listOf("horror", "musical", "western", "cartoon")
        val result = label("What shall we watch?", 20f)
        val pick = Button(this).apply { text = "Pick a movie" }
        pick.setOnClickListener {
            val rnd = Random()
            result.text = "${movies[rnd.nextInt(movies.size)]} ( ${genres[rnd.nextInt(genres.size)]} )"
        }
        return column().apply {
            addView(result)
            addView(pick)
        }
    }

    // ---------- Checklist ----------

    private fun checklistScreen(): View {
        // A checklist for a flight. Follow in order, or don't follow at all.
        val steps = listOf(
            "Passport in pocket",
            "Phone charged",
            "Headphones in place",
            "Water bottle empty (they will take it at security)",
            "You haven't forgotten that you're flying"
        )
        val done = BooleanArray(steps.size)
        val box = column()
        fun render() {
            box.removeAllViews()
            steps.forEachIndexed { i, step ->
                box.addView(Button(this).apply {
                    text = (if (done[i]) "[x] " else "[ ] ") + step
                    setOnClickListener {
                        done[i] = !done[i]
                        render()
                    }
                })
            }
        }
        render()
        return scroll(box)
    }

    // ---------- Fractions ----------

    private fun fractionsScreen(): View {
        // We add two fractions without reducing. Denominators are multiplied together, like in third grade.
        val an = EditText(this).apply { hint = "Numerator 1" }
        val ad = EditText(this).apply { hint = "Denominator 1" }
        val bn = EditText(this).apply { hint = "Numerator 2" }
        val bd = EditText(this).apply { hint = "Denominator 2" }
        val out = label("")
        val add = Button(this).apply { text = "Add" }
        add.setOnClickListener {
            val a1 = an.text.toString().toIntOrNull() ?: 1
            val b1 = ad.text.toString().toIntOrNull() ?: 1
            val a2 = bn.text.toString().toIntOrNull() ?: 1
            val b2 = bd.text.toString().toIntOrNull() ?: 1
            val num = a1 * b2 + a2 * b1
            val den = b1 * b2
            out.text = "$num / $den = %.3f".format(num.toDouble() / den)
        }
        return column().apply {
            addView(an)
            addView(ad)
            addView(bn)
            addView(bd)
            addView(add)
            addView(out)
        }
    }

    // ---------- Football ----------

    private fun footballScreen(): View {
        // The match score. The teams are always the same, so the suspense is low.
        var home = 0
        var away = 0
        val score = label("0 : 0", 48f)
        val homeGoal = Button(this).apply { text = "Home goal" }
        val awayGoal = Button(this).apply { text = "Away goal" }
        val whistle = Button(this).apply { text = "Full time" }
        homeGoal.setOnClickListener {
            home++
            score.text = "$home : $away"
        }
        awayGoal.setOnClickListener {
            away++
            score.text = "$home : $away"
        }
        whistle.setOnClickListener {
            // The match is over. The winner is determined by comparison; a draw if nobody won.
            score.text = when {
                home > away -> "Home win: $home : $away"
                away > home -> "Away win: $home : $away"
                else -> "Draw: $home : $away"
            }
        }
        return column().apply {
            addView(score)
            addView(homeGoal)
            addView(awayGoal)
            addView(whistle)
        }
    }

    // ---------- Bytes ----------

    private fun bytesScreen(): View {
        // We convert bytes to units through powers of a thousand. Recalculated from scratch every time.
        val input = EditText(this).apply {
            hint = "Size in bytes"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val convert = Button(this).apply { text = "Convert" }
        convert.setOnClickListener {
            val bytes = input.text.toString().toDoubleOrNull() ?: 0.0
            val units = listOf("B", "KB", "MB", "GB", "TB")
            var value = bytes
            var unitIndex = 0
            // Divide by a thousand while the number is above a thousand and the units are not over
            while (value >= 1000 && unitIndex < units.size - 1) {
                value /= 1000
                unitIndex++
            }
            out.text = "%.2f %s".format(value, units[unitIndex])
        }
        return column().apply {
            addView(input)
            addView(convert)
            addView(out)
        }
    }

    // ---------- Bot ----------

    private fun chatBotScreen(): View {
        // The bot knows eleven keywords and answers everything else in the same way
        val rules = listOf(
            "hello" to "Hello! I'm a bot, and I'm very busy.",
            "how are you" to "Everything works. Almost.",
            "weather" to "Check the Weather tab, it lies too.",
            "thanks" to "You're welcome, we tried.",
            "bye" to "See you! Don't close the app.",
            "time" to "Check the Clock tab. I'm not for that.",
            "money" to "The Budget tab will count it. Roughly.",
            "cat" to "A cat is, without doubt, the best pet.",
            "dog" to "A dog is good too, but the cat is still better.",
            "help" to "Help is in the tabs at the top. Scroll them, there are a lot.",
            "joke" to "The Joke tab. There are only three of them."
        )
        val chat = label("Bot: write something")
        val input = EditText(this).apply { hint = "Message" }
        val send = Button(this).apply { text = "Send" }
        send.setOnClickListener {
            val msg = input.text.toString().lowercase().trim()
            // We search for a keyword in the message linearly. The first one found wins, the rest are not read.
            var reply = "Didn't get that, but I'll remember it. (I won't.)"
            for ((keyword, answer) in rules) {
                if (msg.contains(keyword)) {
                    reply = answer
                    break
                }
            }
            chat.text = "You: $msg\nBot: $reply"
            input.setText("")
        }
        return column().apply {
            addView(chat)
            addView(input)
            addView(send)
        }
    }

    // ---------- Truth table ----------

    private fun truthTableScreen(): View {
        // A table for AND, OR and XOR. Values are enumerated with nested loops, as in a textbook.
        val out = label("", 14f)
        val show = Button(this).apply { text = "Show table" }
        show.setOnClickListener {
            val sb = StringBuilder("A B | AND OR XOR\n")
            for (a in listOf(false, true)) {
                for (b in listOf(false, true)) {
                    val and = a && b
                    val or = a || b
                    val xor = a != b
                    sb.append("${if (a) 1 else 0} ${if (b) 1 else 0} |  ")
                    sb.append("${if (and) 1 else 0}   ${if (or) 1 else 0}  ${if (xor) 1 else 0}\n")
                }
            }
            out.text = sb.toString()
        }
        return column().apply {
            addView(show)
            addView(out)
        }
    }

    // ---------- Coffee ----------

    private fun coffeeScreen(): View {
        // A brewing timer: first let it stand, then report. The "right away" option is not provided.
        val minutes = EditText(this).apply {
            hint = "Minutes to brew"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val status = label("Coffee not started")
        val brew = Button(this).apply { text = "Brew" }
        brew.setOnClickListener {
            val m = (minutes.text.toString().toIntOrNull() ?: 3).coerceIn(1, 10)
            status.text = "Brewing $m min..."
            // A minute here is two seconds, otherwise the coffee gets cold while we wait for the UI
            Thread.sleep(m * 2000L)
            status.text = "Coffee is ready. Careful, it's hot."
        }
        return column().apply {
            addView(minutes)
            addView(brew)
            addView(status)
        }
    }

    // ---------- Stairs ----------

    private fun stairsScreen(): View {
        // How many ways to climb the stairs if you can take one or two steps at a time. The answer is Fibonacci.
        val n = EditText(this).apply {
            hint = "Steps (up to 30)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val count = Button(this).apply { text = "How many ways" }
        count.setOnClickListener {
            val steps = (n.text.toString().toIntOrNull() ?: 5).coerceIn(0, 30)
            // We count recursively without memoization, because memoization is cheating
            fun ways(k: Int): Int = if (k <= 1) 1 else ways(k - 1) + ways(k - 2)
            out.text = "Ways: ${ways(steps)}"
        }
        return column().apply {
            addView(n)
            addView(count)
            addView(out)
        }
    }

    // ---------- Laundry ----------

    private fun laundryScreen(): View {
        // Wash programs are hardcoded. The temperature is chosen by the label, not by the color of the clothes.
        val programs = mapOf(
            "White" to "90°C, 2 hours, lots of foam",
            "Colors" to "40°C, 1 hour, gently",
            "Wool" to "30°C, 40 minutes, gentle and hopeful",
            "Jeans" to "60°C, 1.5 hours, let them fall apart"
        )
        val choice = EditText(this).apply { hint = "What are we washing" }
        val out = label("")
        val start = Button(this).apply { text = "Start program" }
        start.setOnClickListener {
            val key = choice.text.toString().trim()
            out.text = programs[key] ?: "No such program. Try \"White\"."
        }
        return column().apply {
            addView(choice)
            addView(start)
            addView(out)
        }
    }

    // ---------- Frost ----------

    private fun frostScreen(): View {
        // The feels-like temperature by a simplified formula. Wind is a number we came up with ourselves too.
        val temp = EditText(this).apply {
            hint = "Temperature, °C"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val wind = EditText(this).apply {
            hint = "Wind, m/s"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "How it feels" }
        calc.setOnClickListener {
            val t = temp.text.toString().toDoubleOrNull() ?: 0.0
            val v = wind.text.toString().toDoubleOrNull() ?: 0.0
            // A formula from a textbook we don't have. The coefficients are remembered "roughly".
            val feels = 13.12 + 0.6215 * t - 11.37 * Math.pow(v, 0.16) + 0.3965 * t * Math.pow(v, 0.16)
            out.text = "Feels like %.1f °C".format(feels)
        }
        return column().apply {
            addView(temp)
            addView(wind)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Acceleration ----------

    private fun accelerationScreen(): View {
        // Distance under uniform acceleration: s = v0·t + a·t²/2. Everything in meters and seconds, no units on the screen.
        val v0 = EditText(this).apply {
            hint = "Initial speed"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val a = EditText(this).apply {
            hint = "Acceleration"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val t = EditText(this).apply {
            hint = "Time"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Calculate distance" }
        calc.setOnClickListener {
            val speed = v0.text.toString().toDoubleOrNull() ?: 0.0
            val acc = a.text.toString().toDoubleOrNull() ?: 0.0
            val time = t.text.toString().toDoubleOrNull() ?: 0.0
            val path = speed * time + acc * time * time / 2
            out.text = "Distance: %.2f m".format(path)
        }
        return column().apply {
            addView(v0)
            addView(a)
            addView(t)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Proportions ----------

    private fun proportionsScreen(): View {
        // The proportion a : b = c : x. Solved by cross multiplication, without extra words.
        val a = EditText(this).apply { hint = "a" }
        val b = EditText(this).apply { hint = "b" }
        val c = EditText(this).apply { hint = "c" }
        val out = label("")
        val solve = Button(this).apply { text = "Find x" }
        solve.setOnClickListener {
            val av = a.text.toString().toDoubleOrNull() ?: 0.0
            val bv = b.text.toString().toDoubleOrNull() ?: 0.0
            val cv = c.text.toString().toDoubleOrNull() ?: 0.0
            out.text = if (av == 0.0) "We won't divide by zero" else "x = %.3f".format(bv * cv / av)
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(c)
            addView(solve)
            addView(out)
        }
    }

    // ---------- Holidays ----------

    private fun holidaysScreen(): View {
        // Holidays from memory, by month. We have not opened the official calendar; it's a matter of principle.
        val holidays = mapOf(
            1 to "New Year and holidays",
            2 to "Defender of the Fatherland Day",
            3 to "Women's Day",
            5 to "Spring and Labour Day",
            6 to "Summer",
            9 to "Knowledge Day",
            12 to "New Year is coming soon"
        )
        val month = EditText(this).apply {
            hint = "Month (1–12)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val look = Button(this).apply { text = "What are we celebrating" }
        look.setOnClickListener {
            val m = month.text.toString().toIntOrNull() ?: 1
            out.text = holidays[m] ?: "No holidays this month. Make one up yourself."
        }
        return column().apply {
            addView(month)
            addView(look)
            addView(out)
        }
    }

    // ---------- Word of the day ----------

    private fun wordOfDayScreen(): View {
        // The word of the day changes every day by the day number in the year. Today it is surely the same as yesterday, if you're lucky.
        val words = listOf(
            "Ephemeral - short-lived, fleeting.",
            "Laconic - brief, without extra words.",
            "Unyielding - firm, not giving in.",
            "Inspired - full of inspiration.",
            "Meticulous - accurate, careful."
        )
        val dayOfYear = (System.currentTimeMillis() / 86_400_000L % 365).toInt()
        val word = label(words[dayOfYear % words.size], 18f)
        return column().apply {
            addView(label("Word of the day:"))
            addView(word)
        }
    }

    // ---------- Date difference ----------

    private fun dateDiffScreen(): View {
        // The difference between dates in days. A month is counted as thirty days, a year as three hundred and sixty-five.
        val from = EditText(this).apply {
            hint = "Date 1: YYYY-MM-DD"
        }
        val to = EditText(this).apply {
            hint = "Date 2: YYYY-MM-DD"
        }
        val out = label("")
        val count = Button(this).apply { text = "How many days" }
        count.setOnClickListener {
            val a = parseDay(from.text.toString())
            val b = parseDay(to.text.toString())
            out.text = if (a == null || b == null) "Couldn't read the dates" else "Days between them: ${kotlin.math.abs(b - a)}"
        }
        return column().apply {
            addView(from)
            addView(to)
            addView(count)
            addView(out)
        }
    }

    private fun parseDay(s: String): Int? {
        // We parse by hand: year, month, day. Leap years are not counted, we agreed on that.
        val parts = s.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return y * 365 + m * 30 + d
    }

    // ---------- Ohm's law ----------

    private fun ohmScreen(): View {
        // Voltage, current, resistance: know any two, compute the third. If all three are known, figure it out yourself.
        val voltage = EditText(this).apply {
            hint = "Voltage, V"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val current = EditText(this).apply {
            hint = "Current, A"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val resistance = EditText(this).apply {
            hint = "Resistance, Ω"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val solve = Button(this).apply { text = "Find the unknown" }
        solve.setOnClickListener {
            val u = voltage.text.toString().toDoubleOrNull()
            val i = current.text.toString().toDoubleOrNull()
            val r = resistance.text.toString().toDoubleOrNull()
            out.text = when {
                u != null && i != null && r != null -> "All three are given. Check it yourself, we are not judges."
                u != null && i != null -> "R = %.3f Ω".format(u / i)
                u != null && r != null -> "I = %.3f A".format(u / r)
                i != null && r != null -> "U = %.3f V".format(i * r)
                else -> "We need at least two values"
            }
        }
        return column().apply {
            addView(voltage)
            addView(current)
            addView(resistance)
            addView(solve)
            addView(out)
        }
    }

    // ---------- Density ----------

    private fun densityScreen(): View {
        // Density = mass / volume. The materials and their densities are from memory, so this is not for lab work.
        val materials = mapOf(
            "Water" to 1.0,
            "Iron" to 7.87,
            "Wood" to 0.6,
            "Gold" to 19.3
        )
        val mass = EditText(this).apply {
            hint = "Mass, g"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val material = EditText(this).apply { hint = "Material: Water, Iron, Wood or Gold" }
        val out = label("")
        val calc = Button(this).apply { text = "Volume" }
        calc.setOnClickListener {
            val m = mass.text.toString().toDoubleOrNull() ?: 0.0
            val density = materials[material.text.toString().trim()]
            // The volume is computed through a density that is missing from the table if the material is absent. Zero is an honest answer.
            out.text = if (density == null) "No such material" else "Volume: %.2f cm³".format(m / density)
        }
        return column().apply {
            addView(mass)
            addView(material)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Efficiency ----------

    private fun efficiencyScreen(): View {
        // Efficiency = useful work / energy spent, in percent. Over a hundred is impossible, but we will check anyway.
        val useful = EditText(this).apply {
            hint = "Useful work"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val spent = EditText(this).apply {
            hint = "Energy spent"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Efficiency" }
        calc.setOnClickListener {
            val u = useful.text.toString().toDoubleOrNull() ?: 0.0
            val s = spent.text.toString().toDoubleOrNull() ?: 0.0
            if (s == 0.0) {
                out.text = "No energy, efficiency is infinite, but that is a lie"
                return@setOnClickListener
            }
            val eff = u / s * 100
            out.text = if (eff > 100) "Over a hundred percent, you've invented a perpetual motion machine" else "Efficiency: %.1f %%".format(eff)
        }
        return column().apply {
            addView(useful)
            addView(spent)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Calories ----------

    private fun caloriesScreen(): View {
        // Calories per hundred grams. The table is incomplete, just like our fridge.
        val calories = mapOf(
            "apple" to 52,
            "bread" to 265,
            "chicken" to 239,
            "chocolate" to 546,
            "cucumber" to 15
        )
        val food = EditText(this).apply { hint = "Food" }
        val grams = EditText(this).apply {
            hint = "Grams"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val count = Button(this).apply { text = "Count calories" }
        count.setOnClickListener {
            val per100 = calories[food.text.toString().trim().lowercase()]
            val g = grams.text.toString().toIntOrNull() ?: 100
            out.text = if (per100 == null) "No such food, but there are cookies" else "Calories: ${per100 * g / 100}"
        }
        return column().apply {
            addView(food)
            addView(grams)
            addView(count)
            addView(out)
        }
    }

    // ---------- Day of the week ----------

    private fun weekdayScreen(): View {
        // The day of the week from the day count since the epoch. The epoch fell on a Thursday, we remember that exactly.
        val names = listOf("Thursday", "Friday", "Saturday", "Sunday", "Monday", "Tuesday", "Wednesday")
        val day = EditText(this).apply {
            hint = "Day number since 1970-01-01"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val check = Button(this).apply { text = "Which day" }
        check.setOnClickListener {
            val n = day.text.toString().toIntOrNull() ?: 0
            out.text = names[((n % 7) + 7) % 7]
        }
        return column().apply {
            addView(day)
            addView(check)
            addView(out)
        }
    }

    // ---------- Body temperature ----------

    private fun bodyTempScreen(): View {
        // Degrees Celsius with verdicts. The thermometer is not connected, so no doctor is needed, but we recommend one.
        val temp = EditText(this).apply {
            hint = "Temperature, °C"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val check = Button(this).apply { text = "Assess" }
        check.setOnClickListener {
            val t = temp.text.toString().toDoubleOrNull() ?: 36.6
            out.text = when {
                t < 35 -> "Cold. See a doctor urgently."
                t <= 37.0 -> "Normal. Keep living."
                t <= 38.5 -> "A bit hot, drink some tea."
                else -> "High. Please see a doctor."
            }
        }
        return column().apply {
            addView(temp)
            addView(check)
            addView(out)
        }
    }

    // ---------- Pizza ----------

    private fun pizzaScreen(): View {
        // We split the pizza into slices and between people. The leftover goes to whoever was the hungriest.
        val slices = EditText(this).apply {
            hint = "Slices"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val people = EditText(this).apply {
            hint = "People"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val split = Button(this).apply { text = "Split" }
        split.setOnClickListener {
            val s = slices.text.toString().toIntOrNull() ?: 8
            val p = (people.text.toString().toIntOrNull() ?: 2).coerceAtLeast(1)
            out.text = "${s / p} each, the remaining ${s % p} go to the hungriest"
        }
        return column().apply {
            addView(slices)
            addView(people)
            addView(split)
            addView(out)
        }
    }

    // ---------- Fibonacci ----------

    private fun fibonacciScreen(): View {
        // The first Fibonacci numbers, each one computed again from the previous ones, without memoization
        val count = EditText(this).apply {
            hint = "How many numbers (up to 25)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("", 14f)
        val show = Button(this).apply { text = "Show" }
        show.setOnClickListener {
            val n = (count.text.toString().toIntOrNull() ?: 10).coerceIn(1, 25)
            fun fib(k: Int): Long = if (k < 2) k.toLong() else fib(k - 1) + fib(k - 2)
            out.text = (0 until n).joinToString(", ") { fib(it).toString() }
        }
        return scroll(column().apply {
            addView(count)
            addView(show)
            addView(out)
        })
    }

    // ---------- Twin primes ----------

    private fun twinPrimesScreen(): View {
        // Twin primes differ by two. We check by division, like everything else.
        val limit = EditText(this).apply {
            hint = "Up to which number"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val find = Button(this).apply { text = "Find pairs" }
        find.setOnClickListener {
            val max = limit.text.toString().toIntOrNull() ?: 50
            fun isPrime(x: Int): Boolean {
                if (x < 2) return false
                for (d in 2 until x) if (x % d == 0) return false
                return true
            }
            val pairs = mutableListOf<String>()
            for (x in 2..max - 2) {
                if (isPrime(x) && isPrime(x + 2)) pairs.add("($x, ${x + 2})")
            }
            out.text = if (pairs.isEmpty()) "No pairs found" else pairs.joinToString(" ")
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Seconds ----------

    private fun secondsScreen(): View {
        // We convert seconds into days, hours, minutes and seconds. Dividing one step at a time, like on a childhood calculator.
        val seconds = EditText(this).apply {
            hint = "Seconds"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val split = Button(this).apply { text = "Break down" }
        split.setOnClickListener {
            var rest = seconds.text.toString().toLongOrNull() ?: 0L
            val days = rest / 86_400
            rest %= 86_400
            val hours = rest / 3_600
            rest %= 3_600
            val minutes = rest / 60
            val secs = rest % 60
            out.text = "$days d $hours h $minutes min $secs s"
        }
        return column().apply {
            addView(seconds)
            addView(split)
            addView(out)
        }
    }

    // ---------- Square root ----------

    private fun sqrtScreen(): View {
        // Root by Newton's method, twenty iterations without a convergence check. We hope for the best.
        val number = EditText(this).apply {
            hint = "Number"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val root = Button(this).apply { text = "Root" }
        root.setOnClickListener {
            val n = number.text.toString().toDoubleOrNull() ?: 0.0
            if (n < 0) {
                out.text = "We don't take roots of negatives, that's a different tab"
                return@setOnClickListener
            }
            var x = if (n == 0.0) 0.0 else n
            repeat(20) {
                if (x != 0.0) x = (x + n / x) / 2
            }
            out.text = "√$n ≈ %.6f".format(x)
        }
        return column().apply {
            addView(number)
            addView(root)
            addView(out)
        }
    }

    // ---------- Deposit ----------

    private fun depositScreen(): View {
        // Compound interest: every month the money grows by the rate, and grows on last month's growth too.
        val sum = EditText(this).apply {
            hint = "Deposit amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val rate = EditText(this).apply {
            hint = "Rate, % per year"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val months = EditText(this).apply {
            hint = "Months"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Calculate income" }
        calc.setOnClickListener {
            var money = sum.text.toString().toDoubleOrNull() ?: 0.0
            val monthlyRate = (rate.text.toString().toDoubleOrNull() ?: 0.0) / 100 / 12
            val n = months.text.toString().toIntOrNull() ?: 12
            // Capitalization is computed one month at a time, not by a formula, so the growth is visible
            repeat(n) { money += money * monthlyRate }
            out.text = "After $n months: %.2f".format(money)
        }
        return column().apply {
            addView(sum)
            addView(rate)
            addView(months)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Loan ----------

    private fun loanScreen(): View {
        // Monthly payment on an annuity schedule. Banks compute it this way too, but they add fees that are not here.
        val principal = EditText(this).apply {
            hint = "Loan amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val rate = EditText(this).apply {
            hint = "Rate, % per year"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val years = EditText(this).apply {
            hint = "Years"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Monthly payment" }
        calc.setOnClickListener {
            val p = principal.text.toString().toDoubleOrNull() ?: 0.0
            val r = (rate.text.toString().toDoubleOrNull() ?: 0.0) / 100 / 12
            val n = (years.text.toString().toIntOrNull() ?: 1) * 12
            if (r == 0.0) {
                out.text = "Payment without interest: %.2f".format(p / n)
                return@setOnClickListener
            }
            val factor = Math.pow(1 + r, n.toDouble())
            val payment = p * r * factor / (factor - 1)
            out.text = "Payment: %.2f per month, overpayment: %.2f".format(payment, payment * n - p)
        }
        return column().apply {
            addView(principal)
            addView(rate)
            addView(years)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Estimate ----------

    private fun estimateScreen(): View {
        // Renovation by area: the price per square meter is hardcoded by hand, overheads are twenty percent.
        val area = EditText(this).apply {
            hint = "Area, m²"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val work = EditText(this).apply { hint = "Type of work: painting, tiles, wallpaper" }
        val out = label("")
        val calc = Button(this).apply { text = "Estimate" }
        calc.setOnClickListener {
            val prices = mapOf("painting" to 300.0, "tiles" to 1200.0, "wallpaper" to 500.0)
            val a = area.text.toString().toDoubleOrNull() ?: 0.0
            val price = prices[work.text.toString().trim().lowercase()]
            out.text = if (price == null) "This work is not in the price list" else {
                val base = a * price
                "Work: %.0f RUB, with overheads: %.0f RUB".format(base, base * 1.2)
            }
        }
        return column().apply {
            addView(area)
            addView(work)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Box ----------

    private fun boxScreen(): View {
        // Volume of a box: length, width, height. All sizes in centimeters, liters come from dividing by a thousand.
        val l = EditText(this).apply { hint = "Length, cm"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val w = EditText(this).apply { hint = "Width, cm"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val h = EditText(this).apply { hint = "Height, cm"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Volume" }
        calc.setOnClickListener {
            val v = (l.text.toString().toDoubleOrNull() ?: 0.0) *
                (w.text.toString().toDoubleOrNull() ?: 0.0) *
                (h.text.toString().toDoubleOrNull() ?: 0.0)
            out.text = "%.0f cm³ = %.3f l".format(v, v / 1000)
        }
        return column().apply {
            addView(l)
            addView(w)
            addView(h)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Perimeter ----------

    private fun perimeterScreen(): View {
        // Perimeter of a rectangle: two sides, times two. Obvious, but we'll check it anyway.
        val a = EditText(this).apply { hint = "Side A"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val b = EditText(this).apply { hint = "Side B"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Perimeter" }
        calc.setOnClickListener {
            val x = a.text.toString().toDoubleOrNull() ?: 0.0
            val y = b.text.toString().toDoubleOrNull() ?: 0.0
            // We add the four sides one by one, instead of multiplying two by two: that's more honest
            out.text = "P = %.2f".format(x + y + x + y)
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Circumference ----------

    private fun circleLengthScreen(): View {
        // Circumference from the diameter. Pi is taken from fifth grade, so it's rounded.
        val d = EditText(this).apply { hint = "Diameter"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Length" }
        calc.setOnClickListener {
            val diameter = d.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "L ≈ %.3f".format(3.14 * diameter)
        }
        return column().apply {
            addView(d)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Triangle ----------

    private fun triangleScreen(): View {
        // Area from three sides, Heron's formula. We compute a triangle that doesn't exist too.
        val a = EditText(this).apply { hint = "a"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val b = EditText(this).apply { hint = "b"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val c = EditText(this).apply { hint = "c"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Area" }
        calc.setOnClickListener {
            val x = a.text.toString().toDoubleOrNull() ?: 0.0
            val y = b.text.toString().toDoubleOrNull() ?: 0.0
            val z = c.text.toString().toDoubleOrNull() ?: 0.0
            val p = (x + y + z) / 2
            val inside = p * (p - x) * (p - y) * (p - z)
            // If the value under the root is negative, there is no triangle. We still print a number, because the root is a formality.
            out.text = if (inside < 0) "Such a triangle doesn't exist" else "S = %.3f".format(Math.sqrt(inside))
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(c)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Goal ----------

    private fun goalScreen(): View {
        // Progress toward the goal is stored in SharedPreferences. The percent is computed from the goal, not from the current value.
        var saved = Knowitall.prefs.getInt("goal", 0)
        val target = 1000
        val bar = label("Saved: $saved of $target", 18f)
        val add = EditText(this).apply { hint = "How much to add"; inputType = InputType.TYPE_CLASS_NUMBER }
        val put = Button(this).apply { text = "Add" }
        put.setOnClickListener {
            saved += add.text.toString().toIntOrNull() ?: 0
            Knowitall.prefs.edit().putInt("goal", saved).commit()
            add.setText("")
            val percent = saved * 100 / target
            // The progress bar is drawn from characters, one for every ten percent
            val filled = "#".repeat((percent / 10).coerceAtMost(10))
            val empty = "-".repeat(10 - (percent / 10).coerceAtMost(10))
            bar.text = "[$filled$empty] $percent% ($saved of $target)"
        }
        return column().apply {
            addView(bar)
            addView(add)
            addView(put)
        }
    }

    // ---------- Streak ----------

    private fun streakScreen(): View {
        // A streak of days without gaps. We check for a gap by the day number, which we remember ourselves.
        var streak = Knowitall.prefs.getInt("streak", 0)
        val caption = label("Streak: $streak days", 24f)
        val mark = Button(this).apply { text = "Mark today" }
        mark.setOnClickListener {
            streak++
            Knowitall.prefs.edit().putInt("streak", streak).commit()
            caption.text = "Streak: $streak days 🔥"
        }
        val lose = Button(this).apply { text = "Missed a day" }
        lose.setOnClickListener {
            streak = 0
            Knowitall.prefs.edit().putInt("streak", 0).commit()
            caption.text = "Streak: 0 days. It happens."
        }
        return column().apply {
            addView(caption)
            addView(mark)
            addView(lose)
        }
    }

    // ---------- Brushing ----------

    private fun brushingScreen(): View {
        // Two minutes of brushing, thirty seconds per quadrant. The timer ticks with a busy loop so the teeth don't get distracted.
        val quadrants = listOf("upper left", "upper right", "lower left", "lower right")
        val status = label("Ready? Brush in hand.", 18f)
        val start = Button(this).apply { text = "Start brushing" }
        start.setOnClickListener {
            for (q in quadrants) {
                status.text = "Brushing: $q"
                val until = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < until) { /* chewing on thoughts */ }
            }
            status.text = "Done. Don't forget your tongue."
        }
        return column().apply {
            addView(status)
            addView(start)
        }
    }

    // ---------- Password strength ----------

    private fun passwordCheckScreen(): View {
        // Password rating by length and character set. We don't check dictionaries, nobody forced us to.
        val pass = EditText(this).apply { hint = "Password (it doesn't go anywhere, honestly)" }
        val out = label("")
        val check = Button(this).apply { text = "Rate it" }
        check.setOnClickListener {
            val p = pass.text.toString()
            var score = 0
            if (p.length >= 8) score++
            if (p.any { it.isUpperCase() }) score++
            if (p.any { it.isDigit() }) score++
            if (p.any { !it.isLetterOrDigit() }) score++
            if (p.length >= 14) score++
            out.text = when (score) {
                0, 1 -> "Weak. We'll guess it in a minute."
                2, 3 -> "Medium. We'll guess it in a week."
                4 -> "Good. We'll guess it in years."
                else -> "Excellent. We'll guess it only if you tell us."
            }
        }
        return column().apply {
            addView(pass)
            addView(check)
            addView(out)
        }
    }

    // ---------- Anagram ----------

    private fun anagramScreen(): View {
        // We check whether two words are made of the same letters. We sort the letters with a bubble sort, honoring tradition.
        val first = EditText(this).apply { hint = "First word" }
        val second = EditText(this).apply { hint = "Second word" }
        val out = label("")
        val check = Button(this).apply { text = "Check" }
        check.setOnClickListener {
            val a = sortLetters(first.text.toString().lowercase().filter { it.isLetter() })
            val b = sortLetters(second.text.toString().lowercase().filter { it.isLetter() })
            out.text = if (a.isNotEmpty() && a == b) "Anagram!" else "No, this is not an anagram"
        }
        return column().apply {
            addView(first)
            addView(second)
            addView(check)
            addView(out)
        }
    }

    private fun sortLetters(s: String): String {
        val arr = s.toCharArray()
        for (i in arr.indices) {
            for (j in 0 until arr.size - 1 - i) {
                if (arr[j] > arr[j + 1]) {
                    val t = arr[j]
                    arr[j] = arr[j + 1]
                    arr[j + 1] = t
                }
            }
        }
        return String(arr)
    }

    // ---------- Reverse words ----------

    private fun reverseWordsScreen(): View {
        // We reverse the order of the words, not the letters inside them. That's what "reverse" means here.
        val input = EditText(this).apply { hint = "Phrase" }
        val out = label("")
        val flip = Button(this).apply { text = "Reverse words" }
        flip.setOnClickListener {
            val words = input.text.toString().trim().split(" ").filter { it.isNotEmpty() }
            var result = ""
            for (i in words.indices.reversed()) {
                result += words[i]
                if (i > 0) result += " "
            }
            out.text = result
        }
        return column().apply {
            addView(input)
            addView(flip)
            addView(out)
        }
    }

    // ---------- Letter frequency ----------

    private fun letterFrequencyScreen(): View {
        // Letter frequency is counted with a map, then sorted by descending count by a built-in sort. The top twenty.
        val input = EditText(this).apply { hint = "Text" }
        val out = label("", 14f)
        val count = Button(this).apply { text = "Count letters" }
        count.setOnClickListener {
            val freq = HashMap<Char, Int>()
            for (c in input.text.toString().lowercase()) {
                if (c.isLetter()) freq[c] = (freq[c] ?: 0) + 1
            }
            val top = freq.entries.sortedByDescending { it.value }.take(20)
            out.text = top.joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "No letters found" }
        }
        return scroll(column().apply {
            addView(input)
            addView(count)
            addView(out)
        })
    }

    // ---------- Dice score ----------

    private fun diceScoreScreen(): View {
        // A game called "Points": three dice, the sum of the points. Three of a kind doubles the score, because it's more fun.
        val score = label("Points: 0", 24f)
        var total = 0
        val roll = Button(this).apply { text = "Roll three dice" }
        roll.setOnClickListener {
            val rnd = Random()
            val dice = listOf(rnd.nextInt(6) + 1, rnd.nextInt(6) + 1, rnd.nextInt(6) + 1)
            var gained = dice.sum()
            if (dice.distinct().size == 1) gained *= 2
            total += gained
            score.text = "Rolled ${dice.joinToString(" ")}, points: $total"
        }
        return column().apply {
            addView(score)
            addView(roll)
        }
    }

    // ---------- ROT13 ----------

    private fun rot13Screen(): View {
        // ROT13: every Latin letter is shifted by thirteen. Apply it twice and you get the original back. It's magic.
        val input = EditText(this).apply { hint = "Text" }
        val out = label("")
        val go = Button(this).apply { text = "Convert" }
        go.setOnClickListener {
            out.text = input.text.toString().map { c ->
                when (c) {
                    in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                    in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                    else -> c
                }
            }.joinToString("")
        }
        return column().apply {
            addView(input)
            addView(go)
            addView(out)
        }
    }

    // ---------- Morse ----------

    private fun morseScreen(): View {
        // International Morse code for Latin letters and digits. Non-Latin text is not translated.
        val table = mapOf(
            'a' to ".-", 'b' to "-...", 'c' to "-.-.", 'd' to "-..", 'e' to ".", 'f' to "..-.",
            'g' to "--.", 'h' to "....", 'i' to "..", 'j' to ".---", 'k' to "-.-", 'l' to ".-..",
            'm' to "--", 'n' to "-.", 'o' to "---", 'p' to ".--.", 'q' to "--.-", 'r' to ".-.",
            's' to "...", 't' to "-", 'u' to "..-", 'v' to "...-", 'w' to ".--", 'x' to "-..-",
            'y' to "-.--", 'z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
            '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
            '8' to "---..", '9' to "----."
        )
        val input = EditText(this).apply { hint = "Text in Latin letters" }
        val out = label("", 14f)
        val encode = Button(this).apply { text = "To Morse" }
        encode.setOnClickListener {
            out.text = input.text.toString().lowercase().map { c ->
                if (c == ' ') "/" else table[c] ?: "?"
            }.joinToString(" ")
        }
        return scroll(column().apply {
            addView(input)
            addView(encode)
            addView(out)
        })
    }

    // ---------- Digit sum ----------

    private fun digitSumScreen(): View {
        // The sum of the digits of a number. The minus sign at the start is dropped, because digits have nothing to do with signs.
        val number = EditText(this).apply { hint = "Number"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val calc = Button(this).apply { text = "Add the digits" }
        calc.setOnClickListener {
            val digits = number.text.toString().removePrefix("-")
            var sum = 0
            for (c in digits) {
                if (c.isDigit()) sum += c - '0'
            }
            out.text = "Digit sum: $sum"
        }
        return column().apply {
            addView(number)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Armstrong numbers ----------

    private fun armstrongScreen(): View {
        // An Armstrong number: the sum of its digits, each raised to the number of digits, equals the number itself. We check up to a million.
        val limit = EditText(this).apply { hint = "Up to which (max 1000000)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("", 14f)
        val find = Button(this).apply { text = "Find" }
        find.setOnClickListener {
            val max = (limit.text.toString().toIntOrNull() ?: 1000).coerceAtMost(1_000_000)
            val found = mutableListOf<Int>()
            for (n in 1..max) {
                val s = n.toString()
                var sum = 0.0
                for (c in s) sum += Math.pow((c - '0').toDouble(), s.length.toDouble())
                if (sum.toInt() == n) found.add(n)
            }
            out.text = found.joinToString(", ").ifEmpty { "Nothing found" }
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Pythagorean triples ----------

    private fun triplesScreen(): View {
        // Triples of integers where a² + b² = c². We enumerate all triples up to a given c, no tricks.
        val limit = EditText(this).apply { hint = "Maximum c"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("", 14f)
        val find = Button(this).apply { text = "Find triples" }
        find.setOnClickListener {
            val max = (limit.text.toString().toIntOrNull() ?: 30).coerceIn(5, 200)
            val lines = mutableListOf<String>()
            for (c in 1..max) for (b in 1 until c) for (a in 1..b) {
                if (a * a + b * b == c * c) lines.add("$a, $b, $c")
            }
            out.text = lines.joinToString("\n").ifEmpty { "No triples found" }
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Power ----------

    private fun powerScreen(): View {
        // Exponentiation by multiplying in a loop. Negative exponents are not supported, those are fractions.
        val base = EditText(this).apply { hint = "Base"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val exp = EditText(this).apply { hint = "Exponent (integer, from 0)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val calc = Button(this).apply { text = "Raise" }
        calc.setOnClickListener {
            val b = base.text.toString().toDoubleOrNull() ?: 0.0
            val e = (exp.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
            var result = 1.0
            repeat(e) { result *= b }
            out.text = "$b^$e = $result"
        }
        return column().apply {
            addView(base)
            addView(exp)
            addView(calc)
            addView(out)
        }
    }

    // ---------- GCD ----------

    private fun gcdScreen(): View {
        // Greatest common divisor by Euclid. We subtract until they meet, if division doesn't appeal to us.
        val a = EditText(this).apply { hint = "First"; inputType = InputType.TYPE_CLASS_NUMBER }
        val b = EditText(this).apply { hint = "Second"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val calc = Button(this).apply { text = "GCD" }
        calc.setOnClickListener {
            var x = a.text.toString().toIntOrNull() ?: 0
            var y = b.text.toString().toIntOrNull() ?: 0
            if (x == 0 || y == 0) {
                out.text = "GCD with zero is the number itself"
                return@setOnClickListener
            }
            while (x != y) {
                if (x > y) x -= y else y -= x
            }
            out.text = "GCD = $x"
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(calc)
            addView(out)
        }
    }

    // ---------- LCM ----------

    private fun lcmScreen(): View {
        // Least common multiple through the GCD. First we recall the GCD, then divide the product.
        val a = EditText(this).apply { hint = "First"; inputType = InputType.TYPE_CLASS_NUMBER }
        val b = EditText(this).apply { hint = "Second"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val calc = Button(this).apply { text = "LCM" }
        calc.setOnClickListener {
            val x = a.text.toString().toIntOrNull() ?: 0
            val y = b.text.toString().toIntOrNull() ?: 0
            if (x == 0 || y == 0) {
                out.text = "LCM with zero is zero"
                return@setOnClickListener
            }
            // The GCD is found again, by subtraction, so we don't depend on the previous tab
            var p = x
            var q = y
            while (p != q) {
                if (p > q) p -= q else q -= p
            }
            out.text = "LCM = ${x / p * y}"
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Logarithm ----------

    private fun logScreen(): View {
        // Natural logarithm and base-ten logarithm. Negative numbers go to NaN and we don't apologize.
        val number = EditText(this).apply { hint = "Number"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Logarithm" }
        calc.setOnClickListener {
            val x = number.text.toString().toDoubleOrNull() ?: 1.0
            out.text = "ln = %.4f\nlg = %.4f".format(Math.log(x), Math.log10(x))
        }
        return column().apply {
            addView(number)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Sine table ----------

    private fun sineTableScreen(): View {
        // Sines for angles from zero to ninety degrees in steps of fifteen. Enough for school geometry.
        val out = label("", 14f)
        val show = Button(this).apply { text = "Show sines" }
        show.setOnClickListener {
            val sb = StringBuilder()
            for (deg in 0..90 step 15) {
                val rad = Math.toRadians(deg.toDouble())
                sb.append("sin($deg°) = %.4f\n".format(Math.sin(rad)))
            }
            out.text = sb.toString()
        }
        return column().apply {
            addView(show)
            addView(out)
        }
    }

    // ---------- Roulette ----------

    private fun rouletteScreen(): View {
        // European roulette: numbers from zero to thirty-six, colors by table. Zero is outside the colors.
        val red = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
        val result = label("Spin it", 24f)
        val spin = Button(this).apply { text = "Spin" }
        spin.setOnClickListener {
            val n = Random().nextInt(37)
            val color = when {
                n == 0 -> "green"
                n in red -> "red"
                else -> "black"
            }
            result.text = "Landed on $n, $color"
        }
        return column().apply {
            addView(result)
            addView(spin)
        }
    }

    // ---------- Stickers ----------

    private fun stickersScreen(): View {
        // A sticker collection: one random sticker at a time, we don't track repeats, nobody counts them anyway.
        val stickers = listOf("🐱", "🐶", "🦊", "🐸", "🐵", "🦉", "🐙", "🦄")
        val got = mutableListOf<String>()
        val last = label("No stickers yet", 36f)
        val open = Button(this).apply { text = "Open a sticker" }
        open.setOnClickListener {
            val s = stickers[Random().nextInt(stickers.size)]
            got.add(s)
            last.text = s
        }
        val total = label("")
        val show = Button(this).apply { text = "Collection" }
        show.setOnClickListener {
            total.text = "Total: ${got.size}. Unique: ${got.distinct().size} of ${stickers.size}"
        }
        return column().apply {
            addView(last)
            addView(open)
            addView(show)
            addView(total)
        }
    }

    // ---------- Perfect numbers ----------

    private fun perfectNumbersScreen(): View {
        // A perfect number equals the sum of its divisors, excluding itself. Six, twenty-eight, and that's enough.
        val limit = EditText(this).apply { hint = "Up to"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val find = Button(this).apply { text = "Find" }
        find.setOnClickListener {
            val max = (limit.text.toString().toIntOrNull() ?: 500).coerceAtMost(10_000)
            val found = mutableListOf<Int>()
            for (n in 2..max) {
                var sum = 1
                for (d in 2 until n) if (n % d == 0) sum += d
                if (sum == n) found.add(n)
            }
            out.text = found.joinToString(", ").ifEmpty { "Nothing found" }
        }
        return column().apply {
            addView(limit)
            addView(find)
            addView(out)
        }
    }

    // ---------- Discount ----------

    private fun discountScreen(): View {
        // Price with a discount. We don't give discounts over fifty percent, that's already suspicious.
        val price = EditText(this).apply { hint = "Price"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val percent = EditText(this).apply { hint = "Discount, %"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Final price" }
        calc.setOnClickListener {
            val p = price.text.toString().toDoubleOrNull() ?: 0.0
            val d = (percent.text.toString().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 50.0)
            val final = p * (1 - d / 100)
            out.text = "Price: %.2f (saved %.2f)".format(final, p - final)
        }
        return column().apply {
            addView(price)
            addView(percent)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Minutes ----------

    private fun minutesScreen(): View {
        // Hours and minutes into minutes and back. "Back" is when we say "an hour and a half", and we understand.
        val input = EditText(this).apply { hint = "Minutes"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val toHours = Button(this).apply { text = "To hours" }
        toHours.setOnClickListener {
            val m = input.text.toString().toIntOrNull() ?: 0
            out.text = "${m / 60} h ${m % 60} min"
        }
        val toMinutes = Button(this).apply { text = "From hours" }
        toMinutes.setOnClickListener {
            val h = input.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "%.0f minutes".format(h * 60)
        }
        return column().apply {
            addView(input)
            addView(toHours)
            addView(toMinutes)
            addView(out)
        }
    }

    // ---------- Average ----------

    private fun statsScreen(): View {
        // The arithmetic mean of a list of numbers separated by commas. An empty list is the average of "nothing".
        val numbers = EditText(this).apply { hint = "Numbers, comma-separated" }
        val out = label("")
        val calc = Button(this).apply { text = "Average" }
        calc.setOnClickListener {
            val list = numbers.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }
            var sum = 0.0
            for (x in list) sum += x
            out.text = if (list.isEmpty()) "No numbers, so no average" else "Average: %.3f (of ${list.size})".format(sum / list.size)
        }
        return column().apply {
            addView(numbers)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Median ----------

    private fun medianScreen(): View {
        // Median: sort with a bubble sort and take the middle. For an even count, take the average of the two middle values.
        val numbers = EditText(this).apply { hint = "Numbers, comma-separated" }
        val out = label("")
        val calc = Button(this).apply { text = "Median" }
        calc.setOnClickListener {
            val list = numbers.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }.toMutableList()
            if (list.isEmpty()) {
                out.text = "We can't take the median of nothing"
                return@setOnClickListener
            }
            for (i in list.indices) {
                for (j in 0 until list.size - 1 - i) {
                    if (list[j] > list[j + 1]) {
                        val t = list[j]
                        list[j] = list[j + 1]
                        list[j + 1] = t
                    }
                }
            }
            val mid = list.size / 2
            val median = if (list.size % 2 == 1) list[mid] else (list[mid - 1] + list[mid]) / 2
            out.text = "Median: %.3f".format(median)
        }
        return column().apply {
            addView(numbers)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Variance ----------

    private fun varianceScreen(): View {
        // Variance: the mean square deviation from the average. Computed in two passes, as we were taught.
        val numbers = EditText(this).apply { hint = "Numbers, comma-separated" }
        val out = label("")
        val calc = Button(this).apply { text = "Variance" }
        calc.setOnClickListener {
            val list = numbers.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }
            if (list.isEmpty()) {
                out.text = "No data"
                return@setOnClickListener
            }
            var sum = 0.0
            for (x in list) sum += x
            val mean = sum / list.size
            var sq = 0.0
            for (x in list) sq += (x - mean) * (x - mean)
            out.text = "Variance: %.3f".format(sq / list.size)
        }
        return column().apply {
            addView(numbers)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Parity ----------

    private fun parityScreen(): View {
        // Even or odd. Zero is even, and we will not discuss it.
        val input = EditText(this).apply { hint = "Number"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val check = Button(this).apply { text = "Check" }
        check.setOnClickListener {
            val n = input.text.toString().toIntOrNull() ?: 0
            out.text = if (n % 2 == 0) "Even" else "Odd"
        }
        return column().apply {
            addView(input)
            addView(check)
            addView(out)
        }
    }

    // ---------- Distance between points ----------

    private fun distanceScreen(): View {
        // The distance between two points on a plane by the Pythagorean theorem. Coordinates are typed space-separated: x1 y1 x2 y2.
        val input = EditText(this).apply { hint = "x1 y1 x2 y2" }
        val out = label("")
        val calc = Button(this).apply { text = "Distance" }
        calc.setOnClickListener {
            val parts = input.text.toString().trim().split(" ").mapNotNull { it.toDoubleOrNull() }
            if (parts.size != 4) {
                out.text = "We need four numbers separated by spaces"
                return@setOnClickListener
            }
            val dx = parts[2] - parts[0]
            val dy = parts[3] - parts[1]
            out.text = "Distance: %.3f".format(Math.sqrt(dx * dx + dy * dy))
        }
        return column().apply {
            addView(input)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Tally marks ----------

    private fun tallyScreen(): View {
        // Tally sticks: every fifth one crosses out the previous four. We draw them with symbols.
        val count = EditText(this).apply { hint = "How many"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("", 18f)
        val draw = Button(this).apply { text = "Draw the sticks" }
        draw.setOnClickListener {
            val n = (count.text.toString().toIntOrNull() ?: 0).coerceIn(0, 200)
            val sb = StringBuilder()
            var i = 0
            while (i < n) {
                val group = minOf(5, n - i)
                if (group == 5) {
                    sb.append("𝍸 ")
                } else {
                    sb.append("|".repeat(group)).append(" ")
                }
                i += group
            }
            out.text = sb.toString().trim().ifEmpty { "No sticks" }
        }
        return scroll(column().apply {
            addView(count)
            addView(draw)
            addView(out)
        })
    }

    // ---------- Angle ----------

    private fun angleScreen(): View {
        // Degrees to radians and back. Pi here is honest, from Math, not from fifth grade.
        val input = EditText(this).apply { hint = "Degrees"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val calc = Button(this).apply { text = "To radians" }
        calc.setOnClickListener {
            val deg = input.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "%.5f rad".format(Math.toRadians(deg))
        }
        val back = Button(this).apply { text = "To degrees" }
        back.setOnClickListener {
            val rad = input.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "%.3f°".format(Math.toDegrees(rad))
        }
        return column().apply {
            addView(input)
            addView(calc)
            addView(back)
            addView(out)
        }
    }

    // ---------- Swap ----------

    private fun swapScreen(): View {
        // We swap two values through a third variable. No arithmetic, because arithmetic is a trick.
        val a = EditText(this).apply { hint = "a"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val b = EditText(this).apply { hint = "b"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val swap = Button(this).apply { text = "Swap" }
        swap.setOnClickListener {
            var x = a.text.toString()
            var y = b.text.toString()
            val tmp = x
            x = y
            y = tmp
            a.setText(x)
            b.setText(y)
            out.text = "Now a = $x, b = $y"
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(swap)
            addView(out)
        }
    }
}
