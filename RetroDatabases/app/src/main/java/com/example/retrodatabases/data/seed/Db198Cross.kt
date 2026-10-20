package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 69: the days from the death of Galba to the death of Vitellius, in Python and C. */
val db198Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.CROSS,
        section = "Emperors",
        title = "Days from Galba's death to Vitellius's death, in Python and C",
        originalTitle = "Days between two deaths",
        author = "No single author",
        year = 69,
        summary = "The days from the death of Galba on 15 January 69 to the death of Vitellius on 20 December 69 are counted in Python and in C, and a script checks that both print the same count.",
        details = "The dates are the commonly given ones in the Roman reckoning, and both fall in 69, which is not a leap year, so the count is the same in the Julian and the Gregorian reckoning. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the example as galba.py, galba.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### galba.py
            from datetime import date

            galba = date(69, 1, 15)        # Galba's death, as commonly given
            vitellius = date(69, 12, 20)   # Vitellius's death, as commonly given
            print("days from Galba's death to Vitellius's death:", (vitellius - galba).days)
            ### galba.c
            #include <stdio.h>

            static int day_of_year(int year, int month, int day) {
                static const int lengths[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                int leap = (year % 4 == 0);
                int total = day;
                for (int m = 0; m < month - 1; m++) {
                    total += lengths[m];
                    if (m == 1 && leap) total += 1;
                }
                return total;
            }

            int main(void) {
                int galba = day_of_year(69, 1, 15);       /* Galba's death, as commonly given */
                int vitellius = day_of_year(69, 12, 20);  /* Vitellius's death, as commonly given */
                printf("days from Galba's death to Vitellius's death: %d\n", vitellius - galba);
                return 0;
            }
            ### run.sh
            python3 galba.py > python.txt
            cc -o galba_c galba.c
            ./galba_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
