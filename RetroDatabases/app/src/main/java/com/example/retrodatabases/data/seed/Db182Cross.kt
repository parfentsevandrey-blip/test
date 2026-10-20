package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 220 to 230: the years of Rome from its founding for AD 220 to 230, in Python and in C. */
val db182Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The years of Rome from its founding for AD 220 to 230, counted in Python and C",
        originalTitle = "Ab urbe condita",
        author = "Varro and later Roman writers",
        year = 220,
        summary = "Years counted from the founding of Rome by Varro's reckoning of 753 BC give AD 220 as year 973 and AD 230 as year 983, and a script checks that the Python and C programs print the same lines.",
        details = "Varro's reckoning places the founding of Rome in 753 BC, and other writers gave other founding years, so the count is one reckoning among several. The year 220 is the start of the counted span and not a date given for the reckoning itself, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the sections of the example as roman.py, roman.c and run.sh in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### roman.py
            OFFSET = 753   # AD 1 is year 754 from the founding, because 753 BC is year 1
            for ad in range(220, 231, 5):
                print(f"AD {ad} is year {ad + OFFSET} from the founding")
            print("years counted:", len(range(220, 231)))
            ### roman.c
            #include <stdio.h>

            int main(void) {
                int offset = 753;   /* AD 1 is year 754 from the founding, because 753 BC is year 1 */
                for (int ad = 220; ad <= 230; ad += 5) {
                    printf("AD %d is year %d from the founding\n", ad, ad + offset);
                }
                printf("years counted: %d\n", 230 - 220 + 1);
                return 0;
            }
            ### run.sh
            python3 roman.py > python.txt
            cc -o roman_c roman.c
            ./roman_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
