package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 324: the weekday of the battle of Chrysopolis, computed in Python and in C with the same Julian rule. */
val db172Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The weekday of the battle of Chrysopolis, computed in Python and C",
        originalTitle = "Weekday of 18 September 324",
        author = "No single author",
        year = 324,
        summary = "The battle of Chrysopolis fell on a Friday in the Julian reckoning, and a Python and a C program, each using the same weekday rule, print the same answer.",
        details = "The weekday rule is written for the Julian calendar, and the run script compares the two outputs line by line. The answer depends on the date usually given for the battle, not on the programs.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### weekday.py
            DAYS = ["Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
            year, month, day = 324, 9, 18
            k, j = year % 100, year // 100
            h = (day + (13 * (month + 1)) // 5 + k + k // 4 + 5 + 6 * j) % 7
            print("18 September 324 was a", DAYS[h])
            ### weekday.c
            #include <stdio.h>

            int main(void) {
                static const char *days[] = {"Saturday", "Sunday", "Monday", "Tuesday",
                                             "Wednesday", "Thursday", "Friday"};
                int year = 324, month = 9, day = 18;
                int k = year % 100, j = year / 100;
                int h = (day + (13 * (month + 1)) / 5 + k + k / 4 + 5 + 6 * j) % 7;
                printf("18 September 324 was a %s\n", days[h]);
                return 0;
            }
            ### run.sh
            python3 weekday.py > python.txt
            cc -o weekday_c weekday.c
            ./weekday_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
