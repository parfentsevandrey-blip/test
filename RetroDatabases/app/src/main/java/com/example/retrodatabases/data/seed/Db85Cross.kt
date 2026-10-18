package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1190 and 1191: the days from the drowning of Frederick Barbarossa to the surrender of Acre, counted in Python and in C. */
val db85Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.CROSS,
        section = "Time",
        title = "Days from Frederick Barbarossa's death to the surrender of Acre, in Python and C",
        originalTitle = "Days between two events",
        author = "No single author",
        year = 1191,
        summary = "Frederick Barbarossa drowned on 10 June 1190 and Acre surrendered on 12 July 1191, and a Python and a C program each count the days between the two dates.",
        details = "The C program counts days with a day-count formula written out by hand, and the run script checks that both programs print 397 days. The dates are given in the reckoning of the time, and the count is the same in the Julian and Gregorian calendars because no century year falls between them.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### acre.py
            from datetime import date

            start = date(1190, 6, 10)   # Frederick Barbarossa's death
            end = date(1191, 7, 12)     # surrender of Acre
            print("days:", (end - start).days)
            ### acre.c
            #include <stdio.h>

            /* Days since the start of the proleptic Gregorian era, by Howard Hinnant's day-count method. */
            static long days_from_civil(long y, unsigned m, unsigned d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                unsigned yoe = (unsigned)(y - era * 400);
                unsigned doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + (long)doe - 719468;
            }

            int main(void) {
                long start = days_from_civil(1190, 6, 10);   /* Frederick Barbarossa's death */
                long end = days_from_civil(1191, 7, 12);     /* surrender of Acre */
                printf("days: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 acre.py > python.txt
            cc -o acre_c acre.c
            ./acre_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
