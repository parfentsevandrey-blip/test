package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 161: the days from the death of Antoninus Pius to the birth of Commodus, in Python and in C. */
val db188Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the death of Antoninus Pius to the birth of Commodus, in Python and C",
        originalTitle = "Days between two dates in 161",
        author = "Python and C programmers",
        year = 161,
        summary = "Antoninus Pius died on 7 March 161 and Commodus was born on 31 August 161, and a Python program and a C program both count 177 days between the two dates.",
        details = "Both dates are given as commonly stated, and 161 is not a leap year, so the count needs no leap rule. The C program takes the day of the year for each date from a table of month lengths, and the Python program uses the date library, so the two must print the same number.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            start = date(161, 3, 7)
            end = date(161, 8, 31)
            print("days from the death of Antoninus Pius to the birth of Commodus:", (end - start).days)
            ### days.c
            #include <stdio.h>

            static const int month_days[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

            static int day_of_year(int month, int day) {
                int total = day;
                for (int m = 0; m < month - 1; m++) {
                    total += month_days[m];
                }
                return total;
            }

            int main(void) {
                int start = day_of_year(3, 7);
                int end = day_of_year(8, 31);
                printf("days from the death of Antoninus Pius to the birth of Commodus: %d\n", end - start);
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
