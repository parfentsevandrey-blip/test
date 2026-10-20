package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 274: the Julian day number and weekday of Aurelian's Sun festival of 25 December, computed in Python and C. */
val db177Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The Julian day number and weekday of 25 December 274, in Python and C",
        originalTitle = "Julian day number",
        author = "Joseph Scaliger, who devised the day count",
        year = 274,
        summary = "The Julian day number of 25 December 274 is 1821495, a Friday, and a script checks that the Python and C programs print the same day number and weekday.",
        details = "The day number is computed from the Julian calendar rules, which were in use in 274, and the weekday comes from the remainder when the day number is divided by seven, with day 0 a Monday. The two programs print the same lines, and the run script compares their outputs line by line.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### julian.py
            def julian_day_number(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            jdn = julian_day_number(274, 12, 25)   # 25 December 274 in the Julian calendar
            names = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            print("julian day number:", jdn)
            print("weekday:", names[jdn % 7])
            ### julian.c
            #include <stdio.h>

            int main(void) {
                int year = 274, month = 12, day = 25;   /* 25 December 274 in the Julian calendar */
                int a = (14 - month) / 12;
                int y = year + 4800 - a;
                int m = month + 12 * a - 3;
                long jdn = day + (153 * m + 2) / 5 + 365L * y + y / 4 - 32083;
                static const char *names[] = {"Monday", "Tuesday", "Wednesday", "Thursday",
                                              "Friday", "Saturday", "Sunday"};
                printf("julian day number: %ld\n", jdn);
                printf("weekday: %s\n", names[jdn % 7]);
                return 0;
            }
            ### run.sh
            python3 julian.py > python.txt
            cc -o julian_c julian.c
            ./julian_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
