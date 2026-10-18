package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1221 to 1228: days between two deaths and between a death and a canonization, each in Python and C with the same Julian day formula. */
val db82Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.CROSS,
        section = "Deaths",
        title = "Days from Dominic's death to Francis's death in Python and C",
        originalTitle = "Julian day number",
        author = "Dominican and Franciscan chroniclers",
        year = 1226,
        summary = "Dominic died on 6 August 1221 and Francis on 3 October 1226, and a script checks that the Python and C programs count the same 1884 days with a Julian day formula.",
        details = "Both programs use integer arithmetic with the same formula, so their outputs must match exactly, and the run script compares them line by line. The dates are given as commonly stated in the Julian reckoning.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            def jdn(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            print("days:", jdn(1226, 10, 3) - jdn(1221, 8, 6))
            ### days.c
            #include <stdio.h>

            static long jdn(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                printf("days: %ld\n", jdn(1226, 10, 3) - jdn(1221, 8, 6));
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

    entry(
        databaseId = 82,
        kind = Kind.CROSS,
        section = "Deaths",
        title = "Days from Louis VIII's death to Francis's canonization in Python and C",
        originalTitle = "Julian day number",
        author = "French and Franciscan chroniclers",
        year = 1228,
        summary = "Louis VIII of France died on 8 November 1226 and Francis was canonized on 16 July 1228, and a script checks that the Python and C programs count the same 616 days.",
        details = "The two programs share one integer formula for the Julian day number, so their outputs must match, and the run script compares them line by line. Both dates are given as commonly stated.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### count.py
            def jdn(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            print("days:", jdn(1228, 7, 16) - jdn(1226, 11, 8))
            ### count.c
            #include <stdio.h>

            static long jdn(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                printf("days: %ld\n", jdn(1228, 7, 16) - jdn(1226, 11, 8));
                return 0;
            }
            ### run.sh
            python3 count.py > python.txt
            cc -o count_c count.c
            ./count_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
