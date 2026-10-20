package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 210 to 220: the days from the death of Severus at York to the death of Caracalla, in Python and C. */
val db183Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the death of Severus at York to the death of Caracalla, in Python and C",
        originalTitle = "Days between two dated deaths",
        author = "No single author",
        year = 217,
        summary = "The days from 4 February 211, when Septimius Severus died at York, to 8 April 217, when Caracalla died near Carrhae, are counted in Python and in C with the same leap rule.",
        details = "The count is 2255 days, and both programs use the rule that every fourth year is a leap year, as the Julian calendar of the Roman empire did. The day 8 April is the date commonly given for Caracalla's death, so the count is exact for the dates as written, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            def julian_day(year, month, day):
                days_before = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334]
                leap = 1 if year % 4 == 0 else 0
                before_year = 365 * (year - 1) + (year - 1) // 4
                month_days = days_before[month - 1] + (leap if month > 2 else 0)
                return before_year + month_days + day - 1

            start = julian_day(211, 2, 4)   # Severus dies at York
            end = julian_day(217, 4, 8)     # Caracalla dies near Carrhae
            print("days from 4 February 211 to 8 April 217:", end - start)
            ### days.c
            #include <stdio.h>

            static long julian_day(int year, int month, int day) {
                static const int days_before[12] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                int leap = (year % 4 == 0) ? 1 : 0;
                long before_year = 365L * (year - 1) + (year - 1) / 4;
                long month_days = days_before[month - 1] + ((leap && month > 2) ? 1 : 0);
                return before_year + month_days + day - 1;
            }

            int main(void) {
                long start = julian_day(211, 2, 4);   /* Severus dies at York */
                long end = julian_day(217, 4, 8);     /* Caracalla dies near Carrhae */
                printf("days from 4 February 211 to 8 April 217: %ld\n", end - start);
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
