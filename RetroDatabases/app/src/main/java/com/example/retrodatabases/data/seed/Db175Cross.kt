package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 293: the days from the naming of the Caesars to the Roman new year of 294, in Python and C. */
val db175Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the naming of the Caesars to the Roman new year of 294, in Python and C",
        originalTitle = "Day count between two dates",
        author = "Julian calendar reckoners",
        year = 293,
        summary = "Days from the naming of the Caesars on 1 March 293 to the Roman new year of 294 come to 306, and a script checks that the Python and C programs print the same count.",
        details = "The most common date for the naming of the Caesars is 1 March 293, and the Roman civil year began on 1 January, so the span runs to the Kalends of January 294. The span holds no leap day, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            MONTH_DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

            def julian_leap(year):
                return year % 4 == 0

            def month_length(year, month):
                if month == 2 and julian_leap(year):
                    return 29
                return MONTH_DAYS[month - 1]

            def ordinal(year, month, day):
                # days counted from the start of the Julian calendar to the given date
                total = 0
                for y in range(1, year):
                    total += 366 if julian_leap(y) else 365
                for m in range(1, month):
                    total += month_length(year, m)
                return total + day

            start = ordinal(293, 3, 1)   # the Caesars were named on 1 March 293
            end = ordinal(294, 1, 1)     # the Kalends of January 294
            print("days:", end - start)
            ### days.c
            #include <stdio.h>

            static const int month_days[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

            static int julian_leap(int year) {
                return year % 4 == 0;
            }

            static int month_length(int year, int month) {
                if (month == 2 && julian_leap(year)) return 29;
                return month_days[month - 1];
            }

            static long ordinal(int year, int month, int day) {
                long total = 0;
                for (int y = 1; y < year; y++) total += julian_leap(y) ? 366 : 365;
                for (int m = 1; m < month; m++) total += month_length(year, m);
                return total + day;
            }

            int main(void) {
                long start = ordinal(293, 3, 1);   /* the Caesars were named on 1 March 293 */
                long end = ordinal(294, 1, 1);     /* the Kalends of January 294 */
                printf("days: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 days.py > python.txt
            cc -o days_c days.c
            ./days_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
