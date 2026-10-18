package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1046: the weekday of 25 December for 1040 to 1050 in the Julian reckoning, in Python and C. */
val db100Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The weekdays of Christmas Day from 1040 to 1050, computed in Python and C",
        originalTitle = "The weekdays of Christmas Day from 1040 to 1050, computed in Python and C",
        author = "Julian calendar reckoners",
        year = 1046,
        summary = "Python and C programs find the weekday of 25 December for each year from 1040 to 1050 by the Julian leap rule, and a script shows that both print the same days.",
        details = "Christmas fell on a Thursday in 1040 and in 1046, and on a Sunday in 1043 and 1048, while the Julian leap rule makes every fourth year a leap year. The two programs use only whole-number arithmetic, so the result is exact, and it says nothing about the events of those Christmases.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### weekdays.py
            NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]

            def jan1_weekday(year):
                # Julian calendar: 1 January of year 1 was a Saturday; each earlier year adds 365 days and each fourth year a leap day
                days = (year - 1) * 365 + (year - 1) // 4
                return (6 + days) % 7

            for year in range(1040, 1051):
                # 25 December is day 358 of a common year and day 359 of a leap year, counted from 1 January
                offset = 359 if year % 4 == 0 else 358
                print(year, NAMES[(jan1_weekday(year) + offset) % 7])
            ### weekdays.c
            #include <stdio.h>

            static const char *names[] = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

            static int jan1_weekday(int year) {
                long days = (long)(year - 1) * 365 + (year - 1) / 4;
                return (int)((6 + days) % 7);
            }

            int main(void) {
                for (int year = 1040; year <= 1050; year++) {
                    int offset = (year % 4 == 0) ? 359 : 358;   /* 25 December, counted from 1 January */
                    printf("%d %s\n", year, names[(jan1_weekday(year) + offset) % 7]);
                }
                return 0;
            }
            ### run.sh
            python3 weekdays.py > python.txt
            cc -o weekdays_c weekdays.c
            ./weekdays_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
