package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 397: the days from the murder of the prefect Rufinus to the death of Martin of Tours, in Python and C. */
val db165Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the murder of the praetorian prefect Rufinus to the death of Martin of Tours, in Python and C",
        originalTitle = "Days from the murder of the praetorian prefect Rufinus to the death of Martin of Tours, in Python and C",
        author = "Rufinus the prefect and Martin of Tours",
        year = 397,
        summary = "The praetorian prefect Rufinus was murdered on 27 November 395 and Martin of Tours died on 8 November 397, and two programs, one in Python and one in C, count the days between them.",
        details = "Both dates are the usual ones, and the C program counts days by its own leap-year rule rather than by a date library, so the two outputs must agree. The span crosses the leap day of February 396 and contains no century year, so the count is the same in the Julian and the Gregorian calendars.",
        instructions = """
            1. Save the programs as rufinus_martin.py and rufinus_martin.c, and the script as run.sh, in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### rufinus_martin.py
            from datetime import date

            murder = date(395, 11, 27)   # the murder of the praetorian prefect Rufinus, as commonly given
            death = date(397, 11, 8)     # the death of Martin of Tours, as commonly given
            print("days from Rufinus's murder to Martin's death:", (death - murder).days)
            ### rufinus_martin.c
            #include <stdio.h>

            static int is_leap(int year) {
                return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
            }

            static int month_length(int year, int month) {
                static const int lengths[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                if (month == 2 && is_leap(year)) {
                    return 29;
                }
                return lengths[month - 1];
            }

            /* Days counted from 1 January of year 1 to the given date. */
            static long day_number(int year, int month, int day) {
                long days = 0;
                for (int y = 1; y < year; y++) {
                    days += is_leap(y) ? 366 : 365;
                }
                for (int m = 1; m < month; m++) {
                    days += month_length(year, m);
                }
                return days + (day - 1);
            }

            int main(void) {
                long murder = day_number(395, 11, 27);   /* as commonly given */
                long death = day_number(397, 11, 8);     /* as commonly given */
                printf("days from Rufinus's murder to Martin's death: %ld\n", death - murder);
                return 0;
            }
            ### run.sh
            python3 rufinus_martin.py > python.txt
            cc -o rufinus_martin_c rufinus_martin.c
            ./rufinus_martin_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
