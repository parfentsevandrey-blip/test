package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 313: the days left in the year after the Milan letter was posted, in Python and in C. */
val db173Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days left in 313 after the Milan letter was posted, in Python and C",
        originalTitle = "Days to the end of the year",
        author = "Licinius",
        year = 313,
        summary = "After the Milan letter was posted on 13 June 313, 201 days remained in the year, and a script checks that the Python and C programs count the same days.",
        details = "The example counts the days from the posting to the last day of 313 in Python and in C, and the run script compares the two outputs line by line. The year 313 is a common year, so the C program needs only a table of month lengths.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            posting = date(313, 6, 13)     # the Milan letter posted at Nicomedia, as commonly given
            year_end = date(313, 12, 31)
            print("days from 13 June 313 to 31 December 313:", (year_end - posting).days)
            ### days.c
            #include <stdio.h>

            int main(void) {
                /* days in each month of 313, a common year */
                int month_days[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                int before_posting = 0;
                for (int m = 0; m < 5; m++) {   /* January to May */
                    before_posting += month_days[m];
                }
                int posting_day = before_posting + 13;   /* 13 June is day 164 of the year */
                int year_length = 0;
                for (int m = 0; m < 12; m++) {
                    year_length += month_days[m];
                }
                printf("days from 13 June 313 to 31 December 313: %d\n", year_length - posting_day);
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
