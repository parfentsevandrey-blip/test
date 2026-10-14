package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Инструменты 1980-х, которых не было в первой редакции: ассемблеры, компиляторы, оболочки, связь, вёрстка. */
val db1ExtraTools: List<RecordEntity> = listOf(

    // ---------- Компиляторы и ассемблеры ----------

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Ассемблер MASM",
        originalTitle = "Microsoft Macro Assembler (MASM)",
        author = "Microsoft",
        year = 1981,
        summary = "Макроассемблер Microsoft (1981) для x86: стандарт для ассемблерных программ на PC.",
        details = "MASM поддерживал макросы и условную компиляцию. Игры и системные программы 80-х часто писали на нём.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Компилятор Turbo C",
        originalTitle = "Borland Turbo C",
        author = "Borland",
        year = 1987,
        summary = "Быстрый компилятор C от Borland (1987) с интегрированной средой для MS-DOS.",
        details = "Turbo C объединил редактор, компилятор и отладчик. Компилятор работал очень быстро на 8088 и 80286.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Microsoft C",
        originalTitle = "Microsoft C 1.0",
        author = "Microsoft",
        year = 1983,
        summary = "Первый компилятор C от Microsoft (1983) для MS-DOS: переносимый код для PC.",
        details = "Microsoft C конкурировал с Lattice C. Компиляторы этой эпохи различались в библиотеках и расширениях языка.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Компилятор Lattice C",
        originalTitle = "Lattice C",
        author = "Lattice Inc.",
        year = 1982,
        summary = "Один из первых компиляторов C для PC (1982): популярен у программистов CP/M и MS-DOS.",
        details = "Lattice C продавался за умеренную цену и был одним из первых коммерческих компиляторов C на PC.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Ассемблер Turbo Assembler",
        originalTitle = "Turbo Assembler (TASM)",
        author = "Borland",
        year = 1988,
        summary = "Ассемблер Borland (1988), совместимый с синтаксисом MASM.",
        details = "TASM поддерживал синтаксис MASM. Его использовали разработчики игр и драйверов.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Препроцессор M4",
        originalTitle = "m4",
        author = "Брайан Керниган, Дэннис Ритчи",
        year = 1977,
        summary = "Универсальный макропроцессор Unix (1977): подставляет текст по шаблонам. На нём построены autoconf и конфигурация sendmail.",
        details = "M4 работал с текстом любого языка. Его применяли для генерации сценариев и заготовок программ.",
        rare = true,
    ),

    // ---------- Калькуляторы и математика ----------

    entry(
        kind = Kind.TOOL,
        section = "Компиляторы и генераторы",
        title = "Калькулятор bc",
        originalTitle = "bc",
        author = "Лоринда Черри, Роберт Моррис, Bell Labs",
        year = 1975,
        summary = "Калькулятор произвольной точности Unix (1975): язык с переменными, функциями и любой точностью.",
        details = "bc использовал язык, похожий на C, и часто стоял в конвейерах оболочки. Его применяли для точных вычислений, например для π.",
        rare = true,
    ),

    // ---------- Командная строка ----------

    entry(
        kind = Kind.TOOL,
        section = "Командная строка",
        title = "Оболочка csh",
        originalTitle = "C shell (csh)",
        author = "Билл Джой, Беркли",
        year = 1978,
        summary = "Оболочка Unix с синтаксисом, похожим на C (1978): история команд, псевдонимы и задания.",
        details = "csh появилась в Беркли и стала альтернативой Bourne shell. Её синтаксис часто ругали, но её возможности были новыми.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Командная строка",
        title = "Оболочка ksh",
        originalTitle = "Korn shell (ksh)",
        author = "Дэвид Корн, Bell Labs",
        year = 1983,
        summary = "Оболочка Корна (1983): совмещает синтаксис Bourne shell с удобствами csh.",
        details = "ksh стала стандартом для системных скриптов Unix System V. Многие её возможности позже появились в bash.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Командная строка",
        title = "Оболочка bash",
        originalTitle = "Bourne Again Shell (bash)",
        author = "Брайан Фокс, проект GNU",
        year = 1989,
        summary = "Оболочка проекта GNU (1989): совместима с Bourne shell и содержит идеи csh и ksh.",
        details = "bash стала стандартной оболочкой Linux и macOS. Её синтаксис до сих пор лежит в основе многих скриптов.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Командная строка",
        title = "Утилита gawk",
        originalTitle = "GNU awk (gawk)",
        author = "Пол Рубин и Джей Фенлсон, проект GNU",
        year = 1988,
        summary = "Свободная реализация AWK от проекта GNU (1988) с расширениями для текста и массивов.",
        details = "gawk развивал AWK дальше исходной версии и добавил к нему расширения. Часто используется в скриптах обработки данных.",
        rare = true,
    ),

    // ---------- Связь и передача файлов ----------

    entry(
        kind = Kind.TOOL,
        section = "Связь и передача файлов",
        title = "Протокол Kermit",
        originalTitle = "Kermit",
        author = "Университет Колумбия",
        year = 1981,
        summary = "Протокол и программа передачи файлов (1981) между разными компьютерами через последовательный порт.",
        details = "Kermit работала почти на любой машине. Её использовали для переноса файлов между мейнфреймами, PC и Unix.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Связь и передача файлов",
        title = "Протокол XMODEM",
        originalTitle = "XMODEM",
        author = "Уорд Кристенсен",
        year = 1977,
        summary = "Протокол передачи файлов по модему (1977) с контрольной суммой блоков: основа многих BBS.",
        details = "XMODEM передавал файл блоками по 128 байт с проверкой. Позже появились YMODEM и ZMODEM.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Связь и передача файлов",
        title = "Утилита uucp",
        originalTitle = "uucp (Unix to Unix copy)",
        author = "Дэвид Новитц, Майкл Лэск",
        year = 1978,
        summary = "Передача файлов и почты между машинами Unix по телефонным линиям (1978).",
        details = "uucp работала по расписанию через модем. Сети uucp связывали университеты и компании до появления Интернета.",
        rare = true,
    ),

    // ---------- Документы и вёрстка ----------

    entry(
        kind = Kind.TOOL,
        section = "Документы и вёрстка",
        title = "Система вёрстки LaTeX",
        originalTitle = "LaTeX",
        author = "Лесли Лэмпорт",
        year = 1984,
        summary = "Надстройка над TeX (1984): структура документа задаётся командами разделов, формул и ссылок.",
        details = "LaTeX позволил писать научные статьи и книги без знания низкоуровневого TeX. Стал стандартом для научных публикаций.",
    ),
)
