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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

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
}

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var tabsRow: LinearLayout

    private val modules: List<Pair<String, () -> View>> = listOf(
        "Калькулятор" to { calculatorScreen() },
        "Звонилка" to { dialerScreen() },
        "Блокнот" to { notesScreen() },
        "Контакты" to { contactsScreen() },
        "Всезнайка" to { oracleScreen() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Knowitall.prefs = getSharedPreferences("everything", MODE_PRIVATE)
        Knowitall.notes = Knowitall.prefs.getString("notes", "") ?: ""
        Knowitall.contacts = Knowitall.prefs.getString("contacts", "") ?: ""

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content = FrameLayout(this)

        root.addView(tabsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        modules.forEachIndexed { i, (title, _) ->
            tabsRow.addView(
                Button(this).apply {
                    text = title
                    textSize = 10f
                    setOnClickListener { showModule(i) }
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
}
