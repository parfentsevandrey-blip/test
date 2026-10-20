package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 410: the days from the sack of Rome to the death of Honorius, counted by a Julian day-number formula in Python and in C. */
val db163Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the sack of Rome to the death of the emperor Honorius in Python and C",
        originalTitle = "Days between two dated events",
        author = "Alaric and the emperor Honorius",
        year = 410,
        summary = "A Julian day-number formula counts the days from the sack of Rome to the death of Honorius, and a script checks that the Python and C programs print the same count.",
        details = "Both dates are given as commonly stated in the Julian calendar, and the formula counts days in that calendar. The span contains the leap days of 412, 416 and 420, and the formula counts them, so both programs print 4739 days. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            def julian_day_number(year, month, day):
                # Julian calendar day number; the formula is exact for the years used here
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083


            sack = julian_day_number(410, 8, 24)
            death = julian_day_number(423, 8, 15)
            print("days from the sack of Rome to the death of the emperor Honorius:", death - sack)
            ### days.c
            #include <stdio.h>

            static long julian_day_number(int year, int month, int day) {
                int a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                long sack = julian_day_number(410, 8, 24);
                long death = julian_day_number(423, 8, 15);
                printf("days from the sack of Rome to the death of the emperor Honorius: %ld\n", death - sack);
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
