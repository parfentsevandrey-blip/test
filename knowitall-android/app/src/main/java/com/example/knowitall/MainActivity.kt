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
        "Будильник" to { alarmScreen() }
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
}
