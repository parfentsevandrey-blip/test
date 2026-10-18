package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 971: the years of carving of the Shu canon, from 971 to 983 with both ends included, counted in Python and in C. */
val db107Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.CROSS,
        section = "Dates",
        title = "The years of carving of the Shu canon, 971 to 983, counted in Python and C",
        originalTitle = "The years of carving of the Shu canon, 971 to 983, counted in Python and C",
        author = "Song court commission",
        year = 971,
        summary = "Python and C programs count the years from 971 to 983 with both ends included, and a script checks that both give 13.",
        details = "Most accounts put the start of the carving at 971 and the finish at 983, and one gives 972 as the start, which would make 12 years. The C program counts with its own loop and no date library, while the Python program uses plain arithmetic, so the run tests the counting rather than the events.",
        instructions = """
            1. Save the example as shu_span.py, shu_span.c and run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### shu_span.py
            first = 971   # start of the carving, as most accounts give it
            last = 983    # finish of the carving
            print("years of carving, both ends included:", last - first + 1)
            ### shu_span.c
            #include <stdio.h>

            int main(void) {
                int first = 971;   /* start of the carving, as most accounts give it */
                int last = 983;    /* finish of the carving */
                int years = 0;
                for (int y = first; y <= last; y++) years++;
                printf("years of carving, both ends included: %d\n", years);
                return 0;
            }
            ### run.sh
            python3 shu_span.py > python.txt
            cc -o shu_span_c shu_span.c
            ./shu_span_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
