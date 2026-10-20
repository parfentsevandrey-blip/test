package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 100: the days from Pliny's Panegyricus to Trajan's death, counted in Python and in C. */
val db194Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from Pliny's Panegyricus to Trajan's death, in Python and C",
        originalTitle = "Panegyricus",
        author = "Pliny the Younger and Trajan",
        year = 100,
        summary = "Pliny's Panegyricus was delivered on 1 September 100 and Trajan died on 8 August 117, and a script checks that the Python and C programs count the same days.",
        details = "Both dates are given as commonly stated and are read here as Julian dates. The first date falls after the leap day of 100, so the Python count and the C count with the Julian leap rule agree for this span, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pliny_trajan.py
            from datetime import date

            panegyricus = date(100, 9, 1)     # Pliny's Panegyricus, as commonly given
            trajan_death = date(117, 8, 8)    # Trajan's death, as commonly given
            print("days from Pliny's Panegyricus to Trajan's death:", (trajan_death - panegyricus).days)
            ### pliny_trajan.c
            #include <stdio.h>

            static int is_leap(int year) {
                return year % 4 == 0;   /* Julian leap rule */
            }

            static long ordinal(int year, int month, int day) {
                static const int month_days[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                long days = 0;
                for (int y = 1; y < year; y++) {
                    days += is_leap(y) ? 366 : 365;
                }
                for (int m = 1; m < month; m++) {
                    days += month_days[m - 1];
                    if (m == 2 && is_leap(year)) {
                        days += 1;
                    }
                }
                return days + day;
            }

            int main(void) {
                long start = ordinal(100, 9, 1);
                long end = ordinal(117, 8, 8);
                printf("days from Pliny's Panegyricus to Trajan's death: %ld\n", end - start);
                return 0;
            }
            ### run.sh
            python3 pliny_trajan.py > python.txt
            cc -o pliny_trajan_c pliny_trajan.c
            ./pliny_trajan_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
