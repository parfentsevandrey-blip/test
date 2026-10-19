package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 770 to 780: the years between the Lombard end of 774, the Herstal assembly of 779 and the Tang two-tax of 780, computed in Python and in C. */
val db127Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.CROSS,
        section = "Reforms",
        title = "The years between the Lombard end, the Herstal assembly and the Tang two-tax, in Python and C",
        originalTitle = "Intervals between three dated events",
        author = "Charlemagne and Yang Yan",
        year = 774,
        summary = "The years between the Lombard end, the Herstal assembly and the two-tax are printed by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The dates used are 774 for Pavia, 779 for Herstal and 780 for the two-tax, and the three spans printed are 5, 1 and 6 years. The figures show the arithmetic of the dates only, and they do not settle any link between the events.",
        instructions = """
            1. Save the example as gaps.py, gaps.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### gaps.py
            EVENTS = [("Lombard kingdom ends", 774), ("Herstal assembly", 779), ("two-tax reform", 780)]
            lombard, herstal, two_tax = [year for _, year in EVENTS]
            print("years from the Lombard end to Herstal:", herstal - lombard)
            print("years from Herstal to the two-tax:", two_tax - herstal)
            print("years from the Lombard end to the two-tax:", two_tax - lombard)
            ### gaps.c
            #include <stdio.h>

            int main(void) {
                int lombard = 774, herstal = 779, two_tax = 780;
                printf("years from the Lombard end to Herstal: %d\n", herstal - lombard);
                printf("years from Herstal to the two-tax: %d\n", two_tax - herstal);
                printf("years from the Lombard end to the two-tax: %d\n", two_tax - lombard);
                return 0;
            }
            ### run.sh
            python3 gaps.py > python.txt
            cc -o gaps_c gaps.c
            ./gaps_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
