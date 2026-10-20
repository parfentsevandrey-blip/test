package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 20 to 30: a Persian parasang in stades and Roman miles, in Python and in C. */
val db202Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.CROSS,
        section = "Units",
        title = "The parasang in stades, in Python and C",
        originalTitle = "Parasanges",
        author = "Herodotus and Greek writers",
        year = 23,
        summary = "A Persian parasang was reckoned at thirty stades, and a script turns one to three parasangs into stades and Roman miles in Python and C.",
        details = "Herodotus reckons the parasang at thirty stades, and eight stades make a Roman mile, so one parasang comes to three and three-quarter Roman miles. The Python and C programs print the same three routes of one to three parasangs, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### parasang.py
            STADES_PER_PARASANG = 30   # Herodotus's measure of the Persian parasang
            STADES_PER_MILE = 8        # eight stades to a Roman mile
            for parasangs in range(1, 4):
                stades = parasangs * STADES_PER_PARASANG
                miles = stades / STADES_PER_MILE
                print(f"parasangs {parasangs}, stades {stades}, Roman miles {miles:.2f}")
            ### parasang.c
            #include <stdio.h>

            int main(void) {
                const int stades_per_parasang = 30;   /* Herodotus's measure of the Persian parasang */
                const int stades_per_mile = 8;        /* eight stades to a Roman mile */
                for (int parasangs = 1; parasangs <= 3; parasangs++) {
                    int stades = parasangs * stades_per_parasang;
                    printf("parasangs %d, stades %d, Roman miles %.2f\n", parasangs, stades, (double)stades / stades_per_mile);
                }
                return 0;
            }
            ### run.sh
            python3 parasang.py > python.txt
            cc -o parasang_c parasang.c
            ./parasang_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
