package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1890 to 1900: the same stability test and the same Bessel series in Python and C, checked by a run script. */
val db15Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.CROSS,
        section = "Stability",
        title = "Hurwitz determinants in Python and C",
        originalTitle = "Ueber die Bedingungen, unter welchen eine Gleichung nur Wurzeln mit negativen reellen Theilen besitzt (1895)",
        author = "Adolf Hurwitz",
        year = 1895,
        summary = "A cubic polynomial is stable when its coefficients are positive and one product inequality holds, and two programs in Python and C check the same cases with integer arithmetic.",
        details = "Hurwitz's 1895 criterion for the roots of a polynomial to lie in the left half-plane gives the test for cubics used here, and the same test is also known in Routh's form. The example applies the test to two cubics, one stable and one not, and the two programs print the same lines.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### hurwitz.py
            def stable(a3, a2, a1, a0):
                # Hurwitz test for a3 s^3 + a2 s^2 + a1 s + a0 with a3 > 0.
                return a3 > 0 and a2 > 0 and a1 > 0 and a0 > 0 and a2 * a1 > a3 * a0

            print("s^3 + 6s^2 + 11s + 6 stable: " + str(stable(1, 6, 11, 6)))
            print("s^3 + s^2 + s + 2 stable: " + str(stable(1, 1, 1, 2)))
            ### hurwitz.c
            #include <stdio.h>

            /* Hurwitz test for a3 s^3 + a2 s^2 + a1 s + a0 with a3 > 0. */
            static int stable(int a3, int a2, int a1, int a0) {
                return a3 > 0 && a2 > 0 && a1 > 0 && a0 > 0 && a2 * a1 > a3 * a0;
            }

            int main(void) {
                printf("s^3 + 6s^2 + 11s + 6 stable: %s\n", stable(1, 6, 11, 6) ? "True" : "False");
                printf("s^3 + s^2 + s + 2 stable: %s\n", stable(1, 1, 1, 2) ? "True" : "False");
                return 0;
            }
            ### run.sh
            python3 hurwitz.py > python.txt
            cc -o hurwitz_c hurwitz.c
            ./hurwitz_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.CROSS,
        section = "Special functions",
        title = "Bessel J0 at one by its power series in Python and C",
        originalTitle = "A Treatise on Bessel Functions and their Applications to Physics (1895)",
        author = "Andrew Gray and G. B. Mathews",
        year = 1895,
        summary = "The power series of the Bessel function J0 at x = 1 converges quickly, and two programs in Python and C print the same value to six places.",
        details = "Gray and Mathews's treatise of 1895 collected the Bessel functions and their uses in physics. The Python and C programs sum the same 30 terms of the power series in double precision and print the value 0.765198 for J0 at one.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### bessel.py
            def bessel_j0(x, terms=30):
                # Power series of J0 built term by term: the k-th term is (-1)^k (x/2)^(2k) / (k!)^2.
                total = 1.0
                term = 1.0
                for k in range(1, terms):
                    term *= -(x * x / 4) / (k * k)
                    total += term
                return total

            print(f"J0(1) = {bessel_j0(1.0):.6f}")
            ### bessel.c
            #include <stdio.h>

            /* Power series of J0, built term by term as in the Python program. */
            static double bessel_j0(double x, int terms) {
                double total = 1.0;
                double term = 1.0;
                for (int k = 1; k < terms; k++) {
                    term *= -(x * x / 4.0) / ((double)k * k);
                    total += term;
                }
                return total;
            }

            int main(void) {
                printf("J0(1) = %.6f\n", bessel_j0(1.0, 30));
                return 0;
            }
            ### run.sh
            python3 bessel.py > python.txt
            cc -o bessel_c bessel.c
            ./bessel_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
