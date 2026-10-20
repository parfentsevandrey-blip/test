package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 200 to 210: the years from the battle of Guandu to Cao Cao's order of 210, counted in Python and in C. */
val db184Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Years from the battle of Guandu to Cao Cao's order of 210, in Python and C",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 200,
        summary = "The years from the battle of Guandu in 200 to Cao Cao's order of 210 are counted by the same short program in Python and in C.",
        details = "Both events are taken at their usual dates, and both programs print a count of 10 years, which the run script compares line by line. The example checks date arithmetic only and says nothing about the events themselves.",
        instructions = """
            1. Save the example as guandu.py and guandu.c, and the script as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### guandu.py
            start = 200   # the battle of Guandu, usual date
            end = 210     # Cao Cao's order of 210, usual date
            print("years from Guandu to Cao Cao's order:", end - start)
            ### guandu.c
            #include <stdio.h>

            int main(void) {
                int start = 200;   /* the battle of Guandu, usual date */
                int end = 210;     /* Cao Cao's order of 210, usual date */
                printf("years from Guandu to Cao Cao's order: %d\n", end - start);
                return 0;
            }
            ### run.sh
            python3 guandu.py > python.txt
            cc -o guandu_c guandu.c
            ./guandu_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
