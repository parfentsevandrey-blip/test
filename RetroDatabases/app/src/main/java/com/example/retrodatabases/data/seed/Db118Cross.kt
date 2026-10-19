package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 860 to 870: the Glagolitic block of Unicode, from U+2C00 to U+2C5F, counted in Python and in C. */
val db118Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.CROSS,
        section = "Encoding",
        title = "The Glagolitic letters of Unicode, from U+2C00 to U+2C5F, counted in Python and C",
        originalTitle = "Glagolitsa",
        author = "Unicode Consortium, for the letters of Constantine",
        year = 863,
        summary = "Python and C programs count the code points of the Glagolitic block, from U+2C00 to U+2C5F, and a script checks that both give 96.",
        details = "The block is a modern encoding, not a record of 863: the year 863 dates the alphabet, and most of the block came into Unicode in 2005, with two positions filled in 2021. The run tests the arithmetic of the block's range.",
        rare = true,
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### glagolitic_block.py
            first = 0x2C00   # U+2C00, the first code point of the block
            last = 0x2C5F    # U+2C5F, the last code point of the block
            print("code points in the Glagolitic block:", last - first + 1)
            ### glagolitic_block.c
            #include <stdio.h>

            int main(void) {
                int first = 0x2C00;   /* U+2C00, the first code point */
                int last = 0x2C5F;    /* U+2C5F, the last code point */
                printf("code points in the Glagolitic block: %d\n", last - first + 1);
                return 0;
            }
            ### run.sh
            python3 glagolitic_block.py > python.txt
            cc -o glagolitic_block_c glagolitic_block.c
            ./glagolitic_block_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
