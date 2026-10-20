package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 383: the days from the Edict of Thessalonica to Gratian's death, counted in Python and in C. */
val db166Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the Edict of Thessalonica to the death of Gratian, in Python and C",
        originalTitle = "Day count in Python and C",
        author = "The imperial courts of the west and east",
        year = 383,
        summary = "The Edict of Thessalonica is dated 27 February 380 and Gratian died on 25 August 383, and a Python program and a C program count the 1275 days between them and print the same result.",
        details = "One account gives 28 February 380 for the edict, and the example starts from the earlier 27 February reckoning. Both programs count days in the proleptic Gregorian calendar, and no calendar change falls inside the span, so the count is the same in the Julian reckoning of the time. The C program uses a fixed day formula rather than a library, so the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### edict_gratian.py
            from datetime import date

            edict = date(380, 2, 27)
            gratian = date(383, 8, 25)
            print("days from the Edict of Thessalonica to the death of Gratian:", (gratian - edict).days)
            ### edict_gratian.c
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
                long days = days_from_civil(383, 8, 25) - days_from_civil(380, 2, 27);
                printf("days from the Edict of Thessalonica to the death of Gratian: %ld\n", days);
                return 0;
            }
            ### run.sh
            python3 edict_gratian.py > python.txt
            cc -o edict_gratian_c edict_gratian.c
            ./edict_gratian_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
