package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 140 to 150: the years from the Antonine Wall to Huan's accession, counted by the same program in Python and in C. */
val db190Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Years from the Antonine Wall to Huan's accession, in Python and C",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 146,
        summary = "The Antonine Wall was begun in 142 and Huan took the Han throne in 146, and the same program counts the four years between them in Python and in C.",
        details = "Both dates are used as commonly stated, and the two programs print the same line, which the run script confirms by comparing their output. The count is plain arithmetic on fixed years, so the output is the same on every run.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### wall_huan.py
            START = 142   # the Antonine Wall is begun
            END = 146     # Huan takes the Han throne
            print("years from the Antonine Wall to Huan's accession:", END - START)
            ### wall_huan.c
            #include <stdio.h>

            int main(void) {
                const int start = 142;   /* the Antonine Wall is begun */
                const int end = 146;     /* Huan takes the Han throne */
                printf("years from the Antonine Wall to Huan's accession: %d\n", end - start);
                return 0;
            }
            ### run.sh
            python3 wall_huan.py > python.txt
            cc -o wall_huan_c wall_huan.c
            ./wall_huan_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
