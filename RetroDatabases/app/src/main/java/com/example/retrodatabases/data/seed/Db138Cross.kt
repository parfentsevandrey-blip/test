package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 660 to 670: the years of use of the Linde calendar from 665 to 728, counted in Python and C. */
val db138Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "The years of use of the Linde calendar, from 665 to 728, counted in Python and C",
        originalTitle = "Linde li",
        author = "Li Chunfeng",
        year = 665,
        summary = "The Linde calendar, issued in 665 and last used in 728, is counted by a Python program and a C program that print the same two figures.",
        details = "The difference between the two years is 63, but counting both the year of issue and the last year gives 64 years of use, and the two programs print both figures. The sources give 728 as the calendar's end, and the Dayan calendar of 729 replaced it.",
        rare = true,
        instructions = """
            1. Save the example as linde_span.py, linde_span.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### linde_span.py
            ISSUED = 665     # the Linde calendar issued in 665, as usually given
            LAST_USED = 728  # its last year in use, as usually given
            print("difference between 665 and 728:", LAST_USED - ISSUED)
            print("years of use counting both ends:", LAST_USED - ISSUED + 1)
            ### linde_span.c
            #include <stdio.h>

            int main(void) {
                int issued = 665, last_used = 728;
                printf("difference between 665 and 728: %d\n", last_used - issued);
                printf("years of use counting both ends: %d\n", last_used - issued + 1);
                return 0;
            }
            ### run.sh
            python3 linde_span.py > python.txt
            cc -o linde_span_c linde_span.c
            ./linde_span_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
