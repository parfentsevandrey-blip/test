package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 449 and 450: the days from the Robber Council to the death of Theodosius II, in Python and C. */
val db160Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.CROSS,
        section = "Time",
        title = "Days from the Robber Council to the death of Theodosius II, in Python and C",
        originalTitle = "Day count between two dates",
        author = "No single author",
        year = 449,
        summary = "A Python program and a C program count the days from 8 August 449 to 28 July 450, and a script checks that both print the same number.",
        details = "Both programs use the Gregorian reckoning, and the Julian calendar has the same leap days in these years, so the count of 354 days holds in either calendar. The first date is the day set for the council and the second is the day Theodosius II died.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### council.py
            from datetime import date

            council = date(449, 8, 8)
            death = date(450, 7, 28)
            print("days from the council to the death:", (death - council).days)
            ### council.c
            #include <stdio.h>

            static long days_from_civil(long y, long m, long d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                long yoe = y - era * 400;
                long doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + doe - 719468;
            }

            int main(void) {
                long council = days_from_civil(449, 8, 8);
                long death = days_from_civil(450, 7, 28);
                printf("days from the council to the death: %ld\n", death - council);
                return 0;
            }
            ### run.sh
            python3 council.py > python.txt
            cc -o council_c council.c
            ./council_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
