package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1037: the days from the battle of Stiklestad to Conrad II's Constitutio, counted in Python and in C. */
val db101Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the battle of Stiklestad to Conrad II's Constitutio in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Olaf II of Norway and Conrad II",
        year = 1037,
        summary = "Python and C programs count the days from the battle of Stiklestad on 29 July 1030 to Conrad II's Constitutio on 28 May 1037, and a script checks that both give 2495.",
        details = "The C program counts days with a Julian leap rule and no date library, while the Python program uses date objects, and both count the leap days of 1032 and 1036 alike. The dates are given as commonly stated, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### stiklestad.py
            from datetime import date

            stiklestad = date(1030, 7, 29)    # the battle of Stiklestad, as commonly given
            constitutio = date(1037, 5, 28)   # Conrad II's Constitutio de feudis, as commonly given
            print("days from Stiklestad to the Constitutio:", (constitutio - stiklestad).days)
            ### stiklestad.c
            #include <stdio.h>

            static int is_leap(int y) { return y % 4 == 0; }   /* Julian rule: no century exceptions */

            static long day_number(int y, int m, int d) {
                static const int days_before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long n = 0;
                for (int year = 1; year < y; year++) n += is_leap(year) ? 366 : 365;
                n += days_before[m - 1];
                if (m > 2 && is_leap(y)) n += 1;
                return n + d;
            }

            int main(void) {
                long start = day_number(1030, 7, 29);   /* Stiklestad, as commonly given */
                long end = day_number(1037, 5, 28);     /* Constitutio, as commonly given */
                printf("days from Stiklestad to the Constitutio: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 stiklestad.py > python.txt
            cc -o stiklestad_c stiklestad.c
            ./stiklestad_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
