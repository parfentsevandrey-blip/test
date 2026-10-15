package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1840 to 1850: the same result from two languages, checked by a run script. */
val db20Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.CROSS,
        section = "Euclid's algorithm",
        title = "Lame's bound on Euclid's steps, in Python and C",
        originalTitle = "Note sur la limite du nombre des divisions dans la recherche du plus grand commun diviseur entre deux nombres entiers",
        author = "Gabriel Lame",
        year = 1844,
        summary = "The number of division steps in Euclid's algorithm for two whole numbers is at most five times the number of decimal digits of the smaller one.",
        details = "Lame proved the bound in a note of 1844, and consecutive Fibonacci pairs come close to it. Python and C both count the steps for the pairs of consecutive Fibonacci numbers up to 89 and 55, and the run script shows that their output matches.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### lame.py
            def steps(a, b):
                # number of division steps of Euclid's algorithm, starting from the pair (a, b)
                count = 0
                while b:
                    a, b = b, a % b
                    count += 1
                return count

            def digits(n):
                return len(str(n))

            fib = [1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89]
            for k in range(2, 11):
                big, small = fib[k], fib[k - 1]
                s = steps(big, small)
                bound = 5 * digits(small)
                print(big, small, "steps", s, "bound", bound, "ok", s <= bound)
            ### lame.c
            #include <stdio.h>

            static int steps(long a, long b) {
                int count = 0;
                while (b != 0) {
                    long r = a % b;
                    a = b;
                    b = r;
                    count++;
                }
                return count;
            }

            static int digits(long n) {
                int d = 0;
                do {
                    d++;
                    n /= 10;
                } while (n > 0);
                return d;
            }

            int main(void) {
                long fib[] = {1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89};
                for (int k = 2; k <= 10; k++) {
                    long big = fib[k];
                    long small = fib[k - 1];
                    int s = steps(big, small);
                    int bound = 5 * digits(small);
                    printf("%ld %ld steps %d bound %d ok %s\n", big, small, s, bound, s <= bound ? "True" : "False");
                }
                return 0;
            }
            ### run.sh
            python3 lame.py > py.txt
            gcc -O2 -o lame lame.c
            ./lame > c.txt
            cat py.txt
            diff py.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.CROSS,
        section = "Perfect powers",
        title = "Catalan's consecutive perfect powers, in Python and C",
        originalTitle = "Note extraite d'une lettre adressee a l'editeur",
        author = "Eugene Charles Catalan",
        year = 1844,
        summary = "Catalan asked whether 8 and 9 are the only consecutive whole numbers that are both perfect powers, a statement proved in 2002.",
        details = "Catalan stated the question in 1844, and Mihailescu proved it in 2002. Python and C each list the perfect powers up to 1000 and print the consecutive pairs, which in both programs are just 8 and 9.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### catalan.py
            limit = 1000
            # perfect powers up to the limit: 1 and every base raised to a power of at least 2
            powers = {1}
            for base in range(2, 32):
                value = base * base
                while value <= limit:
                    powers.add(value)
                    value *= base
            print("limit:", limit)
            for n in sorted(powers):
                if n + 1 in powers and n + 1 <= limit:
                    print("pair:", n, n + 1)
            ### catalan.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const int limit = 1000;
                static int is_power[1002];
                memset(is_power, 0, sizeof(is_power));
                is_power[1] = 1;
                for (int base = 2; base < 32; base++) {
                    long value = (long)base * base;
                    while (value <= limit) {
                        is_power[value] = 1;
                        value *= base;
                    }
                }
                printf("limit: %d\n", limit);
                for (int n = 1; n < limit; n++) {
                    if (is_power[n] && is_power[n + 1]) {
                        printf("pair: %d %d\n", n, n + 1);
                    }
                }
                return 0;
            }
            ### run.sh
            python3 catalan.py > py.txt
            gcc -O2 -o catalan catalan.c
            ./catalan > c.txt
            cat py.txt
            diff py.txt c.txt && echo "same result"
        """,
    ),
)
