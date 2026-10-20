package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 690 to 700: Adomnan's years as abbot of Iona at the Cain of 697 and at the end of his abbacy in 704, counted in Python and in C. */
val db135Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.CROSS,
        section = "Irish",
        title = "Adomnan's years as abbot of Iona at the Cain of 697 and at the end of his abbacy in 704, counted in Python and C",
        originalTitle = "Cain Adomnain",
        author = "Adomnan of Iona",
        year = 697,
        summary = "Adomnan's years in office as abbot of Iona at the Cain of 697 and at the end of his abbacy in 704 are counted by a Python program and a C program that must print the same figures.",
        details = "Records place Adomnan's abbacy of Iona from 679 to 704, so the Cain came 18 years into his abbacy and the abbacy ended 7 years later. The two programs print the same three figures, which confirms the arithmetic, while the dates come from the records of the abbacy.",
        rare = true,
        instructions = """
            1. Save the example as adomnan.py, adomnan.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### adomnan.py
            START = 679  # the start of Adomnan's abbacy of Iona, as recorded
            CAIN = 697   # the Cain issued at the Synod of Birr
            END = 704    # the end of his abbacy, as recorded
            print("years in office at the Cain:", CAIN - START)
            print("years in office at the end of his abbacy:", END - START)
            print("years between the Cain and the end of his abbacy:", END - CAIN)
            ### adomnan.c
            #include <stdio.h>

            int main(void) {
                int start = 679, cain = 697, end = 704;
                printf("years in office at the Cain: %d\n", cain - start);
                printf("years in office at the end of his abbacy: %d\n", end - start);
                printf("years between the Cain and the end of his abbacy: %d\n", end - cain);
                return 0;
            }
            ### run.sh
            python3 adomnan.py > python.txt
            cc -o adomnan_c adomnan.c
            ./adomnan_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
