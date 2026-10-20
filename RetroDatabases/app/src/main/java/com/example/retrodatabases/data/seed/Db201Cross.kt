package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 30 to 40: the days from Sejanus's execution to the death of Tiberius, counted in Python and in C. */
val db201Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from Sejanus's execution to Tiberius's death, in Python and C",
        originalTitle = "Days between two dates",
        author = "No single author",
        year = 37,
        summary = "Sejanus's execution in October 31 and Tiberius's death in March 37 are 1976 days apart, and a script checks that the Python and C programs print the same count.",
        details = "Python counts the days with the datetime module and C counts them with a short day-number function, and the run script compares the two outputs line by line. Both dates are Julian dates in the sources, and the count is the same in the Gregorian calendar used by Python, because no century year falls between them. The day used for Sejanus is 18 October, the usual date for a fall of 31, although some texts put his fall about 29.",
        instructions = """
            1. Save the example as sejanus_tiberius.py and sejanus_tiberius.c, and the run.sh part beside them.
            2. Run it: sh run.sh
        """,
        example = """
            ### sejanus_tiberius.py
            from datetime import date

            sejanus = date(31, 10, 18)   # Sejanus's execution, usual date
            tiberius = date(37, 3, 16)   # Tiberius's death, usual date
            print("days from 18 October 31 to 16 March 37:", (tiberius - sejanus).days)
            ### sejanus_tiberius.c
            #include <stdio.h>

            static long day_number(int year, int month, int day) {
                static const int before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long y = year - 1;
                long days = y * 365 + y / 4 + before[month - 1] + day;
                if (month > 2 && year % 4 == 0) {
                    days += 1;
                }
                return days;
            }

            int main(void) {
                long sejanus = day_number(31, 10, 18);
                long tiberius = day_number(37, 3, 16);
                printf("days from 18 October 31 to 16 March 37: %ld\n", tiberius - sejanus);
                return 0;
            }
            ### run.sh
            python3 sejanus_tiberius.py > python.txt
            cc -o sejanus_c sejanus_tiberius.c
            ./sejanus_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
