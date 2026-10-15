package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1690 to 1700: Halley's 1694 cubic iteration, written once in Python and once in C. */
val db35Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.CROSS,
        section = "Equation solving",
        title = "Halley's cubic iteration of 1694, in Python and C",
        originalTitle = "Methodus nova accurata et facilis inveniendi radices aequationum quarumcunque generaliter",
        author = "Edmond Halley",
        year = 1694,
        summary = "Halley's 1694 root method for a cubic is programmed twice, once in Python and once in C, and the two printed roots are compared.",
        details = "Halley published his method in 1694, and it uses the second derivative to correct a rough root faster than Raphson's simpler step. The example runs the method on x cubed minus 2x minus 5 in both languages and checks that the printed results match.",
        instructions = """
            1. Save the first program as halley.py, the C program as halley.c, and the script as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### halley.py
            def f(x):
                return x ** 3 - 2 * x - 5

            def d1(x):
                return 3 * x * x - 2

            def d2(x):
                return 6 * x

            x = 2.0
            for _ in range(20):
                fx = f(x)
                step = 2 * fx * d1(x) / (2 * d1(x) ** 2 - fx * d2(x))
                x = x - step
            print(f"root: {x:.4f}")
            ### halley.c
            #include <stdio.h>

            static double f(double x) { return x * x * x - 2.0 * x - 5.0; }
            static double d1(double x) { return 3.0 * x * x - 2.0; }
            static double d2(double x) { return 6.0 * x; }

            int main(void) {
                double x = 2.0;
                int i;
                for (i = 0; i < 20; i++) {
                    double fx = f(x);
                    double step = 2.0 * fx * d1(x) / (2.0 * d1(x) * d1(x) - fx * d2(x));
                    x = x - step;
                }
                printf("root: %.4f\n", x);
                return 0;
            }
            ### run.sh
            python3 halley.py > py_out.txt
            gcc -O2 -o halley_c halley.c -lm
            ./halley_c > c_out.txt
            cat py_out.txt
            cmp -s py_out.txt c_out.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.CROSS,
        section = "Acoustics",
        title = "Sauveur's beats in Python and C",
        originalTitle = "Sur la determination d'un son fixe",
        author = "Joseph Sauveur",
        year = 1700,
        summary = "Sauveur's beat count for pipes at 100 and 96 vibrations a second is programmed in Python and in C, and the two printed results are compared.",
        details = "His 1700 memoir used beats between sounds to pin down frequency differences, and both programs compute the same beat count from the same pair of frequencies.",
        instructions = """
            1. Save the first program as beats.py, the C program as beats.c, and the script as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### beats.py
            f1 = 100
            f2 = 96
            print(f"beats per second: {abs(f1 - f2)}")
            ### beats.c
            #include <stdio.h>

            int main(void) {
                int f1 = 100;
                int f2 = 96;
                printf("beats per second: %d\n", f1 > f2 ? f1 - f2 : f2 - f1);
                return 0;
            }
            ### run.sh
            python3 beats.py > py_out.txt
            gcc -o beats_c beats.c
            ./beats_c > c_out.txt
            cat py_out.txt
            cmp -s py_out.txt c_out.txt && echo "same result"
        """,
    ),

)
