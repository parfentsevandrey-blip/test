package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Языки 1980-х, которых не было в первой редакции: объектные, параллельные, Lisp-диалекты, экзотика. */
val db1ExtraLanguages: List<RecordEntity> = listOf(

    // ---------- Объектные и параллельные ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык C++",
        originalTitle = "C++",
        author = "Бьёрн Страуструп, Bell Labs",
        year = 1983,
        summary = "C с классами (1979), названный C++ в 1983 году; объектное программирование на базе C.",
        details = "Страуструп расширил C классами, виртуальными функциями и перегрузкой операторов. Первое издание книги «The C++ Programming Language» вышло в 1985 году. " +
            "Язык стал главным для коммерческой разработки с конца 80-х.",
        example = """
            #include <iostream>

            class Counter {
            public:
                void increment() { ++value; }
                int get() const { return value; }
            private:
                int value = 0;
            };

            int main() {
                Counter c;
                c.increment();
                c.increment();
                std::cout << c.get() << "\n";
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Objective-C",
        originalTitle = "Objective-C",
        author = "Брэд Кокс, Том Лав",
        year = 1984,
        summary = "C с объектами в стиле Smalltalk (1984): сообщения вместо прямых вызовов функций.",
        details = "Кокс и Лав добавили к C синтаксис сообщений Smalltalk. Язык выбрала компания NeXT, а позже он стал основным для Apple.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Eiffel",
        originalTitle = "Eiffel",
        author = "Бертран Мейер",
        year = 1986,
        summary = "Объектный язык с проектированием по контракту: предусловия и постусловия входят в язык.",
        details = "Eiffel стал практическим воплощением «проектирования по контракту», которое Мейер ввёл для этого языка. Язык использовался в финансовой сфере.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Self",
        originalTitle = "Self",
        author = "Дэвид Унгар, Рэнди Смит",
        year = 1987,
        summary = "Прототипное объектное программирование (1987): объекты создаются копированием, без классов.",
        details = "Self повлиял на JIT-компиляцию и на прототипы JavaScript. Язык был экспериментальным, но многое из него стало стандартом.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Beta",
        originalTitle = "BETA",
        author = "Мадсен, Меллер-Педерсен, Нигаард (Орхусский университет)",
        year = 1983,
        summary = "Объектный язык (1983), где классы, процедуры и сопрограммы объединены одной конструкцией.",
        details = "Beta — наследник Simula. В нём есть «паттерны», которые объединяют понятия классов и процедур.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык CLU",
        originalTitle = "CLU",
        author = "Барбара Лисков и коллеги, MIT",
        year = 1974,
        summary = "Язык абстракций данных (1974): первые итераторы и абстрактные типы в языке.",
        details = "CLU ввёл абстрактные типы данных и итераторы. Его идеи повлияли на Ada и на последующие языки с абстракциями.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Linda",
        originalTitle = "Linda",
        author = "Дэвид Гелернтер, Yale",
        year = 1985,
        summary = "Модель координации (1985): процессы обмениваются данными через общее «пространство кортежей».",
        details = "Linda не язык в обычном смысле, а набор операций поверх языка. Её идея легла в основу многих параллельных систем.",
        rare = true,
    ),

    // ---------- Функциональные и логические ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Common Lisp",
        originalTitle = "Common Lisp",
        author = "Гай Стил и коллеги",
        year = 1984,
        summary = "Common Lisp (1984): единый Lisp, собранный из диалектов Maclisp, Zetalisp и других.",
        details = "Книга «Common Lisp: The Language» (1984) задала общий язык для всей семьи Lisp. Его использовали в ИИ, экспертных системах и CAD.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Franz Lisp",
        originalTitle = "Franz Lisp",
        author = "Джон Фодераро, Кит Склауэр, Беркли",
        year = 1980,
        summary = "Lisp для Unix (1980) из Беркли: один из первых Lisp, работавших на мини-ЭВМ под Unix.",
        details = "Franz Lisp использовали в университетах для исследований и обучения ИИ.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Zetalisp",
        originalTitle = "Zetalisp",
        author = "MIT и Symbolics",
        year = 1981,
        summary = "Диалект Lisp для Lisp-машин MIT и Symbolics (1981): объектная система Flavors и окна.",
        details = "Zetalisp стал основой среды программирования на Lisp-машинах. Его ценили за интерактивность и отладку.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык T",
        originalTitle = "T",
        author = "Джонатан Рис, Норман Адамс, Yale",
        year = 1984,
        summary = "Минималистичный диалект Scheme (1984) из Yale: лексическая область видимости и простота.",
        details = "T показал, что Scheme можно сделать очень маленьким и при этом практичным. Язык стал учебным для исследований компиляторов.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Hope",
        originalTitle = "Hope",
        author = "Родни Бёрсталл, Дэвид Макквин, Дональд Сэннелла",
        year = 1980,
        summary = "Чисто функциональный язык Эдинбурга (1980) с сопоставлением с образцом и ленивыми списками.",
        details = "Hope стал одним из предшественников Miranda и Haskell. Язык изучали в университетах Великобритании.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык FP",
        originalTitle = "FP",
        author = "Джон Бэкус",
        year = 1977,
        summary = "Функциональный язык Бэкуса (1977): программы строятся из функций высшего порядка без переменных.",
        details = "Бэкус предложил FP в лекции «Можно ли освободить программирование от стиля фон Неймана?». Идея повлияла на функциональное программирование 80-х.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Parlog",
        originalTitle = "Parlog",
        author = "Кит Кларк, Стив Грегори, Imperial College",
        year = 1986,
        summary = "Параллельный Prolog (1986): несколько целей доказываются одновременно.",
        details = "Parlog относится к тем же работам по параллельной логике, что и японский проект ICOT, и опирается на идеи Concurrent Prolog.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык KL1",
        originalTitle = "KL1",
        author = "Японский проект ICOT «Компьютеры пятого поколения»",
        year = 1985,
        summary = "Параллельный логический язык японского проекта (1985) для многопроцессорных машин PIM.",
        details = "KL1 строился на параллельных логических программах. Проект ICOT стал одним из крупнейших экспериментов с логическим программированием.",
        rare = true,
    ),

    // ---------- Императивные и системные ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Modula-3",
        originalTitle = "Modula-3",
        author = "Люк Кардели, Джеймс Донахью и коллеги, DEC SRC",
        year = 1988,
        summary = "Наследник Modula-2 (1988) с объектами, исключениями и потоками в самом языке.",
        details = "Modula-3 создавали в исследовательском центре DEC SRC. Язык выбрали для системного программирования, но широкого распространения он не получил.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Euclid",
        originalTitle = "Euclid",
        author = "Батлер Лэмпсон и коллеги, Xerox PARC",
        year = 1977,
        summary = "Язык для верифицируемых программ (1977): проверки и спецификации встроены в код.",
        details = "Euclid задумывался для системного программирования с возможностью доказать корректность. Его идеи повлияли на последующие языки семейства Pascal.",
        rare = true,
    ),

    // ---------- Учебные и экзотические ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Учебные и экзотические",
        title = "Язык QuickBASIC",
        originalTitle = "Microsoft QuickBASIC",
        author = "Microsoft",
        year = 1985,
        summary = "Компилируемый BASIC от Microsoft (1985): интегрированная среда и быстрый код вместо интерпретатора.",
        details = "QuickBASIC сочетал редактор с компилятором и отладчиком. Он заменил медленный интерпретатор BASIC на MS-DOS.",
    ),

    // ---------- Скрипты и языки описания ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык Scribe",
        originalTitle = "Scribe",
        author = "Брайан Рейд, Carnegie Mellon",
        year = 1980,
        summary = "Язык разметки документов (1980): структура текста задаётся командами, а не оформлением.",
        details = "Scribe повлиял на LaTeX. Его идея разделения содержания и оформления живёт в современных языках разметки.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык HyperTalk",
        originalTitle = "HyperTalk",
        author = "Билл Аткинсон, Apple",
        year = 1987,
        summary = "Скриптовый язык HyperCard (1987): программы пишут прямо на карточках, на английском языке.",
        details = "HyperTalk позволил непрограммистам создавать приложения из карточек и кнопок. Язык повлиял на идеи скриптов в интерфейсах.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык Clipper",
        originalTitle = "Clipper",
        author = "Nantucket Corporation",
        year = 1985,
        summary = "Компилятор языка dBase (1985): программы на dBase превращались в исполняемые файлы.",
        details = "Clipper компилировал программы dBase в самостоятельные исполняемые файлы, которые работали быстрее интерпретатора.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык Tcl",
        originalTitle = "Tcl (Tool Command Language)",
        author = "Джон Устерхаут, Беркли",
        year = 1988,
        summary = "Встраиваемый скриптовый язык (1988): всё — строки, а команды можно добавлять из программы на C.",
        details = "Tcl создан для управления инструментами проектирования. Позже вместе с Tk он дал быстрый способ строить интерфейсы из скриптов.",
        rare = true,
    ),
)
