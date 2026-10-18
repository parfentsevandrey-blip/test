package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1250 and 1251: the character total of the Tripitaka blocks and the Coptic years of the 1250s, each in Python and C. */
val db79Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.CROSS,
        section = "Woodblocks",
        title = "The Tripitaka block total in Python and C",
        originalTitle = "Block total",
        author = "The carvers of the Tripitaka Koreana",
        year = 1251,
        summary = "The total of 52330152 characters on the Tripitaka Koreana blocks is computed by a Python program and a C program that must print the same result.",
        details = "The example uses the usual count of 81,258 blocks and 644 characters per block, and the run script compares the two outputs line by line. The total is a derived figure, so the example checks the arithmetic in two languages rather than a count of the carved blocks.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### total.py
            BLOCKS = 81258
            PER_BLOCK = 23 * 14 * 2
            print("blocks", BLOCKS)
            print("characters per block", PER_BLOCK)
            print("total characters", BLOCKS * PER_BLOCK)
            ### total.c
            #include <stdio.h>

            int main(void) {
                int blocks = 81258;
                int per_block = 23 * 14 * 2;   /* two sides of 23 lines of 14 characters */
                printf("blocks %d\n", blocks);
                printf("characters per block %d\n", per_block);
                printf("total characters %d\n", blocks * per_block);
                return 0;
            }
            ### run.sh
            python3 total.py > python.txt
            cc -o total_c total.c
            ./total_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "The Coptic years of 1250 to 1254 in Python and C",
        originalTitle = "Era of Martyrs",
        author = "The Coptic church of Egypt",
        year = 1250,
        summary = "The Coptic years of 1250 to 1254 are computed by a Python program and a C program that subtract 284 from each Christian year and print the same list.",
        details = "The Era of Martyrs begins in 284 of the Christian era, and the run script compares the two outputs line by line. The example checks the arithmetic in two languages and leaves aside the Coptic new year, which falls in September.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### coptic.py
            OFFSET = 284
            for year in range(1250, 1255):
                print(year, "is Coptic year", year - OFFSET)
            ### coptic.c
            #include <stdio.h>

            int main(void) {
                for (int year = 1250; year <= 1254; year++) {
                    printf("%d is Coptic year %d\n", year, year - 284);   /* the Era of Martyrs begins in 284 of the Christian era */
                }
                return 0;
            }
            ### run.sh
            python3 coptic.py > python.txt
            cc -o coptic_c coptic.c
            ./coptic_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
