package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1640 to 1650: Fermat's test and Torricelli's efflux, each in Python and C. */
val db40Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.CROSS,
        section = "Number theory",
        title = "Fermat's little theorem in Python and C",
        originalTitle = "Lettre a Frenicle de Bessy",
        author = "Pierre de Fermat",
        year = 1640,
        summary = "The same modular-power test is written once in Python and once in C, and a script checks that both programs print the same verdicts.",
        details = "Fermat's test is the same in both languages: the base raised to n minus 1 is reduced modulo n and compared with 1. The run script compares the printed lines for the numbers 97, 91, 341 and 561.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### fermat_test.py
            def passes(n, base):
                # Fermat's test: the base raised to n - 1 must leave remainder 1 when n is prime.
                return pow(base, n - 1, n) == 1

            for n in (97, 91, 341, 561):
                verdict = "passes" if passes(n, 2) else "fails"
                print(f"n = {n}: base 2 test {verdict}")
            ### fermat_test.c
            #include <stdio.h>

            /* Fermat's test: the base raised to n - 1 must leave remainder 1 when n is prime. */
            static unsigned long long power_mod(unsigned long long base, unsigned long long e, unsigned long long m) {
                unsigned long long result = 1 % m;
                base %= m;
                while (e > 0) {
                    if (e & 1) {
                        result = (result * base) % m;
                    }
                    base = (base * base) % m;
                    e >>= 1;
                }
                return result;
            }

            int main(void) {
                unsigned long long numbers[4] = {97, 91, 341, 561};
                for (int i = 0; i < 4; i++) {
                    unsigned long long n = numbers[i];
                    const char *verdict = power_mod(2, n - 1, n) == 1 ? "passes" : "fails";
                    printf("n = %llu: base 2 test %s\n", n, verdict);
                }
                return 0;
            }
            ### run.sh
            python3 fermat_test.py > python.txt
            cc -o fermat_test_c fermat_test.c
            ./fermat_test_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.CROSS,
        section = "Hydraulics",
        title = "Torricelli's efflux speed in Python and C",
        originalTitle = "Opera geometrica",
        author = "Evangelista Torricelli",
        year = 1643,
        summary = "The same speed of efflux is computed once in Python and once in C, and a script checks that both print the same figures.",
        details = "Torricelli's rule gives the speed as the square root of 2gh in both programs, with g set to 9.81 metres per second squared. The run script compares the printed lines for three heights.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### efflux.py
            from math import sqrt

            G = 9.81  # metres per second squared

            def efflux_speed(height):
                # The jet leaves at the speed gained by falling through the height of water above the hole.
                return sqrt(2 * G * height)

            for h in (1.0, 4.0, 0.25):
                print(f"height {h:.2f} m: speed {efflux_speed(h):.2f} m/s")
            ### efflux.c
            #include <stdio.h>
            #include <math.h>

            /* The jet leaves at the speed gained by falling through the height of water above the hole. */
            static double efflux_speed(double height) {
                const double g = 9.81; /* metres per second squared */
                return sqrt(2.0 * g * height);
            }

            int main(void) {
                double heights[3] = {1.0, 4.0, 0.25};
                for (int i = 0; i < 3; i++) {
                    printf("height %.2f m: speed %.2f m/s\n", heights[i], efflux_speed(heights[i]));
                }
                return 0;
            }
            ### run.sh
            python3 efflux.py > python.txt
            cc -o efflux_c efflux.c -lm
            ./efflux_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
