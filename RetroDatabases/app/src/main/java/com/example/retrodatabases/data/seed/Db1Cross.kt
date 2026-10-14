package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/**
 * Cross-code experiments: technologies and code are mixed for the sake of the experiment.
 * All these mixes share one interface: a text stream, a file or build rules.
 */
val db1Cross: List<RecordEntity> = listOf(

    entry(
        kind = Kind.CROSS,
        section = "Filters and pipelines",
        title = "A C filter in a pipeline with awk and sed",
        originalTitle = "C filter in an awk and sed pipeline",
        author = "Unix practice, 1978",
        year = 1978,
        summary = "awk prepares the data, C computes fast, sed formats the output: three languages in one pipeline with no interfaces between them.",
        details = "Each program reads standard input and writes standard output, so the boundary between the languages is a pipe. " +
            "Heavy computation is written in C, data preparation in awk, and formatting in sed. " +
            "The experiment shows that technologies can be mixed if they share an interface: text.",
        instructions = """
            1. Save the C filter as square.c and build it: cc -o square square.c
            2. Run the pipeline from the example.
            3. The result: the squares 1, 4, 9, 16 and 25, each with the prefix “square: ”.
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

            ### pipeline (in the shell)
            awk 'BEGIN { for (i = 1; i <= 5; i++) print i }' | ./square | sed 's/^/square: /'
        """,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Code generation",
        title = "Sine table at build time: make, awk and C",
        originalTitle = "Build-time code generation with make, awk and C",
        author = "make practice, 1976",
        year = 1976,
        summary = "make runs awk, which writes a table in C, and only then builds the program that uses it.",
        details = "The table is computed at build time, not while the program runs: a trick of the 80s, when every clock cycle was expensive. " +
            "The Makefile describes the dependencies: sine.h depends on gen.awk, and the program depends on sine.h. " +
            "Change the generator and everything that depends on it is rebuilt. This is how build rules tie different languages together.",
        instructions = """
            1. Create the files gen.awk, Makefile and main.c from the blocks in the example. Command lines in the Makefile start with a tab.
            2. Run make: sine.h and the program prog appear.
            3. Run ./prog — it prints the sine values for eight angles from 0 to 7π/4.
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
        section = "Logs and reports",
        title = "Log analysis pipeline: perl, sed, sort",
        originalTitle = "Log analysis with perl, sed, sort and uniq",
        author = "Perl and Unix practice",
        year = 1987,
        summary = "Perl picks fields from the log, sort and uniq count, sed formats the output: each tool does what it does best.",
        details = "In -n mode, Perl reads the log line by line and prints the first field, the address. " +
            "Then sort and uniq -c count the repeats, sort -rn builds the ranking, and sed removes the extra spaces. " +
            "The experiment shows that you do not have to pick one language for everything.",
        instructions = """
            1. Save the sample log as access.log. The first field of each line is an address:
               10.0.0.5 GET /index.html
               10.0.0.7 GET /about.html
               10.0.0.5 GET /c.html
               10.0.0.9 GET /index.html
               10.0.0.5 GET /awk.html
               10.0.0.7 GET /perl.html
            2. Run the command from the example.
            3. The result: 3 10.0.0.5, then 2 10.0.0.7 and 1 10.0.0.9.
        """,
        example = """
            perl -ne 'print "$1\n" if /^(\S+)/' access.log | sort | uniq -c | sort -rn | sed 's/^ *//' | head -3
        """,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Languages inside languages",
        title = "Mini Forth in C: a stack inside another language",
        originalTitle = "A mini Forth interpreter in C",
        author = "An experiment in the spirit of Charles Moore",
        year = 1970,
        summary = "The stack model of Forth is implemented in C: numbers go onto the stack, operator words take their arguments from it.",
        details = "The interpreter is an array-stack and a loop over the words: a number is pushed onto the stack, the word + or * pops two values " +
            "and pushes the result. This way a language inside a language takes a few dozen lines. " +
            "The experiment shows how any stack-based language works, PostScript included.",
        instructions = """
            1. Save the code as mini4th.c and build it: cc -o mini4th mini4th.c
            2. Run: echo '2 3 + 4 * .' | ./mini4th — the answer is 20.
            3. Try: echo '10 2 - .' | ./mini4th — the answer is 8.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>

            #define STACK_MAX 64

            static long stack[STACK_MAX];
            static int sp = 0;                  /* number of elements on the stack */

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
                        push(strtol(word, NULL, 10));   /* any other word is a number */
                    }
                }
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        kind = Kind.CROSS,
        section = "Computation",
        title = "π to sixty digits: bc and awk",
        originalTitle = "Pi to 60 digits with bc and awk",
        author = "bc: Lorinda Cherry and Robert Morris, Bell Labs, 1975",
        year = 1975,
        summary = "The arbitrary-precision calculator bc computes π, and awk prints the result with a label.",
        details = "The function a(1) in bc gives arctan(1), and multiplying it by 4 gives π. The precision is set by the variable scale. " +
            "bc works with numbers of any length, so π can be computed to any number of digits. " +
            "awk takes the result from the pipeline and prints it with a label.",
        instructions = """
            1. Run the command from the example.
            2. The result starts like this: pi = 3.141592653589793238462643383279502884197169399375105820974944
        """,
        example = """
            echo "scale=60; 4*a(1)" | bc -l | awk '{ print "pi =", $1 }'
        """,
        rare = true,
    ),
)
