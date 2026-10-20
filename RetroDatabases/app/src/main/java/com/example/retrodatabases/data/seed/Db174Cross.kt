package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 306 to 308: the days from Constantine's acclamation at York to the Carnuntum conference, counted in Python and in C. */
val db174Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from Constantine's acclamation at York to the Carnuntum conference in Python and C",
        originalTitle = "Days between two dated events",
        author = "Constantine, Galerius and Licinius",
        year = 306,
        summary = "Constantine's acclamation at York and the Carnuntum conference are counted in days by a Python program and a C program, and a script checks that both print the same count.",
        details = "The dates are as commonly given, 25 July 306 and 11 November 308, and the count is 840 days. The C program uses a civil-date formula written out in the file, so the comparison tests two separate calendar calculations.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### york.py
            from datetime import date

            york = date(306, 7, 25)         # the acclamation at York, as commonly given
            carnuntum = date(308, 11, 11)   # the conference at Carnuntum, as commonly given
            print("days from York to Carnuntum:", (carnuntum - york).days)
            ### york.c
            #include <stdio.h>

            /* days since 1970 in the proleptic Gregorian calendar, by a civil-date formula */
            static long days_from_civil(long y, unsigned m, unsigned d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                unsigned yoe = (unsigned)(y - era * 400);
                unsigned doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + (long)doe - 719468;
            }

            int main(void) {
                long york = days_from_civil(306, 7, 25);
                long carnuntum = days_from_civil(308, 11, 11);
                printf("days from York to Carnuntum: %ld\n", carnuntum - york);
                return 0;
            }
            ### run.sh
            python3 york.py > python.txt
            cc -o york_c york.c
            ./york_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
