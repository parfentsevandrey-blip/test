package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 40 to 50: the days from Caligula's murder to Claudius's death, counted in Python and in C. */
val db200Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the murder of Caligula to the death of Claudius in Python and C",
        originalTitle = "Days between two deaths",
        author = "No single author",
        year = 41,
        summary = "Caligula was murdered on 24 January 41 and Claudius died on 13 October 54, and a Python and a C program count the days between the two dates and check that they agree.",
        details = "Both dates are given as commonly stated, and the programs count days in the proleptic Gregorian calendar, which gives the same count as the Julian calendar for these years because no century year falls between the dates. The span of 5010 days crosses the leap days of 44, 48 and 52, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            caligula = date(41, 1, 24)     # murder of Caligula, as commonly dated
            claudius = date(54, 10, 13)    # death of Claudius, as commonly dated
            days = (claudius - caligula).days
            print("days from Caligula's murder to Claudius's death:", days)
            print("weeks and days:", days // 7, days % 7)
            ### days.c
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
                long caligula = days_from_civil(41, 1, 24);   /* murder of Caligula, as commonly dated */
                long claudius = days_from_civil(54, 10, 13);  /* death of Claudius, as commonly dated */
                long days = claudius - caligula;
                printf("days from Caligula's murder to Claudius's death: %ld\n", days);
                printf("weeks and days: %ld %ld\n", days / 7, days % 7);
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
