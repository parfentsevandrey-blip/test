package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 810 to 820: the months of the siege of Baghdad, from August 812 to September 813, counted in Python and in C. */
val db123Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.CROSS,
        section = "Abbasid",
        title = "The months of the siege of Baghdad, August 812 to September 813, in Python and C",
        originalTitle = "The months of the siege of Baghdad, August 812 to September 813, in Python and C",
        author = "Tahir ibn Husayn and al-Amin",
        year = 812,
        summary = "Python and C programs count the months from August 812 to September 813 for the siege of Baghdad, and a script checks that both give 13 months.",
        details = "Both programs take the year and month of each date and subtract, so they measure the span in months rather than in years. The start and end are the dates usually given for the fighting over Baghdad, which ran from August 812 until late September 813.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### siege.py
            start = 812 * 12 + 8     # August 812, counted as months from year zero
            end = 813 * 12 + 9       # September 813
            print("months of the siege:", end - start)
            ### siege.c
            #include <stdio.h>

            int main(void) {
                int start = 812 * 12 + 8;
                int end = 813 * 12 + 9;
                printf("months of the siege: %d\n", end - start);
                return 0;
            }
            ### run.sh
            python3 siege.py > python.txt
            cc -o siege_c siege.c
            ./siege_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
