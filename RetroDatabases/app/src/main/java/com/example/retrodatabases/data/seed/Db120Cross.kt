package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 850: two fractions added cross-wise, computed in Python and in C. */
val db120Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.CROSS,
        section = "India",
        title = "Adding two fractions cross-wise without reduction, in Python and C",
        originalTitle = "Adding two fractions cross-wise without reduction, in Python and C",
        author = "Mahavira, a Jain mathematician",
        year = 850,
        summary = "Adding two fractions cross-wise gives a sum over the product of the denominators, and two small programs in Python and C print the same sums for three pairs of fractions.",
        details = "The program multiplies across and keeps the sum over the product of the denominators without reducing it, so 3/4 plus 5/6 is shown as 38/24. The method is cross-multiplication, a modern route to the same sum, and the example is a computation rather than a quotation of Mahavira's rules. Both programs use whole numbers, so the outputs must match, and the run script checks that they do.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### add_fractions.py
            def add(a, b, c, d):
                return a * d + c * b, b * d

            cases = [(1, 2, 1, 3), (3, 4, 5, 6), (2, 5, 1, 10)]
            for a, b, c, d in cases:
                num, den = add(a, b, c, d)
                print(f"{a}/{b} + {c}/{d} = {num}/{den}")
            ### add_fractions.c
            #include <stdio.h>

            static void add(int a, int b, int c, int d, int *num, int *den) {
                *num = a * d + c * b;
                *den = b * d;
            }

            int main(void) {
                int cases[3][4] = {{1, 2, 1, 3}, {3, 4, 5, 6}, {2, 5, 1, 10}};
                for (int i = 0; i < 3; i++) {
                    int a = cases[i][0], b = cases[i][1], c = cases[i][2], d = cases[i][3];
                    int num, den;
                    add(a, b, c, d, &num, &den);
                    printf("%d/%d + %d/%d = %d/%d\n", a, b, c, d, num, den);
                }
                return 0;
            }
            ### run.sh
            python3 add_fractions.py > python.txt
            cc -o add_fractions_c add_fractions.c
            ./add_fractions_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
