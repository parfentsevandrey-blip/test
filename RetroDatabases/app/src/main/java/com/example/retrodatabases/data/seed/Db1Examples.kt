package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Примеры применения: короткие рецепты, которые решают настоящие задачи 80-х. */
val db1Examples: List<RecordEntity> = listOf(

    entry(
        kind = Kind.EXAMPLE,
        section = "Текстовые данные",
        title = "Частота слов в тексте",
        originalTitle = "Word frequency with tr, sort, uniq",
        author = "Практика Unix",
        year = 1973,
        summary = "Одной строкой оболочки узнаём, какие слова в тексте встречаются чаще всего.",
        details = "tr заменяет всё, что не является буквой, на перевод строки, и получается по одному слову на строке. " +
            "sort ставит одинаковые слова рядом, uniq -c считает повторы, а последний sort -rn выводит самые частые слова вверху. " +
            "Так в 80-х анализировали документацию и тексты.",
        instructions = """
            1. Сохраните любой текст в файл book.txt.
            2. Выполните команду из примера.
            3. В выводе слева — число повторов, справа — слово; вверху самые частые.
        """,
        example = """
            tr -cs 'A-Za-z' '\n' < book.txt | tr 'A-Z' 'a-z' | sort | uniq -c | sort -rn | head -5
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Текстовые данные",
        title = "Массовая замена в тексте: sed",
        originalTitle = "sed substitution and line selection",
        author = "Ли Макмахон, Bell Labs",
        year = 1974,
        summary = "Замена слова во всех строках файла без открытия редактора.",
        details = "Команда s/шаблон/замена/g меняет все вхождения в каждой строке; флаг g означает «все», а не только первое. " +
            "Адреса вроде 2,4 выбирают строки, а /шаблон/d удаляет подходящие строки. Результат удобно направлять в новый файл, " +
            "чтобы исходный текст остался целым.",
        instructions = """
            1. Создайте notes.txt с несколькими строками, одна из которых содержит слово Unix.
            2. Выполните три команды из примера по очереди.
            3. Чтобы сохранить результат, добавьте > notes-new.txt к команде.
        """,
        example = """
            sed 's/Unix/UNIX/g' notes.txt
            sed -n '2,4p' notes.txt
            sed '/^#/d' notes.txt
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Журналы и отчёты",
        title = "Топ ошибок в журнале",
        originalTitle = "Error report with grep, cut, sort, uniq",
        author = "Практика Unix",
        year = 1973,
        summary = "Из длинного журнала выделяем строки с ошибками и считаем, какие коды встречаются чаще всего.",
        details = "grep отбирает строки с ERROR, cut вырезает третье поле, sort и uniq -c считают повторы, sort -rn выводит рейтинг. " +
            "Так в 80-х строили отчёты без программирования: конвейер работал на любой Unix-машине и не требовал компилятора.",
        instructions = """
            1. Создайте файл errors.log с таким содержимым:
               12:00 ERROR E42 disk full
               12:01 INFO start
               12:02 ERROR E17 no paper
               12:03 ERROR E42 disk full
               12:04 ERROR E42 disk full
            2. Выполните команду из примера.
            3. Результат: E42 встречается 3 раза, E17 — 1 раз.
        """,
        example = """
            grep 'ERROR' errors.log | cut -d' ' -f3 | sort | uniq -c | sort -rn | head -3
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Программы на C",
        title = "Сортировка любого массива: qsort",
        originalTitle = "qsort from the C standard library",
        author = "Стандартная библиотека C",
        year = 1979,
        summary = "Одна функция сортирует массивы любого типа: правило сравнения передаётся как указатель на функцию.",
        details = "qsort принимает массив, число элементов, размер элемента и функцию сравнения. Тот же код сортирует числа, строки " +
            "и записи — это важный приём переносимых библиотек 80-х. Функция сравнения возвращает отрицательное, нулевое " +
            "или положительное значение.",
        instructions = """
            1. Сохраните код как sortdemo.c.
            2. Соберите: cc -o sortdemo sortdemo.c
            3. Запустите ./sortdemo — числа выведутся по возрастанию: 1, 3, 7, 19, 42, 88.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>

            static int compare(const void *a, const void *b)
            {
                int x = *(const int *)a;
                int y = *(const int *)b;
                return (x > y) - (x < y);
            }

            int main(void)
            {
                int values[] = { 42, 7, 19, 3, 88, 1 };
                size_t n = sizeof values / sizeof values[0];
                size_t i;

                qsort(values, n, sizeof values[0], compare);
                for (i = 0; i < n; i++)
                    printf("%d\n", values[i]);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Базы данных",
        title = "Сводный отчёт: GROUP BY",
        originalTitle = "SQL GROUP BY report",
        author = "IBM, язык SEQUEL",
        year = 1974,
        summary = "GROUP BY превращает записи в сводку: сколько их и каких видов.",
        details = "Агрегатные функции COUNT, MIN, MAX и SUM работают над группами строк с одинаковым значением поля. " +
            "Раньше такие отчёты писали на COBOL отдельными программами; SQL делает то же одним запросом.",
        instructions = """
            1. Сохраните пример как отчёт.sql.
            2. Выполните его в любом SQL-движке, например в sqlite3: sqlite3 < отчёт.sql
            3. Результат: по каждому виду — число записей и самый ранний год.
        """,
        example = """
            CREATE TABLE records (kind TEXT, title TEXT, year INTEGER);
            INSERT INTO records VALUES ('LANGUAGE', 'Pascal', 1970);
            INSERT INTO records VALUES ('LANGUAGE', 'C', 1972);
            INSERT INTO records VALUES ('TOOL', 'make', 1976);

            SELECT kind, COUNT(*) AS total, MIN(year) AS first_year
            FROM records
            GROUP BY kind
            ORDER BY total DESC;
        """,
    ),
)
