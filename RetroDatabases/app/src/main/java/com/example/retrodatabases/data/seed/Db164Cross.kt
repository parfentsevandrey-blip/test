package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 408: the days from the Rhine crossing of 406 to Stilicho's death, counted in Python and in C. */
val db164Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the Rhine crossing to Stilicho's death in Python and C",
        originalTitle = "Day count in Python and C",
        author = "Stilicho and the western court",
        year = 408,
        summary = "The Rhine was crossed on 31 December 406 and Stilicho died on 22 August 408, and a Python program and a C program count the days between the two dates and print the same result.",
        details = "Both programs count days in the proleptic Gregorian calendar, and the C program uses a fixed day formula rather than a library, so the run script compares the two outputs line by line. The dates are the usual ones, and the span is the same in the Julian calendar, since the two calendars differ by the same number of days at both dates.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            rhine = date(406, 12, 31)
            stilicho = date(408, 8, 22)
            print("days from the Rhine crossing to Stilicho's death:", (stilicho - rhine).days)
            ### days.c
            #include <stdio.h>

            /* days since 1 January 1970 in the proleptic Gregorian calendar, by a fixed formula */
            static long days_from_civil(long y, int m, int d) {
                y -= (m <= 2);
                long era = (y >= 0 ? y : y - 399) / 400;
                long yoe = y - era * 400;
                long doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + doe - 719468;
            }

            int main(void) {
                long days = days_from_civil(408, 8, 22) - days_from_civil(406, 12, 31);
                printf("days from the Rhine crossing to Stilicho's death: %ld\n", days);
                return 0;
            }
            ### run.sh
            python3 days.py > python.txt
            cc -o days_c days.c
            ./days_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
