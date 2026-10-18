package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1099: the days from El Cid's taking of Valencia to his death, counted in Python and in C. */
val db95Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from El Cid's taking of Valencia to his death in Python and C",
        originalTitle = "Days between two fixed dates",
        author = "Rodrigo Diaz de Vivar, El Cid",
        year = 1099,
        summary = "Python and C programs count the days from El Cid's taking of Valencia on 15 June 1094 to his death there on 10 July 1099, and a script checks that both give 1851.",
        details = "The span holds one leap day, in 1096, and no century year, so the two programs agree on the count. Accounts differ by two days on the taking of Valencia, and the example uses the day most accounts give.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### valencia.py
            from datetime import date

            taken = date(1094, 6, 15)    # El Cid takes Valencia, as commonly given
            death = date(1099, 7, 10)    # El Cid dies at Valencia, as commonly given
            print("days from the taking of Valencia to the death of El Cid:", (death - taken).days)
            ### valencia.c
            #include <stdio.h>

            static int is_leap(int y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0; }

            static long day_number(int y, int m, int d) {
                static const int days_before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long n = 0;
                for (int year = 1; year < y; year++) n += is_leap(year) ? 366 : 365;
                n += days_before[m - 1];
                if (m > 2 && is_leap(y)) n += 1;
                return n + d;
            }

            int main(void) {
                long taken = day_number(1094, 6, 15);   /* El Cid takes Valencia, as commonly given */
                long death = day_number(1099, 7, 10);   /* El Cid dies at Valencia, as commonly given */
                printf("days from the taking of Valencia to the death of El Cid: %ld\n", death - taken);
                return 0;
            }
            ### run.sh
            python3 valencia.py > python.txt
            cc -o valencia_c valencia.c
            ./valencia_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
