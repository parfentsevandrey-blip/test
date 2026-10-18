package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1270: Louis IX's landing at Carthage and death at Tunis, counted in weeks and days, in Python and C. */
val db77Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.CROSS,
        section = "Campaigns",
        title = "Louis IX's landing at Carthage to his death at Tunis, in weeks and days",
        originalTitle = "Days from landing to death",
        author = "Louis IX of France and his army",
        year = 1270,
        summary = "King Louis IX landed at Carthage on 18 July 1270 and died at Tunis on 25 August 1270, and a short program in Python and in C counts the 38 days between them and writes the count as weeks and days.",
        details = "Both dates are given as commonly stated, and 1270 is not a leap year, so the C program needs only a table of month lengths to count the days. The run script compares the two outputs line by line, and the answer can be checked by hand: 38 days is five weeks and three days.",
        instructions = """
            1. Save the files below in one folder: louis_weeks.py and louis_weeks.c.
            2. Run the script: sh run.sh
        """,
        example = """
            ### louis_weeks.py
            from datetime import date

            # Louis IX landed at Carthage on 18 July 1270 and died at Tunis on 25 August 1270, as commonly given.
            LANDING = date(1270, 7, 18)
            DEATH = date(1270, 8, 25)
            days = (DEATH - LANDING).days
            print("days from landing to death:", days)
            print(f"as weeks and days: {days // 7} weeks and {days % 7} days")
            ### louis_weeks.c
            #include <stdio.h>

            /* Days in each month of 1270, which is not a leap year. */
            static const int month_days[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

            static int day_of_year(int month, int day) {
                int total = 0;
                for (int m = 0; m < month - 1; m++) {
                    total += month_days[m];
                }
                return total + day;
            }

            int main(void) {
                int days = day_of_year(8, 25) - day_of_year(7, 18);
                printf("days from landing to death: %d\n", days);
                printf("as weeks and days: %d weeks and %d days\n", days / 7, days % 7);
                return 0;
            }
            ### run.sh
            python3 louis_weeks.py > python.txt
            cc -o louis_c louis_weeks.c
            ./louis_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
