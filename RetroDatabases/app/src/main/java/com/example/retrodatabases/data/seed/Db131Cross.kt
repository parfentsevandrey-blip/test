package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 730 to 740: the years between three Kaiyuan books of 730, 737 and 738, computed in Python and in C. */
val db131Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.CROSS,
        section = "Tang",
        title = "The years between three Kaiyuan books, the catalogue of 730, the code of 737 and the handbook of 738, in Python and C",
        originalTitle = "Kaiyuan year spans",
        author = "Tang government",
        year = 730,
        summary = "The years between Zhisheng's catalogue of 730, the Kaiyuan Code of 737 and the Tang handbook of 738 are printed by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The spans printed are 7 years from the catalogue to the code, 1 year from the code to the handbook and 8 years from the catalogue to the handbook. The dates used are the usual ones for the three works, and the figures show only the arithmetic of those dates.",
        instructions = """
            1. Save the example as kaiyuan.py, kaiyuan.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### kaiyuan.py
            CATALOGUE = 730   # Zhisheng's Kaiyuan catalogue of Buddhist scriptures
            CODE = 737        # the Kaiyuan Code
            HANDBOOK = 738    # the Tang handbook of offices
            print("years from the catalogue to the code:", CODE - CATALOGUE)
            print("years from the code to the handbook:", HANDBOOK - CODE)
            print("years from the catalogue to the handbook:", HANDBOOK - CATALOGUE)
            ### kaiyuan.c
            #include <stdio.h>

            int main(void) {
                int catalogue = 730, code = 737, handbook = 738;
                printf("years from the catalogue to the code: %d\n", code - catalogue);
                printf("years from the code to the handbook: %d\n", handbook - code);
                printf("years from the catalogue to the handbook: %d\n", handbook - catalogue);
                return 0;
            }
            ### run.sh
            python3 kaiyuan.py > python.txt
            cc -o kaiyuan_c kaiyuan.c
            ./kaiyuan_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
