package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1016: the days from Aethelred II's death to Cnut's death, counted in Python and in C. */
val db103Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from Aethelred II's death to Cnut's death in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Aethelred II and Cnut the Great",
        year = 1016,
        summary = "Python and C programs count the days from Aethelred II's death on 23 April 1016 to Cnut's death on 12 November 1035, and both give the same count of 7142.",
        details = "The C program counts days with its own leap-year rule and no date library, while the Python program uses the calendar, and both give the same count. The span holds no century year, so the Julian and Gregorian reckonings give the same number of days, and the run tests the arithmetic rather than the reigns.",
        instructions = """
            1. Save the first program as aethelred_cnut.py and the second as aethelred_cnut.c.
            2. Run the Python program with python3 aethelred_cnut.py, then compile and run the C program with cc -o aethelred_cnut_c aethelred_cnut.c and ./aethelred_cnut_c.
        """,
        example = """
            ### aethelred_cnut.py
            from datetime import date

            aethelred = date(1016, 4, 23)     # Aethelred II's death, as commonly given
            cnut = date(1035, 11, 12)         # Cnut's death, as commonly given
            print("days from Aethelred II's death to Cnut's death:", (cnut - aethelred).days)
            ### aethelred_cnut.c
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
                long start = day_number(1016, 4, 23);   /* Aethelred II's death, as commonly given */
                long end = day_number(1035, 11, 12);    /* Cnut's death, as commonly given */
                printf("days from Aethelred II's death to Cnut's death: %ld\n", end - start);
                return 0;
            }
        """,
    ),

)
