package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 499: Aryabhata's rule for the circumference of a circle, computed in Python and in C. */
val db155Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.CROSS,
        section = "Circles",
        title = "The circumference rule of Aryabhata in Python and in C",
        originalTitle = "Aryabhatiya circumference rule",
        author = "Aryabhata",
        year = 499,
        summary = "Aryabhata's rule gives a circle's circumference as 62832 for a diameter of 20000, and two programs, one in Python and one in C, print the same circumferences for five diameters.",
        details = "The rule adds four to one hundred, multiplies by eight and adds sixty-two thousand, which gives an approximate ratio of 3.1416 between circumference and diameter. The two programs use whole-number arithmetic and print the same lines, which the run script compares line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### circle.py
            DIAMETERS = [1, 2, 7, 10, 20000]   # illustrative diameters
            RULE = (100 + 4) * 8 + 62000       # Aryabhata's numerator over 20000
            for d in DIAMETERS:
                tenths = RULE * d // 2           # circumference times 10000
                print(f"diameter {d}: circumference {tenths // 10000}.{tenths % 10000:04d}")
            ### circle.c
            #include <stdio.h>

            int main(void) {
                const long diameters[] = {1, 2, 7, 10, 20000};   /* illustrative diameters */
                const long rule = (100 + 4) * 8 + 62000;          /* Aryabhata's numerator over 20000 */
                for (int i = 0; i < 5; i++) {
                    long d = diameters[i];
                    long scaled = rule * d / 2;                    /* circumference times 10000 */
                    printf("diameter %ld: circumference %ld.%04ld\n", d, scaled / 10000, scaled % 10000);
                }
                return 0;
            }
            ### run.sh
            python3 circle.py > python.txt
            cc -o circle_c circle.c
            ./circle_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
