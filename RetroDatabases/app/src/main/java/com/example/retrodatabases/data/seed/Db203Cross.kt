package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 10 to 20: the days from the death of Augustus to the death of Germanicus, counted in Python and in C. */
val db203Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the death of Augustus to the death of Germanicus in Python and C",
        originalTitle = "Julian day count",
        author = "Julian calendar reckoners",
        year = 14,
        summary = "Augustus died on 19 August 14 and Germanicus on 10 October 19, and a Python and a C program count the 1878 days between the two dates and print the same result.",
        details = "Both dates are given in the Julian calendar, as the sources reckon them, and the programs use the Julian day formula for both. The years 14 to 19 hold one leap day, in 16, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            def julian_day(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            augustus = julian_day(14, 8, 19)      # death of Augustus, 19 August 14
            germanicus = julian_day(19, 10, 10)   # death of Germanicus, 10 October 19
            print("days from the death of Augustus to the death of Germanicus:", germanicus - augustus)
            ### days.c
            #include <stdio.h>

            static long julian_day(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                long augustus = julian_day(14, 8, 19);      /* death of Augustus, 19 August 14 */
                long germanicus = julian_day(19, 10, 10);   /* death of Germanicus, 10 October 19 */
                printf("days from the death of Augustus to the death of Germanicus: %ld\n", germanicus - augustus);
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
