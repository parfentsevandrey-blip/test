package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1079: the lengths of the Jalali months in a common year and a leap year, in Python and C. */
val db97Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The lengths of the Jalali months in a common year and a leap year, in Python and C",
        originalTitle = "The lengths of the Jalali months in a common year and a leap year, in Python and C",
        author = "Malik-Shah's astronomers",
        year = 1079,
        summary = "A Python program and a C program add up the Jalali months to give 365 days in a common year and 366 in a leap year, and a script checks that both give the same result.",
        details = "The first six months have 31 days, the next five have 30, and the last has 29, or 30 in a leap year. The C program uses an array and no date library, and the Python program uses a list, so the run tests the arithmetic in both languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### jalali_months.py
            months = [31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30]   # months 1 to 11
            print("days in a common year:", sum(months) + 29)
            print("days in a leap year:", sum(months) + 30)
            ### jalali_months.c
            #include <stdio.h>

            int main(void) {
                int months[11] = {31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30};
                int total = 0;
                for (int i = 0; i < 11; i++) total += months[i];
                printf("days in a common year: %d\n", total + 29);
                printf("days in a leap year: %d\n", total + 30);
                return 0;
            }
            ### run.sh
            python3 jalali_months.py > python.txt
            cc -o jalali_months_c jalali_months.c
            ./jalali_months_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
