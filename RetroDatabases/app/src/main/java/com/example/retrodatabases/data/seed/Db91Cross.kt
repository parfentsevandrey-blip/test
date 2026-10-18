package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1132 and 1133: the Saros of 223 lunations and a count of Roman paces, each in Python and C. */
val db91Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.CROSS,
        section = "Distances",
        title = "The Roman mile from a count of paces in Python and C",
        originalTitle = "Mille passus",
        author = "Roman surveyors and medieval travellers",
        year = 1133,
        summary = "A thousand paces made a Roman mile, and a script checks that the Python and C programs print the same miles and paces for three counts.",
        details = "The counts are illustrative, and each program splits a count of paces into whole miles and the paces left over. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### miles.py
            PACES_PER_MILE = 1000
            for paces in [12450, 3000, 999]:
                miles, left = divmod(paces, PACES_PER_MILE)
                print(f"{paces} paces: {miles} miles and {left} paces")
            ### miles.c
            #include <stdio.h>

            int main(void) {
                int counts[] = {12450, 3000, 999};
                const int paces_per_mile = 1000;
                for (int i = 0; i < 3; i++) {
                    int paces = counts[i];
                    printf("%d paces: %d miles and %d paces\n", paces, paces / paces_per_mile, paces % paces_per_mile);
                }
                return 0;
            }
            ### run.sh
            python3 miles.py > python.txt
            cc -o miles_c miles.c
            ./miles_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 91,
        kind = Kind.CROSS,
        section = "Eclipses",
        title = "The Saros of 223 lunations in Python and C",
        originalTitle = "Saros",
        author = "Babylonian astronomers, as inherited by later astronomers",
        year = 1132,
        summary = "The Saros of 223 lunations is turned into days, and a script checks that the Python and C programs print the same product with two decimal places.",
        details = "Both programs use the mean lunar month of 29.5306 days, and the C program rounds to two decimal places as the Python one does. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### saros.py
            MONTH_DAYS = 29.5306
            LUNATIONS = 223
            print(f"{LUNATIONS} lunations: {LUNATIONS * MONTH_DAYS:.2f} days")
            ### saros.c
            #include <stdio.h>

            int main(void) {
                const double month_days = 29.5306;   /* mean lunar month */
                const int lunations = 223;           /* the Saros cycle */
                printf("%d lunations: %.2f days\n", lunations, lunations * month_days);
                return 0;
            }
            ### run.sh
            python3 saros.py > python.txt
            cc -o saros_c saros.c
            ./saros_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
