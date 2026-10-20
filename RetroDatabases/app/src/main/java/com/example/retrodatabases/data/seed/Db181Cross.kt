package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 230 to 240: the days the Julian reckoning gains on the Egyptian year over one Sothic cycle, in Python and in C. */
val db181Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.CROSS,
        section = "Cycles",
        title = "Days the Julian reckoning gains on the Egyptian year in one Sothic cycle, in Python and C",
        originalTitle = "Julian leap rule",
        author = "Julian calendar reckoners",
        year = 238,
        summary = "A Python program and a C program count the days the Julian reckoning gains on the Egyptian year over one Sothic cycle, and both print the same total of 365.",
        details = "The Julian rule is used here as a model, with every fourth year a leap year, so each leap day is one day the Julian reckoning gains on the 365-day Egyptian year over the 1460 years of one cycle. The sources do not date the cycle to 238; that year is the anchor, the year of Censorinus's book De die natali, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the three files below in one folder: leapdays.py, leapdays.c and run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### leapdays.py
            CYCLE_YEARS = 1460
            count = 0
            for year in range(1, CYCLE_YEARS + 1):
                if year % 4 == 0:
                    count += 1
            print("days gained by the Julian reckoning:", count)
            print("years in the cycle:", CYCLE_YEARS)
            ### leapdays.c
            #include <stdio.h>

            int main(void) {
                const int cycle_years = 1460;   /* Julian years in one Sothic cycle, years 1 to 1460 */
                int count = 0;
                for (int year = 1; year <= cycle_years; year++) {
                    if (year % 4 == 0) {
                        count++;
                    }
                }
                printf("days gained by the Julian reckoning: %d\n", count);
                printf("years in the cycle: %d\n", cycle_years);
                return 0;
            }
            ### run.sh
            python3 leapdays.py > python.txt
            cc -o leapdays_c leapdays.c
            ./leapdays_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
