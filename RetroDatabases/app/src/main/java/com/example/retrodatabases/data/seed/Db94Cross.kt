package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1109: the days from Henry I's coronation to Anselm's death, counted in Python and in C. */
val db94Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from Henry I's coronation to Anselm's death in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Henry I and Anselm of Canterbury",
        year = 1109,
        summary = "Python and C programs count the days from Henry I's coronation on 5 August 1100 to Anselm's death on 21 April 1109, and a script checks that both give 3181.",
        details = "The C program counts days with its own leap-year rule and no date library, while the Python program uses the calendar, and both count the leap days of 1104 and 1108 alike. The dates are given as commonly stated, so the run tests the arithmetic rather than the events.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### henry_anselm.py
            from datetime import date

            coronation = date(1100, 8, 5)    # Henry I's coronation, as commonly given
            death = date(1109, 4, 21)        # Anselm's death, as commonly given
            print("days from Henry I's coronation to Anselm's death:", (death - coronation).days)
            ### henry_anselm.c
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
                long start = day_number(1100, 8, 5);   /* Henry I's coronation, as commonly given */
                long end = day_number(1109, 4, 21);    /* Anselm's death, as commonly given */
                printf("days from Henry I's coronation to Anselm's death: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 henry_anselm.py > python.txt
            cc -o henry_anselm_c henry_anselm.c
            ./henry_anselm_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
