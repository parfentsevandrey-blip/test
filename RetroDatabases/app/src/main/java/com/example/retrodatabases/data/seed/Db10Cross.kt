package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1940 to 1950: the same result from two languages, checked by a run script. */
val db10Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.CROSS,
        section = "C and Python",
        title = "C and Python: Kaprekar's routine",
        originalTitle = "Kaprekar's routine, with the constant 6174 (Kaprekar, 1949)",
        author = "Written as a teaching example",
        year = 1949,
        summary = "The same digit-sorting routine is written in C and in Python, and both print the number of steps to reach 6174, so the two outputs can be compared.",
        details = "The year of Kaprekar's discovery is disputed: one account puts it in 1949, and the 1955 Scripta Mathematica paper is the usual citation. The year given is 1949. The two programs share no code, and the run script checks that their outputs are identical.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### kaprekar.c
            #include <stdio.h>
            #include <stdlib.h>

            /* Compare two digit characters in descending order. */
            static int desc(const void *a, const void *b) {
                return *(const char *)b - *(const char *)a;
            }

            /* Compare two digit characters in ascending order. */
            static int asc(const void *a, const void *b) {
                return *(const char *)a - *(const char *)b;
            }

            /* One Kaprekar step: the largest arrangement of the digits minus the smallest. */
            static int step(int n) {
                char digits[5], big[5], small[5];
                snprintf(digits, sizeof digits, "%04d", n);
                snprintf(big, sizeof big, "%s", digits);
                snprintf(small, sizeof small, "%s", digits);
                qsort(big, 4, 1, desc);
                qsort(small, 4, 1, asc);
                return atoi(big) - atoi(small);
            }

            int main(void) {
                int starts[3] = {3524, 1000, 6174};
                for (int i = 0; i < 3; i++) {
                    int n = starts[i], count = 0;
                    while (n != 6174 && count < 10) {
                        n = step(n);
                        count++;
                    }
                    printf("%d -> 6174 in %d steps\n", starts[i], count);
                }
                return 0;
            }
            ### kaprekar.py
            def step(n):
                digits = f"{n:04d}"
                big = int("".join(sorted(digits, reverse=True)))
                small = int("".join(sorted(digits)))
                return big - small

            for start in (3524, 1000, 6174):
                n, count = start, 0
                while n != 6174 and count < 10:
                    n = step(n)
                    count += 1
                print(f"{start} -> 6174 in {count} steps")
            ### run.sh
            cc -o kaprekar kaprekar.c
            ./kaprekar > c.txt
            python3 kaprekar.py > py.txt
            cat c.txt
            diff c.txt py.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 10,
        kind = Kind.CROSS,
        section = "C and JavaScript",
        title = "C and JavaScript: squares by adding odd numbers",
        originalTitle = "The first EDSAC program, reported to print squares (1949)",
        author = "Written as a teaching example",
        year = 1949,
        summary = "Each square is built by adding odd numbers, a method that needs no multiplication, and C and JavaScript print the same table so the two can be compared.",
        details = "EDSAC's first program, run on 6 May 1949, is reported to have printed squares, and some accounts also mention primes, so this record makes no claim about the rest of that program. The table here is a teaching version: the square of n plus the next odd number gives the square of n plus one. The run script checks that the two languages agree.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### squares.c
            #include <stdio.h>

            int main(void) {
                int square = 0;
                for (int n = 0; n < 10; n++) {
                    printf("%d %d\n", n, square);
                    square += 2 * n + 1;   /* (n + 1) squared minus n squared is 2n + 1 */
                }
                return 0;
            }
            ### squares.js
            let square = 0;
            for (let n = 0; n < 10; n++) {
              console.log(n + " " + square);
              square += 2 * n + 1;
            }
            ### run.sh
            cc -o squares squares.c
            ./squares > c.txt
            node squares.js > js.txt
            cat c.txt
            diff c.txt js.txt && echo "same result"
        """,
    ),
)
