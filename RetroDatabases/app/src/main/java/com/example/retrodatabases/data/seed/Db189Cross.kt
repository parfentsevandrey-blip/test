package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 150: the days from the Nabonassar epoch of 747 BC to 1 January 150, in Python and in C. */
val db189Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "Days from the Nabonassar epoch to 1 January 150, in Python and C",
        originalTitle = "Julian day numbers",
        author = "Astronomers who used the Nabonassar era",
        year = 150,
        summary = "Days are counted from the Nabonassar epoch of 26 February 747 BC to 1 January 150 with a Julian day-number formula, and a script checks that the Python and C programs print the same count.",
        details = "Both dates are in the Julian calendar, so the programs use the Julian day-number formula, which has no Gregorian century rule. The run script compares the two outputs line by line, and the count is 327208 days.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### jd.py
            def julian_day(year, month, day):
                # year uses astronomical numbering, where 1 BC is year 0
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            start = julian_day(-746, 2, 26)   # 26 February 747 BC, the Nabonassar epoch
            target = julian_day(150, 1, 1)    # 1 January AD 150
            print("days from the Nabonassar epoch to 1 January 150:", target - start)
            ### jd.c
            #include <stdio.h>

            static long julian_day(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                long start = julian_day(-746, 2, 26);   /* 26 February 747 BC, the Nabonassar epoch */
                long target = julian_day(150, 1, 1);    /* 1 January AD 150 */
                printf("days from the Nabonassar epoch to 1 January 150: %ld\n", target - start);
                return 0;
            }
            ### run.sh
            python3 jd.py > python.txt
            cc -o jd_c jd.c
            ./jd_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
