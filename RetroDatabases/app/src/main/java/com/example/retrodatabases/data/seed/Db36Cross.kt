package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1683 to 1684: Leibniz's rising and falling test and Tschirnhaus's squared roots, each in Python and C. */
val db36Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.CROSS,
        section = "Calculus",
        title = "Leibniz's rising and falling test in Python and C",
        originalTitle = "Nova methodus pro maximis et minimis",
        author = "Gottfried Wilhelm Leibniz",
        year = 1684,
        summary = "The sign of Leibniz's slope, 20x - 3x^2, is tested once in Python and once in C, and a script checks that both programs print the same turning point.",
        details = "Leibniz's rule of 1684 says the curve rises while its slope is positive and falls once the slope turns negative. The two programs find the last rising whole number and the first falling one for y = x^2 (10 - x), and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### rise_fall.py
            def slope(x):
                # Leibniz's slope for y = x^2 (10 - x) is 20x - 3x^2; its sign shows whether the curve rises or falls.
                return 20 * x - 3 * x * x

            rising = [x for x in range(0, 10) if slope(x) > 0]
            falling = [x for x in range(0, 10) if slope(x) < 0]
            print("last rising whole number:", max(rising))
            print("first falling whole number:", min(falling))
            ### rise_fall.c
            #include <stdio.h>

            static int slope(int x) {
                /* Leibniz's slope for y = x^2 (10 - x) is 20x - 3x^2; its sign shows whether the curve rises or falls. */
                return 20 * x - 3 * x * x;
            }

            int main(void) {
                int last_rising = -1, first_falling = -1;
                for (int x = 0; x < 10; x++) {
                    if (slope(x) > 0) {
                        last_rising = x;
                    }
                    if (slope(x) < 0 && first_falling < 0) {
                        first_falling = x;
                    }
                }
                printf("last rising whole number: %d\n", last_rising);
                printf("first falling whole number: %d\n", first_falling);
                return 0;
            }
            ### run.sh
            python3 rise_fall.py > python.txt
            cc -o rise_fall_c rise_fall.c
            ./rise_fall_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.CROSS,
        section = "Algebra",
        title = "Tschirnhaus's squared roots in Python and C",
        originalTitle = "Methodus auferendi omnes terminos intermedios ex data aequatione",
        author = "Ehrenfried Walther von Tschirnhaus",
        year = 1683,
        summary = "The new coefficients of the equation with squared roots are computed once in Python and once in C, and a script checks that both print the same numbers.",
        details = "Tschirnhaus's squaring of the roots of x^3 - 9x^2 + 26x - 24 gives the coefficients 29, 244 and 576 from the old ones alone. Both programs use only whole-number arithmetic, so the run script can compare the printed lines exactly.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### squares.py
            e1, e2, e3 = 9, 26, 24   # the cubic x^3 - 9x^2 + 26x - 24, with roots 2, 3 and 4
            f1 = e1 * e1 - 2 * e2
            f2 = e2 * e2 - 2 * e1 * e3
            f3 = e3 * e3
            print("new coefficients:", f1, f2, f3)
            ### squares.c
            #include <stdio.h>

            int main(void) {
                /* The cubic x^3 - 9x^2 + 26x - 24 has sums 9, 26 and 24; squaring its roots gives new sums from these. */
                int e1 = 9, e2 = 26, e3 = 24;
                int f1 = e1 * e1 - 2 * e2;
                int f2 = e2 * e2 - 2 * e1 * e3;
                int f3 = e3 * e3;
                printf("new coefficients: %d %d %d\n", f1, f2, f3);
                return 0;
            }
            ### run.sh
            python3 squares.py > python.txt
            cc -o squares_c squares.c
            ./squares_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
