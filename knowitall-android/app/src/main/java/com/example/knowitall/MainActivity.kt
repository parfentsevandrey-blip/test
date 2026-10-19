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
 * Мистер Всезнайка: одно приложение вместо всех остальных.
 *
 * Архитектура: один файл, один объект, один Activity. Глобальное изменяемое состояние
 * для удобства. Всё делается на главном потоке, чтобы никто не сказал, что мы распараллелили.
 */
object Knowitall {
    lateinit var prefs: SharedPreferences
    var calcHistory = ""
    var notes = ""
    var contacts = "" // формат: "Имя=номер;Имя=номер;" — да, вместо базы данных
    var todos = "" // формат: "задача|0;задача|1;" (0 — не сделано, 1 — сделано)
    var steps = 0 // шаги, которые мы "сделали" сами
}

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var tabsRow: LinearLayout

    private val modules: List<Pair<String, () -> View>> = listOf(
        "Калькулятор" to { calculatorScreen() },
        "Звонилка" to { dialerScreen() },
        "Блокнот" to { notesScreen() },
        "Контакты" to { contactsScreen() },
        "Всезнайка" to { oracleScreen() },
        "Таймер" to { timerScreen() },
        "Конвертер" to { converterScreen() },
        "Дела" to { todoScreen() },
        "Пароли" to { passwordScreen() },
        "Погода" to { weatherScreen() },
        "Секундомер" to { stopwatchScreen() },
        "Кубик" to { diceScreen() },
        "Будильник" to { alarmScreen() },
        "Угадайка" to { guessScreen() },
        "Монетка" to { coinScreen() },
        "Шифр" to { cipherScreen() },
        "Переводчик" to { translatorScreen() },
        "Календарь" to { calendarScreen() },
        "Шагомер" to { pedometerScreen() },
        "Плеер" to { playerScreen() },
        "Слова" to { wordCountScreen() },
        "Цитаты" to { quotesScreen() },
        "Таблица" to { multiplyTableScreen() },
        "Проценты" to { percentScreen() },
        "Длина" to { lengthScreen() },
        "Эмодзи" to { emojiScreen() },
        "Двоичный" to { binaryScreen() },
        "Помодоро" to { pomodoroScreen() },
        "Римские" to { romanScreen() },
        "Имена" to { nameScreen() },
        "Гороскоп" to { horoscopeScreen() },
        "Лотерея" to { lotteryScreen() },
        "Квиз" to { quizScreen() },
        "Рецепты" to { recipesScreen() },
        "Сон" to { sleepScreen() },
        "Часы" to { clockScreen() },
        "Коды" to { asciiScreen() },
        "Палиндром" to { palindromeScreen() },
        "Факториал" to { factorialScreen() },
        "Простые" to { primesScreen() },
        "Комплимент" to { complimentScreen() },
        "Анекдот" to { jokeScreen() },
        "Шар" to { magicBallScreen() },
        "Дни" to { daysLeftScreen() },
        "Цвет" to { colorScreen() },
        "Рейтинг" to { ratingScreen() },
        "Лайфхак" to { lifehackScreen() },
        "Клики" to { clickCounterScreen() },
        "Вода" to { waterScreen() },
        "Дневник" to { diaryScreen() },
        "Крестики" to { ticTacToeScreen() },
        "Виселица" to { hangmanScreen() },
        "Элементы" to { elementsScreen() },
        "Градусы" to { temperatureScreen() },
        "Скорость" to { speedScreen() },
        "Площадь" to { areaScreen() },
        "Чаевые" to { tipScreen() },
        "Ритм" to { metronomeScreen() },
        "Ёлочка" to { treeScreen() },
        "Зарядка" to { exerciseScreen() },
        "Числа словами" to { numberWordsScreen() },
        "Квадраты" to { squaresScreen() },
        "Жребий" to { lotScreen() },
        "Шахматы" to { chessScreen() },
        "Ноты" to { notesMusicScreen() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Knowitall.prefs = getSharedPreferences("everything", MODE_PRIVATE)
        Knowitall.notes = Knowitall.prefs.getString("notes", "") ?: ""
        Knowitall.contacts = Knowitall.prefs.getString("contacts", "") ?: ""

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content = FrameLayout(this)

        // Вкладок много, поэтому они лежат в горизонтальной прокрутке, которая режет последнюю пополам
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
        // Каждый раз уничтожаем экран целиком и строим заново, даже если это тот же экран
        content.removeAllViews()
        content.addView(modules[index].second())
        Toast.makeText(this, modules[index].first, Toast.LENGTH_SHORT).show()
    }

    // ---------- Общие "утилиты" (вручную, без библиотек — так честнее) ----------

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

    // ---------- Калькулятор ----------

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
        Thread.sleep(120) // "для ощущения кнопки"; на главном потоке, конечно
        when (key) {
            "C" -> Knowitall.calcHistory = ""
            "=" -> Toast.makeText(this, "Я и так всё показываю 🤓", Toast.LENGTH_SHORT).show()
            else -> Knowitall.calcHistory += key
        }
    }

    private fun liveResult(): String {
        if (Knowitall.calcHistory.isEmpty()) return ""
        // Каждый раз пересчитываем ВСЮ историю с нуля, а не только последнее выражение
        return try {
            val v = evaluate(Knowitall.calcHistory.replace(" ", ""))
            if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        } catch (e: Exception) {
            "не знаю 🤷"
        }
    }

    private fun replaceMatch(s: String, m: MatchResult, with: String): String =
        s.substring(0, m.range.first) + with + s.substring(m.range.last + 1)

    private fun evaluate(expr: String): Double {
        var s = expr
        // Скобки: раскрываем самые внутренние, пока они есть
        val inner = Regex("\\(([^()]*)\\)")
        while (inner.containsMatchIn(s)) {
            val m = inner.find(s)!!
            s = replaceMatch(s, m, evaluate(m.groupValues[1]).toString())
        }
        // Умножение и деление: по одной паре за раз, строку каждый раз переписываем
        val mul = Regex("(-?\\d+\\.?\\d*)([*/])(-?\\d+\\.?\\d*)")
        while (true) {
            val m = mul.find(s) ?: break
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[3].toDouble()
            s = replaceMatch(s, m, (if (m.groupValues[2] == "*") a * b else a / b).toString())
        }
        // Сложение и вычитание — то же самое
        val add = Regex("(-?\\d+\\.?\\d*)([+-])(-?\\d+\\.?\\d*)")
        while (true) {
            val m = add.find(s) ?: break
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[3].toDouble()
            s = replaceMatch(s, m, (if (m.groupValues[2] == "+") a + b else a - b).toString())
        }
        return s.toDouble()
    }

    // ---------- Звонилка ----------

    private fun dialerScreen(): View {
        val number = EditText(this).apply {
            hint = "Номер"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val call = Button(this).apply { text = "Позвонить" }
        call.setOnClickListener {
            // Ждём 300 мс, пока кнопка "сообразит", что её нажали
            val until = System.currentTimeMillis() + 300
            while (System.currentTimeMillis() < until) { /* занято ожиданием */ }
            // Сама не звонит. Открывает настоящую звонилку. Заменяет ровно себя.
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.text)))
        }
        return column().apply {
            addView(label("Звонилка: откроет настоящую звонилку, зато честно"))
            addView(number)
            addView(call)
        }
    }

    // ---------- Блокнот ----------

    private fun notesScreen(): View {
        val editor = EditText(this).apply {
            setText(Knowitall.notes)
            hint = "Пишите сюда"
            minLines = 8
            gravity = Gravity.TOP
        }
        val search = EditText(this).apply { hint = "Поиск по заметкам" }
        val found = label("")

        editor.onChange { text ->
            Knowitall.notes = text
            // Весь блокнот целиком, синхронно, на каждый символ. Плюс бэкап — навсегда.
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
        // Линейный поиск, посимвольно, с копированием хвоста строки на каждом шаге
        for (i in 0 until Knowitall.notes.length) {
            if (Knowitall.notes.substring(i).startsWith(q) && q.isNotEmpty()) {
                out += Knowitall.notes.substring(i, minOf(i + 20, Knowitall.notes.length)).replace("\n", " ") + "\n"
            }
        }
        return out.ifEmpty { "ничего не нашёл" }
    }

    // ---------- Контакты ----------

    private fun contactsScreen(): View {
        val name = EditText(this).apply { hint = "Имя" }
        val number = EditText(this).apply {
            hint = "Номер"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val list = label("")

        fun render() {
            list.text = sortedContacts().joinToString("\n") { "${it.first}: ${it.second}" }
        }

        val add = Button(this).apply {
            text = "Добавить"
            setOnClickListener {
                // Дубликаты не проверяем: это же "супер". Пишем всю таблицу целиком.
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
        // Разбираем строку заново при каждом обращении. Кэш — для слабых.
        val rows = Knowitall.contacts
            .split(";")
            .filter { it.contains("=") }
            .map { val p = it.split("="); p[0] to p[1] }
            .toMutableList()
        // Пузырьковая сортировка, потому что сортировка — это просто
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

    // ---------- Всезнайка ----------

    private fun oracleScreen(): View {
        val question = EditText(this).apply { hint = "Задайте вопрос" }
        val answer = label("Задайте вопрос. Я знаю всё.")
        val ask = Button(this).apply {
            text = "Спросить"
            setOnClickListener {
                answer.text = "Думаю..."
                // Вычисляем ответ на главный вопрос жизни полным перебором. Ответ известен заранее.
                var x = 0
                while (x != 42) {
                    x++
                    Thread.sleep(50)
                }
                // Экран обновится только после выхода из обработчика, так что "Думаю..." никто не увидит
                answer.text = "Ответ на «${question.text}»: $x. Уверенность 100%. Проверял? Нет."
            }
        }
        return column().apply {
            addView(question)
            addView(ask)
            addView(answer)
        }
    }

    // ---------- Таймер ----------

    private fun timerScreen(): View {
        val seconds = EditText(this).apply {
            hint = "Секунд"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val display = label("Таймер не запущен")
        val start = Button(this).apply { text = "Старт" }
        start.setOnClickListener {
            var left = seconds.text.toString().toIntOrNull() ?: 10
            // Отсчёт на главном потоке. Экран обновится только тогда, когда отсчёт закончится
            while (left > 0) {
                Thread.sleep(1000)
                left--
                display.text = "Осталось: $left с"
            }
            Toast.makeText(this, "Динь! (но вы это не услышите, пока мы спим)", Toast.LENGTH_LONG).show()
        }
        return column().apply {
            addView(seconds)
            addView(start)
            addView(display)
        }
    }

    // ---------- Конвертер ----------

    private fun converterScreen(): View {
        // Курсы зашиты в код. Обновляются вместе с релизами, то есть никогда.
        val rates = mapOf("USD" to 90.0, "EUR" to 98.0, "CNY" to 12.5)
        val amount = EditText(this).apply {
            hint = "Сумма в рублях"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val result = label("")
        val grid = GridLayout(this).apply { columnCount = 3 }
        rates.forEach { (currency, rate) ->
            grid.addView(Button(this).apply {
                text = currency
                setOnClickListener {
                    val rub = amount.text.toString().toDoubleOrNull() ?: 0.0
                    // Считаем через строку, потому что так точнее (нет)
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

    // ---------- Дела ----------

    private fun todoScreen(): View {
        val input = EditText(this).apply { hint = "Новое дело" }
        val list = label("")

        fun render() {
            // Каждый раз разбираем всю строку и собираем текст заново
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
            text = "Добавить"
            setOnClickListener {
                Knowitall.todos += "${input.text}|0;"
                input.setText("")
                save()
            }
        }
        val toggle = Button(this).apply {
            text = "Отметить первое"
            setOnClickListener {
                // Меняем только первую запись, потому что остальные — проблемы будущего
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
            text = "Очистить всё"
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

    // ---------- Пароли ----------

    private fun passwordScreen(): View {
        val length = EditText(this).apply {
            hint = "Длина"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val result = label("")
        val generate = Button(this).apply { text = "Сгенерировать" }
        generate.setOnClickListener {
            // java.util.Random, засеянный временем. Криптостойко? Нет. Зато быстро.
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

    // ---------- Погода ----------

    private fun weatherScreen(): View {
        val city = EditText(this).apply { hint = "Город" }
        val result = label("")
        val show = Button(this).apply { text = "Прогноз" }
        show.setOnClickListener {
            // Данные берутся из головы. Сетевой доступ — это для других приложений.
            val temp = Random(System.currentTimeMillis()).nextInt(40) - 20
            result.text = "${city.text.ifEmpty { "Здесь" }}: $temp°C, ${listOf("солнечно", "дождь", "туман", "ветер").random()}"
        }
        return column().apply {
            addView(city)
            addView(show)
            addView(result)
        }
    }

    // ---------- Секундомер ----------

    private fun stopwatchScreen(): View {
        val display = label("0.00 с", 36f)
        var ticks = 0
        val start = Button(this).apply { text = "Старт (10 с)" }
        start.setOnClickListener {
            // Считаем "тики" сами, без таймера. Ровно 1000 мс на тик, кроме случаев, когда не ровно
            ticks = 0
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end) {
                ticks++
            }
            display.text = "%.2f с".format(ticks / 1000.0)
        }
        return column().apply {
            addView(display)
            addView(start)
        }
    }

    // ---------- Кубик ----------

    private fun diceScreen(): View {
        val count = EditText(this).apply {
            hint = "Сколько кубиков"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val result = label("")
        val roll = Button(this).apply { text = "Бросить" }
        roll.setOnClickListener {
            val n = count.text.toString().toIntOrNull() ?: 1
            val rolls = (1..n).map { Random().nextInt(6) + 1 }
            // Сумму считаем заново в цикле, хотя её можно было взять из map
            var sum = 0
            for (r in rolls) sum += r
            result.text = "$rolls → сумма $sum"
        }
        return column().apply {
            addView(count)
            addView(roll)
            addView(result)
        }
    }

    // ---------- Будильник ----------

    private fun alarmScreen(): View {
        val time = EditText(this).apply { hint = "Время (ЧЧ:ММ)" }
        val status = label("Будильник не поставлен")
        val set = Button(this).apply { text = "Поставить" }
        set.setOnClickListener {
            // Будильник — это просто проверка времени в цикле на главном потоке
            val target = time.text.toString()
            status.text = "Жду $target..."
            while (true) {
                val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                if (now == target) break
                Thread.sleep(1000)
            }
            Toast.makeText(this, "Вставай!", Toast.LENGTH_LONG).show()
        }
        return column().apply {
            addView(time)
            addView(set)
            addView(status)
        }
    }

    // ---------- Угадайка ----------

    private fun guessScreen(): View {
        // Загадываем число заново при каждом открытии вкладки, так что выиграть тоже не получится
        val secret = Random().nextInt(100) + 1
        var tries = 0
        val guess = EditText(this).apply {
            hint = "Число от 1 до 100"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val hintLabel = label("Я загадал число")
        val check = Button(this).apply { text = "Проверить" }
        check.setOnClickListener {
            val n = guess.text.toString().toIntOrNull() ?: 0
            tries++
            hintLabel.text = when {
                n < secret -> "Больше (попытка $tries)"
                n > secret -> "Меньше (попытка $tries)"
                else -> "Угадал за $tries! Теперь проверим, что это не случайность."
            }
        }
        return column().apply {
            addView(guess)
            addView(check)
            addView(hintLabel)
        }
    }

    // ---------- Монетка ----------

    private fun coinScreen(): View {
        // Счётчики храним в обычной карте и пересоздаём её при каждом открытии вкладки
        val tally = mutableMapOf("Орёл" to 0, "Решка" to 0)
        val result = label("Пока не подбрасывали")
        val stats = label("Орёл: 0, Решка: 0")
        val flip = Button(this).apply { text = "Подбросить" }
        flip.setOnClickListener {
            // Подбрасываем 50 раз подряд, чтобы монетка "устала" и выпала честно
            var last = ""
            repeat(50) {
                last = if (Random().nextBoolean()) "Орёл" else "Решка"
                tally[last] = (tally[last] ?: 0) + 1
            }
            result.text = "Выпало: $last"
            stats.text = "Орёл: ${tally["Орёл"]}, Решка: ${tally["Решка"]}"
        }
        return column().apply {
            addView(flip)
            addView(result)
            addView(stats)
        }
    }

    // ---------- Шифр ----------

    private fun cipherScreen(): View {
        val text = EditText(this).apply { hint = "Текст (латиница)" }
        val shift = EditText(this).apply {
            hint = "Сдвиг"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val go = Button(this).apply { text = "Зашифровать" }
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
        // Буквы, которые не из a–z, не трогаем. Кириллица тоже не трогается: это не баг, а принцип.
        return s.map { c ->
            if (c in 'a'..'z') 'a' + (c - 'a' + k) % 26 else c
        }.joinToString("")
    }

    // ---------- Переводчик ----------

    private fun translatorScreen(): View {
        // Словарь на четыре слова. Остальное переводится как есть, зато с достоинством.
        val dictionary = listOf("кот" to "cat", "дом" to "house", "собака" to "dog", "вода" to "water")
        val input = EditText(this).apply { hint = "Слово по-русски" }
        val out = label("")
        val translate = Button(this).apply { text = "Перевести" }
        translate.setOnClickListener {
            val w = input.text.toString().trim().lowercase()
            // Ищем слово линейно, каждый раз заново, и не кэшируем, потому что словарь маленький
            out.text = dictionary.firstOrNull { it.first == w }?.second
                ?: "$w (не знаю, но звучит красиво)"
        }
        return column().apply {
            addView(input)
            addView(translate)
            addView(out)
        }
    }

    // ---------- Календарь ----------

    private fun calendarScreen(): View {
        val month = EditText(this).apply {
            hint = "Месяц (1–12)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val show = Button(this).apply { text = "Показать дни" }
        show.setOnClickListener {
            val m = month.text.toString().toIntOrNull() ?: 1
            // Дни в месяцах зашиты руками. Високосный год не поддерживается, потому что он нам не нужен.
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

    // ---------- Шагомер ----------

    private fun pedometerScreen(): View {
        Knowitall.steps = Knowitall.prefs.getInt("steps", 0)
        val total = label("Шагов сегодня: ${Knowitall.steps}", 20f)
        val walk = Button(this).apply { text = "Сделать 100 шагов" }
        walk.setOnClickListener {
            // Шаги "делаем" вручную: 100 раз спим по 20 мс на главном потоке, чтобы было видно усилие
            repeat(100) {
                Thread.sleep(20)
                Knowitall.steps++
            }
            // Сохраняем счётчик синхронно, как и весь остальной блокнот
            Knowitall.prefs.edit().putInt("steps", Knowitall.steps).commit()
            total.text = "Шагов сегодня: ${Knowitall.steps}"
        }
        return column().apply {
            addView(total)
            addView(walk)
        }
    }

    // ---------- Плеер ----------

    private fun playerScreen(): View {
        // Плейлист из четырёх треков, все они существуют только в воображении
        val tracks = listOf("Тишина", "Шум холодильника", "Тик часов", "Ничего")
        var index = 0
        val now = label("▶ ${tracks[index]}", 20f)
        val prev = Button(this).apply { text = "Назад" }
        val next = Button(this).apply { text = "Вперёд" }
        prev.setOnClickListener {
            index = (index - 1 + tracks.size) % tracks.size
            now.text = "▶ ${tracks[index]}  (играет в вашей голове)"
        }
        next.setOnClickListener {
            index = (index + 1) % tracks.size
            now.text = "▶ ${tracks[index]}  (играет в вашей голове)"
        }
        return column().apply {
            addView(now)
            addView(prev)
            addView(next)
        }
    }

    // ---------- Счётчик слов ----------

    private fun wordCountScreen(): View {
        val input = EditText(this).apply {
            hint = "Пишите, считаем"
            minLines = 5
            gravity = Gravity.TOP
        }
        val stats = label("")
        input.onChange { text ->
            // Всё пересчитываем с нуля на каждый символ, разными проходами по строке
            val words = text.trim().split(" ").filter { it.isNotEmpty() }.size
            val letters = text.count { it.isLetter() }
            var spaces = 0
            for (c in text) {
                if (c == ' ') spaces++
            }
            val vowels = text.count { it.lowercaseChar() in "аеёиоуыэюяaeiouy" }
            stats.text = "Слов: $words\nБукв: $letters\nПробелов: $spaces\nГласных: $vowels"
        }
        return column().apply {
            addView(input)
            addView(stats)
        }
    }

    // ---------- Цитаты ----------

    private fun quotesScreen(): View {
        // Цитаты приписаны случайным людям. Проверять авторство мы не будем.
        val quotes = listOf(
            "Знание — сила. — Кто-то очень уверенный",
            "Всё, что можно сделать завтра, лучше не делать. — Лентяй Андреевич",
            "Главное — не спешить, но спешить успеть. — Усталый философ",
            "Жизнь коротка, а кнопки на телефоне мелкие. — Неизвестный пользователь"
        )
        val quote = label(quotes[0], 18f)
        val next = Button(this).apply { text = "Другая цитата" }
        next.setOnClickListener {
            // Выбираем случайную, и она может повториться. Это вдохновляет.
            quote.text = quotes[Random().nextInt(quotes.size)]
        }
        return column().apply {
            addView(quote)
            addView(next)
        }
    }

    // ---------- Таблица умножения ----------

    private fun multiplyTableScreen(): View {
        val n = EditText(this).apply {
            hint = "Какую таблицу"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val show = Button(this).apply { text = "Показать" }
        show.setOnClickListener {
            val k = n.text.toString().toIntOrNull() ?: 2
            // Строку собираем конкатенацией в цикле, а не StringBuilder: так медленнее и веселее
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

    // ---------- Проценты ----------

    private fun percentScreen(): View {
        val base = EditText(this).apply {
            hint = "Число"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val pct = EditText(this).apply {
            hint = "Процент"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Посчитать" }
        calc.setOnClickListener {
            val b = base.text.toString().toDoubleOrNull() ?: 0.0
            val p = pct.text.toString().toDoubleOrNull() ?: 0.0
            // Процент считаем через три промежуточных переменной, потому что так понятнее (нет)
            val fraction = p / 100.0
            val part = b * fraction
            val total = b + part
            out.text = "$p% от $b = %.2f\nИтого: %.2f".format(part, total)
        }
        return column().apply {
            addView(base)
            addView(pct)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Длина ----------

    private fun lengthScreen(): View {
        // Единицы зашиты в код. Метр — основная единица, всё остальное — его родственники.
        val toMeters = mapOf("м" to 1.0, "км" to 1000.0, "см" to 0.01, "мм" to 0.001, "миля" to 1609.34)
        val value = EditText(this).apply {
            hint = "Значение"
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
                    // Переводим во все единицы, кроме той, из которой пришли. Её тоже перечисляем.
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

    // ---------- Эмодзи ----------

    private fun emojiScreen(): View {
        // Эмодзи подбираются по настроению, которое мы определяем по длине строки
        val mood = EditText(this).apply { hint = "Как вы?" }
        val face = label("🙂", 64f)
        val check = Button(this).apply { text = "Определить" }
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

    // ---------- Двоичный ----------

    private fun binaryScreen(): View {
        val number = EditText(this).apply {
            hint = "Число в десятичной"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val convert = Button(this).apply { text = "В двоичную" }
        convert.setOnClickListener {
            var n = number.text.toString().toIntOrNull() ?: 0
            // Делим на два вручную и собираем цифры с конца, потому что toString(2) — это читерство
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

    // ---------- Помодоро ----------

    private fun pomodoroScreen(): View {
        // Классическая техника: 25 минут работы, 5 минут отдыха. Здесь — 25 и 5 секунд, для скорости.
        val phase = label("Готовы работать?", 20f)
        val cycles = label("Завершено циклов: 0")
        var done = 0
        val start = Button(this).apply { text = "Начать цикл" }
        start.setOnClickListener {
            phase.text = "Работаем..."
            repeat(25) { Thread.sleep(1000) }
            phase.text = "Отдыхаем..."
            repeat(5) { Thread.sleep(1000) }
            done++
            // Счётчик обновляем только здесь, потому что главный поток занят сном
            phase.text = "Готово"
            cycles.text = "Завершено циклов: $done"
        }
        return column().apply {
            addView(phase)
            addView(cycles)
            addView(start)
        }
    }

    // ---------- Римские цифры ----------

    private fun romanScreen(): View {
        val number = EditText(this).apply {
            hint = "Число 1–3999"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val convert = Button(this).apply { text = "В римские" }
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
        // Таблица замен зашита вручную. Ноль римлян не знал, поэтому пишем «не знаю».
        if (value <= 0) return "не знаю 🤷"
        val table = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var n = value
        var result = ""
        // Идём по таблице сверху вниз и вычитаем, пока влезает
        for ((arabic, roman) in table) {
            while (n >= arabic) {
                result += roman
                n -= arabic
            }
        }
        return result
    }

    // ---------- Генератор имён ----------

    private fun nameScreen(): View {
        // Имена собраны из трёх списков. Комбинации почти всегда звучат странно.
        val firsts = listOf("Иван", "Мария", "Глеб", "Ольга", "Тимур")
        val lasts = listOf("Смирнов", "Кузнецова", "Попов", "Ковалёва")
        val nicks = listOf("Кабачок", "Ёжик", "Тапок", "Слон")
        val result = label("Нажмите кнопку", 20f)
        val roll = Button(this).apply { text = "Придумать" }
        roll.setOnClickListener {
            val rnd = Random()
            // Три независимых броска, потому что один бросок — это ленивость
            val name = firsts[rnd.nextInt(firsts.size)]
            val surname = lasts[rnd.nextInt(lasts.size)]
            val nick = nicks[rnd.nextInt(nicks.size)]
            result.text = "$name $surname («$nick»)"
        }
        return column().apply {
            addView(result)
            addView(roll)
        }
    }

    // ---------- Гороскоп ----------

    private fun horoscopeScreen(): View {
        // Знаки зодиака и предсказания зашиты в код. Звёзды не консультировались.
        val signs = listOf(
            "Овен", "Телец", "Близнецы", "Рак", "Лев", "Дева",
            "Весы", "Скорпион", "Стрелец", "Козерог", "Водолей", "Рыбы"
        )
        val predictions = listOf(
            "Сегодня всё получится, если не начинать.",
            "Удача придёт, но не в ту дверь.",
            "Вам помогут незнакомцы из чата.",
            "Лучше промолчать, даже если очень хочется."
        )
        val picker = EditText(this).apply { hint = "Ваш знак (номер 1–12)" }
        val out = label("Выберите знак")
        val read = Button(this).apply { text = "Прочитать" }
        read.setOnClickListener {
            val index = (picker.text.toString().toIntOrNull() ?: 1).coerceIn(1, 12) - 1
            // Предсказание выбираем не по знаку, а по случайному числу. Звёзды ленивы.
            val prediction = predictions[Random().nextInt(predictions.size)]
            out.text = "${signs[index]}: $prediction"
        }
        return column().apply {
            addView(picker)
            addView(read)
            addView(out)
        }
    }

    // ---------- Лотерея ----------

    private fun lotteryScreen(): View {
        // Лотерея на шесть номеров из сорока девяти. Выигрыш, честно, маловероятен.
        val ticket = label("Ваш билет: ещё не куплен", 18f)
        val buy = Button(this).apply { text = "Купить билет" }
        val draw = Button(this).apply { text = "Тянуть тираж" }
        val numbers = mutableListOf<Int>()
        buy.setOnClickListener {
            numbers.clear()
            // Выбираем номера, пока не наберём шесть уникальных. Проверку уникальности делаем линейно.
            val rnd = Random()
            while (numbers.size < 6) {
                val n = rnd.nextInt(49) + 1
                if (!numbers.contains(n)) numbers.add(n)
            }
            ticket.text = "Ваш билет: ${numbers.sorted().joinToString(" ")}"
        }
        draw.setOnClickListener {
            if (numbers.isEmpty()) {
                Toast.makeText(this, "Сначала купите билет", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val winning = (1..49).shuffled(Random(System.currentTimeMillis())).take(6)
            // Сравниваем вручную двойным циклом, потому что множества — это для слабых
            var matches = 0
            for (a in numbers) {
                for (b in winning) {
                    if (a == b) matches++
                }
            }
            ticket.text = "Тираж: ${winning.sorted().joinToString(" ")}\nСовпало: $matches"
        }
        return column().apply {
            addView(ticket)
            addView(buy)
            addView(draw)
        }
    }

    // ---------- Квиз ----------

    private fun quizScreen(): View {
        // Вопросы зашиты в список троек: текст, варианты, номер правильного
        val questions = listOf(
            Triple("Сколько будет 2 + 2?", listOf("3", "4", "22"), 1),
            Triple("Столица России?", listOf("Москва", "Пекин", "Париж"), 0),
            Triple("Сколько дней в обычном году?", listOf("360", "365", "400"), 1)
        )
        var current = 0
        var score = 0
        val question = label(questions[current].first, 18f)
        val answers = (0..2).map { i -> Button(this).apply { text = "…" } }
        val status = label("Счёт: 0")

        fun show() {
            val (text, options, _) = questions[current]
            question.text = text
            answers.forEachIndexed { i, button ->
                button.text = options.getOrElse(i) { "—" }
            }
        }

        answers.forEachIndexed { i, button ->
            button.setOnClickListener {
                // Проверяем ответ и сразу переходим к следующему, даже если вопросов больше нет
                if (i == questions[current].third) score++
                current = (current + 1) % questions.size
                status.text = "Счёт: $score"
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

    // ---------- Рецепты ----------

    private fun recipesScreen(): View {
        // Рецепты короткие и опасные. Пропорции не проверялись.
        val recipes = mapOf(
            "Чай" to "Вода, пакетик, подождать до кипения",
            "Бутерброд" to "Хлеб, масло, всё остальное на ваше усмотрение",
            "Омлет" to "Яйца, сковорода, огонь, терпение"
        )
        val pick = EditText(this).apply { hint = "Название блюда" }
        val out = label("")
        val find = Button(this).apply { text = "Найти рецепт" }
        find.setOnClickListener {
            val key = pick.text.toString().trim()
            // Ищем точное совпадение, а если его нет — пробуем все рецепты подряд по подстроке
            out.text = recipes[key]
                ?: recipes.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value
                ?: "Такого блюда нет. Закажите доставку."
        }
        return column().apply {
            addView(pick)
            addView(find)
            addView(out)
        }
    }

    // ---------- Сон ----------

    private fun sleepScreen(): View {
        // Записываем часы сна в общий блокнот одной строкой: это же удобно
        val hours = EditText(this).apply {
            hint = "Сколько часов спали"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val status = label("Записей пока нет")
        val save = Button(this).apply { text = "Записать" }
        save.setOnClickListener {
            val h = hours.text.toString().toDoubleOrNull() ?: 0.0
            Knowitall.notes += "\nСон: $h ч"
            Knowitall.prefs.edit().putString("notes", Knowitall.notes).commit()
            // Считаем средний сон по всему блокноту заново: ищем все строки со словом «Сон»
            val entries = Knowitall.notes.split("\n").filter { it.startsWith("Сон:") }
            var total = 0.0
            for (e in entries) {
                total += e.removePrefix("Сон:").removeSuffix("ч").trim().removeSuffix("ч").toDoubleOrNull() ?: 0.0
            }
            val avg = if (entries.isEmpty()) 0.0 else total / entries.size
            status.text = "Записей: ${entries.size}, в среднем %.1f ч".format(avg)
        }
        return column().apply {
            addView(hours)
            addView(save)
            addView(status)
        }
    }

    // ---------- Часы ----------

    private fun clockScreen(): View {
        val face = label("", 48f)
        val refresh = Button(this).apply { text = "Который час?" }
        refresh.setOnClickListener {
            // Формат времени собираем вручную из часов, минут и секунд, чтобы не подключать форматтеры
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

    // ---------- Коды ----------

    private fun asciiScreen(): View {
        val letter = EditText(this).apply { hint = "Один символ" }
        val out = label("")
        val look = Button(this).apply { text = "Код символа" }
        look.setOnClickListener {
            val c = letter.text.toString().firstOrNull()
            // Берём первый символ, а если строка пустая — показываем код пробела. Пробел тоже символ.
            out.text = if (c == null) "Пусто, код пробела: ${' '.code}" else "'$c' → ${c.code}"
        }
        return column().apply {
            addView(letter)
            addView(look)
            addView(out)
        }
    }

    // ---------- Палиндром ----------

    private fun palindromeScreen(): View {
        val word = EditText(this).apply { hint = "Слово или фраза" }
        val out = label("")
        val check = Button(this).apply { text = "Проверить" }
        check.setOnClickListener {
            // Убираем всё, кроме букв, и сравниваем с перевёрнутой строкой. Пробелы — наши враги.
            val clean = word.text.toString().lowercase().filter { it.isLetter() }
            var reversed = ""
            for (i in clean.length - 1 downTo 0) reversed += clean[i]
            out.text = if (clean.isNotEmpty() && clean == reversed) "Палиндром!" else "Нет, это просто слова"
        }
        return column().apply {
            addView(word)
            addView(check)
            addView(out)
        }
    }

    // ---------- Факториал ----------

    private fun factorialScreen(): View {
        val n = EditText(this).apply {
            hint = "Число от 0 до 20"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Посчитать" }
        calc.setOnClickListener {
            val k = (n.text.toString().toIntOrNull() ?: 0).coerceIn(0, 20)
            // Рекурсия без хвостовой оптимизации. Для 20 хватит, для 21 — уже Long переполнится, но мы тихие
            fun fact(x: Long): Long = if (x <= 1) 1 else x * fact(x - 1)
            out.text = "$k! = ${fact(k.toLong())}"
        }
        return column().apply {
            addView(n)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Простые числа ----------

    private fun primesScreen(): View {
        val limit = EditText(this).apply {
            hint = "До какого числа"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val find = Button(this).apply { text = "Найти простые" }
        find.setOnClickListener {
            val max = limit.text.toString().toIntOrNull() ?: 50
            val found = mutableListOf<Int>()
            // Делим каждое число на все меньшие: решето Эратосфена в гостях не бывает
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

    // ---------- Комплимент ----------

    private fun complimentScreen(): View {
        // Комплименты генерируются из трёх частей. Все они правда про вас, если вы поверите.
        val starts = listOf("Вы", "Ваш взгляд", "Ваша улыбка", "Ваш почерк")
        val middles = listOf("удивительно", "невероятно", "безупречно", "немного")
        val ends = listOf("умны", "оригинальны", "внимательны", "терпеливы")
        val text = label("Нажмите, чтобы получить комплимент", 18f)
        val give = Button(this).apply { text = "Комплимент" }
        give.setOnClickListener {
            val rnd = Random()
            text.text = "${starts[rnd.nextInt(starts.size)]} ${middles[rnd.nextInt(middles.size)]} ${ends[rnd.nextInt(ends.size)]}."
        }
        return column().apply {
            addView(text)
            addView(give)
        }
    }

    // ---------- Анекдот ----------

    private fun jokeScreen(): View {
        // Анекдоты хранятся в коде, потому что интернет — это для других приложений
        val jokes = listOf(
            "Программист пошёл в магазин. Жена сказала: купи хлеб. Он купил хлеб.",
            "Почему Android не ест? Потому что его никто не кормил обновлениями.",
            "Сколько нужно багов, чтобы заменить лампочку? Это не баг, это фича."
        )
        val joke = label("Нажмите, чтобы посмеяться", 18f)
        val next = Button(this).apply { text = "Ещё" }
        next.setOnClickListener {
            joke.text = jokes[Random().nextInt(jokes.size)]
        }
        return column().apply {
            addView(joke)
            addView(next)
        }
    }

    // ---------- Магический шар ----------

    private fun magicBallScreen(): View {
        // Ответы не зависят от вопроса. Вопрос можно не задавать, ответ всё равно придёт.
        val answers = listOf(
            "Да", "Нет", "Возможно", "Спросите позже", "Определённо", "Не рассчитывайте",
            "Звёзды говорят да", "Мне нужно подумать", "Конечно", "Ни за что"
        )
        val question = EditText(this).apply { hint = "Вопрос (необязательно)" }
        val out = label("Шар молчит")
        val ask = Button(this).apply { text = "Спросить шар" }
        ask.setOnClickListener {
            val pick = answers[Random().nextInt(answers.size)]
            // Вопрос в ответ не вставляем: он нам не нужен. Но пустую строку на всякий случай проверим.
            out.text = if (question.text.isEmpty()) pick else "$pick (но вопрос был: ${question.text})"
        }
        return column().apply {
            addView(question)
            addView(ask)
            addView(out)
        }
    }

    // ---------- Дни до события ----------

    private fun daysLeftScreen(): View {
        // Даты событий зашиты в код. Новые события добавляются релизами, то есть никогда.
        val events = mapOf(
            "Новый год" to 1,
            "Лето" to 6,
            "Осень" to 9
        )
        val out = label("Выберите событие")
        val buttons = GridLayout(this).apply { columnCount = 3 }
        events.forEach { (name, month) ->
            buttons.addView(Button(this).apply {
                text = name
                setOnClickListener {
                    // Считаем дни грубо: месяц умножаем на тридцать и вычитаем текущий день года
                    val today = (System.currentTimeMillis() / 86_400_000L % 365).toInt()
                    val target = month * 30
                    val left = ((target - today) % 365 + 365) % 365
                    out.text = "До «$name» примерно $left дн."
                }
            })
        }
        return column().apply {
            addView(buttons)
            addView(out)
        }
    }

    // ---------- Цвет ----------

    private fun colorScreen(): View {
        // Цвет генерируется случайными байтами. Контраст с фоном не проверяем.
        val swatch = label("", 48f)
        val reroll = Button(this).apply { text = "Другой цвет" }
        reroll.setOnClickListener {
            val rnd = Random()
            val r = rnd.nextInt(256)
            val g = rnd.nextInt(256)
            val b = rnd.nextInt(256)
            val hex = "#%02X%02X%02X".format(r, g, b)
            swatch.text = hex
            // Цвет фона ставим на саму надпись, и она может стать невидимой. Это тоже дизайн.
            swatch.setBackgroundColor(android.graphics.Color.rgb(r, g, b))
        }
        return column().apply {
            addView(swatch)
            addView(reroll)
        }
    }

    // ---------- Рейтинг ----------

    private fun ratingScreen(): View {
        // Рейтинг приложений, которые мы "проверили". Проверка заключалась в том, что мы на них смотрели.
        val apps = listOf(
            "Калькулятор" to 3,
            "Блокнот" to 2,
            "Звонилка" to 1,
            "Всезнайка" to 5
        )
        val list = label("")
        val sort = Button(this).apply { text = "Отсортировать по оценке" }
        sort.setOnClickListener {
            // Сортируем вставками вручную, потому что встроенный sortedBy слишком быстрый
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

    // ---------- Лайфхаки ----------

    private fun lifehackScreen(): View {
        // Лайфхаки проверены на одном телефоне, в одну дату, и не повторялись
        val hacks = listOf(
            "Чтобы не терять заряд, выключите экран.",
            "Чтобы найти телефон, позвоните себе с другого.",
            "Чтобы вспомнить пароль, введите наугад.",
            "Чтобы ускорить приложение, закройте все остальные."
        )
        var index = 0
        val hack = label(hacks[index], 18f)
        val next = Button(this).apply { text = "Следующий лайфхак" }
        next.setOnClickListener {
            // Идём по кругу, но индекс сдвигаем на случайное число, чтобы было интереснее
            index = (index + Random().nextInt(hacks.size) + 1) % hacks.size
            hack.text = hacks[index]
        }
        return column().apply {
            addView(hack)
            addView(next)
        }
    }

    // ---------- Счётчик кликов ----------

    private fun clickCounterScreen(): View {
        // Счётчик хранится в SharedPreferences и читается заново при каждом открытии вкладки
        var clicks = Knowitall.prefs.getInt("clicks", 0)
        val count = label("Кликов: $clicks", 36f)
        val tap = Button(this).apply { text = "Нажми меня" }
        tap.setOnClickListener {
            clicks++
            // Записываем на каждый клик синхронно, потому что асинхронность — это сложно
            Knowitall.prefs.edit().putInt("clicks", clicks).commit()
            count.text = "Кликов: $clicks"
        }
        val reset = Button(this).apply { text = "Сбросить" }
        reset.setOnClickListener {
            clicks = 0
            Knowitall.prefs.edit().putInt("clicks", 0).commit()
            count.text = "Кликов: $clicks"
        }
        return column().apply {
            addView(count)
            addView(tap)
            addView(reset)
        }
    }

    // ---------- Вода ----------

    private fun waterScreen(): View {
        // Стаканы считаем в том же SharedPreferences, что и клики. Общая шина — это экономия.
        var glasses = Knowitall.prefs.getInt("water", 0)
        val goal = 8
        val status = label("Выпито: $glasses из $goal", 20f)
        val drink = Button(this).apply { text = "Выпить стакан" }
        drink.setOnClickListener {
            glasses++
            Knowitall.prefs.edit().putInt("water", glasses).commit()
            // Если норма достигнута, поздравляем повторно, пока человек не перестанет нажимать
            status.text = if (glasses >= goal) "Норма выполнена! ($glasses)" else "Выпито: $glasses из $goal"
        }
        return column().apply {
            addView(status)
            addView(drink)
        }
    }

    // ---------- Дневник ----------

    private fun diaryScreen(): View {
        // Записи дневника пишутся в блокнот через разделитель «---». Читать его потом будет сложно.
        val entry = EditText(this).apply {
            hint = "Как прошёл день?"
            minLines = 4
            gravity = Gravity.TOP
        }
        val history = label("")
        fun render() {
            history.text = Knowitall.notes.split("---")
                .map { it.trim() }
                .filter { it.startsWith("Дневник:") }
                .joinToString("\n\n")
        }
        val save = Button(this).apply { text = "Записать день" }
        save.setOnClickListener {
            Knowitall.notes += "\n---\nДневник: ${entry.text}"
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

    // ---------- Крестики-нолики ----------

    private fun ticTacToeScreen(): View {
        // Поле 3×3 хранится строкой из девяти символов. Пустые клетки — пробелы, чтобы было стильно.
        var board = " ".repeat(9)
        var turn = 'X'
        val status = label("Ходит X")
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
                        winner != null -> "Победил $winner!"
                        !board.contains(' ') -> "Ничья"
                        else -> "Ходит $turn"
                    }
                }
            }
        }
        cells.forEach { grid.addView(it) }
        val reset = Button(this).apply { text = "Заново" }
        reset.setOnClickListener {
            board = " ".repeat(9)
            turn = 'X'
            cells.forEach { it.text = " " }
            status.text = "Ходит X"
        }
        return column().apply {
            addView(status)
            addView(grid)
            addView(reset)
        }
    }

    private fun winnerOf(b: String): Char? {
        // Все восемь линий перечисляем руками. Диагонали тоже.
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

    // ---------- Виселица ----------

    private fun hangmanScreen(): View {
        // Слово одно и то же для всех, потому что словарь из одного слова — это простота
        val secret = "кот"
        val guessed = mutableSetOf<Char>()
        var misses = 0
        val word = label("", 28f)
        val info = label("Ошибок: 0 из 6")
        fun render() {
            word.text = secret.map { if (it in guessed) it.toString() else "_" }.joinToString(" ")
            info.text = "Ошибок: $misses из 6"
        }
        val letter = EditText(this).apply { hint = "Буква" }
        val guess = Button(this).apply { text = "Угадать букву" }
        guess.setOnClickListener {
            val c = letter.text.toString().trim().lowercase().firstOrNull() ?: return@setOnClickListener
            letter.setText("")
            if (c in secret) guessed.add(c) else misses++
            render()
            if (misses >= 6) info.text = "Виселица. Слово было «$secret»"
            if (secret.all { it in guessed }) info.text = "Вы выиграли!"
        }
        render()
        return column().apply {
            addView(word)
            addView(letter)
            addView(guess)
            addView(info)
        }
    }

    // ---------- Элементы ----------

    private fun elementsScreen(): View {
        // Пять элементов таблицы Менделеева. Остальные ещё не открыты.
        val elements = mapOf(
            "H" to "Водород, номер 1",
            "O" to "Кислород, номер 8",
            "C" to "Углерод, номер 6",
            "Na" to "Натрий, номер 11",
            "Fe" to "Железо, номер 26"
        )
        val symbol = EditText(this).apply { hint = "Символ, например H" }
        val out = label("")
        val find = Button(this).apply { text = "Найти" }
        find.setOnClickListener {
            val key = symbol.text.toString().trim()
            out.text = elements[key] ?: elements.keys.firstOrNull { it.equals(key, ignoreCase = true) }
                ?.let { elements[it] }
                ?: "Такого элемента пока не открыли"
        }
        return column().apply {
            addView(symbol)
            addView(find)
            addView(out)
        }
    }

    // ---------- Температура ----------

    private fun temperatureScreen(): View {
        val celsius = EditText(this).apply {
            hint = "Градусы Цельсия"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val out = label("")
        val convert = Button(this).apply { text = "Перевести" }
        convert.setOnClickListener {
            val c = celsius.text.toString().toDoubleOrNull() ?: 0.0
            val f = c * 9 / 5 + 32
            val k = c + 273.15
            // Кельвин считаем отдельно, хотя он тоже от Цельсия. Никто не просил, но вдруг пригодится.
            out.text = "%.1f °F\n%.2f K".format(f, k)
        }
        return column().apply {
            addView(celsius)
            addView(convert)
            addView(out)
        }
    }

    // ---------- Скорость ----------

    private fun speedScreen(): View {
        val kmh = EditText(this).apply {
            hint = "Скорость, км/ч"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val convert = Button(this).apply { text = "Перевести" }
        convert.setOnClickListener {
            val v = kmh.text.toString().toDoubleOrNull() ?: 0.0
            // Метры в секунду: делим на три и шесть десятых. Округление приблизительное, как и всё здесь.
            out.text = "%.2f м/с\n%.2f миль/ч".format(v / 3.6, v / 1.609)
        }
        return column().apply {
            addView(kmh)
            addView(convert)
            addView(out)
        }
    }

    // ---------- Площадь ----------

    private fun areaScreen(): View {
        // Площадь прямоугольника и круга. Число пи округлено до трёх знаков, как в школе.
        val a = EditText(this).apply {
            hint = "Ширина или радиус"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val b = EditText(this).apply {
            hint = "Высота (для прямоугольника)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val rect = Button(this).apply { text = "Прямоугольник" }
        rect.setOnClickListener {
            val w = a.text.toString().toDoubleOrNull() ?: 0.0
            val h = b.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "Площадь: %.2f".format(w * h)
        }
        val circle = Button(this).apply { text = "Круг" }
        circle.setOnClickListener {
            val r = a.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "Площадь: %.2f".format(3.142 * r * r)
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(rect)
            addView(circle)
            addView(out)
        }
    }

    // ---------- Чаевые ----------

    private fun tipScreen(): View {
        val bill = EditText(this).apply {
            hint = "Счёт"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val people = EditText(this).apply {
            hint = "Человек за столом"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val split = Button(this).apply { text = "Разделить с чаевыми 10%" }
        split.setOnClickListener {
            val total = (bill.text.toString().toDoubleOrNull() ?: 0.0) * 1.1
            val n = (people.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
            // Делим на людей заново, потому что сумма может измениться от пересчёта
            out.text = "Каждый платит: %.2f".format(total / n)
        }
        return column().apply {
            addView(bill)
            addView(people)
            addView(split)
            addView(out)
        }
    }

    // ---------- Метроном ----------

    private fun metronomeScreen(): View {
        val bpm = EditText(this).apply {
            hint = "Ударов в минуту"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val beat = label("—", 48f)
        val play = Button(this).apply { text = "Играть четыре удара" }
        play.setOnClickListener {
            val interval = 60_000L / (bpm.text.toString().toLongOrNull() ?: 60L).coerceAtLeast(1L)
            // Считаем удары занятым ожиданием, потому что Thread.sleep слишком деликатен
            repeat(4) { i ->
                val until = System.currentTimeMillis() + interval
                while (System.currentTimeMillis() < until) { /* тик-так */ }
                beat.text = if (i % 2 == 0) "TICK" else "TOCK"
            }
        }
        return column().apply {
            addView(bpm)
            addView(play)
            addView(beat)
        }
    }

    // ---------- Ёлочка ----------

    private fun treeScreen(): View {
        // Ёлочка рисуется строками и пробелами. Размер задаём сами, но не больше двадцати.
        val size = EditText(this).apply {
            hint = "Высота (до 20)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("", 14f)
        val draw = Button(this).apply { text = "Нарисовать" }
        draw.setOnClickListener {
            val h = (size.text.toString().toIntOrNull() ?: 5).coerceIn(1, 20)
            val sb = StringBuilder()
            for (row in 1..h) {
                // Пробелы слева и звёздочки по центру. Центрирование — отдельное искусство.
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

    // ---------- Зарядка ----------

    private fun exerciseScreen(): View {
        // Пять упражнений по 10 секунд. Выполняются строго на главном потоке, поэтому смотреть на экран не нужно.
        val exercises = listOf("Присядания", "Наклоны", "Прыжки", "Растяжка", "Выдох")
        val status = label("Готовы?", 20f)
        val start = Button(this).apply { text = "Начать зарядку" }
        start.setOnClickListener {
            for (name in exercises) {
                status.text = "Делаем: $name"
                Thread.sleep(10_000)
            }
            status.text = "Отлично! Можно сесть на диван."
        }
        return column().apply {
            addView(status)
            addView(start)
        }
    }

    // ---------- Числа словами ----------

    private fun numberWordsScreen(): View {
        // Словарь от нуля до двадцати и десятки. Всё, что больше, мы не стали учить.
        val small = listOf(
            "ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
            "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать",
            "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
        )
        val tens = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
        val input = EditText(this).apply {
            hint = "Число от 0 до 99"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val say = Button(this).apply { text = "Словами" }
        say.setOnClickListener {
            val n = (input.text.toString().toIntOrNull() ?: 0).coerceIn(0, 99)
            out.text = if (n < 20) small[n] else {
                // Десятки и единицы склеиваем пробелом, а ноль в конце опускаем, чтобы не было «двадцать ноль»
                val unit = n % 10
                if (unit == 0) tens[n / 10] else "${tens[n / 10]} ${small[unit]}"
            }
        }
        return column().apply {
            addView(input)
            addView(say)
            addView(out)
        }
    }

    // ---------- Квадраты ----------

    private fun squaresScreen(): View {
        // Квадраты от одного до ста. Все посчитаны заново при каждом нажатии, кэш нам не нужен.
        val out = label("", 14f)
        val show = Button(this).apply { text = "Показать квадраты" }
        show.setOnClickListener {
            val sb = StringBuilder()
            for (i in 1..100) {
                // Квадрат считаем умножением в цикле, а не возведением в степень: степень слишком очевидна
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

    // ---------- Жребий ----------

    private fun lotScreen(): View {
        // Список вариантов вводится через запятую и разбирается заново при каждом броске
        val options = EditText(this).apply { hint = "Варианты через запятую" }
        val result = label("Пока не выбрано", 20f)
        val pick = Button(this).apply { text = "Выбрать" }
        pick.setOnClickListener {
            val items = options.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            result.text = if (items.isEmpty()) "Выбирать не из чего" else items[Random().nextInt(items.size)]
        }
        return column().apply {
            addView(options)
            addView(pick)
            addView(result)
        }
    }

    // ---------- Шахматы ----------

    private fun chessScreen(): View {
        // Проверяем, что клетка существует, и определяем её цвет. Фигуры пока не умеем, это следующая версия.
        val square = EditText(this).apply { hint = "Клетка, например e4" }
        val out = label("")
        val check = Button(this).apply { text = "Проверить клетку" }
        check.setOnClickListener {
            val s = square.text.toString().trim().lowercase()
            val file = s.getOrNull(0) ?: ' '
            val rank = s.getOrNull(1)?.digitToIntOrNull() ?: 0
            out.text = when {
                s.length != 2 -> "Так клетки не называют"
                file !in 'a'..'h' || rank !in 1..8 -> "За пределами доски"
                else -> {
                    // Цвет клетки: сумма номера столбца и строки, чётная — тёмная. Проверено на одной доске.
                    val fileIndex = file - 'a' + 1
                    val color = if ((fileIndex + rank) % 2 == 0) "тёмная" else "светлая"
                    "Клетка $s, $color"
                }
            }
        }
        return column().apply {
            addView(square)
            addView(check)
            addView(out)
        }
    }

    // ---------- Ноты ----------

    private fun notesMusicScreen(): View {
        // Ноты до-мажорной гаммы. Диезы и бемоли сюда не поместились.
        val scale = listOf("До", "Ре", "Ми", "Фа", "Соль", "Ля", "Си")
        val freq = mapOf("До" to 262, "Ре" to 294, "Ми" to 330, "Фа" to 349, "Соль" to 392, "Ля" to 440, "Си" to 494)
        val out = label("Нажмите ноту")
        val grid = GridLayout(this).apply { columnCount = 4 }
        scale.forEach { note ->
            grid.addView(Button(this).apply {
                text = note
                setOnClickListener {
                    // Звук не воспроизводим, поэтому просто называем частоту. Слушать — это уже к вам.
                    out.text = "$note: ${freq[note]} Гц"
                }
            })
        }
        return column().apply {
            addView(grid)
            addView(out)
        }
    }
}
