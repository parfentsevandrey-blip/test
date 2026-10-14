package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Построенные продукты: готовые программы с объяснением устройства и инструкцией запуска. */
val db1Projects: List<RecordEntity> = listOf(

    entry(
        kind = Kind.PROJECT,
        section = "Игры и симуляции",
        title = "Игра «Жизнь» Конвея на awk",
        originalTitle = "Conway's Game of Life in awk",
        author = "Джон Конвей (правила), реализация на awk",
        year = 1970,
        summary = "Клеточный автомат в терминале: поколения рождаются, гаснут и движутся по правилам Конвея.",
        details = "Клетка выживает, если рядом живут 2 или 3 соседа, и рождается, если соседей ровно 3. Поле замкнуто в тор: " +
            "край переходит в противоположный. Живые клетки хранятся в ассоциативном массиве cell с ключом «строка, столбец». " +
            "На каждом шаге программа строит новое поле nxt и копирует его обратно. Глайдер — пять клеток — сдвигается по диагонали.",
        instructions = """
            1. Сохраните код как life.awk.
            2. Запустите: awk -f life.awk
            3. Чтобы увидеть больше поколений, увеличьте GENS в блоке BEGIN. Новые фигуры добавляйте в тот же блок: cell[строка, столбец] = 1.
        """,
        example = """
            # Игра «Жизнь» Конвея. Запуск: awk -f life.awk
            BEGIN {
                W = 20; H = 10; GENS = 5
                # стартовая фигура: глайдер
                cell[1, 2] = 1; cell[2, 3] = 1
                cell[3, 1] = 1; cell[3, 2] = 1; cell[3, 3] = 1
                for (g = 0; g < GENS; g++) {
                    show()
                    step()
                }
            }

            function show(   y, x, line, ch) {
                for (y = 0; y < H; y++) {
                    line = ""
                    for (x = 0; x < W; x++) {
                        ch = "."
                        if ((y, x) in cell) ch = "#"
                        line = line ch
                    }
                    print line
                }
                print "----"
            }

            function step(   y, x, dy, dx, n, ny, nx, alive, nxt, k) {
                delete nxt
                for (y = 0; y < H; y++)
                    for (x = 0; x < W; x++) {
                        n = 0
                        for (dy = -1; dy <= 1; dy++)
                            for (dx = -1; dx <= 1; dx++) {
                                if (dy == 0 && dx == 0) continue
                                ny = (y + dy + H) % H
                                nx = (x + dx + W) % W
                                if ((ny, nx) in cell) n++
                            }
                        alive = ((y, x) in cell)
                        if ((alive && (n == 2 || n == 3)) || (!alive && n == 3))
                            nxt[y, x] = 1
                    }
                delete cell
                for (k in nxt) cell[k] = 1
            }
        """,
    ),

    entry(
        kind = Kind.PROJECT,
        section = "Игры и симуляции",
        title = "Случайный лабиринт в консоли",
        originalTitle = "Maze generator with depth-first backtracking",
        author = "Практика 80-х, алгоритм поиска с откатом",
        year = 1980,
        summary = "Программа вырезает проходы в сетке стен: получается дерево без циклов, где между любыми двумя точками ровно один путь.",
        details = "Алгоритм поиска в глубину с откатом: из текущей клетки выбираем случайного соседа, которого ещё не посещали, " +
            "пробиваем стену между ними и продолжаем. Если соседей нет, откатываемся назад. Клетки лежат в нечётных позициях " +
            "сетки, стены — в чётных, поэтому структура всегда корректна.",
        instructions = """
            1. Сохраните код как maze.c и соберите: cc -o maze maze.c
            2. Запустите несколько раз: каждый запуск даёт новый лабиринт, потому что генератор зависит от времени.
            3. Чтобы сделать лабиринт больше, увеличьте N (число клеток по стороне). N должно быть нечётным.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>

            #define N 11                     /* число клеток по стороне */
            #define W (2 * N + 1)            /* полная сетка: клетки и стены между ними */

            static char grid[W][W];
            static int visited[N][N];

            static void carve(int cy, int cx)
            {
                static const int dy[4] = { -1, 1, 0, 0 };
                static const int dx[4] = { 0, 0, -1, 1 };
                int order[4] = { 0, 1, 2, 3 };
                int i, j, tmp;

                visited[cy][cx] = 1;
                grid[2 * cy + 1][2 * cx + 1] = ' ';

                for (i = 3; i > 0; i--) {    /* перемешиваем направления */
                    j = rand() % (i + 1);
                    tmp = order[i];
                    order[i] = order[j];
                    order[j] = tmp;
                }

                for (i = 0; i < 4; i++) {
                    int d = order[i];
                    int ny = cy + dy[d];
                    int nx = cx + dx[d];

                    if (ny < 0 || ny >= N || nx < 0 || nx >= N || visited[ny][nx])
                        continue;
                    grid[2 * cy + 1 + dy[d]][2 * cx + 1 + dx[d]] = ' ';   /* пробиваем стену */
                    carve(ny, nx);
                }
            }

            int main(void)
            {
                int y, x;

                srand((unsigned)time(NULL));
                for (y = 0; y < W; y++)
                    for (x = 0; x < W; x++)
                        grid[y][x] = '#';

                carve(0, 0);

                for (y = 0; y < W; y++) {
                    for (x = 0; x < W; x++)
                        putchar(grid[y][x]);
                    putchar('\n');
                }
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        kind = Kind.PROJECT,
        section = "Утилиты",
        title = "Записная книжка на sh",
        originalTitle = "Address book in plain sh and awk",
        author = "Практика shell-скриптов, 1980-е",
        year = 1979,
        summary = "Телефонная книжка в одном скрипте: добавить запись, найти и показать всё. База данных не нужна.",
        details = "Записи лежат в текстовом файле book.txt, одна запись в строке, поля разделены табуляцией. Команды add и find — это " +
            "ветки оболочки, которые вызывают printf, grep и awk. Проект показывает, что многое из того, что делает база данных, " +
            "можно сделать текстовым файлом с понятным форматом и парой утилит Unix.",
        instructions = """
            1. Сохраните скрипт как book.sh и сделайте его исполняемым: chmod +x book.sh
            2. Добавьте записи: ./book.sh add Иван 555-1234 и ./book.sh add Мария 555-9876
            3. Найдите запись: ./book.sh find Иван
            4. Выведите все записи: ./book.sh list
        """,
        example = """
            #!/bin/sh
            # Записная книжка: записи хранятся в book.txt, поля разделены табуляцией
            case "$1" in
              add)  printf '%s\t%s\n' "$2" "$3" >> book.txt ;;
              find) grep -i "$2" book.txt ;;
              list) awk -F '\t' '{ printf "%-15s %s\n", $1, $2 }' book.txt ;;
              *)    echo "usage: book.sh add|find|list" ;;
            esac
        """,
    ),

    entry(
        kind = Kind.PROJECT,
        section = "Инструменты разработки",
        title = "Калькулятор на yacc",
        originalTitle = "Calculator with yacc grammar and a hand-written lexer",
        author = "Стивен Джонсон (yacc, 1975), практика Unix",
        year = 1975,
        summary = "Грамматика вместо ручного разбора: yacc строит парсер из правил, приоритеты задаются строками объявлений.",
        details = "Файл состоит из трёх частей: объявления (токены и приоритеты операций), правила грамматики с действиями на языке C " +
            "и код лексера. Приоритет * выше + не нужно кодировать руками: он задан порядком строк %left. " +
            "Действие $$ = $1 + $3 вычисляет значение выражения. Так в 80-х писали калькуляторы, конфигураторы и компиляторы.",
        instructions = """
            1. Сохраните грамматику как calc.y. Лексер находится в третьей части файла, менять его не нужно.
            2. Сгенерируйте парсер: bison -y -d calc.y (появятся y.tab.c и y.tab.h).
            3. Соберите: cc -o calc y.tab.c
            4. Запустите: echo "2 + 3 * (4 - 1)" | ./calc — ответ 11.
        """,
        example = """
            %{
            #include <stdio.h>
            #include <ctype.h>
            int yylex(void);
            void yyerror(const char *s);
            %}

            %union {
                double val;
            }

            %token <val> NUMBER
            %type <val> expr
            %left '+' '-'
            %left '*' '/'
            %right UMINUS

            %%
            line : /* пусто */
                 | line expr '\n'         { printf("%g\n", $2); }
                 ;
            expr : expr '+' expr          { $$ = $1 + $3; }
                 | expr '-' expr          { $$ = $1 - $3; }
                 | expr '*' expr          { $$ = $1 * $3; }
                 | expr '/' expr          { $$ = $1 / $3; }
                 | '-' expr %prec UMINUS  { $$ = -$2; }
                 | '(' expr ')'           { $$ = $2; }
                 | NUMBER                 { $$ = $1; }
                 ;
            %%

            int yylex(void)
            {
                int c;

                while ((c = getchar()) == ' ' || c == '\t')
                    ;
                if (isdigit(c) || c == '.') {
                    ungetc(c, stdin);
                    scanf("%lf", &yylval.val);
                    return NUMBER;
                }
                return c;
            }

            void yyerror(const char *s)
            {
                fprintf(stderr, "%s\n", s);
            }

            int main(void)
            {
                return yyparse();
            }
        """,
        rare = true,
    ),
)
