package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1620 to 1630: a slide rule's log product and a logarithm by square roots, each in Python and C. */
val db42Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.CROSS,
        section = "Calculating",
        title = "Oughtred's log product in Python and C",
        originalTitle = "Circles of Proportion and the Horizontal Instrument",
        author = "William Oughtred",
        year = 1622,
        summary = "The slide rule's addition of two logarithms is written once in Python and once in C, and a script checks that both print the same products.",
        details = "The two programs add the logarithms of two numbers and print the products read back from the sums. Both print to the same decimal places, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### oughtred.py
            import math
            for a, b in ((2, 3), (4, 25)):
                total = math.log10(a) + math.log10(b)
                print(f"{a} x {b}: sum of logs {total:.4f}, product {10 ** total:.1f}")
            ### oughtred.c
            #include <stdio.h>
            #include <math.h>

            int main(void) {
                int pairs[2][2] = {{2, 3}, {4, 25}};
                for (int i = 0; i < 2; i++) {
                    double total = log10(pairs[i][0]) + log10(pairs[i][1]);
                    printf("%d x %d: sum of logs %.4f, product %.1f\n", pairs[i][0], pairs[i][1], total, pow(10.0, total));
                }
                return 0;
            }
            ### run.sh
            python3 oughtred.py > python.txt
            cc -o oughtred_c oughtred.c -lm
            ./oughtred_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.CROSS,
        section = "Logarithms",
        title = "Logarithms by square roots in Python and C",
        originalTitle = "Arithmetica logarithmica",
        author = "Henry Briggs",
        year = 1624,
        summary = "Briggs's method of chained square roots is written once in Python and once in C, and a script checks that both print the same logarithms.",
        details = "The same chained square roots of ten run in both languages, using only the basic operations and the square root, so the two programs agree exactly. Both print four decimal places, and the run script compares the two outputs.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### briggs_cross.py
            import math

            def log10_by_roots(x, steps=40):
                y, value, root, weight = 0.0, float(x), 10.0, 1.0
                for _ in range(steps):
                    root = math.sqrt(root)
                    weight /= 2
                    if value >= root:
                        value /= root
                        y += weight
                return y

            for x in (2, 3, 5, 7):
                print(f"log10 of {x} by square roots: {log10_by_roots(x):.4f}")
            ### briggs_cross.c
            #include <stdio.h>
            #include <math.h>

            static double log10_by_roots(double x) {
                double y = 0.0, value = x, root = 10.0, weight = 1.0;
                for (int k = 0; k < 40; k++) {
                    root = sqrt(root);
                    weight /= 2;
                    if (value >= root) {
                        value /= root;
                        y += weight;
                    }
                }
                return y;
            }

            int main(void) {
                int xs[4] = {2, 3, 5, 7};
                for (int i = 0; i < 4; i++) {
                    printf("log10 of %d by square roots: %.4f\n", xs[i], log10_by_roots((double)xs[i]));
                }
                return 0;
            }
            ### run.sh
            python3 briggs_cross.py > python.txt
            cc -o briggs_cross_c briggs_cross.c -lm
            ./briggs_cross_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
