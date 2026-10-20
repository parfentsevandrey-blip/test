package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 438: three dated days of the 430s, each with its Julian day number and weekday, computed in Python and in C. */
val db161Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "Three dated days of the 430s, each with its Julian day number, in Python and C",
        originalTitle = "Julian day number",
        author = "Joseph Scaliger",
        year = 438,
        summary = "A Julian day number gives each date one running count of days, and a script checks that the Python and C programs print the same count and weekday for three dated days.",
        details = "The three dates are 28 August 430, 22 June 431 and 15 February 438, all Julian dates, and the weekday is worked out from the day number with a fixed offset. The run script compares the two outputs line by line, so the same result comes from both languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### julian.py
            NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]

            def julian_day_number(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            for label, year, month, day in [
                ("28 August 430", 430, 8, 28),
                ("22 June 431", 431, 6, 22),
                ("15 February 438", 438, 2, 15),
            ]:
                jdn = julian_day_number(year, month, day)
                print(f"{label}: JDN {jdn}, {NAMES[(jdn + 1) % 7]}")
            ### julian.c
            #include <stdio.h>

            static long julian_day_number(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                const char *names[] = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
                const char *labels[] = {"28 August 430", "22 June 431", "15 February 438"};
                long dates[3][3] = {{430, 8, 28}, {431, 6, 22}, {438, 2, 15}};
                for (int i = 0; i < 3; i++) {
                    long jdn = julian_day_number(dates[i][0], dates[i][1], dates[i][2]);
                    printf("%s: JDN %ld, %s\n", labels[i], jdn, names[(jdn + 1) % 7]);
                }
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
