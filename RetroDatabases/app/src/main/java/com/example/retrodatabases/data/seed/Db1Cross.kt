package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Кросс-код эксперименты: технологии и код смешиваются ради опыта.
 * Общий интерфейс у всех этих смесей один — текстовый поток, файл или правила сборки.
 */
val db1Cross: List<RecordEntity> = listOf(

    entry(
        kind = Kind.CROSS,
        section = "Фильтры и конвейеры",
        title = "Фильтр на C в конвейере с awk и sed",
        originalTitle = "C filter in an awk and sed pipeline",
        author = "Практика Unix, 1978",
        year = 1978,
        summary = "awk готовит данные, C считает быстро, sed оформляет вывод: три языка в одном конвейере без интерфейсов между ними.",
        details = "Каждая программа читает стандартный ввод и пишет в стандартный вывод, поэтому граница между языками — это труба. " +
            "Тяжёлые вычисления пишут на C, подготовку данных — на awk, оформление — на sed. Эксперимент показывает: технологии " +
            "можно смешивать, если у них общий интерфейс — текст.",
        instructions = """
            1. Сохраните C-фильтр как square.c и соберите: cc -o square square.c
            2. Выполните конвейер из примера.
            3. Результат: квадраты 1, 4, 9, 16 и 25, каждый с префиксом «квадрат: ».
        """,
        example = """
            ### square.c
            #include <stdio.h>

            int main(void)
            {
                long n;

                while (scanf("%ld", &n) == 1)
                    printf("%ld\n", n * n);
                return 0;
            }

            ### конвейер (в оболочке)
            awk 'BEGIN { for (i = 1; i <= 5; i++) print i }' | ./square | sed 's/^/квадрат: /'
        """,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Генерация кода",
        title = "Таблица синусов при сборке: make, awk и C",
        originalTitle = "Build-time code generation with make, awk and C",
        author = "Практика make, 1976",
        year = 1976,
        summary = "make запускает awk, который пишет таблицу на C, и только потом собирает программу, которая её использует.",
        details = "Таблица вычисляется при сборке, а не во время работы программы — приём 80-х, когда каждый такт был дорог. " +
            "Makefile описывает зависимости: sine.h зависит от gen.awk, программа — от sine.h. Изменили генератор — пересоберётся " +
            "всё, что от него зависит. Так разные языки связывают правила сборки.",
        instructions = """
            1. Создайте файлы gen.awk, Makefile и main.c по блокам из примера. Строки команд в Makefile начинаются с табуляции.
            2. Выполните make: появятся sine.h и программа prog.
            3. Запустите ./prog — выведутся значения синуса для восьми углов от 0 до 7π/4.
        """,
        example = """
            ### gen.awk
            BEGIN {
                print "static const double sine_table[8] = {"
                for (i = 0; i < 8; i++)
                    printf "    %.6f,\n", sin(i * 3.14159265358979 / 4)
                print "};"
            }

            ### Makefile
            prog: main.c sine.h
            <TAB>cc -o prog main.c

            sine.h: gen.awk
            <TAB>awk -f gen.awk > sine.h

            ### main.c
            #include <stdio.h>
            #include "sine.h"

            int main(void)
            {
                int i;

                for (i = 0; i < 8; i++)
                    printf("%d: %.6f\n", i, sine_table[i]);
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Журналы и отчёты",
        title = "Конвейер анализа журнала: perl, sed, sort",
        originalTitle = "Log analysis with perl, sed, sort and uniq",
        author = "Практика Perl и Unix",
        year = 1987,
        summary = "Perl выбирает поля из журнала, sort и uniq считают, sed оформляет вывод: каждый инструмент делает то, что умеет лучше всего.",
        details = "Perl в режиме -n читает журнал построчно и печатает первое поле — адрес. Дальше sort и uniq -c считают повторы, " +
            "sort -rn выстраивает рейтинг, а sed убирает лишние пробелы. Эксперимент показывает, что не нужно выбирать один язык на всё.",
        instructions = """
            1. Сохраните пример журнала как access.log. Первое поле каждой строки — адрес:
               10.0.0.5 GET /index.html
               10.0.0.7 GET /about.html
               10.0.0.5 GET /c.html
               10.0.0.9 GET /index.html
               10.0.0.5 GET /awk.html
               10.0.0.7 GET /perl.html
            2. Выполните команду из примера.
            3. Результат: 3 10.0.0.5, затем 2 10.0.0.7 и 1 10.0.0.9.
        """,
        example = """
            perl -ne 'print "$1\n" if /^(\S+)/' access.log | sort | uniq -c | sort -rn | sed 's/^ *//' | head -3
        """,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Языки внутри языков",
        title = "Мини-Forth на C: стек внутри другого языка",
        originalTitle = "A mini Forth interpreter in C",
        author = "Эксперимент в духе Чарльза Мура",
        year = 1970,
        summary = "Стековая модель Forth реализована на C: числа уходят в стек, слова-операторы забирают аргументы оттуда.",
        details = "Интерпретатор — массив-стек и цикл по словам: число кладётся в стек, слово + или * снимает два значения " +
            "и возвращает результат. Так язык внутри языка получается из нескольких десятков строк. Эксперимент показывает, " +
            "как устроен любой стековый язык, включая PostScript.",
        instructions = """
            1. Сохраните код как mini4th.c и соберите: cc -o mini4th mini4th.c
            2. Выполните: echo '2 3 + 4 * .' | ./mini4th — ответ 20.
            3. Попробуйте: echo '10 2 - .' | ./mini4th — ответ 8.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>

            #define STACK_MAX 64

            static long stack[STACK_MAX];
            static int sp = 0;                  /* число элементов в стеке */

            static void push(long v)
            {
                if (sp >= STACK_MAX) {
                    fprintf(stderr, "stack overflow\n");
                    exit(1);
                }
                stack[sp++] = v;
            }

            static long pop(void)
            {
                if (sp <= 0) {
                    fprintf(stderr, "stack underflow\n");
                    exit(1);
                }
                return stack[--sp];
            }

            int main(void)
            {
                char word[64];

                while (scanf("%63s", word) == 1) {
                    if (strcmp(word, "+") == 0) {
                        long b = pop();
                        push(pop() + b);
                    } else if (strcmp(word, "-") == 0) {
                        long b = pop();
                        push(pop() - b);
                    } else if (strcmp(word, "*") == 0) {
                        long b = pop();
                        push(pop() * b);
                    } else if (strcmp(word, ".") == 0) {
                        printf("%ld\n", pop());
                    } else {
                        push(strtol(word, NULL, 10));   /* любое другое слово — число */
                    }
                }
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Вычисления",
        title = "π с шестьюдесятью знаками: bc и awk",
        originalTitle = "Pi to 60 digits with bc and awk",
        author = "bc: Лоринда Черри и Роберт Моррис, Bell Labs, 1975",
        year = 1975,
        summary = "Калькулятор произвольной точности bc считает π, а awk выводит результат с подписью.",
        details = "Функция a(1) в bc даёт arctan(1), умножение на 4 — π. Точность задаёт переменная scale. bc работает с числами " +
            "любой длины, поэтому π можно посчитать с любым числом знаков. awk принимает результат из конвейера и печатает его с подписью.",
        instructions = """
            1. Выполните команду из примера.
            2. Результат начинается так: pi = 3.141592653589793238462643383279502884197169399375105820974944
        """,
        example = """
            echo "scale=60; 4*a(1)" | bc -l | awk '{ print "pi =", $1 }'
        """,
        rare = true,
    ),
)
