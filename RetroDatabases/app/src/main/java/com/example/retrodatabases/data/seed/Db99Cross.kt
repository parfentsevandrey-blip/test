package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1059: the days from the Great Schism to the papal decree, counted in Python and in C. */
val db99Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the Great Schism to the 1059 papal decree in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Cardinal Humbert and Pope Nicholas II",
        year = 1059,
        summary = "Python and C programs count the days from the bull of 16 July 1054 to the decree of 13 April 1059, and a script checks that both give 1732.",
        details = "The C program uses the Julian leap rule and no date library, while the Python program uses the calendar, and the two rules agree here because no century year falls between the two dates. The dates are as commonly stated, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the sections below as schism_decree.py, schism_decree.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### schism_decree.py
            from datetime import date

            schism = date(1054, 7, 16)     # the bull of the Great Schism, as commonly given
            decree = date(1059, 4, 13)     # the papal election decree, as commonly given
            print("days from the Great Schism to the papal decree:", (decree - schism).days)
            ### schism_decree.c
            #include <stdio.h>

            static int is_leap(int y) { return y % 4 == 0; }

            static long day_number(int y, int m, int d) {
                static const int days_before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long n = 0;
                for (int year = 1; year < y; year++) n += is_leap(year) ? 366 : 365;
                n += days_before[m - 1];
                if (m > 2 && is_leap(y)) n += 1;
                return n + d;
            }

            int main(void) {
                long start = day_number(1054, 7, 16);   /* the bull, as commonly given */
                long end = day_number(1059, 4, 13);     /* the decree, as commonly given */
                printf("days from the Great Schism to the papal decree: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 schism_decree.py > python.txt
            cc -o schism_decree_c schism_decree.c
            ./schism_decree_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
