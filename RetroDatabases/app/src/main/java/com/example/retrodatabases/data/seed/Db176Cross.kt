package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 280: how long the states of Shu and Wu lasted, counted in Python and in C. */
val db176Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.CROSS,
        section = "States",
        title = "How long Shu and Wu lasted as states of the Three Kingdoms, counted in Python and C",
        originalTitle = "Years of two states",
        author = "No single author",
        year = 280,
        summary = "Shu lasted from its imperial title in 221 to its fall in 263, and Wu from its imperial title in 229 to its fall in 280, and a script checks that the Python and C programs print the same counts.",
        details = "Accounts of Wu give 222 as its start as a kingdom and 229 as its start as an empire, and the example uses 229. The run script compares the two outputs line by line, and the counts are 42 years for Shu and 51 years for Wu.",
        instructions = """
            1. Save the programs as shu_wu.py and shu_wu.c, and the script as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### shu_wu.py
            start_shu, end_shu = 221, 263   # Shu takes the imperial title in 221 and falls in 263
            start_wu, end_wu = 229, 280     # Wu takes the imperial title in 229 and falls in 280
            print("Shu", end_shu - start_shu, "years")
            print("Wu", end_wu - start_wu, "years")
            print("Wu outlasted Shu by", (end_wu - start_wu) - (end_shu - start_shu), "years")
            ### shu_wu.c
            #include <stdio.h>

            int main(void) {
                int start_shu = 221, end_shu = 263;   /* Shu takes the imperial title in 221 and falls in 263 */
                int start_wu = 229, end_wu = 280;     /* Wu takes the imperial title in 229 and falls in 280 */
                printf("Shu %d years\n", end_shu - start_shu);
                printf("Wu %d years\n", end_wu - start_wu);
                printf("Wu outlasted Shu by %d years\n", (end_wu - start_wu) - (end_shu - start_shu));
                return 0;
            }
            ### run.sh
            python3 shu_wu.py > python.txt
            cc -o shu_wu_c shu_wu.c
            ./shu_wu_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
