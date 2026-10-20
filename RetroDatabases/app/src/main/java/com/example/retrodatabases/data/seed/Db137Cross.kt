package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 670 to 680: the years of the Arab siege of Constantinople, 674 to 678, counted in Python and in C. */
val db137Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.CROSS,
        section = "Umayyad",
        title = "The years of the Arab siege of Constantinople, 674 to 678, counted in Python and C",
        originalTitle = "The siege of Constantinople",
        author = "The Umayyad caliphate",
        year = 674,
        summary = "A Python program and a C program count the years of the Arab siege of Constantinople, 674 to 678, and a script checks that both print the same figures.",
        details = "The siege is dated 674 to 678, so it covers five years counted inclusively and four years between its first and last year. The two programs print the same figures, so the check confirms the arithmetic, while the dates come from the commonly given span of the siege.",
        instructions = """
            1. Save the example as siege_years.py, siege_years.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### siege_years.py
            FIRST = 674  # the siege begins, as commonly dated
            LAST = 678   # the siege ends, as commonly dated
            print("first year:", FIRST)
            print("last year:", LAST)
            print("years counted inclusively:", LAST - FIRST + 1)
            print("years between the first and last:", LAST - FIRST)
            ### siege_years.c
            #include <stdio.h>

            int main(void) {
                int first = 674, last = 678;
                printf("first year: %d\n", first);
                printf("last year: %d\n", last);
                printf("years counted inclusively: %d\n", last - first + 1);
                printf("years between the first and last: %d\n", last - first);
                return 0;
            }
            ### run.sh
            python3 siege_years.py > python.txt
            cc -o siege_years_c siege_years.c
            ./siege_years_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
