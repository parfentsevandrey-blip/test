package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1002: the days from the death of Otto III to St Brice's Day, counted in Python and in C. */
val db104Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the death of Otto III to St Brice's Day in Python and C",
        originalTitle = "Days from the death of Otto III to St Brice's Day in Python and C",
        author = "Otto III and Aethelred II",
        year = 1002,
        summary = "Python and C programs count the days from the death of Otto III on 24 January 1002 to St Brice's Day on 13 November 1002, and a script checks that both give 293.",
        details = "The C program counts days with its own leap-year rule and no date library, while the Python program uses the calendar, and both count the days of the non-leap year 1002 alike. The dates are given as commonly stated, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### otto_brice.py
            from datetime import date

            otto_death = date(1002, 1, 24)     # death of Otto III, as commonly stated
            brice = date(1002, 11, 13)         # St Brice's Day massacre, as commonly stated
            print("days from Otto III's death to St Brice's Day:", (brice - otto_death).days)
            ### otto_brice.c
            #include <stdio.h>

            static int is_leap(int y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0; }

            static long day_number(int y, int m, int d) {
                static const int days_before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long n = 0;
                for (int year = 1; year < y; year++) n += is_leap(year) ? 366 : 365;
                n += days_before[m - 1];
                if (m > 2 && is_leap(y)) n += 1;
                return n + d;
            }

            int main(void) {
                long start = day_number(1002, 1, 24);   /* death of Otto III, as commonly stated */
                long end = day_number(1002, 11, 13);    /* St Brice's Day massacre, as commonly stated */
                printf("days from Otto III's death to St Brice's Day: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 otto_brice.py > python.txt
            cc -o otto_brice_c otto_brice.c
            ./otto_brice_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
