package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 890 to 900: Sridhara's rule for a quadratic equation, in a Python program and a C program that must print the same roots. */
val db115Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.CROSS,
        section = "Algebra",
        title = "Sridhara's rule for a quadratic equation, in Python and C",
        originalTitle = "Patiganita",
        author = "Sridhara",
        year = 900,
        summary = "Python and C programs apply Sridhara's rule for a quadratic equation, and a script checks that both print the same roots.",
        details = "Sridhara's rule multiplies both sides by four times the coefficient of the square, adds the square of the linear coefficient to both sides and takes the root. The two programs solve two equations with whole-number coefficients, so the run tests the two languages on the same arithmetic.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sridhara.py
            def root(a, b, c):
                return ((4 * a * c + b * b) ** 0.5 - b) / (2 * a)

            for a, b, c in [(2, 3, 2), (1, 2, 3)]:
                x = root(a, b, c)
                print(f"a={a} b={b} c={c} root={x:.2f} check={a * x * x + b * x:.2f}")
            ### sridhara.c
            #include <stdio.h>
            #include <math.h>

            static double root(int a, int b, int c) {
                return (sqrt(4.0 * a * c + (double)b * b) - b) / (2.0 * a);
            }

            int main(void) {
                int cases[2][3] = {{2, 3, 2}, {1, 2, 3}};
                for (int i = 0; i < 2; i++) {
                    int a = cases[i][0], b = cases[i][1], c = cases[i][2];
                    double x = root(a, b, c);
                    printf("a=%d b=%d c=%d root=%.2f check=%.2f\n", a, b, c, x, a * x * x + b * x);
                }
                return 0;
            }
            ### run.sh
            python3 sridhara.py > python.txt
            cc -o sridhara_c sridhara.c -lm
            ./sridhara_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
