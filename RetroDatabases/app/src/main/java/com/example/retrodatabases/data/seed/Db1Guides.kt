package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Пояснения и инструкции: как устроены приёмы 1980-х и как их повторить руками. */
val db1Guides: List<RecordEntity> = listOf(

    entry(
        kind = Kind.GUIDE,
        section = "Как это работает",
        title = "Как устроен конвейер Unix",
        originalTitle = "How pipes work",
        author = "Практика Unix, Bell Labs",
        year = 1973,
        summary = "Конвейер — это два процесса, соединённые буфером ядра: вывод одного становится вводом другого.",
        details = "Оболочка создаёт канал вызовом pipe(), порождает процессы через fork() и подменяет стандартные потоки через dup2(). " +
            "Читатель блокируется, пока писатель не положит данные в канал; когда писатель закрывает свой конец, читатель получает конец файла. " +
            "Так команда ls | wc -l работает без временных файлов, а каждая программа не знает, с чем она соединена.",
        instructions = """
            1. Сохраните пример как pipeline.c и соберите: cc -o pipeline pipeline.c
            2. Запустите: ./pipeline
            3. Программа делает то же, что оболочка делает для команды echo hello | tr a-z A-Z, и выводит HELLO.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <unistd.h>
            #include <sys/wait.h>

            int main(void)
            {
                int fd[2];

                if (pipe(fd) == -1) {
                    perror("pipe");
                    return 1;
                }

                if (fork() == 0) {                  /* первый процесс: echo пишет в конвейер */
                    dup2(fd[1], STDOUT_FILENO);
                    close(fd[0]);
                    close(fd[1]);
                    execlp("echo", "echo", "hello", (char *)NULL);
                    perror("execlp echo");
                    exit(1);
                }

                if (fork() == 0) {                  /* второй процесс: tr читает из конвейера */
                    dup2(fd[0], STDIN_FILENO);
                    close(fd[0]);
                    close(fd[1]);
                    execlp("tr", "tr", "a-z", "A-Z", (char *)NULL);
                    perror("execlp tr");
                    exit(1);
                }

                close(fd[0]);                       /* родителю концы конвейера не нужны */
                close(fd[1]);
                wait(NULL);
                wait(NULL);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.GUIDE,
        section = "Пошаговые инструкции",
        title = "Makefile с нуля: инструкция",
        originalTitle = "Makefile step by step",
        author = "Стюарт Фелдман (make), практика 80-х",
        year = 1976,
        summary = "Пошаговое создание Makefile: цели, зависимости и команды, которые выполняются только при изменении файлов.",
        details = "Правило состоит из цели, списка зависимостей и команд. Команды обязательно начинаются с символа табуляции — это самая частая " +
            "ошибка новичков. Make сравнивает время изменения цели и зависимостей: если зависимость новее, цель пересобирается. " +
            "Поэтому после правки одного файла пересборка занимает секунды, а не минуты.",
        instructions = """
            1. Создайте файлы main.c, util.c и util.h с простыми функциями.
            2. Сохраните пример как Makefile в той же папке. Строки команд должны начинаться с табуляции, а не с пробелов.
            3. Выполните make: первая цель в файле станет целью по умолчанию.
            4. Измените util.c и выполните make ещё раз: пересоберутся только util.o и prog.
        """,
        example = """
            prog: main.o util.o
            <TAB>cc -o prog main.o util.o

            main.o: main.c util.h
            <TAB>cc -c main.c

            util.o: util.c util.h
            <TAB>cc -c util.c
        """,
    ),

    entry(
        kind = Kind.GUIDE,
        section = "Как это работает",
        title = "Рекурсивный спуск: как разбирать выражения",
        originalTitle = "Recursive descent parsing",
        author = "Практика компиляторов, 1970–80-е",
        year = 1975,
        summary = "Каждое правило грамматики становится функцией. Так разбирают выражения вроде 2 + 3 * (4 - 1).",
        details = "Грамматика: выражение — слагаемые, соединённые знаками + и -; слагаемое — множители, соединённые * и /; " +
            "множитель — число или выражение в скобках. Каждому нетерминалу соответствует функция, которая читает свою часть строки " +
            "и возвращает значение. Приоритет операций получается сам собой: умножение разбирается глубже сложения. " +
            "Этот приём лежит в основе большинства парсеров 80-х.",
        instructions = """
            1. Сохраните код как calc.c и соберите: cc -o calc calc.c
            2. Запустите: ./calc "2 + 3 * (4 - 1)". Ответ: 11.
            3. Проверьте: ./calc "10 / 4" (ответ 2.5) и ./calc "(1 + 2) * (3 + 4)" (ответ 21).
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <ctype.h>

            static const char *p;                   /* текущая позиция во входной строке */

            static double expr(void);

            static void skip(void)
            {
                while (isspace((unsigned char)*p))
                    p++;
            }

            static double factor(void)              /* множитель: число или (выражение) */
            {
                double v;
                char *end;

                skip();
                if (*p == '(') {
                    p++;
                    v = expr();
                    skip();
                    if (*p == ')')
                        p++;
                    return v;
                }
                v = strtod(p, &end);
                p = end;
                return v;
            }

            static double term(void)                /* слагаемое: множители через * и / */
            {
                double v = factor();

                for (;;) {
                    skip();
                    if (*p == '*') {
                        p++;
                        v *= factor();
                    } else if (*p == '/') {
                        p++;
                        v /= factor();
                    } else {
                        return v;
                    }
                }
            }

            static double expr(void)                /* выражение: слагаемые через + и - */
            {
                double v = term();

                for (;;) {
                    skip();
                    if (*p == '+') {
                        p++;
                        v += term();
                    } else if (*p == '-') {
                        p++;
                        v -= term();
                    } else {
                        return v;
                    }
                }
            }

            int main(int argc, char *argv[])
            {
                if (argc != 2) {
                    fprintf(stderr, "usage: %s \"expression\"\n", argv[0]);
                    return 1;
                }
                p = argv[1];
                printf("%g\n", expr());
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.GUIDE,
        section = "Как это работает",
        title = "Стек Forth по шагам",
        originalTitle = "Forth stack trace",
        author = "Чарльз Мур",
        year = 1970,
        summary = "Forth выполняет слова слева направо, а данные лежат в стеке. Пошаговая трассировка показывает каждое состояние.",
        details = "Число кладёт значение на вершину стека. Слово + снимает два верхних значения и кладёт сумму. " +
            "Точка «.» снимает вершину и печатает её. Программа — последовательность слов, поэтому отладка сводится к вопросу: " +
            "что лежит в стеке после этого слова? Мур применял этот подход в системах управления задолго до персональных компьютеров.",
        instructions = """
            1. Установите GNU Forth (пакет gforth) или любой другой Forth-интерпретатор.
            2. Запустите gforth и введите: 2 3 + 4 * .
            3. В выводе появится 20. Сверьте таблицу ниже: после каждого слова состояние стека меняется.
        """,
        example = """
            Слово   Стек (дно слева, вершина справа)   Комментарий
            2       2                                  положили число
            3       2 3                                ещё одно число
            +       5                                  сняли 2 и 3, положили 5
            4       5 4                                положили 4
            *       20                                 сняли 5 и 4, положили 20
            .       (пусто)                            сняли 20 и напечатали
        """,
        rare = true,
    ),

    entry(
        kind = Kind.GUIDE,
        section = "Пошаговые инструкции",
        title = "Как запустить старые программы в эмуляторе",
        originalTitle = "Running 80s software in emulators",
        author = "Сообщество ретрокомпьютеров",
        year = 1982,
        summary = "Старые программы работают в эмуляторе: нужны файл программы, эмулятор и правильная модель машины.",
        details = "Эмулятор имитирует процессор, память и периферию. Для ZX Spectrum подходит Fuse, для MS-DOS — DOSBox. " +
            "Программы распространяются образами: .tap и .z80 для Spectrum, .img и .exe для DOS. Многие программы 80-х " +
            "запускались командой LOAD с пустым именем файла — так же, как на настоящей машине.",
        instructions = """
            1. Установите эмулятор: Fuse для ZX Spectrum или DOSBox для MS-DOS.
            2. Откройте файл программы через меню эмулятора (File → Open).
            3. В эмуляторе Spectrum введите LOAD "" (ключевое слово LOAD и две кавычки), нажмите Enter, затем RUN.
            4. Если программа не запускается, проверьте модель машины, которую она требует: 48K или 128K.
        """,
    ),
)
