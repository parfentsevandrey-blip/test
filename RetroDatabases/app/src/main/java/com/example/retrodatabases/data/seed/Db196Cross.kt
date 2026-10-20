package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 81: the days from the death of Titus to the death of Domitian, counted in Python and in C. */
val db196Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.CROSS,
        section = "Days",
        title = "Days from the death of Titus to the death of Domitian, in Python and C",
        originalTitle = "Days between two deaths",
        author = "Titus and Domitian",
        year = 81,
        summary = "Titus died on 13 September 81 and Domitian was killed on 18 September 96, and a Python and a C program count the days between the two dates and check that they agree.",
        details = "Accounts differ by a day on the death of Titus, and the count uses 13 September. The span crosses the leap days of 84, 88, 92 and 96, and the count is the same in the Julian and Gregorian reckonings because no century year falls inside it.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### titus_domitian.py
            from datetime import date

            titus = date(81, 9, 13)        # death of Titus, 13 September 81, as usually given
            domitian = date(96, 9, 18)     # death of Domitian, 18 September 96
            print("days from the death of Titus to the death of Domitian:", (domitian - titus).days)
            ### titus_domitian.c
            #include <stdio.h>

            /* days from a fixed origin for a date in the proleptic Gregorian calendar (Hinnant's method) */
            static long days_from_civil(long y, long m, long d) {
                y -= m <= 2;
                long era = (y >= 0 ? y : y - 399) / 400;
                long yoe = y - era * 400;
                long doy = (153 * (m > 2 ? m - 3 : m + 9) + 2) / 5 + d - 1;
                long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + doe - 719468;
            }

            int main(void) {
                long titus = days_from_civil(81, 9, 13);      /* death of Titus, 13 September 81 */
                long domitian = days_from_civil(96, 9, 18);   /* death of Domitian, 18 September 96 */
                printf("days from the death of Titus to the death of Domitian: %ld\n", domitian - titus);
                return 0;
            }
            ### run.sh
            python3 titus_domitian.py > python.txt
            cc -o titus_c titus_domitian.c
            ./titus_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
