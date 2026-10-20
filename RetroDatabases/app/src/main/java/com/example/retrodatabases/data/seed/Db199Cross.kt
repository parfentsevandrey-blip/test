package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code records of 55: the days from the death of Claudius to the death of Britannicus, in Python and C. */
val db199Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the death of Claudius to the death of Britannicus, in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Suetonius and Tacitus, for the two deaths",
        year = 55,
        summary = "The death of Claudius on 13 October 54 and the death of Britannicus in February 55 are a few months apart, and a script prints the days between them in Python and C.",
        details = "Accounts differ on the day of Britannicus's death, which falls between 11 and 12 February 55, so the programs print the days to both dates. The run script compares the two outputs line by line, and the counts 121 and 122 can be checked by hand.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            claudius = date(54, 10, 13)       # the death of Claudius, 13 October 54
            britannicus_a = date(55, 2, 11)   # one account of the death of Britannicus
            britannicus_b = date(55, 2, 12)   # the other account
            print("days to 11 February 55:", (britannicus_a - claudius).days)
            print("days to 12 February 55:", (britannicus_b - claudius).days)
            ### days.c
            #include <stdio.h>

            /* Julian calendar day count from a fixed origin; years 54 and 55 have the same leap years in both calendars. */
            static int is_leap(int y) { return y % 4 == 0; }

            static long day_number(int y, int m, int d) {
                static const int month_days[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                long n = 365L * (y - 1) + (y - 1) / 4;   /* Julian leap years before year y */
                for (int i = 0; i < m - 1; i++) {
                    n += month_days[i];
                    if (i == 1 && is_leap(y)) n += 1;
                }
                return n + d;
            }

            int main(void) {
                long claudius = day_number(54, 10, 13);
                long britannicus_a = day_number(55, 2, 11);
                long britannicus_b = day_number(55, 2, 12);
                printf("days to 11 February 55: %ld\n", britannicus_a - claudius);
                printf("days to 12 February 55: %ld\n", britannicus_b - claudius);
                return 0;
            }
            ### run.sh
            python3 days.py > python.txt
            cc -o days_c days.c
            ./days_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
