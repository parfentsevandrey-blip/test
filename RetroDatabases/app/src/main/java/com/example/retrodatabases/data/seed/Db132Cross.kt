package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 720 to 730: the years between the Nihon Shoki, the Sanze Isshin no Ho and the Konden Einen Shizai Ho, computed in Python and in C. */
val db132Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.CROSS,
        section = "Japanese",
        title = "The years between the Nihon Shoki, the Sanze law and the Konden law, in Python and C",
        originalTitle = "Japanese year spans",
        author = "The Japanese court",
        year = 720,
        summary = "The years between the Nihon Shoki, the Sanze law and the Konden law are printed by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The dates used are 720 for the Nihon Shoki, 723 for the Sanze Isshin no Ho and 743 for the Konden Einen Shizai Ho, and the programs print the spans of 3 and 20 years and the total of 23. The dates are the usual ones, and the Sanze law's own date is the one in the Sanze record, so the figures show arithmetic rather than a claim about how the laws were linked.",
        instructions = """
            1. Save the example as japan_years.py, japan_years.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### japan_years.py
            EVENTS = [("Nihon Shoki", 720), ("Sanze Isshin no Ho", 723), ("Konden Einen Shizai Ho", 743)]
            spans = [later[1] - earlier[1] for earlier, later in zip(EVENTS, EVENTS[1:])]
            print("spans:", " ".join(str(s) for s in spans))
            print("first to last:", EVENTS[-1][1] - EVENTS[0][1], "years")
            ### japan_years.c
            #include <stdio.h>

            int main(void) {
                int shoki = 720, sanze = 723, konden = 743;
                printf("spans: %d %d\n", sanze - shoki, konden - sanze);
                printf("first to last: %d years\n", konden - shoki);
                return 0;
            }
            ### run.sh
            python3 japan_years.py > python.txt
            cc -o japan_years_c japan_years.c
            ./japan_years_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
