package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1236: the blocks of the Tripitaka Koreana divided by two readings of the carving's length, in Python and C. */
val db81Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.CROSS,
        section = "Printing",
        title = "Blocks per year of the Tripitaka carving under two readings, in Python and C",
        originalTitle = "Palman daejanggyeong carving years",
        author = "The Goryeo carvers",
        year = 1236,
        summary = "The 81,258 blocks of the Tripitaka Koreana give 5417 blocks a year over 15 years or 5078 over 16, and a script checks that the Python and C programs print the same two figures.",
        details = "The two readings of the carving's length are the ones given in the accounts, and the division is whole-number division in both languages. The run script compares the two outputs line by line, as the other cross records do.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### blocks_year.py
            BLOCKS = 81258
            for years in (15, 16):
                print(years, "years:", BLOCKS // years, "blocks a year")
            ### blocks_year.c
            #include <stdio.h>

            int main(void) {
                const int blocks = 81258;
                const int spans[2] = {15, 16};
                for (int i = 0; i < 2; i++) {
                    printf("%d years: %d blocks a year\n", spans[i], blocks / spans[i]);
                }
                return 0;
            }
            ### run.sh
            python3 blocks_year.py > python.txt
            cc -o blocks_year_c blocks_year.c
            ./blocks_year_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
