package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1066: the days from Edward the Confessor's death to William's crowning, counted in Python and in C. */
val db98Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from Edward the Confessor's death to William's crowning in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Edward the Confessor and William I of England",
        year = 1066,
        summary = "Python and C programs count the days from Edward the Confessor's death on 5 January 1066 to William's crowning on 25 December 1066, and a script checks that both give 354.",
        details = "The C program counts days with a table of the days before each month and no date library, while the Python program uses the calendar, and both give the same result because 1066 is not a leap year. The dates are given as commonly stated, so the run tests the arithmetic rather than the events.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### edward_william.py
            from datetime import date

            edward_death = date(1066, 1, 5)          # Edward's death, as commonly given
            william_crowning = date(1066, 12, 25)    # William's crowning, as commonly given
            print("days from Edward's death to William's crowning:", (william_crowning - edward_death).days)
            ### edward_william.c
            #include <stdio.h>

            static const int days_before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

            static int day_of_year(int month, int day) {
                return days_before[month - 1] + day;   /* 1066 is not a leap year */
            }

            int main(void) {
                int start = day_of_year(1, 5);      /* Edward's death, as commonly given */
                int end = day_of_year(12, 25);      /* William's crowning, as commonly given */
                printf("days from Edward's death to William's crowning: %d\n", end - start);
                return 0;
            }
            ### run.sh
            python3 edward_william.py > python.txt
            cc -o edward_william_c edward_william.c
            ./edward_william_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
