package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 96 to 98: the days from Domitian's assassination to Nerva's death, counted in Python and in C. */
val db195Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.CROSS,
        section = "Time",
        title = "Days from Domitian's murder to Nerva's death, in Python and C",
        originalTitle = "Days between two imperial dates",
        author = "No single author",
        year = 98,
        summary = "The days from Domitian's murder on 18 September 96 to Nerva's death on 27 January 98 are counted in Python and in C, and a run script checks that both print the same count.",
        details = "Most references give the two dates used here, but one passage gives 19 September 96 for Nerva's proclamation and another gives 25 January 98 for Nerva's death, and a different reading changes the count. The span contains no leap day, because the leap day of 96 falls before the murder and 97 and 98 are not leap years, so the count is 496 days. The Python program uses the standard date library and the C program a Julian day count, and both print 496.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            start = date(1996, 9, 18)   # Domitian's murder, as most references give it
            end = date(1998, 1, 27)     # Nerva's death, as most references give it
            print("days from Domitian's murder to Nerva's death:", (end - start).days)
            ### days.c
            #include <stdio.h>

            /* days since 1 January of year 1 in the Julian calendar, with a leap year every fourth year */
            static long days_from(int year, int month, int day) {
                static const int before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long days = 365L * (year - 1) + (year - 1) / 4;
                days += before[month - 1] + day - 1;
                if (month > 2 && year % 4 == 0) {
                    days += 1;
                }
                return days;
            }

            int main(void) {
                long span = days_from(1998, 1, 27) - days_from(1996, 9, 18);
                printf("days from Domitian's murder to Nerva's death: %ld\n", span);
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
