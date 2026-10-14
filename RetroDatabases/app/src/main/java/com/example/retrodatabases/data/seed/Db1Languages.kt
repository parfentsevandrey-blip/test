package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Языки программирования 1980-х и их предки: от массовых до почти забытых. */
val db1Languages: List<RecordEntity> = listOf(

    // ---------- Императивные и системные ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Pascal",
        originalTitle = "Pascal",
        author = "Никлаус Вирт",
        year = 1970,
        summary = "Строгий учебный язык с типами, блоками и процедурами; приучал писать аккуратно.",
        details = "Вирт создал Pascal в 1970 году как язык для обучения программированию. " +
            "В 80-х он стал основным языком вузовских курсов, а Turbo Pascal (1983) сделал его массовым на PC. " +
            "Компилятор проверял типы до запуска, и для тех лет это было большим шагом.",
        example = """
            program Average;
            var
              a, b, sum: integer;
            begin
              a := 7;
              b := 5;
              sum := a + b;
              writeln('Sum = ', sum);
            end.
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык C",
        originalTitle = "C",
        author = "Деннис Ритчи",
        year = 1972,
        summary = "Язык системного программирования, на котором переписали Unix.",
        details = "Ритчи создал C в 1972 году, а в 1973-м ядро Unix переписали на нём. " +
            "Книга Кернигана и Ритчи «The C Programming Language» (1978) стала учебником десятилетия. " +
            "Стандарт ANSI C (C89) принят в 1989 году. Язык дал переносимость: один исходник собирался на разных машинах.",
        example = """
            #include <stdio.h>

            int main(void)
            {
                int i;
                for (i = 1; i <= 5; i++)
                    printf("%d\n", i * i);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Ada",
        originalTitle = "Ada",
        author = "Жан Ишбиа и команда по заказу Минобороны США",
        year = 1983,
        summary = "Язык для больших оборонных систем: пакеты, строгие типы, исключения и параллельные задачи.",
        details = "Ada создали по заказу Министерства обороны США. Стандарт 1983 года получил номер MIL-STD-1815 " +
            "в честь года рождения Ады Лавлейс. Язык был тяжёлым, но предлагал пакеты с отдельным интерфейсом, " +
            "обработку исключений и параллельные задачи прямо в языке.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Modula-2",
        originalTitle = "Modula-2",
        author = "Никлаус Вирт",
        year = 1978,
        summary = "Наследник Pascal с модулями: интерфейс и реализация разделены, компиляция раздельная.",
        details = "Вирт разработал Modula-2 в конце 1970-х в ETH Zurich. Модуль состоит из описания интерфейса " +
            "и реализации, поэтому большие программы собирались по частям. Язык преподавали в Европе, " +
            "а операционную систему Medos-2 написали на нём.",
        example = """
            DEFINITION MODULE Stack;
              PROCEDURE Push(x: INTEGER);
              PROCEDURE Pop(): INTEGER;
            END Stack.
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Oberon",
        originalTitle = "Oberon",
        author = "Никлаус Вирт, Юрг Гутнехт",
        year = 1987,
        summary = "Упрощённый наследник Modula-2 с расширением типов; операционную систему написали на нём целиком.",
        details = "Вирт и Гутнехт создали язык вместе с операционной системой Oberon System. Языка меньше, чем у Modula-2: " +
            "объекты появляются через расширение записей, а не через отдельные конструкции.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык Mesa",
        originalTitle = "Mesa",
        author = "Команда Xerox PARC",
        year = 1977,
        summary = "Язык Xerox PARC для системного программирования: на нём писали ПО рабочих станций Alto и Star.",
        details = "Mesa создавали для больших систем с модулями и строгой проверкой интерфейсов. " +
            "Большая часть программного обеспечения рабочих станций Xerox написана на нём.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Concurrent Pascal",
        originalTitle = "Concurrent Pascal",
        author = "Пер Бринч Хансен",
        year = 1975,
        summary = "Pascal с параллельными процессами и мониторами: общие данные защищены структурой языка.",
        details = "Бринч Хансен ввёл мониторы в 1975 году. На Concurrent Pascal написана экспериментальная " +
            "операционная система Solo (1976), в которой параллельность проверялась прямо компилятором.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык PL/M",
        originalTitle = "PL/M",
        author = "Intel",
        year = 1973,
        summary = "Язык Intel для процессоров 8080 и 8086: Pascal-подобный синтаксис и низкоуровневые возможности.",
        details = "Создан для программирования микропроцессоров Intel. На PL/M написана CP/M — первая массовая " +
            "операционная система для 8-битных машин.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык BCPL",
        originalTitle = "BCPL",
        author = "Мартин Ричардс",
        year = 1967,
        summary = "Простой язык для системного программирования, предок языков B и C.",
        details = "Ричардс создал BCPL в Кембридже. Язык компилировался на разные машины через промежуточный код, " +
            "поэтому переносился легко. Из BCPL выросли B (Томпсон), а затем C.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Императивные и системные",
        title = "Язык B",
        originalTitle = "B",
        author = "Кен Томпсон",
        year = 1969,
        summary = "Язык без типов для миникомпьютера PDP-7 — промежуточный шаг от BCPL к C.",
        details = "Томпсон упростил BCPL и создал B для PDP-7, где работала ранняя версия Unix. " +
            "Затем Ритчи добавил типы и получил C.",
        rare = true,
    ),

    // ---------- Научные и деловые ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Научные и деловые",
        title = "Язык FORTRAN",
        originalTitle = "FORTRAN",
        author = "Команда Джона Бэкуса, IBM",
        year = 1957,
        summary = "Старейший язык для научных расчётов; в 80-х на нём считали физику, механику и метеорологию.",
        details = "FORTRAN (FORmula TRANslator) появился в IBM в 1957 году. Язык оптимизировали под числа и массивы, " +
            "поэтому численные библиотеки 80-х часто писали на нём.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Научные и деловые",
        title = "Язык COBOL",
        originalTitle = "COBOL",
        author = "Комитет CODASYL, при участии Грейс Хоппер",
        year = 1959,
        summary = "Деловой язык банков и страховых: описания данных и фразы, похожие на английский.",
        details = "COBOL создал комитет CODASYL в 1959 году. В 80-х миллионы строк COBOL работали в банках " +
            "и госучреждениях; стандарты COBOL-74 и COBOL-85 закрепили язык.",
        example = """
                   IDENTIFICATION DIVISION.
                   PROGRAM-ID. HELLO.
                   PROCEDURE DIVISION.
                       DISPLAY 'HELLO, WORLD'.
                       STOP RUN.
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Научные и деловые",
        title = "Язык ALGOL 60",
        originalTitle = "ALGOL 60",
        author = "Международный комитет",
        year = 1960,
        summary = "Образец для будущих языков: блоки, рекурсия и форма записи синтаксиса BNF.",
        details = "Отчёт 1960 года задал структуру блочных языков. Форма Бэкуса — Наура (BNF) для описания синтаксиса " +
            "появилась именно здесь. Алгол-60 использовали и в СССР: для БЭСМ-6 были трансляторы.",
        example = """
            begin
              integer i, s;
              s := 0;
              for i := 1 step 1 until 10 do
                s := s + i
            end
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Научные и деловые",
        title = "Язык ALGOL 68",
        originalTitle = "ALGOL 68",
        author = "Комитет IFIP",
        year = 1968,
        summary = "Сложный язык с гибкой системой типов и выражениями; мощный, но трудный в реализации.",
        details = "Алгол-68 задумывали как преемника Алгола-60. Богатая система типов и выражения-операторы были мощными, " +
            "но компиляторы получались громоздкими, и язык так и не стал массовым.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Научные и деловые",
        title = "Язык PL/I",
        originalTitle = "PL/I",
        author = "IBM",
        year = 1964,
        summary = "Универсальный язык IBM: задумывался как замена сразу FORTRAN, COBOL и ALGOL.",
        details = "Получился сложным: много конструкций и исключений. Язык жил в мейнфреймах IBM и в 80-х оставался " +
            "в больших корпоративных системах.",
        rare = true,
    ),

    // ---------- Объектные и параллельные ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Simula 67",
        originalTitle = "Simula 67",
        author = "Оле-Йохан Даль, Кристен Нигаард",
        year = 1967,
        summary = "Первый язык с классами, объектами и наследованием; предок Smalltalk и C++.",
        details = "Язык создали в Норвегии для моделирования систем. Классы, объекты и виртуальные процедуры, " +
            "которые Simula ввела первой, позже стали основой объектно-ориентированного программирования.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Smalltalk-80",
        originalTitle = "Smalltalk-80",
        author = "Алан Кей и команда Xerox PARC",
        year = 1980,
        summary = "Программа — набор объектов, которые хранят состояние и общаются сообщениями.",
        details = "Smalltalk-80 выпустили в Xerox PARC в 1980 году. Он сделал ООП практичным: классы, наследование, " +
            "окна и мышь. В начале 80-х появились C++ (Страуструп, 1983) и Objective-C; Smalltalk задал образец " +
            "интерактивной среды разработки.",
        example = """
            "Создаём счёт и кладём 100"
            | account |
            account := BankAccount new.
            account deposit: 100.
            account balance printNl.
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Occam",
        originalTitle = "Occam",
        author = "INMOS, Дэвид Мэй",
        year = 1983,
        summary = "Язык параллельных процессов для транспьютеров: процессы и каналы описаны прямо в языке.",
        details = "Occam основан на модели CSP Хоара. Программа — набор процессов, которые обмениваются данными по каналам; " +
            "параллельность здесь часть языка, а не библиотека.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Объектные и параллельные",
        title = "Язык Erlang",
        originalTitle = "Erlang",
        author = "Джо Армстронг, Ericsson",
        year = 1986,
        summary = "Язык для телефонных систем: лёгкие процессы, сообщения и перезапуск сбойных частей.",
        details = "Erlang создали внутри Ericsson для надёжных телекоммуникационных систем. Публично язык вышел только " +
            "в 1998 году, но идея «пусть падает и перезапускается» уже работала в 80-х.",
        rare = true,
    ),

    // ---------- Функциональные и логические ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Lisp",
        originalTitle = "Lisp",
        author = "Джон Маккарти",
        year = 1958,
        summary = "Программы — списки и функции. В 80-х Lisp-машины и Common Lisp были на пике.",
        details = "Маккарти создал Lisp в 1958 году для обработки символов и задач ИИ. Common Lisp (спецификация 1984) " +
            "объединил диалекты; Lisp-машины стоили дорого, но задавали стиль разработки.",
        example = """
            (defun factorial (n)
              (if (<= n 1)
                  1
                  (* n (factorial (- n 1)))))
            (factorial 5)  ; => 120
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Scheme",
        originalTitle = "Scheme",
        author = "Джеральд Сассман, Гай Стил",
        year = 1975,
        summary = "Минималистичный диалект Lisp с лексической областью видимости; учебный язык MIT.",
        details = "Scheme создали в MIT в 1975 году. Язык положили в основу книги SICP (1985) — одного из самых " +
            "известных учебников 80-х.",
        example = """
            (define (fact n)
              (if (= n 0) 1 (* n (fact (- n 1)))))
            (fact 5)  ; => 120
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык ML",
        originalTitle = "ML, Standard ML",
        author = "Роберт Милнер, Эдинбургский университет",
        year = 1973,
        summary = "Функциональный язык с выводом типов и модулями; предок OCaml и F#.",
        details = "ML появился в Эдинбургском университете как язык для доказательств. Вывод типов без лишних аннотаций " +
            "и модульная система сделали его образцом для языков следующего поколения.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Miranda",
        originalTitle = "Miranda",
        author = "Дэвид Тёрнер",
        year = 1985,
        summary = "Ленивый чисто функциональный язык, непосредственный предшественник Haskell.",
        details = "Miranda появилась в 1985 году как коммерческий язык с удобным сопоставлением с образцом. " +
            "Её идеи вошли в Haskell, созданный в 1990 году.",
        example = """
            fac 0 = 1
            fac n = n * fac (n - 1)
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Функциональные и логические",
        title = "Язык Prolog",
        originalTitle = "Prolog",
        author = "Ален Колмерауэр, Филипп Руссель",
        year = 1972,
        summary = "Программа — факты и правила; вычисление — поиск доказательства.",
        details = "Prolog придумали в Марсельском университете в 1972 году. В 1982-м японский проект «Компьютеры пятого " +
            "поколения» сделал логическое программирование модным. В 80-х Prolog использовали в экспертных системах " +
            "и разборе естественного языка.",
        example = """
            parent(anna, boris).
            parent(boris, vera).

            grandparent(X, Z) :- parent(X, Y), parent(Y, Z).

            % ?- grandparent(anna, vera).   → true
        """,
    ),

    // ---------- Учебные и экзотические ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Учебные и экзотические",
        title = "Язык BASIC",
        originalTitle = "BASIC",
        author = "Джон Кемени, Томас Курц",
        year = 1964,
        summary = "Простой язык для новичков: в 80-х на нём писали почти все домашние компьютеры.",
        details = "Кемени и Курц создали BASIC в Дартмутском колледже. Диалекты были в ПЗУ ZX Spectrum, Commodore 64 " +
            "и Apple II, в комплекте шёл BASICA для IBM PC, а в СССР — в БК-0010.",
        example = """
            10 PRINT "HELLO"
            20 GOTO 10
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Учебные и экзотические",
        title = "Язык Logo",
        originalTitle = "Logo",
        author = "Уолли Файгенбаум, Сеймур Пейперт, Синтия Соломон",
        year = 1967,
        summary = "Язык-черепашка: команды двигают экранного исполнителя и рисуют узоры.",
        details = "Logo создали для обучения детей программированию. Пейперт описал идеи в книге «Mindstorms» (1980): " +
            "ребёнок учится думать о процессах, управляя черепашкой. В 80-х Logo появился на школьных компьютерах.",
        example = """
            REPEAT 4 [FORWARD 100 RIGHT 90]
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Учебные и экзотические",
        title = "Язык Forth",
        originalTitle = "Forth",
        author = "Чарльз Мур",
        year = 1970,
        summary = "Стековый язык: мало ключевых слов, компактный код, быстрая работа на слабых машинах.",
        details = "Мур создал Forth в конце 1960-х для управления телескопами. Программа — словарь слов, " +
            "и каждое слово работает со стеком. В 80-х Forth ценили в робототехнике и управлении, где память и скорость " +
            "были на вес золота.",
        example = """
            : SQUARE  DUP * ;
            5 SQUARE .      \ печатает 25
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Учебные и экзотические",
        title = "Язык APL",
        originalTitle = "APL",
        author = "Кеннет Айверсон",
        year = 1962,
        summary = "Язык для массивов: одна строка заменяет целый цикл, а код записывают спецсимволами.",
        details = "Айверсон описал нотацию в книге «A Programming Language» (1962). Программисты APL работали " +
            "с клавиатурой, где были математические знаки, и писали очень короткий код.",
        example = """
            +/⍳10   ⍝ сумма чисел от 1 до 10: 55
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Учебные и экзотические",
        title = "Язык Icon",
        originalTitle = "Icon",
        author = "Ральф Гризволд",
        year = 1977,
        summary = "Язык обработки строк: поиск и перебор результатов — встроенные генераторы.",
        details = "Гризволд создал Icon в Аризонском университете. Выражение может возвращать несколько значений " +
            "по очереди, поэтому поиск в тексте записывается коротко.",
        example = """
            every write(find("a", "banana"))   # печатает 2, 4 и 6
        """,
        rare = true,
    ),

    // ---------- Скрипты и языки описания ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык AWK",
        originalTitle = "AWK",
        author = "Ахо, Вайнбергер, Керниган",
        year = 1977,
        summary = "Язык обработки таблиц: для каждой строки задаётся шаблон и действие.",
        details = "Имя языка составлено из фамилий авторов. В 80-х AWK был стандартным инструментом Unix " +
            "для отчётов по текстовым данным.",
        example = """
            awk '{ sum += $2 } END { print sum }' data.txt
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык Perl",
        originalTitle = "Perl",
        author = "Ларри Волл",
        year = 1987,
        summary = "Скриптовый язык для отчётов и обработки текста с регулярными выражениями.",
        details = "Perl 1.0 вышел в 1987 году. Удобство работы с текстом сделало его любимцем системных администраторов, " +
            "а позже — основой ранних веб-программ.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык REXX",
        originalTitle = "REXX",
        author = "Майк Коушлоу, IBM",
        year = 1979,
        summary = "Читаемый скриптовый язык IBM для VM/CMS, позже — для OS/2.",
        details = "Коушлоу создал REXX в IBM в 1979 году. Язык заменял сложные командные файлы и был понятен " +
            "людям без опыта программирования.",
        example = """
            /* REXX: сумма от 1 до 10 */
            total = 0
            do i = 1 to 10
              total = total + i
            end
            say 'Сумма =' total
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык SQL",
        originalTitle = "SQL (SEQUEL)",
        author = "Дональд Чемберлин, Рэй Бойс, IBM",
        year = 1974,
        summary = "Язык запросов: описываешь, какие данные нужны, а не как их достать.",
        details = "SEQUEL появился в IBM в 1974 году. Коммерческие СУБД 80-х реализовали язык по-своему; в 1986 году " +
            "появился стандарт ANSI SQL-86, в 1987-м его приняла ISO.",
        example = """
            SELECT title, year
            FROM records
            WHERE kind = 'LANGUAGE'
            ORDER BY year;
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Скрипты и языки описания",
        title = "Язык PostScript",
        originalTitle = "PostScript",
        author = "Джон Уорноук, Чарльз Гешке (Adobe)",
        year = 1982,
        summary = "Стековый язык описания страниц: принтер исполняет программу, которая рисует страницу.",
        details = "Adobe создала PostScript в 1982 году. Линии, текст и шрифты задавались командами, поэтому одна страница " +
            "печаталась одинаково на любом совместимом принтере.",
        example = """
            72 72 moveto
            200 200 lineto
            stroke
            showpage
        """,
    ),
)
