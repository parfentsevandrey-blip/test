package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 79: the two dates given for the eruption of Vesuvius, and the days between them, in Python and in C. */
val db197Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.CROSS,
        section = "Eruption",
        title = "The two dates given for the eruption of 79, the days between them in Python and C",
        originalTitle = "Ante diem nonum Kalendas Septembres",
        author = "Pliny the Younger and the excavators of Pompeii",
        year = 79,
        summary = "Pliny the Younger dates the eruption to 24 August, a charcoal inscription from Pompeii reads XVI K NOV for 17 October, and a script counts the 54 days between the two dates in Python and C.",
        details = "The inscription was found in 2018 in the Casa del Giardino in Regio V, and it gives no year, so its link to 79 rests on the context; accounts differ on whether the eruption fell in August or in the autumn. Both dates fall in 79, which is not a leap year in either calendar, so the day counts agree whichever reckoning is used.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### days.py
            MONTH_DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]   # 79 is not a leap year

            def day_of_year(month, day):
                return sum(MONTH_DAYS[:month - 1]) + day

            traditional = day_of_year(8, 24)   # 24 August, from Pliny the Younger
            graffito = day_of_year(10, 17)     # 17 October, from the Pompeii inscription
            print("days between the two dates:", graffito - traditional)
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
                int traditional = day_of_year(8, 24);
                int graffito = day_of_year(10, 17);
                printf("days between the two dates: %d\n", graffito - traditional);
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
