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
        "Ноты" to { notesMusicScreen() },
        "Гадалка" to { tarotScreen() },
        "Покупки" to { shoppingScreen() },
        "Бюджет" to { budgetScreen() },
        "BMI" to { bmiScreen() },
        "Пульс" to { pulseScreen() },
        "Книги" to { booksScreen() },
        "Фильмы" to { moviesScreen() },
        "Чек-лист" to { checklistScreen() },
        "Дроби" to { fractionsScreen() },
        "Футбол" to { footballScreen() },
        "Байты" to { bytesScreen() },
        "Бот" to { chatBotScreen() },
        "Истинность" to { truthTableScreen() },
        "Кофе" to { coffeeScreen() },
        "Лестница" to { stairsScreen() },
        "Стирка" to { laundryScreen() },
        "Мороз" to { frostScreen() },
        "Ускорение" to { accelerationScreen() },
        "Пропорции" to { proportionsScreen() },
        "Праздники" to { holidaysScreen() },
        "Слово дня" to { wordOfDayScreen() },
        "Даты" to { dateDiffScreen() },
        "Ом" to { ohmScreen() },
        "Плотность" to { densityScreen() },
        "КПД" to { efficiencyScreen() },
        "Калории" to { caloriesScreen() },
        "Неделя" to { weekdayScreen() },
        "Температура тела" to { bodyTempScreen() },
        "Пицца" to { pizzaScreen() },
        "Фибоначчи" to { fibonacciScreen() },
        "Пары" to { twinPrimesScreen() },
        "Секунды" to { secondsScreen() },
        "Корень" to { sqrtScreen() },
        "Вклад" to { depositScreen() },
        "Кредит" to { loanScreen() },
        "Смета" to { estimateScreen() },
        "Коробка" to { boxScreen() },
        "Периметр" to { perimeterScreen() },
        "Окружность" to { circleLengthScreen() },
        "Треугольник" to { triangleScreen() },
        "Цель" to { goalScreen() },
        "Серия" to { streakScreen() },
        "Чистка" to { brushingScreen() },
        "Пароль" to { passwordCheckScreen() },
        "Анаграмма" to { anagramScreen() },
        "Перевёртыш" to { reverseWordsScreen() },
        "Буквы" to { letterFrequencyScreen() },
        "Очки" to { diceScoreScreen() },
        "ROT13" to { rot13Screen() },
        "Морзе" to { morseScreen() },
        "Сумма цифр" to { digitSumScreen() },
        "Армстронг" to { armstrongScreen() },
        "Тройки" to { triplesScreen() },
        "Степень" to { powerScreen() },
        "НОД" to { gcdScreen() },
        "НОК" to { lcmScreen() },
        "Логарифм" to { logScreen() },
        "Синус" to { sineTableScreen() },
        "Рулетка" to { rouletteScreen() },
        "Наклейки" to { stickersScreen() },
        "Совершенные" to { perfectNumbersScreen() },
        "Скидка" to { discountScreen() },
        "Минуты" to { minutesScreen() },
        "Среднее" to { statsScreen() },
        "Чётность" to { parityScreen() },
        "Точка" to { distanceScreen() },
        "Лента" to { tallyScreen() },
        "Градус" to { angleScreen() },
        "Обмен" to { swapScreen() }
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

    // ---------- Гадалка ----------

    private fun tarotScreen(): View {
        // Колода из двенадцати карт. Значения карт не связаны с будущим, но звучат убедительно.
        val deck = listOf(
            "Маг", "Жрица", "Императрица", "Император", "Колесница", "Отшельник",
            "Колесо Фортуны", "Сила", "Повешенный", "Смерть", "Звезда", "Луна"
        )
        val meanings = mapOf(
            "Маг" to "Вы умеете больше, чем думаете.",
            "Жрица" to "Ответ ждёт вас в тишине.",
            "Смерть" to "Не пугайтесь, это просто конец вкладки.",
            "Луна" to "Что-то скрыто, но его можно найти в настройках."
        )
        val card = label("Карта ещё не вытянута", 20f)
        val meaning = label("")
        val draw = Button(this).apply { text = "Вытянуть карту" }
        draw.setOnClickListener {
            // Тасуем колоду заново перед каждым вытягиванием, иначе карты начинают обижаться
            val picked = deck.shuffled(Random()).first()
            card.text = picked
            meaning.text = meanings[picked] ?: "Значение этой карты мы не учили."
        }
        return column().apply {
            addView(card)
            addView(draw)
            addView(meaning)
        }
    }

    // ---------- Список покупок ----------

    private fun shoppingScreen(): View {
        // Список хранится в том же формате «строка|статус», что и дела, но мы сделали вид, что это новое
        var items = Knowitall.prefs.getString("shopping", "") ?: ""
        val input = EditText(this).apply { hint = "Что купить" }
        val list = label("")
        fun render() {
            list.text = items.split(";").filter { it.isNotEmpty() }.joinToString("\n") { "• $it" }
        }
        val add = Button(this).apply { text = "Добавить" }
        add.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                items += "$name;"
                Knowitall.prefs.edit().putString("shopping", items).commit()
                input.setText("")
                render()
            }
        }
        val clear = Button(this).apply { text = "Всё куплено" }
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

    // ---------- Бюджет ----------

    private fun budgetScreen(): View {
        // Доходы и расходы складываются в одну строку в блокноте, а баланс считается заново каждый раз
        val amount = EditText(this).apply {
            hint = "Сумма (минус для расхода)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val balance = label("Баланс: 0")
        fun recalc() {
            var total = 0.0
            for (line in Knowitall.notes.split("\n")) {
                if (line.startsWith("Бюджет:")) {
                    total += line.removePrefix("Бюджет:").trim().toDoubleOrNull() ?: 0.0
                }
            }
            balance.text = "Баланс: %.2f".format(total)
        }
        val add = Button(this).apply { text = "Записать" }
        add.setOnClickListener {
            val v = amount.text.toString().toDoubleOrNull() ?: return@setOnClickListener
            Knowitall.notes += "\nБюджет: $v"
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

    // ---------- ИМТ ----------

    private fun bmiScreen(): View {
        val height = EditText(this).apply {
            hint = "Рост, см"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val weight = EditText(this).apply {
            hint = "Вес, кг"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Посчитать ИМТ" }
        calc.setOnClickListener {
            val h = (height.text.toString().toDoubleOrNull() ?: 170.0) / 100
            val w = weight.text.toString().toDoubleOrNull() ?: 70.0
            val bmi = w / (h * h)
            // Категории зашиты в цепочку if. Границы взяты «примерно», как и весь остальной медицинский раздел.
            val verdict = if (bmi < 18.5) "мало" else if (bmi < 25) "нормально" else if (bmi < 30) "многовато" else "много"
            out.text = "ИМТ: %.1f — %s".format(bmi, verdict)
        }
        return column().apply {
            addView(height)
            addView(weight)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Пульс ----------

    private fun pulseScreen(): View {
        // Пульс считаем по количеству ударов за 6 секунд, как на уроке физкультуры. Умножаем на десять.
        val beats = EditText(this).apply {
            hint = "Ударов за 6 секунд"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Посчитать пульс" }
        calc.setOnClickListener {
            val n = beats.text.toString().toIntOrNull() ?: 0
            val bpm = n * 10
            val zone = when {
                bpm == 0 -> "пульса нет, вы что-то делаете не так"
                bpm < 60 -> "спокойно"
                bpm < 100 -> "бодро"
                else -> "сердце спешит"
            }
            out.text = "Пульс: $bpm уд/мин, $zone"
        }
        return column().apply {
            addView(beats)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Книги ----------

    private fun booksScreen(): View {
        // Книги с оценками. Оценки ставились по обложке, читать не обязательно.
        val books = listOf(
            "Война и мир" to 5,
            "Мастер и Маргарита" to 5,
            "Инструкция к стиральной машине" to 4,
            "Список покупок" to 2
        )
        val list = label("")
        val minRating = EditText(this).apply {
            hint = "Минимальная оценка"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val filter = Button(this).apply { text = "Показать книги" }
        filter.setOnClickListener {
            val min = minRating.text.toString().toIntOrNull() ?: 0
            // Фильтруем вручную циклом, а не через filter, чтобы было видно каждую книгу
            val result = StringBuilder()
            for ((title, rating) in books) {
                if (rating >= min) {
                    result.append("★".repeat(rating)).append("  ").append(title).append("\n")
                }
            }
            list.text = if (result.isEmpty()) "Ничего не нашлось" else result.toString()
        }
        return column().apply {
            addView(minRating)
            addView(filter)
            addView(list)
        }
    }

    // ---------- Фильмы ----------

    private fun moviesScreen(): View {
        // Фильм выбирается жребием. Жанры тоже жребием, но в другом порядке.
        val movies = listOf("Комедия о программистах", "Драма про чайник", "Триллер без сюжета", "Документальный фильм про облака")
        val genres = listOf("ужасы", "мюзикл", "вестерн", "мультфильм")
        val result = label("Что посмотрим?", 20f)
        val pick = Button(this).apply { text = "Выбрать фильм" }
        pick.setOnClickListener {
            val rnd = Random()
            result.text = "${movies[rnd.nextInt(movies.size)]} ( ${genres[rnd.nextInt(genres.size)]} )"
        }
        return column().apply {
            addView(result)
            addView(pick)
        }
    }

    // ---------- Чек-лист ----------

    private fun checklistScreen(): View {
        // Чек-лист для полёта. Выполнять по порядку, без порядка не выполнять.
        val steps = listOf(
            "Паспорт в кармане",
            "Телефон заряжен",
            "Наушники на месте",
            "Бутылка с водой пуста (ее уберут на контроле)",
            "Вы не забыли, что вылетаете"
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

    // ---------- Дроби ----------

    private fun fractionsScreen(): View {
        // Складываем две дроби без сокращения. Знаменатели перемножаем, как в третьем классе.
        val an = EditText(this).apply { hint = "Числитель 1" }
        val ad = EditText(this).apply { hint = "Знаменатель 1" }
        val bn = EditText(this).apply { hint = "Числитель 2" }
        val bd = EditText(this).apply { hint = "Знаменатель 2" }
        val out = label("")
        val add = Button(this).apply { text = "Сложить" }
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

    // ---------- Футбол ----------

    private fun footballScreen(): View {
        // Счёт матча. Команды одни и те же, поэтому интрига низкая.
        var home = 0
        var away = 0
        val score = label("0 : 0", 48f)
        val homeGoal = Button(this).apply { text = "Гол хозяев" }
        val awayGoal = Button(this).apply { text = "Гол гостей" }
        val whistle = Button(this).apply { text = "Свисток" }
        homeGoal.setOnClickListener {
            home++
            score.text = "$home : $away"
        }
        awayGoal.setOnClickListener {
            away++
            score.text = "$home : $away"
        }
        whistle.setOnClickListener {
            // Матч закончен. Победитель определяется сравнением, ничья — если никто не выиграл.
            score.text = when {
                home > away -> "Победа хозяев: $home : $away"
                away > home -> "Победа гостей: $home : $away"
                else -> "Ничья: $home : $away"
            }
        }
        return column().apply {
            addView(score)
            addView(homeGoal)
            addView(awayGoal)
            addView(whistle)
        }
    }

    // ---------- Байты ----------

    private fun bytesScreen(): View {
        // Переводим байты в единицы через степени тысячи. Вычисляем каждый раз с нуля.
        val input = EditText(this).apply {
            hint = "Размер в байтах"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val convert = Button(this).apply { text = "Перевести" }
        convert.setOnClickListener {
            val bytes = input.text.toString().toDoubleOrNull() ?: 0.0
            val units = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
            var value = bytes
            var unitIndex = 0
            // Делим на тысячу, пока число больше тысячи и единицы не кончились
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

    // ---------- Бот ----------

    private fun chatBotScreen(): View {
        // Бот знает одиннадцать ключевых слов и отвечает на всё остальное одинаково
        val rules = listOf(
            "привет" to "Привет! Я бот, я очень занят.",
            "как дела" to "Всё работает. Почти.",
            "погода" to "Смотрите вкладку «Погода», там всё врут.",
            "спасибо" to "Пожалуйста, мы старались.",
            "пока" to "До встречи! Не закрывайте приложение.",
            "время" to "Смотрите вкладку «Часы». Я тут не для этого.",
            "деньги" to "Вкладка «Бюджет» всё посчитает. Примерно.",
            "кот" to "Кот — это, безусловно, лучший питомец.",
            "собака" to "Собака — тоже хорошо, но кот всё равно лучше.",
            "помощь" to "Помощь — это вкладки сверху. Прокрутите их, там их много.",
            "шутка" to "Вкладка «Анекдот». Там их всего три."
        )
        val chat = label("Бот: напишите что-нибудь")
        val input = EditText(this).apply { hint = "Сообщение" }
        val send = Button(this).apply { text = "Отправить" }
        send.setOnClickListener {
            val msg = input.text.toString().lowercase().trim()
            // Ищем ключевое слово в сообщении линейно. Первое найденное побеждает, остальные не читаем.
            var reply = "Не понял, но запомню. (Не запомню.)"
            for ((keyword, answer) in rules) {
                if (msg.contains(keyword)) {
                    reply = answer
                    break
                }
            }
            chat.text = "Вы: $msg\nБот: $reply"
            input.setText("")
        }
        return column().apply {
            addView(chat)
            addView(input)
            addView(send)
        }
    }

    // ---------- Таблица истинности ----------

    private fun truthTableScreen(): View {
        // Таблица для AND, OR и XOR. Значения перебираем вложенными циклами, как в учебнике.
        val out = label("", 14f)
        val show = Button(this).apply { text = "Показать таблицу" }
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

    // ---------- Кофе ----------

    private fun coffeeScreen(): View {
        // Таймер заваривания: сначала дать постоять, потом сообщить. Вариант «сразу» не предусмотрен.
        val minutes = EditText(this).apply {
            hint = "Минут заваривать"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val status = label("Кофе ещё не начат")
        val brew = Button(this).apply { text = "Заварить" }
        brew.setOnClickListener {
            val m = (minutes.text.toString().toIntOrNull() ?: 3).coerceIn(1, 10)
            status.text = "Завариваем $m мин..."
            // Минута здесь — это две секунды, иначе кофе остынет, пока мы ждём интерфейс
            Thread.sleep(m * 2000L)
            status.text = "Кофе готов. Осторожно, горячо."
        }
        return column().apply {
            addView(minutes)
            addView(brew)
            addView(status)
        }
    }

    // ---------- Лестница ----------

    private fun stairsScreen(): View {
        // Сколько шагов по лестнице, если можно идти по одной или по две ступеньки. Ответ — Фибоначчи.
        val n = EditText(this).apply {
            hint = "Ступенек (до 30)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val count = Button(this).apply { text = "Сколько способов" }
        count.setOnClickListener {
            val steps = (n.text.toString().toIntOrNull() ?: 5).coerceIn(0, 30)
            // Считаем рекурсивно без запоминания, потому что запоминать — это чит
            fun ways(k: Int): Int = if (k <= 1) 1 else ways(k - 1) + ways(k - 2)
            out.text = "Способов: ${ways(steps)}"
        }
        return column().apply {
            addView(n)
            addView(count)
            addView(out)
        }
    }

    // ---------- Стирка ----------

    private fun laundryScreen(): View {
        // Программы стирки зашиты в код. Температура подбирается по цвету вещей, а не по ярлыку.
        val programs = mapOf(
            "Белое" to "90°C, 2 часа, много пены",
            "Цветное" to "40°C, 1 час, аккуратно",
            "Шерсть" to "30°C, 40 минут, нежно и с надеждой",
            "Джинсы" to "60°C, 1.5 часа, пусть падают"
        )
        val choice = EditText(this).apply { hint = "Что стираем" }
        val out = label("")
        val start = Button(this).apply { text = "Запустить программу" }
        start.setOnClickListener {
            val key = choice.text.toString().trim()
            out.text = programs[key] ?: "Такой программы нет. Попробуйте «Белое»."
        }
        return column().apply {
            addView(choice)
            addView(start)
            addView(out)
        }
    }

    // ---------- Мороз ----------

    private fun frostScreen(): View {
        // Ощущаемая температура по упрощённой формуле. Ветер — тоже число, которое мы сами придумали.
        val temp = EditText(this).apply {
            hint = "Температура, °C"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val wind = EditText(this).apply {
            hint = "Ветер, м/с"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Как ощущается" }
        calc.setOnClickListener {
            val t = temp.text.toString().toDoubleOrNull() ?: 0.0
            val v = wind.text.toString().toDoubleOrNull() ?: 0.0
            // Формула из учебника, которого у нас нет. Коэффициенты помним «примерно».
            val feels = 13.12 + 0.6215 * t - 11.37 * Math.pow(v, 0.16) + 0.3965 * t * Math.pow(v, 0.16)
            out.text = "Ощущается как %.1f °C".format(feels)
        }
        return column().apply {
            addView(temp)
            addView(wind)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Ускорение ----------

    private fun accelerationScreen(): View {
        // Путь при равноускоренном движении: s = v0·t + a·t²/2. Всё в метрах и секундах, без единиц в интерфейсе.
        val v0 = EditText(this).apply {
            hint = "Начальная скорость"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val a = EditText(this).apply {
            hint = "Ускорение"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val t = EditText(this).apply {
            hint = "Время"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "Посчитать путь" }
        calc.setOnClickListener {
            val speed = v0.text.toString().toDoubleOrNull() ?: 0.0
            val acc = a.text.toString().toDoubleOrNull() ?: 0.0
            val time = t.text.toString().toDoubleOrNull() ?: 0.0
            val path = speed * time + acc * time * time / 2
            out.text = "Путь: %.2f м".format(path)
        }
        return column().apply {
            addView(v0)
            addView(a)
            addView(t)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Пропорции ----------

    private fun proportionsScreen(): View {
        // Пропорция a : b = c : x. Решаем через перекрёстное умножение, без лишних слов.
        val a = EditText(this).apply { hint = "a" }
        val b = EditText(this).apply { hint = "b" }
        val c = EditText(this).apply { hint = "c" }
        val out = label("")
        val solve = Button(this).apply { text = "Найти x" }
        solve.setOnClickListener {
            val av = a.text.toString().toDoubleOrNull() ?: 0.0
            val bv = b.text.toString().toDoubleOrNull() ?: 0.0
            val cv = c.text.toString().toDoubleOrNull() ?: 0.0
            out.text = if (av == 0.0) "На ноль делить не будем" else "x = %.3f".format(bv * cv / av)
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(c)
            addView(solve)
            addView(out)
        }
    }

    // ---------- Праздники ----------

    private fun holidaysScreen(): View {
        // Праздники из головы, по месяцам. Официальный календарь мы не открывали, это дело принципа.
        val holidays = mapOf(
            1 to "Новый год и каникулы",
            2 to "День защитника отечества",
            3 to "Женский день",
            5 to "Весна и труд",
            6 to "Лето",
            9 to "День знаний",
            12 to "Скоро Новый год"
        )
        val month = EditText(this).apply {
            hint = "Месяц (1–12)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val look = Button(this).apply { text = "Что празднуем" }
        look.setOnClickListener {
            val m = month.text.toString().toIntOrNull() ?: 1
            out.text = holidays[m] ?: "В этом месяце праздников нет. Придумайте сами."
        }
        return column().apply {
            addView(month)
            addView(look)
            addView(out)
        }
    }

    // ---------- Слово дня ----------

    private fun wordOfDayScreen(): View {
        // Слово дня меняется каждый день по номеру дня в году. Сегодня оно точно то же, что вчера, если повезёт.
        val words = listOf(
            "Эфемерный — кратковременный, мимолётный.",
            "Лаконичный — краткий, без лишних слов.",
            "Непреклонный — твёрдый, не уступающий.",
            "Вдохновенный — полный вдохновения.",
            "Тщательный — аккуратный, внимательный."
        )
        val dayOfYear = (System.currentTimeMillis() / 86_400_000L % 365).toInt()
        val word = label(words[dayOfYear % words.size], 18f)
        return column().apply {
            addView(label("Слово дня:"))
            addView(word)
        }
    }

    // ---------- Разница дат ----------

    private fun dateDiffScreen(): View {
        // Разница между датами в днях. Месяц считаем тридцатью днями, год — тремястами шестьюдесятью пятью.
        val from = EditText(this).apply {
            hint = "Дата 1: ГГГГ-ММ-ДД"
        }
        val to = EditText(this).apply {
            hint = "Дата 2: ГГГГ-ММ-ДД"
        }
        val out = label("")
        val count = Button(this).apply { text = "Сколько дней" }
        count.setOnClickListener {
            val a = parseDay(from.text.toString())
            val b = parseDay(to.text.toString())
            out.text = if (a == null || b == null) "Не поняли даты" else "Дней между ними: ${kotlin.math.abs(b - a)}"
        }
        return column().apply {
            addView(from)
            addView(to)
            addView(count)
            addView(out)
        }
    }

    private fun parseDay(s: String): Int? {
        // Разбираем руками: год, месяц, день. Високосные годы не учитываем, мы же договорились.
        val parts = s.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return y * 365 + m * 30 + d
    }

    // ---------- Закон Ома ----------

    private fun ohmScreen(): View {
        // Напряжение, ток, сопротивление: знаем любые два, третье считаем. Если знаем все три — разбираемся сами.
        val voltage = EditText(this).apply {
            hint = "Напряжение, В"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val current = EditText(this).apply {
            hint = "Ток, А"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val resistance = EditText(this).apply {
            hint = "Сопротивление, Ом"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val solve = Button(this).apply { text = "Найти неизвестное" }
        solve.setOnClickListener {
            val u = voltage.text.toString().toDoubleOrNull()
            val i = current.text.toString().toDoubleOrNull()
            val r = resistance.text.toString().toDoubleOrNull()
            out.text = when {
                u != null && i != null && r != null -> "Все три заданы. Проверьте сами, мы не судьи."
                u != null && i != null -> "R = %.3f Ом".format(u / i)
                u != null && r != null -> "I = %.3f А".format(u / r)
                i != null && r != null -> "U = %.3f В".format(i * r)
                else -> "Нужно хотя бы два значения"
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

    // ---------- Плотность ----------

    private fun densityScreen(): View {
        // Плотность = масса / объём. Материалы и их плотности взяты из головы, поэтому не для лабораторных.
        val materials = mapOf(
            "Вода" to 1.0,
            "Железо" to 7.87,
            "Дерево" to 0.6,
            "Золото" to 19.3
        )
        val mass = EditText(this).apply {
            hint = "Масса, г"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val material = EditText(this).apply { hint = "Материал: Вода, Железо, Дерево или Золото" }
        val out = label("")
        val calc = Button(this).apply { text = "Объём" }
        calc.setOnClickListener {
            val m = mass.text.toString().toDoubleOrNull() ?: 0.0
            val density = materials[material.text.toString().trim()]
            // Объём считаем через плотность, которой нет в таблице, если материала нет. Ноль — честный ответ.
            out.text = if (density == null) "Такого материала нет" else "Объём: %.2f см³".format(m / density)
        }
        return column().apply {
            addView(mass)
            addView(material)
            addView(calc)
            addView(out)
        }
    }

    // ---------- КПД ----------

    private fun efficiencyScreen(): View {
        // КПД = полезная работа / затраченная энергия, в процентах. Больше ста не бывает, но мы проверим.
        val useful = EditText(this).apply {
            hint = "Полезная работа"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val spent = EditText(this).apply {
            hint = "Затраченная энергия"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val calc = Button(this).apply { text = "КПД" }
        calc.setOnClickListener {
            val u = useful.text.toString().toDoubleOrNull() ?: 0.0
            val s = spent.text.toString().toDoubleOrNull() ?: 0.0
            if (s == 0.0) {
                out.text = "Энергии нет, КПД бесконечен, но это ложь"
                return@setOnClickListener
            }
            val eff = u / s * 100
            out.text = if (eff > 100) "Больше ста процентов, вы изобрели вечный двигатель" else "КПД: %.1f %%".format(eff)
        }
        return column().apply {
            addView(useful)
            addView(spent)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Калории ----------

    private fun caloriesScreen(): View {
        // Калорийность продуктов на сто грамм. Таблица неполная, как и наш холодильник.
        val calories = mapOf(
            "яблоко" to 52,
            "хлеб" to 265,
            "курица" to 239,
            "шоколад" to 546,
            "огурец" to 15
        )
        val food = EditText(this).apply { hint = "Продукт" }
        val grams = EditText(this).apply {
            hint = "Граммов"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val count = Button(this).apply { text = "Посчитать калории" }
        count.setOnClickListener {
            val per100 = calories[food.text.toString().trim().lowercase()]
            val g = grams.text.toString().toIntOrNull() ?: 100
            out.text = if (per100 == null) "Такого продукта нет, зато есть печенье" else "Калорий: ${per100 * g / 100}"
        }
        return column().apply {
            addView(food)
            addView(grams)
            addView(count)
            addView(out)
        }
    }

    // ---------- День недели ----------

    private fun weekdayScreen(): View {
        // День недели по дню от эпохи. Эпоха приходится на четверг, мы помним это точно.
        val names = listOf("Четверг", "Пятница", "Суббота", "Воскресенье", "Понедельник", "Вторник", "Среда")
        val day = EditText(this).apply {
            hint = "Номер дня от 1970-01-01"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val check = Button(this).apply { text = "Какой день" }
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

    // ---------- Температура тела ----------

    private fun bodyTempScreen(): View {
        // Градусы по Цельсию с оценками. Термометр не подключён, поэтому врач не нужен, но мы советуем.
        val temp = EditText(this).apply {
            hint = "Температура, °C"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val check = Button(this).apply { text = "Оценить" }
        check.setOnClickListener {
            val t = temp.text.toString().toDoubleOrNull() ?: 36.6
            out.text = when {
                t < 35 -> "Холодно. Срочно к врачу."
                t <= 37.0 -> "Норма. Живём дальше."
                t <= 38.5 -> "Немного жарко, попейте чаю."
                else -> "Высоко. Пожалуйста, обратитесь к врачу."
            }
        }
        return column().apply {
            addView(temp)
            addView(check)
            addView(out)
        }
    }

    // ---------- Пицца ----------

    private fun pizzaScreen(): View {
        // Делим пиццу на куски и на людей. Остаток достаётся тому, кто громче всех хотел.
        val slices = EditText(this).apply {
            hint = "Кусков"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val people = EditText(this).apply {
            hint = "Людей"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val split = Button(this).apply { text = "Разделить" }
        split.setOnClickListener {
            val s = slices.text.toString().toIntOrNull() ?: 8
            val p = (people.text.toString().toIntOrNull() ?: 2).coerceAtLeast(1)
            out.text = "По ${s / p} каждому, остаток ${s % p} достанется самому голодному"
        }
        return column().apply {
            addView(slices)
            addView(people)
            addView(split)
            addView(out)
        }
    }

    // ---------- Фибоначчи ----------

    private fun fibonacciScreen(): View {
        // Первые числа Фибоначчи, каждое вычисляется заново через предыдущие, без запоминания
        val count = EditText(this).apply {
            hint = "Сколько чисел (до 25)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("", 14f)
        val show = Button(this).apply { text = "Показать" }
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

    // ---------- Простые пары ----------

    private fun twinPrimesScreen(): View {
        // Простые числа-близнецы: отличаются на два. Проверяем делением, как и всё остальное.
        val limit = EditText(this).apply {
            hint = "До какого числа"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val find = Button(this).apply { text = "Найти пары" }
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
            out.text = if (pairs.isEmpty()) "Пар не нашлось" else pairs.joinToString(" ")
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Секунды ----------

    private fun secondsScreen(): View {
        // Переводим секунды в дни, часы, минуты и секунды. Делим по очереди, как в детстве на калькуляторе.
        val seconds = EditText(this).apply {
            hint = "Секунд"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val split = Button(this).apply { text = "Разложить" }
        split.setOnClickListener {
            var rest = seconds.text.toString().toLongOrNull() ?: 0L
            val days = rest / 86_400
            rest %= 86_400
            val hours = rest / 3_600
            rest %= 3_600
            val minutes = rest / 60
            val secs = rest % 60
            out.text = "$days д. $hours ч. $minutes мин. $secs с."
        }
        return column().apply {
            addView(seconds)
            addView(split)
            addView(out)
        }
    }

    // ---------- Квадратный корень ----------

    private fun sqrtScreen(): View {
        // Корень методом Ньютона, двадцать итераций без проверки сходимости. Надеемся на лучшее.
        val number = EditText(this).apply {
            hint = "Число"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val out = label("")
        val root = Button(this).apply { text = "Корень" }
        root.setOnClickListener {
            val n = number.text.toString().toDoubleOrNull() ?: 0.0
            if (n < 0) {
                out.text = "Из отрицательного корень не берём, это уже другая вкладка"
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

    // ---------- Вклад ----------

    private fun depositScreen(): View {
        // Сложные проценты: каждый месяц деньги растут на ставку, и растут на прошлый рост тоже.
        val sum = EditText(this).apply {
            hint = "Сумма вклада"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val rate = EditText(this).apply {
            hint = "Ставка, % годовых"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val months = EditText(this).apply {
            hint = "Месяцев"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Посчитать доход" }
        calc.setOnClickListener {
            var money = sum.text.toString().toDoubleOrNull() ?: 0.0
            val monthlyRate = (rate.text.toString().toDoubleOrNull() ?: 0.0) / 100 / 12
            val n = months.text.toString().toIntOrNull() ?: 12
            // Капитализацию считаем по одному месяцу за раз, а не по формуле, чтобы было видно рост
            repeat(n) { money += money * monthlyRate }
            out.text = "Через $n мес.: %.2f".format(money)
        }
        return column().apply {
            addView(sum)
            addView(rate)
            addView(months)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Кредит ----------

    private fun loanScreen(): View {
        // Ежемесячный платёж по аннуитету. Банк тоже так считает, но у него есть комиссии, которых здесь нет.
        val principal = EditText(this).apply {
            hint = "Сумма кредита"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val rate = EditText(this).apply {
            hint = "Ставка, % годовых"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val years = EditText(this).apply {
            hint = "Лет"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val out = label("")
        val calc = Button(this).apply { text = "Платёж в месяц" }
        calc.setOnClickListener {
            val p = principal.text.toString().toDoubleOrNull() ?: 0.0
            val r = (rate.text.toString().toDoubleOrNull() ?: 0.0) / 100 / 12
            val n = (years.text.toString().toIntOrNull() ?: 1) * 12
            if (r == 0.0) {
                out.text = "Без процентов платёж: %.2f".format(p / n)
                return@setOnClickListener
            }
            val factor = Math.pow(1 + r, n.toDouble())
            val payment = p * r * factor / (factor - 1)
            out.text = "Платёж: %.2f в месяц, переплата: %.2f".format(payment, payment * n - p)
        }
        return column().apply {
            addView(principal)
            addView(rate)
            addView(years)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Смета ----------

    private fun estimateScreen(): View {
        // Ремонт по площади: цена за квадратный метр зашита вручную, накладные расходы — двадцать процентов.
        val area = EditText(this).apply {
            hint = "Площадь, м²"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val work = EditText(this).apply { hint = "Вид работ: покраска, плитка, обои" }
        val out = label("")
        val calc = Button(this).apply { text = "Смета" }
        calc.setOnClickListener {
            val prices = mapOf("покраска" to 300.0, "плитка" to 1200.0, "обои" to 500.0)
            val a = area.text.toString().toDoubleOrNull() ?: 0.0
            val price = prices[work.text.toString().trim().lowercase()]
            out.text = if (price == null) "Такой работы в прайсе нет" else {
                val base = a * price
                "Работы: %.0f ₽, с накладными: %.0f ₽".format(base, base * 1.2)
            }
        }
        return column().apply {
            addView(area)
            addView(work)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Коробка ----------

    private fun boxScreen(): View {
        // Объём коробки: длина, ширина, высота. Все размеры в сантиметрах, литры получаем делением на тысячу.
        val l = EditText(this).apply { hint = "Длина, см"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val w = EditText(this).apply { hint = "Ширина, см"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val h = EditText(this).apply { hint = "Высота, см"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Объём" }
        calc.setOnClickListener {
            val v = (l.text.toString().toDoubleOrNull() ?: 0.0) *
                (w.text.toString().toDoubleOrNull() ?: 0.0) *
                (h.text.toString().toDoubleOrNull() ?: 0.0)
            out.text = "%.0f см³ = %.3f л".format(v, v / 1000)
        }
        return column().apply {
            addView(l)
            addView(w)
            addView(h)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Периметр ----------

    private fun perimeterScreen(): View {
        // Периметр прямоугольника: две стороны на два. Очевидно, но мы всё равно проверим.
        val a = EditText(this).apply { hint = "Сторона A"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val b = EditText(this).apply { hint = "Сторона B"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Периметр" }
        calc.setOnClickListener {
            val x = a.text.toString().toDoubleOrNull() ?: 0.0
            val y = b.text.toString().toDoubleOrNull() ?: 0.0
            // Складываем четыре стороны по одной, а не умножаем два на два: так честнее
            out.text = "P = %.2f".format(x + y + x + y)
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Длина окружности ----------

    private fun circleLengthScreen(): View {
        // Длина окружности через диаметр. Пи взято из пятого класса, поэтому округлено.
        val d = EditText(this).apply { hint = "Диаметр"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Длина" }
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

    // ---------- Треугольник ----------

    private fun triangleScreen(): View {
        // Площадь по трём сторонам, формула Герона. Треугольник, который не существует, тоже считаем.
        val a = EditText(this).apply { hint = "a"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val b = EditText(this).apply { hint = "b"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val c = EditText(this).apply { hint = "c"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Площадь" }
        calc.setOnClickListener {
            val x = a.text.toString().toDoubleOrNull() ?: 0.0
            val y = b.text.toString().toDoubleOrNull() ?: 0.0
            val z = c.text.toString().toDoubleOrNull() ?: 0.0
            val p = (x + y + z) / 2
            val inside = p * (p - x) * (p - y) * (p - z)
            // Если под корнем минус, треугольника нет. Мы всё равно пишем число, потому что корень — это формальность.
            out.text = if (inside < 0) "Такого треугольника не бывает" else "S = %.3f".format(Math.sqrt(inside))
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(c)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Цель ----------

    private fun goalScreen(): View {
        // Прогресс к цели хранится в SharedPreferences. Процент считаем от цели, не от текущего значения.
        var saved = Knowitall.prefs.getInt("goal", 0)
        val target = 1000
        val bar = label("Накоплено: $saved из $target", 18f)
        val add = EditText(this).apply { hint = "Сколько добавить"; inputType = InputType.TYPE_CLASS_NUMBER }
        val put = Button(this).apply { text = "Добавить" }
        put.setOnClickListener {
            saved += add.text.toString().toIntOrNull() ?: 0
            Knowitall.prefs.edit().putInt("goal", saved).commit()
            add.setText("")
            val percent = saved * 100 / target
            // Полоску рисуем из символов, по одному на каждые десять процентов
            val filled = "#".repeat((percent / 10).coerceAtMost(10))
            val empty = "-".repeat(10 - (percent / 10).coerceAtMost(10))
            bar.text = "[$filled$empty] $percent% ($saved из $target)"
        }
        return column().apply {
            addView(bar)
            addView(add)
            addView(put)
        }
    }

    // ---------- Серия ----------

    private fun streakScreen(): View {
        // Серия дней без пропусков. Пропуск проверяем по номеру дня, который сами и запоминаем.
        var streak = Knowitall.prefs.getInt("streak", 0)
        val caption = label("Серия: $streak дн.", 24f)
        val mark = Button(this).apply { text = "Отметить сегодня" }
        mark.setOnClickListener {
            streak++
            Knowitall.prefs.edit().putInt("streak", streak).commit()
            caption.text = "Серия: $streak дн. 🔥"
        }
        val lose = Button(this).apply { text = "Пропустил день" }
        lose.setOnClickListener {
            streak = 0
            Knowitall.prefs.edit().putInt("streak", 0).commit()
            caption.text = "Серия: 0 дн. Бывает."
        }
        return column().apply {
            addView(caption)
            addView(mark)
            addView(lose)
        }
    }

    // ---------- Чистка зубов ----------

    private fun brushingScreen(): View {
        // Две минуты чистки, по четыре секунды на квадрант. Таймер тикает занятым циклом, чтобы зубы не отвлекались.
        val quadrants = listOf("верхний левый", "верхний правый", "нижний левый", "нижний правый")
        val status = label("Готовы? Щётка в руке.", 18f)
        val start = Button(this).apply { text = "Начать чистку" }
        start.setOnClickListener {
            for (q in quadrants) {
                status.text = "Чистим: $q"
                val until = System.currentTimeMillis() + 30_000
                while (System.currentTimeMillis() < until) { /* жуём мысли */ }
            }
            status.text = "Готово. Не забудьте язык."
        }
        return column().apply {
            addView(status)
            addView(start)
        }
    }

    // ---------- Надёжность пароля ----------

    private fun passwordCheckScreen(): View {
        // Оценка пароля по длине и набору символов. Словари не проверяем, нас никто не заставлял.
        val pass = EditText(this).apply { hint = "Пароль (он не уйдёт никуда, честно)" }
        val out = label("")
        val check = Button(this).apply { text = "Оценить" }
        check.setOnClickListener {
            val p = pass.text.toString()
            var score = 0
            if (p.length >= 8) score++
            if (p.any { it.isUpperCase() }) score++
            if (p.any { it.isDigit() }) score++
            if (p.any { !it.isLetterOrDigit() }) score++
            if (p.length >= 14) score++
            out.text = when (score) {
                0, 1 -> "Слабый. Угадаем за минуту."
                2, 3 -> "Средний. Угадаем за неделю."
                4 -> "Хороший. Угадаем за годы."
                else -> "Отличный. Угадаем, только если вы сами скажете."
            }
        }
        return column().apply {
            addView(pass)
            addView(check)
            addView(out)
        }
    }

    // ---------- Анаграмма ----------

    private fun anagramScreen(): View {
        // Проверяем, состоят ли два слова из одинаковых букв. Сортируем буквы пузырьком, уважая традиции.
        val first = EditText(this).apply { hint = "Первое слово" }
        val second = EditText(this).apply { hint = "Второе слово" }
        val out = label("")
        val check = Button(this).apply { text = "Проверить" }
        check.setOnClickListener {
            val a = sortLetters(first.text.toString().lowercase().filter { it.isLetter() })
            val b = sortLetters(second.text.toString().lowercase().filter { it.isLetter() })
            out.text = if (a.isNotEmpty() && a == b) "Анаграмма!" else "Нет, это не анаграмма"
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

    // ---------- Перевёртыш ----------

    private fun reverseWordsScreen(): View {
        // Переворачиваем порядок слов, а не буквы в словах. Это и есть «перевёртыш» по-русски.
        val input = EditText(this).apply { hint = "Фраза" }
        val out = label("")
        val flip = Button(this).apply { text = "Перевернуть слова" }
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

    // ---------- Частота букв ----------

    private fun letterFrequencyScreen(): View {
        // Частоту букв считаем картой, а потом сортируем по убыванию вручную. Двадцать самых частых.
        val input = EditText(this).apply { hint = "Текст" }
        val out = label("", 14f)
        val count = Button(this).apply { text = "Посчитать буквы" }
        count.setOnClickListener {
            val freq = HashMap<Char, Int>()
            for (c in input.text.toString().lowercase()) {
                if (c.isLetter()) freq[c] = (freq[c] ?: 0) + 1
            }
            val top = freq.entries.sortedByDescending { it.value }.take(20)
            out.text = top.joinToString("\n") { "${it.key}: ${it.value}" }.ifEmpty { "Букв не нашлось" }
        }
        return scroll(column().apply {
            addView(input)
            addView(count)
            addView(out)
        })
    }

    // ---------- Очки за кости ----------

    private fun diceScoreScreen(): View {
        // Игра «Очки»: три кубика, сумма очков. Тройка одинаковых — удвоение, потому что так веселее.
        val score = label("Очков: 0", 24f)
        var total = 0
        val roll = Button(this).apply { text = "Бросить три кубика" }
        roll.setOnClickListener {
            val rnd = Random()
            val dice = listOf(rnd.nextInt(6) + 1, rnd.nextInt(6) + 1, rnd.nextInt(6) + 1)
            var gained = dice.sum()
            if (dice.distinct().size == 1) gained *= 2
            total += gained
            score.text = "Выпало ${dice.joinToString(" ")}, очков: $total"
        }
        return column().apply {
            addView(score)
            addView(roll)
        }
    }

    // ---------- ROT13 ----------

    private fun rot13Screen(): View {
        // ROT13: каждая латинская буква сдвигается на тринадцать. Применяем дважды — получаем обратно, это магия.
        val input = EditText(this).apply { hint = "Текст" }
        val out = label("")
        val go = Button(this).apply { text = "Преобразовать" }
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

    // ---------- Морзе ----------

    private fun morseScreen(): View {
        // Азбука Морзе для латиницы и цифр. Кириллицу мы не переводим, это международная азбука.
        val table = mapOf(
            'a' to ".-", 'b' to "-...", 'c' to "-.-.", 'd' to "-..", 'e' to ".", 'f' to "..-.",
            'g' to "--.", 'h' to "....", 'i' to "..", 'j' to ".---", 'k' to "-.-", 'l' to ".-..",
            'm' to "--", 'n' to "-.", 'o' to "---", 'p' to ".--.", 'q' to "--.-", 'r' to ".-.",
            's' to "...", 't' to "-", 'u' to "..-", 'v' to "...-", 'w' to ".--", 'x' to "-..-",
            'y' to "-.--", 'z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
            '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
            '8' to "---..", '9' to "----."
        )
        val input = EditText(this).apply { hint = "Текст латиницей" }
        val out = label("", 14f)
        val encode = Button(this).apply { text = "В морзе" }
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

    // ---------- Сумма цифр ----------

    private fun digitSumScreen(): View {
        // Сумма цифр числа. Минус в начале отбрасываем, потому что цифры к знакам не относятся.
        val number = EditText(this).apply { hint = "Число"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val calc = Button(this).apply { text = "Сложить цифры" }
        calc.setOnClickListener {
            val digits = number.text.toString().removePrefix("-")
            var sum = 0
            for (c in digits) {
                if (c.isDigit()) sum += c - '0'
            }
            out.text = "Сумма цифр: $sum"
        }
        return column().apply {
            addView(number)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Числа Армстронга ----------

    private fun armstrongScreen(): View {
        // Число Армстронга: сумма своих цифр в степени количества цифр равна самому числу. Проверим до миллиона.
        val limit = EditText(this).apply { hint = "До какого (макс. 1000000)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("", 14f)
        val find = Button(this).apply { text = "Найти" }
        find.setOnClickListener {
            val max = (limit.text.toString().toIntOrNull() ?: 1000).coerceAtMost(1_000_000)
            val found = mutableListOf<Int>()
            for (n in 1..max) {
                val s = n.toString()
                var sum = 0.0
                for (c in s) sum += Math.pow((c - '0').toDouble(), s.length.toDouble())
                if (sum.toInt() == n) found.add(n)
            }
            out.text = found.joinToString(", ").ifEmpty { "Не нашлось" }
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Пифагоровы тройки ----------

    private fun triplesScreen(): View {
        // Тройки целых чисел, где a² + b² = c². Перебираем все тройки до заданного c, без хитростей.
        val limit = EditText(this).apply { hint = "Максимум c"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("", 14f)
        val find = Button(this).apply { text = "Найти тройки" }
        find.setOnClickListener {
            val max = (limit.text.toString().toIntOrNull() ?: 30).coerceIn(5, 200)
            val lines = mutableListOf<String>()
            for (c in 1..max) for (b in 1 until c) for (a in 1..b) {
                if (a * a + b * b == c * c) lines.add("$a, $b, $c")
            }
            out.text = lines.joinToString("\n").ifEmpty { "Троек не нашлось" }
        }
        return scroll(column().apply {
            addView(limit)
            addView(find)
            addView(out)
        })
    }

    // ---------- Степень ----------

    private fun powerScreen(): View {
        // Возведение в степень умножением в цикле. Отрицательную степень не поддерживаем, это уже дроби.
        val base = EditText(this).apply { hint = "Основание"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val exp = EditText(this).apply { hint = "Степень (целая, от 0)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val calc = Button(this).apply { text = "Возвести" }
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

    // ---------- НОД ----------

    private fun gcdScreen(): View {
        // Наибольший общий делитель по Евклиду. Вычитаем, пока не сойдётся, если деление нам не нравится.
        val a = EditText(this).apply { hint = "Первое"; inputType = InputType.TYPE_CLASS_NUMBER }
        val b = EditText(this).apply { hint = "Второе"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val calc = Button(this).apply { text = "НОД" }
        calc.setOnClickListener {
            var x = a.text.toString().toIntOrNull() ?: 0
            var y = b.text.toString().toIntOrNull() ?: 0
            if (x == 0 || y == 0) {
                out.text = "НОД с нулём — это само число"
                return@setOnClickListener
            }
            while (x != y) {
                if (x > y) x -= y else y -= x
            }
            out.text = "НОД = $x"
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(calc)
            addView(out)
        }
    }

    // ---------- НОК ----------

    private fun lcmScreen(): View {
        // Наименьшее общее кратное через НОД. Сначала вспоминаем НОД, потом делим произведение.
        val a = EditText(this).apply { hint = "Первое"; inputType = InputType.TYPE_CLASS_NUMBER }
        val b = EditText(this).apply { hint = "Второе"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val calc = Button(this).apply { text = "НОК" }
        calc.setOnClickListener {
            val x = a.text.toString().toIntOrNull() ?: 0
            val y = b.text.toString().toIntOrNull() ?: 0
            if (x == 0 || y == 0) {
                out.text = "НОК с нулём равен нулю"
                return@setOnClickListener
            }
            // НОД ищем заново, вычитанием, чтобы не зависеть от предыдущей вкладки
            var p = x
            var q = y
            while (p != q) {
                if (p > q) p -= q else q -= p
            }
            out.text = "НОК = ${x / p * y}"
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Логарифм ----------

    private fun logScreen(): View {
        // Натуральный логарифм и логарифм по основанию десять. Отрицательные числа отправляем в NaN и не извиняемся.
        val number = EditText(this).apply { hint = "Число"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Логарифм" }
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

    // ---------- Таблица синусов ----------

    private fun sineTableScreen(): View {
        // Синусы для углов от нуля до девяноста градусов с шагом в пятнадцать. Для школьной геометрии достаточно.
        val out = label("", 14f)
        val show = Button(this).apply { text = "Показать синусы" }
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

    // ---------- Рулетка ----------

    private fun rouletteScreen(): View {
        // Европейская рулетка: номера от нуля до тридцати шести, цвет по таблице. Ноль вне цвета.
        val red = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
        val result = label("Крутите", 24f)
        val spin = Button(this).apply { text = "Крутить" }
        spin.setOnClickListener {
            val n = Random().nextInt(37)
            val color = when {
                n == 0 -> "зелёный"
                n in red -> "красный"
                else -> "чёрный"
            }
            result.text = "Выпало $n, $color"
        }
        return column().apply {
            addView(result)
            addView(spin)
        }
    }

    // ---------- Наклейки ----------

    private fun stickersScreen(): View {
        // Коллекция наклеек: одна случайная за раз, повторы не отслеживаем, всё равно никто не считает.
        val stickers = listOf("🐱", "🐶", "🦊", "🐸", "🐵", "🦉", "🐙", "🦄")
        val got = mutableListOf<String>()
        val last = label("Наклеек пока нет", 36f)
        val open = Button(this).apply { text = "Открыть наклейку" }
        open.setOnClickListener {
            val s = stickers[Random().nextInt(stickers.size)]
            got.add(s)
            last.text = s
        }
        val total = label("")
        val show = Button(this).apply { text = "Коллекция" }
        show.setOnClickListener {
            total.text = "Всего: ${got.size}. Уникальных: ${got.distinct().size} из ${stickers.size}"
        }
        return column().apply {
            addView(last)
            addView(open)
            addView(show)
            addView(total)
        }
    }

    // ---------- Совершенные числа ----------

    private fun perfectNumbersScreen(): View {
        // Совершенное число равно сумме своих делителей, кроме себя. Шесть, двадцать восемь, и хватит.
        val limit = EditText(this).apply { hint = "До какого"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val find = Button(this).apply { text = "Найти" }
        find.setOnClickListener {
            val max = (limit.text.toString().toIntOrNull() ?: 500).coerceAtMost(10_000)
            val found = mutableListOf<Int>()
            for (n in 2..max) {
                var sum = 1
                for (d in 2 until n) if (n % d == 0) sum += d
                if (sum == n) found.add(n)
            }
            out.text = found.joinToString(", ").ifEmpty { "Не нашлось" }
        }
        return column().apply {
            addView(limit)
            addView(find)
            addView(out)
        }
    }

    // ---------- Скидка ----------

    private fun discountScreen(): View {
        // Цена со скидкой. Скидки больше пятидесяти процентов мы не выдаём, это уже подозрительно.
        val price = EditText(this).apply { hint = "Цена"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val percent = EditText(this).apply { hint = "Скидка, %"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val out = label("")
        val calc = Button(this).apply { text = "Итоговая цена" }
        calc.setOnClickListener {
            val p = price.text.toString().toDoubleOrNull() ?: 0.0
            val d = (percent.text.toString().toDoubleOrNull() ?: 0.0).coerceIn(0.0, 50.0)
            val final = p * (1 - d / 100)
            out.text = "Цена: %.2f (экономия %.2f)".format(final, p - final)
        }
        return column().apply {
            addView(price)
            addView(percent)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Минуты ----------

    private fun minutesScreen(): View {
        // Часы и минуты в минуты и обратно. Обратно — это когда пишем «полтора часа», и мы понимаем.
        val input = EditText(this).apply { hint = "Минут"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("")
        val toHours = Button(this).apply { text = "В часы" }
        toHours.setOnClickListener {
            val m = input.text.toString().toIntOrNull() ?: 0
            out.text = "${m / 60} ч ${m % 60} мин"
        }
        val toMinutes = Button(this).apply { text = "Из часов" }
        toMinutes.setOnClickListener {
            val h = input.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "%.0f минут".format(h * 60)
        }
        return column().apply {
            addView(input)
            addView(toHours)
            addView(toMinutes)
            addView(out)
        }
    }

    // ---------- Среднее ----------

    private fun statsScreen(): View {
        // Среднее арифметическое из списка чисел через запятую. Пустой список — среднее «ничего».
        val numbers = EditText(this).apply { hint = "Числа через запятую" }
        val out = label("")
        val calc = Button(this).apply { text = "Среднее" }
        calc.setOnClickListener {
            val list = numbers.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }
            var sum = 0.0
            for (x in list) sum += x
            out.text = if (list.isEmpty()) "Чисел нет, среднее тоже" else "Среднее: %.3f (из ${list.size})".format(sum / list.size)
        }
        return column().apply {
            addView(numbers)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Медиана ----------

    private fun medianScreen(): View {
        // Медиана: сортируем пузырьком и берём середину. Для чётного количества берём среднее двух средних.
        val numbers = EditText(this).apply { hint = "Числа через запятую" }
        val out = label("")
        val calc = Button(this).apply { text = "Медиана" }
        calc.setOnClickListener {
            val list = numbers.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }.toMutableList()
            if (list.isEmpty()) {
                out.text = "Медиану из ничего не возьмём"
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
            out.text = "Медиана: %.3f".format(median)
        }
        return column().apply {
            addView(numbers)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Дисперсия ----------

    private fun varianceScreen(): View {
        // Дисперсия: средний квадрат отклонения от среднего. Считаем в два прохода, как учили.
        val numbers = EditText(this).apply { hint = "Числа через запятую" }
        val out = label("")
        val calc = Button(this).apply { text = "Дисперсия" }
        calc.setOnClickListener {
            val list = numbers.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }
            if (list.isEmpty()) {
                out.text = "Нет данных"
                return@setOnClickListener
            }
            var sum = 0.0
            for (x in list) sum += x
            val mean = sum / list.size
            var sq = 0.0
            for (x in list) sq += (x - mean) * (x - mean)
            out.text = "Дисперсия: %.3f".format(sq / list.size)
        }
        return column().apply {
            addView(numbers)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Чётность ----------

    private fun parityScreen(): View {
        // Чётное или нечётное. Ноль чётный, и мы не будем это обсуждать.
        val input = EditText(this).apply { hint = "Число"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val check = Button(this).apply { text = "Проверить" }
        check.setOnClickListener {
            val n = input.text.toString().toIntOrNull() ?: 0
            out.text = if (n % 2 == 0) "Чётное" else "Нечётное"
        }
        return column().apply {
            addView(input)
            addView(check)
            addView(out)
        }
    }

    // ---------- Расстояние между точками ----------

    private fun distanceScreen(): View {
        // Расстояние между точками на плоскости по теореме Пифагора. Координаты вводим через пробел: x y x y.
        val input = EditText(this).apply { hint = "x1 y1 x2 y2" }
        val out = label("")
        val calc = Button(this).apply { text = "Расстояние" }
        calc.setOnClickListener {
            val parts = input.text.toString().trim().split(" ").mapNotNull { it.toDoubleOrNull() }
            if (parts.size != 4) {
                out.text = "Нужно четыре числа через пробел"
                return@setOnClickListener
            }
            val dx = parts[2] - parts[0]
            val dy = parts[3] - parts[1]
            out.text = "Расстояние: %.3f".format(Math.sqrt(dx * dx + dy * dy))
        }
        return column().apply {
            addView(input)
            addView(calc)
            addView(out)
        }
    }

    // ---------- Лента ----------

    private fun tallyScreen(): View {
        // Счётные палочки: каждая пятая — перечёркивает четыре предыдущие. Рисуем их символами.
        val count = EditText(this).apply { hint = "Сколько"; inputType = InputType.TYPE_CLASS_NUMBER }
        val out = label("", 18f)
        val draw = Button(this).apply { text = "Нарисовать палочки" }
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
            out.text = sb.toString().trim().ifEmpty { "Палочек нет" }
        }
        return scroll(column().apply {
            addView(count)
            addView(draw)
            addView(out)
        })
    }

    // ---------- Угол ----------

    private fun angleScreen(): View {
        // Градусы в радианы и обратно. Число пи здесь честное, из Math, а не из пятого класса.
        val input = EditText(this).apply { hint = "Градусы"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val calc = Button(this).apply { text = "В радианы" }
        calc.setOnClickListener {
            val deg = input.text.toString().toDoubleOrNull() ?: 0.0
            out.text = "%.5f рад".format(Math.toRadians(deg))
        }
        val back = Button(this).apply { text = "В градусы" }
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

    // ---------- Обмен ----------

    private fun swapScreen(): View {
        // Меняем местами два числа через третью переменную. Без арифметики, потому что арифметика — это хитрость.
        val a = EditText(this).apply { hint = "a"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val b = EditText(this).apply { hint = "b"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val out = label("")
        val swap = Button(this).apply { text = "Поменять местами" }
        swap.setOnClickListener {
            var x = a.text.toString()
            var y = b.text.toString()
            val tmp = x
            x = y
            y = tmp
            a.setText(x)
            b.setText(y)
            out.text = "Теперь a = $x, b = $y"
        }
        return column().apply {
            addView(a)
            addView(b)
            addView(swap)
            addView(out)
        }
    }
}
