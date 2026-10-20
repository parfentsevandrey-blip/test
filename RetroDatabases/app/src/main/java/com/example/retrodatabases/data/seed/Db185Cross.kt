package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 192 to 197: the days from Commodus's death to the battle of Lugdunum, in Python and C. */
val db185Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from Commodus's death to the battle of Lugdunum, in Python and C",
        originalTitle = "Days between two dated events",
        author = "No single author",
        year = 192,
        summary = "Commodus died on 31 December 192 and the battle of Lugdunum was fought on 19 February 197, and a Python program and a C program count the days between the two dates.",
        details = "Both dates are the usual ones, and the two programs print the same count of 1511 days, because no century year falls between them, so the Gregorian count that Python uses matches the Julian count of the period. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            commodus = date(192, 12, 31)   # Commodus's death, as commonly given
            lugdunum = date(197, 2, 19)    # the battle of Lugdunum, as commonly given
            print("days from Commodus's death to Lugdunum:", (lugdunum - commodus).days)
            ### days.c
            #include <stdio.h>

            /* Days since 1 January 1970 in the proleptic Gregorian calendar (Hinnant's method). */
            static long days_from_civil(long y, unsigned m, unsigned d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                unsigned yoe = (unsigned)(y - era * 400);
                unsigned doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                unsigned doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + (long)doe - 719468;
            }

            int main(void) {
                long commodus = days_from_civil(192, 12, 31);
                long lugdunum = days_from_civil(197, 2, 19);
                printf("days from Commodus's death to Lugdunum: %ld\n", lugdunum - commodus);
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
