package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 138: the days of Hadrian's reign from his accession to his death, in Python and in C. */
val db191Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from Hadrian's accession to his death, in Python and C",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 138,
        summary = "Hadrian's reign ran from 11 August 117 to his death on 10 July 138, and a script checks that the Python and C programs count the same 7638 days.",
        details = "The accession is given as 11 August 117, although some accounts give 10 August, which would make the count one day longer, and the death of 10 July 138 at Baiae is widely agreed. The span lies between 117 and 138, where the Julian and Gregorian reckonings have the same leap years, so the day count is the same in both; the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### hadrian.py
            from datetime import date

            accession = date(117, 8, 11)   # Hadrian's accession, as commonly given
            death = date(138, 7, 10)       # Hadrian's death at Baiae, as commonly given
            print("days from accession to death:", (death - accession).days)
            ### hadrian.c
            #include <stdio.h>

            /* days since 1 January 1970, proleptic Gregorian calendar */
            static long days_from_civil(long y, unsigned m, unsigned d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                unsigned yoe = (unsigned)(y - era * 400);
                unsigned doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + (long)doe - 719468;
            }

            int main(void) {
                long accession = days_from_civil(117, 8, 11);
                long death = days_from_civil(138, 7, 10);
                printf("days from accession to death: %ld\n", death - accession);
                return 0;
            }
            ### run.sh
            python3 hadrian.py > python.txt
            cc -o hadrian_c hadrian.c
            ./hadrian_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
