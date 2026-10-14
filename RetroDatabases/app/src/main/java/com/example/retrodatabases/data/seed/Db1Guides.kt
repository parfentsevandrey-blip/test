package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides and instructions: how the techniques of the 1980s work and how to repeat them by hand. */
val db1Guides: List<RecordEntity> = listOf(

    entry(
        kind = Kind.GUIDE,
        section = "How it works",
        title = "How a Unix pipeline works",
        originalTitle = "How pipes work",
        author = "Unix practice, Bell Labs",
        year = 1973,
        summary = "A pipeline is two processes connected by a kernel buffer: the output of one becomes the input of the other.",
        details = "The shell creates a channel with pipe(), starts processes with fork() and redirects the standard streams with dup2(). " +
            "The reader blocks until the writer puts data into the channel; when the writer closes its end, the reader gets end-of-file. " +
            "This is how the command ls | wc -l works without temporary files, and each program does not know what it is connected to.",
        instructions = """
            1. Save the example as pipeline.c and build it: cc -o pipeline pipeline.c
            2. Run: ./pipeline
            3. The program does what the shell does for the command echo hello | tr a-z A-Z, and prints HELLO.
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

                if (fork() == 0) {                  /* first process: echo writes into the pipe */
                    dup2(fd[1], STDOUT_FILENO);
                    close(fd[0]);
                    close(fd[1]);
                    execlp("echo", "echo", "hello", (char *)NULL);
                    perror("execlp echo");
                    exit(1);
                }

                if (fork() == 0) {                  /* second process: tr reads from the pipe */
                    dup2(fd[0], STDIN_FILENO);
                    close(fd[0]);
                    close(fd[1]);
                    execlp("tr", "tr", "a-z", "A-Z", (char *)NULL);
                    perror("execlp tr");
                    exit(1);
                }

                close(fd[0]);                       /* the parent needs neither end of the pipe */
                close(fd[1]);
                wait(NULL);
                wait(NULL);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.GUIDE,
        section = "Step-by-step instructions",
        title = "Makefile from scratch: step by step",
        originalTitle = "Makefile step by step",
        author = "Stuart Feldman (make), 80s practice",
        year = 1976,
        summary = "Creating a Makefile step by step: targets, dependencies and commands that run only when files change.",
        details = "A rule consists of a target, a list of dependencies and commands. Commands must start with a tab character; " +
            "this is the most common beginner mistake. Make compares the modification time of the target and its dependencies: " +
            "if a dependency is newer, the target is rebuilt. So after editing one file, a rebuild takes seconds, not minutes.",
        instructions = """
            1. Create the files main.c, util.c and util.h with simple functions.
            2. Save the example as Makefile in the same folder. Command lines must start with a tab, not with spaces.
            3. Run make: the first target in the file becomes the default target.
            4. Change util.c and run make again: only util.o and prog are rebuilt.
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
        section = "How it works",
        title = "Recursive descent: how to parse expressions",
        originalTitle = "Recursive descent parsing",
        author = "Compiler practice, 1970s–80s",
        year = 1975,
        summary = "Each grammar rule becomes a function. This is how expressions such as 2 + 3 * (4 - 1) are parsed.",
        details = "The grammar: an expression is terms joined by + and -; a term is factors joined by * and /; " +
            "a factor is a number or an expression in parentheses. Each nonterminal corresponds to a function that reads its part of the string " +
            "and returns a value. Operator precedence comes out naturally: multiplication is parsed deeper than addition. " +
            "Most parsers of the 80s were built on this technique.",
        instructions = """
            1. Save the code as calc.c and build it: cc -o calc calc.c
            2. Run: ./calc "2 + 3 * (4 - 1)". The answer is 11.
            3. Check: ./calc "10 / 4" (the answer is 2.5) and ./calc "(1 + 2) * (3 + 4)" (the answer is 21).
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <ctype.h>

            static const char *p;                   /* current position in the input string */

            static double expr(void);

            static void skip(void)
            {
                while (isspace((unsigned char)*p))
                    p++;
            }

            static double factor(void)              /* factor: a number or (expression) */
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

            static double term(void)                /* term: factors joined by * and / */
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

            static double expr(void)                /* expression: terms joined by + and - */
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
        section = "How it works",
        title = "Forth stack step by step",
        originalTitle = "Forth stack trace",
        author = "Charles Moore",
        year = 1970,
        summary = "Forth executes words from left to right, and the data lives on a stack. A step-by-step trace shows every state.",
        details = "A number pushes its value onto the top of the stack. The word + pops the two top values and pushes their sum. " +
            "The dot “.” pops the top and prints it. A program is a sequence of words, so debugging comes down to one question: " +
            "what is on the stack after this word? Moore used this approach in control systems long before personal computers.",
        instructions = """
            1. Install GNU Forth (the gforth package) or any other Forth interpreter.
            2. Run gforth and type: 2 3 + 4 * .
            3. The output shows 20. Check the table below: after each word the state of the stack changes.
        """,
        example = """
            Word    Stack (bottom left, top right)     Comment
            2       2                                  pushed the number
            3       2 3                                one more number
            +       5                                  popped 2 and 3, pushed 5
            4       5 4                                pushed 4
            *       20                                 popped 5 and 4, pushed 20
            .       (empty)                            popped 20 and printed it
        """,
        rare = true,
    ),

    entry(
        kind = Kind.GUIDE,
        section = "Step-by-step instructions",
        title = "How to run old programs in an emulator",
        originalTitle = "Running 80s software in emulators",
        author = "Retrocomputing community",
        year = 1982,
        summary = "Old programs run in an emulator if you have the program file, an emulator and the right machine model.",
        details = "An emulator imitates the processor, memory and peripherals. For the ZX Spectrum, Fuse is a good choice; for MS-DOS, DOSBox. " +
            "Programs are distributed as images: .tap and .z80 for the Spectrum, .img and .exe for DOS. " +
            "Many 80s programs were started with the LOAD command and an empty file name, just as on a real machine.",
        instructions = """
            1. Install an emulator: Fuse for the ZX Spectrum or DOSBox for MS-DOS.
            2. Open the program file through the emulator's menu (File → Open).
            3. In the Spectrum emulator, type LOAD "" (the keyword LOAD and two quotes), press Enter, then RUN.
            4. If the program does not start, check the machine model it requires: 48K or 128K.
        """,
    ),
)
