package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1760 to 1770: Lambert's tangent expansion and cotton yarn counts, each in Python and C. */
val db28Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.CROSS,
        section = "Continued fractions",
        title = "Lambert's tangent continued fraction in Python and C",
        originalTitle = "Memoire sur quelques proprietes remarquables des quantites transcendantes circulaires et logarithmiques",
        author = "Johann Heinrich Lambert",
        year = 1768,
        summary = "Lambert's continued fraction for tan x is cut at four depths and evaluated exactly, once in Python and once in C, and a script checks that both print the same fractions.",
        details = "Lambert's memoir of 1768 used a continued fraction for the tangent in its proof that pi is irrational, and the expansion is cut at depths one to four here. The Python program evaluates the fractions with exact rationals, and the C program does the same with integer numerators and denominators, so the two outputs can be compared line by line. At depth four the value at x = 1 is 841/540.",
        instructions = """
            1. Save the example as tan_cf.py and tan_cf.c, and the script as run.sh, in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### tan_cf.py
            from fractions import Fraction as F

            def tan_cf(x, depth):
                # Lambert's continued fraction: tan x = x / (1 - x^2 / (3 - x^2 / (5 - ...))), cut after the given depth.
                x2 = F(x) * x
                tail = F(2 * depth + 1)      # innermost partial denominator
                for k in range(depth - 1, 0, -1):
                    tail = (2 * k + 1) - x2 / tail
                return F(x) / (1 - x2 / tail)

            for depth in range(1, 5):
                print(f"depth {depth}: {tan_cf(1, depth)}")
            ### tan_cf.c
            #include <stdio.h>

            int main(void) {
                for (int depth = 1; depth <= 4; depth++) {
                    /* The innermost partial denominator 2 depth + 1 is kept as the fraction tn / td. */
                    long long tn = 2 * depth + 1, td = 1;
                    for (int k = depth - 1; k >= 1; k--) {
                        /* (2k+1) - 1/(tn/td) = ((2k+1) tn - td) / tn */
                        long long next = (2 * k + 1) * tn - td;
                        td = tn;
                        tn = next;
                    }
                    /* tan = 1 / (1 - 1/tail) = tn / (tn - td) */
                    printf("depth %d: %lld/%lld\n", depth, tn, tn - td);
                }
                return 0;
            }
            ### run.sh
            python3 tan_cf.py > python.txt
            cc -o tan_cf_c tan_cf.c
            ./tan_cf_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.CROSS,
        section = "Textiles",
        title = "Cotton yarn counted in hanks in Python and C",
        originalTitle = "Cotton yarn counted in hanks in Python and C",
        author = "The Lancashire cotton trade",
        year = 1764,
        summary = "The count of cotton yarn in hanks is the yards divided by 840 for each pound, and the program prints the count for four skeins once in Python and once in C.",
        details = "The cotton hank was 840 yards, and a yarn's count was the number of hanks in one pound of it. The example uses four illustrative skeins and computes the count with integer division in Python and again in C, and the run script compares the two outputs. The count was the everyday measure of cotton yarn in the Lancashire trade of the period.",
        instructions = """
            1. Save the example as hanks.py and hanks.c, and the script as run.sh, in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### hanks.py
            HANK = 840   # yards in a cotton hank

            def hanks(yards, pounds):
                # The count is the number of hanks in one pound, so the yards are divided by 840 for each pound.
                return yards // (HANK * pounds)

            for yards, pounds in ((16800, 1), (8400, 1), (16800, 2), (25200, 3)):
                print(f"{yards} yards at {pounds} lb: {hanks(yards, pounds)} hanks")
            ### hanks.c
            #include <stdio.h>

            int main(void) {
                const int hank = 840;   /* yards in a cotton hank */
                int yards[4] = {16800, 8400, 16800, 25200};
                int pounds[4] = {1, 1, 2, 3};
                for (int i = 0; i < 4; i++) {
                    printf("%d yards at %d lb: %d hanks\n", yards[i], pounds[i], yards[i] / (hank * pounds[i]));
                }
                return 0;
            }
            ### run.sh
            python3 hanks.py > python.txt
            cc -o hanks_c hanks.c
            ./hanks_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
