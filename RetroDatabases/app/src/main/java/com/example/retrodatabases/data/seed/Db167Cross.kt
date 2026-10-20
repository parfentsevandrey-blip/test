package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 378: the days from the battle of Adrianople to the death of Basil of Caesarea, in Python and in C. */
val db167Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the battle of Adrianople to the death of Basil of Caesarea, in Python and C",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 378,
        summary = "The battle of Adrianople and the death of Basil of Caesarea are 145 days apart, and a Python program and a C program count the days and must print the same line.",
        details = "Both dates are given as commonly stated, with Basil's death on 1 January 379. No century year falls between them, so the day count is the same in the Julian reckoning and in the calendar used by the two programs, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days_adrianople.py
            from datetime import date

            adrianople = date(378, 8, 9)   # the battle of Adrianople, as commonly given
            basil = date(379, 1, 1)        # the death of Basil of Caesarea, as commonly given
            print("days from Adrianople to Basil's death:", (basil - adrianople).days)
            ### days_adrianople.c
            #include <stdio.h>

            /* Days from 1970-01-01 to a Gregorian date, by the civil-day formula. */
            static long days_from_civil(long y, long m, long d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                long yoe = y - era * 400;
                long doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + doe - 719468;
            }

            int main(void) {
                long adrianople = days_from_civil(378, 8, 9);   /* the battle of Adrianople, as commonly given */
                long basil = days_from_civil(379, 1, 1);        /* the death of Basil of Caesarea, as commonly given */
                printf("days from Adrianople to Basil's death: %ld\n", basil - adrianople);
                return 0;
            }
            ### run.sh
            python3 days_adrianople.py > python.txt
            cc -o days_adrianople_c days_adrianople.c
            ./days_adrianople_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
