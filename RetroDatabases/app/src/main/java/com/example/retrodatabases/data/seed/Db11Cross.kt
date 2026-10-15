package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1930 to 1940: the same result from two languages, checked by a run script. */
val db11Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.CROSS,
        section = "Python and C",
        title = "Python and C: Collatz stopping times agree",
        originalTitle = "The 3n+1 problem, posed by Lothar Collatz in 1937, in two languages",
        author = "Written as a teaching example",
        year = 1937,
        summary = "The same stopping-time count is written in Python and in C, and a run script checks that the two outputs are identical.",
        details = "The two programs share no code. Both print the number of steps for the numbers 1 to 10 and for 27, and the run script compares their output line by line. Collatz posed the question in 1937.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### collatz.py
            def stopping_time(n):
                steps = 0
                while n != 1:
                    n = n // 2 if n % 2 == 0 else 3 * n + 1
                    steps += 1
                return steps

            for n in list(range(1, 11)) + [27]:
                print(n, stopping_time(n))
            ### collatz.c
            #include <stdio.h>

            static int stopping_time(long n) {
                int steps = 0;
                while (n != 1) {
                    n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
                    steps++;
                }
                return steps;
            }

            int main(void) {
                long values[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 27};
                for (int i = 0; i < 11; i++) {
                    printf("%ld %d\n", values[i], stopping_time(values[i]));
                }
                return 0;
            }
            ### run.sh
            python3 collatz.py > py.txt
            cc -o collatz collatz.c
            ./collatz > c.txt
            cat py.txt
            diff py.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.CROSS,
        section = "Python and JavaScript",
        title = "Python and JavaScript: the same Goedel numbers",
        originalTitle = "Goedel numbering of short formulas, in two languages",
        author = "Written as a teaching example",
        year = 1931,
        summary = "The same Goedel numbers are computed in Python and in JavaScript, and a run script checks that both print the same lines.",
        details = "The two programs share no code. They compute the numbers of the formulas 0=0 and S0=S0 from the same symbol codes and the same primes, and the run script compares the output. Goedel introduced the coding in 1931.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### goedel.py
            SYMBOLS = {"0": 1, "S": 2, "=": 3}
            PRIMES = [2, 3, 5, 7, 11]

            def encode(formula):
                number = 1
                for k, symbol in enumerate(formula):
                    number *= PRIMES[k] ** SYMBOLS[symbol]
                return number

            for formula in ["0=0", "S0=S0"]:
                print(formula, encode(formula))
            ### goedel.js
            const SYMBOLS = { "0": 1, S: 2, "=": 3 };
            const PRIMES = [2, 3, 5, 7, 11];

            function encode(formula) {
              let number = 1;
              [...formula].forEach((symbol, k) => {
                number *= Math.pow(PRIMES[k], SYMBOLS[symbol]);
              });
              return number;
            }

            for (const formula of ["0=0", "S0=S0"]) {
              console.log(formula, encode(formula));
            }
            ### run.sh
            python3 goedel.py > py.txt
            node goedel.js > js.txt
            cat py.txt
            diff py.txt js.txt && echo "same result"
        """,
    ),
)
