package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 420: the Julian day numbers of 1 January 420 and 1 January 430, and the days between them, in Python and C. */
val db162Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "The Julian day numbers of 1 January 420 and 1 January 430, and the days between them, in Python and C",
        originalTitle = "Julian day number",
        author = "Julius Caesar's calendar, counted in later day numbers",
        year = 420,
        summary = "A day number counts the days from a fixed starting point, and a script checks that the Python and C programs print the same day numbers for 1 January 420 and 1 January 430, with 3653 days between them.",
        details = "The day numbers use the Julian calendar formula as written for those years, and the count of 3653 days includes the leap days of 420, 424 and 428. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### daynumber.py
            def julian_day(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            start = julian_day(420, 1, 1)
            end = julian_day(430, 1, 1)
            print("day number of 1 January 420:", start)
            print("day number of 1 January 430:", end)
            print("days between:", end - start)
            ### daynumber.c
            #include <stdio.h>

            static long julian_day(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                long start = julian_day(420, 1, 1);
                long end = julian_day(430, 1, 1);
                printf("day number of 1 January 420: %ld\n", start);
                printf("day number of 1 January 430: %ld\n", end);
                printf("days between: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 daynumber.py > python.txt
            cc -o daynumber_c daynumber.c
            ./daynumber_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
